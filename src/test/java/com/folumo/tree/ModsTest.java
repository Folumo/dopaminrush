package com.folumo.tree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModsTest {
    private static Path write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private static Path baseTree(Path dir) throws Exception {
        return write(dir.resolve("tree.properties"), """
                currency=Energy
                nodes=a
                a.name=Base
                a.desc=Original
                a.cost=10
                """);
    }

    @Test
    void noModsFolderIsJustTheBaseTree(@TempDir Path dir) throws Exception {
        Mods m = Mods.load(baseTree(dir));
        assertEquals(1, m.tree.nodes.size());
        assertTrue(m.loaded.isEmpty());
        assertTrue(m.errors.isEmpty());
    }

    @Test
    void modsAddNodesAndOverrideSingleKeys(@TempDir Path dir) throws Exception {
        Path tree = baseTree(dir);
        write(dir.resolve("mods/1-branch/tree.properties"), """
                nodes=b
                b.name=Modded
                b.cost=50
                b.requires=a
                a.desc=Changed by a mod
                """);
        write(dir.resolve("mods/1-branch/mod.properties"), "name=Branch\nversion=1.0\nauthor=Tester\n");
        write(dir.resolve("mods/2-later/tree.properties"), "nodes=c\nc.name=Later\nc.requires=b\n"); // can build on an earlier mod

        Mods m = Mods.load(tree);
        assertEquals(java.util.List.of("Branch 1.0 by Tester", "2-later"), m.loaded);
        assertTrue(m.errors.isEmpty(), m.errors.toString());
        assertEquals(3, m.tree.nodes.size());
        assertEquals("Changed by a mod", m.tree.nodes.get("a").description);
        assertEquals("Base", m.tree.nodes.get("a").name, "keys the mod didn't touch stay");
        assertEquals(java.util.Set.of("a"), m.tree.nodes.get("b").requires);
    }

    @Test
    void brokenModsAreSkippedNotFatal(@TempDir Path dir) throws Exception {
        Path tree = baseTree(dir);
        write(dir.resolve("mods/bad-number/tree.properties"), "nodes=x\nx.cost=lots\n");
        write(dir.resolve("mods/missing-link/tree.properties"), "nodes=y\ny.requires=nope\n");
        write(dir.resolve("mods/loop/tree.properties"), "nodes=p,q\np.requires=q\nq.requires=p\n");
        write(dir.resolve("mods/bad-sound/sounds/readme.txt"), "not a wav");
        write(dir.resolve("mods/good/tree.properties"), "nodes=g\ng.requires=a\n");
        write(dir.resolve("mods/good/sounds/Gather.WAV"), "");

        Mods m = Mods.load(tree);
        assertEquals(java.util.List.of("good"), m.loaded);
        assertEquals(4, m.errors.size(), m.errors.toString());
        assertTrue(m.errors.get(0).startsWith("bad-number:"));
        assertEquals(java.util.Set.of("a", "g"), m.tree.nodes.keySet(), "nothing from the broken mods leaks in");
        assertTrue(m.sounds.containsKey("gather"));
    }
}
