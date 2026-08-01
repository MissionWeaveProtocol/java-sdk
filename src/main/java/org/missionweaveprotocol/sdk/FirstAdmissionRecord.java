package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Immutable validated First-Admission Record. */
public final class FirstAdmissionRecord {
  private final byte[] bytes;
  private final String protocolVersion;
  private final String admissionRecordId;
  private final String organizationId;
  private final SignedDocumentKind documentKind;
  private final String signingHash;
  private final String keyId;
  private final Principal principal;
  private final String trustedAcceptedAt;
  private final Principal acceptedBy;

  FirstAdmissionRecord(
      byte[] bytes,
      String protocolVersion,
      String admissionRecordId,
      String organizationId,
      SignedDocumentKind documentKind,
      String signingHash,
      String keyId,
      Principal principal,
      String trustedAcceptedAt,
      Principal acceptedBy) {
    this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    this.protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
    this.admissionRecordId = Objects.requireNonNull(admissionRecordId, "admissionRecordId");
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    this.documentKind = Objects.requireNonNull(documentKind, "documentKind");
    this.signingHash = Objects.requireNonNull(signingHash, "signingHash");
    this.keyId = Objects.requireNonNull(keyId, "keyId");
    this.principal = Objects.requireNonNull(principal, "principal");
    this.trustedAcceptedAt = Objects.requireNonNull(trustedAcceptedAt, "trustedAcceptedAt");
    this.acceptedBy = Objects.requireNonNull(acceptedBy, "acceptedBy");
  }

  /** Exact validated bytes returned by the adapter or prepared for append. */
  public byte[] bytes() {
    return bytes.clone();
  }

  public String protocolVersion() {
    return protocolVersion;
  }

  public String admissionRecordId() {
    return admissionRecordId;
  }

  public String organizationId() {
    return organizationId;
  }

  public SignedDocumentKind documentKind() {
    return documentKind;
  }

  public String signingHash() {
    return signingHash;
  }

  public String keyId() {
    return keyId;
  }

  public Principal principal() {
    return principal;
  }

  public String trustedAcceptedAt() {
    return trustedAcceptedAt;
  }

  public Principal acceptedBy() {
    return acceptedBy;
  }
}
