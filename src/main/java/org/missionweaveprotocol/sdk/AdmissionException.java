package org.missionweaveprotocol.sdk;

import java.io.Serial;
import java.util.Objects;

/** Deliberately non-oracular Admission-stage failure. */
public final class AdmissionException extends Exception {
  @Serial private static final long serialVersionUID = 1L;

  private final AdmissionDiagnostic diagnostic;

  AdmissionException(AdmissionReason reason) {
    super("Signed Document admission failed: AUTH_INVALID_SIGNATURE");
    this.diagnostic = new AdmissionDiagnostic(Objects.requireNonNull(reason, "reason"));
  }

  /** Wire-safe failure code. */
  public String wireCode() {
    return "AUTH_INVALID_SIGNATURE";
  }

  /** Protected Admission-stage evidence for access-controlled audit storage. */
  public AdmissionDiagnostic diagnostic() {
    return diagnostic;
  }
}
