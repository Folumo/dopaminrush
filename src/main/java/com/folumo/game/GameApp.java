package com.folumo.game;

import com.folumo.tree.Fmt;
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

/** App 2: play a tree made in the editor. */
public final class GameApp {
    private final JFrame frame = new JFrame();
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
    private final JButton buy = Theme.button("Buy", Theme.ACCENT);

    private final Settings settings = Settings.load(Settings.FILE);
    private final Sfx sfx = new Sfx();
    private final JPanel settingsLayer = new JPanel(new GridBagLayout());

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Path treeFile = Path.of(args.length > 0 ? args[0] : "tree.properties");
            if (!Files.exists(treeFile)) {
                JFileChooser fc = new JFileChooser(new java.io.File("."));
                fc.setDialogTitle("Pick a tree file made with the editor");
                fc.setFileFilter(new FileNameExtensionFilter("Tree files (*.properties)", "properties"));
                if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) System.exit(0);
                treeFile = fc.getSelectedFile().toPath();
            }
            try {
                new GameApp(treeFile);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Couldn't start:\n" + e.getMessage(), "Game", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private GameApp(Path treeFile) throws Exception {
        Tree tree = Tree.load(treeFile);
        String base = treeFile.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        saveFile = treeFile.toAbsolutePath().resolveSibling(base + ".save");
        state = GameState.load(tree, saveFile);

        canvas = new TreeCanvas(tree);
        canvas.fill = n -> state.owned.contains(n.id) ? Theme.GREEN
                : !state.unlocked(n) ? Theme.LOCKED
                : state.canBuy(n) ? Theme.ACCENT : Theme.PURPLE;
        canvas.subtitle = n -> state.owned.contains(n.id) ? "Owned" : Fmt.num(n.cost) + " " + tree.currency;
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
        JLabel title = Theme.label("Time for a little break", Theme.H2, Theme.GOLD);
        JLabel body = Theme.label("", Theme.BODY, Theme.TEXT);
        JButton ok = Theme.button("Okay", Theme.ACCENT);
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
            body.setText("<html><div style='width:220px'>You've been playing for <b>" + Fmt.duration(played[0])
                    + "</b>.<br><br>It's for your own good: stand up, stretch, drink some water and look out of a window."
                    + "<br><br>Your dopamine will still be here when you get back.</div></html>");
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
        JButton open = Theme.button("Settings", new Color(0x374151));
        open.addActionListener(e -> toggleSettings());
        JPanel bar = floating(new FlowLayout(FlowLayout.CENTER, 16, 20), gather);
        bar.add(open);
        return bar;
    }

    private JComponent settingsPanel() {
        Theme.Card card = new Theme.Card();
        JCheckBox sound = check("Sound effects", settings.sound);
        JSlider volume = new JSlider(0, 100, settings.volume);
        volume.setOpaque(false);
        volume.setEnabled(settings.sound);
        JCheckBox popups = check("Floating numbers", settings.popups);

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
        JButton reset = Theme.button("Reset progress", Theme.RED.darker());
        reset.addActionListener(e -> {
            String typed = JOptionPane.showInputDialog(frame,
                    "This deletes ALL your progress and can't be undone.\nType RESET to confirm:",
                    "Reset progress", JOptionPane.WARNING_MESSAGE);
            if (typed == null) return; // cancelled
            if (!typed.trim().equals("RESET")) {
                sfx.play(Sfx.Sound.DENIED);
                JOptionPane.showMessageDialog(frame, "Nothing was reset.", "Reset progress", JOptionPane.INFORMATION_MESSAGE);
            } else {
                state = new GameState(state.tree);
                canvas.selected = null;
                settingsLayer.setVisible(false);
                refresh();
            }
        });
        JButton save = Theme.button("Save game", Theme.GREEN.darker());
        save.addActionListener(e -> {
            if (!saveProgress()) return;
            sfx.play(Sfx.Sound.BUY);
            canvas.pop("Saved!", SwingUtilities.convertPoint(save, save.getWidth() / 2, 0, canvas), Theme.GREEN);
        });
        JButton exit = Theme.button("Save and exit", new Color(0x374151));
        exit.addActionListener(e -> exit());
        JButton close = Theme.button("Close", Theme.ACCENT);
        close.addActionListener(e -> settingsLayer.setVisible(false));

        JComponent[] rows = {Theme.label("Settings", Theme.H2, Color.WHITE), sound,
                Theme.label("Volume", Theme.BODY, Theme.MUTED), volume, popups,
                row(save, exit), row(reset, close)};
        JComponent width = (JComponent) Box.createRigidArea(new Dimension(300, 0));
        width.setAlignmentX(Component.LEFT_ALIGNMENT); // mixed alignments make BoxLayout shift everything
        card.add(width);
        for (JComponent c : rows) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(c);
            card.add(Box.createVerticalStrut(c == popups ? 22 : 10));
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
            JOptionPane.showMessageDialog(frame, "Couldn't save progress:\n" + ex.getMessage(), "Game", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void exit() {
        saveProgress(); // a failed save already told the player; still let them quit
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
        if (settings.popups) canvas.pop(n.shape == Node.Shape.HEXAGON ? "Milestone!" : "Unlocked!", at, Theme.GREEN);
        if (n.id.equals("final")) startEnding();
    }

    /** Ending files sit next to the tree file: ending.mp4 and credits.txt. */
    private void startEnding() {
        saveProgress();
        settingsLayer.setVisible(false);
        Runnable backToTree = () -> {
            canvas.setTree(state.tree); // back to normal zoom at the top of the tree
            refresh();
        };
        new Ending(frame, canvas, saveFile.resolveSibling("ending.mp4"), saveFile.resolveSibling("credits.txt"),
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
        currency.setText(state.rebirths == 0 ? cur
                : cur + "  ·  Rebirth " + state.rebirths + " (×" + Fmt.num(state.multiplier()) + ")");
        income.setText("+" + Fmt.num(state.income()) + " /s");
        gather.setText("Gather  +" + Fmt.num(state.clickValue()));

        Node n = canvas.selected;
        info.setVisible(n != null);
        if (n != null) {
            boolean owned = state.owned.contains(n.id), unlocked = state.unlocked(n);
            StringBuilder sb = new StringBuilder("<html><div style='width:230px'>");
            if (!n.description.isBlank()) sb.append(esc(n.description).replace("\n", "<br>")).append("<br><br>");
            if (n.income > 0) sb.append("<font color='#34d399'>+").append(Fmt.num(n.income)).append(' ').append(esc(cur)).append(" /s</font><br>");
            if (n.clickBonus > 0) sb.append("<font color='#fcd34d'>+").append(Fmt.num(n.clickBonus)).append(" per click</font><br>");
            if (!n.requires.isEmpty()) {
                sb.append("<br><font color='#8b93b0'>Requires</font><br>");
                for (String r : n.requires) {
                    boolean has = state.owned.contains(r);
                    // Dialog is Java's logical font, which falls back to a symbol font for these marks
                    sb.append(has ? "<font color='#34d399'><font face='Dialog'>✔</font> " : "<font color='#f87171'><font face='Dialog'>✖</font> ")
                            .append(esc(state.tree.nodes.get(r).name)).append("</font><br>");
                }
            }
            if (!owned) sb.append("<br><b>Cost: <font color='#fcd34d'>").append(Fmt.num(n.cost)).append(' ').append(esc(cur)).append("</font></b>");
            infoName.setText("<html><div style='width:230px'>" + esc(n.name) + "</div></html>");
            infoBody.setText(sb.append("</div></html>").toString());
            buy.setText(owned ? "Owned" : !unlocked ? "Locked" : "Buy");
            buy.setEnabled(state.canBuy(n));
        }
        canvas.repaint();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
