package org.tibiawalk.desktop;

import org.tibiawalk.core.render.TibiaColor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.function.Consumer;

/** Botão que mostra a cor atual e abre a paleta do jogo (19 x 7), com opção de hex livre. */
final class ColorButton extends JButton {

    private static final int COLUMNS = 19;

    private TibiaColor color = TibiaColor.WHITE;
    private final Consumer<TibiaColor> onChange;

    ColorButton(String label, Consumer<TibiaColor> onChange) {
        super(label);
        this.onChange = onChange;
        setHorizontalAlignment(LEFT);
        setIconTextGap(8);
        setIcon(new javax.swing.Icon() {
            @Override
            public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
                g.setColor(new Color(color.rgb()));
                g.fillRect(x, y, 18, 18);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, y, 17, 17);
            }

            @Override
            public int getIconWidth() {
                return 18;
            }

            @Override
            public int getIconHeight() {
                return 18;
            }
        });
        addActionListener(e -> openPalette());
    }

    TibiaColor color() {
        return color;
    }

    void setColor(TibiaColor newColor) {
        color = newColor;
        setToolTipText("#" + newColor.toHex());
        repaint();
    }

    private void openPalette() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, getText(), JDialog.ModalityType.APPLICATION_MODAL);

        JPanel grid = new JPanel(new GridLayout(0, COLUMNS, 2, 2));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (int i = 0; i < TibiaColor.PALETTE_SIZE; i++) {
            TibiaColor swatch = TibiaColor.palette(i);
            JButton cell = new JButton();
            cell.setPreferredSize(new Dimension(20, 20));
            cell.setBackground(new Color(swatch.rgb()));
            cell.setContentAreaFilled(false);
            cell.setOpaque(true);
            cell.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            cell.setToolTipText(i + "  (#" + swatch.toHex() + ")");
            cell.addActionListener(e -> {
                pick(swatch);
                dialog.dispose();
            });
            grid.add(cell);
        }

        JButton custom = new JButton("Cor livre (hex)…");
        custom.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(dialog, getText(), new Color(color.rgb()));
            if (chosen != null) {
                pick(new TibiaColor(chosen.getRGB() & 0xFFFFFF));
                dialog.dispose();
            }
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(custom);

        JComponent content = (JComponent) dialog.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(grid, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void pick(TibiaColor newColor) {
        setColor(newColor);
        onChange.accept(newColor);
    }
}
