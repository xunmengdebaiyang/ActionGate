package com.actiongate.worker;

import com.actiongate.workflow.ActionResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcActionIdempotencyStoreTest {
    @Test
    void detectsDigestConflictsForAnExistingPostgresRecord() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("tenant"), eq("create_refund"), eq("key")))
                .thenReturn(new ActionResult(ActionResult.Status.CONFLICT, "create_refund-old", "create_refund",
                        "Idempotency key is already associated with different action parameters.", null));

        var result = new JdbcActionIdempotencyStore(jdbc, "tenant")
                .executeOnce("create_refund", "key", "b".repeat(64), "refund");
        assertEquals(ActionResult.Status.CONFLICT, result.status());
    }
}
