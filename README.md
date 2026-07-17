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
| Protocol commit | [`00964ea9064cbf1f0eca8af21a0c57367ee14752`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/00964ea9064cbf1f0eca8af21a0c57367ee14752) |
| JSON Schemas | 21 |
| Conformance vectors | 43: 22 valid and 21 invalid |

The JAR contains the complete offline bundle. [PROTOCOL_PIN.json](PROTOCOL_PIN.json)
records its source, file counts, and SHA-256 tree digests.

## Capabilities

- `StrictJson` rejects duplicate object members, invalid UTF-8, and trailing
  data at the trust boundary.
- `SchemaCatalog` compiles the 21 Draft 2020-12 schemas into a fully offline
  registry with format assertions enabled.
- `FrameCodec` strictly decodes, validates, and canonically encodes generic
  MissionWeaveProtocol WebSocket frames; it does not create a connection.
- `CanonicalJson` provides RFC 8785 JCS and SHA-256 identifiers.
- `Ed25519`, `Base64Url`, and `DocumentSignatures` provide JDK Ed25519 signing,
  unpadded base64url, and top-level `signature` omission.
- `ConformanceRunner` and `ConformanceCli` run all 43 packaged vectors.

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

The packaged result is `43/43 conformance vectors passed`.

## Documentation

- [Usage and API guide](docs/usage.md)
- [Compatibility and conformance scope](docs/conformance.md)

## Security and behavioral boundaries

- Schema validation checks document shape and formats. It does not grant
  authority, authenticate an Agent, or prove that an action is allowed.
- Signature helpers do not provide key trust, storage, discovery, revocation,
  timestamp policy, replay prevention, or session and membership fencing.
- `FrameCodec` is a serializer, not a transport, coordinator, worker scheduler,
  durable store, retry engine, or state-machine implementation.
- A `43/43` result demonstrates schema-and-vector conformance only; it does not
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
