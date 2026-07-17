# MissionWeaveProtocol Java SDK

The MissionWeaveProtocol Java SDK is the official Java 21 implementation of the
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
schema and conformance bundle.

This repository is under active development. It provides strict JSON parsing,
offline Draft 2020-12 schema validation, RFC 8785 canonicalization, SHA-256 and
Ed25519 helpers, a generic validating WebSocket frame codec, and the complete
pinned MissionWeaveProtocol 0.1 schema-and-vector bundle.

The packaged resources are pinned by [`PROTOCOL_PIN.json`](PROTOCOL_PIN.json) to
protocol commit `5821df8f0c07893f193af1908235888a0154fb6e`. The build verifies
their file counts and SHA-256 tree digests before it succeeds.

The current conformance claim is deliberately limited to the 21 packaged JSON
Schemas and 43 implementation-neutral vectors. It is not a claim of complete
MissionWeaveProtocol runtime conformance.

## Requirements

- Java 21
- Docker, when a local JDK is unavailable

The repository includes Maven Wrapper scripts pinned to Maven 3.9.

## Verify

```bash
./mvnw -B verify
```

Without a local JDK or Maven installation:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  mvn -B -ntp verify
```

## Coordinates

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## License

Licensed under [Apache-2.0](LICENSE).
