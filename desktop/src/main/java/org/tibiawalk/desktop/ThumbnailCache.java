package org.tibiawalk.desktop;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Miniaturas geradas sob demanda numa thread de fundo. A fila é LIFO: ao rolar a lista, o que acabou
 * de aparecer na tela é renderizado antes do que já passou.
 */
final class ThumbnailCache<K> {

    private final int size;
    private final Icon placeholder;
    private final Map<K, Icon> ready = new ConcurrentHashMap<>();
    private final Map<K, Boolean> requested = new ConcurrentHashMap<>();
    private final Runnable onReady;
    private final ThreadPoolExecutor executor;

    ThumbnailCache(int size, Runnable onReady) {
        this.size = size;
        this.onReady = onReady;
        this.placeholder = new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
        LinkedBlockingDeque<Runnable> stack = new LinkedBlockingDeque<>() {
            @Override
            public boolean offer(Runnable r) {
                return offerFirst(r);
            }
        };
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, stack, r -> {
            Thread t = new Thread(r, "thumbnails");
            t.setDaemon(true);
            return t;
        });
    }

    /** A miniatura pronta, ou um espaço vazio do mesmo tamanho enquanto ela é gerada. */
    Icon get(K key, Supplier<BufferedImage> render) {
        Icon icon = ready.get(key);
        if (icon != null) {
            return icon;
        }
        if (requested.putIfAbsent(key, Boolean.TRUE) == null) {
            executor.execute(() -> {
                Icon made;
                try {
                    made = new ImageIcon(fit(render.get()));
                } catch (RuntimeException e) {
                    made = placeholder;
                }
                ready.put(key, made);
                SwingUtilities.invokeLater(onReady);
            });
        }
        return placeholder;
    }

    /** Corta a área transparente e encaixa no quadrado, suavizando só quando precisa reduzir. */
    private BufferedImage fit(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        if (maxX < 0) {
            return out;
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        double scale = Math.min(1.0, Math.min((double) size / w, (double) size / h));
        int dw = Math.max(1, (int) Math.round(w * scale));
        int dh = Math.max(1, (int) Math.round(h * scale));
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale < 1
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, (size - dw) / 2, (size - dh) / 2, (size - dw) / 2 + dw, (size - dh) / 2 + dh,
                minX, minY, maxX + 1, maxY + 1, null);
        g.dispose();
        return out;
    }
}
