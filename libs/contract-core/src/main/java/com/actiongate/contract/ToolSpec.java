package com.actiongate.contract;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolSpec(String toolName, String version, SideEffectLevel sideEffectLevel, JsonNode inputSchema) {
    public ToolSpec {
        Objects.requireNonNull(toolName);
        Objects.requireNonNull(version);
        Objects.requireNonNull(sideEffectLevel);
        inputSchema = Objects.requireNonNull(inputSchema).deepCopy();
    }

    @Override
    public JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }

    public enum SideEffectLevel {
        READ_ONLY, SIDE_EFFECT
    }
}
