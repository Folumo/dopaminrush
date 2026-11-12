package com.folumo.game;

import com.folumo.tree.Node;
import com.folumo.tree.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Game rules and progress, without any UI. */
public final class GameState {
    public final Tree tree;
    public double points;
    public final Set<String> owned = new HashSet<>();

    public GameState(Tree tree) {
        this.tree = tree;
    }

    public double income() {
        return owned.stream().mapToDouble(id -> tree.nodes.get(id).income).sum();
    }

    public double clickValue() {
        return 1 + owned.stream().mapToDouble(id -> tree.nodes.get(id).clickBonus).sum();
    }

    public boolean unlocked(Node n) {
        return owned.containsAll(n.requires);
    }

    public boolean canBuy(Node n) {
        return !owned.contains(n.id) && unlocked(n) && points >= n.cost;
    }

    public boolean buy(Node n) {
        if (!canBuy(n)) return false;
        points -= n.cost;
        owned.add(n.id);
        return true;
    }

    public void click() {
        points += clickValue();
    }

    public void tick(double seconds) {
        points += income() * seconds;
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("points", Double.toString(points));
        p.setProperty("owned", String.join(",", owned));
        p.setProperty("time", Long.toString(System.currentTimeMillis()));
        Tree.store(p, file, "Game progress");
    }

    /** Loads progress (or starts fresh) and adds what was earned while the game was closed. */
    public static GameState load(Tree tree, Path file) throws IOException {
        GameState s = new GameState(tree);
        if (!Files.exists(file)) return s;
        Properties p = Tree.read(file);
        s.points = Tree.num(p, "points");
        s.owned.addAll(Tree.split(p.getProperty("owned", "")));
        s.owned.retainAll(tree.nodes.keySet()); // nodes may have been deleted in the editor since
        long away = System.currentTimeMillis() - (long) Tree.num(p, "time");
        s.tick(Math.max(0, away) / 1000.0);
        return s;
    }
}
