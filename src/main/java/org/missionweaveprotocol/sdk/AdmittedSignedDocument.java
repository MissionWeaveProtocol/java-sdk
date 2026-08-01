package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Immutable six-stage verification and authenticated Admission evidence. */
public final class AdmittedSignedDocument {
  private final VerifiedSignedDocument verified;
  private final FirstAdmissionRecord record;

  AdmittedSignedDocument(VerifiedSignedDocument verified, FirstAdmissionRecord record) {
    this.verified = Objects.requireNonNull(verified, "verified");
    this.record = Objects.requireNonNull(record, "record");
  }

  public VerifiedSignedDocument verified() {
    return verified;
  }

  public FirstAdmissionRecord record() {
    return record;
  }

  /** Exact authenticated record bytes. */
  public byte[] recordBytes() {
    return record.bytes();
  }
}
