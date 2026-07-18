# MissionWeaveProtocol Java SDK conformance

## Compatibility statement

SDK version `0.1.0-SNAPSHOT` targets MissionWeaveProtocol `0.1` and the wire
namespace `missionweaveprotocol`. The vendored bundle is pinned to protocol
commit
[`6f10987627d62fb296e3490ceceb5539b1e94b70`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/6f10987627d62fb296e3490ceceb5539b1e94b70).

`PROTOCOL_PIN.json` records the authoritative source, file counts, and SHA-256
tree digests:

| Artifact | JSON files | SHA-256 |
| --- | ---: | --- |
| `schemas` | 21 | `a225900a2c2a6c0d03de38ffa7d67dd16fd1586ca63b8ce1d019159fba5f0413` |
| `conformance` | 53 | `21badf03fc8b05874a744a2d66d064265c635512dd49378b8d24ab1aa0e958da` |
| complete bundle | 74 | `b5590fae29ae09e8c2ec77973405878f4dcb13d23e8acdfb888d563ec770bba7` |

The 53 conformance files are one manifest plus 52 vectors: 25 expected-valid
documents and 27 expected-invalid documents.

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
52/52 conformance vectors passed
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
and compiles a fresh Maven project that verifies packaged resources, runs all 52
vectors, and decodes a schema-valid frame.

## Deliberate limits

The Java SDK claims **schema-and-vector conformance only**. A `52/52` result does
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
