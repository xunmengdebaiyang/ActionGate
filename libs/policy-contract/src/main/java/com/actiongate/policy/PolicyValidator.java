package com.actiongate.policy;

import java.util.HashSet;
import java.util.Set;

import com.actiongate.contract.ContractJson;
import com.actiongate.contract.SchemaValidator;

import static com.actiongate.contract.ContractViolationException.require;

public final class PolicyValidator {
    private static final SchemaValidator SCHEMA = SchemaValidator.resource("/schemas/policy.schema.json");

    private PolicyValidator() {
    }

    public static PolicyDocument parseAndValidate(String json) {
        PolicyDocument policy = ContractJson.convert(SCHEMA.validate(json), PolicyDocument.class);
        Set<String> ids = new HashSet<>();
        for (var rule : policy.rules()) {
            require(ids.add(rule.id()), "Duplicate rule id: " + rule.id());
        }
        return policy;
    }
}
