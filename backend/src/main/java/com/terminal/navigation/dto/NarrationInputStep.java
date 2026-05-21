package com.terminal.navigation.dto;

public record NarrationInputStep(
        String fromId,
        String toId,
        String fromLabel,
        String toLabel,
        String edgeType,
        double floorFrom,
        double floorTo,
        String fromCheckpointType,
        String toCheckpointType
) {}
