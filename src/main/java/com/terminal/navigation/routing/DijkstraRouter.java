package com.terminal.navigation.routing;

import com.terminal.navigation.graph.Graph;
import com.terminal.navigation.graph.GraphEdge;

import java.util.*;

/**
 * DijkstraRouter computes the shortest path between two nodes in a terminal graph
 * using Dijkstra's algorithm.
 */
public final class DijkstraRouter {

    /**
     * Computes the shortest path using default PASSENGER profile.
     */
    public RouteResult shortestPath(Graph g, String from, String to) {
        return shortestPath(g, from, to, PassengerProfile.PASSENGER);
    }

    /**
     * Computes the shortest path between two nodes, respecting passenger profile restrictions.
     *
     * @param g       validated terminal graph
     * @param from    start node ID
     * @param to      destination node ID
     * @param profile passenger profile (determines which edge types can be used)
     * @return routing result containing the path and total cost
     * @throws IllegalArgumentException if start or destination node does not exist
     * @throws IllegalStateException    if no route exists between the nodes
     */
    public RouteResult shortestPath(Graph g, String from, String to, PassengerProfile profile) {
        Objects.requireNonNull(g, "Graph is null");
        Objects.requireNonNull(from, "from is null");
        Objects.requireNonNull(to, "to is null");
        Objects.requireNonNull(profile, "profile is null");

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
        Map<String, Double> dist = new HashMap<>();

        // Predecessor map used for path reconstruction
        Map<String, String> prev = new HashMap<>();

        for (String id : g.nodeIds()) {
            dist.put(id, Double.MAX_VALUE);
        }
        dist.put(from, 0.0);

        // Priority queue ordered by current shortest distance
        PriorityQueue<NodeDist> pq =
                new PriorityQueue<>(Comparator.comparingDouble(NodeDist::dist));
        pq.add(new NodeDist(from, 0.0));

        // Main Dijkstra loop
        while (!pq.isEmpty()) {
            NodeDist current = pq.poll();

            // Ignore outdated queue entries
            if (Double.compare(current.dist(), dist.get(current.node())) != 0) {
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
                double alternative = current.dist() + e.cost();
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
                    "No route found from " + from + " to " + to + " for profile " + profile);
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

    private record NodeDist(String node, double dist) {}
}
