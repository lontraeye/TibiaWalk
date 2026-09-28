package org.tibiawalk.desktop;

import org.tibiawalk.core.render.OutfitRenderer;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** Mostra os frames em loop, ampliados sem suavização (pixel art), sobre um fundo quadriculado. */
final class PreviewPanel extends JComponent {

    private static final int CHECKER = 16;
    private static final Color LIGHT = new Color(0x5a, 0x7a, 0x4a);
    private static final Color DARK = new Color(0x4d, 0x6b, 0x3f);

    private List<OutfitRenderer.Frame> frames = List.of();
    private int current;
    private String message = "Carregando…";
    private final Timer timer;

    PreviewPanel() {
        setPreferredSize(new Dimension(320, 320));
        timer = new Timer(100, e -> advance());
        timer.setRepeats(false);
    }

    void show(List<OutfitRenderer.Frame> newFrames) {
        timer.stop();
        frames = newFrames;
        current = 0;
        message = null;
        repaint();
        scheduleNext();
    }

    void showMessage(String text) {
        timer.stop();
        frames = List.of();
        message = text;
        repaint();
    }

    private void advance() {
        if (frames.isEmpty()) {
            return;
        }
        current = (current + 1) % frames.size();
        repaint();
        scheduleNext();
    }

    private void scheduleNext() {
        if (frames.size() > 1) {
            timer.setInitialDelay(frames.get(current).durationMs());
            timer.restart();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        for (int y = 0; y < getHeight(); y += CHECKER) {
            for (int x = 0; x < getWidth(); x += CHECKER) {
                g.setColor(((x + y) / CHECKER) % 2 == 0 ? LIGHT : DARK);
                g.fillRect(x, y, CHECKER, CHECKER);
            }
        }

        if (!frames.isEmpty()) {
            BufferedImage image = frames.get(current).image();
            int scale = Math.max(1, Math.min(getWidth() / image.getWidth(), getHeight() / image.getHeight()));
            int w = image.getWidth() * scale;
            int h = image.getHeight() * scale;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        } else if (message != null) {
            g.setColor(Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int textWidth = g.getFontMetrics().stringWidth(message);
            g.drawString(message, (getWidth() - textWidth) / 2, getHeight() / 2);
        }
        g.dispose();
    }
}
