package org.tibiawalk.desktop;

import org.tibiawalk.core.assets.ClientAssets;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.nio.file.Path;
import java.util.Optional;

/** Ponto de entrada do app desktop. */
public final class DesktopApp {

    private MainWindow window;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fica com o visual padrão do Swing
        }
        SwingUtilities.invokeLater(() -> {
            DesktopApp app = new DesktopApp();
            Optional<Path> found = args.length > 0 ? AssetsLocator.normalize(Path.of(args[0])) : AssetsLocator.find();
            found.ifPresentOrElse(app::open, app::chooseAssets);
        });
    }

    /** Pede a pasta ao usuário; aceita a pasta do launcher, de packages/Tibia ou a própria assets. */
    private void chooseAssets() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Onde está o cliente Tibia? (pasta de instalação ou a pasta assets)");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        while (true) {
            if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) {
                if (window == null) {
                    System.exit(0);
                }
                return;
            }
            Optional<Path> assets = AssetsLocator.normalize(chooser.getSelectedFile().toPath());
            if (assets.isPresent()) {
                open(assets.get());
                return;
            }
            JOptionPane.showMessageDialog(window,
                    "Não achei o catalog-content.json nessa pasta.\n"
                            + "Abra o jogo pelo launcher uma vez para ele baixar os arquivos e tente de novo.",
                    "Pasta inválida", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void open(Path assetsDir) {
        JWindow splash = new JWindow();
        JLabel label = new JLabel("Carregando assets do Tibia…", SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        splash.add(label);
        splash.pack();
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        new SwingWorker<ClientAssets, Void>() {
            @Override
            protected ClientAssets doInBackground() throws Exception {
                return ClientAssets.open(assetsDir);
            }

            @Override
            protected void done() {
                splash.dispose();
                try {
                    ClientAssets assets = get();
                    AssetsLocator.remember(assetsDir);
                    if (window != null) {
                        window.dispose();
                    }
                    window = new MainWindow(assets, DesktopApp.this::chooseAssets);
                    window.setVisible(true);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(window, "Não consegui abrir os assets:\n" + cause.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                    chooseAssets();
                }
            }
        }.execute();
    }
}
