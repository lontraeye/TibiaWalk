package org.tibiawalk.core.sprites;

import org.tibiawalk.core.assets.AssetCatalog;
import org.tibiawalk.core.assets.SpriteSheet;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Entrega sprites pelo ID, decodificando as folhas sob demanda e mantendo as mais recentes em cache. */
public final class SpriteStore {

    private static final int DEFAULT_CACHED_SHEETS = 64;

    private final AssetCatalog catalog;
    private final Map<String, BufferedImage> sheetCache;

    public SpriteStore(AssetCatalog catalog) {
        this(catalog, DEFAULT_CACHED_SHEETS);
    }

    public SpriteStore(AssetCatalog catalog, int cachedSheets) {
        this.catalog = catalog;
        this.sheetCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                return size() > cachedSheets;
            }
        };
    }

    /** O sprite como imagem ARGB (32x32, 32x64, 64x32 ou 64x64); null se o ID não existir. */
    public BufferedImage sprite(int spriteId) {
        SpriteSheet sheet = catalog.findSheet(spriteId);
        if (sheet == null) {
            return null;
        }
        BufferedImage image = sheet(sheet);
        int w = sheet.spriteWidth();
        int h = sheet.spriteHeight();
        int columns = image.getWidth() / w;
        int index = spriteId - sheet.firstSpriteId();
        int x = (index % columns) * w;
        int y = (index / columns) * h;

        BufferedImage sprite = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        sprite.setRGB(0, 0, w, h, image.getRGB(x, y, w, h, null, 0, w), 0, w);
        return sprite;
    }

    public synchronized BufferedImage sheet(SpriteSheet sheet) {
        return sheetCache.computeIfAbsent(sheet.file(), file -> {
            try {
                return SpriteSheetDecoder.decode(catalog.assetsDir().resolve(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
