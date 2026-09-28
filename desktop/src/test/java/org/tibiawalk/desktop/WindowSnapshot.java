package org.tibiawalk.desktop;

import org.tibiawalk.core.assets.ClientAssets;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Depuração: abre a janela, opcionalmente clica em "Aleatório", e salva um print. Args: assets saida.png [cliques] */
public final class WindowSnapshot {

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        ClientAssets assets = ClientAssets.open(Path.of(args[0]));
        int clicks = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        AtomicReference<MainWindow> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow(assets, () -> { });
            window.setVisible(true);
            ref.set(window);
        });
        Thread.sleep(1500);
        for (int i = 0; i < clicks; i++) {
            SwingUtilities.invokeAndWait(() -> find(ref.get().getContentPane(), "Aleatório").doClick());
            Thread.sleep(800);
        }
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = ref.get();
            BufferedImage image = new BufferedImage(window.getRootPane().getWidth(), window.getRootPane().getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            window.getRootPane().paint(image.getGraphics());
            try {
                ImageIO.write(image, "png", new File(args[1]));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            window.dispose();
        });
        System.exit(0);
    }

    private static JButton find(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                return b;
            }
            if (c instanceof Container child) {
                JButton found = find(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
