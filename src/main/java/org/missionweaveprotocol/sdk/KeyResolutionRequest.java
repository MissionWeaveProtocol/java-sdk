package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Lossless routing and selection context for acquiring complete Registry evidence. */
public record KeyResolutionRequest(
    SignedDocumentKind kind,
    String keyId,
    Principal expectedPrincipal,
    boolean servicePrincipalRequired,
    String protectedTime,
    ExactInstant protectedInstant) {
  /**
   * Construct complete context for one verification decision.
   *
   * <p>These fields help an adapter locate the applicable authoritative Registry revision and help
   * the codec select a binding after validating the complete snapshot. They do not authorize a
   * key-filtered projection or omission of Organization-wide bindings or retained history.
   */
  public KeyResolutionRequest {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(protectedTime, "protectedTime");
    Objects.requireNonNull(protectedInstant, "protectedInstant");
    if (servicePrincipalRequired == (expectedPrincipal != null)) {
      throw new IllegalArgumentException(
          "A key request must require either an exact Principal or a service Principal");
    }
  }
}
