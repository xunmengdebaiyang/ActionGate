package com.actiongate.worker;

import java.sql.ResultSet;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.actiongate.workflow.ActionResult;

/** PostgreSQL-backed action idempotency with a unique key and request digest conflict detection. */
public final class JdbcActionIdempotencyStore implements ActionIdempotencyStore {
    private final JdbcTemplate jdbc;
    private final String tenantId;

    public JdbcActionIdempotencyStore(JdbcTemplate jdbc, String tenantId) {
        this.jdbc = jdbc;
        this.tenantId = tenantId;
    }

    @Override
    public ActionResult executeOnce(String tool, String key, String requestHash, String message) {
        String actionId = tool + "-" + requestHash.substring(0, 24);
        try {
            jdbc.update("""
                    INSERT INTO action_idempotency_records
                        (tenant_id, tool_name, idempotency_key, request_hash, status, action_id, message)
                    VALUES (?, ?, ?, ?, 'EXECUTED', ?, ?)
                    """, tenantId, tool, key, requestHash, actionId, message);
            return new ActionResult(ActionResult.Status.EXECUTED, actionId, tool, message, null);
        } catch (DuplicateKeyException duplicate) {
            return jdbc.queryForObject("""
                    SELECT request_hash, action_id, message
                    FROM action_idempotency_records
                    WHERE tenant_id = ? AND tool_name = ? AND idempotency_key = ?
                    """, (ResultSet rs, int row) -> {
                String storedHash = rs.getString("request_hash");
                String storedActionId = rs.getString("action_id");
                String storedMessage = rs.getString("message");
                if (!storedHash.equals(requestHash)) {
                    return new ActionResult(ActionResult.Status.CONFLICT, storedActionId, tool,
                            "Idempotency key is already associated with different action parameters.", null);
                }
                return new ActionResult(ActionResult.Status.IDEMPOTENT_REPLAY, storedActionId, tool,
                        storedMessage, null);
            }, tenantId, tool, key);
        }
    }
}
