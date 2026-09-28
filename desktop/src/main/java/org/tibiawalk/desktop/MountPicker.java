package org.tibiawalk.desktop;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Seletor de montaria em grade, com miniatura de cada uma e busca por nome. */
final class MountPicker {

    /** Uma montaria disponível; looktype 0 = sem montaria. */
    record Option(int looktype, String name) {
    }

    static final int THUMB = 64;

    private final List<Option> options;
    private final IntFunction<BufferedImage> render;
    private final Map<Integer, Icon> thumbnails = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> requested = ConcurrentHashMap.newKeySet();
    /** Quem repintar quando uma miniatura fica pronta (a grade aberta, o botão da janela). */
    private volatile Runnable onThumbnail = () -> { };
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mount-thumbnails");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param render renderiza a montaria parada (virada para o sul) pelo looktype; roda fora da EDT
     */
    MountPicker(List<Option> options, IntFunction<BufferedImage> render) {
        this.options = options;
        this.render = render;
    }

    /** Miniatura já pronta, ou null se ainda não foi gerada. */
    Icon thumbnail(int looktype) {
        return thumbnails.get(looktype);
    }

    void onThumbnail(Runnable listener) {
        onThumbnail = listener;
    }

    /** Gera em segundo plano as miniaturas que faltam; cada uma pronta avisa o listener atual. */
    void preload() {
        for (Option option : options) {
            if (option.looktype() == 0 || !requested.add(option.looktype())) {
                continue;
            }
            loader.submit(() -> {
                try {
                    thumbnails.put(option.looktype(), new ImageIcon(fit(render.apply(option.looktype()))));
                } catch (RuntimeException e) {
                    thumbnails.put(option.looktype(), new ImageIcon(new BufferedImage(THUMB, THUMB,
                            BufferedImage.TYPE_INT_ARGB)));
                }
                SwingUtilities.invokeLater(() -> onThumbnail.run());
            });
        }
    }

    /** Abre o seletor; chama onPick com o looktype escolhido (0 = sem montaria). */
    void open(Component parent, int current, IntConsumer onPick) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Escolher montaria", JDialog.ModalityType.APPLICATION_MODAL);

        DefaultListModel<Option> model = new DefaultListModel<>();
        JList<Option> grid = new JList<>(model);
        grid.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        grid.setVisibleRowCount(-1);
        grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grid.setCellRenderer(new Cell());
        grid.setFixedCellWidth(THUMB + 44);
        grid.setFixedCellHeight(THUMB + 34);

        JTextField search = new JTextField();
        search.setToolTipText("Buscar montaria");
        Runnable filter = () -> {
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            model.clear();
            for (Option option : options) {
                if (q.isEmpty() || option.name().toLowerCase(Locale.ROOT).contains(q)
                        || Integer.toString(option.looktype()).equals(q)) {
                    model.addElement(option);
                }
            }
            if (!model.isEmpty()) {
                grid.setSelectedIndex(0); // Enter ou "Usar" já pegam o primeiro resultado
            }
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter.run();
            }
        });
        filter.run();
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).looktype() == current) {
                grid.setSelectedIndex(i);
                grid.ensureIndexIsVisible(i);
            }
        }

        Runnable confirm = () -> {
            Option chosen = grid.getSelectedValue();
            if (chosen != null) {
                onPick.accept(chosen.looktype());
                dialog.dispose();
            }
        };
        grid.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    confirm.run();
                }
            }
        });
        grid.registerKeyboardAction(e -> confirm.run(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

        JButton ok = new JButton("Usar");
        ok.addActionListener(e -> confirm.run());
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(search, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setPreferredSize(new Dimension(6 * (THUMB + 44) + 24, 480));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);

        // Repinta a grade conforme as miniaturas ficam prontas.
        Runnable previous = onThumbnail;
        onThumbnail(() -> {
            grid.repaint();
            previous.run();
        });
        preload();

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        search.requestFocusInWindow();
        dialog.setVisible(true);
        onThumbnail(previous);
    }

    /** Reduz ou amplia (sem suavizar) para caber no quadrado da miniatura. */
    static BufferedImage fit(BufferedImage image) {
        double scale = Math.min((double) THUMB / image.getWidth(), (double) THUMB / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage out = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, (THUMB - w) / 2, (THUMB - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    private final class Cell extends JLabel implements ListCellRenderer<Option> {
        Cell() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Option> list, Option value, int index,
                                                      boolean selected, boolean focus) {
            setText("<html><center>" + value.name() + "</center></html>");
            setIcon(value.looktype() == 0 ? null : thumbnail(value.looktype()));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            setToolTipText(value.looktype() == 0 ? null : value.name() + " (" + value.looktype() + ")");
            return this;
        }
    }
}
