package com.folumo.tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads the base tree plus every mod in the {@code mods/} folder next to it.
 * A mod is a folder with any of: {@code tree.properties} (new nodes, or overrides of single keys),
 * {@code sounds/*.wav} (sound replacements), {@code mod.properties} (name, version, author).
 * Mods load in folder-name order; a broken mod is skipped and reported, never fatal.
 */
public final class Mods {
    public Tree tree;
    /** Display names of the mods that loaded, e.g. "Extra Branch 1.0 by Someone". */
    public final List<String> loaded = new ArrayList<>();
    /** One message per skipped mod or bad file. */
    public final List<String> errors = new ArrayList<>();
    /** Sound name (lower case, e.g. "gather") → replacement WAV; later mods win. */
    public final Map<String, Path> sounds = new LinkedHashMap<>();

    public static Path folder(Path treeFile) {
        return treeFile.toAbsolutePath().resolveSibling("mods");
    }

    public static Mods load(Path treeFile) throws IOException {
        Mods m = new Mods();
        Properties merged = Tree.read(treeFile);
        translate(merged, treeFile);
        m.tree = Tree.fromProperties(merged); // a broken base tree is the game's problem, so let it throw

        Path dir = folder(treeFile);
        if (!Files.isDirectory(dir)) return m;
        List<Path> mods;
        try (Stream<Path> s = Files.list(dir)) {
            mods = s.filter(Files::isDirectory).sorted().toList();
        }
        for (Path mod : mods) {
            String name = mod.getFileName().toString();
            try {
                Properties next = (Properties) merged.clone();
                Path modTree = mod.resolve("tree.properties");
                if (Files.exists(modTree)) {
                    Properties mp = Tree.read(modTree);
                    Set<String> ids = new LinkedHashSet<>(Tree.split(next.getProperty("nodes", "")));
                    ids.addAll(Tree.split(mp.getProperty("nodes", "")));
                    next.putAll(mp);
                    next.setProperty("nodes", String.join(",", ids));
                }
                translate(next, modTree); // a mod can also be just a translation: only tree_<lang>.properties
                Tree t = Tree.fromProperties(next);
                check(t, next);

                Map<String, Path> modSounds = new LinkedHashMap<>();
                Path soundDir = mod.resolve("sounds");
                if (Files.isDirectory(soundDir)) try (Stream<Path> s = Files.list(soundDir)) {
                    for (Path f : s.sorted().toList()) {
                        String file = f.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!file.endsWith(".wav")) throw new IllegalArgumentException("sounds/" + f.getFileName() + " is not a .wav file");
                        modSounds.put(file.substring(0, file.length() - 4), f);
                    }
                }

                merged = next;
                m.tree = t;
                m.sounds.putAll(modSounds);
                m.loaded.add(displayName(mod, name));
            } catch (Exception e) {
                m.errors.add(name + ": " + e.getMessage());
            }
        }
        return m;
    }

    /** Lays {@code tree_<lang>.properties} (names, descriptions, currency) over the English texts, if that file exists. */
    private static void translate(Properties target, Path englishTree) throws IOException {
        Path file = I18n.localized(englishTree);
        if (file.equals(englishTree) || !Files.exists(file)) return;
        Properties p = Tree.read(file);
        p.remove("nodes"); // a translation never adds or removes nodes
        target.putAll(p);
    }

    /** Catches mistakes the game would otherwise hide: links to missing nodes and loops. */
    private static void check(Tree t, Properties p) {
        for (Node n : t.nodes.values()) {
            for (String r : Tree.split(p.getProperty(n.id + ".requires", "")))
                if (!t.nodes.containsKey(r)) throw new IllegalArgumentException("node '" + n.id + "' requires unknown node '" + r + "'");
            for (String r : n.requires)
                if (t.wouldCycle(n.id, r)) throw new IllegalArgumentException("node '" + n.id + "' is part of a requirement loop");
        }
    }

    private static String displayName(Path mod, String folder) throws IOException {
        Path info = mod.resolve("mod.properties");
        if (!Files.exists(info)) return folder;
        Properties p = Tree.read(info);
        String s = p.getProperty("name", folder).trim();
        if (!p.getProperty("version", "").isBlank()) s += " " + p.getProperty("version").trim();
        if (!p.getProperty("author", "").isBlank()) s += " by " + p.getProperty("author").trim();
        return s;
    }
}
