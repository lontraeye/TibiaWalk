package org.tibiawalk.cli;

import com.google.gson.GsonBuilder;
import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.metadata.GameCharacter;
import org.tibiawalk.core.metadata.LooktypeKind;
import org.tibiawalk.core.metadata.LooktypeMeta;
import org.tibiawalk.core.metadata.Metadata;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "list", mixinStandardHelpOptions = true,
        description = "Lista os looktypes do cliente, com nome e o que cada um suporta.")
final class ListCommand implements Callable<Integer> {

    @Mixin
    TibiaWalkCli.AssetsOption assets;

    static final class KindFilter {
        @Option(names = "--players", description = "Só outfits de player.")
        boolean players;

        @Option(names = "--mounts", description = "Só montarias.")
        boolean mounts;

        @Option(names = "--creatures", description = "Só monstros e bosses.")
        boolean creatures;

        @Option(names = "--npcs", description = "NPCs, com o outfit completo de cada um.")
        boolean npcs;

        @Option(names = "--monsters", description = "Monstros e bosses, com o outfit completo de cada um.")
        boolean monsters;

        @Option(names = "--unknown", description = "Só looktypes sem nome conhecido.")
        boolean unknown;
    }

    @ArgGroup(exclusive = true)
    KindFilter kind;

    @Option(names = "--female", description = "Com --players: só femininos.")
    boolean female;

    @Option(names = "--male", description = "Com --players: só masculinos.")
    boolean male;

    @Option(names = "--with-addons", description = "Só outfits com addons.")
    boolean withAddons;

    @Option(names = {"-s", "--search"}, description = "Filtra pelo nome (contém, sem diferenciar maiúsculas).")
    String search;

    @Option(names = "--json", description = "Saída em JSON.")
    boolean json;

    private record Row(OutfitInfo info, Optional<LooktypeMeta> meta) {
        String kindName() {
            return meta.map(m -> m.kind().name().toLowerCase(Locale.ROOT)).orElse("?");
        }

        String name() {
            return meta.map(LooktypeMeta::name).orElse("");
        }
    }

    @Override
    public Integer call() throws Exception {
        Metadata metadata = Metadata.bundled();
        if (kind != null && (kind.npcs || kind.monsters)) {
            return listCharacters(metadata, kind.npcs);
        }
        ClientAssets client = assets.open();

        Stream<Row> rows = client.outfits().stream().map(o -> new Row(o, metadata.get(o.looktype())));
        if (kind != null) {
            if (kind.players) {
                rows = rows.filter(r -> is(r, LooktypeKind.PLAYER));
            } else if (kind.mounts) {
                rows = rows.filter(r -> is(r, LooktypeKind.MOUNT));
            } else if (kind.creatures) {
                rows = rows.filter(r -> is(r, LooktypeKind.CREATURE));
            } else if (kind.unknown) {
                rows = rows.filter(r -> r.meta().isEmpty());
            }
        }
        if (female || male) {
            String sex = female ? "female" : "male";
            rows = rows.filter(r -> r.meta().map(m -> sex.equals(m.sex())).orElse(false));
        }
        if (withAddons) {
            rows = rows.filter(r -> r.info().addonCount() > 0);
        }
        if (search != null) {
            String q = search.toLowerCase(Locale.ROOT);
            rows = rows.filter(r -> r.meta().map(m -> m.name().toLowerCase(Locale.ROOT).contains(q)
                    || m.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(q))).orElse(false));
        }
        List<Row> list = rows.toList();

        if (json) {
            List<Map<String, Object>> out = list.stream().map(ListCommand::toMap).toList();
            System.out.println(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(out));
            return 0;
        }

        System.out.println("looktype  tipo      nome                           sexo    addons  montável  colorível");
        for (Row r : list) {
            OutfitInfo o = r.info();
            System.out.printf("%8d  %-8s  %-30s %-7s %6d  %8s  %9s%n", o.looktype(), r.kindName(),
                    truncate(r.name(), 30), r.meta().map(LooktypeMeta::sex).orElse(""),
                    o.addonCount(), o.mountable() ? "sim" : "-", o.colorable() ? "sim" : "-");
        }
        System.out.printf("%d looktypes%n", list.size());
        return 0;
    }

    /** Personagens (não looktypes): nome, looktype e o outfit que usam. */
    private int listCharacters(Metadata metadata, boolean npcs) {
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<GameCharacter> list = metadata.characters().stream()
                .filter(c -> npcs == (c.kind() == GameCharacter.Kind.NPC))
                .filter(c -> q.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(q))
                .toList();
        if (json) {
            System.out.println(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(list));
            return 0;
        }
        System.out.println("nome                                tipo      looktype  cores (h/b/l/f)  addons  montaria");
        for (GameCharacter c : list) {
            System.out.printf("%-35s %-8s  %8d  %3d/%3d/%3d/%3d  %6d  %s%n", truncate(c.name(), 35),
                    c.kind().name().toLowerCase(Locale.ROOT), c.looktype(), c.head(), c.body(), c.legs(), c.feet(),
                    c.addons(), c.mount() > 0 ? c.mount() : "-");
        }
        System.out.printf("%d %s%n", list.size(), npcs ? "NPCs" : "monstros e bosses");
        return 0;
    }

    private static boolean is(Row row, LooktypeKind kind) {
        return row.meta().map(m -> m.kind() == kind).orElse(false);
    }

    private static Map<String, Object> toMap(Row r) {
        OutfitInfo o = r.info();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("looktype", o.looktype());
        map.put("kind", r.meta().isPresent() ? r.kindName() : null);
        map.put("name", r.meta().map(LooktypeMeta::name).orElse(null));
        r.meta().map(LooktypeMeta::sex).ifPresent(sex -> map.put("sex", sex));
        r.meta().map(LooktypeMeta::mountId).ifPresent(id -> map.put("mountId", id));
        map.put("addons", o.addonCount());
        map.put("mountable", o.mountable());
        map.put("colorable", o.colorable());
        map.put("directions", o.directions());
        map.put("idleFrames", o.idle() == null ? 0 : o.idle().frames());
        map.put("movingFrames", o.moving() == null ? 0 : o.moving().frames());
        return map;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
