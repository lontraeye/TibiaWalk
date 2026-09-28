package org.tibiawalk.core.render;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.proto.AppearancesProto.AppearanceFlags;
import org.tibiawalk.core.proto.AppearancesProto.SpriteInfo;
import org.tibiawalk.core.sprites.SpriteStore;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Monta o outfit (montaria, base, addons) e aplica as cores pelo template. */
public final class OutfitRenderer {

    /** Um frame pronto e quanto tempo ele fica na tela. */
    public record Frame(BufferedImage image, int durationMs) {
    }

    private static final int TILE = 32;

    private final ClientAssets assets;
    private final SpriteStore sprites;

    public OutfitRenderer(ClientAssets assets) {
        this(assets, new SpriteStore(assets.catalog()));
    }

    public OutfitRenderer(ClientAssets assets, SpriteStore sprites) {
        this.assets = assets;
        this.sprites = sprites;
    }

    /**
     * Todos os frames da animação, com o mesmo tamanho.
     *
     * @param frameDurationMs duração fixa por frame; 0 usa a duração do cliente
     */
    public List<Frame> frames(OutfitRequest request, AnimationType type, int frameDurationMs) {
        OutfitInfo outfit = requireOutfit(request.looktype());
        OutfitInfo mount = mountOf(request, outfit);

        OutfitInfo.Animation animation = animationOf(outfit, type);
        int count = animation.frames();
        if (mount != null) {
            count = Math.max(count, animationOf(mount, type).frames());
        }

        List<List<Piece>> pieces = new ArrayList<>(count);
        Rectangle bounds = null;
        for (int phase = 0; phase < count; phase++) {
            List<Piece> framePieces = pieces(request, outfit, mount, type, phase);
            pieces.add(framePieces);
            for (Piece piece : framePieces) {
                bounds = bounds == null ? piece.bounds() : bounds.union(piece.bounds());
            }
        }
        if (bounds == null) {
            bounds = new Rectangle(0, 0, TILE, TILE);
        }

        List<Frame> frames = new ArrayList<>(count);
        for (int phase = 0; phase < count; phase++) {
            BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            for (Piece piece : pieces.get(phase)) {
                g.drawImage(piece.image(), piece.x() - bounds.x, piece.y() - bounds.y, null);
            }
            g.dispose();
            int[] durations = animation.durationsMs();
            int duration = frameDurationMs > 0 ? frameDurationMs : durations[phase % durations.length];
            frames.add(new Frame(image, duration > 0 ? duration : 100));
        }
        return frames;
    }

    /** Um frame só: o primeiro frame parado. */
    public BufferedImage still(OutfitRequest request) {
        return frames(request, AnimationType.IDLE, 0).get(0).image();
    }

    private record Piece(BufferedImage image, int x, int y) {
        Rectangle bounds() {
            return new Rectangle(x, y, image.getWidth(), image.getHeight());
        }
    }

    private List<Piece> pieces(OutfitRequest request, OutfitInfo outfit, OutfitInfo mount,
                               AnimationType type, int phase) {
        int direction = request.direction().ordinal();
        List<Piece> pieces = new ArrayList<>();

        // Montado, o sprite z=1 do outfit já vem alinhado à montaria: ambos usam o shift dela.
        OutfitInfo anchor = mount != null ? mount : outfit;
        int z = 0;
        if (mount != null) {
            // montaria colorível usa as mesmas cores do outfit (por enquanto)
            add(pieces, request, mount, type, phase, direction, 0, 0, mount);
            z = 1;
        }

        AppearanceFlags flags = assets.appearance(outfit.looktype()).getFlags();
        boolean addonsBehind = switch (request.direction()) {
            case NORTH -> flags.getReverseAddonsNorth();
            case EAST -> flags.getReverseAddonsEast();
            case SOUTH -> flags.getReverseAddonsSouth();
            case WEST -> flags.getReverseAddonsWest();
        };

        List<Integer> addonLayers = new ArrayList<>();
        for (int y = 1; y < outfit.addonLayers(); y++) {
            if ((request.addons() & (1 << (y - 1))) != 0) {
                addonLayers.add(y);
            }
        }

        if (!addonsBehind) {
            add(pieces, request, outfit, type, phase, direction, 0, z, anchor);
        }
        for (int y : addonLayers) {
            add(pieces, request, outfit, type, phase, direction, y, z, anchor);
        }
        if (addonsBehind) {
            add(pieces, request, outfit, type, phase, direction, 0, z, anchor);
        }
        return pieces;
    }

    private void add(List<Piece> pieces, OutfitRequest request, OutfitInfo thing, AnimationType type,
                     int phase, int x, int y, int z, OutfitInfo anchor) {
        OutfitInfo.Animation animation = animationOf(thing, type);
        SpriteInfo info = animation.group().getSpriteInfo();
        int width = Math.max(1, info.getPatternWidth());
        int height = Math.max(1, info.getPatternHeight());
        int depth = Math.max(1, info.getPatternDepth());
        int layers = Math.max(1, info.getLayers());
        int p = phase % animation.frames();

        int base = ((((p * depth + Math.min(z, depth - 1)) * height + Math.min(y, height - 1))
                * width + Math.min(x, width - 1)) * layers);
        BufferedImage sprite = sprites.sprite(info.getSpriteId(base));
        if (sprite == null) {
            return;
        }
        if (layers > 1) {
            BufferedImage template = sprites.sprite(info.getSpriteId(base + 1));
            if (template != null) {
                sprite = colorize(sprite, template, request);
            }
        }

        // Âncora no canto inferior direito do tile, deslocada pelo "shift" do cliente.
        AppearanceFlags flags = assets.appearance(anchor.looktype()).getFlags();
        int left = TILE - sprite.getWidth() - flags.getShift().getX();
        int top = TILE - sprite.getHeight() - flags.getShift().getY();
        pieces.add(new Piece(sprite, left, top));
    }

    /** Multiplica o pixel base pela cor da parte indicada na máscara. */
    static BufferedImage colorize(BufferedImage sprite, BufferedImage template, OutfitRequest request) {
        int w = sprite.getWidth();
        int h = sprite.getHeight();
        int[] px = sprite.getRGB(0, 0, w, h, null, 0, w);
        int[] mask = template.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < px.length; i++) {
            int m = mask[i];
            if ((m >>> 24) == 0) {
                continue;
            }
            boolean r = ((m >> 16) & 0xFF) > 127;
            boolean g = ((m >> 8) & 0xFF) > 127;
            boolean b = (m & 0xFF) > 127;
            TibiaColor color;
            if (r && g && !b) {
                color = request.head();
            } else if (r && !g && !b) {
                color = request.body();
            } else if (!r && g && !b) {
                color = request.legs();
            } else if (!r && !g && b) {
                color = request.feet();
            } else {
                continue;
            }
            int p = px[i];
            int red = ((p >> 16) & 0xFF) * color.red() / 255;
            int green = ((p >> 8) & 0xFF) * color.green() / 255;
            int blue = (p & 0xFF) * color.blue() / 255;
            px[i] = (p & 0xFF000000) | red << 16 | green << 8 | blue;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private OutfitInfo requireOutfit(int looktype) {
        OutfitInfo outfit = assets.outfit(looktype);
        if (outfit == null || outfit.idle() == null) {
            throw new IllegalArgumentException("Looktype não existe no cliente: " + looktype);
        }
        return outfit;
    }

    /** A montaria só vale se o outfit tiver versão montada. */
    private OutfitInfo mountOf(OutfitRequest request, OutfitInfo outfit) {
        if (request.mount() <= 0 || !outfit.mountable()) {
            return null;
        }
        return requireOutfit(request.mount());
    }

    private static OutfitInfo.Animation animationOf(OutfitInfo thing, AnimationType type) {
        if (type == AnimationType.MOVING && thing.moving() != null) {
            return thing.moving();
        }
        return thing.idle();
    }
}
