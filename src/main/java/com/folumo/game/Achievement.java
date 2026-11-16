package com.folumo.game;

import java.util.Locale;
import java.util.function.Predicate;

/** Milestones in how the game is played. Each one earned adds {@link GameState#ACHIEVEMENT_BONUS} to everything, forever. */
enum Achievement {
    CLICKS_1K(s -> s.clicks >= 1_000),
    CLICKS_10K(s -> s.clicks >= 10_000),
    BOUGHT_100(s -> s.bought >= 100),
    BOUGHT_500(s -> s.bought >= 500),
    PLAYED_3H(s -> s.playSeconds >= 3 * 3600),
    NIGHT_OWL(s -> s.lateNights >= 3),
    RETURNS(s -> s.sessionsToday >= 10),
    REFUSED(s -> s.offersRefused >= 3),
    TAKEN(s -> s.offersTaken >= 3),
    MOLECULES(s -> s.molecules >= 10),
    NUMB(s -> s.tolerance >= .5),
    REBORN(s -> s.rebirths >= 1);

    final Predicate<GameState> reached;

    Achievement(Predicate<GameState> reached) {
        this.reached = reached;
    }

    /** Message key prefix: ach.clicks_1k.name / ach.clicks_1k.text */
    String key() {
        return "ach." + name().toLowerCase(Locale.ROOT);
    }
}
