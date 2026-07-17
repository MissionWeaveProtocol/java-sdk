package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

/** Canonical Ed25519 signatures over protocol documents without their top-level signature field. */
public final class DocumentSignatures {
  private DocumentSignatures() {}

  /** Build the canonical signing payload from a strict JSON document. */
  public static byte[] signingPayload(byte[] document) throws IOException {
    return signingPayload(StrictJson.parse(document));
  }

  /** Build the canonical signing payload from a JSON tree. */
  public static byte[] signingPayload(JsonNode document) throws IOException {
    Objects.requireNonNull(document, "document");
    if (!(document instanceof ObjectNode object)) {
      throw new IllegalArgumentException("Signed protocol document must be a JSON object");
    }
    ObjectNode unsigned = object.deepCopy();
    unsigned.remove("signature");
    return CanonicalJson.canonicalize(unsigned);
  }

  /** Sign a protocol document with a JDK Ed25519 private key. */
  public static String sign(byte[] document, PrivateKey privateKey) throws IOException {
    return Ed25519.sign(signingPayload(document), privateKey);
  }

  /** Sign a protocol document with an unpadded base64url raw private key. */
  public static String sign(byte[] document, String privateKey) throws IOException {
    return Ed25519.sign(signingPayload(document), privateKey);
  }

  /** Verify a protocol document with a JDK Ed25519 public key. */
  public static boolean verify(byte[] document, String signature, PublicKey publicKey)
      throws IOException {
    return Ed25519.verify(signingPayload(document), signature, publicKey);
  }

  /** Verify a protocol document with an unpadded base64url raw public key. */
  public static boolean verify(byte[] document, String signature, String publicKey)
      throws IOException {
    return Ed25519.verify(signingPayload(document), signature, publicKey);
  }
}
