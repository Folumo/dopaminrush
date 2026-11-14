package com.folumo.editor;

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
import java.util.LinkedHashSet;
import java.util.Properties;
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
    private final JTextField cost = new JTextField(), income = new JTextField(), click = new JTextField(),
            maxLevel = new JTextField(), requires = new JTextField();
    private final JLabel modeNote = new JLabel();

    // Mod mode: the main tree is shown read-only, and saving writes only the mod's own nodes.
    private Set<String> baseIds = Set.of();
    private Properties modOriginal; // the mod's tree.properties as last loaded/saved; null when not editing a mod
    private String baseCurrency;

    /** {@code EditorApp [tree.properties]} or {@code EditorApp --mod <mods/my-mod>} */
    public static void main(String[] args) {
        boolean mod = args.length > 1 && args[0].equals("--mod");
        Path start = Path.of(mod ? "tree.properties" : args.length > 0 ? args[0] : "tree.properties");
        SwingUtilities.invokeLater(() -> {
            EditorApp app = new EditorApp(start);
            if (mod) app.loadMod(Path.of(args[1]));
        });
    }

    private EditorApp(Path start) {
        file = start;
        if (Files.exists(start)) load(start);

        canvas.movable = n -> !baseIds.contains(n.id);
        canvas.fill = n -> baseIds.contains(n.id) ? Theme.mix(Theme.LOCKED, Theme.MUTED, .35) // main tree, read-only
                : n.requires.isEmpty() ? Theme.GOLD : Theme.ACCENT; // gold = starting nodes
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
        bar.add(button("New", () -> { if (confirmDiscard()) { file = null; leaveModMode(); setTree(new Tree()); } }));
        bar.add(button("Open...", this::open));
        bar.add(button("Open mod...", this::openMod));
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
        modeNote.setForeground(new Color(0x7c6fc4));
        p.add(modeNote, c);
        Object[][] rows = {{"Id", idLabel}, {"Name", name},
                {"<html>Shape <font color=gray>(square = normal, circle = important,<br>hexagon = milestone that starts new branches)</font></html>", shape},
                {"Description", new JScrollPane(desc)},
                {"Cost (first level)", cost}, {"Income per second (per level)", income}, {"Click bonus (per level)", click},
                {"<html>Max level <font color=gray>(times it can be bought; each level costs<br>"
                        + String.format(java.util.Locale.ROOT, "%.1f", com.folumo.game.GameState.LEVEL_COST_GROWTH) + "x the one before)</font></html>", maxLevel},
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
        maxLevel.setText(n == null ? "" : Integer.toString(n.maxLevel));
        requires.setText(n == null ? "" : String.join(", ", n.requires));
        boolean editable = n != null && !baseIds.contains(n.id);
        for (JComponent f : new JComponent[]{name, shape, desc, cost, income, click, maxLevel, requires}) f.setEnabled(editable);
    }

    private void apply() {
        applyCurrency();
        Node n = canvas.selected;
        if (n == null || baseIds.contains(n.id)) return;
        try {
            double c = parse(cost, "Cost"), inc = parse(income, "Income"), cl = parse(click, "Click bonus"), max = parse(maxLevel, "Max level");
            if (c < 0) throw new IllegalArgumentException("Cost can't be negative.");
            if (max < 1 || max != Math.floor(max)) throw new IllegalArgumentException("Max level must be a whole number, 1 or more.");
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
            n.maxLevel = (int) max;
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
        if (baseIds.contains(sel.id)) {
            error("Main-tree nodes can't be changed in a mod. Select one of your nodes, then Shift+click this one to link it.");
            return;
        }
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
        Node n = tree.add(p.x, p.y, modOriginal == null ? "n" : modPrefix());
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
        if (baseIds.contains(canvas.selected.id)) {
            error("Main-tree nodes can't be deleted in a mod.");
            return;
        }
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
        String what = file == null ? "untitled" : modOriginal != null ? "mod " + file.getParent().getFileName() : file.getFileName().toString();
        frame.setTitle("Tree Editor - " + what + (dirty ? " *" : ""));
        modeNote.setText(modOriginal == null ? "" : "<html><b>Editing a mod.</b> Grey nodes are the main tree:<br>"
                + "link to them with Shift+click, but they can't<br>be changed here. Save writes only your nodes.<br><br></html>");
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
            leaveModMode();
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
            if (modOriginal != null) saveMod();
            else tree.save(file);
            dirty = false;
            updateTitle();
            return true;
        } catch (Exception e) {
            error("Couldn't save " + file + ":\n" + e.getMessage());
            return false;
        }
    }

    private boolean saveAs() {
        if (modOriginal != null) {
            error("A mod always saves into its own folder. Use Save.");
            return false;
        }
        JFileChooser fc = chooser();
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return false;
        Path p = fc.getSelectedFile().toPath();
        if (!p.getFileName().toString().contains(".")) p = p.resolveSibling(p.getFileName() + ".properties");
        file = p;
        return save();
    }

    private void leaveModMode() {
        baseIds = Set.of();
        modOriginal = null;
    }

    /** Ids of new mod nodes start with the mod's folder name, so two mods don't pick the same id. */
    private String modPrefix() {
        return file.getParent().getFileName().toString().replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(java.util.Locale.ROOT) + "_";
    }

    /** Pick a folder inside mods/ (or its tree.properties); the main tree is the tree.properties next to mods/. */
    private void openMod() {
        if (!confirmDiscard()) return;
        Path start = file != null ? Mods.folder(file) : Mods.folder(Path.of("tree.properties"));
        JFileChooser fc = new JFileChooser(Files.isDirectory(start) ? start.toFile() : new java.io.File("."));
        fc.setDialogTitle("Pick a mod folder (inside mods/)");
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) loadMod(fc.getSelectedFile().toPath());
    }

    /** @param picked a mod folder, or its tree.properties (which may not exist yet for a new mod) */
    private void loadMod(Path picked) {
        picked = picked.toAbsolutePath();
        Path modTree = Files.isDirectory(picked) ? picked.resolve("tree.properties") : picked;
        Path base = modTree.getParent().getParent().resolveSibling("tree.properties"); // <game>/mods/<mod>/tree.properties
        if (!Files.exists(base)) {
            JFileChooser bc = chooser();
            bc.setDialogTitle("Where is the main tree.properties this mod builds on?");
            if (bc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            base = bc.getSelectedFile().toPath();
        }
        try {
            Properties baseProps = Tree.read(base);
            Properties mod = Files.exists(modTree) ? Tree.read(modTree) : new Properties();
            Properties merged = (Properties) baseProps.clone();
            Mods.merge(merged, mod);
            Tree t = Tree.fromProperties(merged);
            file = modTree;
            baseIds = Set.copyOf(Tree.split(baseProps.getProperty("nodes", "")));
            baseCurrency = baseProps.getProperty("currency", t.currency);
            modOriginal = mod;
            setTree(t);
        } catch (Exception e) {
            leaveModMode();
            error("Couldn't open the mod:\n" + e.getMessage());
        }
    }

    /**
     * Writes the mod's own nodes (and nothing of the main tree) back into its tree.properties.
     * Other keys the mod had, like overrides of main-tree texts, are kept as they were.
     */
    private void saveMod() throws java.io.IOException {
        Properties out = Mods.modPart(modOriginal, tree, baseIds, baseCurrency);
        Files.createDirectories(file.getParent());
        Tree.store(out, file, "Mod tree, made with the tree editor");
        modOriginal = out;
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
