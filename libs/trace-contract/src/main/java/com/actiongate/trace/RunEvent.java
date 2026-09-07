package com.actiongate.trace; import java.time.Instant; public record RunEvent(String eventId,String runId,String stepId,String eventType,Instant occurredAt) {}
