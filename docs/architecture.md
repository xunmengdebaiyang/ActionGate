# ActionGate Architecture

## Module Boundaries

```text
control-plane
  +-- workflow-contract -- contract-core
  +-- policy-contract
  |     +-- contract-core
  +-- trace-contract
        +-- contract-core

worker
  +-- workflow-contract -- contract-core
```

- `contract-core`: strict JSON parsing, bundled JSON Schema validation, WorkflowDefinition,
  WorkflowVersion, VersionReference and ToolSpec. Owns workflow graph validation and tool argument validation.
- `policy-contract`: the versioned Policy grammar, duplicate-rule checks, immutable EvaluationCase
  and ReleaseDecision data records, and the small runtime PolicyEvaluator. It has no Spring dependency.
- `trace-contract`: AgentRun, Approval and RunEvent data records with version and trace correlation.
  No persistence, tracing exporter, or execution state machine is installed.
- `workflow-contract`: versioned Temporal Workflow/Activity interfaces, synthetic ticket schema,
  and bounded input/result records. Does not depend on either application.
- `worker`: compiled consultation workflow, Mock Provider, read-only order Activity and policy-gated
  refund/exchange Activities;
  Spring Boot process with Temporal namespace health.
- `control-plane`: Spring Boot submission/query API. Uses Temporal for execution state and
  results, with no local run registry.

The parent POM aggregates modules and manages dependency versions. Every nested module resolves
the parent through `../../pom.xml`. Libraries remain ordinary JARs. Worker publishes an
additional executable `-exec.jar`, allowing its normal JAR to be reused by API integration
tests without starting another application. Control-plane explicitly scans only its own
API/configuration packages, so the Worker configuration cannot leak into the API process.

## Validation Boundary

External JSON enters through the validators, not through direct record construction:

1. Reject malformed JSON, duplicate object keys, and trailing documents.
2. Validate against the bundled Draft 2020-12 schema using networknt.
3. Map snake_case JSON to Java records.
4. Enforce the cross-field rules that JSON Schema cannot express conveniently.

The public record constructors describe data shapes; they are not substitutes for the complete
Workflow, Tool, and Policy validation entry points. Lists, maps, and the mutable ToolSpec schema
tree are defensively copied. Exceptions expose structured violation messages.

The shared engine lives in contract-core to keep the two schema consumers consistent without
coupling a library to Spring Boot. Schemas are loaded from the classpath, so packaged JAR consumers
do not depend on the repository working directory.

## Plan Alignment

The contract skeleton, read-only consultation path and week-3 policy-gated Mock side-effect path
are implemented. The consultation workflow queries synthetic orders and classifies fixed scenarios
through Activities. Action requests with complete fields call Mock refund/exchange Activities only
after policy evaluation; missing approval, excessive refund amounts and missing idempotency keys
complete with a manual result.

Real local execution uses the official Temporal dev server with a SQLite history file.
Tests use TestWorkflowEnvironment, including deterministic replay, fixed released-history replay,
bounded Activity retry,
terminal failures, and HTTP submission/result querying. See
[Temporal development](temporal-development.md) and [verification](temporal-verification.md).

PostgreSQL business persistence, durable approval signals, real side-effect adapters, tracing export,
model integration and evaluation commands remain future work.

Workflow code must remain deterministic; network, model and tool calls belong in Activities.
Runtime delivery semantics are at-least-once. Approval validity and idempotency at the
tool/database boundary must be implemented before connecting production side effects.
