# MissionWeaveProtocol Java SDK conformance

## Compatibility statement

SDK version `0.1.0-SNAPSHOT` targets MissionWeaveProtocol `0.1` and the wire
namespace `missionweaveprotocol`. The vendored bundle is pinned to protocol
commit
[`f7e70a72c76bbeb5014c186cd820aac2112f0dde`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/f7e70a72c76bbeb5014c186cd820aac2112f0dde).

`PROTOCOL_PIN.json` records the authoritative source, file counts, and SHA-256
tree digests:

| Artifact | JSON files | SHA-256 |
| --- | ---: | --- |
| `schemas` | 22 | `941a5a19b8664207f1ff48b799219c2f981ecd491a5cca527d586028d976ec76` |
| `conformance` | 59 | `2362acd8345e5860e605ed06984f1673a1ea0a00e76c1fe00fed222326782f24` |
| complete bundle | 81 | `c95fc8f8334947dacf51a2c6e84d9b13f5b39b7d3827591569a1e2c5acfe47d7` |

The 59 conformance files are one manifest plus 58 vectors: 27 expected-valid documents and 31
expected-invalid documents. The 22nd Schema and two new structural vectors define the
First-Admission Record as durable metadata rather than a Signed Document.

The independently pinned cryptography bundle adds nine Signed Document profiles, 22 cases, and 62
evaluations: 12 complete and 50 rejected at their first normative semantic stage.

The independently pinned Admission bundle adds 19 digest-protected artifacts, 5 cases, and 30 Admission
evaluations: 12 complete and 18 rejected. It is pinned to artifact digest
`sha256:39971bfafb68ef6c18f9026220cccc4f023fd4d5c8074f8ff0276cb1129cd0a0` and binds the unchanged
cryptography digest `sha256:5eade516e4bc5dcf04477727ebcccd11f33348b2d9135fb6fe0365c6e6cc2ea3`.

## What the runner checks

`SchemaCatalog` and `ConformanceRunner` provide the current conformance layer:

- strict Jackson parsing rejects duplicate object members and trailing data;
- every schema declares and is compiled as JSON Schema Draft 2020-12;
- all `$ref` resolution uses an in-memory registry of the 22 packaged schemas;
- the validator does not enable remote schema fetching;
- JSON Schema format assertions are enabled;
- each manifest entry is checked against its named schema and expected validity;
- `SignedDocumentCodec` is exercised against all 62 cryptography evaluations, including strict
  Ed25519 point/scalar encodings, exact timestamps, Registry validity, JCS bytes, and hashes;
- `AdmissionService.admitFirst` is exercised with `AdmissionCurrentKeyResolver`, while
  `verifyHistoricalAdmission` is exercised with the historical `KeyResolver`, against all 30
  Admission evaluations with exact totals of 12 complete and 18 rejected;
- first admission verifies before log access, invokes trusted context only after authoritative
  absence, and revalidates the authenticated append return value;
- historical replay reruns all six stages with retained Registry history, requires a record, and
  performs no append;
- Admission Log authentication, append integrity, unavailable or indeterminate service outcomes,
  record binding, exclusive key intervals, and Event self-anchoring all fail closed at stage
  `admission` with wire code `AUTH_INVALID_SIGNATURE`;
- the source tree, compiled classpath, built JAR, and installed Maven consumer
  are exercised independently.

For Signed Document stage 4, the SDK tests the complete Registry-evidence path:

- the completeness gate rejects null snapshots, `PARTIAL`, `UNSPECIFIED`, unavailable evidence,
  and empty Registry bytes;
- strict Registry JSON parsing rejects invalid UTF-8, byte-order marks, duplicate members, and
  trailing data, then checks the exact root and binding shapes;
- every complete Registry identifier, including identifiers in unrelated bindings, is validated
  before key selection;
- every binding is validated, including unrelated bindings, with canonical 32-byte Ed25519 keys
  and strict non-identity, on-curve, prime-order point checks;
- global indexes enforce immutable key-ID bindings, unique public-key ownership, and no aliases
  for a Principal/algorithm/public-key tuple;
- complete retained history is checked for contiguous sequence numbers, semantically equivalent
  duplicate sequence records (equal RFC 3339 instants may use different text, with the first text
  preserved), append order, immutable `validFrom`, and monotonic `validUntil` and `revokedAt`
  restrictions;
- fixtures with more than 64 bindings and more than 64 history records confirm that fixture-only
  limits are not imposed by the runtime;
- key selection happens only after the complete scan, and codec-produced evidence retains the
  Registry `organizationId`.

Those checks validate the evidence supplied to the codec. The deployment adapter remains
responsible for establishing trust, Organization scope, the applicability or currency of the
authoritative coherent revision, completeness, and historical coverage before asserting
`ORGANIZATION_WIDE`. The bytes carried by `KeyRegistrySnapshot` are a Java-SDK-local evidence
representation, not a standardized Registry snapshot wire artifact. This coverage therefore does
not claim complete runtime protocol conformance.

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
58/58 conformance vectors passed
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
and compiles a fresh Maven project that exercises the Registry snapshot public API, verifies
packaged resources and the `19/5/30` Admission bundle, runs all 58 vectors, admits a Command through
the public current-Registry API, and decodes a schema-valid frame.

## Deliberate limits

The Java SDK claims **schema-and-vector conformance only**. A `58/58` schema result plus all 62
cryptography evaluations and all 30 Admission evaluations does not establish complete
MissionWeaveProtocol conformance.

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
