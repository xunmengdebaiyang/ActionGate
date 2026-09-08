package com.actiongate.api;

import java.util.UUID;

import com.actiongate.contract.VersionReference;
import com.actiongate.workflow.RunResult;

public record RunView(UUID runId, String temporalRunId, Status status, VersionReference workflowVersion,
                      RunResult result, String errorCode) {
    public enum Status {
        RUNNING, COMPLETED, FAILED, TIMED_OUT, CANCELLED, TERMINATED, CONTINUED_AS_NEW
    }
}
