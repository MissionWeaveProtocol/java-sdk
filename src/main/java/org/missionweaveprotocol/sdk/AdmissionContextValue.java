package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Trusted deployment context used to prepare a new First-Admission Record. */
public record AdmissionContextValue(
    String admissionRecordId, String trustedAcceptedAt, Principal acceptedBy) {
  public AdmissionContextValue {
    Objects.requireNonNull(admissionRecordId, "admissionRecordId");
    Objects.requireNonNull(trustedAcceptedAt, "trustedAcceptedAt");
    Objects.requireNonNull(acceptedBy, "acceptedBy");
    if (!acceptedBy.type().equals("service")) {
      throw new IllegalArgumentException("acceptedBy must be a service Principal");
    }
  }
}
