# Temporal Development Milestone

## Scope and Prerequisites

This milestone runs a compiled Java workflow through a real Temporal service. Inputs are
synthetic scenario identifiers, not free-form customer text. The worker registers read-only
lookup/classification Activities plus Mock `create_refund` and `create_exchange` Activities.
The latter are guarded by the bundled Policy and never call a real order or payment system.

Use JDK 21 and the repository Maven Wrapper. Real local execution also needs the official
[Temporal CLI 1.8.3](https://github.com/temporalio/cli/releases/tag/v1.8.3).
It embeds Temporal Server 1.31.2 and a development UI. The SDK and testing library are pinned
to 1.38.0. Maven tests use TestWorkflowEnvironment and do not need the CLI, Docker or a server.

The dev server uses a SQLite file. This is local Temporal history storage, not a replacement
for the PostgreSQL business database used by the Worker for action idempotency. The control plane
requires API keys by default; local tests explicitly disable that protection. All services bind
to loopback by default.

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

POST `/api/v1/runs` accepts a ticket with optional action fields:

```json
{"order_id": "10001", "scenario": "ORDER_STATUS"}
```

An action request contains no approval status. Submit the refund first, then send a separate
Approval record to the run:

```json
{"order_id":"10001","scenario":"REFUND_REQUEST","amount":50.00,
 "idempotency_key":"refund-10001-1"}
```

The approval endpoint is `POST /api/v1/runs/{run_id}/approval` and accepts `operator`,
`decision`, `expires_at`, `tool_name` and a SHA-256 `request_hash`. For a refund,
`request_hash` is the digest of the canonical action parameters. The workflow checks the run,
tool, digest, decision and expiry before calling the action Activity; invalid or expired
approvals complete with `MANUAL_REQUIRED`.

An exchange uses `sku` and `idempotency_key`. Action fields are optional for compatibility;
an action scenario without its required fields remains a manual result. Amounts are checked
against the synthetic order total at the runtime Policy boundary.

Clients may send an `Idempotency-Key` header containing 1-128 ASCII letters, digits,
periods, underscores or hyphens. Repeating the same key with the same validated ticket
returns the original run, including after the control plane restarts. Reusing a key with
different ticket content returns HTTP 409. Requests without the header keep the original
fresh-run behavior. The key fingerprint is stored in Temporal memo metadata. Action-level
idempotency records are stored in PostgreSQL and include the request digest, so reusing a key
with different parameters returns a conflict instead of replaying the original action.

`scenario` is required; `order_id` may be omitted or null. Non-null order IDs must contain
1-12 digits. Unknown fields, unknown scenarios, duplicate keys, malformed JSON and numeric
order IDs are rejected before Temporal submission. The input limit is 4096 characters.
Free-form message fields are deliberately unsupported in this mock milestone.

| Scenario / condition | Completed outcome | Reason |
| --- | --- | --- |
| ORDER_STATUS with known order | ANSWERED | CONSULTATION_ANSWERED |
| REFUND_REQUEST with known order | MANUAL_REQUIRED | REFUND_REQUIRES_HUMAN |
| EXCHANGE_REQUEST with known order | MANUAL_REQUIRED | EXCHANGE_REQUIRES_HUMAN |
| Approved refund within order amount | ACTION_EXECUTED | REFUND_EXECUTED |
| Valid exchange with a new idempotency key | ACTION_EXECUTED | EXCHANGE_EXECUTED |
| Repeated action idempotency key | ACTION_REPLAYED | IDEMPOTENT_REPLAY |
| Refund without approval | MANUAL_REQUIRED | APPROVAL_REQUIRED |
| Refund above order amount | MANUAL_REQUIRED | POLICY_BLOCKED |
| INSUFFICIENT_INFORMATION with known order | MANUAL_REQUIRED | INSUFFICIENT_INFORMATION |
| Missing order ID | MANUAL_REQUIRED | MISSING_ORDER_ID |
| Unknown order ID | MANUAL_REQUIRED | ORDER_NOT_FOUND |

The mock catalog has `10001` (SHIPPED, CNY 199.00) and `10002`
(PROCESSING, CNY 89.90). All other valid IDs return ORDER_NOT_FOUND.
Order lookup precedes classification, so missing/unknown-order reasons take precedence.

MANUAL_REQUIRED is a completed workflow result requiring future human handling; it does
not create a pending approval or wait for a person. For a refund lacking approval or exceeding
the order amount, the policy-gated Activity returns this result without calling the Mock Provider.

GET `/api/v1/runs/{uuid}` reports RUNNING, COMPLETED, FAILED, TIMED_OUT, CANCELLED,
TERMINATED or CONTINUED_AS_NEW. Failure responses expose stable error codes without raw
Activity stack traces. Cancellation is observable but no cancellation API is provided yet.

- HTTP 400: invalid ticket or malformed run ID.
- HTTP 404: execution not found for this workflow type.
- HTTP 503: Temporal unavailable or an operation/result could not be confirmed.

Submission has a five-second overall Temporal request deadline by default, configurable with
`ACTIONGATE_TEMPORAL_REQUEST_TIMEOUT` up to one minute. The deadline covers connection setup,
RPC retries and result lookup. A transport timeout may happen after acceptance, so HTTP 503 does
not prove that no run was created; retry with the same `Idempotency-Key` to recover the receipt.
This milestone has no financial side effects.

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

The runtime Policy evaluator is wired at the side-effect Activity boundary. It evaluates the
active bundled policy before the Mock Provider is called. Refund approval, order amount limits,
and idempotency-key presence are enforced; violations return a completed manual result and do
not execute the provider. The Mock Provider uses the PostgreSQL idempotency store in the Worker
process. Tests use an in-memory store only when exercising the workflow without a database.
Integration with real side-effect systems remains future work.

## Configuration

| Environment variable | Default |
| --- | --- |
| ACTIONGATE_TEMPORAL_TARGET | 127.0.0.1:7233 |
| ACTIONGATE_TEMPORAL_NAMESPACE | default |
| ACTIONGATE_TEMPORAL_TASK_QUEUE | actiongate-after-sales-consultation-v1 |
| ACTIONGATE_TEMPORAL_REQUEST_TIMEOUT | 5s (maximum 1m) |
| ACTIONGATE_TEMPORAL_API_KEY | empty; requires TLS when set |
| ACTIONGATE_TEMPORAL_TLS_ENABLED | false |
| ACTIONGATE_TEMPORAL_TLS_TRUST_CERT_PATH | empty |
| ACTIONGATE_TEMPORAL_TLS_SERVER_NAME | empty; optional TLS authority override |
| ACTIONGATE_TEMPORAL_TLS_CLIENT_CERT_PATH | empty |
| ACTIONGATE_TEMPORAL_TLS_CLIENT_KEY_PATH | empty |
| ACTIONGATE_API_KEY | required when control-plane security is enabled |
| ACTIONGATE_APPROVAL_API_KEY | required for approval requests |
| ACTIONGATE_RATE_LIMIT_PER_MINUTE | 60 |
| ACTIONGATE_MAX_BODY_BYTES | 4096 |
| ACTIONGATE_DB_URL | jdbc:postgresql://127.0.0.1:5432/actiongate |
| ACTIONGATE_DB_USERNAME | actiongate |
| ACTIONGATE_DB_PASSWORD | empty |
| ACTIONGATE_API_PORT | 8080 |
| ACTIONGATE_WORKER_PORT | 8082 |
| ACTIONGATE_BIND_ADDRESS | 127.0.0.1 |

API and Worker must use the same target, namespace and task queue. Create a custom namespace
before starting either service and provision the PostgreSQL database before starting the Worker.
The dev script supports custom `-Port` and `-UiPort`.

Stop services with Ctrl+C in their terminals. Keep the SQLite database to preserve histories.
Do not modify Workflow v1 behavior in place after storing histories without a compatible
Temporal versioning/migration strategy and replay tests. Released V1 histories are checked in
under `apps/worker/src/test/resources/history/`; CI replays each fixed history and includes a
deliberately incompatible workflow test that must fail replay.
