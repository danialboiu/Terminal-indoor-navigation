package com.terminal.navigation.dto;

public record NodeOption(
        String id,
        String label,
        double floor,
        String category,
        boolean selectableFrom,
        boolean selectableTo,
        String hint
) {}
