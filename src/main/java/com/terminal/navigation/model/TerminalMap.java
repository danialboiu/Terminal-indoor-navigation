package com.terminal.navigation.model;

import java.util.List;

/**
 * Root JSON model representing the terminal layout.
 * Contains all nodes (locations) and edges (walkable connections) as defined in the input file.
 * This class contains no logic and is validated later during graph construction.
 */
public class TerminalMap {

    /** List of terminal nodes (must be present and non-empty in JSON). */
    public List<Node> nodes;

    /** List of connections between nodes (must be present and non-empty in JSON). */
    public List<EdgeDef> edges;

    public TerminalMap() {}
}
