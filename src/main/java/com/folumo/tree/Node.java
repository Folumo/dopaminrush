package com.folumo.tree;

import java.util.LinkedHashSet;
import java.util.Set;

/** One upgrade in the tree. */
public final class Node {
    public final String id;
    public String name;
    public String description = "";
    public double cost;
    public double income;     // currency per second once owned
    public double clickBonus; // added to each manual click once owned
    public int x, y;
    public final Set<String> requires = new LinkedHashSet<>();

    public Node(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
