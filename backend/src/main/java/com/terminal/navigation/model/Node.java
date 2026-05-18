package com.terminal.navigation.model;

/**
 * A location point in the terminal graph.
 * Loaded from JSON and used to label/describe nodes in the UI.
 */
public class Node {
    public String id;
    public String label;

    public Boolean enabled;
    public Double floor;
    public String category;
    public Boolean selectableFrom;
    public Boolean selectableTo;
    public String poiType;
    public String checkpointType;

    public Node() {}

    @Override
    public String toString() {
        return "Node{id='%s', label='%s'}".formatted(id, label);
    }
}
