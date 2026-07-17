# MissionWeaveProtocol Java SDK

The MissionWeaveProtocol Java SDK is the official Java 21 implementation of the
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
schema and conformance bundle.

This repository is under active development. It provides a reproducible Maven
build and ships the pinned MissionWeaveProtocol 0.1 schema and conformance
bundle. Validation, codec, and signing APIs are added in subsequent pull
requests.

The packaged resources are pinned by [`PROTOCOL_PIN.json`](PROTOCOL_PIN.json) to
protocol commit `5821df8f0c07893f193af1908235888a0154fb6e`. The build verifies
their file counts and SHA-256 tree digests before it succeeds.

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
