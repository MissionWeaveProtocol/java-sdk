package org.missionweaveprotocol.sdk;

import java.io.Serial;
import java.util.Objects;

/** Non-oracular Signed Document failure with a separately retained protected diagnostic. */
public final class SignedDocumentVerificationException extends Exception {
  @Serial private static final long serialVersionUID = 1L;

  private final VerificationDiagnostic diagnostic;

  SignedDocumentVerificationException(VerificationDiagnostic diagnostic) {
    super("Signed Document verification failed: " + diagnostic.stage().wireCode());
    this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
  }

  /** Wire-safe error classification that does not reveal the cryptographic oracle. */
  public String wireCode() {
    return diagnostic.stage().wireCode();
  }

  /** First semantic stage and specific reason for access-controlled audit storage. */
  public VerificationDiagnostic diagnostic() {
    return diagnostic;
  }
}
