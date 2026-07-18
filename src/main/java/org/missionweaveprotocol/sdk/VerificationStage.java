package org.missionweaveprotocol.sdk;

/** Ordered semantic stage retained in protected verification diagnostics. */
public enum VerificationStage {
  PARSE("parse", "PROTOCOL_VIOLATION"),
  SCHEMA("schema", "SCHEMA_VALIDATION_FAILED"),
  SIGNATURE_ENVELOPE("signature-envelope", "AUTH_INVALID_SIGNATURE"),
  KEY_RESOLUTION("key-resolution", "AUTH_INVALID_SIGNATURE"),
  CANONICALIZATION("canonicalization", "PROTOCOL_VIOLATION"),
  SIGNATURE("signature", "AUTH_INVALID_SIGNATURE"),
  COMPLETE("complete", null);

  private final String id;
  private final String wireCode;

  VerificationStage(String id, String wireCode) {
    this.id = id;
    this.wireCode = wireCode;
  }

  /** Stable stage identifier from the cryptography profile. */
  public String id() {
    return id;
  }

  /** Non-oracular protocol wire code, or {@code null} for success. */
  public String wireCode() {
    return wireCode;
  }
}
