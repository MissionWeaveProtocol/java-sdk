package org.missionweaveprotocol.sdk;

/** Complete current Registry evidence applicable to a new First Admission. */
@FunctionalInterface
public interface AdmissionCurrentKeyResolver {
  /** Resolve one current complete Organization-wide Registry snapshot. */
  KeyRegistrySnapshot resolveCurrent(KeyResolutionRequest request) throws KeyResolutionException;
}
