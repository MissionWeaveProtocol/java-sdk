package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Immutable signature-envelope text and decoded Ed25519 material. */
public final class SignatureMaterial {
  private final String algorithm;
  private final String keyId;
  private final String createdAt;
  private final String value;
  private final byte[] bytes;

  SignatureMaterial(String algorithm, String keyId, String createdAt, String value, byte[] bytes) {
    this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
    this.keyId = Objects.requireNonNull(keyId, "keyId");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.value = Objects.requireNonNull(value, "value");
    this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
  }

  public String algorithm() {
    return algorithm;
  }

  public String keyId() {
    return keyId;
  }

  public String createdAt() {
    return createdAt;
  }

  public String value() {
    return value;
  }

  public byte[] bytes() {
    return bytes.clone();
  }
}
