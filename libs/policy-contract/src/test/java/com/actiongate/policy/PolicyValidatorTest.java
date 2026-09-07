package com.actiongate.policy;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class PolicyValidatorTest { @Test void acceptsValidPolicy(){assertEquals("after-sales-safety",PolicyValidator.parseAndValidate("{\"policy_id\":\"after-sales-safety\",\"version\":\"1.0.0\",\"rules\":[{\"id\":\"refund\",\"tool\":\"create_refund\"}]} ").policy_id());} @Test void rejectsMissingRules(){assertThrows(IllegalArgumentException.class,()->PolicyValidator.parseAndValidate("{\"policy_id\":\"x\",\"version\":\"1\",\"rules\":[]}"));} }
