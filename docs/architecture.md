# Contract Skeleton Architecture

## Module Boundaries

```text
control-plane
  +-- policy-contract
  |     +-- contract-core
  +-- trace-contract
        +-- contract-core
```

- `contract-core`: strict JSON parsing, bundled JSON Schema validation, WorkflowDefinition,
  WorkflowVersion, VersionReference and ToolSpec. Owns workflow graph validation and tool argument validation.
- `policy-contract`: the versioned Policy grammar, duplicate-rule checks, and immutable EvaluationCase
  and ReleaseDecision data records. It has no Spring dependency and performs no policy evaluation.
- `trace-contract`: AgentRun, Approval and RunEvent data records with version and trace correlation.
  No persistence, tracing exporter, or execution state machine is installed.
- `control-plane`: Spring Boot application, HTTP status and Actuator. This is the only executable JAR.

The parent POM aggregates modules and manages dependency versions. Every nested module resolves
the parent through `../../pom.xml`. Libraries remain ordinary JARs; the Boot repackage goal
runs only in the control-plane module. The obsolete root `src/` tree has been migrated.

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

This checkpoint completes the contract skeleton portion of week 1, including executable tests.
It does not complete all week-1 infrastructure tasks or the full MVP.

The next planned execution milestone is a Temporal worker, a deterministic after-sales workflow,
a Java Mock Provider, and the read-only query_order activity. Database persistence, approval
signals, business policy evaluation, idempotent side effects, tracing export and evaluation
commands remain future work.

Future Workflow code must remain deterministic; network, model and tool calls belong in Activities.
Runtime delivery semantics will be at-least-once. Approval validity and idempotency at the
tool/database boundary must be implemented and tested before side effects are enabled.
