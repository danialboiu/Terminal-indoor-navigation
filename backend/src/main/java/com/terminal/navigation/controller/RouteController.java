package com.terminal.navigation.controller;

import com.terminal.navigation.graph.Graph;
import com.terminal.navigation.routing.DijkstraRouter;
import com.terminal.navigation.routing.PassengerProfile;
import com.terminal.navigation.routing.RouteResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RouteController {

    private final Graph graph;
    private final DijkstraRouter router;

    public RouteController(Graph graph, DijkstraRouter router) {
        this.graph = graph;
        this.router = router;
    }

    @GetMapping("/route")
    public ResponseEntity<?> getRoute(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "PASSENGER") String profile) {
        try {
            PassengerProfile passengerProfile = PassengerProfile.valueOf(profile.toUpperCase());
            RouteResult result = router.shortestPath(graph, from, to, passengerProfile);
            return ResponseEntity.ok(new RouteResponse(
                    result.from(),
                    result.to(),
                    result.path(),
                    Math.round(result.totalCost() * 10.0) / 10.0,
                    passengerProfile.name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/nodes")
    public ResponseEntity<?> getNodes() {
        return ResponseEntity.ok(Map.of("nodes", graph.nodeIds()));
    }

    @GetMapping("/profiles")
    public ResponseEntity<?> getProfiles() {
        List<ProfileInfo> profiles = Arrays.stream(PassengerProfile.values())
                .map(p -> new ProfileInfo(p.name(), getProfileDescription(p)))
                .toList();
        return ResponseEntity.ok(Map.of("profiles", profiles));
    }

    private String getProfileDescription(PassengerProfile profile) {
        return switch (profile) {
            case PASSENGER -> "Standard passenger - can use all paths";
            case PARENT_WITH_STROLLER -> "Parent with stroller - cannot use stairs or escalators";
            case ELDERLY -> "Elderly passenger - cannot use stairs";
            case WHEELCHAIR_USER -> "Wheelchair user - cannot use stairs or escalators";
        };
    }

    record RouteResponse(String from, String to, List<String> path, double cost, String profile) {}
    record ProfileInfo(String name, String description) {}
}
