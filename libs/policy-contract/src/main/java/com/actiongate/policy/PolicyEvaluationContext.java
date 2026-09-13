package com.actiongate.policy;

import java.math.BigDecimal;

public record PolicyEvaluationContext(String tool, boolean sideEffect, BigDecimal amount,
                                      BigDecimal orderAmount, String idempotencyKey,
                                      String approvalStatus) {
}
