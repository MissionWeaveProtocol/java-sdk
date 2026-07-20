package org.missionweaveprotocol.sdk;

/** Adapter to an Organization-controlled Agent Registry. */
@FunctionalInterface
public interface KeyResolver {
  /**
   * Return one coherent, authoritative, applicable Organization-wide Registry snapshot.
   *
   * <p>The snapshot must contain the Organization-wide bindings and complete retained history
   * needed for verification. An authoritative unknown key is represented by a non-null complete
   * snapshot in which the key is absent. Every request field, including {@link
   * KeyResolutionRequest#keyId()}, is routing and observability context only; an adapter must never
   * filter the returned evidence to the requested key.
   */
  KeyRegistrySnapshot resolve(KeyResolutionRequest request) throws KeyResolutionException;
}
