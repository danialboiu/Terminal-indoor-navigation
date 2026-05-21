package com.terminal.navigation.service;

import com.terminal.navigation.dto.NarrationInputStep;
import com.terminal.navigation.dto.NarrationInstruction;
import com.terminal.navigation.dto.NarrationResult;
import com.terminal.navigation.dto.NodeOption;
import com.terminal.navigation.dto.NodesResponse;
import com.terminal.navigation.dto.ProfileInfo;
import com.terminal.navigation.dto.ProfilesResponse;
import com.terminal.navigation.dto.RouteInstructionsResponse;
import com.terminal.navigation.graph.Graph;
import com.terminal.navigation.graph.GraphEdge;
import com.terminal.navigation.model.Node;
import com.terminal.navigation.routing.DijkstraRouter;
import com.terminal.navigation.routing.PassengerProfile;
import com.terminal.navigation.routing.RouteResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TerminalRouteService {
    private final TerminalMapProvider mapProvider;
    private final DijkstraRouter router;
    private final RouteNarrationService narrationService;

    public TerminalRouteService(
            TerminalMapProvider mapProvider,
            DijkstraRouter router,
            RouteNarrationService narrationService
    ) {
        this.mapProvider = mapProvider;
        this.router = router;
        this.narrationService = narrationService;
    }

    public RouteInstructionsResponse routeInstructions(String from, String to, String profile) {
        TerminalMapProvider.TerminalMapSnapshot mapSnapshot = mapProvider.current();
        PassengerProfile passengerProfile = parseProfile(profile);
        RouteResult result = resolveRoute(mapSnapshot, from, to, passengerProfile);
        List<NarrationInputStep> inputSteps = buildNarrationInputSteps(mapSnapshot, result.path());
        String fromLabel = nodeLabel(mapSnapshot, result.from());
        String toLabel = nodeLabel(mapSnapshot, result.to());
        NarrationResult narration;
        try {
            narration = narrationService.narrate(
                    inputSteps,
                    passengerProfile,
                    fromLabel,
                    toLabel
            );
        } catch (RuntimeException ignored) {
            narration = fallbackNarration(inputSteps, fromLabel, toLabel);
        }

        return new RouteInstructionsResponse(
                narration.summary(),
                narration.instructions(),
                inputSteps
        );
    }

    private NarrationResult fallbackNarration(List<NarrationInputStep> inputSteps, String fromLabel, String toLabel) {
        String summary = "AI instructions are unavailable. Follow the listed terminal points from "
                + fromLabel + " to " + toLabel + ".";

        List<NarrationInstruction> instructions = new ArrayList<>();
        int chunkSize = 5;
        for (int start = 0; start < inputSteps.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize - 1, inputSteps.size() - 1);
            List<String> labels = fallbackLabels(inputSteps, start, end);
            String text = "Follow these points: " + String.join(" -> ", labels) + ".";
            if (end == inputSteps.size() - 1) {
                text += "\nYou have arrived at " + toLabel + ".";
            }
            instructions.add(new NarrationInstruction(start, end, text, fallbackWarnings(inputSteps, start, end)));
        }

        return new NarrationResult(summary, List.copyOf(instructions));
    }

    private List<String> fallbackLabels(List<NarrationInputStep> inputSteps, int start, int end) {
        ArrayList<String> labels = new ArrayList<>();
        if (start == 0) {
            addFallbackLabel(labels, inputSteps.get(start).fromLabel());
        }
        for (int i = start; i <= end; i++) {
            addFallbackLabel(labels, inputSteps.get(i).toLabel());
        }
        if (labels.isEmpty()) {
            labels.add("next terminal point");
        }
        return List.copyOf(labels);
    }

    private void addFallbackLabel(List<String> labels, String label) {
        String cleanLabel = cleanText(label);
        if (cleanLabel.isBlank() || isTechnicalFallbackLabel(cleanLabel)) {
            return;
        }
        if (labels.isEmpty() || !labels.get(labels.size() - 1).equals(cleanLabel)) {
            labels.add(cleanLabel);
        }
    }

    private boolean isTechnicalFallbackLabel(String label) {
        String upper = label.toUpperCase();
        return upper.contains("JUNCTION")
                || upper.contains("CORRIDOR")
                || upper.contains("POST-SECURITY");
    }

    private List<String> fallbackWarnings(List<NarrationInputStep> inputSteps, int start, int end) {
        ArrayList<String> warnings = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            addFallbackWarning(warnings, inputSteps.get(i).fromCheckpointType());
            addFallbackWarning(warnings, inputSteps.get(i).toCheckpointType());
        }
        return List.copyOf(warnings);
    }

    private void addFallbackWarning(List<String> warnings, String checkpointType) {
        String warning = switch (cleanText(checkpointType)) {
            case "SECURITY" -> "Prepare your boarding pass before the security gates.";
            case "PASSPORT_CONTROL" -> "Keep your passport and boarding pass ready for passport control.";
            default -> "";
        };
        if (!warning.isBlank() && !warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private RouteResult resolveRoute(
            TerminalMapProvider.TerminalMapSnapshot mapSnapshot,
            String from,
            String to,
            PassengerProfile passengerProfile
    ) {
        return SmartDestination.fromId(to)
                .map(destination -> routeToNearest(mapSnapshot, from, destination, passengerProfile))
                .orElseGet(() -> router.shortestPath(mapSnapshot.graph(), from, to, passengerProfile));
    }

    private RouteResult routeToNearest(
            TerminalMapProvider.TerminalMapSnapshot mapSnapshot,
            String from,
            SmartDestination destination,
            PassengerProfile passengerProfile
    ) {
        RouteResult best = null;
        for (Node node : mapSnapshot.nodeById().values()) {
            if (!isSmartDestinationCandidate(node, destination)) {
                continue;
            }
            try {
                RouteResult candidate = router.shortestPath(mapSnapshot.graph(), from, node.id, passengerProfile);
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
        TerminalMapProvider.TerminalMapSnapshot mapSnapshot = mapProvider.current();
        ArrayList<NodeOption> options = new ArrayList<>();
        Arrays.stream(SmartDestination.values())
                .map(SmartDestination::toNodeOption)
                .forEach(options::add);

        mapSnapshot.nodeById().values().stream()
                .filter(node -> Boolean.TRUE.equals(node.enabled))
                .filter(this::isNodeOptionVisible)
                .map(node -> new NodeOption(
                        node.id,
                        nodeLabel(mapSnapshot, node.id),
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

    private String nodeLabel(TerminalMapProvider.TerminalMapSnapshot mapSnapshot, String nodeId) {
        Node node = mapSnapshot.nodeById().get(nodeId);
        if (node != null && node.label != null && !node.label.isBlank()) {
            return node.label;
        }
        return nodeId;
    }

    private List<NarrationInputStep> buildNarrationInputSteps(
            TerminalMapProvider.TerminalMapSnapshot mapSnapshot,
            List<String> path
    ) {
        if (path == null || path.size() < 2) {
            return List.of();
        }

        ArrayList<NarrationInputStep> steps = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            String fromId = path.get(i);
            String toId = path.get(i + 1);
            GraphEdge edge = edgeBetween(mapSnapshot.graph(), fromId, toId);
            Node fromNode = mapSnapshot.nodeById().get(fromId);
            Node toNode = mapSnapshot.nodeById().get(toId);
            double floorFrom = mapSnapshot.graph().getNodeFloor(fromId);
            double floorTo = mapSnapshot.graph().getNodeFloor(toId);
            String fromLabel = nodeLabel(mapSnapshot, fromId);
            String toLabel = nodeLabel(mapSnapshot, toId);

            steps.add(new NarrationInputStep(
                    fromId,
                    toId,
                    cleanText(fromLabel),
                    cleanText(toLabel),
                    edge.type().name(),
                    floorFrom,
                    floorTo,
                    normalizeCheckpointType(fromNode != null ? fromNode.checkpointType : null),
                    normalizeCheckpointType(toNode != null ? toNode.checkpointType : null)
            ));
        }
        return List.copyOf(steps);
    }

    private GraphEdge edgeBetween(Graph graph, String fromId, String toId) {
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
