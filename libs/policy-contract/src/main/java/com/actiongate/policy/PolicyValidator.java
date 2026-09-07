package com.actiongate.policy;
import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper;
public final class PolicyValidator { private static final ObjectMapper M=new ObjectMapper(); private PolicyValidator() {}
 public static PolicyDocument parseAndValidate(String json) { try { JsonNode r=M.readTree(json); req(r,"policy_id"); req(r,"version"); if(!r.path("rules").isArray()||r.path("rules").isEmpty()) throw new IllegalArgumentException("rules must not be empty"); for(JsonNode x:r.path("rules")){req(x,"id");req(x,"tool");} return M.treeToValue(r,PolicyDocument.class); } catch(Exception e){throw new IllegalArgumentException("Invalid policy contract: "+e.getMessage(),e);} }
 private static void req(JsonNode n,String f){if(!n.hasNonNull(f)||n.path(f).asText().isBlank())throw new IllegalArgumentException("Missing field: "+f);}
}
