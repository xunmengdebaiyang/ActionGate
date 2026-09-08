# Temporal Milestone Verification

Date: 2026-09-08
Baseline: `9754723d2dd9595617a432c60394117192478995`

## Automated Build

Executed in the standalone `C:\ActionGate` checkout with JDK 21.0.12.1,
Maven Wrapper 3.9.11 and Temporal Java SDK/testing 1.38.0:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

All seven reactor projects passed. Test totals:

| Suite | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| ContractTest | 47 | 0 / 0 / 0 |
| PolicyValidatorTest | 29 | 0 / 0 / 0 |
| RunEventTest | 3 | 0 / 0 / 0 |
| AfterSalesWorkflowTest | 12 | 0 / 0 / 0 |
| ControlPlaneTest | 2 | 0 / 0 / 0 |
| RunsApiTest | 17 | 0 / 0 / 0 |
| TemporalUnavailableTest | 1 | 0 / 0 / 0 |
| Total | 111 | 0 / 0 / 0 |

New checks include consultation answers, manual outcomes, missing/unknown orders,
bounded transient retries, exhausted and non-retryable failures, invalid Activity/provider
results, duplicate Workflow IDs, deterministic history replay, async HTTP submission,
result lookup, pending/timeout/failure statuses, malformed requests, unknown executions
and sanitized dependency errors.

Workflow replay consumed recorded history without executing Activities again. Tests inspect
Activity scheduling history for refund/exchange requests and observe only the read-only
query and mock classification Activity types. No side-effect handlers are registered.

The API tests use real HTTP and a Temporal test service with the actual Worker implementation.
Only the unavailable-dependency error mapping test uses a mocked service.

## Packaged Real Stack

Verified with the official Temporal CLI 1.8.3, embedded Server 1.31.2, UI 2.50.1,
and the packaged Worker/API JARs, using the persistent database
`.local/temporal/temporal.db`.

The release archive SHA-256 was checked against the digest in GitHub's official release
metadata before extraction. The CLI and JDK installations are outside the repository.

Both Java health endpoints returned UP, and Temporal cluster health returned SERVING.
`scripts/smoke.ps1` passed all six real executions:

| Case | Outcome | Reason |
| --- | --- | --- |
| Known order consultation | ANSWERED | CONSULTATION_ANSWERED |
| Refund request | MANUAL_REQUIRED | REFUND_REQUIRES_HUMAN |
| Exchange request | MANUAL_REQUIRED | EXCHANGE_REQUIRES_HUMAN |
| Insufficient information | MANUAL_REQUIRED | INSUFFICIENT_INFORMATION |
| Unknown order | MANUAL_REQUIRED | ORDER_NOT_FOUND |
| Missing order ID | MANUAL_REQUIRED | MISSING_ORDER_ID |

## Persistence and Worker Availability

A separate real-process check performed these steps:

1. Stop the ActionGate Worker after the six smoke cases complete.
2. Submit a consultation for synthetic order 10002.
3. Verify RUNNING while no Worker polls the queue.
4. Stop the API and Temporal dev server, preserving the SQLite database.
5. Restart Temporal using the same database, then restart the API.
6. Verify the pending execution retains the same public and Temporal run IDs.
7. Query a previously completed consultation and verify its result is preserved.
8. Start the Worker and verify the pending execution completes with ANSWERED.

Observed public run ID: `20019dfe-f70b-48c5-b1b1-0926c1c62c91`.
Observed Temporal run ID: `01a0815f-63d1-79d6-b391-a734d1ab01c4`.

The preserved execution answered: `Order 10002 is PROCESSING.`
No replacement execution or new Temporal run ID was created.

This demonstrates persistent queuing and result retrieval across process restarts.
It is not a mid-Activity crash test, a full recovery benchmark, an approval test,
or proof of idempotent financial side effects.

## Environment Notes

- The Windows TEMP/TMP workaround from README was retained for local JDK processes.
- The first packaging attempt encountered a JAR held open by the previous demo server;
  after stopping that identified process, clean verify completed successfully.
- Runtime databases, logs, binaries and build outputs are ignored by Git.
- GitHub Actions runs the same Maven verification on Windows and Linux after push.
