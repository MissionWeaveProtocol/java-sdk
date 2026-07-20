# MissionWeaveProtocol Java SDK conformance

## Compatibility statement

SDK version `0.1.0-SNAPSHOT` targets MissionWeaveProtocol `0.1` and the wire
namespace `missionweaveprotocol`. The vendored bundle is pinned to protocol
commit
[`33e47ad8a7318f942de77fb72dbb054d85881b40`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/33e47ad8a7318f942de77fb72dbb054d85881b40).

`PROTOCOL_PIN.json` records the authoritative source, file counts, and SHA-256
tree digests:

| Artifact | JSON files | SHA-256 |
| --- | ---: | --- |
| `schemas` | 21 | `de90adb6a84995ce6e7e35f20c58f74293546ad2aca61796429c8b1d8d269c42` |
| `conformance` | 57 | `fc7d6b2005b4cdebcb9d47efd0a3ce991fea111776c4271beaf8945e11b5d7df` |
| complete bundle | 78 | `eed30aeb0a6d39575b6ab2f3121de27cef34d27dd9659ee4e5a7204ec5deeea7` |

The 57 conformance files are one manifest plus 56 vectors: 26 expected-valid
documents and 30 expected-invalid documents.

The independently pinned cryptography bundle adds nine Signed Document profiles, 22 cases, and 58
evaluations: 12 complete and 46 rejected at their first normative semantic stage.

## What the runner checks

`SchemaCatalog` and `ConformanceRunner` provide the current conformance layer:

- strict Jackson parsing rejects duplicate object members and trailing data;
- every schema declares and is compiled as JSON Schema Draft 2020-12;
- all `$ref` resolution uses an in-memory registry of the 21 packaged schemas;
- the validator does not enable remote schema fetching;
- JSON Schema format assertions are enabled;
- each manifest entry is checked against its named schema and expected validity;
- `SignedDocumentCodec` is exercised against all 58 cryptography evaluations, including strict
  Ed25519 point/scalar encodings, exact timestamps, Registry validity, JCS bytes, and hashes;
- the source tree, compiled classpath, built JAR, and installed Maven consumer
  are exercised independently.

For Signed Document stage 4, the SDK tests the complete Registry-evidence path:

- the completeness gate rejects null snapshots, `PARTIAL`, `UNSPECIFIED`, unavailable evidence,
  and empty Registry bytes;
- strict Registry JSON parsing rejects invalid UTF-8, byte-order marks, duplicate members, and
  trailing data, then checks the exact root and binding shapes and absolute identifiers;
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
56/56 conformance vectors passed
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
packaged resources, runs all 56 vectors, and decodes a schema-valid frame.

## Deliberate limits

The Java SDK claims **schema-and-vector conformance only**. A `56/56` schema result plus all 58
cryptography evaluations does
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
