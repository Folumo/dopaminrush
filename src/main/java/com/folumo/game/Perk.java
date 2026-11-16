package com.folumo.game;

import java.util.Locale;

/** Permanent upgrades bought with Insight, which every rebirth gives. They survive all later rebirths. */
enum Perk {
    QUICKSTART(3, 1, 1),     // start each run with 1K / 1M / 1B points
    KEEP_AUTOPILOT(1, 3, 0), // the offline side tree isn't reset by a rebirth
    DISCOUNT(3, 2, 0),       // everything costs 10% less per level
    SIGHT(1, 4, 0);          // see one more step into the fog

    final int max, baseCost, costStep;

    Perk(int max, int baseCost, int costStep) {
        this.max = max;
        this.baseCost = baseCost;
        this.costStep = costStep;
    }

    /** Insight needed to go from {@code level} to the next level. */
    int cost(int level) {
        return baseCost + level * costStep;
    }

    /** Message key prefix: perk.quickstart.name / perk.quickstart.text */
    String key() {
        return "perk." + name().toLowerCase(Locale.ROOT);
    }
}
