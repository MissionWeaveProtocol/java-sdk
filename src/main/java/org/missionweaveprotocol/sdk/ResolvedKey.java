package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Key binding returned by an Organization-controlled Agent Registry adapter. */
public record ResolvedKey(
    String keyId,
    Principal principal,
    String algorithm,
    String publicKey,
    String validFrom,
    String validUntil,
    String revokedAt) {
  public ResolvedKey {
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(principal, "principal");
    Objects.requireNonNull(algorithm, "algorithm");
    Objects.requireNonNull(publicKey, "publicKey");
    Objects.requireNonNull(validFrom, "validFrom");
  }
}
