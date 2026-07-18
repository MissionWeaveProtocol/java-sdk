package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Immutable evidence produced after all six cryptographic verification stages succeed. */
public final class VerifiedSignedDocument {
  private final SignedDocumentKind kind;
  private final ObjectNode document;
  private final byte[] receivedBytes;
  private final byte[] signingBytes;
  private final String signingHash;
  private final byte[] canonicalBytes;
  private final String canonicalHash;
  private final String protectedTime;
  private final ExactInstant protectedInstant;
  private final SignatureMaterial signature;
  private final ResolvedKey resolvedKey;

  VerifiedSignedDocument(
      SignedDocumentKind kind,
      ObjectNode document,
      byte[] receivedBytes,
      byte[] signingBytes,
      String signingHash,
      byte[] canonicalBytes,
      String canonicalHash,
      String protectedTime,
      ExactInstant protectedInstant,
      SignatureMaterial signature,
      ResolvedKey resolvedKey) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.document = Objects.requireNonNull(document, "document").deepCopy();
    this.receivedBytes = Objects.requireNonNull(receivedBytes, "receivedBytes").clone();
    this.signingBytes = Objects.requireNonNull(signingBytes, "signingBytes").clone();
    this.signingHash = Objects.requireNonNull(signingHash, "signingHash");
    this.canonicalBytes = Objects.requireNonNull(canonicalBytes, "canonicalBytes").clone();
    this.canonicalHash = Objects.requireNonNull(canonicalHash, "canonicalHash");
    this.protectedTime = Objects.requireNonNull(protectedTime, "protectedTime");
    this.protectedInstant = Objects.requireNonNull(protectedInstant, "protectedInstant");
    this.signature = Objects.requireNonNull(signature, "signature");
    this.resolvedKey = Objects.requireNonNull(resolvedKey, "resolvedKey");
  }

  public SignedDocumentKind kind() {
    return kind;
  }

  /** Return a detached copy so callers cannot mutate retained verification evidence. */
  public ObjectNode document() {
    return document.deepCopy();
  }

  public byte[] receivedBytes() {
    return receivedBytes.clone();
  }

  public byte[] signingBytes() {
    return signingBytes.clone();
  }

  public String signingHash() {
    return signingHash;
  }

  public byte[] canonicalBytes() {
    return canonicalBytes.clone();
  }

  public String canonicalHash() {
    return canonicalHash;
  }

  public String protectedTime() {
    return protectedTime;
  }

  public ExactInstant protectedInstant() {
    return protectedInstant;
  }

  public SignatureMaterial signature() {
    return signature;
  }

  public ResolvedKey resolvedKey() {
    return resolvedKey;
  }

  public Principal resolvedPrincipal() {
    return resolvedKey.principal();
  }
}
