package com.terminal.navigation.service;

import com.terminal.navigation.dto.NarrationInputStep;
import com.terminal.navigation.dto.NarrationResult;
import com.terminal.navigation.dto.NodeOption;
import com.terminal.navigation.dto.NodesResponse;
import com.terminal.navigation.dto.ProfileInfo;
import com.terminal.navigation.dto.ProfilesResponse;
import com.terminal.navigation.dto.RouteInstructionsResponse;
import com.terminal.navigation.graph.Graph;
import com.terminal.navigation.graph.GraphEdge;
import com.terminal.navigation.model.Node;
import com.terminal.navigation.model.TerminalMap;
import com.terminal.navigation.routing.DijkstraRouter;
import com.terminal.navigation.routing.PassengerProfile;
import com.terminal.navigation.routing.RouteResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TerminalRouteService {
    private final Graph graph;
    private final DijkstraRouter router;
    private final RouteNarrationService narrationService;
    private final Map<String, Node> nodeById;

    public TerminalRouteService(
            Graph graph,
            DijkstraRouter router,
            RouteNarrationService narrationService,
            TerminalMap terminalMap
    ) {
        this.graph = graph;
        this.router = router;
        this.narrationService = narrationService;
        this.nodeById = buildNodeLookup(terminalMap);
    }

    public RouteInstructionsResponse routeInstructions(String from, String to, String profile) {
        PassengerProfile passengerProfile = parseProfile(profile);
        RouteResult result = resolveRoute(from, to, passengerProfile);
        List<NarrationInputStep> inputSteps = buildNarrationInputSteps(result.path());
        String fromLabel = nodeLabel(result.from());
        String toLabel = nodeLabel(result.to());
        NarrationResult narration = narrationService.narrate(
                inputSteps,
                passengerProfile,
                fromLabel,
                toLabel
        );

        return new RouteInstructionsResponse(
                narration.summary(),
                narration.instructions(),
                inputSteps
        );
    }

    private RouteResult resolveRoute(String from, String to, PassengerProfile passengerProfile) {
        return SmartDestination.fromId(to)
                .map(destination -> routeToNearest(from, destination, passengerProfile))
                .orElseGet(() -> router.shortestPath(graph, from, to, passengerProfile));
    }

    private RouteResult routeToNearest(String from, SmartDestination destination, PassengerProfile passengerProfile) {
        RouteResult best = null;
        for (Node node : nodeById.values()) {
            if (!isSmartDestinationCandidate(node, destination)) {
                continue;
            }
            try {
                RouteResult candidate = router.shortestPath(graph, from, node.id, passengerProfile);
                if (best == null || candidate.totalCost() < best.totalCost()) {
                    best = candidate;
                }
            } catch (IllegalStateException ignored) {
                // Another toilet may still be reachable.
            }
        }

        if (best == null) {
            String destinationLabel = destination.toNodeOption().label().toLowerCase();
            throw new IllegalStateException("No reachable " + destinationLabel + " from this starting point.");
        }
        return best;
    }

    public NodesResponse nodes() {
        ArrayList<NodeOption> options = new ArrayList<>();
        Arrays.stream(SmartDestination.values())
                .map(SmartDestination::toNodeOption)
                .forEach(options::add);

        nodeById.values().stream()
                .filter(node -> Boolean.TRUE.equals(node.enabled))
                .filter(this::isNodeOptionVisible)
                .map(node -> new NodeOption(
                        node.id,
                        nodeLabel(node.id),
                        node.floor == null ? 0.0 : node.floor,
                        nodeCategory(node),
                        isSelectableFrom(node),
                        isSelectableTo(node),
                        ""
                ))
                .forEach(options::add);

        List<NodeOption> nodes = options.stream()
                .sorted((a, b) -> a.label().compareToIgnoreCase(b.label()))
                .toList();
        return new NodesResponse(nodes);
    }

    public ProfilesResponse profiles() {
        List<ProfileInfo> profiles = Arrays.stream(PassengerProfile.values())
                .map(profile -> new ProfileInfo(profile.name(), profile.description()))
                .toList();
        return new ProfilesResponse(profiles);
    }

    private PassengerProfile parseProfile(String profile) {
        String value = profile == null || profile.isBlank() ? "PASSENGER" : profile.trim().toUpperCase();
        return PassengerProfile.valueOf(value);
    }

    private static Map<String, Node> buildNodeLookup(TerminalMap terminalMap) {
        Map<String, Node> lookup = new HashMap<>();
        if (terminalMap.nodes != null) {
            for (Node node : terminalMap.nodes) {
                if (node != null && node.id != null && !node.id.isBlank()) {
                    lookup.put(node.id, node);
                }
            }
        }
        return Map.copyOf(lookup);
    }

    private String nodeLabel(String nodeId) {
        Node node = nodeById.get(nodeId);
        if (node != null && node.label != null && !node.label.isBlank()) {
            return node.label;
        }
        return nodeId;
    }

    private List<NarrationInputStep> buildNarrationInputSteps(List<String> path) {
        if (path == null || path.size() < 2) {
            return List.of();
        }

        ArrayList<NarrationInputStep> steps = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            String fromId = path.get(i);
            String toId = path.get(i + 1);
            GraphEdge edge = edgeBetween(fromId, toId);
            Node fromNode = nodeById.get(fromId);
            Node toNode = nodeById.get(toId);
            double floorFrom = graph.getNodeFloor(fromId);
            double floorTo = graph.getNodeFloor(toId);
            String fromLabel = nodeLabel(fromId);
            String toLabel = nodeLabel(toId);

            steps.add(new NarrationInputStep(
                    fromId,
                    toId,
                    cleanText(fromLabel),
                    cleanText(toLabel),
                    edge.type().name(),
                    floorFrom,
                    floorTo,
                    normalizeCheckpointType(fromNode != null ? fromNode.checkpointType : null),
                    normalizeCheckpointType(toNode != null ? toNode.checkpointType : null),
                    cleanText(edge.relationHint())
            ));
        }
        return List.copyOf(steps);
    }

    private GraphEdge edgeBetween(String fromId, String toId) {
        return graph.edgesFrom(fromId).stream()
                .filter(edge -> toId.equals(edge.to()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing edge in route: " + fromId + " -> " + toId));
    }

    private String normalizeCheckpointType(String checkpointType) {
        if (checkpointType == null || checkpointType.isBlank()) {
            return "";
        }
        String normalized = checkpointType.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "SECURITY", "PASSPORT_CONTROL" -> normalized;
            default -> "";
        };
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isNodeOptionVisible(Node node) {
        return isSelectableFrom(node) || isSelectableTo(node);
    }

    private boolean isSmartDestinationCandidate(Node node, SmartDestination destination) {
        return node != null
                && Boolean.TRUE.equals(node.enabled)
                && node.id != null
                && isSelectableTo(node)
                && destination.matches(node);
    }

    private boolean isSelectableFrom(Node node) {
        return node.selectableFrom == null || Boolean.TRUE.equals(node.selectableFrom);
    }

    private boolean isSelectableTo(Node node) {
        return node.selectableTo == null || Boolean.TRUE.equals(node.selectableTo);
    }

    private String nodeCategory(Node node) {
        String category = cleanText(node.category);
        return category.isBlank() ? "Other" : category;
    }

}
