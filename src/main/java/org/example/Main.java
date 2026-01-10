package org.example;

import graph.Graph;
import graph.GraphBuilder;
import io.TerminalMapLoader;
import model.TerminalMap;
import routing.DijkstraRouter;
import routing.PassengerProfile;
import routing.RouteResult;

/**
 * Entry point of the terminal indoor navigation prototype.
 *
 * <p>This class orchestrates the routing workflow:
 * <ol>
 *   <li>Loads the terminal configuration from a static JSON resource</li>
 *   <li>Builds a validated graph representation</li>
 *   <li>Executes shortest-path routing between two nodes</li>
 *   <li>Outputs the routing result</li>
 * </ol>
 *
 * <p>No business logic or validation is implemented here.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        // Default routing query: Gate A1 to Observation Deck (multi-floor journey)
        String from = (args.length >= 1) ? args[0] : "9";
        String to   = (args.length >= 2) ? args[1] : "F2-3";

        // Load terminal configuration (static resource)
        TerminalMapLoader loader = new TerminalMapLoader();
        TerminalMap map = loader.load("terminal-map.json");

        // Build graph
        Graph graph = new GraphBuilder().build(map);
        DijkstraRouter router = new DijkstraRouter();

        System.out.println("=== Terminal Indoor Navigation ===");
        System.out.println("From: " + from + "  To: " + to);
        System.out.println();

        // Demo: Compare routes for different passenger profiles
        for (PassengerProfile profile : PassengerProfile.values()) {
            try {
                RouteResult result = router.shortestPath(graph, from, to, profile);
                System.out.println("--- " + profile.name() + " ---");
                printRoute(graph, result);
            } catch (IllegalStateException e) {
                System.out.println("--- " + profile.name() + " ---");
                System.out.println("  No accessible route available!");
                System.out.println();
            }
        }

        System.out.println("=== Notes ===");
        System.out.println("- STANDARD: All routes available");
        System.out.println("- PARENT_WITH_STROLLER: No stairs, escalators penalized (3x)");
        System.out.println("- ELDERLY: No stairs, escalators penalized (2x)");
        System.out.println("- WHEELCHAIR: Elevator only (no stairs/escalators)");
        System.out.println("- Corridor Gate A2 <-> Gate B2 is CLOSED");
    }

    private static void printRoute(Graph graph, RouteResult result) {
        System.out.println("  Cost: " + result.totalCost());
        System.out.print("  Path: ");
        int prevFloor = -1;
        StringBuilder sb = new StringBuilder();
        for (String nodeId : result.path()) {
            int floor = graph.getNodeFloor(nodeId);
            if (prevFloor != -1 && floor != prevFloor) {
                sb.append(" [F").append(prevFloor).append("->F").append(floor).append("] ");
            }
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(nodeId);
            prevFloor = floor;
        }
        System.out.println(sb);
        System.out.println();
    }
}
