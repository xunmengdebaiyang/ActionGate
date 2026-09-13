package com.actiongate.worker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.actiongate.workflow.ActionResult;

final class InMemoryActionIdempotencyStore implements ActionIdempotencyStore {
    private final Map<String, Entry> records = new ConcurrentHashMap<>();

    @Override
    public ActionResult executeOnce(String tool, String key, String requestHash, String message) {
        String recordKey = tool + ":" + key;
        Entry candidate = new Entry(requestHash, tool + "-" + requestHash.substring(0, 24), message);
        Entry existing = records.putIfAbsent(recordKey, candidate);
        if (existing == null) {
            return new ActionResult(ActionResult.Status.EXECUTED, candidate.actionId(), tool, message, null);
        }
        if (!existing.requestHash().equals(requestHash)) {
            return new ActionResult(ActionResult.Status.CONFLICT, existing.actionId(), tool,
                    "Idempotency key is already associated with different action parameters.", null);
        }
        return new ActionResult(ActionResult.Status.IDEMPOTENT_REPLAY, existing.actionId(), tool,
                existing.message(), null);
    }

    private record Entry(String requestHash, String actionId, String message) {
    }
}
