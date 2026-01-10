package routing;

import graph.Graph;
import graph.GraphEdge;

import java.util.*;

/**
 * DijkstraRouter computes the shortest path between two nodes in a terminal graph
 * using Dijkstra’s algorithm.
 *
 * <p>This class operates exclusively on an already validated and immutable
 * {@link graph.Graph}. It performs no configuration validation and no I/O.
 *
 * <p>The routing cost is computed by summing edge costs along the path.
 * The interpretation of the cost value (e.g., distance, time) is handled externally.
 */
public final class DijkstraRouter {

    /**
     * Computes the shortest path using the STANDARD passenger profile.
     */
    public RouteResult shortestPath(Graph g, String from, String to) {
        return shortestPath(g, from, to, PassengerProfile.STANDARD);
    }

    /**
     * Computes the shortest path between two nodes in the given graph,
     * respecting the passenger profile's allowed edge types and cost penalties.
     *
     * @param g       validated terminal graph
     * @param from    start node ID
     * @param to      destination node ID
     * @param profile passenger profile defining routing constraints
     * @return routing result containing the path and total cost
     * @throws IllegalArgumentException if start or destination node does not exist
     * @throws IllegalStateException    if no route exists between the nodes
     */
    public RouteResult shortestPath(Graph g, String from, String to, PassengerProfile profile) {
        Objects.requireNonNull(g, "Graph is null");
        Objects.requireNonNull(from, "from is null");
        Objects.requireNonNull(to, "to is null");

        if (!g.hasNode(from)) {
            throw new IllegalArgumentException("Unknown start node: " + from);
        }
        if (!g.hasNode(to)) {
            throw new IllegalArgumentException("Unknown destination node: " + to);
        }
        if (!g.isNodeEnabled(from)) {
            throw new IllegalArgumentException("Start node is disabled: " + from);
        }
        if (!g.isNodeEnabled(to)) {
            throw new IllegalArgumentException("Destination node is disabled: " + to);
        }

        // Distance from start node to each node
        Map<String, Integer> dist = new HashMap<>();

        // Predecessor map used for path reconstruction
        Map<String, String> prev = new HashMap<>();

        for (String id : g.nodeIds()) {
            dist.put(id, Integer.MAX_VALUE);
        }
        dist.put(from, 0);

        // Priority queue ordered by current shortest distance
        PriorityQueue<NodeDist> pq =
                new PriorityQueue<>(Comparator.comparingInt(NodeDist::dist));
        pq.add(new NodeDist(from, 0));

        // Main Dijkstra loop
        while (!pq.isEmpty()) {
            NodeDist current = pq.poll();

            // Ignore outdated queue entries
            if (current.dist() != dist.get(current.node())) {
                continue;
            }

            // Early termination when destination is reached
            if (current.node().equals(to)) {
                break;
            }

            // Relax outgoing edges
            for (GraphEdge e : g.edgesFrom(current.node())) {
                // Skip disabled edges or edges leading to disabled nodes
                if (!e.enabled() || !g.isNodeEnabled(e.to())) {
                    continue;
                }
                // Skip edge types not allowed by passenger profile
                if (!profile.isAllowed(e.type())) {
                    continue;
                }
                // Apply cost penalty based on passenger profile
                int effectiveCost = profile.getEffectiveCost(e.type(), e.cost());
                int alternative = current.dist() + effectiveCost;
                if (alternative < dist.get(e.to())) {
                    dist.put(e.to(), alternative);
                    prev.put(e.to(), current.node());
                    pq.add(new NodeDist(e.to(), alternative));
                }
            }
        }

        // No path found
        if (!from.equals(to) && !prev.containsKey(to)) {
            throw new IllegalStateException(
                    "No route found from " + from + " to " + to);
        }

        // Reconstruct path by following predecessors
        LinkedList<String> path = new LinkedList<>();
        String current = to;
        path.addFirst(current);

        while (!current.equals(from)) {
            current = prev.get(current);
            if (current == null) {
                throw new IllegalStateException(
                        "No route found from " + from + " to " + to);
            }
            path.addFirst(current);
        }

        return new RouteResult(from, to, List.copyOf(path), dist.get(to));
    }

    /**
     * Internal helper record used by the priority queue.
     * Stores a node ID and its current known shortest distance.
     */
    private record NodeDist(String node, int dist) {}
}
