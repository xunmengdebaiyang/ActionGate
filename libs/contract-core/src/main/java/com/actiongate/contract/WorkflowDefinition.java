package com.actiongate.contract;

import java.util.List;
import java.util.Map;

public record WorkflowDefinition(String workflowId, String name, String version, String entryNode, List<Node> nodes) {
    public WorkflowDefinition {
        nodes = List.copyOf(nodes);
    }

    public record Node(String id, NodeType type, String toolName, Map<String, String> transitions) {
        public Node {
            transitions = Map.copyOf(transitions);
        }
    }

    public enum NodeType {
        LLM, TOOL, APPROVAL, END
    }
}
