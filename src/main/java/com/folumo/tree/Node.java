package com.folumo.tree;

import java.util.LinkedHashSet;
import java.util.Set;

/** One upgrade in the tree. */
public final class Node {
    /** Square = normal, circle = important, hexagon = very important milestone that new branches grow from. */
    public enum Shape { SQUARE, CIRCLE, HEXAGON }

    public final String id;
    public Shape shape = Shape.SQUARE;
    public String name;
    public String description = "";
    public double cost;
    public double income;     // currency per second once owned
    public double clickBonus; // added to each manual click once owned
    /** How many times it can be bought; each level adds the income and click bonus again. */
    public int maxLevel = 1;
    public int x, y;
    public final Set<String> requires = new LinkedHashSet<>();

    /** Level cap used when a tree doesn't set {@code max}: normal nodes repeat the most, milestones once. */
    public static int defaultMax(Shape s) {
        return switch (s) {
            case SQUARE -> 10;
            case CIRCLE -> 5;
            case HEXAGON -> 1;
        };
    }

    public Node(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
