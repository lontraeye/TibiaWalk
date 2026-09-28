package org.tibiawalk.core.metadata;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.proto.StaticDataProto.Creature;
import org.tibiawalk.core.proto.StaticDataProto.StaticData;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera o metadata.json: outfits e montarias do Canary + monstros e bosses do staticdata do cliente.
 *
 * <p>Args: pastaAssets arquivoSaida [ref do Canary, padrão main]
 */
public final class MetadataBuilder {

    private static final String CANARY_REPO = "opentibiabr/canary";
    private static final String RAW = "https://raw.githubusercontent.com/" + CANARY_REPO + "/%s/data/XML/%s";

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String ref = args.length > 2 && !args[2].isBlank() ? args[2] : "main";
        new MetadataBuilder().build(assets, output, ref);
    }

    void build(Path assetsDir, Path output, String ref) throws Exception {
        ClientAssets client = ClientAssets.open(assetsDir);
        String commit = resolveCommit(ref);
        System.out.println("Canary " + ref + " @ " + commit);

        // Ordem de prioridade: player > mount > creature (bosses reusam looktypes de player, ex. Black Knight = 131).
        Map<Integer, LooktypeMeta> entries = new LinkedHashMap<>();
        int players = readOutfits(download(commit, "outfits.xml"), entries);
        int mounts = readMounts(download(commit, "mounts.xml"), entries);
        int creatures = readCreatures(client, entries);

        List<Integer> missing = new ArrayList<>();
        for (LooktypeMeta meta : entries.values()) {
            OutfitInfo info = client.outfit(meta.looktype());
            if (info == null) {
                missing.add(meta.looktype());
            }
        }

        JsonObject source = new JsonObject();
        source.addProperty("canary", "https://github.com/" + CANARY_REPO + "/tree/" + commit);
        source.addProperty("clientStaticData", client.catalog().staticDataFile() == null
                ? "" : client.catalog().staticDataFile().getFileName().toString());
        source.addProperty("generated", LocalDate.now().toString());

        JsonObject root = new JsonObject();
        root.add("source", source);
        Gson gson = MetadataJson.gson();
        root.add("looktypes", gson.toJsonTree(entries.values().stream()
                .sorted((a, b) -> Integer.compare(a.looktype(), b.looktype())).toList()));

        Files.createDirectories(output.toAbsolutePath().getParent());
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            gson.newBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        System.out.printf("players: %d, mounts: %d, creatures: %d -> %d looktypes em %s%n",
                players, mounts, creatures, entries.size(), output.toAbsolutePath());
        if (!missing.isEmpty()) {
            System.out.println("Aviso: looktypes que não existem neste cliente: " + missing);
        }
    }

    private int readOutfits(byte[] xml, Map<Integer, LooktypeMeta> entries) throws Exception {
        int count = 0;
        for (Element e : elements(xml, "outfit")) {
            int looktype = Integer.parseInt(e.getAttribute("looktype"));
            String sex = "0".equals(e.getAttribute("type")) ? "female" : "male";
            entries.put(looktype, new LooktypeMeta(looktype, LooktypeKind.PLAYER, e.getAttribute("name"), sex,
                    "yes".equals(e.getAttribute("premium")), null, null));
            count++;
        }
        return count;
    }

    private int readMounts(byte[] xml, Map<Integer, LooktypeMeta> entries) throws Exception {
        int count = 0;
        for (Element e : elements(xml, "mount")) {
            int looktype = Integer.parseInt(e.getAttribute("clientid"));
            entries.putIfAbsent(looktype, new LooktypeMeta(looktype, LooktypeKind.MOUNT, e.getAttribute("name"),
                    null, "yes".equals(e.getAttribute("premium")), Integer.parseInt(e.getAttribute("id")), null));
            count++;
        }
        return count;
    }

    private int readCreatures(ClientAssets client, Map<Integer, LooktypeMeta> entries) throws IOException {
        Path file = client.catalog().staticDataFile();
        if (file == null) {
            System.out.println("Aviso: cliente sem staticdata, criaturas ficam sem nome");
            return 0;
        }
        StaticData data;
        try (InputStream in = Files.newInputStream(file)) {
            data = StaticData.parseFrom(in);
        }

        // Vários monstros usam o mesmo looktype: o primeiro vira o nome, o resto vira alias.
        Map<Integer, List<String>> names = new LinkedHashMap<>();
        List<Creature> all = new ArrayList<>(data.getMonsterList());
        all.addAll(data.getBossList());
        for (Creature creature : all) {
            int looktype = creature.getOutfit().getLooktype();
            if (looktype == 0) {
                continue; // criaturas desenhadas como item (ex. objetos animados)
            }
            List<String> list = names.computeIfAbsent(looktype, k -> new ArrayList<>());
            if (!list.contains(creature.getName())) {
                list.add(creature.getName());
            }
        }

        int count = 0;
        for (Map.Entry<Integer, List<String>> e : names.entrySet()) {
            List<String> list = e.getValue();
            LooktypeMeta existing = entries.get(e.getKey());
            if (existing == null) {
                entries.put(e.getKey(), new LooktypeMeta(e.getKey(), LooktypeKind.CREATURE, list.get(0), null,
                        false, null, list.subList(1, list.size())));
                count++;
            } else {
                // Guarda o nome da criatura como alias do player/mount (ex. "Black Knight" em 131).
                List<String> aliases = new ArrayList<>(existing.aliases());
                list.stream().filter(n -> !aliases.contains(n) && !n.equalsIgnoreCase(existing.name()))
                        .forEach(aliases::add);
                entries.put(e.getKey(), new LooktypeMeta(existing.looktype(), existing.kind(), existing.name(),
                        existing.sex(), existing.premium(), existing.mountId(), aliases));
            }
        }
        return count;
    }

    private String resolveCommit(String ref) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.github.com/repos/" + CANARY_REPO + "/commits/" + ref))
                .header("Accept", "application/vnd.github+json").build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.out.println("Aviso: não consegui resolver o commit (" + response.statusCode() + "), usando " + ref);
            return ref;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject().get("sha").getAsString();
    }

    private byte[] download(String ref, String file) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(RAW, ref, file))).build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Falha ao baixar " + file + " do Canary: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static List<Element> elements(byte[] xml, String tag) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList nodes = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagName(tag);
        List<Element> list = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            list.add((Element) nodes.item(i));
        }
        return list;
    }
}
