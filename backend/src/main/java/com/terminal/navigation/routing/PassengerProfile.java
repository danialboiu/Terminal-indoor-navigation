package com.terminal.navigation.routing;

import com.terminal.navigation.model.EdgeType;

import java.util.Set;

public enum PassengerProfile {

    PASSENGER(
            "Standard passenger - can use all paths",
            Set.of(EdgeType.CORRIDOR, EdgeType.STAIRS, EdgeType.ESCALATOR, EdgeType.ELEVATOR)
    ),

    PARENT_WITH_STROLLER(
            "Parent with stroller - cannot use stairs or escalators",
            Set.of(EdgeType.CORRIDOR, EdgeType.ELEVATOR)
    ),

    ELDERLY(
            "Elderly passenger - cannot use stairs",
            Set.of(EdgeType.CORRIDOR, EdgeType.ESCALATOR, EdgeType.ELEVATOR)
    ),

    WHEELCHAIR_USER(
            "Wheelchair user - cannot use stairs or escalators",
            Set.of(EdgeType.CORRIDOR, EdgeType.ELEVATOR)
    );

    private final String description;
    private final Set<EdgeType> allowedTypes;

    PassengerProfile(String description, Set<EdgeType> allowedTypes) {
        this.description = description;
        this.allowedTypes = allowedTypes;
    }

    public String description() {
        return description;
    }

    public boolean isAllowed(EdgeType type) {
        return allowedTypes.contains(type);
    }
}
