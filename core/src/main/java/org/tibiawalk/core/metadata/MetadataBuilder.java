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
 *   <li>heurística: looktype sem nome que tem versão montada e cores é outfit de player;</li>
 *   <li>monstros e NPCs do Canary (um .lua por criatura): nomeia o que o bestiário não cobre;</li>
 *   <li>metadata-overrides.json: correções manuais.</li>
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
        List<CanaryLuaSource.Creature> luaCreatures = new CanaryLuaSource(http, CANARY_REPO).creatures(commit);
        int[] fromLua = readCanaryCreatures(luaCreatures, entries);
        List<GameCharacter> characters = buildCharacters(client, luaCreatures);
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
        source.addProperty("clientLooktypes", client.outfits().size());

        JsonObject root = new JsonObject();
        root.add("source", source);
        Gson gson = MetadataJson.gson();
        root.add("looktypes", gson.toJsonTree(entries.values().stream()
                .sorted((a, b) -> Integer.compare(a.looktype(), b.looktype())).toList()));
        root.add("characters", gson.toJsonTree(characters));

        Metadata previous = null;
        if (Files.isRegularFile(output)) {
            try (var reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
                previous = Metadata.read(reader);
            }
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            gson.newBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        System.out.printf("players: %d do TibiaWiki + %d só do Canary; mounts: %d do TibiaWiki + %d só do Canary; "
                        + "creatures: %d do staticdata + %d do Canary; NPCs: %d; sem nome mas com cara de player: %d "
                        + "-> %d looktypes em %s%n",
                wikiPlayers, canaryPlayers, wikiMounts, canaryMounts, creatures, fromLua[0], fromLua[1],
                guessed.size(), entries.size(), output.toAbsolutePath());
        long unnamed = client.outfits().stream().filter(o -> !entries.containsKey(o.looktype())).count();
        System.out.println("Looktypes do cliente ainda sem nome: " + unnamed);
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
        if (previous != null) {
            printChanges(previous, entries, client.outfits().size());
            printCharacterChanges(previous.characters(), characters);
        }
    }

    /**
     * NPCs (Canary), monstros e bosses (staticdata do cliente + Canary), com o outfit completo.
     * Mesmo nome nas duas fontes: outfit e tipo do staticdata (é o oficial do cliente), nome com a grafia
     * do Canary (o bestiário vem em minúsculas).
     */
    private List<GameCharacter> buildCharacters(ClientAssets client, List<CanaryLuaSource.Creature> lua)
            throws IOException {
        Map<String, GameCharacter> byKey = new LinkedHashMap<>();
        Map<String, String> canaryNames = new java.util.HashMap<>();
        for (CanaryLuaSource.Creature c : lua) {
            if (!c.name().matches(".*\\p{L}.*")) {
                continue; // arquivos de exemplo/placeholder, ex. "...".lua
            }
            GameCharacter.Kind kind = c.type() == CanaryLuaSource.Type.NPC ? GameCharacter.Kind.NPC
                    : c.boss() ? GameCharacter.Kind.BOSS : GameCharacter.Kind.MONSTER;
            String key = characterKey(kind, c.name());
            canaryNames.putIfAbsent(key, c.name());
            byKey.putIfAbsent(key, new GameCharacter(c.name(), kind, c.looktype(), c.head(), c.body(), c.legs(),
                    c.feet(), c.addons(), c.mount(), "canary"));
        }

        StaticData data = staticData(client);
        if (data != null) {
            addStatic(data.getMonsterList(), GameCharacter.Kind.MONSTER, byKey, canaryNames);
            addStatic(data.getBossList(), GameCharacter.Kind.BOSS, byKey, canaryNames);
        }

        int dropped = 0;
        List<GameCharacter> result = new ArrayList<>();
        for (GameCharacter c : byKey.values()) {
            OutfitInfo info = client.outfit(c.looktype());
            if (info == null || info.idle() == null) {
                dropped++;
                continue;
            }
            // Montaria que não existe no cliente (ou outfit que não monta) é descartada.
            int mount = c.mount() > 0 && info.mountable() && client.outfit(c.mount()) != null ? c.mount() : 0;
            result.add(mount == c.mount() ? c : new GameCharacter(c.name(), c.kind(), c.looktype(), c.head(),
                    c.body(), c.legs(), c.feet(), c.addons(), mount, c.source()));
        }
        result.sort(java.util.Comparator.comparing(GameCharacter::kind)
                .thenComparing(GameCharacter::name, String.CASE_INSENSITIVE_ORDER));
        long npcs = result.stream().filter(c -> c.kind() == GameCharacter.Kind.NPC).count();
        long monsters = result.stream().filter(c -> c.kind() == GameCharacter.Kind.MONSTER).count();
        long bosses = result.stream().filter(c -> c.kind() == GameCharacter.Kind.BOSS).count();
        System.out.printf("Personagens: %d NPCs, %d monstros, %d bosses%s%n", npcs, monsters, bosses,
                dropped > 0 ? " (" + dropped + " com looktype que não existe neste cliente, ignorados)" : "");
        return result;
    }

    private static void addStatic(List<Creature> creatures, GameCharacter.Kind kind, Map<String, GameCharacter> byKey,
                                  Map<String, String> canaryNames) {
        for (Creature c : creatures) {
            int looktype = c.getOutfit().getLooktype();
            if (looktype == 0) {
                continue;
            }
            // Um boss do bestiário pode estar no Canary como monstro comum, e vice-versa: procura nos dois.
            String key = characterKey(kind, c.getName());
            String other = characterKey(kind == GameCharacter.Kind.BOSS ? GameCharacter.Kind.MONSTER
                    : GameCharacter.Kind.BOSS, c.getName());
            if (!byKey.containsKey(key) && byKey.containsKey(other)) {
                byKey.remove(other);
                canaryNames.putIfAbsent(key, canaryNames.get(other));
            }
            var colors = c.getOutfit().getColors();
            String name = canaryNames.getOrDefault(key, c.getName());
            String source = canaryNames.containsKey(key) ? "staticdata+canary" : "staticdata";
            GameCharacter previous = byKey.get(key);
            byKey.put(key, new GameCharacter(name, kind, looktype, Math.min(colors.getHead(), 132),
                    Math.min(colors.getBody(), 132), Math.min(colors.getLegs(), 132), Math.min(colors.getFeet(), 132),
                    c.getOutfit().getAddons() & 3, previous != null ? previous.mount() : 0, source));
        }
    }

    /** NPC e criatura com o mesmo nome são personagens diferentes; monstro e boss disputam o mesmo nome. */
    private static String characterKey(GameCharacter.Kind kind, String name) {
        return (kind == GameCharacter.Kind.NPC ? "npc:" : kind.name().toLowerCase() + ":")
                + name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private StaticData staticData;

    private StaticData staticData(ClientAssets client) throws IOException {
        if (staticData == null && client.catalog().staticDataFile() != null) {
            try (InputStream in = Files.newInputStream(client.catalog().staticDataFile())) {
                staticData = StaticData.parseFrom(in);
            }
        }
        return staticData;
    }

    private static void printCharacterChanges(List<GameCharacter> before, List<GameCharacter> now) {
        java.util.Set<String> old = new java.util.HashSet<>();
        before.forEach(c -> old.add(characterKey(c.kind(), c.name())));
        java.util.Set<String> current = new java.util.HashSet<>();
        now.forEach(c -> current.add(characterKey(c.kind(), c.name())));
        List<String> added = now.stream().filter(c -> !old.contains(characterKey(c.kind(), c.name())))
                .map(c -> c.name() + " [" + c.kind().name().toLowerCase() + "]").toList();
        List<String> removed = before.stream().filter(c -> !current.contains(characterKey(c.kind(), c.name())))
                .map(c -> c.name() + " [" + c.kind().name().toLowerCase() + "]").toList();
        printList("Personagens novos", added);
        printList("Personagens removidos", removed);
    }

    private static final int MAX_LINES = 25;

    /** O que mudou em relação ao metadata.json anterior, para revisar antes de commitar. */
    private static void printChanges(Metadata previous, Map<Integer, LooktypeMeta> current, int clientLooktypes) {
        Map<Integer, LooktypeMeta> old = new java.util.TreeMap<>();
        previous.all().forEach(m -> old.put(m.looktype(), m));

        List<String> added = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        new java.util.TreeMap<>(current).forEach((lt, now) -> {
            LooktypeMeta before = old.get(lt);
            if (before == null) {
                added.add(lt + " " + describe(now));
            } else if (!before.name().equals(now.name()) || before.kind() != now.kind()
                    || !java.util.Objects.equals(before.sex(), now.sex())) {
                changed.add(lt + " " + describe(before) + " -> " + describe(now));
            }
        });
        old.forEach((lt, before) -> {
            if (!current.containsKey(lt)) {
                removed.add(lt + " " + describe(before));
            }
        });

        System.out.println();
        System.out.println("=== Mudanças em relação ao metadata.json anterior ===");
        if (previous.source().has("clientLooktypes")) {
            int before = previous.source().get("clientLooktypes").getAsInt();
            if (before != clientLooktypes) {
                System.out.printf("Cliente: %d -> %d looktypes (%+d)%n", before, clientLooktypes, clientLooktypes - before);
            }
        }
        if (added.isEmpty() && changed.isEmpty() && removed.isEmpty()) {
            System.out.println("Nenhuma mudança de nome, categoria ou sexo.");
            return;
        }
        printList("Ganharam nome", added);
        printList("Mudaram", changed);
        printList("Perderam nome", removed);
    }

    private static String describe(LooktypeMeta m) {
        return m.name() + " [" + m.kind().name().toLowerCase() + (m.sex() != null ? ", " + m.sex() : "") + "]";
    }

    private static void printList(String title, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        System.out.println(title + " (" + lines.size() + "):");
        lines.stream().limit(MAX_LINES).forEach(line -> System.out.println("  " + line));
        if (lines.size() > MAX_LINES) {
            System.out.println("  … e mais " + (lines.size() - MAX_LINES));
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

    /**
     * Monstros primeiro, depois NPCs. Looktype novo ganha nome; criatura/NPC já conhecido ganha alias.
     * Player e montaria não recebem alias: NPCs vestindo Citizen, por exemplo, seriam centenas.
     *
     * @return {monstros novos, NPCs novos}
     */
    private int[] readCanaryCreatures(List<CanaryLuaSource.Creature> creatures, Map<Integer, LooktypeMeta> entries) {
        int[] added = new int[2];
        for (CanaryLuaSource.Type type : CanaryLuaSource.Type.values()) {
            for (CanaryLuaSource.Creature c : creatures) {
                if (c.type() != type) {
                    continue;
                }
                LooktypeMeta existing = entries.get(c.looktype());
                if (existing == null) {
                    LooktypeKind kind = type == CanaryLuaSource.Type.NPC ? LooktypeKind.NPC : LooktypeKind.CREATURE;
                    entries.put(c.looktype(), new LooktypeMeta(c.looktype(), kind, c.name(), null, false, null,
                            null, "canary"));
                    added[type.ordinal()]++;
                } else if ((existing.kind() == LooktypeKind.CREATURE || existing.kind() == LooktypeKind.NPC)
                        && !existing.name().equalsIgnoreCase(c.name())
                        && existing.aliases().stream().noneMatch(a -> a.equalsIgnoreCase(c.name()))) {
                    List<String> aliases = new ArrayList<>(existing.aliases());
                    aliases.add(c.name());
                    entries.put(c.looktype(), new LooktypeMeta(existing.looktype(), existing.kind(), existing.name(),
                            existing.sex(), existing.premium(), existing.mountId(), aliases, existing.source()));
                }
            }
        }
        return added;
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
        StaticData data = staticData(client);

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
