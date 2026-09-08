package com.actiongate.worker;

import com.actiongate.workflow.RunResult.Intent;
import com.actiongate.workflow.TicketInput.Scenario;

public final class MockProvider {
    public Intent classify(Scenario scenario) {
        return switch (scenario) {
            case ORDER_STATUS -> Intent.QUESTION;
            case REFUND_REQUEST -> Intent.REFUND;
            case EXCHANGE_REQUEST -> Intent.EXCHANGE;
            case INSUFFICIENT_INFORMATION -> Intent.MANUAL;
        };
    }
}
