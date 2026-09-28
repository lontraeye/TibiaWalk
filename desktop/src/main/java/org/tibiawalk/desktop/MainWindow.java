package org.tibiawalk.desktop;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.metadata.LooktypeKind;
import org.tibiawalk.core.metadata.LooktypeMeta;
import org.tibiawalk.core.metadata.Metadata;
import org.tibiawalk.core.render.AnimationType;
import org.tibiawalk.core.render.Direction;
import org.tibiawalk.core.render.GifWriter;
import org.tibiawalk.core.render.OutfitRenderer;
import org.tibiawalk.core.render.OutfitRequest;
import org.tibiawalk.core.render.TibiaColor;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

/** Janela principal: lista de looktypes, preview animado e opções do outfit. */
final class MainWindow extends JFrame {

    /** Um item da lista (ou da caixa de montarias). */
    private record Entry(OutfitInfo info, Optional<LooktypeMeta> meta) {
        int looktype() {
            return info.looktype();
        }

        String name() {
            return meta.map(LooktypeMeta::name).orElse("");
        }

        @Override
        public String toString() {
            if (meta.isEmpty()) {
                return "#" + looktype();
            }
            String sex = meta.get().sex() == null ? "" : ("female".equals(meta.get().sex()) ? " ♀" : " ♂");
            return name() + sex + "  (" + looktype() + ")";
        }
    }

    private enum Filter {
        PLAYERS("Outfits de player", e -> kind(e, LooktypeKind.PLAYER)),
        MOUNTS("Montarias", e -> kind(e, LooktypeKind.MOUNT)),
        CREATURES("Criaturas", e -> kind(e, LooktypeKind.CREATURE)),
        UNKNOWN("Sem nome", e -> e.meta().isEmpty()),
        ALL("Todos", e -> true);

        final String label;
        final Predicate<Entry> test;

        Filter(String label, Predicate<Entry> test) {
            this.label = label;
            this.test = test;
        }

        @Override
        public String toString() {
            return label;
        }

        private static boolean kind(Entry e, LooktypeKind kind) {
            return e.meta().map(m -> m.kind() == kind).orElse(false);
        }
    }

    private static final String[] DIRECTION_LABELS = {"Norte", "Leste", "Sul", "Oeste"};
    private static final Entry NO_MOUNT = null;

    private final OutfitRenderer renderer;
    private final List<Entry> entries = new ArrayList<>();
    private final Random random = new Random();

    private final JComboBox<Filter> filter = new JComboBox<>(Filter.values());
    private final JTextField search = new JTextField();
    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(listModel);

    private final PreviewPanel preview = new PreviewPanel();
    private final JLabel status = new JLabel(" ");

    private final JCheckBox addon1 = new JCheckBox("Addon 1");
    private final JCheckBox addon2 = new JCheckBox("Addon 2");
    private final ColorButton head = new ColorButton("Cabeça", c -> refresh());
    private final ColorButton body = new ColorButton("Corpo", c -> refresh());
    private final ColorButton legs = new ColorButton("Pernas", c -> refresh());
    private final ColorButton feet = new ColorButton("Pés", c -> refresh());
    private final JComboBox<Entry> mount = new JComboBox<>();
    private final Map<Direction, JRadioButton> directions = new EnumMap<>(Direction.class);
    private final JCheckBox walking = new JCheckBox("Andando", true);
    private final JSlider speed = new JSlider(40, 400, 100);

    private List<OutfitRenderer.Frame> currentFrames = List.of();
    private int renderGeneration;
    private boolean adjusting;

    MainWindow(ClientAssets assets, Runnable chooseAssets) {
        super("TibiaWalk");
        this.renderer = new OutfitRenderer(assets);
        Metadata metadata = Metadata.bundled();
        for (OutfitInfo info : assets.outfits()) {
            if (info.idle() != null) {
                entries.add(new Entry(info, metadata.get(info.looktype())));
            }
        }

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(menu(chooseAssets));

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(buildListPanel(), BorderLayout.WEST);
        root.add(preview, BorderLayout.CENTER);
        root.add(buildControls(), BorderLayout.EAST);
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);

        head.setColor(TibiaColor.palette(78));
        body.setColor(TibiaColor.palette(69));
        legs.setColor(TibiaColor.palette(58));
        feet.setColor(TibiaColor.palette(76));
        fillMounts();
        applyFilter();

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private JMenuBar menu(Runnable chooseAssets) {
        JMenu file = new JMenu("Arquivo");
        JMenuItem folder = new JMenuItem("Escolher pasta do cliente…");
        folder.addActionListener(e -> chooseAssets.run());
        JMenuItem quit = new JMenuItem("Sair");
        quit.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        file.add(folder);
        file.addSeparator();
        file.add(quit);
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        return bar;
    }

    private JComponent buildListPanel() {
        filter.addActionListener(e -> applyFilter());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        search.setToolTipText("Buscar por nome ou número");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onOutfitSelected();
            }
        });

        JPanel top = new JPanel(new GridLayout(0, 1, 4, 4));
        top.add(filter);
        top.add(search);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(260, 420));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildControls() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        addon1.addActionListener(e -> refresh());
        addon2.addActionListener(e -> refresh());
        panel.add(section("Addons", addon1, addon2));

        panel.add(section("Cores", head, body, legs, feet));

        mount.addActionListener(e -> refresh());
        mount.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean selected, boolean focus) {
                return super.getListCellRendererComponent(l, value == null ? "Sem montaria" : value,
                        index, selected, focus);
            }
        });
        panel.add(section("Montaria", mount));

        ButtonGroup group = new ButtonGroup();
        JPanel dirs = new JPanel(new GridLayout(2, 2));
        for (Direction d : Direction.values()) {
            JRadioButton button = new JRadioButton(DIRECTION_LABELS[d.ordinal()], d == Direction.SOUTH);
            button.addActionListener(e -> refresh());
            group.add(button);
            directions.put(d, button);
            dirs.add(button);
        }
        panel.add(section("Direção", dirs));

        walking.addActionListener(e -> refresh());
        speed.setToolTipText("Duração de cada frame (ms)");
        speed.addChangeListener(e -> {
            if (!speed.getValueIsAdjusting()) {
                refresh();
            }
        });
        panel.add(section("Animação", walking, new JLabel("Velocidade (ms por frame)"), speed));

        JButton randomize = new JButton("Aleatório");
        randomize.addActionListener(e -> randomize());
        JButton saveGif = new JButton("Salvar GIF…");
        saveGif.addActionListener(e -> save(true));
        JButton savePng = new JButton("Salvar PNG…");
        savePng.addActionListener(e -> save(false));
        panel.add(section("", randomize, saveGif, savePng));

        panel.add(Box.createVerticalGlue());
        panel.setPreferredSize(new Dimension(220, panel.getPreferredSize().height));
        return panel;
    }

    private static JComponent section(String title, JComponent... children) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));
        if (!title.isEmpty()) {
            panel.setBorder(BorderFactory.createTitledBorder(title));
        }
        for (JComponent child : children) {
            panel.add(child);
        }
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private void fillMounts() {
        mount.addItem(NO_MOUNT);
        entries.stream()
                .filter(e -> Filter.MOUNTS.test.test(e))
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(mount::addItem);
    }

    private void applyFilter() {
        Entry previous = list.getSelectedValue();
        Filter selected = (Filter) filter.getSelectedItem();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);

        listModel.clear();
        entries.stream()
                .filter(selected.test)
                .filter(e -> query.isEmpty()
                        || e.name().toLowerCase(Locale.ROOT).contains(query)
                        || Integer.toString(e.looktype()).equals(query)
                        || e.meta().map(m -> m.aliases().stream()
                        .anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(query))).orElse(false))
                .sorted(selected == Filter.ALL || selected == Filter.UNKNOWN
                        ? Comparator.comparingInt(Entry::looktype)
                        : Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                                .thenComparingInt(Entry::looktype))
                .forEach(listModel::addElement);

        if (previous != null && listModel.contains(previous)) {
            list.setSelectedValue(previous, true);
        } else if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            preview.showMessage("Nada encontrado");
        }
    }

    private void onOutfitSelected() {
        Entry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        adjusting = true;
        OutfitInfo info = entry.info();
        addon1.setEnabled(info.addonCount() >= 1);
        addon2.setEnabled(info.addonCount() >= 2);
        if (!addon1.isEnabled()) {
            addon1.setSelected(false);
        }
        if (!addon2.isEnabled()) {
            addon2.setSelected(false);
        }
        mount.setEnabled(info.mountable());
        for (ColorButton button : List.of(head, body, legs, feet)) {
            button.setEnabled(info.colorable());
        }
        adjusting = false;
        refresh();
    }

    private OutfitRequest request() {
        Entry entry = list.getSelectedValue();
        int addons = (addon1.isSelected() ? 1 : 0) | (addon2.isSelected() ? 2 : 0);
        Entry mountEntry = (Entry) mount.getSelectedItem();
        Direction direction = directions.entrySet().stream()
                .filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse(Direction.SOUTH);
        return OutfitRequest.of(entry.looktype())
                .withAddons(addons)
                .withColors(head.color(), body.color(), legs.color(), feet.color())
                .withMount(mount.isEnabled() && mountEntry != null ? mountEntry.looktype() : 0)
                .withDirection(direction);
    }

    /** Renderiza fora da thread da interface; só o pedido mais recente é exibido. */
    private void refresh() {
        if (adjusting || list.getSelectedValue() == null) {
            return;
        }
        OutfitRequest request = request();
        AnimationType type = walking.isSelected() ? AnimationType.MOVING : AnimationType.IDLE;
        int frameMs = walking.isSelected() ? speed.getValue() : 0;
        int generation = ++renderGeneration;

        new SwingWorker<List<OutfitRenderer.Frame>, Void>() {
            @Override
            protected List<OutfitRenderer.Frame> doInBackground() {
                return renderer.frames(request, type, frameMs);
            }

            @Override
            protected void done() {
                if (generation != renderGeneration) {
                    return;
                }
                try {
                    currentFrames = get();
                    preview.show(currentFrames);
                    status.setText(describe(list.getSelectedValue(), request, currentFrames));
                } catch (Exception e) {
                    currentFrames = List.of();
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    preview.showMessage("Erro: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private static String describe(Entry entry, OutfitRequest request, List<OutfitRenderer.Frame> frames) {
        OutfitInfo info = entry.info();
        StringBuilder text = new StringBuilder("looktype ").append(info.looktype());
        entry.meta().ifPresent(m -> text.append(" · ").append(m.name())
                .append(" · ").append(m.kind().name().toLowerCase(Locale.ROOT)));
        text.append(" · ").append(info.addonCount()).append(" addon(s)");
        if (info.mountable()) {
            text.append(" · montável");
        }
        text.append(" · ").append(frames.size()).append(" frame(s) ")
                .append(frames.get(0).image().getWidth()).append('x').append(frames.get(0).image().getHeight());
        if (request.mount() > 0) {
            text.append(" · montaria ").append(request.mount());
        }
        return text.toString();
    }

    private void randomize() {
        if (listModel.isEmpty()) {
            return;
        }
        // A seleção dispara onOutfitSelected, que já habilita/desabilita addons e montaria para o novo outfit.
        list.setSelectedIndex(random.nextInt(listModel.size()));
        list.ensureIndexIsVisible(list.getSelectedIndex());
        OutfitInfo info = list.getSelectedValue().info();
        adjusting = true;
        addon1.setSelected(addon1.isEnabled() && random.nextBoolean());
        addon2.setSelected(addon2.isEnabled() && random.nextBoolean());
        for (ColorButton button : List.of(head, body, legs, feet)) {
            button.setColor(TibiaColor.palette(random.nextInt(TibiaColor.PALETTE_SIZE)));
        }
        if (info.mountable() && mount.getItemCount() > 1 && random.nextBoolean()) {
            mount.setSelectedIndex(1 + random.nextInt(mount.getItemCount() - 1));
        } else {
            mount.setSelectedIndex(0);
        }
        directions.get(Direction.values()[random.nextInt(4)]).setSelected(true);
        adjusting = false;
        refresh();
    }

    private void save(boolean gif) {
        if (currentFrames.isEmpty()) {
            return;
        }
        OutfitRequest request = request();
        JFileChooser chooser = new JFileChooser();
        String extension = gif ? "gif" : "png";
        chooser.setFileFilter(new FileNameExtensionFilter(extension.toUpperCase(Locale.ROOT), extension));
        chooser.setSelectedFile(new File(suggestedName(request) + "." + extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            file = new File(file.getParentFile(), file.getName() + "." + extension);
        }
        try {
            if (gif) {
                try (OutputStream out = Files.newOutputStream(file.toPath())) {
                    GifWriter.write(currentFrames, out);
                }
            } else {
                ImageIO.write(currentFrames.get(0).image(), "png", file);
            }
            status.setText("Salvo em " + file.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Não consegui salvar: " + e.getMessage(), "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String suggestedName(OutfitRequest r) {
        Entry entry = list.getSelectedValue();
        String base = entry.meta().map(m -> m.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_"))
                .orElse(Integer.toString(r.looktype()));
        return base + "_" + r.looktype() + "_a" + r.addons() + (r.mount() > 0 ? "_m" + r.mount() : "")
                + "_" + r.direction().name().toLowerCase(Locale.ROOT);
    }
}
