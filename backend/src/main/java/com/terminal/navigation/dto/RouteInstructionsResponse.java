package com.terminal.navigation.dto;

import java.util.List;

public record RouteInstructionsResponse(
        String summary,
        List<NarrationInstruction> instructions,
        List<NarrationInputStep> inputSteps
) {}
