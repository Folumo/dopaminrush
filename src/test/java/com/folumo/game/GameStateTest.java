package com.folumo.game;

import com.folumo.tree.Node;
import com.folumo.tree.Tree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    private final Tree tree = new Tree();
    private final Node root = tree.add(0, 0), child = tree.add(0, 0);

    GameStateTest() {
        root.cost = 10;
        root.income = 2;
        root.clickBonus = 1;
        child.cost = 5;
        child.requires.add(root.id);
    }

    @Test
    void buyingRules() {
        GameState s = new GameState(tree);
        s.points = 100;
        assertFalse(s.buy(child), "locked until root is owned");
        assertTrue(s.buy(root));
        assertEquals(90, s.points);
        assertFalse(s.buy(root), "level cap 1: can't buy twice");

        s.points = 1;
        assertFalse(s.buy(child), "not enough points");
        s.points = 5;
        assertTrue(s.buy(child));
        assertEquals(0, s.points);
        assertEquals(2, s.bought);
    }

    @Test
    void levelsCostMoreAndStopAtTheCap() {
        root.maxLevel = 3;
        GameState s = new GameState(tree);
        s.points = 1_000;
        assertEquals(10, s.cost(root));
        assertTrue(s.buy(root));
        assertEquals(10 * GameState.LEVEL_COST_GROWTH, s.cost(root), 1e-9);
        assertTrue(s.buy(root));
        assertTrue(s.buy(root));
        assertTrue(s.maxed(root));
        assertFalse(s.buy(root), "level cap reached");
        assertEquals(3, s.level(root));
        assertEquals(6, s.income(), "each level adds the income again");
        assertEquals(4, s.clickValue(), "1 + 3 levels of +1");
    }

    @Test
    void incomeAndClicks() {
        GameState s = new GameState(tree);
        s.click();
        assertEquals(1, s.points);
        s.levels.put(root.id, 1);
        s.click();
        assertEquals(3, s.points);
        s.play(1.5);
        assertEquals(6, s.points);
        assertEquals(2, s.clicks);
        assertEquals(1.5, s.playSeconds);
        assertEquals(6, s.earned);
    }

    @Test
    void rebornKeepsABonus(@TempDir Path dir) throws Exception {
        GameState s = new GameState(tree);
        s.points = 500;
        s.levels.put(root.id, 1);
        s.clicks = 7;
        s.reborn();
        assertEquals(0, s.points);
        assertTrue(s.levels.isEmpty());
        assertEquals(1, s.rebirths);
        assertEquals(7, s.clicks, "lifetime stats survive a rebirth");
        s.click();
        assertEquals(2, s.points, "1 per click, doubled by the first rebirth");
        s.levels.put(root.id, 1);
        assertEquals(4, s.income(), "root's 2/s doubled");

        Path f = dir.resolve("r.save");
        s.save(f);
        assertEquals(1, GameState.load(tree, f).rebirths);
    }

    @Test
    void saveLoadKeepsProgress(@TempDir Path dir) throws Exception {
        root.maxLevel = 5;
        GameState s = new GameState(tree);
        s.points = 42;
        s.levels.put(root.id, 4);
        s.levels.put(child.id, 1);
        s.clicks = 12;
        s.playSeconds = 99;
        Path f = dir.resolve("g.save");
        s.save(f);

        GameState l = GameState.load(tree, f);
        assertEquals(s.levels, l.levels);
        assertEquals(12, l.clicks);
        assertEquals(99, l.playSeconds);
        assertTrue(l.points >= 42, "offline earnings only add");
        assertEquals(l.points - 42, l.awayEarned, 1e-6);
        assertEquals(0, GameState.load(tree, dir.resolve("missing.save")).points);
    }

    @Test
    void oldSavesAndBadLevelsStillLoad(@TempDir Path dir) throws Exception {
        root.maxLevel = 5;
        Path f = Files.writeString(dir.resolve("old.save"),
                "points=3\nowned=" + root.id + ":99," + child.id + ",gone:2\ntime=" + System.currentTimeMillis() + "\n");
        GameState l = GameState.load(tree, f);
        assertEquals(5, l.level(root), "levels above the cap are clamped");
        assertEquals(1, l.level(child), "plain ids from older saves are level 1");
        assertFalse(l.owns("gone"), "removed nodes are dropped");
    }

    @Test
    void offlineEarningsAreAShareOfIncomeForLimitedTime(@TempDir Path dir) throws Exception {
        long tenHoursAgo = System.currentTimeMillis() - 10 * 3600_000L;
        Path f = Files.writeString(dir.resolve("o.save"), "points=0\nowned=" + root.id + "\ntime=" + tenHoursAgo + "\n");
        assertEquals(2 * 3600 * 0.05, GameState.load(tree, f).points, 0.01, "root's 2/s at 5%, capped at 1 hour");

        child.offlineRate = 0.25;
        child.offlineHours = 2;
        Files.writeString(f, "points=0\nowned=" + root.id + "," + child.id + "\ntime=" + tenHoursAgo + "\n");
        GameState s = GameState.load(tree, f);
        assertEquals(0.30, s.offlineRate(), 1e-9);
        assertEquals(3, s.offlineHours(), 1e-9);
        assertEquals(2 * 3 * 3600 * 0.30, s.points, 0.01);

        child.income = child.clickBonus = 0;
        s.levels.remove(child.id);
        s.points = 1e9;
        assertNotSame(child, s.bestNext(0), "the next-goal hint skips offline-only upgrades");
    }

    @Test
    void toleranceRisesWithPurchasesFadesAndCalmNodesClearIt() {
        root.maxLevel = 50;
        GameState s = new GameState(tree);
        s.points = 1e12;
        for (int i = 0; i < 10; i++) s.buy(root);
        assertEquals(10 * GameState.TOLERANCE_PER_BUY, s.tolerance, 1e-9);
        s.play(GameState.TOLERANCE_FADE_SECONDS);
        assertEquals(10 * GameState.TOLERANCE_PER_BUY / Math.E, s.tolerance, 1e-9, "fades with time");
        child.calm = true;
        s.buy(child);
        assertEquals(0, s.tolerance, "a calm node clears it");
    }

    @Test
    void sessionsAndLateNightsAreCounted() {
        GameState s = new GameState(tree);
        java.time.LocalDateTime night = java.time.LocalDateTime.of(2026, 11, 15, 2, 30);
        s.startSession(night);
        s.startSession(night.plusHours(1));
        assertEquals(2, s.sessionsToday);
        assertEquals(1, s.lateNights, "one late night per day, however often");
        s.startSession(night.plusDays(1).withHour(14));
        assertEquals(1, s.sessionsToday, "a new day starts the count over");
        assertEquals(1, s.lateNights);
    }

    @Test
    void achievementsAreEarnedOnceAndBoostEverything() {
        GameState s = new GameState(tree);
        s.clicks = 1_000;
        assertEquals(java.util.List.of(Achievement.CLICKS_1K), s.newAchievements());
        assertTrue(s.newAchievements().isEmpty(), "only once");
        assertEquals(1 + GameState.ACHIEVEMENT_BONUS, s.multiplier(), 1e-9);
    }

    @Test
    void everyAchievementAndPerkHasTexts() {
        com.folumo.tree.I18n.setLanguage("en");
        for (Achievement a : Achievement.values())
            for (String k : new String[]{a.key() + ".name", a.key() + ".text"})
                assertFalse(com.folumo.tree.I18n.t(k).startsWith("!"), "missing " + k);
        for (Perk p : Perk.values())
            for (String k : new String[]{p.key() + ".name", p.key() + ".text"})
                assertFalse(com.folumo.tree.I18n.t(k).startsWith("!"), "missing " + k);
    }

    @Test
    void rebirthGivesInsightForPerks() {
        GameState s = new GameState(tree);
        s.levels.put(root.id, 1);
        child.offlineRate = .1;
        s.levels.put(child.id, 1);
        s.reborn();
        assertEquals(GameState.INSIGHT_PER_REBIRTH, s.insight);
        assertTrue(s.buyPerk(Perk.KEEP_AUTOPILOT));
        assertTrue(s.buyPerk(Perk.QUICKSTART));
        assertFalse(s.buyPerk(Perk.SIGHT), "not enough Insight left");
        assertFalse(s.buyPerk(Perk.KEEP_AUTOPILOT), "max level");
        s.levels.put(root.id, 1);
        s.levels.put(child.id, 1);
        s.reborn();
        assertEquals(1000, s.points, "quick start");
        assertTrue(s.owns(child.id), "the offline-only node is kept");
        assertFalse(s.owns(root.id));
        assertTrue(s.buyPerk(Perk.DISCOUNT));
        assertEquals(10 * (1 - GameState.DISCOUNT_PER_LEVEL), s.cost(root), 1e-9);
    }

    @Test
    void newStatsSurviveSaving(@TempDir Path dir) throws Exception {
        GameState s = new GameState(tree);
        s.lateNights = 2;
        s.offersRefused = 3;
        s.molecules = 4;
        s.tolerance = .3;
        s.insight = 7;
        s.perks.put(Perk.SIGHT, 1);
        s.achievements.add(Achievement.REFUSED);
        s.history.addAll(java.util.List.of(1.0, 2.5));
        Path f = dir.resolve("s.save");
        s.save(f);
        GameState l = GameState.load(tree, f);
        assertEquals(2, l.lateNights);
        assertEquals(3, l.offersRefused);
        assertEquals(4, l.molecules);
        assertEquals(.3, l.tolerance, 1e-9);
        assertEquals(7, l.insight);
        assertEquals(1, l.perk(Perk.SIGHT));
        assertEquals(java.util.Set.of(Achievement.REFUSED), l.achievements);
        assertEquals(java.util.List.of(1.0, 2.5), l.history);
    }

    @Test
    void depthCountsStepsFromWhatIsOwned() {
        Node grandchild = tree.add(0, 0);
        grandchild.requires.add(child.id);
        GameState s = new GameState(tree);
        assertEquals(1, s.depths().get(root.id), "nothing needed first");
        assertEquals(2, s.depths().get(child.id));
        assertEquals(3, s.depths().get(grandchild.id));
        s.levels.put(root.id, 1);
        assertEquals(0, s.depths().get(root.id));
        assertEquals(1, s.depths().get(child.id));
        assertEquals(2, s.depths().get(grandchild.id));
    }

    @Test
    void bestNextPicksTheQuickestPayoff() {
        Node rich = tree.add(0, 0), poor = tree.add(0, 0);
        rich.cost = poor.cost = 100;
        rich.income = 10;
        poor.income = 1;
        GameState s = new GameState(tree);
        s.levels.put(root.id, 1);
        s.levels.put(child.id, 1);
        assertSame(rich, s.bestNext(0));
        s.levels.put(rich.id, rich.maxLevel);
        assertSame(poor, s.bestNext(0));
        s.levels.put(poor.id, 1);
        assertNull(s.bestNext(0), "everything bought");
    }
}
