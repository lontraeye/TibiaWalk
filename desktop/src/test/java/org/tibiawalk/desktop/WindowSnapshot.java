package org.tibiawalk.desktop;

import org.tibiawalk.core.assets.ClientAssets;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Depuração: abre a janela e executa ações, para conferir a interface sem clicar à mão.
 * Args: pastaAssets ação...
 * Ações: click:&lt;texto do botão&gt;, type:&lt;texto na busca&gt;, select:&lt;categoria&gt;, wait:&lt;ms&gt;,
 * shot:&lt;arquivo.png&gt; (janela principal), dialog:&lt;arquivo.png&gt; (diálogo aberto).
 */
public final class WindowSnapshot {

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        ClientAssets assets = ClientAssets.open(Path.of(args[0]));
        AtomicReference<MainWindow> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow(assets, () -> { });
            window.setVisible(true);
            ref.set(window);
        });
        Thread.sleep(1500);

        for (int i = 1; i < args.length; i++) {
            String action = args[i];
            String value = action.substring(action.indexOf(':') + 1);
            if (action.startsWith("click:")) {
                // invokeLater: o clique pode abrir um diálogo modal, que bloquearia um invokeAndWait
                SwingUtilities.invokeLater(() -> {
                    AbstractButton button = find(topWindow(ref.get()), AbstractButton.class, value);
                    if (button == null) {
                        System.err.println("Botão não encontrado: " + value);
                    } else {
                        button.doClick();
                    }
                });
                Thread.sleep(800);
            } else if (action.startsWith("type:")) {
                SwingUtilities.invokeAndWait(() -> {
                    JTextField field = find(topWindow(ref.get()), JTextField.class, null);
                    if (field != null) {
                        field.setText(value);
                    }
                });
                Thread.sleep(500);
            } else if (action.startsWith("select:")) {
                // escolhe o item do combo de categoria cujo texto contém o valor
                SwingUtilities.invokeAndWait(() -> {
                    javax.swing.JComboBox<?> combo = find(ref.get().getContentPane(), javax.swing.JComboBox.class, null);
                    for (int k = 0; k < combo.getItemCount(); k++) {
                        if (String.valueOf(combo.getItemAt(k)).contains(value)) {
                            combo.setSelectedIndex(k);
                        }
                    }
                });
                Thread.sleep(800);
            } else if (action.startsWith("wait:")) {
                Thread.sleep(Long.parseLong(value));
            } else if (action.startsWith("shot:")) {
                capture(ref.get(), value);
            } else if (action.startsWith("dialog:")) {
                capture(topWindow(ref.get()), value);
            }
        }
        System.exit(0);
    }

    /** O diálogo aberto mais recente, ou a janela principal. */
    private static Window topWindow(Window main) {
        Window top = main;
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing()) {
                top = w;
            }
        }
        return top;
    }

    private static void capture(Window window, String file) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = ((RootPaneContainer) window).getRootPane();
            BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
            root.paint(image.getGraphics());
            try {
                ImageIO.write(image, "png", new File(file));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static <T extends Component> T find(Container root, Class<T> type, String text) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && (text == null
                    || (c instanceof AbstractButton b && b.getText() != null && b.getText().contains(text)))) {
                return type.cast(c);
            }
            if (c instanceof Container child) {
                T found = find(child, type, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
