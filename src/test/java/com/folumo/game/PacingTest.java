package com.folumo.game;

import com.folumo.tree.Fmt;
import com.folumo.tree.Node;
import com.folumo.tree.Tree;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plays the shipped tree with a simple bot that always buys {@link GameState#bestNext} and prints when
 * each milestone is reached. Run {@code gradlew test --tests *PacingTest -i} to see the report.
 */
class PacingTest {
    /** A player who clicks Gather now and then, not nonstop. */
    static final double CLICKS_PER_SECOND = 1;

    @Test
    void shippedTreeReachesTheEndingInAFewHours() throws Exception {
        Tree tree = Tree.load(Path.of("tree.properties"));
        double hours = simulate(tree, true) / 3600;
        assertTrue(hours > 3 && hours < 12, "The Final Rush should take 3-12 hours of play, took " + hours);
    }

    /** @return seconds until the final node is bought */
    static double simulate(Tree tree, boolean print) {
        GameState s = new GameState(tree);
        double time = 0, longestWait = 0;
        String longestWaitFor = "";
        Node finalNode = tree.nodes.get("final");
        for (int step = 0; step < 100_000; step++) {
            Node n = s.bestNext(CLICKS_PER_SECOND);
            assertNotNull(n, "ran out of things to buy before the final node");
            double rate = s.income() + s.clickValue() * CLICKS_PER_SECOND;
            double wait = Math.max(0, (s.cost(n) - s.points) / rate);
            if (wait > longestWait) {
                longestWait = wait;
                longestWaitFor = n.name + " lv" + (s.level(n) + 1);
            }
            time += wait;
            s.points = Math.max(s.points + rate * wait, s.cost(n)); // no rounding shortfall
            assertTrue(s.buy(n), "bot couldn't buy " + n.id);
            if (print && s.level(n) == 1 && n.shape == Node.Shape.HEXAGON)
                System.out.printf("%7s  %-22s  income %s/s%n", clock(time), n.name, Fmt.num(s.income()));
            if (n == finalNode) {
                if (print) System.out.printf("Purchases: %d, longest wait: %s for %s%n", s.bought, clock(longestWait), longestWaitFor);
                return time;
            }
        }
        return fail("never reached the final node");
    }

    private static String clock(double seconds) {
        long m = Math.round(seconds / 60);
        return m / 60 + "h" + String.format("%02d", m % 60);
    }
}
