package com.folumo.game;

import com.folumo.tree.I18n;
import com.folumo.tree.Tree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Player preferences, kept in the user's home folder so they apply to every tree. */
final class Settings {
    static final Path FILE = Path.of(System.getProperty("user.home"), ".dopaminrush.properties");

    boolean sound = true;
    int volume = 70; // 0..100
    boolean popups = true;
    boolean goalHint = true; // ring and arrow on the best next purchase
    String language = I18n.systemLanguage();

    static Settings load(Path file) {
        Settings s = new Settings();
        if (!Files.exists(file)) return s;
        try {
            Properties p = Tree.read(file);
            s.sound = Boolean.parseBoolean(p.getProperty("sound", "true").trim());
            s.volume = Math.max(0, Math.min(100, Integer.parseInt(p.getProperty("volume", "70").trim())));
            s.popups = Boolean.parseBoolean(p.getProperty("popups", "true").trim());
            s.goalHint = Boolean.parseBoolean(p.getProperty("goalHint", "true").trim());
            String lang = p.getProperty("language", s.language).trim();
            s.language = I18n.LANGUAGES.contains(lang) ? lang : s.language;
        } catch (Exception e) {
            System.err.println("Ignoring unreadable settings (" + file + "): " + e.getMessage());
            return new Settings();
        }
        return s;
    }

    void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("sound", Boolean.toString(sound));
        p.setProperty("volume", Integer.toString(volume));
        p.setProperty("popups", Boolean.toString(popups));
        p.setProperty("goalHint", Boolean.toString(goalHint));
        p.setProperty("language", language);
        Tree.store(p, file, "Dopamin Rush settings");
    }
}
