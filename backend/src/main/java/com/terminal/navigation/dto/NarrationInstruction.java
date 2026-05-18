package com.terminal.navigation.dto;

import java.util.List;

public record NarrationInstruction(int fromStepIndex, int toStepIndex, String text, List<String> warnings) {}
