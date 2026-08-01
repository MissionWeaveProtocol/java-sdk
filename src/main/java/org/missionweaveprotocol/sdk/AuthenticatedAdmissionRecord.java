package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Adapter-authenticated First-Admission Record bytes. */
public final class AuthenticatedAdmissionRecord {
  private final byte[] recordBytes;
  private final Principal authenticatedService;

  /** Construct immutable record evidence authenticated as one Organization service. */
  public AuthenticatedAdmissionRecord(byte[] recordBytes, Principal authenticatedService) {
    this.recordBytes = Objects.requireNonNull(recordBytes, "recordBytes").clone();
    this.authenticatedService =
        Objects.requireNonNull(authenticatedService, "authenticatedService");
    if (!authenticatedService.type().equals("service")) {
      throw new IllegalArgumentException("authenticatedService must be a service Principal");
    }
  }

  /** Exact adapter-returned bytes. */
  public byte[] recordBytes() {
    return recordBytes.clone();
  }

  /** Service identity authenticated by the adapter. */
  public Principal authenticatedService() {
    return authenticatedService;
  }
}
