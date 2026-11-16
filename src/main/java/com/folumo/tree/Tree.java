package com.folumo.tree;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** The whole upgrade tree, shared by the editor and the game. */
public final class Tree {
    public String currency = "Energy";
    public final Map<String, Node> nodes = new LinkedHashMap<>();

    public Node add(int x, int y) {
        return add(x, y, "n");
    }

    /** Adds a node with the first free id {@code <prefix><number>}. */
    public Node add(int x, int y, String prefix) {
        int i = 1;
        while (nodes.containsKey(prefix + i)) i++;
        Node n = new Node(prefix + i, "New node");
        n.x = x;
        n.y = y;
        n.cost = 10;
        nodes.put(n.id, n);
        return n;
    }

    public void remove(String id) {
        nodes.remove(id);
        for (Node n : nodes.values()) n.requires.remove(id);
    }

    /** True if making {@code node} require {@code req} would create a loop. */
    public boolean wouldCycle(String node, String req) {
        Deque<String> todo = new ArrayDeque<>(List.of(req));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String cur = todo.pop();
            if (cur.equals(node)) return true;
            Node n = nodes.get(cur);
            if (seen.add(cur) && n != null) todo.addAll(n.requires);
        }
        return false;
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("currency", currency);
        p.setProperty("nodes", String.join(",", nodes.keySet()));
        for (Node n : nodes.values()) put(p, n);
        store(p, file, "Node tree");
    }

    /** Writes one node's keys into {@code p}. */
    public static void put(Properties p, Node n) {
        String k = n.id + ".";
        p.setProperty(k + "name", n.name);
        p.setProperty(k + "desc", n.description);
        p.setProperty(k + "cost", Double.toString(n.cost));
        p.setProperty(k + "income", Double.toString(n.income));
        p.setProperty(k + "click", Double.toString(n.clickBonus));
        p.setProperty(k + "shape", n.shape.name().toLowerCase(Locale.ROOT));
        p.setProperty(k + "max", Integer.toString(n.maxLevel));
        if (n.offlineRate != 0) p.setProperty(k + "offline", Double.toString(n.offlineRate));
        if (n.offlineHours != 0) p.setProperty(k + "offlineHours", Double.toString(n.offlineHours));
        if (n.calm) p.setProperty(k + "calm", "true");
        p.setProperty(k + "x", Integer.toString(n.x));
        p.setProperty(k + "y", Integer.toString(n.y));
        p.setProperty(k + "requires", String.join(",", n.requires));
    }

    public static Tree load(Path file) throws IOException {
        return fromProperties(read(file));
    }

    public static Tree fromProperties(Properties p) {
        Tree t = new Tree();
        t.currency = p.getProperty("currency", t.currency);
        for (String id : split(p.getProperty("nodes", ""))) {
            String k = id + ".";
            Node n = new Node(id, p.getProperty(k + "name", id));
            n.description = p.getProperty(k + "desc", "");
            n.cost = num(p, k + "cost");
            n.income = num(p, k + "income");
            n.clickBonus = num(p, k + "click");
            n.offlineRate = num(p, k + "offline");
            n.offlineHours = num(p, k + "offlineHours");
            n.calm = Boolean.parseBoolean(p.getProperty(k + "calm", "false").trim());
            String shape = p.getProperty(k + "shape", "square").trim();
            try {
                n.shape = Node.Shape.valueOf(shape.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Bad shape for '" + id + "': " + shape + " (use square, circle or hexagon)");
            }
            n.maxLevel = p.containsKey(k + "max") ? Math.max(1, (int) num(p, k + "max")) : Node.defaultMax(n.shape);
            n.x = (int) num(p, k + "x");
            n.y = (int) num(p, k + "y");
            n.requires.addAll(split(p.getProperty(k + "requires", "")));
            t.nodes.put(id, n);
        }
        for (Node n : t.nodes.values()) n.requires.retainAll(t.nodes.keySet()); // drop links to missing nodes
        return t;
    }

    /** Writes to a temp file first so a crash mid-write can't corrupt the existing file. */
    public static void store(Properties p, Path file, String comment) throws IOException {
        Path abs = file.toAbsolutePath();
        Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, comment);
        }
        Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static Properties read(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    public static List<String> split(String s) {
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public static double num(Properties p, String key) {
        String v = p.getProperty(key, "0").trim();
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad number for '" + key + "': " + v);
        }
    }
}
