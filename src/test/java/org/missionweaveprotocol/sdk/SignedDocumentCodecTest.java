package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignedDocumentCodecTest {
  @TempDir Path temporaryDirectory;

  @Test
  void verifiesTheGoldenCommandThroughThePublicInterface() throws Exception {
    byte[] received = resource("cryptography/vectors/signed-documents/valid/command.json");
    byte[] registry = resource("cryptography/keys/registry-valid.json");
    byte[] expectedSigningBytes =
        resource("cryptography/vectors/canonicalization/command.signing.jcs");
    String expectedKeyId = "urn:missionweaveprotocol:key:crypto-vector-rfc8032-1";
    Principal expectedPrincipal =
        new Principal("agent", "urn:missionweaveprotocol:agent:crypto-vector-coordinator");
    AtomicInteger resolutionCalls = new AtomicInteger();
    KeyResolver resolver =
        request -> {
          resolutionCalls.incrementAndGet();
          assertEquals(SignedDocumentKind.COMMAND, request.kind());
          assertEquals(expectedKeyId, request.keyId());
          assertEquals(expectedPrincipal, request.expectedPrincipal());
          assertFalse(request.servicePrincipalRequired());
          assertEquals("2026-07-15T00:00:00Z", request.protectedTime());
          assertEquals(ExactInstant.parse("2026-07-15T00:00:00Z"), request.protectedInstant());
          return KeyRegistrySnapshot.organizationWide(registry);
        };

    VerifiedSignedDocument verified =
        new SignedDocumentCodec().verify(SignedDocumentKind.COMMAND, received, resolver);

    assertEquals(SignedDocumentKind.COMMAND, verified.kind());
    assertArrayEquals(received, verified.receivedBytes());
    assertArrayEquals(expectedSigningBytes, verified.signingBytes());
    assertEquals(
        "sha256:6655c5d67ae3ecc19a4ed04bda7f1372aeaafc7adf939a77715de96ef2100695",
        verified.signingHash());
    assertEquals(
        "sha256:1d17d0bd5379e554d48d14a6b328671f12860c6c3278bc1e7ca4e1163a74353f",
        verified.canonicalHash());
    assertArrayEquals(
        CanonicalJson.canonicalize(StrictJson.parse(received)), verified.canonicalBytes());
    assertEquals("2026-07-15T00:00:00Z", verified.protectedTime());
    assertEquals("", verified.protectedInstant().fractionalDigits());
    assertEquals(1, resolutionCalls.get());
    assertEquals(
        "urn:missionweaveprotocol:organization:acme", verified.resolvedKey().organizationId());
    assertEquals(expectedKeyId, verified.resolvedKey().keyId());
    assertEquals(expectedPrincipal, verified.resolvedPrincipal());
    assertEquals("Ed25519", verified.signature().algorithm());
    assertEquals(expectedKeyId, verified.signature().keyId());
    assertEquals("2026-07-15T00:00:00Z", verified.signature().createdAt());
    assertEquals(64, verified.signature().bytes().length);

    ObjectNode callerCopy = verified.document();
    callerCopy.put("kind", "tampered");
    assertEquals("mission.submit_for_approval", verified.document().path("kind").textValue());
  }

  @Test
  void signsTheGoldenCommandAndCopiesTheProtectedTimeExactly() throws Exception {
    ObjectNode expected =
        (ObjectNode)
            StrictJson.parse(resource("cryptography/vectors/signed-documents/valid/command.json"));
    ObjectNode unsigned = expected.deepCopy();
    unsigned.remove("signature");
    JsonNode fixture = StrictJson.parse(resource("cryptography/keys/signing-coordinator.json"));
    String seed = fixture.path("seed").textValue();
    SigningKey signingKey =
        new SigningKey() {
          @Override
          public String keyId() {
            return fixture.path("keyId").textValue();
          }

          @Override
          public byte[] sign(byte[] signingBytes) {
            return Base64Url.decode(Ed25519.sign(signingBytes, seed));
          }
        };

    ObjectNode signed =
        new SignedDocumentCodec().sign(SignedDocumentKind.COMMAND, unsigned, signingKey);

    assertEquals(expected, signed);
    assertEquals(
        unsigned.path("issuedAt").textValue(),
        signed.path("signature").path("createdAt").textValue());
    assertFalse(unsigned.has("signature"));
  }

  @Test
  void snapshotsReceivedBytesBeforeCallingTheExternalResolver() throws Exception {
    byte[] original = resource("cryptography/vectors/signed-documents/valid/command.json");
    byte[] registry = resource("cryptography/keys/registry-valid.json");
    byte[] callerBytes = original.clone();
    KeyResolver resolver =
        request -> {
          Arrays.fill(callerBytes, (byte) 0);
          return KeyRegistrySnapshot.organizationWide(registry);
        };

    VerifiedSignedDocument verified =
        new SignedDocumentCodec().verify(SignedDocumentKind.COMMAND, callerBytes, resolver);

    assertFalse(Arrays.equals(original, callerBytes));
    assertArrayEquals(original, verified.receivedBytes());
  }

  @Test
  void matchesAllSignedDocumentCryptographyEvaluations() throws Exception {
    JsonNode manifest = StrictJson.parse(resource("cryptography/manifest.json"));
    FixtureSchemas fixtureSchemas = loadFixtureSchemas(manifest.path("fixtureSchemas"));
    SignedDocumentCodec codec = new SignedDocumentCodec();
    int evaluations = 0;
    int completed = 0;
    int rejected = 0;
    int canonicalizationEvaluations = 0;

    for (JsonNode testCase : manifest.path("cases")) {
      if (testCase.path("kind").textValue().equals("canonicalization")) {
        for (JsonNode evaluation : testCase.path("evaluations")) {
          evaluations++;
          byte[] canonical =
              CanonicalJson.canonicalize(resource(evaluation.path("input").textValue()));
          assertArrayEquals(
              resource(evaluation.path("expectedJcs").textValue()),
              canonical,
              testCase.path("id").textValue());
          assertEquals(
              evaluation.path("sha256").textValue(),
              "sha256:" + CanonicalJson.sha256Hex(canonical),
              testCase.path("id").textValue());
          completed++;
          canonicalizationEvaluations++;
        }
        continue;
      }
      for (JsonNode evaluation : testCase.path("evaluations")) {
        evaluations++;
        SignedDocumentKind kind = kind(evaluation.path("profileId").textValue());
        byte[] document = resource(evaluation.path("document").textValue());
        byte[] registryFixture = resource(evaluation.path("registry").textValue());
        fixtureSchemas.catalog().validate(fixtureSchemas.registrySchema(), registryFixture);
        KeyResolver resolver = request -> KeyRegistrySnapshot.organizationWide(registryFixture);
        JsonNode signingKeyFixture = null;
        if (evaluation.has("signingKey")) {
          byte[] signingKeyBytes = resource(evaluation.path("signingKey").textValue());
          fixtureSchemas.catalog().validate(fixtureSchemas.signingKeySchema(), signingKeyBytes);
          signingKeyFixture = StrictJson.parse(signingKeyBytes);
        }
        JsonNode expected = evaluation.path("expect");
        if (expected.path("stage").textValue().equals("complete")) {
          VerifiedSignedDocument verified = codec.verify(kind, document, resolver);
          JsonNode evidence = expected.path("verified");
          assertEquals(evidence.path("keyId").textValue(), verified.resolvedKey().keyId());
          assertEquals(
              new Principal(
                  evidence.path("principal").path("type").textValue(),
                  evidence.path("principal").path("id").textValue()),
              verified.resolvedPrincipal());
          assertEquals(evidence.path("protectedTime").textValue(), verified.protectedTime());
          assertArrayEquals(
              resource(evidence.path("signingBytes").textValue()), verified.signingBytes());
          assertEquals(evidence.path("signingHash").textValue(), verified.signingHash());
          assertEquals(evidence.path("signature").textValue(), verified.signature().value());
          assertEquals(evidence.path("signedDocumentHash").textValue(), verified.canonicalHash());
          assertSigningReproducesDocument(
              codec,
              kind,
              Objects.requireNonNull(signingKeyFixture, "complete evaluation signing key"),
              verified.document());
          completed++;
        } else {
          SignedDocumentVerificationException error =
              assertThrows(
                  SignedDocumentVerificationException.class,
                  () -> codec.verify(kind, document, resolver),
                  testCase.path("id").textValue());
          assertEquals(expected.path("stage").textValue(), error.diagnostic().stage().id());
          assertEquals(expected.path("wireCode").textValue(), error.wireCode());
          assertEquals(
              "Signed Document verification failed: " + expected.path("wireCode").textValue(),
              error.getMessage());
          assertNull(error.getCause());
          rejected++;
        }
      }
    }

    assertEquals(58, evaluations);
    assertEquals(12, completed);
    assertEquals(46, rejected);
    assertEquals(1, canonicalizationEvaluations);
  }

  @Test
  void rejectsNonAsciiKeyIdentifiersDuringSchemaValidation() throws Exception {
    byte[] registry = resource("cryptography/keys/registry-valid.json");
    String received =
        new String(
                resource("cryptography/vectors/signed-documents/valid/command.json"),
                StandardCharsets.UTF_8)
            .replace(
                "urn:missionweaveprotocol:key:crypto-vector-rfc8032-1",
                "urn:missionweaveprotocol:key:crypto-vector-\\uD800");
    AtomicBoolean resolved = new AtomicBoolean();
    KeyResolver resolver =
        request -> {
          resolved.set(true);
          return KeyRegistrySnapshot.organizationWide(registry);
        };

    SignedDocumentVerificationException error =
        assertThrows(
            SignedDocumentVerificationException.class,
            () ->
                new SignedDocumentCodec()
                    .verify(
                        SignedDocumentKind.COMMAND,
                        received.getBytes(StandardCharsets.UTF_8),
                        resolver));

    assertEquals(VerificationStage.SCHEMA, error.diagnostic().stage());
    assertEquals("SCHEMA_VALIDATION_FAILED", error.wireCode());
    assertFalse(resolved.get());
  }

  @Test
  void signingRejectsExistingEnvelopesNonJsonValuesAndNonUtcProtectedTime() throws Exception {
    ObjectNode valid =
        (ObjectNode)
            StrictJson.parse(resource("cryptography/vectors/signed-documents/valid/command.json"));
    SigningKey unused =
        new SigningKey() {
          @Override
          public String keyId() {
            return "urn:missionweaveprotocol:key:unused";
          }

          @Override
          public byte[] sign(byte[] signingBytes) {
            throw new AssertionError("invalid input must be rejected before signing");
          }
        };
    SignedDocumentCodec codec = new SignedDocumentCodec();
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.sign(SignedDocumentKind.COMMAND, valid, unused));

    ObjectNode nonJson = valid.deepCopy();
    nonJson.remove("signature");
    nonJson.putPOJO("nonJson", new Object());
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.sign(SignedDocumentKind.COMMAND, nonJson, unused));

    AtomicBoolean invalidKeySignerCalled = new AtomicBoolean();
    SigningKey invalidKey =
        new SigningKey() {
          @Override
          public String keyId() {
            return "urn:missionweaveprotocol:key:invalid-\uD800";
          }

          @Override
          public byte[] sign(byte[] signingBytes) {
            invalidKeySignerCalled.set(true);
            return new byte[64];
          }
        };
    ObjectNode invalidKeyDocument = valid.deepCopy();
    invalidKeyDocument.remove("signature");
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.sign(SignedDocumentKind.COMMAND, invalidKeyDocument, invalidKey));
    assertFalse(invalidKeySignerCalled.get());

    ObjectNode offset = valid.deepCopy();
    offset.remove("signature");
    offset.put("issuedAt", "2026-07-15T08:00:00+08:00");
    assertThrows(
        IllegalArgumentException.class,
        () -> codec.sign(SignedDocumentKind.COMMAND, offset, unused));
  }

  private static void assertSigningReproducesDocument(
      SignedDocumentCodec codec, SignedDocumentKind kind, JsonNode fixture, ObjectNode expected)
      throws IOException {
    SigningKey signingKey =
        new SigningKey() {
          @Override
          public String keyId() {
            return fixture.path("keyId").textValue();
          }

          @Override
          public byte[] sign(byte[] signingBytes) {
            return Base64Url.decode(Ed25519.sign(signingBytes, fixture.path("seed").textValue()));
          }
        };
    ObjectNode unsigned = expected.deepCopy();
    unsigned.remove("signature");
    assertEquals(expected, codec.sign(kind, unsigned, signingKey));
  }

  private FixtureSchemas loadFixtureSchemas(JsonNode declarations) throws IOException {
    String registryPath = requiredManifestText(declarations, "registry");
    String signingKeyPath = requiredManifestText(declarations, "signingKey");
    String registrySchema = Path.of(registryPath).getFileName().toString();
    String signingKeySchema = Path.of(signingKeyPath).getFileName().toString();
    Path schemaDirectory = Files.createDirectories(temporaryDirectory.resolve("fixtures/schemas"));
    Files.write(schemaDirectory.resolve(registrySchema), resource(registryPath));
    Files.write(schemaDirectory.resolve(signingKeySchema), resource(signingKeyPath));
    SchemaCatalog catalog = SchemaCatalog.from(temporaryDirectory.resolve("fixtures"));
    return new FixtureSchemas(catalog, registrySchema, signingKeySchema);
  }

  private static String requiredManifestText(JsonNode object, String field) {
    JsonNode value = object.path(field);
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException("Manifest fixture schema is missing: " + field);
    }
    return value.textValue();
  }

  private static SignedDocumentKind kind(String id) {
    return Arrays.stream(SignedDocumentKind.values())
        .filter(kind -> kind.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown Signed Document kind: " + id));
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream input =
        SignedDocumentCodecTest.class.getClassLoader().getResourceAsStream(name)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + name);
      }
      return input.readAllBytes();
    }
  }

  private record FixtureSchemas(
      SchemaCatalog catalog, String registrySchema, String signingKeySchema) {}
}
