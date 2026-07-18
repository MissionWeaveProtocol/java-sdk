package org.missionweaveprotocol.sdk;

/** External Ed25519 signing adapter, such as an HSM or workload-identity key. */
public interface SigningKey {
  /** Immutable Registry key identifier placed in the signature envelope. */
  String keyId();

  /** Produce a raw 64-byte pure-Ed25519 signature over the exact supplied bytes. */
  byte[] sign(byte[] signingBytes);
}
