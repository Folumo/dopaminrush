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
            canvas.selected = n;
            if (n != null && e.getClickCount() == 2) tryBuy(n);
            refresh();
        };

        canvas.setLayout(new BorderLayout());
        canvas.add(hud(), BorderLayout.NORTH);
        canvas.add(infoCard(), BorderLayout.EAST);
        canvas.add(bottomBar(), BorderLayout.SOUTH);

        frame.setTitle(base);
        frame.setContentPane(canvas);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    state.save(saveFile);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Couldn't save progress:\n" + ex.getMessage(), "Game", JOptionPane.ERROR_MESSAGE);
                }
                System.exit(0);
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
            Point at = SwingUtilities.convertPoint(gather, gather.getWidth() / 2 + (int) (Math.random() * 80 - 40), 0, canvas);
            canvas.pop("+" + Fmt.num(state.clickValue()), at, Theme.GOLD);
            refresh();
        });
        JButton reset = Theme.button("Reset", new Color(0x374151));
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(frame, "Start over from zero?", "Reset", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                state = new GameState(state.tree);
                canvas.selected = null;
                refresh();
            }
        });
        JPanel bar = floating(new FlowLayout(FlowLayout.CENTER, 16, 20), gather);
        bar.add(reset);
        return bar;
    }

    /** See-through holder, so the tree shows (and stays clickable) around the floating widget. */
    private static JPanel floating(LayoutManager layout, JComponent child) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        p.add(child);
        return p;
    }

    private void tryBuy(Node n) {
        if (!state.buy(n)) return;
        Point at = canvas.toScreen(n);
        at.translate(0, -TreeCanvas.R);
        canvas.pop("Unlocked!", at, Theme.GREEN);
    }

    private void refresh() {
        String cur = state.tree.currency;
        points.setText(Fmt.num(state.points));
        currency.setText(cur);
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
                    sb.append(has ? "<font color='#34d399'>✔ " : "<font color='#f87171'>✖ ")
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
