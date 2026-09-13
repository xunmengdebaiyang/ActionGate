package com.actiongate.control;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import com.actiongate.contract.ContractJson;
import com.actiongate.workflow.TicketInput;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

record SubmissionIdentity(UUID runId, String fingerprint) {
    static final String MEMO_KEY = "actiongate-submission-v1";

    static SubmissionIdentity from(TicketInput input, String key) {
        if (key == null) {
            return new SubmissionIdentity(UUID.randomUUID(), null);
        }
        if (!key.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1-128 ASCII letters, digits, dots, underscores or hyphens");
        }
        // Separate keyed UUIDs (version 8) from unkeyed random UUIDs (version 4).
        byte[] digest = digest("actiongate-consultation-request-v1:" + key);
        digest[6] = (byte) ((digest[6] & 0x0f) | 0x80);
        digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
        var bytes = ByteBuffer.wrap(digest);
        return new SubmissionIdentity(new UUID(bytes.getLong(), bytes.getLong()),
                HexFormat.of().formatHex(digest(ContractJson.tree(input).toString())));
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
