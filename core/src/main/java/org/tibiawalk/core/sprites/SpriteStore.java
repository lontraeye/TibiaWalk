package org.tibiawalk.core.sprites;

import org.tibiawalk.core.assets.AssetCatalog;
import org.tibiawalk.core.assets.SpriteSheet;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entrega sprites pelo ID, decodificando as folhas sob demanda e mantendo as mais recentes em cache.
 *
 * <p>Seguro para várias threads: folhas diferentes são decodificadas em paralelo, e duas threads pedindo
 * a mesma folha esperam uma única decodificação.
 */
public final class SpriteStore {

    private static final int DEFAULT_CACHED_SHEETS = 64;

    private final AssetCatalog catalog;
    private final Map<String, BufferedImage> sheetCache;
    private final Map<String, CompletableFuture<BufferedImage>> decoding = new ConcurrentHashMap<>();

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

    public BufferedImage sheet(SpriteSheet sheet) {
        String file = sheet.file();
        synchronized (sheetCache) {
            BufferedImage cached = sheetCache.get(file);
            if (cached != null) {
                return cached;
            }
        }

        // Fora da trava do cache: só quem criou o future decodifica; os outros esperam o mesmo resultado.
        CompletableFuture<BufferedImage> mine = new CompletableFuture<>();
        CompletableFuture<BufferedImage> pending = decoding.putIfAbsent(file, mine);
        if (pending != null) {
            return join(pending);
        }
        try {
            BufferedImage image = SpriteSheetDecoder.decode(catalog.assetsDir().resolve(file));
            synchronized (sheetCache) {
                sheetCache.put(file, image);
            }
            mine.complete(image);
            return image;
        } catch (IOException | RuntimeException e) {
            mine.completeExceptionally(e);
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        } finally {
            decoding.remove(file, mine);
        }
    }

    private static BufferedImage join(CompletableFuture<BufferedImage> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw new UncheckedIOException(io);
            }
            throw cause instanceof RuntimeException r ? r : e;
        }
    }
}
