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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignedDocumentCodecTest {
  @TempDir Path temporaryDirectory;

  @Test
  void verifiesTheGoldenCommandThroughThePublicInterface() throws Exception {
    byte[] received = resource("cryptography/vectors/signed-documents/valid/command.json");
    byte[] expectedSigningBytes =
        resource("cryptography/vectors/canonicalization/command.signing.jcs");
    ResolvedKey resolvedKey =
        new ResolvedKey(
            "urn:missionweaveprotocol:key:crypto-vector-rfc8032-1",
            new Principal("agent", "urn:missionweaveprotocol:agent:crypto-vector-coordinator"),
            "Ed25519",
            "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
            "2026-07-15T08:00:00+08:00",
            "2026-07-16T00:00:00Z",
            null);
    KeyResolver resolver =
        request -> {
          assertEquals(SignedDocumentKind.COMMAND, request.kind());
          assertEquals(resolvedKey.keyId(), request.keyId());
          assertEquals(resolvedKey.principal(), request.expectedPrincipal());
          assertFalse(request.servicePrincipalRequired());
          assertEquals("2026-07-15T00:00:00Z", request.protectedTime());
          return resolvedKey;
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
    assertEquals(resolvedKey, verified.resolvedKey());
    assertEquals(resolvedKey.principal(), verified.resolvedPrincipal());
    assertEquals("Ed25519", verified.signature().algorithm());
    assertEquals(resolvedKey.keyId(), verified.signature().keyId());
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
    byte[] callerBytes = original.clone();
    ResolvedKey resolvedKey =
        new ResolvedKey(
            "urn:missionweaveprotocol:key:crypto-vector-rfc8032-1",
            new Principal("agent", "urn:missionweaveprotocol:agent:crypto-vector-coordinator"),
            "Ed25519",
            "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
            "2026-07-15T08:00:00+08:00",
            "2026-07-16T00:00:00Z",
            null);
    KeyResolver resolver =
        request -> {
          Arrays.fill(callerBytes, (byte) 0);
          return resolvedKey;
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
        KeyResolver resolver = new FixtureKeyResolver(registryFixture);
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
          return new ResolvedKey(
              request.keyId(),
              request.expectedPrincipal(),
              "Ed25519",
              "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
              "2026-07-15T08:00:00+08:00",
              "2026-07-16T00:00:00Z",
              null);
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

  private static final class FixtureKeyResolver implements KeyResolver {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final byte[] registryBytes;

    private FixtureKeyResolver(byte[] registryBytes) {
      this.registryBytes = registryBytes.clone();
    }

    @Override
    public ResolvedKey resolve(KeyResolutionRequest request) {
      try {
        return resolveChecked(request);
      } catch (IOException error) {
        throw new IllegalArgumentException("Registry fixture is not strict JSON", error);
      }
    }

    private ResolvedKey resolveChecked(KeyResolutionRequest request) throws IOException {
      JsonNode registry = StrictJson.parse(registryBytes);
      if (!registry.isObject() || !registry.path("bindings").isArray()) {
        throw new IllegalArgumentException("Registry fixture has invalid structure");
      }

      Map<String, Binding> bindings = new HashMap<>();
      Map<String, String> publicKeyOwners = new HashMap<>();
      Map<String, String> tupleIds = new HashMap<>();
      for (JsonNode rawBinding : registry.path("bindings")) {
        String keyId = text(rawBinding, "keyId");
        Principal principal =
            new Principal(
                text(rawBinding.path("principal"), "type"),
                text(rawBinding.path("principal"), "id"));
        String algorithm = text(rawBinding, "algorithm");
        if (!algorithm.equals("Ed25519")) {
          throw new IllegalArgumentException("Registry key algorithm is not Ed25519");
        }
        String publicKey = text(rawBinding, "publicKey");
        byte[] publicKeyBytes = canonicalBase64(publicKey);
        if (publicKeyBytes.length != 32) {
          throw new IllegalArgumentException("Registry public key is not 32 bytes");
        }
        String validFromText = text(rawBinding, "validFrom");
        ExactInstant validFrom = ExactInstant.parse(validFromText);
        Binding candidate =
            new Binding(
                keyId, principal, algorithm, publicKey, validFromText, validFrom, new TreeMap<>());
        Binding binding = bindings.get(keyId);
        if (binding == null) {
          bindings.put(keyId, candidate);
          binding = candidate;
        } else if (!binding.sameImmutable(candidate)) {
          throw new IllegalArgumentException("Registry reuses a key ID for another binding");
        }

        String owner = keyId + '\u0000' + principal.type() + '\u0000' + principal.id();
        String previousOwner = publicKeyOwners.putIfAbsent(publicKey, owner);
        if (previousOwner != null && !previousOwner.equals(owner)) {
          throw new IllegalArgumentException("Registry reuses a public key");
        }
        String tuple =
            principal.type()
                + '\u0000'
                + principal.id()
                + '\u0000'
                + algorithm
                + '\u0000'
                + publicKey;
        String previousId = tupleIds.putIfAbsent(tuple, keyId);
        if (previousId != null && !previousId.equals(keyId)) {
          throw new IllegalArgumentException("Registry contains a key-ID alias");
        }

        JsonNode history = rawBinding.path("validityHistory");
        if (!history.isArray()) {
          throw new IllegalArgumentException("Registry validityHistory is not an array");
        }
        for (JsonNode rawStatus : history) {
          long sequence = positiveSafeInteger(rawStatus.path("sequence"));
          Status status =
              new Status(
                  sequence,
                  ExactInstant.parse(text(rawStatus, "recordedAt")),
                  optionalText(rawStatus, "validUntil"),
                  optionalInstant(rawStatus, "validUntil"),
                  optionalText(rawStatus, "revokedAt"),
                  optionalInstant(rawStatus, "revokedAt"));
          Status previous = binding.history.putIfAbsent(sequence, status);
          if (previous != null && !previous.equals(status)) {
            throw new IllegalArgumentException("Registry rewrites validity history");
          }
        }
      }

      for (Binding binding : bindings.values()) {
        long expectedSequence = 1;
        ExactInstant previousRecorded = null;
        for (Status status : binding.history.values()) {
          if (status.sequence != expectedSequence++) {
            throw new IllegalArgumentException("Registry validity history is not contiguous");
          }
          if (previousRecorded != null && status.recordedAt.compareTo(previousRecorded) < 0) {
            throw new IllegalArgumentException("Registry validity history is not append ordered");
          }
          previousRecorded = status.recordedAt;
          binding.apply(status);
        }
      }

      Binding selected = bindings.get(request.keyId());
      return selected == null ? null : selected.resolved();
    }

    private static String text(JsonNode object, String field) {
      JsonNode value = object.path(field);
      if (!value.isTextual()) {
        throw new IllegalArgumentException("Registry field is not text: " + field);
      }
      return value.textValue();
    }

    private static String optionalText(JsonNode object, String field) {
      return object.has(field) ? text(object, field) : null;
    }

    private static ExactInstant optionalInstant(JsonNode object, String field) {
      String value = optionalText(object, field);
      return value == null ? null : ExactInstant.parse(value);
    }

    private static long positiveSafeInteger(JsonNode value) {
      if (!value.isIntegralNumber() || !value.canConvertToLong()) {
        throw new IllegalArgumentException("Registry sequence is not an integer");
      }
      long sequence = value.longValue();
      if (sequence < 1 || sequence > MAX_SAFE_INTEGER) {
        throw new IllegalArgumentException("Registry sequence is outside the safe range");
      }
      return sequence;
    }

    private static byte[] canonicalBase64(String text) {
      byte[] bytes = Base64Url.decode(text);
      if (!Base64Url.encode(bytes).equals(text)) {
        throw new IllegalArgumentException("Registry public key is not canonical base64url");
      }
      return bytes;
    }

    private static final class Binding {
      private final String keyId;
      private final Principal principal;
      private final String algorithm;
      private final String publicKey;
      private final String validFromText;
      private final ExactInstant validFrom;
      private final TreeMap<Long, Status> history;
      private String validUntilText;
      private ExactInstant validUntil;
      private String revokedAtText;
      private ExactInstant revokedAt;

      private Binding(
          String keyId,
          Principal principal,
          String algorithm,
          String publicKey,
          String validFromText,
          ExactInstant validFrom,
          TreeMap<Long, Status> history) {
        this.keyId = keyId;
        this.principal = principal;
        this.algorithm = algorithm;
        this.publicKey = publicKey;
        this.validFromText = validFromText;
        this.validFrom = validFrom;
        this.history = history;
      }

      private boolean sameImmutable(Binding other) {
        return principal.equals(other.principal)
            && algorithm.equals(other.algorithm)
            && publicKey.equals(other.publicKey)
            && validFrom.equals(other.validFrom);
      }

      private void apply(Status status) {
        if (status.validUntil != null) {
          if (validUntil != null && status.validUntil.compareTo(validUntil) > 0) {
            throw new IllegalArgumentException("Registry moves validUntil later");
          }
          validUntil = status.validUntil;
          validUntilText = status.validUntilText;
        }
        if (status.revokedAt != null) {
          if (revokedAt != null && status.revokedAt.compareTo(revokedAt) > 0) {
            throw new IllegalArgumentException("Registry moves revokedAt later");
          }
          revokedAt = status.revokedAt;
          revokedAtText = status.revokedAtText;
        }
      }

      private ResolvedKey resolved() {
        return new ResolvedKey(
            keyId, principal, algorithm, publicKey, validFromText, validUntilText, revokedAtText);
      }
    }

    private record Status(
        long sequence,
        ExactInstant recordedAt,
        String validUntilText,
        ExactInstant validUntil,
        String revokedAtText,
        ExactInstant revokedAt) {
      private Status {
        Objects.requireNonNull(recordedAt, "recordedAt");
      }
    }
  }
}
