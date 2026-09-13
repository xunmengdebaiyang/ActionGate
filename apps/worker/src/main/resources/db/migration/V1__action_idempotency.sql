CREATE TABLE IF NOT EXISTS action_idempotency_records (
    tenant_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    action_id VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, tool_name, idempotency_key),
    CONSTRAINT action_idempotency_hash_format CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT action_idempotency_status CHECK (status IN ('PENDING', 'EXECUTED', 'FAILED_RETRYABLE', 'FAILED_FINAL'))
);

CREATE INDEX IF NOT EXISTS action_idempotency_created_at_idx
    ON action_idempotency_records (created_at);
