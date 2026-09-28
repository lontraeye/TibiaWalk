package org.tibiawalk.desktop;

import org.tibiawalk.core.assets.ClientAssets;
import org.tibiawalk.core.assets.OutfitInfo;
import org.tibiawalk.core.metadata.GameCharacter;
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
import javax.swing.ImageIcon;
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
import javax.swing.JToggleButton;
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

    /**
     * Um item da lista: um looktype, ou um personagem (NPC/monstro/boss) com o outfit completo dele.
     *
     * @param character null para itens que são só looktype
     */
    private record Entry(OutfitInfo info, Optional<LooktypeMeta> meta, GameCharacter character) {
        int looktype() {
            return info.looktype();
        }

        String name() {
            return character != null ? character.name() : meta.map(LooktypeMeta::name).orElse("");
        }

        String sex() {
            return character != null ? null : meta.map(LooktypeMeta::sex).orElse(null);
        }

        @Override
        public String toString() {
            if (character != null) {
                return name() + (character.kind() == GameCharacter.Kind.BOSS ? " ★" : "") + "  (" + looktype() + ")";
            }
            return meta.isEmpty() ? "#" + looktype() : name() + "  (" + looktype() + ")";
        }
    }

    private enum Filter {
        PLAYERS("Outfits de player", e -> kind(e, LooktypeKind.PLAYER)),
        MOUNTS("Montarias", e -> kind(e, LooktypeKind.MOUNT)),
        MONSTERS("Monstros e bosses", e -> e.character() != null && e.character().kind() != GameCharacter.Kind.NPC),
        NPCS("NPCs", e -> e.character() != null && e.character().kind() == GameCharacter.Kind.NPC),
        UNKNOWN("Sem nome", e -> e.character() == null && e.meta().isEmpty()),
        ALL("Todos os looktypes", e -> e.character() == null);

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
            return e.character() == null && e.meta().map(m -> m.kind() == kind).orElse(false);
        }
    }

    private static final String[] DIRECTION_LABELS = {"Norte", "Leste", "Sul", "Oeste"};

    private final OutfitRenderer renderer;
    private final List<Entry> entries = new ArrayList<>();
    private final Random random = new Random();

    private final JComboBox<Filter> filter = new JComboBox<>(Filter.values());
    private final Map<Filter, Integer> counts = new EnumMap<>(Filter.class);
    private final JLabel listCount = new JLabel(" ");
    private final JTextField search = new JTextField();
    private final JToggleButton male = new JToggleButton("♂ Masculino", true);
    private final JToggleButton female = new JToggleButton("♀ Feminino");
    private final JToggleButton bossOnly = new JToggleButton("★ Só bosses");
    /** Linha abaixo da categoria: sexo (outfits de player) ou "só bosses" (monstros). */
    private final JPanel filterRow = new JPanel(new java.awt.CardLayout());
    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(listModel);

    private final PreviewPanel preview = new PreviewPanel();
    private final JLabel status = new JLabel(" ");

    private final JCheckBox addon1 = new JCheckBox("Addon 1");
    private final JCheckBox addon2 = new JCheckBox("Addon 2");
    /** Em NPC/monstro vestido com um outfit de player: pula para esse outfit, mantendo cores e addons. */
    private final JButton goToOutfit = new JButton("Ir para o outfit");
    private final ColorButton head = new ColorButton("Cabeça", c -> refresh());
    private final ColorButton body = new ColorButton("Corpo", c -> refresh());
    private final ColorButton legs = new ColorButton("Pernas", c -> refresh());
    private final ColorButton feet = new ColorButton("Pés", c -> refresh());
    private final JButton mountButton = new JButton();
    private final MountPicker mountPicker;
    private final List<MountPicker.Option> mountOptions = new ArrayList<>();
    private int mountLooktype;
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
                entries.add(new Entry(info, metadata.get(info.looktype()), null));
            }
        }
        for (GameCharacter character : metadata.characters()) {
            OutfitInfo info = assets.outfit(character.looktype());
            if (info != null && info.idle() != null) {
                entries.add(new Entry(info, metadata.get(character.looktype()), character));
            }
        }

        mountOptions.add(new MountPicker.Option(0, "Sem montaria"));
        entries.stream()
                .filter(e -> Filter.MOUNTS.test.test(e))
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> mountOptions.add(new MountPicker.Option(e.looktype(), e.name())));
        mountPicker = new MountPicker(mountOptions, looktype -> renderer.still(OutfitRequest.of(looktype)));

        countCategories();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(menu(chooseAssets));

        updateMountButton(); // antes de montar os controles, que calculam a altura com o ícone já no botão
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
        applyFilter();
        mountPicker.onThumbnail(this::updateMountButton);
        mountPicker.preload();

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

    /** Total por categoria (no combo) e, entre os players, por sexo (nos botões). */
    private void countCategories() {
        for (Filter f : Filter.values()) {
            counts.put(f, (int) entries.stream().filter(f.test).count());
        }
        List<Entry> players = entries.stream().filter(Filter.PLAYERS.test).toList();
        // Sem sexo definido conta nos dois, como aparece na lista.
        long males = players.stream().filter(e -> e.sex() == null || "male".equals(e.sex())).count();
        long females = players.stream().filter(e -> e.sex() == null || "female".equals(e.sex())).count();
        male.setText("♂ Masculino (" + males + ")");
        female.setText("♀ Feminino (" + females + ")");
    }

    private JComponent buildListPanel() {
        filter.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean selected, boolean focus) {
                Filter f = (Filter) value;
                String text = f == null ? "" : f.label + "  (" + counts.getOrDefault(f, 0) + ")";
                return super.getListCellRendererComponent(l, text, index, selected, focus);
            }
        });
        // Trocar de categoria limpa a busca (como as abas do web); setText já refiltra se havia texto.
        filter.addActionListener(e -> {
            if (search.getText().isEmpty()) {
                applyFilter();
            } else {
                search.setText("");
            }
        });
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

        ButtonGroup sexGroup = new ButtonGroup();
        sexGroup.add(male);
        sexGroup.add(female);
        male.addActionListener(e -> applyFilter());
        female.addActionListener(e -> applyFilter());
        JPanel sex = new JPanel(new GridLayout(1, 2, 4, 0));
        sex.add(male);
        sex.add(female);
        bossOnly.setToolTipText("Mostrar só os bosses");
        bossOnly.addActionListener(e -> applyFilter());
        filterRow.add(sex, "sex");
        filterRow.add(bossOnly, "boss");

        JPanel top = new JPanel(new GridLayout(0, 1, 4, 4));
        top.add(filter);
        top.add(filterRow);
        top.add(search);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(260, 420));
        panel.add(scroll, BorderLayout.CENTER);
        listCount.setForeground(java.awt.Color.GRAY);
        panel.add(listCount, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildControls() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        addon1.addActionListener(e -> refresh());
        addon2.addActionListener(e -> refresh());
        goToOutfit.setEnabled(false);
        goToOutfit.setToolTipText("Disponível em NPCs e monstros que usam um outfit de player");
        goToOutfit.addActionListener(e -> goToPlayerOutfit());
        panel.add(section("Addons", addon1, addon2, goToOutfit));

        panel.add(section("Cores", head, body, legs, feet));

        mountButton.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        mountButton.setToolTipText("Escolher montaria");
        mountButton.addActionListener(e -> mountPicker.open(mountButton, mountLooktype, this::setMount));
        JButton noMount = new JButton("✕");
        noMount.setToolTipText("Remover montaria");
        noMount.addActionListener(e -> setMount(0));
        JPanel mountRow = new JPanel(new BorderLayout(4, 0));
        mountRow.add(mountButton, BorderLayout.CENTER);
        mountRow.add(noMount, BorderLayout.EAST);
        panel.add(section("Montaria", mountRow));

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

    private void setMount(int looktype) {
        mountLooktype = looktype;
        updateMountButton();
        refresh();
    }

    private void updateMountButton() {
        String name = mountOptions.stream().filter(o -> o.looktype() == mountLooktype)
                .map(MountPicker.Option::name).findFirst().orElse("#" + mountLooktype);
        mountButton.setText(name);
        // Sempre um ícone de 32x32 (vazio quando não há miniatura) para o botão não mudar de altura.
        javax.swing.Icon thumb = mountLooktype == 0 ? null : mountPicker.thumbnail(mountLooktype);
        mountButton.setIcon(thumb == null ? new ImageIcon(new java.awt.image.BufferedImage(32, 32,
                java.awt.image.BufferedImage.TYPE_INT_ARGB)) : new ImageIcon(((ImageIcon) thumb).getImage()
                .getScaledInstance(32, 32, java.awt.Image.SCALE_FAST)));
    }

    private void applyFilter() {
        Entry previous = list.getSelectedValue();
        Filter selected = (Filter) filter.getSelectedItem();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        boolean bySex = selected == Filter.PLAYERS;
        male.setEnabled(bySex);
        female.setEnabled(bySex);
        String sex = female.isSelected() ? "female" : "male";
        ((java.awt.CardLayout) filterRow.getLayout()).show(filterRow, selected == Filter.MONSTERS ? "boss" : "sex");
        Predicate<Entry> scope = scope(selected, bySex, sex);

        listModel.clear();
        entries.stream()
                .filter(scope)
                .filter(e -> query.isEmpty()
                        || e.name().toLowerCase(Locale.ROOT).contains(query)
                        || Integer.toString(e.looktype()).equals(query)
                        // aliases são do looktype; para personagem, só o nome dele conta
                        || e.character() == null && e.meta().map(m -> m.aliases().stream()
                        .anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(query))).orElse(false))
                .sorted(selected == Filter.ALL || selected == Filter.UNKNOWN
                        ? Comparator.comparingInt(Entry::looktype)
                        : Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                                .thenComparingInt(Entry::looktype))
                .forEach(listModel::addElement);
        updateListCount(scope, bySex, sex);

        Entry counterpart = previous == null ? null : counterpart(previous);
        if (previous != null && listModel.contains(previous)) {
            list.setSelectedValue(previous, true);
        } else if (counterpart != null) {
            // Trocou o sexo: mantém o mesmo outfit (ex. Citizen 128 -> 136) com addons e cores.
            list.setSelectedValue(counterpart, true);
        } else if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            preview.showMessage("Nada encontrado");
        }
    }

    /** Categoria + sexo (players) + "só bosses" (monstros): tudo menos a busca. */
    private Predicate<Entry> scope(Filter selected, boolean bySex, String sex) {
        boolean bosses = selected == Filter.MONSTERS && bossOnly.isSelected();
        return selected.test
                .and(e -> !bySex || e.sex() == null || sex.equals(e.sex())) // sem sexo definido: nos dois
                .and(e -> !bosses || e.character().kind() == GameCharacter.Kind.BOSS);
    }

    /** "Mostrando 12 de 142 (feminino)": o que está na lista contra o total da categoria/sexo. */
    private void updateListCount(Predicate<Entry> scope, boolean bySex, String sex) {
        long total = entries.stream().filter(scope).count();
        String suffix = bySex ? ("female".equals(sex) ? " (feminino)" : " (masculino)")
                : bossOnly.isSelected() && filter.getSelectedItem() == Filter.MONSTERS ? " (bosses)" : "";
        listCount.setText(listModel.size() == total
                ? total + " itens" + suffix
                : "Mostrando " + listModel.size() + " de " + total + suffix);
    }

    /** O item da lista atual com o mesmo nome e categoria (a versão do outro sexo). */
    private Entry counterpart(Entry entry) {
        if (entry.character() != null || entry.meta().isEmpty()) {
            return null;
        }
        for (int i = 0; i < listModel.size(); i++) {
            Entry candidate = listModel.get(i);
            if (candidate.meta().isPresent() && candidate.meta().get().kind() == entry.meta().get().kind()
                    && candidate.name().equalsIgnoreCase(entry.name())) {
                return candidate;
            }
        }
        return null;
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
        mountButton.setEnabled(info.mountable());
        for (ColorButton button : List.of(head, body, legs, feet)) {
            button.setEnabled(info.colorable());
        }
        if (entry.character() != null) {
            applyPreset(entry.character(), info);
        }
        boolean character = entry.character() != null;
        if (character) {
            // Addon de NPC/monstro é fixo: as caixas mostram o que ele usa, mas não mudam.
            addon1.setEnabled(false);
            addon2.setEnabled(false);
        }
        Entry outfit = character ? playerOutfitOf(entry) : null;
        goToOutfit.setEnabled(outfit != null);
        goToOutfit.setText(outfit != null ? "Ir para o outfit: " + outfit.name() : "Ir para o outfit");
        adjusting = false;
        refresh();
    }

    /** O item de outfit de player com o mesmo looktype do personagem, se houver. */
    private Entry playerOutfitOf(Entry entry) {
        return entries.stream()
                .filter(e -> e.character() == null && e.looktype() == entry.looktype())
                .filter(e -> Filter.PLAYERS.test.test(e))
                .findFirst().orElse(null);
    }

    private void goToPlayerOutfit() {
        Entry current = list.getSelectedValue();
        Entry target = current == null ? null : playerOutfitOf(current);
        if (target == null) {
            return;
        }
        // A troca de filtro seleciona outro item no caminho; guarda os addons do NPC para reaplicar no fim.
        int addons = (addon1.isSelected() ? 1 : 0) | (addon2.isSelected() ? 2 : 0);
        if ("female".equals(target.sex())) {
            female.setSelected(true);
        } else {
            male.setSelected(true);
        }
        search.setText("");
        filter.setSelectedItem(Filter.PLAYERS);
        list.setSelectedValue(target, true);
        adjusting = true;
        addon1.setSelected(addon1.isEnabled() && (addons & 1) != 0);
        addon2.setSelected(addon2.isEnabled() && (addons & 2) != 0);
        adjusting = false;
        refresh();
    }

    /** Veste o outfit do NPC/monstro: cores, addons e montaria (dá para mexer depois). */
    private void applyPreset(GameCharacter character, OutfitInfo info) {
        addon1.setSelected(addon1.isEnabled() && (character.addons() & 1) != 0);
        addon2.setSelected(addon2.isEnabled() && (character.addons() & 2) != 0);
        head.setColor(TibiaColor.palette(character.head()));
        body.setColor(TibiaColor.palette(character.body()));
        legs.setColor(TibiaColor.palette(character.legs()));
        feet.setColor(TibiaColor.palette(character.feet()));
        mountLooktype = info.mountable() ? character.mount() : 0;
        updateMountButton();
    }

    private OutfitRequest request() {
        Entry entry = list.getSelectedValue();
        int addons = (addon1.isSelected() ? 1 : 0) | (addon2.isSelected() ? 2 : 0);
        Direction direction = directions.entrySet().stream()
                .filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse(Direction.SOUTH);
        return OutfitRequest.of(entry.looktype())
                .withAddons(addons)
                .withColors(head.color(), body.color(), legs.color(), feet.color())
                .withMount(entry.info().mountable() ? mountLooktype : 0)
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
        if (entry.character() != null) {
            text.append(" · ").append(entry.character().name()).append(" · ")
                    .append(entry.character().kind().name().toLowerCase(Locale.ROOT));
            entry.meta().ifPresent(m -> text.append(" (visual: ").append(m.name()).append(')'));
        } else {
            entry.meta().ifPresent(m -> text.append(" · ").append(m.name())
                    .append(" · ").append(m.kind().name().toLowerCase(Locale.ROOT)));
        }
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
        Entry picked = list.getSelectedValue();
        OutfitInfo info = picked.info();
        adjusting = true;
        if (picked.character() != null) {
            // NPC/monstro sorteado mantém a roupa dele; só a direção varia.
            directions.get(Direction.values()[random.nextInt(4)]).setSelected(true);
            adjusting = false;
            refresh();
            return;
        }
        addon1.setSelected(addon1.isEnabled() && random.nextBoolean());
        addon2.setSelected(addon2.isEnabled() && random.nextBoolean());
        for (ColorButton button : List.of(head, body, legs, feet)) {
            button.setColor(TibiaColor.palette(random.nextInt(TibiaColor.PALETTE_SIZE)));
        }
        mountLooktype = info.mountable() && mountOptions.size() > 1 && random.nextBoolean()
                ? mountOptions.get(1 + random.nextInt(mountOptions.size() - 1)).looktype() : 0;
        updateMountButton();
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
        String base = entry.name().isEmpty() ? Integer.toString(r.looktype())
                : entry.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return base + "_" + r.looktype() + "_a" + r.addons() + (r.mount() > 0 ? "_m" + r.mount() : "")
                + "_" + r.direction().name().toLowerCase(Locale.ROOT);
    }
}
