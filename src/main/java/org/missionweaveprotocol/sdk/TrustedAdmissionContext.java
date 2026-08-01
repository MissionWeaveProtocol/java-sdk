package org.missionweaveprotocol.sdk;

/** Trusted deployment seam invoked only after authoritative Admission Log absence. */
@FunctionalInterface
public interface TrustedAdmissionContext {
  /** Issue trusted record context for one Organization and signing hash. */
  AdmissionContextValue issue(String organizationId, String signingHash)
      throws AdmissionAdapterException;
}
