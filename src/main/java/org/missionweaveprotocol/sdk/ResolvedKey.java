package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Codec-produced evidence for a binding selected from a validated Registry snapshot. */
public record ResolvedKey(
    String organizationId,
    String keyId,
    Principal principal,
    String algorithm,
    String publicKey,
    String validFrom,
    String validUntil,
    String revokedAt) {
  public ResolvedKey {
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(principal, "principal");
    Objects.requireNonNull(algorithm, "algorithm");
    Objects.requireNonNull(publicKey, "publicKey");
    Objects.requireNonNull(validFrom, "validFrom");
  }
}
