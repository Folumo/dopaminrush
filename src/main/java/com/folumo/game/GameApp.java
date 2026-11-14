package com.folumo.game;

import com.folumo.tree.Fmt;
import com.folumo.tree.I18n;
import com.folumo.tree.Mods;
import com.folumo.tree.Node;
import com.folumo.tree.Theme;
import com.folumo.tree.Tree;
import com.folumo.tree.TreeCanvas;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.folumo.tree.I18n.t;

/** App 2: play a tree made in the editor. */
public final class GameApp {
    private final JFrame frame = new JFrame();
    private final Path treeFile;
    private final Path saveFile;
    private GameState state;
    private final TreeCanvas canvas;

    private final JLabel points = Theme.label("", Theme.TITLE, Color.WHITE);
    private final JLabel currency = Theme.label("", Theme.BODY, Theme.MUTED);
    private final JLabel income = Theme.label("", Theme.BOLD, Theme.GREEN);
    private final JButton gather = Theme.button("", Theme.GOLD.darker());

    private final Theme.Card info = new Theme.Card();
    private final JLabel infoName = Theme.label("", Theme.H2, Color.WHITE);
    private final JLabel infoBody = Theme.label("", Theme.BODY, Theme.TEXT);
    private final JButton buy = Theme.button(t("buy.buy"), Theme.ACCENT);

    private final Settings settings = Settings.load(Settings.FILE);
    private final Sfx sfx = new Sfx();
    private final JPanel settingsLayer = new JPanel(new GridBagLayout());
    private final Mods mods;
    private final Path modsFolder;

    public static void main(String[] args) {
        I18n.setLanguage(Settings.load(Settings.FILE).language);
        SwingUtilities.invokeLater(() -> {
            Path treeFile = Path.of(args.length > 0 ? args[0] : "tree.properties");
            if (!Files.exists(treeFile)) {
                JFileChooser fc = new JFileChooser(new java.io.File("."));
                fc.setDialogTitle(t("app.pickTree"));
                fc.setFileFilter(new FileNameExtensionFilter(t("app.treeFiles"), "properties"));
                if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) System.exit(0);
                treeFile = fc.getSelectedFile().toPath();
            }
            try {
                new GameApp(treeFile);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, t("app.cantStart", e.getMessage()), t("app.game"), JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private GameApp(Path treeFile) throws Exception {
        this.treeFile = treeFile.toAbsolutePath();
        mods = Mods.load(treeFile);
        Tree tree = mods.tree;
        mods.sounds.forEach((name, wav) -> {
            try {
                sfx.replace(name, wav);
            } catch (IllegalArgumentException e) {
                mods.errors.add("sounds: " + e.getMessage());
            }
        });
        modsFolder = Mods.folder(treeFile);
        String base = treeFile.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        saveFile = this.treeFile.resolveSibling(base + ".save");
        state = GameState.load(tree, saveFile);

        canvas = new TreeCanvas(tree);
        canvas.fill = n -> state.owned.contains(n.id) ? Theme.GREEN
                : !state.unlocked(n) ? Theme.LOCKED
                : state.canBuy(n) ? Theme.ACCENT : Theme.PURPLE;
        canvas.subtitle = n -> state.owned.contains(n.id) ? t("node.owned") : Fmt.num(n.cost) + " " + tree.currency;
        canvas.edgeColor = (from, to) -> !state.owned.contains(from.id) ? null
                : state.owned.contains(to.id) ? Theme.GREEN : Theme.mix(Theme.ACCENT, Theme.BG, .35);
        canvas.onPress = (n, e) -> {
            if (n != null && n != canvas.selected) sfx.play(Sfx.Sound.SELECT);
            if (n != null) settingsLayer.setVisible(false); // info card and settings never overlap
            canvas.selected = n;
            if (n != null && e.getClickCount() == 2) tryBuy(n);
            refresh();
        };

        canvas.setLayout(new BorderLayout());
        canvas.add(hud(), BorderLayout.NORTH);
        canvas.add(infoCard(), BorderLayout.EAST);
        canvas.add(bottomBar(), BorderLayout.SOUTH);
        canvas.add(settingsPanel(), BorderLayout.CENTER);
        canvas.add(breakReminder(), BorderLayout.WEST);
        canvas.registerKeyboardAction(e -> toggleSettings(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        applySound();

        frame.setTitle(base);
        frame.setContentPane(canvas);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setIconImages(com.folumo.tree.GameIcon.images());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        long[] last = {System.nanoTime()};
        new Timer(100, e -> {
            long now = System.nanoTime();
            state.tick((now - last[0]) / 1e9);
            last[0] = now;
            refresh();
        }).start();
        new Timer(15_000, e -> {
            try {
                state.save(saveFile);
            } catch (Exception ex) {
                System.err.println("Autosave failed: " + ex.getMessage());
            }
        }).start();

        refresh();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        if (!mods.errors.isEmpty()) // the errors themselves stay in English: they're for mod makers
            JOptionPane.showMessageDialog(frame, t("mods.skipped") + "\n\n• " + String.join("\n• ", mods.errors),
                    t("mods.title"), JOptionPane.WARNING_MESSAGE);
    }

    private JComponent hud() {
        Theme.Card card = new Theme.Card();
        for (JLabel l : new JLabel[]{points, currency, income}) {
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(l);
        }
        return floating(new FlowLayout(FlowLayout.CENTER, 0, 16), card);
    }

    /** Every 30 minutes of play, a card on the left kindly suggests a break. */
    private JComponent breakReminder() {
        Theme.Card card = new Theme.Card();
        JLabel title = Theme.label(t("break.title"), Theme.H2, Theme.GOLD);
        JLabel body = Theme.label("", Theme.BODY, Theme.TEXT);
        JButton ok = Theme.button(t("break.ok"), Theme.ACCENT);
        for (JComponent c : new JComponent[]{title, body, ok}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(c);
            card.add(Box.createVerticalStrut(12));
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 0));
        wrap.add(card, BorderLayout.NORTH);
        card.setVisible(false);

        Timer hide = new Timer(60_000, e -> card.setVisible(false));
        hide.setRepeats(false);
        ok.addActionListener(e -> card.setVisible(false));
        int every = 30; // minutes
        int[] played = {0};
        new Timer(every * 60_000, e -> {
            played[0] += every;
            body.setText("<html><div style='width:220px'>" + t("break.body", I18n.duration(played[0])) + "</div></html>");
            card.setVisible(true);
            sfx.play(Sfx.Sound.BUY_IMPORTANT);
            hide.restart();
        }).start();
        return wrap;
    }

    private JComponent infoCard() {
        buy.addActionListener(e -> { if (canvas.selected != null) tryBuy(canvas.selected); });
        for (JComponent c : new JComponent[]{infoName, infoBody, buy}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(c);
            info.add(Box.createVerticalStrut(12));
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 16));
        wrap.add(info, BorderLayout.NORTH);
        return wrap;
    }

    private JComponent bottomBar() {
        gather.setFont(Theme.font(Font.BOLD, 20));
        gather.setBorder(BorderFactory.createEmptyBorder(16, 40, 16, 40));
        gather.addActionListener(e -> {
            state.click();
            sfx.play(Sfx.Sound.GATHER, .9 + Math.random() * .25);
            Point at = SwingUtilities.convertPoint(gather, gather.getWidth() / 2 + (int) (Math.random() * 80 - 40), 0, canvas);
            if (settings.popups) canvas.pop("+" + Fmt.num(state.clickValue()), at, Theme.GOLD);
            refresh();
        });
        JButton open = Theme.button(t("settings.open"), new Color(0x374151));
        open.addActionListener(e -> toggleSettings());
        JPanel bar = floating(new FlowLayout(FlowLayout.CENTER, 16, 20), gather);
        bar.add(open);
        return bar;
    }

    private JComponent settingsPanel() {
        Theme.Card card = new Theme.Card();
        JCheckBox sound = check(t("settings.sound"), settings.sound);
        JSlider volume = new JSlider(0, 100, settings.volume);
        volume.setOpaque(false);
        volume.setEnabled(settings.sound);
        JCheckBox popups = check(t("settings.popups"), settings.popups);

        sound.addActionListener(e -> {
            settings.sound = sound.isSelected();
            volume.setEnabled(settings.sound);
            applySound();
            sfx.play(Sfx.Sound.SELECT);
            saveSettings();
        });
        volume.addChangeListener(e -> {
            settings.volume = volume.getValue();
            applySound();
            if (!volume.getValueIsAdjusting()) { // play a sample at the new volume once the slider is let go
                sfx.play(Sfx.Sound.BUY);
                saveSettings();
            }
        });
        popups.addActionListener(e -> {
            settings.popups = popups.isSelected();
            saveSettings();
        });

        JComboBox<String> language = new JComboBox<>(I18n.LANGUAGES.toArray(String[]::new));
        language.setSelectedItem(I18n.language());
        language.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(list, I18n.nativeName((String) value), i, sel, focus);
            }
        });
        language.setMaximumSize(language.getPreferredSize());
        language.addActionListener(e -> {
            String picked = (String) language.getSelectedItem();
            if (picked == null || picked.equals(I18n.language())) return;
            settings.language = picked;
            saveSettings();
            if (JOptionPane.showConfirmDialog(frame, t("settings.languageRestart"), t("settings.language"),
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) restart();
        });

        JButton reset = Theme.button(t("reset.button"), Theme.RED.darker());
        reset.addActionListener(e -> {
            String word = t("reset.word");
            String typed = JOptionPane.showInputDialog(frame, t("reset.prompt", word), t("reset.button"), JOptionPane.WARNING_MESSAGE);
            if (typed == null) return; // cancelled
            if (!typed.trim().equals(word)) {
                sfx.play(Sfx.Sound.DENIED);
                JOptionPane.showMessageDialog(frame, t("reset.nothing"), t("reset.button"), JOptionPane.INFORMATION_MESSAGE);
            } else {
                state = new GameState(state.tree);
                canvas.selected = null;
                settingsLayer.setVisible(false);
                refresh();
            }
        });
        JButton save = Theme.button(t("save.button"), Theme.GREEN.darker());
        save.addActionListener(e -> {
            if (!saveProgress()) return;
            sfx.play(Sfx.Sound.BUY);
            canvas.pop(t("save.done"), SwingUtilities.convertPoint(save, save.getWidth() / 2, 0, canvas), Theme.GREEN);
        });
        JButton exit = Theme.button(t("exit.button"), new Color(0x374151));
        exit.addActionListener(e -> exit());
        JButton close = Theme.button(t("settings.close"), Theme.ACCENT);
        close.addActionListener(e -> settingsLayer.setVisible(false));

        JLabel modList = Theme.label("<html><div style='width:280px'>" + (mods.loaded.isEmpty() ? t("mods.none")
                : esc(t("mods.loaded", String.join(", ", mods.loaded)))) + (mods.errors.isEmpty() ? ""
                : "<br><font color='#f87171'>" + t("mods.skippedCount", mods.errors.size()) + "</font>")
                + "</div></html>", Theme.BODY, Theme.TEXT);
        JButton openMods = Theme.button(t("mods.openFolder"), new Color(0x374151));
        openMods.addActionListener(e -> {
            try {
                Files.createDirectories(modsFolder);
                Desktop.getDesktop().open(modsFolder.toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, t("mods.cantOpen", modsFolder, ex.getMessage()), t("mods.title"), JOptionPane.ERROR_MESSAGE);
            }
        });

        JComponent[] rows = {Theme.label(t("settings.title"), Theme.H2, Color.WHITE), sound,
                Theme.label(t("settings.volume"), Theme.BODY, Theme.MUTED), volume, popups,
                Theme.label(t("settings.language"), Theme.BODY, Theme.MUTED), language,
                Theme.label(t("mods.heading"), Theme.BODY, Theme.MUTED), modList, openMods,
                row(save, exit), row(reset, close)};
        JComponent width = (JComponent) Box.createRigidArea(new Dimension(300, 0));
        width.setAlignmentX(Component.LEFT_ALIGNMENT); // mixed alignments make BoxLayout shift everything
        card.add(width);
        for (JComponent c : rows) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(c);
            card.add(Box.createVerticalStrut(c == popups || c == language || c == openMods ? 22 : 10));
        }
        settingsLayer.setOpaque(false);
        settingsLayer.add(card);
        settingsLayer.setVisible(false);
        return settingsLayer;
    }

    private static JPanel row(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        for (JComponent c : items) {
            if (p.getComponentCount() > 0) p.add(Box.createHorizontalStrut(10));
            p.add(c);
        }
        return p;
    }

    /** Opening settings closes the node info card so the two don't overlap. */
    private void toggleSettings() {
        boolean open = !settingsLayer.isVisible();
        if (open) canvas.selected = null;
        settingsLayer.setVisible(open);
        refresh();
    }

    /** @return true if saved; shows the error otherwise */
    private boolean saveProgress() {
        try {
            state.save(saveFile);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, t("save.failed", ex.getMessage()), t("app.game"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void exit() {
        saveProgress(); // a failed save already told the player; still let them quit
        System.exit(0);
    }

    /** Saves, starts a fresh copy of the game (same Java, same classpath, same tree) and quits this one. */
    private void restart() {
        saveProgress();
        try {
            String java = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), GameApp.class.getName(), treeFile.toString())
                    .inheritIO().start();
        } catch (Exception ex) {
            System.err.println("Couldn't restart, please start the game again: " + ex.getMessage());
        }
        System.exit(0);
    }

    private static JCheckBox check(String text, boolean on) {
        JCheckBox c = new JCheckBox(text, on);
        c.setOpaque(false);
        c.setFocusPainted(false);
        c.setForeground(Theme.TEXT);
        c.setFont(Theme.BODY);
        return c;
    }

    private void applySound() {
        sfx.setVolume(settings.sound ? settings.volume : 0);
    }

    private void saveSettings() {
        try {
            settings.save(Settings.FILE);
        } catch (Exception ex) {
            System.err.println("Couldn't save settings: " + ex.getMessage());
        }
    }

    /** See-through holder, so the tree shows (and stays clickable) around the floating widget. */
    private static JPanel floating(LayoutManager layout, JComponent child) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        p.add(child);
        return p;
    }

    private void tryBuy(Node n) {
        if (!state.buy(n)) {
            if (!state.owned.contains(n.id)) sfx.play(Sfx.Sound.DENIED);
            return;
        }
        sfx.play(switch (n.shape) {
            case SQUARE -> Sfx.Sound.BUY;
            case CIRCLE -> Sfx.Sound.BUY_IMPORTANT;
            case HEXAGON -> Sfx.Sound.MILESTONE;
        });
        Point at = canvas.toScreen(n);
        at.translate(0, -TreeCanvas.size(n));
        if (settings.popups) canvas.pop(t(n.shape == Node.Shape.HEXAGON ? "pop.milestone" : "pop.unlocked"), at, Theme.GREEN);
        if (n.id.equals("final")) startEnding();
    }

    /** Ending files sit next to the tree file: ending.mp4 and credits.txt (or ending_hr.mp4, credits_hr.txt, ...). */
    private void startEnding() {
        saveProgress();
        settingsLayer.setVisible(false);
        Runnable backToTree = () -> {
            canvas.setTree(state.tree); // back to normal zoom at the top of the tree
            refresh();
        };
        new Ending(frame, canvas, I18n.localized(saveFile.resolveSibling("ending.mp4")),
                I18n.localized(saveFile.resolveSibling("credits.txt")),
                GameState.REBIRTH_BONUS * (state.rebirths + 1) + 1,
                backToTree,
                () -> {
                    state.reborn();
                    saveProgress();
                    backToTree.run();
                    sfx.play(Sfx.Sound.MILESTONE);
                }).start();
    }

    private void refresh() {
        String cur = state.tree.currency;
        points.setText(Fmt.num(state.points));
        currency.setText(state.rebirths == 0 ? cur : t("hud.rebirth", cur, state.rebirths, Fmt.num(state.multiplier())));
        income.setText(t("hud.perSecond", Fmt.num(state.income())));
        gather.setText(t("gather", Fmt.num(state.clickValue())));

        Node n = canvas.selected;
        info.setVisible(n != null);
        if (n != null) {
            boolean owned = state.owned.contains(n.id), unlocked = state.unlocked(n);
            StringBuilder sb = new StringBuilder("<html><div style='width:230px'>");
            if (!n.description.isBlank()) sb.append(esc(n.description).replace("\n", "<br>")).append("<br><br>");
            if (n.income > 0) sb.append("<font color='#34d399'>").append(esc(t("info.perSecond", Fmt.num(n.income), cur))).append("</font><br>");
            if (n.clickBonus > 0) sb.append("<font color='#fcd34d'>").append(esc(t("info.perClick", Fmt.num(n.clickBonus)))).append("</font><br>");
            if (!n.requires.isEmpty()) {
                sb.append("<br><font color='#8b93b0'>").append(esc(t("info.requires"))).append("</font><br>");
                for (String r : n.requires) {
                    boolean has = state.owned.contains(r);
                    // Dialog is Java's logical font, which falls back to a symbol font for these marks
                    sb.append(has ? "<font color='#34d399'><font face='Dialog'>✔</font> " : "<font color='#f87171'><font face='Dialog'>✖</font> ")
                            .append(esc(state.tree.nodes.get(r).name)).append("</font><br>");
                }
            }
            if (!owned) sb.append("<br><b>").append(esc(t("info.cost"))).append(" <font color='#fcd34d'>")
                    .append(Fmt.num(n.cost)).append(' ').append(esc(cur)).append("</font></b>");
            infoName.setText("<html><div style='width:230px'>" + esc(n.name) + "</div></html>");
            infoBody.setText(sb.append("</div></html>").toString());
            buy.setText(t(owned ? "buy.owned" : !unlocked ? "buy.locked" : "buy.buy"));
            buy.setEnabled(state.canBuy(n));
        }
        canvas.repaint();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
