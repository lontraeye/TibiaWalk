package org.tibiawalk.web;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.metadata.GameCharacter;
import org.tibiawalk.core.metadata.LooktypeKind;
import org.tibiawalk.core.metadata.LooktypeMeta;
import org.tibiawalk.core.metadata.Metadata;
import org.tibiawalk.core.render.AnimationType;
import org.tibiawalk.core.render.Direction;
import org.tibiawalk.core.render.GifWriter;
import org.tibiawalk.core.render.OutfitRenderer;
import org.tibiawalk.core.render.OutfitRequest;
import org.tibiawalk.core.render.TibiaColor;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Renderiza a partir dos parâmetros da URL, com cache em memória dos arquivos prontos. */
final class OutfitService {

    /** Cores padrão de personagem novo no Tibia. */
    private static final int[] DEFAULT_COLORS = {78, 69, 58, 76};
    private static final int MAX_CACHED = 1000;

    record Rendered(byte[] bytes, String contentType) {
    }

    private record Params(OutfitRequest request, AnimationType animation, int frameMs, boolean png) {
        String key() {
            OutfitRequest r = request;
            return String.join("|", Integer.toString(r.looktype()), Integer.toString(r.addons()),
                    r.head().toHex(), r.body().toHex(), r.legs().toHex(), r.feet().toHex(),
                    Integer.toString(r.mount()), r.direction().name(), animation.name(),
                    Integer.toString(frameMs), png ? "png" : "gif");
        }
    }

    private final ClientAssets assets;
    private final OutfitRenderer renderer;
    private final Metadata metadata = Metadata.bundled();
    private final Map<String, Rendered> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Rendered> eldest) {
            return size() > MAX_CACHED;
        }
    };

    OutfitService(ClientAssets assets) {
        this.assets = assets;
        this.renderer = new OutfitRenderer(assets);
    }

    Rendered render(Context ctx, boolean png) throws IOException {
        Params params = parse(ctx, png);
        String key = params.key();
        synchronized (cache) {
            Rendered cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        List<OutfitRenderer.Frame> frames = renderer.frames(params.request(), params.animation(), params.frameMs());
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        if (png) {
            ImageIO.write(frames.get(0).image(), "png", out);
        } else {
            GifWriter.write(frames, out);
        }
        Rendered rendered = new Rendered(out.toByteArray(), png ? "image/png" : "image/gif");
        synchronized (cache) {
            cache.put(key, rendered);
        }
        return rendered;
    }

    private Params parse(Context ctx, boolean png) {
        // npc=/monster= trazem o outfit completo do personagem; parâmetros explícitos sobrescrevem.
        GameCharacter preset = character(ctx);
        String sex = bool(ctx, "female") ? "female" : "male";
        int looktype = preset != null && isBlank(ctx.queryParam("looktype")) ? preset.looktype()
                : looktype(required(ctx, "looktype"), null, sex, "looktype");
        OutfitInfo info = assets.outfit(looktype);
        if (info == null || info.idle() == null) {
            throw new BadRequestResponse("Looktype não existe no cliente: " + looktype);
        }

        int addons = intParam(ctx, "addons", preset != null ? preset.addons() : 0, 0, 3);
        String mountParam = ctx.queryParam("mount");
        int mount = isBlank(mountParam) ? (preset != null ? preset.mount() : 0)
                : looktype(mountParam, LooktypeKind.MOUNT, null, "mount");
        if (mount > 0 && assets.outfit(mount) == null) {
            throw new BadRequestResponse("Montaria não existe no cliente: " + mount);
        }

        TibiaColor head = color(ctx, "head", preset != null ? preset.head() : DEFAULT_COLORS[0]);
        TibiaColor body = color(ctx, "body", preset != null ? preset.body() : DEFAULT_COLORS[1]);
        TibiaColor legs = color(ctx, "legs", preset != null ? preset.legs() : DEFAULT_COLORS[2]);
        TibiaColor feet = color(ctx, "feet", preset != null ? preset.feet() : DEFAULT_COLORS[3]);

        AnimationType animation = bool(ctx, "idle") || png ? AnimationType.IDLE : AnimationType.MOVING;
        int frameMs = animation == AnimationType.MOVING ? intParam(ctx, "frameMs", 100, 20, 2000) : 0;

        OutfitRequest request = OutfitRequest.of(looktype)
                .withAddons(addons)
                .withColors(head, body, legs, feet)
                .withMount(mount)
                .withDirection(direction(ctx.queryParam("direction")));
        return new Params(request, animation, frameMs, png);
    }

    private GameCharacter character(Context ctx) {
        String npc = ctx.queryParam("npc");
        String monster = ctx.queryParam("monster");
        if (!isBlank(npc)) {
            checkLength(npc, "npc");
            return metadata.character(npc, GameCharacter.Kind.NPC)
                    .orElseThrow(() -> new BadRequestResponse("NPC desconhecido: " + npc));
        }
        if (!isBlank(monster)) {
            checkLength(monster, "monster");
            return metadata.character(monster, GameCharacter.Kind.MONSTER, GameCharacter.Kind.BOSS)
                    .orElseThrow(() -> new BadRequestResponse("Monstro desconhecido: " + monster));
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void checkLength(String value, String param) {
        if (value.length() > 64) {
            throw new BadRequestResponse(param + " muito longo");
        }
    }

    /** Personagens para a interface, com o que o looktype deles suporta. */
    List<Map<String, Object>> characters(String kind, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean npcs = "npc".equalsIgnoreCase(kind);
        List<Map<String, Object>> result = new ArrayList<>();
        for (GameCharacter c : metadata.characters()) {
            if (kind != null && !kind.isBlank() && npcs != (c.kind() == GameCharacter.Kind.NPC)) {
                continue;
            }
            if (!q.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            OutfitInfo info = assets.outfit(c.looktype());
            if (info == null || info.idle() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", c.name());
            row.put("kind", c.kind().name().toLowerCase(Locale.ROOT));
            row.put("looktype", c.looktype());
            row.put("head", c.head());
            row.put("body", c.body());
            row.put("legs", c.legs());
            row.put("feet", c.feet());
            row.put("addons", c.addons());
            row.put("mount", c.mount());
            row.put("addonCount", info.addonCount());
            row.put("mountable", info.mountable());
            row.put("colorable", info.colorable());
            result.add(row);
        }
        return result;
    }

    private int looktype(String value, LooktypeKind kind, String sex, String param) {
        if (value.length() > 64) {
            throw new BadRequestResponse(param + " muito longo");
        }
        Optional<Integer> resolved = metadata.resolve(value, kind, sex);
        return resolved.orElseThrow(() -> new BadRequestResponse(param + " desconhecido: " + value));
    }

    private static Direction direction(String value) {
        if (value == null || value.isBlank()) {
            return Direction.SOUTH;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "0", "n", "north" -> Direction.NORTH;
            case "1", "e", "east" -> Direction.EAST;
            case "2", "s", "south" -> Direction.SOUTH;
            case "3", "w", "west" -> Direction.WEST;
            default -> throw new BadRequestResponse("direction inválida: " + value);
        };
    }

    private static TibiaColor color(Context ctx, String param, int fallback) {
        String value = ctx.queryParam(param);
        if (value == null || value.isBlank()) {
            return TibiaColor.palette(fallback);
        }
        try {
            return TibiaColor.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(param + ": " + e.getMessage());
        }
    }

    private static String required(Context ctx, String param) {
        String value = ctx.queryParam(param);
        if (value == null || value.isBlank()) {
            throw new BadRequestResponse("Parâmetro obrigatório: " + param);
        }
        return value;
    }

    private static int intParam(Context ctx, String param, int fallback, int min, int max) {
        String value = ctx.queryParam(param);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(value.trim());
            if (v < min || v > max) {
                throw new BadRequestResponse(param + " deve estar entre " + min + " e " + max);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new BadRequestResponse(param + " deve ser um número");
        }
    }

    private static boolean bool(Context ctx, String param) {
        String value = ctx.queryParam(param);
        return value != null && (value.isEmpty() || value.equals("1") || value.equalsIgnoreCase("true"));
    }

    /** Lista para a interface: looktypes com nome e capacidades, filtrados. */
    List<Map<String, Object>> looktypes(String kind, String sex, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (OutfitInfo info : assets.outfits()) {
            if (info.idle() == null) {
                continue;
            }
            Optional<LooktypeMeta> meta = metadata.get(info.looktype());
            if (!matchesKind(meta, kind)) {
                continue;
            }
            if (sex != null && !sex.isBlank() && !meta.map(m -> sex.equals(m.sex())).orElse(false)) {
                continue;
            }
            if (!q.isEmpty() && !meta.map(m -> m.name().toLowerCase(Locale.ROOT).contains(q)).orElse(false)
                    && !Integer.toString(info.looktype()).equals(q)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("looktype", info.looktype());
            row.put("name", meta.map(LooktypeMeta::name).orElse(null));
            row.put("kind", meta.map(m -> m.kind().name().toLowerCase(Locale.ROOT)).orElse(null));
            meta.map(LooktypeMeta::sex).ifPresent(s -> row.put("sex", s));
            row.put("addons", info.addonCount());
            row.put("mountable", info.mountable());
            row.put("colorable", info.colorable());
            result.add(row);
        }
        result.sort((a, b) -> {
            String na = (String) a.get("name");
            String nb = (String) b.get("name");
            if (na == null || nb == null) {
                return na == null && nb == null
                        ? Integer.compare((int) a.get("looktype"), (int) b.get("looktype")) : (na == null ? 1 : -1);
            }
            return na.compareToIgnoreCase(nb);
        });
        return result;
    }

    private static boolean matchesKind(Optional<LooktypeMeta> meta, String kind) {
        if (kind == null || kind.isBlank() || kind.equals("all")) {
            return true;
        }
        if (kind.equals("unknown")) {
            return meta.isEmpty();
        }
        return meta.map(m -> m.kind().name().equalsIgnoreCase(kind)).orElse(false);
    }

    List<String> palette() {
        List<String> colors = new ArrayList<>(TibiaColor.PALETTE_SIZE);
        for (int i = 0; i < TibiaColor.PALETTE_SIZE; i++) {
            colors.add(TibiaColor.palette(i).toHex());
        }
        return colors;
    }

    Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("looktypes", assets.outfits().size());
        info.put("metadataSource", metadata.source());
        return info;
    }
}
