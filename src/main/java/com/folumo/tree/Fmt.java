package com.folumo.tree;

import java.util.Locale;

/** Short idle-game numbers: 950, 1.25K, 3.40M ... */
public final class Fmt {
    private static final String[] UNITS = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

    private Fmt() {}

    /** 30 → "30 minutes", 60 → "1 hour", 90 → "1 hour 30 minutes". */
    public static String duration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        String hours = h == 0 ? "" : h + (h == 1 ? " hour" : " hours");
        String mins = m == 0 ? "" : m + (m == 1 ? " minute" : " minutes");
        return hours.isEmpty() ? mins : mins.isEmpty() ? hours : hours + " " + mins;
    }

    public static String num(double v) {
        int i = 0;
        while (Math.abs(v) >= 1000 && i < UNITS.length - 1) {
            v /= 1000;
            i++;
        }
        if (i > 0) return String.format(Locale.ROOT, "%.2f%s", v, UNITS[i]);
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
