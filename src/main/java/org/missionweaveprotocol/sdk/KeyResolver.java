package org.missionweaveprotocol.sdk;

/** Adapter to an Organization-controlled Agent Registry. */
@FunctionalInterface
public interface KeyResolver {
  /**
   * Resolve an immutable binding after enforcing Organization-wide key-ID, public-key, tuple, and
   * append-only validity-history invariants. Return {@code null} when the key is unknown.
   */
  ResolvedKey resolve(KeyResolutionRequest request);
}
