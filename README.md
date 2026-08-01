**English** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)

# MissionWeaveProtocol Java SDK

The official Java 21 SDK for validating, canonicalizing, signing, and testing
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1 data.

> Conformance claim: **schema-and-vector conformance only**. The SDK does not
> claim complete runtime protocol conformance.

## Requirements and dependency

- Java 21
- Maven 3.9 or the included Maven Wrapper

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Protocol compatibility

| Item | Pinned value |
| --- | --- |
| SDK coordinates | `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT` |
| Protocol version | `0.1` |
| Wire namespace | `missionweaveprotocol` |
| Protocol commit | [`f7e70a72c76bbeb5014c186cd820aac2112f0dde`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/f7e70a72c76bbeb5014c186cd820aac2112f0dde) |
| JSON Schemas | 22 |
| Conformance vectors | 58: 27 valid and 31 invalid |
| Cryptography evaluations | [62](cryptography/README.md) |
| Admission evaluations | [30: 12 complete and 18 rejected](admission/README.md) |

The JAR contains the complete offline bundle. [PROTOCOL_PIN.json](PROTOCOL_PIN.json)
records its source, file counts, and SHA-256 tree digests.

## Capabilities

- `StrictJson` rejects duplicate object members, invalid UTF-8, and trailing
  data at the trust boundary.
- `SchemaCatalog` compiles the 22 Draft 2020-12 schemas into a fully offline
  registry with format assertions enabled.
- `FrameCodec` strictly decodes, validates, and canonically encodes generic
  MissionWeaveProtocol WebSocket frames; it does not create a connection.
- `CanonicalJson` provides RFC 8785 JCS and SHA-256 identifiers.
- `Ed25519`, `Base64Url`, and `DocumentSignatures` provide JDK Ed25519 signing,
  unpadded base64url, and top-level `signature` omission.
- `SignedDocumentCodec` applies the complete six-stage Signed Document profile. A `KeyResolver`
  receives a `KeyResolutionRequest` and returns a `KeyRegistrySnapshot` created with
  `KeyRegistrySnapshot.organizationWide(registryBytes)` and containing complete Registry bytes,
  not a selected `ResolvedKey`.
- `ORGANIZATION_WIDE` is a trusted adapter assertion, not a completeness proof. It states that
  those bytes cover one coherent, authoritative Registry revision applicable to the verification
  decision for one Organization-controlled Agent Registry, including all Organization-wide
  bindings and its complete retained validity history. `request.keyId()` is routing context only
  and must never filter the Registry or return a partial projection.
- The codec treats the bytes as untrusted and validates every binding, global no-reuse and
  no-alias invariants, and complete validity history before selecting the key. `PARTIAL`,
  `UNSPECIFIED`, `null`, empty, unavailable, or malformed evidence fails closed at key resolution;
  codec-produced evidence retains `organizationId`.
- `AdmissionService.admitFirst` uses an `AdmissionCurrentKeyResolver` for complete current Registry
  evidence, while `verifyHistoricalAdmission` uses the existing historical `KeyResolver`. Both rerun
  all six stages before authenticated Admission Log decisions; the API uses typed adapters and no
  caller-provided trust, authentication, or integrity booleans.
- `ConformanceRunner` and `ConformanceCli` run all 58 packaged vectors; the Admission suite executes
  all 30 evaluations with 12 complete and 18 rejected.

## Quick start

```java
import java.nio.charset.StandardCharsets;
import org.missionweaveprotocol.sdk.FrameCodec;

public final class QuickStart {
  public static void main(String[] args) throws Exception {
    byte[] incoming = """
        {
          "protocolVersion": "0.1",
          "frameId": "urn:uuid:00000000-0000-4000-8000-000000000010",
          "frameType": "PING",
          "nonce": "cGluZw",
          "sentAt": "2026-07-17T08:00:00Z"
        }
        """.getBytes(StandardCharsets.UTF_8);

    FrameCodec codec = new FrameCodec();
    var frame = codec.decode(incoming);
    byte[] canonical = codec.encode(frame);

    System.out.println(frame.get("frameType").textValue());
    System.out.println(new String(canonical, StandardCharsets.UTF_8));
  }
}
```

For durable signed objects, call `SignedDocumentCodec.sign(kind, unsigned, signingKey)` and
`verify(kind, receivedBytes, keyResolver)`. The codec never infers the kind and returns immutable
verification evidence including the received bytes, the signing bytes and their hash, and the
complete canonical bytes and their hash.

For durable first-admission evidence, call `AdmissionService.admitFirst` with current Registry
evidence, an authenticated append-only `AdmissionLog`, and a `TrustedAdmissionContext`.
`verifyHistoricalAdmission` instead consumes retained historical Registry evidence, requires an
existing validated record, and never appends a missing record.

## Runnable examples

The build compiles and tests all three repository examples:

```bash
./mvnw -q -Dexec.classpathScope=test \
  -Dexec.mainClass=org.missionweaveprotocol.examples.ValidateAndSignExample \
  exec:java

./mvnw -q -Dexec.classpathScope=test \
  -Dexec.mainClass=org.missionweaveprotocol.examples.FrameRoundTripExample \
  exec:java

./mvnw -q -Dexec.classpathScope=test \
  -Dexec.mainClass=org.missionweaveprotocol.examples.RunConformanceExample \
  exec:java
```

## Conformance runner

Run the packaged vectors or a separate protocol bundle:

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java

./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

The packaged result is `58/58 conformance vectors passed`.

## Documentation

- [Usage and API guide](docs/usage.md)
- [Compatibility and conformance scope](docs/conformance.md)

## Security and behavioral boundaries

- Schema validation checks document shape and formats. It does not grant
  authority, authenticate an Agent, or prove that an action is allowed.
- Signature helpers do not provide key trust, storage, discovery, revocation,
  timestamp policy, replay prevention, or session and membership fencing.
- Admission depends on deployment-authenticated service identity, authorized writes, and
  append-only integrity supplied by the adapters; successful SDK validation does not establish
  those deployment properties by itself.
- `FrameCodec` is a serializer, not a transport, coordinator, worker scheduler,
  durable store, retry engine, or state-machine implementation.
- A `58/58` result plus 62 cryptography and 30 Admission evaluations demonstrates bounded
  conformance only; it does not
  establish interoperability, complete behavior, security, or production
  readiness.

## Development

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

Without a local JDK or Maven installation:

```bash
docker run --rm \
  -v missionweaveprotocol-java-m2:/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw -B -ntp verify
```

## License

Apache-2.0. See [LICENSE](LICENSE).
