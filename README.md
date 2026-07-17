# MissionWeaveProtocol Java SDK

The MissionWeaveProtocol Java SDK is the official Java 21 implementation of the
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
schema and conformance bundle.

This repository is under active development. The current foundation provides a
reproducible Maven build, formatting, tests, packaging, canonical naming checks,
and protected GitHub CI. Protocol resources and validation APIs are added in
subsequent pull requests.

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
