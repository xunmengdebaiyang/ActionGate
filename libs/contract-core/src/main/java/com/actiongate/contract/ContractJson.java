package com.actiongate.contract;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class ContractJson {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    private ContractJson() {
    }

    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new ContractViolationException(List.of("JSON document must not be empty"));
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            // Do not include the parser's raw input excerpt in validation errors.
            throw new ContractViolationException(List.of("Malformed JSON, duplicate key, or trailing content"));
        }
    }

    public static <T> T convert(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (JsonProcessingException exception) {
            throw new ContractViolationException(List.of("JSON cannot be mapped to " + type.getSimpleName()));
        }
    }

    public static JsonNode tree(Object value) {
        return MAPPER.valueToTree(value);
    }
}
