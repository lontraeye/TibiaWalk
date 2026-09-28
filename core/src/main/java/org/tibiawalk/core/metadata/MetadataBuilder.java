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
 * Gera o metadata.json. Fontes, em ordem de prioridade:
 * <ol>
 *   <li>TibiaWiki: outfits (male_id/female_id) e montarias; costuma ser o mais atualizado;</li>
 *   <li>Canary (outfits.xml, mounts.xml): completa o que faltar e fornece o id interno da montaria;</li>
 *   <li>staticdata do cliente: monstros e bosses;</li>
 *   <li>heurística: looktype sem nome que tem versão montada e cores é outfit de player.</li>
 * </ol>
 *
 * <p>Args: pastaAssets arquivoSaida [ref do Canary, padrão main]
 */
public final class MetadataBuilder {

    private static final String CANARY_REPO = "opentibiabr/canary";
    private static final String RAW = "https://raw.githubusercontent.com/" + CANARY_REPO + "/%s/data/XML/%s";

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    /** Par masculino/feminino segundo o TibiaWiki: looktype -> o outro looktype do mesmo outfit. */
    private final Map<Integer, Integer> wikiPartner = new java.util.HashMap<>();

    /** Args: pastaAssets arquivoSaida [ref do Canary] [arquivo de overrides] */
    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String ref = args.length > 2 && !args[2].isBlank() ? args[2] : "main";
        Path overrides = args.length > 3 && !args[3].isBlank() ? Path.of(args[3]) : null;
        new MetadataBuilder().build(assets, output, ref, overrides);
    }

    void build(Path assetsDir, Path output, String ref, Path overrides) throws Exception {
        ClientAssets client = ClientAssets.open(assetsDir);
        String commit = resolveCommit(ref);
        System.out.println("Canary " + ref + " @ " + commit);

        // Ordem de prioridade: player > mount > creature (bosses reusam looktypes de player, ex. Black Knight = 131).
        Map<Integer, LooktypeMeta> entries = new LinkedHashMap<>();
        TibiaWikiSource wiki = new TibiaWikiSource(http);
        int wikiPlayers = readWikiOutfits(wiki.outfits(), entries);
        int canaryPlayers = readOutfits(download(commit, "outfits.xml"), entries);
        int wikiMounts = readWikiMounts(wiki.mounts(), entries);
        int canaryMounts = readMounts(download(commit, "mounts.xml"), entries);
        int creatures = readCreatures(client, entries);
        List<Integer> guessed = guessPlayers(client, entries);
        int overridden = applyOverrides(overrides, entries);
        guessed.removeIf(lt -> !"heuristic".equals(entries.get(lt).source()));

        List<Integer> missing = new ArrayList<>();
        for (LooktypeMeta meta : entries.values()) {
            OutfitInfo info = client.outfit(meta.looktype());
            if (info == null) {
                missing.add(meta.looktype());
            }
        }

        JsonObject source = new JsonObject();
        source.addProperty("tibiawiki", "https://tibia.fandom.com (CC BY-SA)");
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
        System.out.printf("players: %d do TibiaWiki + %d só do Canary; mounts: %d do TibiaWiki + %d só do Canary; "
                        + "creatures: %d; sem nome mas com cara de player: %d -> %d looktypes em %s%n",
                wikiPlayers, canaryPlayers, wikiMounts, canaryMounts, creatures, guessed.size(),
                entries.size(), output.toAbsolutePath());
        if (overridden > 0) {
            System.out.println("Overrides aplicados: " + overridden);
        }
        if (!guessed.isEmpty()) {
            System.out.println("Outfits de player sem nome em nenhuma fonte (nome/sexo provisórios; corrija em "
                    + "metadata-overrides.json): " + guessed);
        }
        if (!missing.isEmpty()) {
            System.out.println("Aviso: looktypes que não existem neste cliente: " + missing);
        }
    }

    private int readWikiOutfits(List<TibiaWikiSource.Outfit> outfits, Map<Integer, LooktypeMeta> entries) {
        int count = 0;
        for (TibiaWikiSource.Outfit o : outfits) {
            if (o.male() != null && o.male() > 0) {
                entries.put(o.male(), new LooktypeMeta(o.male(), LooktypeKind.PLAYER, o.name(), "male",
                        o.premium(), null, null, "tibiawiki"));
                count++;
            }
            if (o.female() != null && o.female() > 0) {
                entries.put(o.female(), new LooktypeMeta(o.female(), LooktypeKind.PLAYER, o.name(), "female",
                        o.premium(), null, null, "tibiawiki"));
                count++;
            }
            if (o.male() != null && o.female() != null) {
                wikiPartner.put(o.male(), o.female());
                wikiPartner.put(o.female(), o.male());
            }
        }
        return count;
    }

    /**
     * Completa com o Canary o que o TibiaWiki não tem; retorna quantos vieram só dele.
     *
     * <p>Sexo divergente: vale o Canary (é o que os servidores usam, e conferindo os sprites ele acertou
     * Wayfarer e Beastmaster), exceto quando o próprio Canary dá o mesmo sexo aos dois lados do par.
     */
    private int readOutfits(byte[] xml, Map<Integer, LooktypeMeta> entries) throws Exception {
        Map<Integer, String> canarySex = new java.util.HashMap<>();
        List<Element> outfits = elements(xml, "outfit");
        for (Element e : outfits) {
            canarySex.put(Integer.parseInt(e.getAttribute("looktype")), "0".equals(e.getAttribute("type")) ? "female" : "male");
        }

        int count = 0;
        for (Element e : outfits) {
            int looktype = Integer.parseInt(e.getAttribute("looktype"));
            String sex = canarySex.get(looktype);
            LooktypeMeta existing = entries.get(looktype);
            if (existing != null) {
                if (!sex.equals(existing.sex())) {
                    Integer partner = wikiPartner.get(looktype);
                    boolean canaryConsistent = partner == null || !sex.equals(canarySex.get(partner));
                    System.out.printf("Sexo divergente em %d (%s): TibiaWiki=%s, Canary=%s -> usando %s%n",
                            looktype, existing.name(), existing.sex(), sex, canaryConsistent ? "Canary" : "TibiaWiki");
                    if (canaryConsistent) {
                        entries.put(looktype, new LooktypeMeta(looktype, existing.kind(), existing.name(), sex,
                                existing.premium(), null, existing.aliases(), "tibiawiki+canary"));
                    }
                }
                continue;
            }
            entries.put(looktype, new LooktypeMeta(looktype, LooktypeKind.PLAYER, e.getAttribute("name"), sex,
                    "yes".equals(e.getAttribute("premium")), null, null, "canary"));
            count++;
        }
        return count;
    }

    private int readWikiMounts(List<TibiaWikiSource.Mount> mounts, Map<Integer, LooktypeMeta> entries) {
        int count = 0;
        for (TibiaWikiSource.Mount m : mounts) {
            if (entries.putIfAbsent(m.looktype(), new LooktypeMeta(m.looktype(), LooktypeKind.MOUNT, m.name(),
                    null, false, null, null, "tibiawiki")) == null) {
                count++;
            }
        }
        return count;
    }

    /** Do Canary vem o id interno da montaria e o premium; montarias que o TibiaWiki não tem entram aqui. */
    private int readMounts(byte[] xml, Map<Integer, LooktypeMeta> entries) throws Exception {
        int count = 0;
        for (Element e : elements(xml, "mount")) {
            int looktype = Integer.parseInt(e.getAttribute("clientid"));
            int mountId = Integer.parseInt(e.getAttribute("id"));
            boolean premium = "yes".equals(e.getAttribute("premium"));
            LooktypeMeta existing = entries.get(looktype);
            if (existing == null) {
                entries.put(looktype, new LooktypeMeta(looktype, LooktypeKind.MOUNT, e.getAttribute("name"),
                        null, premium, mountId, null, "canary"));
                count++;
            } else if (existing.kind() == LooktypeKind.MOUNT) {
                entries.put(looktype, new LooktypeMeta(looktype, LooktypeKind.MOUNT, existing.name(), null,
                        premium, mountId, existing.aliases(), existing.source()));
            }
        }
        return count;
    }

    /**
     * Looktypes que nenhuma fonte nomeou mas que têm versão montada e cores: na prática, outfits de
     * player novos. Entram como player sem sexo definido, para aparecerem no app mesmo assim.
     */
    private List<Integer> guessPlayers(ClientAssets client, Map<Integer, LooktypeMeta> entries) {
        List<OutfitInfo> candidates = client.outfits().stream()
                .filter(info -> !entries.containsKey(info.looktype()) && info.mountable() && info.colorable())
                .toList();
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        candidates.forEach(info -> ids.add(info.looktype()));

        // Pares consecutivos viram um outfit só, menor = masculino (ordem de 81 dos 104 pares conhecidos).
        List<Integer> guessed = new ArrayList<>();
        for (OutfitInfo info : candidates) {
            int lt = info.looktype();
            if (entries.containsKey(lt)) {
                continue;
            }
            if (ids.contains(lt + 1) && sameShape(info, client.outfit(lt + 1))) {
                String name = "Outfit #" + lt + "/" + (lt + 1);
                entries.put(lt, new LooktypeMeta(lt, LooktypeKind.PLAYER, name, "male", false, null, null, "heuristic"));
                entries.put(lt + 1, new LooktypeMeta(lt + 1, LooktypeKind.PLAYER, name, "female", false, null, null,
                        "heuristic"));
                guessed.add(lt);
                guessed.add(lt + 1);
            } else {
                entries.put(lt, new LooktypeMeta(lt, LooktypeKind.PLAYER, "Outfit #" + lt, null, false, null, null,
                        "heuristic"));
                guessed.add(lt);
            }
        }
        return guessed;
    }

    private static boolean sameShape(OutfitInfo a, OutfitInfo b) {
        return b != null && a.addonLayers() == b.addonLayers() && a.mountStates() == b.mountStates()
                && a.layers() == b.layers() && a.directions() == b.directions();
    }

    /**
     * Correções manuais, com prioridade sobre todas as fontes. Formato:
     * {@code [{"looktype": 1640, "name": "...", "sex": "male", "kind": "player"}]}; campos omitidos
     * mantêm o valor atual.
     */
    private int applyOverrides(Path file, Map<Integer, LooktypeMeta> entries) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return 0;
        }
        com.google.gson.JsonArray list;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            list = JsonParser.parseReader(reader).getAsJsonArray();
        }
        int count = 0;
        for (var element : list) {
            JsonObject o = element.getAsJsonObject();
            int lt = o.get("looktype").getAsInt();
            LooktypeMeta current = entries.get(lt);
            LooktypeKind kind = o.has("kind") ? LooktypeKind.valueOf(o.get("kind").getAsString().toUpperCase())
                    : current != null ? current.kind() : LooktypeKind.PLAYER;
            String name = o.has("name") ? o.get("name").getAsString() : current != null ? current.name() : "#" + lt;
            String sex = o.has("sex") ? (o.get("sex").isJsonNull() ? null : o.get("sex").getAsString())
                    : current != null ? current.sex() : null;
            entries.put(lt, new LooktypeMeta(lt, kind, name, sex,
                    current != null && current.premium(), current != null ? current.mountId() : null,
                    current != null ? current.aliases() : null, "override"));
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
                        false, null, list.subList(1, list.size()), "staticdata"));
                count++;
            } else {
                // Guarda o nome da criatura como alias do player/mount (ex. "Black Knight" em 131).
                List<String> aliases = new ArrayList<>(existing.aliases());
                list.stream().filter(n -> !aliases.contains(n) && !n.equalsIgnoreCase(existing.name()))
                        .forEach(aliases::add);
                entries.put(e.getKey(), new LooktypeMeta(existing.looktype(), existing.kind(), existing.name(),
                        existing.sex(), existing.premium(), existing.mountId(), aliases, existing.source()));
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
