package com.actiongate.contract;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

public final class SchemaValidator {
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final JsonSchema schema;

    private SchemaValidator(JsonSchema schema) {
        this.schema = schema;
    }

    public static SchemaValidator resource(String path) {
        try (InputStream input = SchemaValidator.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled schema: " + path);
            }
            return new SchemaValidator(FACTORY.getSchema(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled schema: " + path, exception);
        }
    }

    // Only schema trees produced from the restricted ToolSpec contract may enter here.
    static SchemaValidator toolInput(ToolSpec tool) {
        return new SchemaValidator(FACTORY.getSchema(tool.inputSchema()));
    }

    public JsonNode validate(String json) {
        JsonNode node = ContractJson.parse(json);
        validate(node);
        return node;
    }

    public void validate(JsonNode node) {
        var violations = schema.validate(node).stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .toList();
        if (!violations.isEmpty()) {
            throw new ContractViolationException(violations);
        }
    }
}
