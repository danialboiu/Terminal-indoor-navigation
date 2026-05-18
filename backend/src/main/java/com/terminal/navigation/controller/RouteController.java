package com.terminal.navigation.controller;

import com.terminal.navigation.dto.NodesResponse;
import com.terminal.navigation.dto.ProfilesResponse;
import com.terminal.navigation.dto.RouteInstructionsResponse;
import com.terminal.navigation.service.TerminalRouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RouteController {
    private final TerminalRouteService routeService;

    public RouteController(TerminalRouteService routeService) {
        this.routeService = routeService;
    }

    // Main route endpoint: calculates the route and returns passenger-friendly AI/mock instructions.
    @GetMapping("/route-instructions")
    public RouteInstructionsResponse getRouteInstructions(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(name = "profile", defaultValue = "PASSENGER") String profile) {
        return routeService.routeInstructions(from, to, profile);
    }

    // Provides terminal locations for the From/To dropdowns.
    @GetMapping("/nodes")
    public NodesResponse getNodes() {
        return routeService.nodes();
    }

    // Provides supported passenger profiles and their UI descriptions.
    @GetMapping("/profiles")
    public ProfilesResponse getProfiles() {
        return routeService.profiles();
    }
}
