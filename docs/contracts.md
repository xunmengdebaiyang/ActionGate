# Versioned Contracts

## Schemas and Examples

| Contract | Bundled schema | Repository example |
| --- | --- | --- |
| Workflow | contract-core: /schemas/workflow.schema.json | workflows/after-sales-v1.json |
| Tool | contract-core: /schemas/tool.schema.json | tools/query_order.json, create_refund.json, create_exchange.json |
| Policy | policy-contract: /schemas/policy.schema.json | policies/after-sales-safety-v1.json |

Schemas are under each module's `src/main/resources`. Examples are instance documents,
not schema files. Tests load these exact example files as Maven test resources.

Identifiers use `[a-z][a-z0-9_-]{0,63}`. Versions are concrete `major.minor.patch` values
without leading zeroes; prerelease tags and aliases such as `latest` are not supported yet.
Unknown properties, incorrect JSON types and empty required values are rejected.
Unsupported grammar must be introduced through an explicit contract revision.

## Workflow

A definition contains `workflow_id`, `name`, `version`, `entry_node`, and `nodes`.
Each node has an `id`, a `type` and a `transitions` map from outcome names to node IDs.
TOOL nodes require `tool_name`; other node types must omit it.

LLM, TOOL and APPROVAL are the planned execution nodes. END is a structural terminal marker,
not another executable node type. Nonterminal nodes require transitions; END has none.
Definitions are DAGs in this checkpoint: all nodes must be reachable, transitions must resolve,
IDs must be unique and cycles are rejected. Temporal Activity retries will be handled by the
execution layer rather than graph loops.

`WorkflowValidator.validateWithTools(json, catalog)` also checks that referenced tools exist.
The example reaches the refund tool only through the approval node's `approved` outcome.
This describes the desired workflow; the contract validator does not prove that arbitrary
graphs enforce approval before every side effect.

`WorkflowVersion.fromJson` returns the validated definition and a SHA-256 hash of normalized
JSON with sorted object keys. Whitespace and JSON object ordering are ignored; array order,
the version string, and definition content contribute to the hash. This is a local format,
not an implementation of a general canonical JSON standard.

## Tools and Arguments

ToolSpec contains `tool_name`, `version`, `side_effect_level` and `input_schema`.
READ_ONLY and SIDE_EFFECT are the supported risk levels.

The MVP input-schema dialect is intentionally restricted:

- object input with explicit `properties`, `required` and `additionalProperties: false`;
- string properties with optional positive `minLength` and a nonempty, unique string `enum`;
- integer/number properties with optional nonnegative `minimum`;
- boolean properties.

References, remote schemas, nested objects, defaults, coercion, arrays and other keywords
are rejected by the ToolSpec schema. Required names must exist in properties.
SIDE_EFFECT tools must require an `idempotency_key` string with `minLength >= 1`.
Argument validation also rejects whitespace-only idempotency keys.

```java
ToolSpec tool = ToolValidator.parseAndValidate(toolSpecJson);
JsonNode args = ToolValidator.validateArguments(tool, argumentsJson);
```

Arguments are validated using the declared input schema, with no string-to-number conversion.
The refund example requires a positive amount. An amount larger than an order total is a
runtime business-policy violation, not a schema violation: there is no live order context here.
No tool calls, refunds, exchanges or deduplication are executed by these validators.

## Policy

PolicyDocument contains `policy_id`, `version`, `status` and a nonempty `rules` array.
Status is DRAFT, ACTIVE or RETIRED. Rule IDs must be unique and each rule uses:

```json
{
  "id": "refund-requires-approval",
  "when": {"tool": "create_refund"},
  "assert": {"approval.status": "APPROVED"},
  "on_violation": "BLOCK"
}
```

A condition is exactly one of `tool`, `side_effect: true`, or a nonempty `tool_not_in` list.
An assertion is exactly one of:

- `approval.status: APPROVED`;
- `args.amount_lte: context.order_amount`;
- `args.idempotency_key_present: true`;
- `always: false`.

All initial rules use BLOCK on violation. Unknown operators and mixed condition/assertion
forms are rejected. `PolicyValidator.parseAndValidate` validates syntax and rule IDs;
it does not evaluate the assertions, inspect approvals, or authorize actions.

## Runtime and Evaluation Data Records

AgentRun binds a run ID to immutable workflow/policy version references and a status.
Approval identifies the run, operator, decision and expiry. Its presence alone does not
establish a currently valid approval; expiry checks belong to the future runtime.

RunEvent includes run/step IDs, time, lowercase event_type, nonzero OTel trace/span IDs
and pinned workflow/policy references. Persistence must eventually enforce append-only
events. The current record does not provide an audit database or emit spans.

EvaluationCase and ReleaseDecision reserve the planned evaluation data shapes.
PASS requires zero violations and FAIL requires at least one. These records do not
provide an evaluator, release gate, dataset runner or deployment API.

Callers should use `ContractJson` for JSON mapping. Collections and schema trees returned
by the immutable contracts cannot be used to mutate the underlying records.
