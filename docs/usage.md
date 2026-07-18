# MissionWeaveProtocol Java SDK usage

The SDK targets Java 21 and MissionWeaveProtocol 0.1. Its current Maven
coordinates are:

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The protocol resources are packaged in the JAR and resolved offline. No schema
download is required at runtime.

## Strict JSON and schema validation

Parse untrusted JSON bytes with `StrictJson` before using individual fields.
The parser accepts exactly one JSON value and rejects duplicate object members,
invalid UTF-8, and trailing data.

```java
import java.nio.file.Files;
import java.nio.file.Path;
import org.missionweaveprotocol.sdk.SchemaCatalog;
import org.missionweaveprotocol.sdk.StrictJson;

byte[] commandBytes = Files.readAllBytes(Path.of("command.json"));
var command = StrictJson.parse(commandBytes);

SchemaCatalog catalog = SchemaCatalog.packaged();
catalog.validate("command.schema.json", command);
```

`SchemaCatalog.packaged()` compiles the 21 bundled Draft 2020-12 schemas once
for the catalog instance. Relative `$ref` values resolve only from the bundled
schema registry, and format assertions such as `date-time` are enabled.

To validate a checked-out protocol bundle instead:

```java
SchemaCatalog catalog = SchemaCatalog.from(Path.of("../missionweaveprotocol"));
catalog.validate("schemas/command.schema.json", command);
```

Unknown schema names raise `IllegalArgumentException`. A structurally invalid
document raises `SchemaValidationException`, whose `errors()` method contains
stable validation details. Malformed JSON raises `IOException`.

## Canonical JSON and hashes

`CanonicalJson` implements RFC 8785 JSON Canonicalization Scheme (JCS) and
SHA-256 helpers.

```java
import org.missionweaveprotocol.sdk.CanonicalJson;

byte[] canonical = CanonicalJson.canonicalize(command);
String contentId = CanonicalJson.canonicalHash(command);
```

`canonicalHash` returns a lowercase identifier such as `sha256:0123...`.

## Ed25519 document signatures

`Ed25519` uses the Java 21 JDK provider. Raw 32-byte private seeds, raw 32-byte
public keys, and 64-byte signatures are represented as unpadded base64url.

`DocumentSignatures` removes only the top-level `signature` member before JCS
canonicalization. Nested fields named `signature` remain part of the signed
payload.

```java
import org.missionweaveprotocol.sdk.DocumentSignatures;
import org.missionweaveprotocol.sdk.Ed25519;

Ed25519.EncodedKeyPair keys = Ed25519.generateKeyPair();
String signature = DocumentSignatures.sign(commandBytes, keys.privateKey());

if (!DocumentSignatures.verify(commandBytes, signature, keys.publicKey())) {
  throw new IllegalStateException("Signature verification failed");
}
```

These helpers do not decide whether a key is trusted, current, authorized, or
revoked. Applications remain responsible for key discovery, storage, policy,
replay prevention, fencing, and timestamp checks.

## Generic WebSocket frames

`FrameCodec` validates generic JSON objects against
`websocket-frame.schema.json`. It deliberately does not create a WebSocket
connection or implement session management.

```java
import java.nio.charset.StandardCharsets;
import org.missionweaveprotocol.sdk.FrameCodec;

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
byte[] canonicalFrame = codec.encode(frame);
```

`decode` performs strict parsing and schema validation. `encode` validates the
provided tree and emits JCS bytes.

## Conformance runner

Run the packaged manifest from Java:

```java
import org.missionweaveprotocol.sdk.ConformanceReport;
import org.missionweaveprotocol.sdk.ConformanceRunner;

ConformanceReport report = ConformanceRunner.runPackaged();
System.out.println(report.summary());
if (!report.passed()) {
  throw new IllegalStateException("Conformance vectors failed");
}
```

Run the CLI against packaged resources:

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java
```

Or point it at a protocol repository or release-bundle root:

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

The expected bundled result is `52/52 conformance vectors passed`.

## Runnable examples

The examples are compiled as test sources and exercised by the build.

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

## Security boundary

- Schema validation checks structure and formats; it does not grant authority
  or prove that an action is allowed.
- Signature verification proves possession of a private key for exact bytes;
  it does not establish identity or authorization without a trusted registry.
- `FrameCodec` is a serializer, not a transport, coordinator, worker scheduler,
  durable store, retry engine, or protocol state machine.
- Passing all bundled vectors demonstrates schema-and-vector conformance only,
  not complete protocol behavior, interoperability, or production readiness.
