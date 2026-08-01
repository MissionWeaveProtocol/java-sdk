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

  /** Parsed exact lower validity bound retained from the selected Registry evidence. */
  public ExactInstant validFromInstant() {
    return ExactInstant.parse(validFrom);
  }

  /** Parsed exact exclusive expiry bound, or {@code null} when absent. */
  public ExactInstant validUntilInstant() {
    return validUntil == null ? null : ExactInstant.parse(validUntil);
  }

  /** Parsed exact exclusive revocation bound, or {@code null} when absent. */
  public ExactInstant revokedAtInstant() {
    return revokedAt == null ? null : ExactInstant.parse(revokedAt);
  }
}
