package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Deep codec for the six-stage MissionWeaveProtocol 0.1 Signed Document profile. */
public final class SignedDocumentCodec {
  private final SchemaCatalog schemas;

  /** Construct a codec using the normative schemas packaged with the SDK. */
  public SignedDocumentCodec() throws IOException {
    this(SchemaCatalog.packaged());
  }

  /** Construct a codec over an explicit normative schema catalog. */
  public SignedDocumentCodec(SchemaCatalog schemas) {
    this.schemas = Objects.requireNonNull(schemas, "schemas");
  }

  /** Validate, canonically sign, and return one explicitly selected Signed Document kind. */
  public ObjectNode sign(SignedDocumentKind kind, JsonNode unsignedDocument, SigningKey signingKey)
      throws IOException {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(unsignedDocument, "unsignedDocument");
    Objects.requireNonNull(signingKey, "signingKey");
    if (!(unsignedDocument instanceof ObjectNode object)) {
      throw new IllegalArgumentException("Unsigned Signed Document must be a JSON object");
    }
    if (object.has("signature")) {
      throw new IllegalArgumentException("Unsigned Signed Document already has a signature");
    }
    requireJsonValue(object);

    JsonNode protectedNode = object.at(kind.protectedTimePointer());
    if (!protectedNode.isTextual()) {
      throw new IllegalArgumentException("Protected signed time is not a string");
    }
    String protectedTime = protectedNode.textValue();
    ExactInstant.parse(protectedTime);
    if (!protectedTime.endsWith("Z")) {
      throw new IllegalArgumentException("Protected signed time must use uppercase Z");
    }

    String keyId = Objects.requireNonNull(signingKey.keyId(), "signingKey.keyId()");
    ObjectNode signed = object.deepCopy();
    ObjectNode envelope = signed.putObject("signature");
    envelope.put("algorithm", "Ed25519");
    envelope.put("keyId", keyId);
    envelope.put("createdAt", protectedTime);
    envelope.put("value", Base64Url.encode(new byte[64]));
    schemas.validate(kind.schemaName(), signed);
    CanonicalJson.canonicalize(signed);

    byte[] signingBytes = CanonicalJson.canonicalize(object);
    byte[] signature =
        Objects.requireNonNull(signingKey.sign(signingBytes.clone()), "SigningKey returned null")
            .clone();
    validateSignatureEncoding(signature);
    envelope.put("value", Base64Url.encode(signature));
    return signed;
  }

  /** Verify one explicitly selected Signed Document kind from its received UTF-8 bytes. */
  public VerifiedSignedDocument verify(
      SignedDocumentKind kind, byte[] receivedBytes, KeyResolver resolver)
      throws SignedDocumentVerificationException {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(receivedBytes, "receivedBytes");
    Objects.requireNonNull(resolver, "resolver");
    byte[] received = receivedBytes.clone();

    JsonNode parsed = parse(received);
    validateSchema(kind, parsed);
    ObjectNode document = (ObjectNode) parsed;
    Envelope envelope = validateEnvelope(kind, document);
    ResolvedKey resolved = resolveKey(kind, envelope, resolver);
    byte[] signingBytes = canonicalize(document, true);
    byte[] canonicalBytes = canonicalize(document, false);
    verifySignature(signingBytes, envelope, resolved);

    return new VerifiedSignedDocument(
        kind,
        document,
        received,
        signingBytes,
        hash(signingBytes),
        canonicalBytes,
        hash(canonicalBytes),
        envelope.protectedTime,
        envelope.protectedInstant,
        envelope.signature,
        resolved);
  }

  private JsonNode parse(byte[] receivedBytes) throws SignedDocumentVerificationException {
    try {
      return StrictJson.parse(receivedBytes);
    } catch (IOException | RuntimeException error) {
      throw failure(VerificationStage.PARSE, message(error));
    }
  }

  private void validateSchema(SignedDocumentKind kind, JsonNode document)
      throws SignedDocumentVerificationException {
    try {
      schemas.validate(kind.schemaName(), document);
      if (!(document instanceof ObjectNode)) {
        throw new IllegalArgumentException("Signed Document is not a JSON object");
      }
      JsonNode protectedTime = document.at(kind.protectedTimePointer());
      if (!protectedTime.isTextual()) {
        throw new IllegalArgumentException("protected signed time is not a string");
      }
      ExactInstant.parse(protectedTime.textValue());
      JsonNode createdAt = document.path("signature").path("createdAt");
      if (!createdAt.isTextual()) {
        throw new IllegalArgumentException("signature.createdAt is not a string");
      }
      ExactInstant.parse(createdAt.textValue());
    } catch (IllegalArgumentException error) {
      throw failure(VerificationStage.SCHEMA, message(error));
    }
  }

  private Envelope validateEnvelope(SignedDocumentKind kind, ObjectNode document)
      throws SignedDocumentVerificationException {
    try {
      ObjectNode signature = (ObjectNode) document.path("signature");
      String protectedTime = document.at(kind.protectedTimePointer()).textValue();
      String createdAt = signature.path("createdAt").textValue();
      ExactInstant protectedInstant = ExactInstant.parse(protectedTime);
      ExactInstant.parse(createdAt);
      if (!protectedTime.endsWith("Z") || !createdAt.endsWith("Z")) {
        throw new IllegalArgumentException(
            "protected time and signature.createdAt must use uppercase Z");
      }
      if (!protectedTime.equals(createdAt)) {
        throw new IllegalArgumentException(
            "protected time and signature.createdAt are not byte-equal");
      }

      Principal expected = expectedPrincipal(kind, document);
      String value = signature.path("value").textValue();
      byte[] signatureBytes = canonicalBase64Url(value, "signature.value");
      if (signatureBytes.length != 64) {
        throw new IllegalArgumentException("signature.value does not decode to 64 bytes");
      }
      validateSignatureEncoding(signatureBytes);
      SignatureMaterial material =
          new SignatureMaterial(
              signature.path("algorithm").textValue(),
              signature.path("keyId").textValue(),
              createdAt,
              value,
              signatureBytes);
      return new Envelope(protectedTime, protectedInstant, expected, material);
    } catch (RuntimeException error) {
      throw failure(VerificationStage.SIGNATURE_ENVELOPE, message(error));
    }
  }

  private ResolvedKey resolveKey(SignedDocumentKind kind, Envelope envelope, KeyResolver resolver)
      throws SignedDocumentVerificationException {
    try {
      KeyResolutionRequest request =
          new KeyResolutionRequest(
              kind,
              envelope.signature.keyId(),
              envelope.expectedPrincipal,
              kind.servicePrincipalRequired(),
              envelope.protectedTime,
              envelope.protectedInstant);
      KeyRegistrySnapshot snapshot = resolver.resolve(request);
      if (snapshot == null) {
        throw new IllegalArgumentException("Registry resolver returned no evidence");
      }
      if (snapshot.completeness() != KeyRegistryCompleteness.ORGANIZATION_WIDE) {
        throw new IllegalArgumentException("Registry evidence is not Organization-wide complete");
      }
      return AgentRegistryKeyResolution.resolve(snapshot.registryBytes(), request);
    } catch (KeyResolutionException | IOException | RuntimeException error) {
      throw failure(VerificationStage.KEY_RESOLUTION, message(error));
    }
  }

  private byte[] canonicalize(ObjectNode document, boolean omitSignature)
      throws SignedDocumentVerificationException {
    try {
      JsonNode value = document;
      if (omitSignature) {
        ObjectNode unsigned = document.deepCopy();
        unsigned.remove("signature");
        value = unsigned;
      }
      return CanonicalJson.canonicalize(value);
    } catch (IOException | RuntimeException error) {
      throw failure(VerificationStage.CANONICALIZATION, message(error));
    }
  }

  private void verifySignature(byte[] signingBytes, Envelope envelope, ResolvedKey resolved)
      throws SignedDocumentVerificationException {
    try {
      if (!Ed25519.verify(signingBytes, envelope.signature.value(), resolved.publicKey())) {
        throw new IllegalArgumentException("Ed25519 signature does not verify");
      }
    } catch (RuntimeException error) {
      throw failure(VerificationStage.SIGNATURE, message(error));
    }
  }

  private static Principal expectedPrincipal(SignedDocumentKind kind, ObjectNode document) {
    if (kind.servicePrincipalRequired()) {
      return null;
    }
    JsonNode selected = document.at(kind.signerPointer());
    if (kind == SignedDocumentKind.ARTIFACT) {
      if (!selected.isTextual()) {
        throw new IllegalArgumentException("expected Agent signer ID is not a string");
      }
      return new Principal("agent", selected.textValue());
    }
    if (!selected.isObject()
        || !selected.path("type").isTextual()
        || !selected.path("id").isTextual()) {
      throw new IllegalArgumentException("expected signer is not a Principal object");
    }
    return new Principal(selected.path("type").textValue(), selected.path("id").textValue());
  }

  private static byte[] canonicalBase64Url(String value, String label) {
    byte[] decoded = Base64Url.decode(value);
    if (!Base64Url.encode(decoded).equals(value)) {
      throw new IllegalArgumentException(label + " is not canonical unpadded base64url");
    }
    return decoded;
  }

  private static void validateSignatureEncoding(byte[] signature) {
    if (signature.length != 64) {
      throw new IllegalArgumentException("Ed25519 signature must contain 64 raw bytes");
    }
    Ed25519Point.validate(Arrays.copyOf(signature, 32), true, "signature R");
    if (Ed25519Point.littleEndian(Arrays.copyOfRange(signature, 32, 64))
            .compareTo(Ed25519Point.ORDER)
        >= 0) {
      throw new IllegalArgumentException("signature S is outside the Ed25519 scalar range");
    }
  }

  private static void requireJsonValue(JsonNode value) {
    if (value.isMissingNode() || value.isBinary() || value.isPojo()) {
      throw new IllegalArgumentException("Signed Document contains a non-JSON value");
    }
    if (value.isObject()) {
      value.fields().forEachRemaining(field -> requireJsonValue(field.getValue()));
    } else if (value.isArray()) {
      value.forEach(SignedDocumentCodec::requireJsonValue);
    }
  }

  private static String hash(byte[] bytes) {
    return "sha256:" + CanonicalJson.sha256Hex(bytes);
  }

  private static SignedDocumentVerificationException failure(
      VerificationStage stage, String reason) {
    return new SignedDocumentVerificationException(new VerificationDiagnostic(stage, reason));
  }

  private static String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  private record Envelope(
      String protectedTime,
      ExactInstant protectedInstant,
      Principal expectedPrincipal,
      SignatureMaterial signature) {}
}
