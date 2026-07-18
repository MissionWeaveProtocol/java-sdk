package org.missionweaveprotocol.sdk;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Specific first-failure evidence intended for protected audit storage. */
public record VerificationDiagnostic(VerificationStage stage, String reason)
    implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  public VerificationDiagnostic {
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(reason, "reason");
    if (stage == VerificationStage.COMPLETE) {
      throw new IllegalArgumentException("A failure diagnostic cannot use the complete stage");
    }
  }
}
