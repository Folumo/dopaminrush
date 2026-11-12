package com.folumo.game;

import com.folumo.tree.Node;
import com.folumo.tree.Tree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        assertFalse(s.buy(root), "can't buy twice");

        s.points = 1;
        assertFalse(s.buy(child), "not enough points");
        s.points = 5;
        assertTrue(s.buy(child));
        assertEquals(0, s.points);
    }

    @Test
    void incomeAndClicks() {
        GameState s = new GameState(tree);
        s.click();
        assertEquals(1, s.points);
        s.owned.add(root.id);
        s.click();
        assertEquals(3, s.points);
        s.tick(1.5);
        assertEquals(6, s.points);
    }

    @Test
    void saveLoadKeepsProgress(@TempDir Path dir) throws Exception {
        GameState s = new GameState(tree);
        s.points = 42;
        s.owned.add(root.id);
        Path f = dir.resolve("g.save");
        s.save(f);

        GameState l = GameState.load(tree, f);
        assertEquals(s.owned, l.owned);
        assertTrue(l.points >= 42, "offline earnings only add");
        assertEquals(0, GameState.load(tree, dir.resolve("missing.save")).points);
    }
}
