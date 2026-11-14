package com.folumo.tree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TreeTest {
    @Test
    void saveLoadRoundTrip(@TempDir Path dir) throws Exception {
        Tree t = new Tree();
        t.currency = "Ätherium";
        Node a = t.add(10, 20);
        a.name = "Čelik = steel, #1";
        a.description = "line one\nline two";
        a.income = 2.5;
        Node b = t.add(30, 40);
        b.requires.add(a.id);
        b.clickBonus = 3;
        b.shape = Node.Shape.HEXAGON;

        Path f = dir.resolve("t.properties");
        t.save(f);
        Tree l = Tree.load(f);

        assertEquals("Ätherium", l.currency);
        assertEquals(t.nodes.keySet(), l.nodes.keySet());
        Node la = l.nodes.get(a.id), lb = l.nodes.get(b.id);
        assertEquals(a.name, la.name);
        assertEquals(a.description, la.description);
        assertEquals(2.5, la.income);
        assertEquals(10, la.x);
        assertEquals(3, lb.clickBonus);
        assertEquals(b.requires, lb.requires);
        assertEquals(Node.Shape.SQUARE, la.shape);
        assertEquals(Node.Shape.HEXAGON, lb.shape);
    }

    @Test
    void wouldCycle() {
        Tree t = new Tree();
        Node a = t.add(0, 0), b = t.add(0, 0), c = t.add(0, 0);
        b.requires.add(a.id);
        c.requires.add(b.id);
        assertTrue(t.wouldCycle(a.id, c.id));  // a <- b <- c, so a can't require c
        assertTrue(t.wouldCycle(a.id, a.id));
        assertFalse(t.wouldCycle(c.id, a.id));

        t.remove(b.id);
        assertTrue(c.requires.isEmpty());
    }

    @Test
    void format() {
        assertEquals("0", Fmt.num(0));
        assertEquals("0.5", Fmt.num(0.5));
        assertEquals("999", Fmt.num(999));
        assertEquals("1.25K", Fmt.num(1250));
        assertEquals("3.40M", Fmt.num(3_400_000));
    }
}
