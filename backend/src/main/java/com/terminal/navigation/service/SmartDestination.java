package com.terminal.navigation.service;

import com.terminal.navigation.dto.NodeOption;
import com.terminal.navigation.model.Node;

import java.util.Arrays;
import java.util.Optional;

enum SmartDestination {
    NEAREST_TOILET(
            "NEAREST_TOILET",
            "Nearest toilet",
            "Smart destinations",
            "Automatic closest reachable toilet",
            "TOILET"
    );

    private final String id;
    private final String label;
    private final String category;
    private final String hint;
    private final String poiType;

    SmartDestination(String id, String label, String category, String hint, String poiType) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.hint = hint;
        this.poiType = poiType;
    }

    NodeOption toNodeOption() {
        return new NodeOption(id, label, 0.0, category, false, true, hint);
    }

    boolean matches(Node node) {
        return node != null && node.poiType != null && poiType.equalsIgnoreCase(node.poiType.trim());
    }

    static Optional<SmartDestination> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(destination -> destination.id.equals(normalized))
                .findFirst();
    }
}
