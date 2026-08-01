package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** First-admission creation and historical Admission Log verification. */
public final class AdmissionService {
  private final SignedDocumentCodec codec;
  private final SchemaCatalog schemas;

  /** Construct a service over the exact schemas packaged with this SDK. */
  public AdmissionService() throws IOException {
    schemas = SchemaCatalog.packaged();
    codec = new SignedDocumentCodec(schemas);
  }

  /** Prepare but do not commit a new First-Admission Record. */
  public PreparedFirstAdmission prepareFirstAdmission(
      VerifiedSignedDocument verified, TrustedAdmissionContext trustedContext)
      throws AdmissionException {
    if (verified == null || trustedContext == null) {
      throw failure(AdmissionReason.RECORD_SCHEMA_INVALID);
    }

    AdmissionContextValue context;
    try {
      context =
          trustedContext.issue(
              verified.resolvedKey().organizationId(), verified.signingHash());
    } catch (AdmissionAdapterException error) {
      throw remap(error);
    }
    if (context == null) {
      throw failure(AdmissionReason.RECORD_SCHEMA_INVALID);
    }

    ExactInstant acceptedAt;
    try {
      acceptedAt = ExactInstant.parse(context.trustedAcceptedAt());
    } catch (RuntimeException error) {
      throw failure(AdmissionReason.MALFORMED_TRUSTED_TIME);
    }

    ObjectNode value = StrictJson.mapper().createObjectNode();
    value.put("protocolVersion", MissionWeaveProtocol.WIRE_VERSION);
    value.put("admissionRecordId", context.admissionRecordId());
    value.put("organizationId", verified.resolvedKey().organizationId());
    value.put("documentKind", verified.kind().id());
    value.put("signingHash", verified.signingHash());
    value.put("keyId", verified.resolvedKey().keyId());
    value.set("principal", principalValue(verified.resolvedPrincipal()));
    value.put("trustedAcceptedAt", context.trustedAcceptedAt());
    value.set("acceptedBy", principalValue(context.acceptedBy()));

    byte[] recordBytes;
    try {
      recordBytes = CanonicalJson.canonicalize(value);
    } catch (IOException | RuntimeException error) {
      throw failure(AdmissionReason.RECORD_SCHEMA_INVALID);
    }
    ParsedRecord parsed = parseRecord(recordBytes);
    validateBindings(parsed, verified, context.acceptedBy());
    if (!parsed.acceptedAt().equals(acceptedAt)) {
      throw failure(AdmissionReason.RECORD_SCHEMA_INVALID);
    }
    return new PreparedFirstAdmission(verified, parsed.record());
  }

  /** Verify with current Registry evidence and admit one Signed Document exactly once. */
  public AdmittedSignedDocument admitFirst(
      SignedDocumentKind kind,
      byte[] documentBytes,
      AdmissionCurrentKeyResolver registry,
      AdmissionLog admissionLog,
      TrustedAdmissionContext trustedContext)
      throws SignedDocumentVerificationException, AdmissionException {
    Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(admissionLog, "admissionLog");
    Objects.requireNonNull(trustedContext, "trustedContext");

    VerifiedSignedDocument verified =
        codec.verify(kind, documentBytes, request -> registry.resolveCurrent(request));
    AdmissionLookup lookup = lookup(admissionLog, verified);
    if (lookup instanceof AdmissionLookup.Found found) {
      return validateAuthenticatedRecord(found.record(), verified);
    }
    if (!(lookup instanceof AdmissionLookup.AuthoritativeAbsence)) {
      throw failure(AdmissionReason.LOG_INDETERMINATE);
    }

    PreparedFirstAdmission prepared = prepareFirstAdmission(verified, trustedContext);
    AuthenticatedAdmissionRecord committed;
    try {
      committed =
          admissionLog.appendOrReturnExisting(
              verified.resolvedKey().organizationId(),
              verified.signingHash(),
              prepared.recordBytes());
    } catch (AdmissionAdapterException error) {
      throw remap(error);
    }
    if (committed == null) {
      throw failure(AdmissionReason.COMMIT_FAILED);
    }
    return validateAuthenticatedRecord(committed, verified);
  }

  /** Rerun six-stage verification and require an existing historical Admission record. */
  public AdmittedSignedDocument verifyHistoricalAdmission(
      SignedDocumentKind kind,
      byte[] documentBytes,
      KeyResolver registry,
      AdmissionLog admissionLog)
      throws SignedDocumentVerificationException, AdmissionException {
    Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(admissionLog, "admissionLog");

    VerifiedSignedDocument verified = codec.verify(kind, documentBytes, registry);
    AdmissionLookup lookup = lookup(admissionLog, verified);
    if (lookup instanceof AdmissionLookup.Found found) {
      return validateAuthenticatedRecord(found.record(), verified);
    }
    if (lookup instanceof AdmissionLookup.AuthoritativeAbsence) {
      throw failure(AdmissionReason.RECORD_MISSING);
    }
    throw failure(AdmissionReason.LOG_INDETERMINATE);
  }

  private static AdmissionLookup lookup(
      AdmissionLog admissionLog, VerifiedSignedDocument verified) throws AdmissionException {
    AdmissionLookup lookup;
    try {
      lookup =
          admissionLog.lookup(
              verified.resolvedKey().organizationId(), verified.signingHash());
    } catch (AdmissionAdapterException error) {
      throw remap(error);
    }
    if (lookup == null) {
      throw failure(AdmissionReason.LOG_INDETERMINATE);
    }
    return lookup;
  }

  private AdmittedSignedDocument validateAuthenticatedRecord(
      AuthenticatedAdmissionRecord authenticated, VerifiedSignedDocument verified)
      throws AdmissionException {
    byte[] recordBytes = authenticated.recordBytes();
    if (isEventSelfAnchoring(recordBytes, verified)) {
      throw failure(AdmissionReason.EVENT_SELF_ANCHORING);
    }
    ParsedRecord parsed = parseRecord(recordBytes);
    validateBindings(parsed, verified, authenticated.authenticatedService());
    return new AdmittedSignedDocument(verified, parsed.record());
  }

  private ParsedRecord parseRecord(byte[] bytes) throws AdmissionException {
    try {
      JsonNode value = StrictJson.parse(bytes);
      schemas.validate("first-admission-record.schema.json", value);
      if (!(value instanceof ObjectNode object)) {
        throw new IllegalArgumentException("First-Admission Record must be an object");
      }
      String protocolVersion = requiredText(object, "protocolVersion");
      String admissionRecordId = requiredText(object, "admissionRecordId");
      String organizationId = requiredText(object, "organizationId");
      SignedDocumentKind documentKind = documentKind(requiredText(object, "documentKind"));
      String signingHash = requiredText(object, "signingHash");
      String keyId = requiredText(object, "keyId");
      Principal principal = principal(object.get("principal"));
      String trustedAcceptedAt = requiredText(object, "trustedAcceptedAt");
      ExactInstant acceptedAt = ExactInstant.parse(trustedAcceptedAt);
      Principal acceptedBy = principal(object.get("acceptedBy"));
      if (!acceptedBy.type().equals("service")) {
        throw new IllegalArgumentException("acceptedBy is not a service Principal");
      }
      FirstAdmissionRecord record =
          new FirstAdmissionRecord(
              bytes,
              protocolVersion,
              admissionRecordId,
              organizationId,
              documentKind,
              signingHash,
              keyId,
              principal,
              trustedAcceptedAt,
              acceptedBy);
      return new ParsedRecord(record, acceptedAt);
    } catch (IOException | RuntimeException error) {
      throw failure(AdmissionReason.RECORD_SCHEMA_INVALID);
    }
  }

  private static void validateBindings(
      ParsedRecord parsed, VerifiedSignedDocument verified, Principal authenticatedService)
      throws AdmissionException {
    FirstAdmissionRecord record = parsed.record();
    ResolvedKey resolved = verified.resolvedKey();
    if (!record.organizationId().equals(resolved.organizationId())
        || record.documentKind() != verified.kind()
        || !record.signingHash().equals(verified.signingHash())
        || !record.keyId().equals(resolved.keyId())
        || !record.principal().equals(resolved.principal())) {
      throw failure(AdmissionReason.RECORD_BINDING_MISMATCH);
    }
    if (!record.acceptedBy().equals(authenticatedService)) {
      throw failure(AdmissionReason.LOG_AUTHENTICATION_FAILED);
    }

    ExactInstant acceptedAt = parsed.acceptedAt();
    if (acceptedAt.compareTo(resolved.validFromInstant()) < 0
        || atOrAfter(acceptedAt, resolved.validUntilInstant())
        || atOrAfter(acceptedAt, resolved.revokedAtInstant())) {
      throw failure(AdmissionReason.TRUSTED_TIME_OUTSIDE_KEY_INTERVAL);
    }
  }

  private static boolean atOrAfter(ExactInstant value, ExactInstant boundary) {
    return boundary != null && value.compareTo(boundary) >= 0;
  }

  private static ObjectNode principalValue(Principal principal) {
    ObjectNode value = StrictJson.mapper().createObjectNode();
    value.put("type", principal.type());
    value.put("id", principal.id());
    return value;
  }

  private static Principal principal(JsonNode value) {
    if (value == null
        || !value.isObject()
        || !value.path("type").isTextual()
        || !value.path("id").isTextual()) {
      throw new IllegalArgumentException("First-Admission Record Principal is malformed");
    }
    return new Principal(value.path("type").textValue(), value.path("id").textValue());
  }

  private static String requiredText(ObjectNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isTextual()) {
      throw new IllegalArgumentException("First-Admission Record " + field + " is not a string");
    }
    return value.textValue();
  }

  private static SignedDocumentKind documentKind(String id) {
    for (SignedDocumentKind kind : SignedDocumentKind.values()) {
      if (kind.id().equals(id)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("Unknown First-Admission Record documentKind: " + id);
  }

  private static boolean isEventSelfAnchoring(
      byte[] recordBytes, VerifiedSignedDocument verified) {
    if (verified.kind() != SignedDocumentKind.EVENT) {
      return false;
    }
    if (Arrays.equals(recordBytes, verified.receivedBytes())
        || Arrays.equals(recordBytes, verified.canonicalBytes())) {
      return true;
    }
    try {
      return Arrays.equals(CanonicalJson.canonicalize(recordBytes), verified.canonicalBytes());
    } catch (IOException | RuntimeException error) {
      return false;
    }
  }

  private static AdmissionException failure(AdmissionReason reason) {
    return new AdmissionException(reason);
  }

  private static AdmissionException remap(AdmissionAdapterException error) {
    return failure(error.reason());
  }

  private record ParsedRecord(FirstAdmissionRecord record, ExactInstant acceptedAt) {}
}
