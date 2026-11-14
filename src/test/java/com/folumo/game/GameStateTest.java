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
