package com.folumo.editor;

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
import java.util.LinkedHashSet;
import java.util.Set;

/** App 1: build the node tree and save it for the game. */
public final class EditorApp {
    private final JFrame frame = new JFrame();
    private Tree tree = new Tree();
    private Path file;
    private boolean dirty;
    private final TreeCanvas canvas = new TreeCanvas(tree);

    private final JTextField currency = new JTextField(10);
    private final JLabel idLabel = new JLabel("-");
    private final JTextField name = new JTextField(18);
    private final JComboBox<Node.Shape> shape = new JComboBox<>(Node.Shape.values());
    private final JTextArea desc = new JTextArea(5, 18);
    private final JTextField cost = new JTextField(), income = new JTextField(), click = new JTextField(), requires = new JTextField();

    public static void main(String[] args) {
        Path start = Path.of(args.length > 0 ? args[0] : "tree.properties");
        SwingUtilities.invokeLater(() -> new EditorApp(start));
    }

    private EditorApp(Path start) {
        file = start;
        if (Files.exists(start)) load(start);

        canvas.movable = true;
        canvas.fill = n -> n.requires.isEmpty() ? Theme.GOLD : Theme.ACCENT; // gold = starting nodes
        canvas.edgeColor = (from, to) -> from == canvas.selected || to == canvas.selected ? Theme.GOLD : null;
        canvas.onMoved = this::changed;
        canvas.onPress = (n, e) -> {
            if (n != null && e.isShiftDown() && canvas.selected != null && n != canvas.selected) toggleRequire(n);
            else {
                canvas.selected = n;
                showSelected();
            }
        };

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(button("New", () -> { if (confirmDiscard()) { file = null; setTree(new Tree()); } }));
        bar.add(button("Open...", this::open));
        bar.add(button("Save", this::save));
        bar.add(button("Save As...", this::saveAs));
        bar.addSeparator();
        bar.add(button("Add node", this::addNode));
        bar.add(button("Delete node", this::deleteNode));
        bar.addSeparator();
        bar.add(new JLabel("Currency: "));
        bar.add(currency);
        currency.setMaximumSize(currency.getPreferredSize());
        currency.addActionListener(e -> applyCurrency());

        frame.setLayout(new BorderLayout());
        frame.add(bar, BorderLayout.NORTH);
        frame.add(canvas, BorderLayout.CENTER);
        frame.add(form(), BorderLayout.EAST);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setIconImages(com.folumo.tree.GameIcon.images());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmDiscard()) System.exit(0);
            }
        });
        currency.setText(tree.currency);
        showSelected();
        updateTitle();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel form() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(2, 0, 2, 0);
        c.weightx = 1;
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        Object[][] rows = {{"Id", idLabel}, {"Name", name},
                {"<html>Shape <font color=gray>(square = normal, circle = important,<br>hexagon = milestone that starts new branches)</font></html>", shape},
                {"Description", new JScrollPane(desc)},
                {"Cost", cost}, {"Income per second", income}, {"Click bonus", click},
                {"Requires (ids, comma separated)", requires}};
        for (Object[] r : rows) {
            p.add(new JLabel((String) r[0]), c);
            p.add((Component) r[1], c);
            if (r[1] instanceof JTextField f) f.addActionListener(e -> apply());
        }
        c.insets = new Insets(10, 0, 2, 0);
        p.add(button("Apply", this::apply), c);
        p.add(new JLabel("<html><br>Drag a node to move it.<br>Drag empty space to pan.<br>"
                + "Shift+click a node to toggle it<br>as a requirement of the selected one.</html>"), c);
        c.weighty = 1;
        p.add(Box.createGlue(), c);
        return p;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void showSelected() {
        Node n = canvas.selected;
        idLabel.setText(n == null ? "-" : n.id);
        name.setText(n == null ? "" : n.name);
        shape.setSelectedItem(n == null ? Node.Shape.SQUARE : n.shape);
        desc.setText(n == null ? "" : n.description);
        cost.setText(n == null ? "" : Double.toString(n.cost));
        income.setText(n == null ? "" : Double.toString(n.income));
        click.setText(n == null ? "" : Double.toString(n.clickBonus));
        requires.setText(n == null ? "" : String.join(", ", n.requires));
        for (JComponent f : new JComponent[]{name, shape, desc, cost, income, click, requires}) f.setEnabled(n != null);
    }

    private void apply() {
        applyCurrency();
        Node n = canvas.selected;
        if (n == null) return;
        try {
            double c = parse(cost, "Cost"), inc = parse(income, "Income"), cl = parse(click, "Click bonus");
            if (c < 0) throw new IllegalArgumentException("Cost can't be negative.");
            Set<String> req = new LinkedHashSet<>(Tree.split(requires.getText()));
            for (String r : req) {
                if (!tree.nodes.containsKey(r)) throw new IllegalArgumentException("No node with id '" + r + "'.");
                if (tree.wouldCycle(n.id, r)) throw new IllegalArgumentException("Requiring '" + r + "' would create a loop.");
            }
            n.name = name.getText().trim().isEmpty() ? n.id : name.getText().trim();
            n.shape = (Node.Shape) shape.getSelectedItem();
            n.description = desc.getText();
            n.cost = c;
            n.income = inc;
            n.clickBonus = cl;
            n.requires.clear();
            n.requires.addAll(req);
            changed();
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    private void applyCurrency() {
        String c = currency.getText().trim();
        if (!c.isEmpty() && !c.equals(tree.currency)) {
            tree.currency = c;
            changed();
        }
    }

    private static double parse(JTextField f, String label) {
        try {
            return Double.parseDouble(f.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private void toggleRequire(Node other) {
        Node sel = canvas.selected;
        if (!sel.requires.remove(other.id)) {
            if (tree.wouldCycle(sel.id, other.id)) {
                error("That would create a loop.");
                return;
            }
            sel.requires.add(other.id);
        }
        requires.setText(String.join(", ", sel.requires));
        changed();
    }

    private void addNode() {
        Point p = canvas.viewCenter();
        Node n = tree.add(p.x, p.y);
        if (canvas.selected != null) { // new node continues from the selected one
            n.x = canvas.selected.x;
            n.y = canvas.selected.y + 150;
            n.requires.add(canvas.selected.id);
        }
        canvas.selected = n;
        showSelected();
        changed();
    }

    private void deleteNode() {
        if (canvas.selected == null) return;
        tree.remove(canvas.selected.id);
        canvas.selected = null;
        showSelected();
        changed();
    }

    private void changed() {
        dirty = true;
        updateTitle();
        canvas.repaint();
    }

    private void updateTitle() {
        frame.setTitle("Tree Editor - " + (file == null ? "untitled" : file.getFileName()) + (dirty ? " *" : ""));
    }

    private void setTree(Tree t) {
        tree = t;
        canvas.setTree(t);
        currency.setText(t.currency);
        dirty = false;
        showSelected();
        updateTitle();
    }

    private void load(Path p) {
        try {
            Tree t = Tree.load(p);
            file = p;
            setTree(t);
        } catch (Exception e) {
            error("Couldn't open " + p + ":\n" + e.getMessage());
        }
    }

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(file != null ? file.toAbsolutePath().getParent().toFile() : new java.io.File("."));
        fc.setFileFilter(new FileNameExtensionFilter("Tree files (*.properties)", "properties"));
        return fc;
    }

    private void open() {
        if (!confirmDiscard()) return;
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) load(fc.getSelectedFile().toPath());
    }

    /** @return true if the file was saved */
    private boolean save() {
        if (file == null) return saveAs();
        applyCurrency();
        try {
            tree.save(file);
            dirty = false;
            updateTitle();
            return true;
        } catch (Exception e) {
            error("Couldn't save " + file + ":\n" + e.getMessage());
            return false;
        }
    }

    private boolean saveAs() {
        JFileChooser fc = chooser();
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return false;
        Path p = fc.getSelectedFile().toPath();
        if (!p.getFileName().toString().contains(".")) p = p.resolveSibling(p.getFileName() + ".properties");
        file = p;
        return save();
    }

    /** @return true if it's fine to throw away the current tree */
    private boolean confirmDiscard() {
        if (!dirty) return true;
        int r = JOptionPane.showConfirmDialog(frame, "Save changes first?", "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);
        return r == JOptionPane.NO_OPTION || (r == JOptionPane.YES_OPTION && save());
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Tree Editor", JOptionPane.ERROR_MESSAGE);
    }
}
