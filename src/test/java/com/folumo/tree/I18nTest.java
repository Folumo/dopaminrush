package com.folumo.tree;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    @AfterEach
    void backToEnglish() {
        I18n.setLanguage("en");
    }

    @Test
    void durationsInEnglishAndCroatian() {
        I18n.setLanguage("en");
        assertEquals("30 minutes", I18n.duration(30));
        assertEquals("1 hour", I18n.duration(60));
        assertEquals("1 hour 30 minutes", I18n.duration(90));
        assertEquals("2 hours", I18n.duration(120));

        I18n.setLanguage("hr"); // three plural forms: 1 sat, 2 sata, 5 sati
        assertEquals("1 sat", I18n.duration(60));
        assertEquals("2 sata", I18n.duration(120));
        assertEquals("5 sati", I18n.duration(300));
        assertEquals("21 sat", I18n.duration(21 * 60));
        assertEquals("1 sat i 30 minuta", I18n.duration(90));
    }

    @Test
    void textsFillArgumentsAndFallBack() {
        I18n.setLanguage("de");
        assertEquals("Sammeln  +5", I18n.t("gather", 5));
        I18n.setLanguage("xx");
        assertEquals("en", I18n.language(), "unknown languages fall back to English");
        assertEquals("!no.such.key!", I18n.t("no.such.key"));
    }

    /** Every language must translate every English key, so nothing silently shows up in English. */
    @Test
    void everyLanguageHasEveryKey() throws Exception {
        Properties en = props("/i18n/messages.properties");
        for (String lang : I18n.LANGUAGES) {
            if (lang.equals("en")) continue;
            Properties p = props("/i18n/messages_" + lang + ".properties");
            for (String key : en.stringPropertyNames()) {
                boolean pluralFormOnly = key.endsWith(".one") || key.endsWith(".few");
                if (!pluralFormOnly) assertTrue(p.containsKey(key), lang + " is missing " + key);
            }
        }
    }

    @Test
    void translatedTreesAndFiles(@TempDir Path dir) throws Exception {
        Path tree = Files.writeString(dir.resolve("tree.properties"), "currency=Energy\nnodes=a\na.name=Start\na.cost=7\n");
        Files.writeString(dir.resolve("tree_hr.properties"), "currency=Energija\nnodes=sneaky\na.name=Početak\n");
        Files.writeString(dir.resolve("credits_hr.txt"), "hvala");

        I18n.setLanguage("hr");
        Tree t = Mods.load(tree).tree;
        assertEquals("Energija", t.currency);
        assertEquals("Početak", t.nodes.get("a").name);
        assertEquals(7, t.nodes.get("a").cost, "numbers come from the English tree");
        assertEquals(1, t.nodes.size(), "translations can't add nodes");
        assertEquals("credits_hr.txt", I18n.localized(dir.resolve("credits.txt")).getFileName().toString());
        assertEquals("ending.mp4", I18n.localized(dir.resolve("ending.mp4")).getFileName().toString(), "falls back when missing");
    }

    /** The shipped tree translations must cover every node of the shipped tree, and nothing else. */
    @Test
    void shippedTreeIsFullyTranslated() throws Exception {
        Tree en = Tree.load(Path.of("tree.properties"));
        for (String lang : I18n.LANGUAGES) {
            if (lang.equals("en")) continue;
            Properties p = Tree.read(Path.of("tree_" + lang + ".properties"));
            assertTrue(p.containsKey("currency"), lang + " is missing currency");
            for (String id : en.nodes.keySet())
                for (String key : new String[]{id + ".name", id + ".desc"})
                    assertTrue(p.containsKey(key), "tree_" + lang + " is missing " + key);
            for (String key : p.stringPropertyNames())
                assertTrue(key.equals("currency") || en.nodes.containsKey(key.substring(0, key.indexOf('.'))),
                        "tree_" + lang + " has a key for an unknown node: " + key);
        }
    }

    private static Properties props(String resource) throws Exception {
        Properties p = new Properties();
        try (InputStream in = I18nTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " not found");
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return p;
    }
}
