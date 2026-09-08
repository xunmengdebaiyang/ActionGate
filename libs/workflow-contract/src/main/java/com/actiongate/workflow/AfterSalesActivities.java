package com.actiongate.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AfterSalesActivities {
    @ActivityMethod(name = "ActionGateQueryOrderV1")
    OrderLookup queryOrder(String orderId);

    @ActivityMethod(name = "ActionGateMockClassifyV1")
    RunResult.Intent classify(TicketInput.Scenario scenario);
}
