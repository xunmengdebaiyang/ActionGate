package com.actiongate.workflow;

import java.math.BigDecimal;

import com.actiongate.trace.Approval;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AfterSalesActionActivities {
    @ActivityMethod(name = "ActionGateCreateRefundV1")
    ActionResult createRefund(String orderId, BigDecimal amount, BigDecimal orderAmount,
                              String idempotencyKey, Approval approval);

    @ActivityMethod(name = "ActionGateCreateExchangeV1")
    ActionResult createExchange(String orderId, String sku, BigDecimal orderAmount,
                                String idempotencyKey);
}
