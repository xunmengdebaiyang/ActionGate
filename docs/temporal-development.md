# Temporal Development Milestone

## Scope and Prerequisites

This milestone runs a compiled Java workflow through a real Temporal service. Inputs are
synthetic scenario identifiers, not free-form customer text. Only two Activities are registered:
`ActionGateQueryOrderV1` and `ActionGateMockClassifyV1`. No refund, exchange, payment, approval
or external model calls are implemented.

Use JDK 21 and the repository Maven Wrapper. Real local execution also needs the official
[Temporal CLI 1.8.3](https://github.com/temporalio/cli/releases/tag/v1.8.3).
It embeds Temporal Server 1.31.2 and a development UI. The SDK and testing library are pinned
to 1.38.0. Maven tests use TestWorkflowEnvironment and do not need the CLI, Docker or a server.

The dev server uses a SQLite file. This is local Temporal history storage, not a replacement
for the planned PostgreSQL business database. No production credentials are needed.
All services bind to loopback by default and have no production authentication setup.

## Start Locally

Build from the repository root:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

In three terminals, also at the repository root:

```powershell
# Terminal 1: keep this process running.
.\scripts\start-temporal.ps1 -TemporalCommand "C:\path\to\temporal.exe"

# Terminal 2
java -jar apps/worker/target/worker-0.2.0-SNAPSHOT-exec.jar

# Terminal 3
java -jar apps/control-plane/target/control-plane-0.2.0-SNAPSHOT.jar
```

When `temporal` is on PATH, omit `-TemporalCommand`. The startup script preserves the
database at `.local/temporal/temporal.db`; it never resets existing history.

Equivalent server command on Linux/macOS, after creating `.local/temporal`:

```bash
temporal server start-dev --ip 127.0.0.1 --ui-ip 127.0.0.1 \
  --port 7233 --ui-port 8233 --db-filename .local/temporal/temporal.db \
  --ui-disable-news-fetch
```

Health and UI:

- API: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- Worker: [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)
- Temporal UI: [http://localhost:8233](http://localhost:8233)

Health probes check that the configured Temporal namespace is reachable. Worker startup
fails if it cannot connect to Temporal within five seconds. The API can start with Temporal
unavailable, but health is DOWN and submission/query operations report dependency failure.
A healthy process does not guarantee that any particular task queue has an active poller.

The Windows TEMP workaround in README applies to both build and Java service terminals.
Before rebuilding on Windows, stop old Java processes holding the target JARs open.

## Submit and Query

```powershell
$body = @{ order_id = "10001"; scenario = "ORDER_STATUS" } | ConvertTo-Json
$run = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/runs -ContentType application/json -Body $body
Invoke-RestMethod "http://localhost:8080/api/v1/runs/$($run.run_id)"
```

POST returns HTTP 202, a Location header, a public UUID `run_id` and Temporal's
`temporal_run_id`. The internal Workflow ID is
`actiongate-consultation-<run_id>`; use it to find the execution in Temporal UI.

GET reads execution metadata and completed results from Temporal. There is no in-memory
run registry, so restarting the API does not lose results. Completed records remain queryable
for the Temporal namespace's configured retention period.

Run `.\scripts\smoke.ps1` to exercise six synthetic cases against the real local stack.

## API Contract

POST `/api/v1/runs` accepts exactly:

```json
{"order_id": "10001", "scenario": "ORDER_STATUS"}
```

`scenario` is required; `order_id` may be omitted or null. Non-null order IDs must contain
1-12 digits. Unknown fields, unknown scenarios, duplicate keys, malformed JSON and numeric
order IDs are rejected before Temporal submission. The input limit is 4096 characters.
Free-form message fields are deliberately unsupported in this mock milestone.

| Scenario / condition | Completed outcome | Reason |
| --- | --- | --- |
| ORDER_STATUS with known order | ANSWERED | CONSULTATION_ANSWERED |
| REFUND_REQUEST with known order | MANUAL_REQUIRED | REFUND_REQUIRES_HUMAN |
| EXCHANGE_REQUEST with known order | MANUAL_REQUIRED | EXCHANGE_REQUIRES_HUMAN |
| INSUFFICIENT_INFORMATION with known order | MANUAL_REQUIRED | INSUFFICIENT_INFORMATION |
| Missing order ID | MANUAL_REQUIRED | MISSING_ORDER_ID |
| Unknown order ID | MANUAL_REQUIRED | ORDER_NOT_FOUND |

The mock catalog has `10001` (SHIPPED, CNY 199.00) and `10002`
(PROCESSING, CNY 89.90). All other valid IDs return ORDER_NOT_FOUND.
Order lookup precedes classification, so missing/unknown-order reasons take precedence.

MANUAL_REQUIRED is a completed workflow result requiring future human handling; it does
not create a pending approval or wait for a person. The workflow has no side-effect tools.

GET `/api/v1/runs/{uuid}` reports RUNNING, COMPLETED, FAILED, TIMED_OUT, CANCELLED,
TERMINATED or CONTINUED_AS_NEW. Failure responses expose stable error codes without raw
Activity stack traces. Cancellation is observable but no cancellation API is provided yet.

- HTTP 400: invalid ticket or malformed run ID.
- HTTP 404: execution not found for this workflow type.
- HTTP 503: Temporal unavailable or an operation/result could not be confirmed.

Every successful POST creates a fresh execution. Submission deduplication is not implemented;
a transport timeout may happen after acceptance, so HTTP 503 does not prove that no run was
created. This milestone has no financial side effects.

## Execution Boundaries

The compiled workflow is pinned to `after-sales-consultation@1.0.0` and
`ActionGateAfterSalesConsultationV1`; its default task queue is
`actiongate-after-sales-consultation-v1`. The provider is pinned to
`mock-scenarios@1.0.0`. Existing `after-sales-v1.json` remains a future full-flow example.
The consultation JSON is a validated descriptive blueprint, not a dynamically interpreted
workflow DSL or a claim that the entire prior example can now execute.

Workflow code uses only deterministic branching and SDK Activity scheduling. Tool schema
loading, order lookup and mock classification run inside Activities. Public IDs are generated
by the API outside Workflow code. Synthetic input is bounded and stored in Temporal history;
there is no free-form prompt/customer-text store in this milestone.

Activities have a 5-second start-to-close timeout, a 20-second schedule-to-close timeout,
at most three attempts, and exponential retry intervals starting at one second and capped
at two seconds. Explicit non-retryable failures stop immediately. Invalid Activity/provider
results produce terminal workflow failures rather than endlessly failing Workflow Tasks.
API-created executions have a five-minute execution timeout, including time waiting for a Worker.

No runtime Policy evaluator is wired yet. Refund and exchange are withheld by the compiled
read-only workflow. Approval validity, refund amount limits, business audit persistence and
idempotent side effects remain future milestones.

## Configuration

| Environment variable | Default |
| --- | --- |
| ACTIONGATE_TEMPORAL_TARGET | 127.0.0.1:7233 |
| ACTIONGATE_TEMPORAL_NAMESPACE | default |
| ACTIONGATE_TEMPORAL_TASK_QUEUE | actiongate-after-sales-consultation-v1 |
| ACTIONGATE_API_PORT | 8080 |
| ACTIONGATE_WORKER_PORT | 8082 |
| ACTIONGATE_BIND_ADDRESS | 127.0.0.1 |

API and Worker must use the same target, namespace and task queue. Create a custom namespace
before starting either service. The dev script supports custom `-Port` and `-UiPort`.

Stop services with Ctrl+C in their terminals. Keep the SQLite database to preserve histories.
Do not modify Workflow v1 behavior in place after storing histories without a compatible
Temporal versioning/migration strategy and replay tests.
