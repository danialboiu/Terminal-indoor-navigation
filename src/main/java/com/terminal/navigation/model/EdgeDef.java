package com.terminal.navigation.model;

/**
 * JSON-defined connection between two nodes in the terminal graph.
 * Represents a walkable segment with a cost (distance or time) and optional type
 * used later for constraints (e.g., avoid stairs).
 */
public class EdgeDef {
    public String from;
    public String to;

    /** Cost value (supports decimals). */
    public Double cost;

    /** If true, adds edges in both directions; otherwise only from -> to. */
    public Boolean bidirectional;

    /** Segment type for constraint filtering: CORRIDOR, STAIRS, ELEVATOR, ESCALATOR, etc. */
    public EdgeType type;

    public Boolean enabled;

    public EdgeDef() {}

    @Override
    public String toString() {
        return "EdgeDef{%s -> %s, cost=%.2f, type=%s, bidirectional=%s}"
                .formatted(from, to, cost, type, bidirectional);
    }
}
