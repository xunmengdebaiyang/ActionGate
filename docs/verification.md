# Verification Record

Date: 2026-09-08

## Baseline

Before changes, both local copies and GitHub main referenced
`9f6d26269242d94acbc4b9750fc6cbc8189dd667`. The remote revision was confirmed
through GitHub's API and later through Git ls-remote. Implementation takes place
in the standalone `C:\ActionGate` working copy; the earlier Documents copy is unchanged.

## Build

Verified on Windows with Azul Zulu JDK 21.0.12.1 and Maven Wrapper 3.9.11:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

The full reactor succeeds and produces an executable
`apps/control-plane/target/control-plane-0.2.0-SNAPSHOT.jar`.
Library modules are ordinary JARs.

| Suite | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| ContractTest | 46 | 0 / 0 / 0 |
| PolicyValidatorTest | 29 | 0 / 0 / 0 |
| RunEventTest | 3 | 0 / 0 / 0 |
| ControlPlaneTest | 2 | 0 / 0 / 0 |
| Total | 80 | 0 / 0 / 0 |

The tests cover real example files; invalid schema types, fields and versions;
duplicate IDs; unknown/unreachable graph references and cycles; required idempotency
keys; malformed and duplicate-key JSON; argument schemas; immutable collections;
workflow hashes; policy serialization; and trace/version correlation.

ControlPlaneTest starts Spring Boot on a random port and checks status and Actuator
over real HTTP. It requires no Docker, database, Temporal, API keys or model service.

The packaged JAR was also started separately on port 8080. Both
`/api/v1/status` (version 0.2.0, ready) and `/actuator/health` (UP)
returned successful HTTP responses. This verifies the executable artifact and
the migrated application resources outside the test classpath.

## Local Environment Finding

The first HTTP test run failed while the JDK initialized its Windows selector pipe:
`UnixDomainSockets.connect: Invalid argument`. The machine's TEMP path used an
8.3 short name. Setting TEMP and TMP to an existing ordinary project-local directory
for the build process resolved it. This is documented in the README; no tests were
disabled and no system-wide settings were changed.

GitHub Actions is configured for Java 21 on Windows and Linux. Local Windows results
are reported above; hosted CI results must be read from GitHub after pushing.

## Scope Limit

These checks establish a buildable, testable contract skeleton. They do not demonstrate
Temporal recovery, real policy enforcement, audit persistence or idempotent side effects.
No claims of production readiness or exactly-once execution are made.
