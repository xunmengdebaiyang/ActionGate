package com.actiongate.worker;

import com.actiongate.workflow.ActionResult;

/** Durable action deduplication boundary. Production uses PostgreSQL; tests may use an in-memory implementation. */
public interface ActionIdempotencyStore {
    ActionResult executeOnce(String tool, String idempotencyKey, String requestHash, String message);
}
