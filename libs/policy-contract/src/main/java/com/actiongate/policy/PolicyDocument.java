package com.actiongate.policy;
import java.util.List;
public record PolicyDocument(String policy_id, String version, List<Rule> rules) { public record Rule(String id, String tool, boolean approvalRequired, boolean sideEffect, String onViolation) {} }
