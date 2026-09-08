package com.actiongate.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public record WorkflowVersion(VersionReference reference, String definitionHash, WorkflowDefinition definition) {
    public static WorkflowVersion fromJson(String json) {
        WorkflowDefinition definition = WorkflowValidator.parseAndValidate(json);
        try {
            byte[] bytes = sorted(ContractJson.tree(definition)).toString().getBytes(StandardCharsets.UTF_8);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new WorkflowVersion(new VersionReference(definition.workflowId(), definition.version()),
                    hash, definition);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    // Stable JSON object ordering; node/array order remains part of the versioned definition.
    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            var fields = new TreeMap<String, JsonNode>();
            node.properties().forEach(field -> fields.put(field.getKey(), sorted(field.getValue())));
            return JsonNodeFactory.instance.objectNode().setAll(fields);
        }
        if (node.isArray()) {
            var array = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> array.add(sorted(value)));
            return array;
        }
        return node;
    }
}
