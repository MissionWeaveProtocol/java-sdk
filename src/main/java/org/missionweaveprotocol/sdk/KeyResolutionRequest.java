package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Lossless information needed to resolve one Signed Document key binding. */
public record KeyResolutionRequest(
    SignedDocumentKind kind,
    String keyId,
    Principal expectedPrincipal,
    boolean servicePrincipalRequired,
    String protectedTime,
    ExactInstant protectedInstant) {
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
