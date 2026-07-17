# MissionWeaveProtocol Java SDK conformance

## Compatibility statement

SDK version `0.1.0-SNAPSHOT` targets MissionWeaveProtocol `0.1` and the wire
namespace `missionweaveprotocol`. The vendored bundle is pinned to protocol
commit
[`00964ea9064cbf1f0eca8af21a0c57367ee14752`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/00964ea9064cbf1f0eca8af21a0c57367ee14752).

`PROTOCOL_PIN.json` records the authoritative source, file counts, and SHA-256
tree digests:

| Artifact | JSON files | SHA-256 |
| --- | ---: | --- |
| `schemas` | 21 | `cbb37b7d55ad1a21a01370d6c09677b05dcd1383d6d77fa60b9c58b0fd85c624` |
| `conformance` | 44 | `100d2d2104d07bd7dcfbde354555a85d244f4b7c20c1c5dda0136ce36b4b8675` |
| complete bundle | 65 | `281fb1ec9b73e07f7a2897e576dbbad021085cf7293c1e9450ba3fbdec7f2cda` |

The 44 conformance files are one manifest plus 43 vectors: 22 expected-valid
documents and 21 expected-invalid documents.

## What the runner checks

`SchemaCatalog` and `ConformanceRunner` provide the current conformance layer:

- strict Jackson parsing rejects duplicate object members and trailing data;
- every schema declares and is compiled as JSON Schema Draft 2020-12;
- all `$ref` resolution uses an in-memory registry of the 21 packaged schemas;
- the validator does not enable remote schema fetching;
- JSON Schema format assertions are enabled;
- each manifest entry is checked against its named schema and expected validity;
- the source tree, compiled classpath, built JAR, and installed Maven consumer
  are exercised independently.

Run the packaged vectors:

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java
```

Run a separate protocol checkout or release bundle:

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

Successful output is:

```text
43/43 conformance vectors passed
```

## Build gates

The protected GitHub check runs:

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

`verify` runs unit tests, creates the binary and source JARs, checks formatting,
and executes integration tests against the built binary JAR. The installed
consumer smoke test then installs `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT`
and compiles a fresh Maven project that verifies packaged resources, runs all 43
vectors, and decodes a schema-valid frame.

## Deliberate limits

The Java SDK claims **schema-and-vector conformance only**. A `43/43` result does
not establish complete MissionWeaveProtocol conformance.

In particular, this SDK does not by itself implement or certify:

- transport interoperability or WebSocket connection management;
- Organization identity, Agent Registry trust, authorization, or revocation;
- Mission and WorkItem state transitions;
- durable Group event ordering and idempotency;
- coordinator leases, session or membership fencing, or worker scheduling;
- budget accounting, approval policy, retry behavior, or persistence;
- end-to-end security or production readiness.

Applications and higher-level runtimes must implement those behaviors from the
normative protocol specification and test them independently.
