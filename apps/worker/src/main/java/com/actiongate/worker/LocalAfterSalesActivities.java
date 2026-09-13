package com.actiongate.worker;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.actiongate.contract.ContractJson;
import com.actiongate.contract.ContractViolationException;
import com.actiongate.contract.ToolValidator;
import com.actiongate.policy.PolicyDecision;
import com.actiongate.policy.PolicyDocument;
import com.actiongate.policy.PolicyEvaluationContext;
import com.actiongate.policy.PolicyEvaluator;
import com.actiongate.policy.PolicyValidator;
import com.actiongate.workflow.ActionResult;
import com.actiongate.trace.Approval;
import com.actiongate.workflow.AfterSalesActionActivities;
import com.actiongate.workflow.AfterSalesActivities;
import com.actiongate.workflow.OrderLookup;
import com.actiongate.workflow.OrderSummary;
import com.actiongate.workflow.RunResult.Intent;
import com.actiongate.workflow.TicketInput;
import com.actiongate.workflow.TicketInput.Scenario;
import io.temporal.failure.ApplicationFailure;

public final class LocalAfterSalesActivities implements AfterSalesActivities, AfterSalesActionActivities {
    private static final Map<String, OrderSummary> ORDERS = Map.of(
            "10001", new OrderSummary("10001", new BigDecimal("199.00"), "CNY", OrderSummary.Status.SHIPPED),
            "10002", new OrderSummary("10002", new BigDecimal("89.90"), "CNY", OrderSummary.Status.PROCESSING));
    private final ToolValidator.CompiledTool queryOrder;
    private final ToolValidator.CompiledTool createRefund;
    private final ToolValidator.CompiledTool createExchange;
    private final PolicyDocument policy;
    private final MockProvider provider;

    public LocalAfterSalesActivities() {
        this(new MockProvider());
    }

    public LocalAfterSalesActivities(MockProvider provider) {
        this.provider = provider;
        try (var input = getClass().getResourceAsStream("/tools/query_order.json")) {
            if (input == null) {
                throw new IllegalStateException("Bundled query_order contract is missing");
            }
            queryOrder = ToolValidator.compile(ToolValidator.parseAndValidate(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load query_order contract", exception);
        }
        createRefund = loadTool("/tools/create_refund.json");
        createExchange = loadTool("/tools/create_exchange.json");
        policy = loadPolicy("/policies/after-sales-safety-v1.json");
    }

    @Override
    public OrderLookup queryOrder(String orderId) {
        try {
            if (orderId == null) {
                throw new ContractViolationException(java.util.List.of("order_id is required"));
            }
            queryOrder.validateArguments(ContractJson.tree(Map.of("order_id", orderId)).toString());
        } catch (ContractViolationException exception) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid order query arguments", "INVALID_TOOL_ARGUMENTS");
        }
        OrderSummary order = ORDERS.get(orderId);
        return new OrderLookup(order != null, order);
    }

    @Override
    public Intent classify(Scenario scenario) {
        if (scenario == null) {
            throw ApplicationFailure.newNonRetryableFailure("Scenario is required", "INVALID_PROVIDER_INPUT");
        }
        return provider.classify(scenario);
    }

    @Override
    public ActionResult createRefund(String orderId, BigDecimal amount, BigDecimal orderAmount,
                                     String idempotencyKey, Approval approval) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("order_id", orderId);
        args.put("amount", amount);
        args.put("idempotency_key", idempotencyKey);
        validateArguments(createRefund, args, "refund");
        PolicyDecision decision = PolicyEvaluator.evaluate(policy,
                new PolicyEvaluationContext("create_refund", true, amount, orderAmount, idempotencyKey,
                        approval == null ? "PENDING" : approval.decision().name()));
        if (!decision.allowed()) {
            return blocked(decision, "create_refund", approval);
        }
        return provider.createRefund(orderId, amount, idempotencyKey);
    }

    @Override
    public ActionResult createExchange(String orderId, String sku, BigDecimal orderAmount,
                                       String idempotencyKey) {
        if (sku == null || sku.isBlank()) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid exchange arguments", "INVALID_TOOL_ARGUMENTS");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("order_id", orderId);
        args.put("sku", sku);
        args.put("idempotency_key", idempotencyKey);
        validateArguments(createExchange, args, "exchange");
        PolicyDecision decision = PolicyEvaluator.evaluate(policy,
                new PolicyEvaluationContext("create_exchange", true, null, orderAmount, idempotencyKey,
                        "APPROVED"));
        if (!decision.allowed()) {
            return blocked(decision, "create_exchange", null);
        }
        return provider.createExchange(orderId, sku, idempotencyKey);
    }

    private ActionResult blocked(PolicyDecision decision, String tool, Approval approval) {
        boolean requiresApproval = decision.violations().contains("refund-requires-approval")
                && (approval == null || approval.decision() != Approval.Decision.REJECTED);
        return new ActionResult(requiresApproval ? ActionResult.Status.APPROVAL_REQUIRED : ActionResult.Status.REJECTED,
                null, tool, requiresApproval ? "Approval is required before this action can execute."
                        : "Action blocked by policy.", decision.violations());
    }

    private static void validateArguments(ToolValidator.CompiledTool tool, Map<String, Object> args,
                                         String action) {
        try {
            tool.validateArguments(ContractJson.tree(args).toString());
        } catch (ContractViolationException exception) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid " + action + " arguments",
                    "INVALID_TOOL_ARGUMENTS");
        }
    }

    private ToolValidator.CompiledTool loadTool(String resource) {
        try (var input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Bundled tool contract is missing: " + resource);
            }
            return ToolValidator.compile(ToolValidator.parseAndValidate(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load tool contract: " + resource, exception);
        }
    }

    private PolicyDocument loadPolicy(String resource) {
        try (var input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Bundled policy is missing: " + resource);
            }
            return PolicyValidator.parseAndValidate(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load policy: " + resource, exception);
        }
    }
}
