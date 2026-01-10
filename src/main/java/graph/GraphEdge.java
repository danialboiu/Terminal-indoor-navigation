package graph;

import model.EdgeType;

public record GraphEdge(String to, int cost, EdgeType type, boolean enabled) {}

