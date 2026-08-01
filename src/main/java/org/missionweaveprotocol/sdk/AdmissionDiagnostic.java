package org.missionweaveprotocol.sdk;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Protected local Admission evidence that must not be returned to peers. */
public record AdmissionDiagnostic(AdmissionReason reason) implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  public AdmissionDiagnostic {
    Objects.requireNonNull(reason, "reason");
  }

  /** Stable Admission semantic stage. */
  public String stage() {
    return "admission";
  }
}
