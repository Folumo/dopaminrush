package com.folumo.tree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Translations. UI text lives in {@code src/main/resources/i18n/messages_<lang>.properties} (English is the base file);
 * game content (tree, credits, video) uses files like {@code tree_<lang>.properties} next to the English one.
 */
public final class I18n {
    public static final List<String> LANGUAGES = List.of("en", "hr", "de", "es", "pt");

    private static String lang = "en";
    private static ResourceBundle bundle = load(lang);

    private I18n() {}

    public static void setLanguage(String code) {
        lang = LANGUAGES.contains(code) ? code : "en";
        bundle = load(lang);
    }

    public static String language() {
        return lang;
    }

    /** The system's language if the game has it, else English. */
    public static String systemLanguage() {
        String l = Locale.getDefault().getLanguage();
        return LANGUAGES.contains(l) ? l : "en";
    }

    /** "Hrvatski", "Deutsch", ... in the language itself. */
    public static String nativeName(String code) {
        Locale l = Locale.forLanguageTag(code);
        String n = l.getDisplayLanguage(l);
        return n.isEmpty() ? code : n.substring(0, 1).toUpperCase(l) + n.substring(1);
    }

    private static ResourceBundle load(String code) {
        // no-fallback control: a missing key falls back to English, never to the system's language
        return ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(code),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /** Translated text with {0}, {1}... filled in. A missing key shows as !key! so it's easy to spot. */
    public static String t(String key, Object... args) {
        String s;
        try {
            s = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    /** Picks {@code key.one}, {@code key.few} or {@code key.other} for n by the language's plural rules. */
    public static String plural(String key, int n) {
        String k = key + "." + pluralCategory(n);
        return t(bundle.containsKey(k) ? k : key + ".other", n);
    }

    static String pluralCategory(int n) {
        if (lang.equals("hr")) { // 1 sat, 2 sata, 5 sati; 21 sat, 22 sata, 11-14 sati
            int d = n % 10, h = n % 100;
            if (d == 1 && h != 11) return "one";
            if (d >= 2 && d <= 4 && (h < 12 || h > 14)) return "few";
            return "other";
        }
        return n == 1 ? "one" : "other";
    }

    /** 30 → "30 minutes", 90 → "1 hour 30 minutes", in the current language. */
    public static String duration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        String hours = h == 0 ? "" : plural("time.hours", h), mins = m == 0 ? "" : plural("time.minutes", m);
        return hours.isEmpty() ? mins : mins.isEmpty() ? hours : t("time.hoursAndMinutes", hours, mins);
    }

    /** {@code name_<lang>.ext} next to {@code file} if it exists, else {@code file} itself. */
    public static Path localized(Path file) {
        if (lang.equals("en")) return file;
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path p = file.resolveSibling(dot < 0 ? name + "_" + lang : name.substring(0, dot) + "_" + lang + name.substring(dot));
        return Files.exists(p) ? p : file;
    }
}
