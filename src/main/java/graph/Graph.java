package graph;

import java.util.*;

/**
 * Graph represents the terminal layout as an immutable network
 * of locations and connections, optimized for routing algorithms.
 */
public final class Graph {

    private final Map<String, List<GraphEdge>> adj;
    private final Map<String, Boolean> nodeEnabled;
    private final Map<String, Integer> nodeFloors;

    public Graph(Map<String, List<GraphEdge>> adj,
                 Map<String, Boolean> nodeEnabled,
                 Map<String, Integer> nodeFloors) {

        if (adj == null || adj.isEmpty()) {
            throw new IllegalArgumentException("Graph adjacency map must not be null or empty.");
        }
        if (nodeEnabled == null || nodeEnabled.isEmpty()) {
            throw new IllegalArgumentException("nodeEnabled map must not be null or empty.");
        }
        if (nodeFloors == null || nodeFloors.isEmpty()) {
            throw new IllegalArgumentException("nodeFloors map must not be null or empty.");
        }

        Map<String, List<GraphEdge>> frozen = new HashMap<>();
        for (Map.Entry<String, List<GraphEdge>> e : adj.entrySet()) {
            if (e.getKey() == null || e.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Graph contains null/blank node id.");
            }
            List<GraphEdge> edges = e.getValue();
            if (edges == null) {
                throw new IllegalArgumentException("Adjacency list is null for node: " + e.getKey());
            }
            frozen.put(e.getKey(), List.copyOf(edges));
        }

        // Ensure metadata exists for every node in the graph
        for (String id : frozen.keySet()) {
            if (!nodeEnabled.containsKey(id)) {
                throw new IllegalArgumentException("Missing enabled metadata for node: " + id);
            }
            if (!nodeFloors.containsKey(id)) {
                throw new IllegalArgumentException("Missing floor metadata for node: " + id);
            }
            if (nodeFloors.get(id) == null) {
                throw new IllegalArgumentException("Null floor metadata for node: " + id);
            }
            if (nodeEnabled.get(id) == null) {
                throw new IllegalArgumentException("Null enabled metadata for node: " + id);
            }
        }

        this.adj = Collections.unmodifiableMap(frozen);
        this.nodeEnabled = Map.copyOf(nodeEnabled);
        this.nodeFloors = Map.copyOf(nodeFloors);
    }

    public boolean hasNode(String id) {
        return adj.containsKey(id);
    }

    /** Returns a defensive copy of all node IDs in the graph. */
    public Set<String> nodeIds() {
        return Set.copyOf(adj.keySet());
    }

    /** Returns outgoing edges from the given node, or an empty list if none exist. */
    public List<GraphEdge> edgesFrom(String id) {
        return adj.getOrDefault(id, List.of());
    }

    /** Returns whether the given node is enabled (defaults to true if not found). */
    public boolean isNodeEnabled(String id) {
        Boolean enabled = nodeEnabled.get(id);
        if (enabled == null) {
            throw new IllegalArgumentException("Unknown node id (enabled): " + id);
        }
        return enabled;
    }

    /** Returns the floor of the given node (defaults to 1 if not specified). */
    public int getNodeFloor(String id) {
        Integer floor = nodeFloors.get(id);
        if (floor == null) {
            throw new IllegalArgumentException("Unknown node id (floor): " + id);
        }
        return floor;
    }
}
