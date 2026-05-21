package com.terminal.navigation.dto;

import java.util.List;

public record AiMessageRequest(
        String message,
        List<ChatMessage> messages,
        RouteContext routeContext
) {
    public record ChatMessage(String role, String text) {}

    public record RouteContext(
            String summary,
            List<NarrationInstruction> instructions,
            List<NarrationInputStep> inputSteps
    ) {}
}
