package com.folumo.game;

import com.folumo.tree.Node;
import com.folumo.tree.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Game rules and progress, without any UI. */
public final class GameState {
    public final Tree tree;
    public double points;
    /** Node id → level bought (1..maxLevel). A node is owned once it has level 1. */
    public final Map<String, Integer> levels = new LinkedHashMap<>();
    public int rebirths;

    /** Lifetime stats, kept through rebirths and shown before the ending video. */
    public long clicks, bought;
    public double playSeconds, earned;
    /** What {@link #load} added for the time the game was closed. */
    public double awaySeconds, awayEarned;

    /** Extra multiplier on all income and clicks earned per rebirth: 1 rebirth = x2, 2 = x3, ... */
    public static final double REBIRTH_BONUS = 1.0;
    /** Each level of a node costs this much more than the one before. */
    public static final double LEVEL_COST_GROWTH = 1.3;

    public GameState(Tree tree) {
        this.tree = tree;
    }

    public double multiplier() {
        return 1 + rebirths * REBIRTH_BONUS;
    }

    public int level(Node n) {
        return levels.getOrDefault(n.id, 0);
    }

    public boolean owns(String id) {
        return levels.containsKey(id);
    }

    public boolean maxed(Node n) {
        return level(n) >= n.maxLevel;
    }

    /** Price of the next level. */
    public double cost(Node n) {
        return n.cost * Math.pow(LEVEL_COST_GROWTH, level(n));
    }

    public double income() {
        return levels.entrySet().stream().mapToDouble(e -> tree.nodes.get(e.getKey()).income * e.getValue()).sum() * multiplier();
    }

    public double clickValue() {
        return (1 + levels.entrySet().stream().mapToDouble(e -> tree.nodes.get(e.getKey()).clickBonus * e.getValue()).sum()) * multiplier();
    }

    /** Start the tree over, keeping a permanent bonus (and the lifetime stats). */
    public void reborn() {
        rebirths++;
        points = 0;
        levels.clear();
    }

    public boolean unlocked(Node n) {
        return levels.keySet().containsAll(n.requires);
    }

    /** Unlocked and not at its level cap; it may still be too expensive. */
    public boolean available(Node n) {
        return !maxed(n) && unlocked(n);
    }

    public boolean canBuy(Node n) {
        return available(n) && points >= cost(n);
    }

    public boolean buy(Node n) {
        if (!canBuy(n)) return false;
        points -= cost(n);
        levels.merge(n.id, 1, Integer::sum);
        bought++;
        return true;
    }

    public void click() {
        double v = clickValue();
        points += v;
        earned += v;
        clicks++;
    }

    public void tick(double seconds) {
        double v = income() * seconds;
        points += v;
        earned += v;
    }

    /** Time passing while the game is open. */
    public void play(double seconds) {
        tick(seconds);
        playSeconds += seconds;
    }

    /**
     * The purchase that pays off soonest: time to save up for it plus time for it to pay for itself.
     * Used for the "next goal" hint and by the pacing simulation. Nodes that earn nothing (like the
     * final node) only count their saving-up time, so they win once nothing else is worth it.
     *
     * @param clicksPerSecond how often the player is assumed to click Gather
     * @return null if nothing can be bought anymore
     */
    public Node bestNext(double clicksPerSecond) {
        double rate = income() + clickValue() * clicksPerSecond;
        Node best = null;
        double bestScore = Double.MAX_VALUE;
        for (Node n : tree.nodes.values()) {
            if (!available(n)) continue;
            double c = cost(n), gain = (n.income + n.clickBonus * clicksPerSecond) * multiplier();
            double score = Math.max(0, c - points) / rate + (gain > 0 ? c / gain : 0);
            if (score < bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("points", Double.toString(points));
        List<String> owned = new ArrayList<>();
        levels.forEach((id, lvl) -> owned.add(lvl == 1 ? id : id + ":" + lvl));
        p.setProperty("owned", String.join(",", owned));
        p.setProperty("rebirths", Integer.toString(rebirths));
        p.setProperty("clicks", Long.toString(clicks));
        p.setProperty("bought", Long.toString(bought));
        p.setProperty("played", Double.toString(playSeconds));
        p.setProperty("earned", Double.toString(earned));
        p.setProperty("time", Long.toString(System.currentTimeMillis()));
        Tree.store(p, file, "Game progress");
    }

    /** Loads progress (or starts fresh) and adds what was earned while the game was closed. */
    public static GameState load(Tree tree, Path file) throws IOException {
        GameState s = new GameState(tree);
        if (!Files.exists(file)) return s;
        Properties p = Tree.read(file);
        s.points = Tree.num(p, "points");
        for (String entry : Tree.split(p.getProperty("owned", ""))) { // "id" (level 1) or "id:level"
            String[] parts = entry.split(":", 2);
            Node n = tree.nodes.get(parts[0].trim());
            if (n == null) continue; // deleted in the editor, or its mod was removed
            int lvl = 1;
            if (parts.length > 1) try {
                lvl = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                // a hand-edited level that isn't a number counts as level 1
            }
            s.levels.put(n.id, Math.max(1, Math.min(n.maxLevel, lvl)));
        }
        s.rebirths = Math.max(0, (int) Tree.num(p, "rebirths"));
        s.clicks = (long) Tree.num(p, "clicks");
        s.bought = (long) Tree.num(p, "bought");
        s.playSeconds = Tree.num(p, "played");
        s.earned = Tree.num(p, "earned");
        s.awaySeconds = Math.max(0, System.currentTimeMillis() - (long) Tree.num(p, "time")) / 1000.0;
        double before = s.points;
        s.tick(s.awaySeconds);
        s.awayEarned = s.points - before;
        return s;
    }
}
