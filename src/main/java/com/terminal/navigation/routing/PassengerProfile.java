package com.terminal.navigation.routing;

import com.terminal.navigation.model.EdgeType;

import java.util.Set;

public enum PassengerProfile {

    PASSENGER(
            Set.of(EdgeType.CORRIDOR, EdgeType.STAIRS, EdgeType.ESCALATOR, EdgeType.ELEVATOR)
    ),

    PARENT_WITH_STROLLER(
            Set.of(EdgeType.CORRIDOR, EdgeType.ELEVATOR)
    ),

    ELDERLY(
            Set.of(EdgeType.CORRIDOR, EdgeType.ESCALATOR, EdgeType.ELEVATOR)
    ),

    WHEELCHAIR_USER(
            Set.of(EdgeType.CORRIDOR, EdgeType.ELEVATOR)
    );

    private final Set<EdgeType> allowedTypes;

    PassengerProfile(Set<EdgeType> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }

    public boolean isAllowed(EdgeType type) {
        return allowedTypes.contains(type);
    }
}
