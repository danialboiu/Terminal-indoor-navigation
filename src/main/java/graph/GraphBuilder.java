package graph;

import model.EdgeDef;
import model.Node;
import model.TerminalMap;

import java.util.*;

/**
 * GraphBuilder is responsible for transforming a raw terminal configuration
 * (loaded from JSON) into a validated, immutable graph structure.
 *
 * <p>This class performs all structural and semantic validation of the input data:
 * <ul>
 *   <li>Checks that all nodes are uniquely defined</li>
 *   <li>Ensures that all edges reference existing nodes</li>
 *   <li>Validates edge properties such as cost, directionality, and type</li>
 * </ul>
 *
 * <p>The resulting {@link Graph} is guaranteed to be consistent and safe for use
 * by routing algorithms (e.g., shortest-path computation).
 *
 * <p>No routing logic is implemented here; this class strictly separates
 * configuration validation from algorithmic processing.
 */
public final class GraphBuilder {

    /**
     * Builds a validated graph from the given terminal map definition.
     *
     * @param map terminal configuration loaded from JSON
     * @return immutable graph representation of the terminal
     * @throws IllegalArgumentException if the configuration is invalid or incomplete
     */
    public Graph build(TerminalMap map) {
        Objects.requireNonNull(map, "TerminalMap is null");

        // --- Basic structural validation ---
        if (map.nodes == null || map.nodes.isEmpty()) {
            throw new IllegalArgumentException("No nodes defined in terminal configuration.");
        }
        if (map.edges == null || map.edges.isEmpty()) {
            throw new IllegalArgumentException("No edges defined in terminal configuration.");
        }

        // --- Validate nodes and ensure uniqueness ---
        Set<String> nodeIds = new HashSet<>();
        Map<String, Boolean> nodeEnabled = new HashMap<>();
        Map<String, Integer> nodeFloors = new HashMap<>();
        for (Node n : map.nodes) {
            if (n == null || isBlank(n.id)) {
                throw new IllegalArgumentException("Node with missing or empty id.");
            }
            if (!nodeIds.add(n.id)) {
                throw new IllegalArgumentException("Duplicate node id detected: " + n.id);
            }
            // Default enabled to true if not specified
            nodeEnabled.put(n.id, n.enabled == null ? true : n.enabled);
            // Default floor to 1 if not specified
            nodeFloors.put(n.id, n.floor == null ? 1 : n.floor);
        }

        // --- Initialize adjacency list for all nodes ---
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        for (String id : nodeIds) {
            adj.put(id, new ArrayList<>());
        }

        // --- Validate and add edges ---
        for (EdgeDef e : map.edges) {
            if (e == null) {
                throw new IllegalArgumentException("Null edge definition encountered.");
            }
            if (isBlank(e.from) || isBlank(e.to)) {
                throw new IllegalArgumentException("Edge with missing 'from' or 'to' field.");
            }
            if (!nodeIds.contains(e.from)) {
                throw new IllegalArgumentException("Edge references unknown source node: " + e.from);
            }
            if (!nodeIds.contains(e.to)) {
                throw new IllegalArgumentException("Edge references unknown destination node: " + e.to);
            }

            // --- Validate mandatory edge attributes ---
            if (e.cost == null || e.cost <= 0) {
                throw new IllegalArgumentException(
                        "Invalid edge cost for connection: " + e.from + " -> " + e.to);
            }
            if (e.bidirectional == null) {
                throw new IllegalArgumentException(
                        "Missing directionality for edge: " + e.from + " -> " + e.to);
            }
            if (e.type == null) {
                throw new IllegalArgumentException(
                        "Missing edge type for connection: " + e.from + " -> " + e.to);
            }

            // Default enabled to true if not specified
            boolean edgeEnabled = e.enabled == null ? true : e.enabled;

            // --- Add forward edge ---
            adj.get(e.from).add(new GraphEdge(e.to, e.cost, e.type, edgeEnabled));

            // --- Add reverse edge if bidirectional ---
            if (e.bidirectional) {
                adj.get(e.to).add(new GraphEdge(e.from, e.cost, e.type, edgeEnabled));
            }
        }

        // --- Construct immutable graph ---
        return new Graph(adj, nodeEnabled, nodeFloors);
    }

    /**
     * Utility method to check whether a string is null or blank.
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
