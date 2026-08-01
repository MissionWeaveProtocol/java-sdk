package org.missionweaveprotocol.sdk;

/** Authenticated append-only Admission Log adapter. */
public interface AdmissionLog {
  /** Lookup one Organization and signing hash. */
  AdmissionLookup lookup(String organizationId, String signingHash)
      throws AdmissionAdapterException;

  /** Commit a candidate or return the already committed authenticated record. */
  AuthenticatedAdmissionRecord appendOrReturnExisting(
      String organizationId, String signingHash, byte[] candidateBytes)
      throws AdmissionAdapterException;
}
