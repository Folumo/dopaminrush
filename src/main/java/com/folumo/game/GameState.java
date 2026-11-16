package com.folumo.game;

import com.folumo.tree.Node;
import com.folumo.tree.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

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

    /** What the game notices about how it's played (it mirrors some of it back to the player). */
    public long lastDay, lastLateDay; // epoch days
    public int sessionsToday, lateNights;
    public int offersTaken, offersRefused, molecules;
    /** 0..MAX_TOLERANCE: rises with every purchase and fades with time; calm nodes clear it. It dulls pops and sounds. */
    public double tolerance;
    public final Set<Achievement> achievements = EnumSet.noneOf(Achievement.class);
    /** Earned by rebirths, spent on perks that last forever. */
    public int insight;
    public final Map<Perk, Integer> perks = new EnumMap<>(Perk.class);
    /** Income (without event boosts) sampled once per minute of play, newest last. */
    public final List<Double> history = new ArrayList<>();
    /** Temporary multiplier from events, like a golden molecule's rush. Not saved. */
    public double boost = 1;

    /** Extra multiplier on all income and clicks earned per rebirth: 1 rebirth = x2, 2 = x3, ... */
    public static final double REBIRTH_BONUS = 1.0;
    /** While the game is closed it earns this share of the income, for at most this many hours; offline nodes raise both. */
    public static final double BASE_OFFLINE_RATE = 0.05, BASE_OFFLINE_HOURS = 1;
    /** Each level of a node costs this much more than the one before. */
    public static final double LEVEL_COST_GROWTH = 1.3;
    public static final double ACHIEVEMENT_BONUS = 0.02;
    public static final int INSIGHT_PER_REBIRTH = 5;
    public static final double TOLERANCE_PER_BUY = 0.02, MAX_TOLERANCE = 0.8, TOLERANCE_FADE_SECONDS = 600;
    public static final double DISCOUNT_PER_LEVEL = 0.1;
    public static final int HISTORY_MINUTES = 240;

    public GameState(Tree tree) {
        this.tree = tree;
    }

    public double multiplier() {
        return (1 + rebirths * REBIRTH_BONUS) * (1 + ACHIEVEMENT_BONUS * achievements.size());
    }

    public int perk(Perk p) {
        return perks.getOrDefault(p, 0);
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
        return n.cost * Math.pow(LEVEL_COST_GROWTH, level(n)) * (1 - DISCOUNT_PER_LEVEL * perk(Perk.DISCOUNT));
    }

    public double income() {
        return levels.entrySet().stream().mapToDouble(e -> tree.nodes.get(e.getKey()).income * e.getValue()).sum() * multiplier() * boost;
    }

    public double clickValue() {
        return (1 + levels.entrySet().stream().mapToDouble(e -> tree.nodes.get(e.getKey()).clickBonus * e.getValue()).sum()) * multiplier() * boost;
    }

    public double offlineRate() {
        return BASE_OFFLINE_RATE + levels.entrySet().stream().mapToDouble(e -> tree.nodes.get(e.getKey()).offlineRate * e.getValue()).sum();
    }

    public double offlineHours() {
        return BASE_OFFLINE_HOURS + levels.entrySet().stream().mapToDouble(e -> tree.nodes.get(e.getKey()).offlineHours * e.getValue()).sum();
    }

    /** Start the tree over, keeping a permanent bonus, Insight to spend, perks and the lifetime stats. */
    public void reborn() {
        rebirths++;
        insight += INSIGHT_PER_REBIRTH;
        points = perk(Perk.QUICKSTART) > 0 ? Math.pow(1000, perk(Perk.QUICKSTART)) : 0;
        if (perk(Perk.KEEP_AUTOPILOT) > 0) levels.keySet().removeIf(id -> !offlineOnly(tree.nodes.get(id)));
        else levels.clear();
        tolerance = 0;
    }

    public boolean buyPerk(Perk p) {
        int lvl = perk(p);
        if (lvl >= p.max || insight < p.cost(lvl)) return false;
        insight -= p.cost(lvl);
        perks.put(p, lvl + 1);
        return true;
    }

    public boolean unlocked(Node n) {
        return levels.keySet().containsAll(n.requires);
    }

    /** Unlocked and not at its level cap; it may still be too expensive. */
    public boolean available(Node n) {
        return !maxed(n) && unlocked(n);
    }

    /**
     * How far each node is from what the player owns: 0 = owned, 1 = all requirements owned,
     * 2 = needs one more step first, and so on. The game hides nodes that are too far away.
     */
    public Map<String, Integer> depths() {
        Map<String, Integer> d = new HashMap<>();
        for (Node n : tree.nodes.values()) depth(n, d);
        return d;
    }

    private int depth(Node n, Map<String, Integer> d) {
        Integer known = d.get(n.id);
        if (known != null) return known;
        int v = 0;
        if (!owns(n.id)) {
            v = 1;
            for (String r : n.requires) v = Math.max(v, 1 + depth(tree.nodes.get(r), d));
        }
        d.put(n.id, v);
        return v;
    }

    public boolean canBuy(Node n) {
        return available(n) && points >= cost(n);
    }

    public boolean buy(Node n) {
        if (!canBuy(n)) return false;
        points -= cost(n);
        levels.merge(n.id, 1, Integer::sum);
        bought++;
        tolerance = n.calm ? 0 : Math.min(MAX_TOLERANCE, tolerance + TOLERANCE_PER_BUY);
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
        long minuteBefore = (long) (playSeconds / 60);
        playSeconds += seconds;
        tolerance *= Math.exp(-seconds / TOLERANCE_FADE_SECONDS);
        if ((long) (playSeconds / 60) != minuteBefore) {
            history.add(income() / boost);
            if (history.size() > HISTORY_MINUTES) history.removeFirst();
        }
    }

    /** Counts this start of the game: how often today, and whether it's the middle of the night. */
    public void startSession(LocalDateTime now) {
        long day = now.toLocalDate().toEpochDay();
        sessionsToday = day == lastDay ? sessionsToday + 1 : 1;
        lastDay = day;
        if (now.getHour() < 5 && lastLateDay != day) {
            lateNights++;
            lastLateDay = day;
        }
    }

    /** Marks and returns the achievements reached since the last call. */
    public List<Achievement> newAchievements() {
        List<Achievement> fresh = new ArrayList<>();
        for (Achievement a : Achievement.values())
            if (!achievements.contains(a) && a.reached.test(this)) {
                achievements.add(a);
                fresh.add(a);
            }
        return fresh;
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
            if (!available(n) || offlineOnly(n)) continue; // offline upgrades are the player's choice, not a payoff
            double c = cost(n), gain = (n.income + n.clickBonus * clicksPerSecond) * multiplier();
            double score = Math.max(0, c - points) / rate + (gain > 0 ? c / gain : 0);
            if (score < bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    private static boolean offlineOnly(Node n) {
        return n.income == 0 && n.clickBonus == 0 && (n.offlineRate != 0 || n.offlineHours != 0);
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
        p.setProperty("lastDay", Long.toString(lastDay));
        p.setProperty("lastLateDay", Long.toString(lastLateDay));
        p.setProperty("sessionsToday", Integer.toString(sessionsToday));
        p.setProperty("lateNights", Integer.toString(lateNights));
        p.setProperty("offersTaken", Integer.toString(offersTaken));
        p.setProperty("offersRefused", Integer.toString(offersRefused));
        p.setProperty("molecules", Integer.toString(molecules));
        p.setProperty("tolerance", Double.toString(tolerance));
        p.setProperty("achievements", String.join(",", achievements.stream().map(Enum::name).toList()));
        p.setProperty("insight", Integer.toString(insight));
        List<String> perkList = new ArrayList<>();
        perks.forEach((k, v) -> perkList.add(k.name() + ":" + v));
        p.setProperty("perks", String.join(",", perkList));
        p.setProperty("history", String.join(",", history.stream().map(d -> Double.toString(d)).toList()));
        p.setProperty("time", Long.toString(System.currentTimeMillis()));
        Tree.store(p, file, "Game progress");
    }

    /** Loads progress (or starts fresh) and adds what was earned while the game was closed. */
    public static GameState load(Tree tree, Path file) throws IOException {
        GameState s = new GameState(tree);
        if (!Files.exists(file)) return s;
        Properties p = Tree.read(file);
        s.points = Tree.num(p, "points");
        levelList(p.getProperty("owned", "")).forEach((id, lvl) -> { // "id" (level 1) or "id:level"
            Node n = tree.nodes.get(id);
            if (n != null) s.levels.put(n.id, Math.min(n.maxLevel, lvl)); // missing: deleted in the editor, or its mod was removed
        });
        s.rebirths = Math.max(0, (int) Tree.num(p, "rebirths"));
        s.clicks = (long) Tree.num(p, "clicks");
        s.bought = (long) Tree.num(p, "bought");
        s.playSeconds = Tree.num(p, "played");
        s.earned = Tree.num(p, "earned");
        s.lastDay = (long) Tree.num(p, "lastDay");
        s.lastLateDay = (long) Tree.num(p, "lastLateDay");
        s.sessionsToday = (int) Tree.num(p, "sessionsToday");
        s.lateNights = (int) Tree.num(p, "lateNights");
        s.offersTaken = (int) Tree.num(p, "offersTaken");
        s.offersRefused = (int) Tree.num(p, "offersRefused");
        s.molecules = (int) Tree.num(p, "molecules");
        s.tolerance = Math.max(0, Math.min(MAX_TOLERANCE, Tree.num(p, "tolerance")));
        for (String a : Tree.split(p.getProperty("achievements", "")))
            try {
                s.achievements.add(Achievement.valueOf(a));
            } catch (IllegalArgumentException e) {
                // an achievement from another version of the game: ignore it
            }
        s.insight = Math.max(0, (int) Tree.num(p, "insight"));
        levelList(p.getProperty("perks", "")).forEach((name, lvl) -> {
            try {
                Perk perk = Perk.valueOf(name);
                s.perks.put(perk, Math.min(perk.max, lvl));
            } catch (IllegalArgumentException e) {
                // unknown perk from another version: ignore it
            }
        });
        for (String h : Tree.split(p.getProperty("history", "")))
            try {
                s.history.add(Double.parseDouble(h));
            } catch (NumberFormatException e) {
                // skip a damaged sample
            }
        s.awaySeconds = Math.max(0, System.currentTimeMillis() - (long) Tree.num(p, "time")) / 1000.0;
        double before = s.points;
        s.tick(Math.min(s.awaySeconds, s.offlineHours() * 3600) * s.offlineRate());
        s.awayEarned = s.points - before;
        return s;
    }

    /** Parses "a,b:3" into {a=1, b=3}; levels below 1 or not numbers count as 1. */
    private static Map<String, Integer> levelList(String s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String entry : Tree.split(s)) {
            String[] parts = entry.split(":", 2);
            int lvl = 1;
            if (parts.length > 1) try {
                lvl = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                // a hand-edited level that isn't a number counts as level 1
            }
            out.put(parts[0].trim(), Math.max(1, lvl));
        }
        return out;
    }
}
