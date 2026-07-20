package org.missionweaveprotocol.sdk;

import java.util.Objects;

/**
 * Immutable Registry evidence supplied by an Organization-controlled adapter.
 *
 * <p>With {@link KeyRegistryCompleteness#ORGANIZATION_WIDE}, the adapter asserts that the bytes
 * describe one coherent, authoritative, applicable Organization revision with Organization-wide
 * bindings and complete retained history. The JSON bytes are a Java-SDK-local evidence
 * representation, not a MissionWeaveProtocol Registry wire artifact or, by themselves, a
 * completeness proof.
 */
public final class KeyRegistrySnapshot {
  private final byte[] registryBytes;
  private final KeyRegistryCompleteness completeness;

  /** Creates a snapshot from Registry JSON bytes and the adapter's completeness assertion. */
  public KeyRegistrySnapshot(byte[] registryBytes, KeyRegistryCompleteness completeness) {
    this.registryBytes = Objects.requireNonNull(registryBytes, "registryBytes").clone();
    this.completeness = Objects.requireNonNull(completeness, "completeness");
  }

  /** Creates a snapshot whose adapter asserts Organization-wide completeness. */
  public static KeyRegistrySnapshot organizationWide(byte[] registryBytes) {
    return new KeyRegistrySnapshot(registryBytes, KeyRegistryCompleteness.ORGANIZATION_WIDE);
  }

  /** Returns a defensive copy of the Registry JSON evidence bytes. */
  public byte[] registryBytes() {
    return registryBytes.clone();
  }

  /** Returns the completeness asserted by the Registry acquisition adapter. */
  public KeyRegistryCompleteness completeness() {
    return completeness;
  }
}
