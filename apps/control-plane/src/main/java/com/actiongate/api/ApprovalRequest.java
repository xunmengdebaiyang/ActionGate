package com.actiongate.api;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.actiongate.contract.ContractJson;
import com.actiongate.contract.ContractViolationException;
import com.actiongate.trace.Approval;
import com.fasterxml.jackson.databind.JsonNode;

import static com.actiongate.contract.ContractViolationException.require;

record ApprovalRequest(UUID approvalId, String operator, Approval.Decision decision, Instant expiresAt,
                       String toolName, String requestHash) {
    private static final Set<String> FIELDS = Set.of("approval_id", "operator", "decision", "expires_at",
            "tool_name", "request_hash");

    static ApprovalRequest parse(String json) {
        JsonNode node = ContractJson.parse(json);
        require(node.isObject(), "Approval must be a JSON object");
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        require(FIELDS.containsAll(fields) && fields.containsAll(Set.of("operator", "decision", "expires_at",
                "tool_name", "request_hash")), "Approval contains unknown or missing fields");
        return ContractJson.convert(node, ApprovalRequest.class);
    }

    Approval toApproval(UUID runId) {
        return new Approval(approvalId == null ? UUID.randomUUID() : approvalId, runId, operator, decision,
                expiresAt, toolName, requestHash);
    }
}
