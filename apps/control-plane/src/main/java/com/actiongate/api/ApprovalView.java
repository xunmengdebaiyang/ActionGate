package com.actiongate.api;

import java.time.Instant;
import java.util.UUID;

import com.actiongate.trace.Approval;

public record ApprovalView(UUID approvalId, UUID runId, String operator, Approval.Decision decision,
                           Instant expiresAt, String toolName, String requestHash) {
    static ApprovalView from(Approval approval) {
        return new ApprovalView(approval.approvalId(), approval.runId(), approval.operator(), approval.decision(),
                approval.expiresAt(), approval.toolName(), approval.requestHash());
    }
}
