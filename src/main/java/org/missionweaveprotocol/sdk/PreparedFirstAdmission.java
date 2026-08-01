package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** A verified Signed Document plus its validated candidate record. */
public final class PreparedFirstAdmission {
  private final VerifiedSignedDocument verified;
  private final FirstAdmissionRecord record;

  PreparedFirstAdmission(VerifiedSignedDocument verified, FirstAdmissionRecord record) {
    this.verified = Objects.requireNonNull(verified, "verified");
    this.record = Objects.requireNonNull(record, "record");
  }

  public VerifiedSignedDocument verified() {
    return verified;
  }

  public FirstAdmissionRecord record() {
    return record;
  }

  /** Canonical candidate bytes supplied to the Admission Log. */
  public byte[] recordBytes() {
    return record.bytes();
  }
}
