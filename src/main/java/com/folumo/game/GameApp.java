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
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

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
    private boolean inEnding;
    /** Steps from what the player owns (see GameState.depths); nodes further than SIGHT are hidden. */
    private Map<String, Integer> depth = Map.of();
    private static final int SIGHT = 2;

    private double shownPoints = Double.NaN; // the big counter rolls towards state.points
    private final Deque<Long> recentClicks = new ArrayDeque<>();
    private final Set<String> notedThisSession = new HashSet<>(); // each kind of quiet note at most once per session
    private final Deque<String[]> noticeQueue = new ArrayDeque<>();
    private final Timer rushEnd = new Timer(30_000, e -> state.boost = 1);
    private JComponent settingsCard, layerCard; // layerCard: what the centre layer shows, settings or the journal
    private int era = -2; // the ambience playing now; -1 = none, -2 = not decided yet

    private final Theme.Card offerCard = new Theme.Card();
    private final JLabel offerBody = Theme.label("", Theme.BODY, Theme.TEXT);
    private final JButton offerOpen = Theme.button("", Theme.GOLD.darker());
    private final Timer offerTick = new Timer(1000, e -> offerTick());
    private int offerLeft;
    private double offerPrice;

    public static void main(String[] args) {
        I18n.setLanguage(Settings.load(Settings.FILE).language);
        SwingUtilities.invokeLater(() -> {
            Path treeFile = args.length > 0 ? Path.of(args[0]) : defaultTree();
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

    /** tree.properties in the current folder, else next to the game's jar (where the packaged app keeps it). */
    private static Path defaultTree() {
        Path here = Path.of("tree.properties");
        if (Files.exists(here)) return here;
        try {
            Path besideJar = Path.of(GameApp.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .resolveSibling("tree.properties");
            if (Files.exists(besideJar)) return besideJar;
        } catch (Exception e) {
            // no jar location (unusual class loader): fall back to the file picker
        }
        return here;
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
        Path dir = this.treeFile.getParent();
        if (Files.isWritable(dir)) saveFile = dir.resolve(base + ".save");
        else { // e.g. installed under Program Files: keep progress in the player's home folder
            saveFile = Path.of(System.getProperty("user.home"), ".dopaminrush", base + ".save");
            Files.createDirectories(saveFile.getParent());
        }
        state = GameState.load(tree, saveFile);
        state.startSession(LocalDateTime.now());

        canvas = new TreeCanvas(tree);
        canvas.fill = n -> state.owns(n.id) ? (state.canBuy(n) ? Theme.mix(Theme.GREEN, Theme.ACCENT, .5) : Theme.GREEN)
                : !state.unlocked(n) ? Theme.LOCKED
                : state.canBuy(n) ? Theme.ACCENT : Theme.PURPLE;
        // Fog of war: what you own, what you can buy next, and one more step as "???".
        canvas.shown = n -> inEnding || depth.getOrDefault(n.id, 0) <= sight();
        canvas.mystery = n -> !inEnding && depth.getOrDefault(n.id, 0) == sight();
        canvas.onBonus = this::caughtMolecule;
        rushEnd.setRepeats(false);
        canvas.subtitle = n -> {
            if (canvas.mystery.test(n)) return "";
            String price = Fmt.num(state.cost(n)) + " " + state.tree.currency;
            if (n.maxLevel == 1) return state.owns(n.id) ? t("node.owned") : price;
            String lvl = t("node.level", state.level(n), n.maxLevel);
            return state.maxed(n) ? lvl : state.owns(n.id) ? lvl + "  ·  " + Fmt.num(state.cost(n)) : price;
        };
        canvas.edgeColor = (from, to) -> !state.owns(from.id) ? null
                : state.owns(to.id) ? Theme.GREEN : Theme.mix(Theme.ACCENT, Theme.BG, .35);
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
        canvas.add(notices(), BorderLayout.WEST);
        canvas.registerKeyboardAction(e -> {
                    if (settingsLayer.isVisible()) closeLayer();
                    else toggleSettings();
                },
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        applySound();

        frame.setTitle(base.equals("tree") ? "Dopamin Rush" : base); // the main tree is the game itself
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
            state.play((now - last[0]) / 1e9);
            last[0] = now;
            for (Achievement a : state.newAchievements()) announce(a);
            updateAmbience();
            refresh();
        }).start();
        later(90, 180, () -> canvas.spawnBonus());
        later(240, 480, () -> {
            if (state.points > 0 && !offerCard.isVisible()) showOffer();
        });
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
        if (state.awaySeconds >= 60 && state.awayEarned > 0)
            notice(t("away.title"), t("away.body", I18n.duration((int) (state.awaySeconds / 60)),
                    Fmt.num(state.awayEarned), esc(tree.currency)) + "<br><br>" + offlineNow("away.limits"), t("away.ok"));
        if (state.sessionsToday >= 5) note("returns", t("observe.returns", state.sessionsToday));
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() < 5) note("late", t("observe.lateNight", String.format("%d:%02d", now.getHour(), now.getMinute())));
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
        JPanel row = floating(new FlowLayout(FlowLayout.CENTER, 16, 16), card);
        row.add(offerCard());
        return row;
    }

    /** A "mystery box" that sometimes pops up next to the HUD. Opening it always costs more than it gives. */
    private JComponent offerCard() {
        for (JComponent c : new JComponent[]{Theme.label(t("offer.title"), Theme.H2, Theme.GOLD), offerBody, offerOpen}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            offerCard.add(c);
            offerCard.add(Box.createVerticalStrut(8));
        }
        offerOpen.addActionListener(e -> takeOffer());
        offerCard.setVisible(false);
        return offerCard;
    }

    private void showOffer() {
        offerPrice = state.points * .25;
        offerLeft = 20;
        offerOpen.setText(t("offer.open", Fmt.num(offerPrice)));
        offerBody.setText("<html>" + t("offer.body", offerLeft) + "</html>");
        offerCard.setVisible(true);
        sfx.play(Sfx.Sound.BUY_IMPORTANT);
        offerTick.restart();
    }

    private void offerTick() {
        if (--offerLeft > 0) {
            offerBody.setText("<html>" + t("offer.body", offerLeft) + "</html>");
            return;
        }
        offerTick.stop(); // ignored to the end: a small reward for not taking the bait
        offerCard.setVisible(false);
        state.offersRefused++;
        double gift = Math.max(state.income() * 60, state.clickValue() * 10);
        state.points += gift;
        state.earned += gift;
        notice(t("observe.title"), t("observe.offerRefused", Fmt.num(gift), esc(state.tree.currency)), t("break.ok"));
    }

    private void takeOffer() {
        offerTick.stop();
        offerCard.setVisible(false);
        if (state.points < offerPrice) {
            sfx.play(Sfx.Sound.DENIED);
            return;
        }
        double got = offerPrice * (.1 + .6 * Math.random()); // always less than the price
        state.points += got - offerPrice;
        state.offersTaken++;
        sfx.play(Sfx.Sound.BUY);
        canvas.pop("+" + Fmt.num(got), SwingUtilities.convertPoint(offerOpen, offerOpen.getWidth() / 2, 0, canvas), Theme.GOLD);
        note("offerTaken", t("observe.offerTaken"));
    }

    /** The golden molecule was caught: either a rush (×3 for 30 s) or two minutes of income at once. */
    private void caughtMolecule(Point at) {
        state.molecules++;
        sfx.play(Sfx.Sound.MILESTONE);
        if (Math.random() < .5) {
            state.boost = 3;
            rushEnd.restart();
            canvas.pop(t("event.rush"), at, Theme.GOLD);
        } else {
            double lump = Math.max(state.income() * 120, state.clickValue() * 30);
            state.points += lump;
            state.earned += lump;
            canvas.pop("+" + Fmt.num(lump), at, Theme.GOLD);
        }
        refresh();
    }

    /** Runs {@code action} after a random delay between the bounds (seconds), then again, forever; skipped during the ending. */
    private void later(int minSeconds, int maxSeconds, Runnable action) {
        Timer t = new Timer(0, null);
        t.addActionListener(e -> {
            if (!inEnding) action.run();
            t.setInitialDelay((minSeconds + (int) (Math.random() * (maxSeconds - minSeconds))) * 1000);
            t.restart();
        });
        t.setRepeats(false);
        t.setInitialDelay((minSeconds + (int) (Math.random() * (maxSeconds - minSeconds))) * 1000);
        t.start();
    }

    /** A quiet note about how the game is being played; each kind shows at most once per session. */
    private void note(String kind, String body) {
        if (notedThisSession.add(kind)) notice(t("observe.title"), esc(body), t("break.ok"));
    }

    private void announce(Achievement a) {
        notice(t("achievement.title", t(a.key() + ".name")), esc(t(a.key() + ".text")) + "<br><br><font color='#34d399'>"
                + esc(t("achievement.bonus", Fmt.num(GameState.ACHIEVEMENT_BONUS * 100))) + "</font>", t("break.ok"));
        sfx.play(Sfx.Sound.MILESTONE);
    }

    private int sight() {
        return SIGHT + state.perk(Perk.SIGHT);
    }

    /** Eras follow the main tree's rows: the deepest node owned picks the background sound. */
    private void updateAmbience() {
        int deepest = 0;
        for (String id : state.levels.keySet()) deepest = Math.max(deepest, state.tree.nodes.get(id).y);
        // shortcut: the row bounds match the shipped tree; a mod tree with other rows gets roughly fitting eras
        int e = !settings.sound || !settings.ambience ? -1
                : deepest < 900 ? 0 : deepest < 1440 ? 1 : deepest < 1980 ? 2 : deepest < 2340 ? 3 : 4;
        if (e != era) {
            era = e;
            sfx.setAmbience(e);
        }
    }

    private final Theme.Card noticeCard = new Theme.Card();
    private final JLabel noticeTitle = Theme.label("", Theme.H2, Theme.GOLD);
    private final JLabel noticeBody = Theme.label("", Theme.BODY, Theme.TEXT);
    private final JButton noticeOk = Theme.button("", Theme.ACCENT);
    private final Timer noticeHide = new Timer(60_000, e -> hideNotice());

    /** A card on the left for the break reminder and the welcome back message; hides by itself after a minute. */
    private JComponent notices() {
        for (JComponent c : new JComponent[]{noticeTitle, noticeBody, noticeOk}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            noticeCard.add(c);
            noticeCard.add(Box.createVerticalStrut(12));
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(40, 16, 16, 0)); // the HUD row is above this, so 40 clears the minimap
        wrap.add(noticeCard, BorderLayout.NORTH);
        noticeCard.setVisible(false);
        noticeHide.setRepeats(false);
        noticeOk.addActionListener(e -> hideNotice());

        int every = 30; // minutes of play between break reminders
        int[] played = {0};
        new Timer(every * 60_000, e -> {
            played[0] += every;
            notice(t("break.title"), t("break.body", I18n.duration(played[0])), t("break.ok"));
            sfx.play(Sfx.Sound.BUY_IMPORTANT);
        }).start();
        return wrap;
    }

    /** @param body may contain simple HTML. Waits its turn if another notice is showing. */
    private void notice(String title, String body, String ok) {
        if (noticeCard.isVisible()) {
            noticeQueue.add(new String[]{title, body, ok});
            return;
        }
        noticeTitle.setText(title);
        noticeBody.setText("<html><div style='width:220px'>" + body + "</div></html>");
        noticeOk.setText(ok);
        noticeCard.setVisible(true);
        noticeHide.restart();
    }

    private void hideNotice() {
        noticeCard.setVisible(false);
        String[] next = noticeQueue.poll();
        if (next != null) notice(next[0], next[1], next[2]);
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
            long now = System.currentTimeMillis();
            recentClicks.add(now);
            while (recentClicks.peek() < now - 10_000) recentClicks.poll();
            if (recentClicks.size() >= 40) note("clicks", t("observe.clicks", recentClicks.size()));
            sfx.play(Sfx.Sound.GATHER, .9 + Math.random() * .25);
            Point at = SwingUtilities.convertPoint(gather, gather.getWidth() / 2 + (int) (Math.random() * 80 - 40), 0, canvas);
            if (settings.popups) canvas.pop("+" + Fmt.num(state.clickValue()), at, Theme.GOLD);
            refresh();
        });
        JButton goal = Theme.button(t("goal.button"), Theme.PURPLE);
        goal.addActionListener(e -> {
            Node n = state.bestNext(1);
            if (n == null) return;
            settingsLayer.setVisible(false);
            canvas.selected = n;
            canvas.focus(n);
            sfx.play(Sfx.Sound.SELECT);
            refresh();
        });
        JButton journal = Theme.button(t("journal.button"), new Color(0x374151));
        journal.addActionListener(e -> toggleJournal());
        JButton open = Theme.button(t("settings.open"), new Color(0x374151));
        open.addActionListener(e -> toggleSettings());
        JPanel bar = floating(new FlowLayout(FlowLayout.CENTER, 16, 20), goal);
        bar.add(gather);
        bar.add(journal);
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
        JCheckBox ambience = check(t("settings.ambience"), settings.ambience);
        ambience.addActionListener(e -> {
            settings.ambience = ambience.isSelected();
            saveSettings();
            updateAmbience();
        });
        JCheckBox goalHint = check(t("settings.goal"), settings.goalHint);
        goalHint.addActionListener(e -> {
            settings.goalHint = goalHint.isSelected();
            saveSettings();
            refresh();
        });

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
                Theme.label(t("settings.volume"), Theme.BODY, Theme.MUTED), volume, ambience, popups, goalHint,
                Theme.label(t("settings.language"), Theme.BODY, Theme.MUTED), language,
                Theme.label(t("mods.heading"), Theme.BODY, Theme.MUTED), modList, openMods,
                row(save, exit), row(reset, close)};
        JComponent width = (JComponent) Box.createRigidArea(new Dimension(300, 0));
        width.setAlignmentX(Component.LEFT_ALIGNMENT); // mixed alignments make BoxLayout shift everything
        card.add(width);
        for (JComponent c : rows) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(c);
            card.add(Box.createVerticalStrut(c == goalHint || c == language || c == openMods ? 14 : 6));
        }
        settingsLayer.setOpaque(false);
        settingsLayer.add(card);
        settingsCard = layerCard = card;
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

    /** The centre layer shows settings or the journal. Opening it closes the node info card so the two don't overlap. */
    private void showLayer(JComponent card) {
        settingsLayer.removeAll();
        settingsLayer.add(card);
        layerCard = card;
        canvas.selected = null;
        settingsLayer.setVisible(true);
        settingsLayer.revalidate();
        settingsLayer.repaint();
        refresh();
    }

    private void closeLayer() {
        settingsLayer.setVisible(false);
        refresh();
    }

    private void toggleSettings() {
        if (settingsLayer.isVisible() && layerCard == settingsCard) closeLayer();
        else showLayer(settingsCard);
    }

    private void toggleJournal() {
        if (settingsLayer.isVisible() && layerCard != settingsCard) closeLayer();
        else showLayer(journal());
    }

    private JComponent journal() {
        return Journal.build(state, () -> {
            sfx.play(Sfx.Sound.MILESTONE);
            showLayer(journal()); // rebuilt with the new levels and Insight
        }, this::closeLayer, canvas.getHeight() - 290); // room between the HUD and the bottom bar
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
        updateAmbience();
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
            if (!state.maxed(n)) sfx.play(Sfx.Sound.DENIED);
            return;
        }
        int lvl = state.level(n);
        // tolerance: the more you've bought lately, the duller each purchase sounds and looks
        sfx.play(switch (n.shape) {
            case SQUARE -> Sfx.Sound.BUY;
            case CIRCLE -> Sfx.Sound.BUY_IMPORTANT;
            case HEXAGON -> Sfx.Sound.MILESTONE;
        }, 1 - .35 * state.tolerance);
        Point at = canvas.toScreen(n);
        at.translate(0, -TreeCanvas.size(n));
        if (settings.popups) canvas.pop(lvl > 1 ? t("pop.level", lvl)
                : t(n.shape == Node.Shape.HEXAGON ? "pop.milestone" : "pop.unlocked"), at, Theme.mix(Theme.GREEN, Theme.MUTED, state.tolerance));
        if (n.shape == Node.Shape.HEXAGON) canvas.ring(n);
        if (state.tolerance >= .5) note("tolerance", t("observe.tolerance"));
        if (n.id.equals("final") && lvl == 1) startEnding();
    }

    /** Ending files sit next to the tree file: ending.mp4 and credits.txt (or ending_hr.mp4, credits_hr.txt, ...). */
    private void startEnding() {
        saveProgress();
        settingsLayer.setVisible(false);
        noticeCard.setVisible(false);
        offerTick.stop();
        offerCard.setVisible(false);
        canvas.minimap = false; // just the tree for the ending
        inEnding = true;
        Runnable backToTree = () -> {
            inEnding = false;
            canvas.minimap = true;
            canvas.setTree(state.tree); // back to normal zoom at the top of the tree
            refresh();
        };
        new Ending(frame, canvas, I18n.localized(treeFile.resolveSibling("ending.mp4")),
                I18n.localized(treeFile.resolveSibling("credits.txt")),
                GameState.REBIRTH_BONUS * (state.rebirths + 1) + 1,
                List.of(new String[]{t("stats.played"), I18n.duration(Math.max(1, (int) (state.playSeconds / 60)))},
                        new String[]{t("stats.clicks"), Fmt.num(state.clicks)},
                        new String[]{t("stats.bought"), Fmt.num(state.bought)},
                        new String[]{t("stats.earned", state.tree.currency), Fmt.num(state.earned)}),
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
        depth = state.depths();
        shownPoints = Double.isNaN(shownPoints) ? state.points : shownPoints + (state.points - shownPoints) * .35;
        if (Math.abs(state.points - shownPoints) <= Math.abs(state.points) * 1e-4 + .05) shownPoints = state.points;
        points.setText(Fmt.num(shownPoints));
        currency.setText(state.rebirths == 0 ? cur : t("hud.rebirth", cur, state.rebirths, Fmt.num(state.multiplier())));
        income.setText(t("hud.perSecond", Fmt.num(state.income())) + (state.boost > 1 ? "   " + t("hud.rush") : ""));
        gather.setText(t("gather", Fmt.num(state.clickValue())));
        canvas.goal = settings.goalHint && !inEnding ? state.bestNext(1) : null;

        Node n = canvas.selected;
        info.setVisible(n != null);
        if (n != null) {
            boolean maxed = state.maxed(n), unlocked = state.unlocked(n);
            int lvl = state.level(n);
            StringBuilder sb = new StringBuilder("<html><div style='width:230px'>");
            boolean mystery = canvas.mystery.test(n);
            if (mystery) sb.append(esc(t("info.mystery"))).append("<br>");
            else if (!n.description.isBlank()) sb.append(esc(n.description).replace("\n", "<br>")).append("<br><br>");
            if (n.income > 0 && !mystery) sb.append("<font color='#34d399'>").append(esc(t("info.perSecond", Fmt.num(n.income), cur))).append("</font><br>");
            if (n.clickBonus > 0 && !mystery) sb.append("<font color='#fcd34d'>").append(esc(t("info.perClick", Fmt.num(n.clickBonus)))).append("</font><br>");
            if (n.offlineRate > 0 && !mystery) sb.append("<font color='#7c6fc4'>").append(esc(t("info.offlineRate", Fmt.num(n.offlineRate * 100)))).append("</font><br>");
            if (n.offlineHours > 0 && !mystery) sb.append("<font color='#7c6fc4'>").append(esc(t("info.offlineHours", I18n.duration((int) Math.round(n.offlineHours * 60))))).append("</font><br>");
            if ((n.offlineRate > 0 || n.offlineHours > 0) && !mystery) sb.append("<font color='#8b93b0'>").append(offlineNow("info.offlineNow")).append("</font><br>");
            if (n.calm && !mystery) sb.append("<font color='#34d399'>").append(esc(t("info.calm"))).append("</font><br>");
            if (n.maxLevel > 1 && !mystery) sb.append("<font color='#8b93b0'>").append(esc(t("info.level", lvl, n.maxLevel))).append("</font><br>");
            if (!n.requires.isEmpty()) {
                sb.append("<br><font color='#8b93b0'>").append(esc(t("info.requires"))).append("</font><br>");
                for (String r : n.requires) {
                    boolean has = state.owns(r);
                    // Dialog is Java's logical font, which falls back to a symbol font for these marks
                    sb.append(has ? "<font color='#34d399'><font face='Dialog'>✔</font> " : "<font color='#f87171'><font face='Dialog'>✖</font> ")
                            .append(esc(state.tree.nodes.get(r).name)).append("</font><br>");
                }
            }
            if (!maxed && !mystery) sb.append("<br><b>").append(esc(t("info.cost"))).append(" <font color='#fcd34d'>")
                    .append(Fmt.num(state.cost(n))).append(' ').append(esc(cur)).append("</font></b>");
            infoName.setText("<html><div style='width:230px'>" + (mystery ? "???" : esc(n.name)) + "</div></html>");
            infoBody.setText(sb.append("</div></html>").toString());
            buy.setText(t(maxed ? (n.maxLevel == 1 ? "buy.owned" : "buy.maxed")
                    : !unlocked ? "buy.locked" : lvl > 0 ? "buy.upgrade" : "buy.buy"));
            buy.setEnabled(state.canBuy(n));
        }
        canvas.repaint();
    }

    /** The current offline share and time, in the message {@code key}. */
    private String offlineNow(String key) {
        return esc(t(key, Fmt.num(state.offlineRate() * 100), I18n.duration((int) Math.round(state.offlineHours() * 60))));
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
