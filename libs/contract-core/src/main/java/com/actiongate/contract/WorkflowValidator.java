package com.actiongate.contract;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.actiongate.contract.ContractViolationException.require;

public final class WorkflowValidator {
    private static final SchemaValidator SCHEMA = SchemaValidator.resource("/schemas/workflow.schema.json");

    private WorkflowValidator() {
    }

    public static WorkflowDefinition parseAndValidate(String json) {
        WorkflowDefinition workflow = ContractJson.convert(SCHEMA.validate(json), WorkflowDefinition.class);
        Map<String, WorkflowDefinition.Node> nodes = new HashMap<>();
        for (var node : workflow.nodes()) {
            require(nodes.putIfAbsent(node.id(), node) == null, "Duplicate node id: " + node.id());
        }
        require(nodes.containsKey(workflow.entryNode()), "Unknown entry_node: " + workflow.entryNode());
        Map<String, Integer> indegrees = new HashMap<>();
        nodes.keySet().forEach(id -> indegrees.put(id, 0));
        for (var node : workflow.nodes()) {
            for (String target : node.transitions().values()) {
                require(nodes.containsKey(target), "Unknown transition target: " + target);
                indegrees.compute(target, (id, degree) -> degree + 1);
            }
        }
        Set<String> reachable = new HashSet<>();
        var pending = new ArrayDeque<String>();
        pending.add(workflow.entryNode());
        while (!pending.isEmpty()) {
            String id = pending.remove();
            if (reachable.add(id)) {
                pending.addAll(nodes.get(id).transitions().values());
            }
        }
        require(reachable.size() == nodes.size(), "Workflow contains unreachable nodes");

        // MVP definitions are DAGs; Activity retries belong to the future execution layer.
        indegrees.forEach((id, degree) -> {
            if (degree == 0) {
                pending.add(id);
            }
        });
        int visited = 0;
        while (!pending.isEmpty()) {
            var node = nodes.get(pending.remove());
            visited++;
            for (String target : node.transitions().values()) {
                if (indegrees.compute(target, (id, degree) -> degree - 1) == 0) {
                    pending.add(target);
                }
            }
        }
        require(visited == nodes.size(), "Workflow cycles are not supported in the MVP contract");
        require(workflow.nodes().stream().anyMatch(node -> node.type() == WorkflowDefinition.NodeType.END),
                "Workflow requires an END node");
        return workflow;
    }

    public static WorkflowDefinition validateWithTools(String json, Map<String, ToolSpec> tools) {
        WorkflowDefinition workflow = parseAndValidate(json);
        for (var node : workflow.nodes()) {
            if (node.type() == WorkflowDefinition.NodeType.TOOL) {
                require(tools.containsKey(node.toolName()), "Unknown tool: " + node.toolName());
                require(tools.get(node.toolName()).toolName().equals(node.toolName()), "Tool catalog key mismatch");
            }
        }
        return workflow;
    }
}
