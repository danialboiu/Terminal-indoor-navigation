package com.terminal.navigation.graph;

import com.terminal.navigation.model.EdgeType;

public record GraphEdge(String to, double cost, EdgeType type, boolean enabled) {}

