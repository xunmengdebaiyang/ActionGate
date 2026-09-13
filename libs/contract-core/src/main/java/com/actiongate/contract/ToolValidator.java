package com.actiongate.contract;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import static com.actiongate.contract.ContractViolationException.require;

public final class ToolValidator {
    private static final SchemaValidator SCHEMA = SchemaValidator.resource("/schemas/tool.schema.json");

    private ToolValidator() {
    }

    public static ToolSpec parseAndValidate(String json) {
        JsonNode node = SCHEMA.validate(json);
        JsonNode input = node.path("input_schema");
        Set<String> required = new HashSet<>();
        input.path("required").forEach(field -> {
            require(input.path("properties").has(field.asText()),
                    "input_schema.required references unknown property: " + field.asText());
            required.add(field.asText());
        });
        if (node.path("side_effect_level").asText().equals("SIDE_EFFECT")) {
            JsonNode key = input.path("properties").path("idempotency_key");
            require(required.contains("idempotency_key") && key.path("type").asText().equals("string")
                    && key.path("minLength").asInt() >= 1,
                    "Side-effect tools require a nonempty string idempotency_key");
        }
        return ContractJson.convert(node, ToolSpec.class);
    }

    public static JsonNode validateArguments(ToolSpec tool, String arguments) {
        return compile(tool).validateArguments(arguments);
    }

    public static CompiledTool compile(ToolSpec tool) {
        // Directly constructed records must pass the same registration checks as JSON inputs.
        ToolSpec validated = parseAndValidate(ContractJson.tree(tool).toString());
        return new CompiledTool(validated, SchemaValidator.toolInput(validated));
    }

    public static final class CompiledTool {
        private final ToolSpec tool;
        private final SchemaValidator input;

        private CompiledTool(ToolSpec tool, SchemaValidator input) {
            this.tool = tool;
            this.input = input;
        }

        public JsonNode validateArguments(String arguments) {
            JsonNode result = input.validate(arguments);
            if (tool.sideEffectLevel() == ToolSpec.SideEffectLevel.SIDE_EFFECT) {
                require(!result.path("idempotency_key").asText().isBlank(), "idempotency_key must not be blank");
            }
            return result;
        }
    }
}
