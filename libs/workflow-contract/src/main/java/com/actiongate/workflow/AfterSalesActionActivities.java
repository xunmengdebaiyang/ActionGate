package com.actiongate.workflow;

import java.math.BigDecimal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AfterSalesActionActivities {
    @ActivityMethod(name = "ActionGateCreateRefundV1")
    ActionResult createRefund(String orderId, BigDecimal amount, BigDecimal orderAmount,
                              String idempotencyKey, TicketInput.ApprovalStatus approvalStatus);

    @ActivityMethod(name = "ActionGateCreateExchangeV1")
    ActionResult createExchange(String orderId, String sku, BigDecimal orderAmount,
                                String idempotencyKey, TicketInput.ApprovalStatus approvalStatus);
}
