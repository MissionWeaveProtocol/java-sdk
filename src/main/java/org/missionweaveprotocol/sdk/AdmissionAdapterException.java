package org.missionweaveprotocol.sdk;

import java.io.Serial;
import java.util.Objects;

/** Typed protected failure returned by a trusted Admission deployment adapter. */
public final class AdmissionAdapterException extends Exception {
  @Serial private static final long serialVersionUID = 1L;

  private final AdmissionReason reason;
  private final String detail;

  /** Construct one adapter failure with stable reason and local-only detail. */
  public AdmissionAdapterException(AdmissionReason reason, String detail) {
    super("Admission adapter failed: " + Objects.requireNonNull(detail, "detail"));
    this.reason = Objects.requireNonNull(reason, "reason");
    this.detail = detail;
  }

  /** Stable reason remapped by {@link AdmissionService}. */
  public AdmissionReason reason() {
    return reason;
  }

  /** Protected local detail that must not be placed on the protocol wire. */
  public String detail() {
    return detail;
  }
}
