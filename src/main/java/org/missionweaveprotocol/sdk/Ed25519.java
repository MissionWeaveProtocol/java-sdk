package org.missionweaveprotocol.sdk;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** JDK Ed25519 operations with interoperable raw 32-byte key encodings. */
public final class Ed25519 {
  private static final int KEY_BYTES = 32;
  private static final int SIGNATURE_BYTES = 64;
  private static final byte[] PRIVATE_PREFIX =
      HexFormat.of().parseHex("302e020100300506032b657004220420");
  private static final byte[] PUBLIC_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

  private Ed25519() {}

  /** Generate an unpadded base64url raw private/public key pair. */
  public static EncodedKeyPair generateKeyPair() {
    try {
      java.security.KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
      return new EncodedKeyPair(
          encodePrivateKey(keyPair.getPrivate()), encodePublicKey(keyPair.getPublic()));
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("Ed25519 is required by Java 21", error);
    }
  }

  /** Load an Ed25519 private key from an unpadded base64url 32-byte seed. */
  public static PrivateKey loadPrivateKey(String encoded) {
    byte[] seed = requireLength(Base64Url.decode(encoded), KEY_BYTES, "private key");
    try {
      return keyFactory()
          .generatePrivate(new PKCS8EncodedKeySpec(concatenate(PRIVATE_PREFIX, seed)));
    } catch (GeneralSecurityException error) {
      throw new IllegalArgumentException("Invalid Ed25519 private key", error);
    }
  }

  /** Load an Ed25519 public key from unpadded base64url raw key bytes. */
  public static PublicKey loadPublicKey(String encoded) {
    byte[] raw = requireLength(Base64Url.decode(encoded), KEY_BYTES, "public key");
    try {
      return keyFactory().generatePublic(new X509EncodedKeySpec(concatenate(PUBLIC_PREFIX, raw)));
    } catch (GeneralSecurityException error) {
      throw new IllegalArgumentException("Invalid Ed25519 public key", error);
    }
  }

  /** Encode an Ed25519 private key as an unpadded base64url 32-byte seed. */
  public static String encodePrivateKey(PrivateKey key) {
    return Base64Url.encode(
        extractRaw(Objects.requireNonNull(key, "key"), PRIVATE_PREFIX, "private"));
  }

  /** Encode an Ed25519 public key as unpadded base64url raw key bytes. */
  public static String encodePublicKey(PublicKey key) {
    return Base64Url.encode(
        extractRaw(Objects.requireNonNull(key, "key"), PUBLIC_PREFIX, "public"));
  }

  /** Sign bytes and return an unpadded base64url Ed25519 signature. */
  public static String sign(byte[] message, PrivateKey privateKey) {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(privateKey, "privateKey");
    try {
      Signature signer = Signature.getInstance("Ed25519");
      signer.initSign(privateKey);
      signer.update(message);
      return Base64Url.encode(signer.sign());
    } catch (GeneralSecurityException error) {
      throw new IllegalArgumentException("Unable to sign with Ed25519 private key", error);
    }
  }

  /** Sign bytes with an unpadded base64url raw private key. */
  public static String sign(byte[] message, String privateKey) {
    return sign(message, loadPrivateKey(privateKey));
  }

  /** Verify an unpadded base64url Ed25519 signature. */
  public static boolean verify(byte[] message, String signature, PublicKey publicKey) {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(publicKey, "publicKey");
    final byte[] signatureBytes;
    try {
      signatureBytes = Base64Url.decode(signature);
    } catch (IllegalArgumentException error) {
      return false;
    }
    if (signatureBytes.length != SIGNATURE_BYTES) {
      return false;
    }
    try {
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(publicKey);
      verifier.update(message);
      return verifier.verify(signatureBytes);
    } catch (GeneralSecurityException error) {
      return false;
    }
  }

  /** Verify with an unpadded base64url raw public key. */
  public static boolean verify(byte[] message, String signature, String publicKey) {
    return verify(message, signature, loadPublicKey(publicKey));
  }

  private static KeyFactory keyFactory() throws GeneralSecurityException {
    return KeyFactory.getInstance("Ed25519");
  }

  private static byte[] extractRaw(java.security.Key key, byte[] prefix, String kind) {
    byte[] encoded = key.getEncoded();
    if (encoded == null
        || encoded.length != prefix.length + KEY_BYTES
        || !Arrays.equals(prefix, Arrays.copyOf(encoded, prefix.length))) {
      throw new IllegalArgumentException(
          "Ed25519 " + kind + " key does not use the RFC 8410 encoding");
    }
    return Arrays.copyOfRange(encoded, prefix.length, encoded.length);
  }

  private static byte[] requireLength(byte[] value, int expected, String label) {
    if (value.length != expected) {
      throw new IllegalArgumentException(
          "Ed25519 " + label + " must contain " + expected + " raw bytes");
    }
    return value;
  }

  private static byte[] concatenate(byte[] prefix, byte[] value) {
    byte[] combined = Arrays.copyOf(prefix, prefix.length + value.length);
    System.arraycopy(value, 0, combined, prefix.length, value.length);
    return combined;
  }

  /** Unpadded base64url raw Ed25519 key material. */
  public record EncodedKeyPair(String privateKey, String publicKey) {
    public EncodedKeyPair {
      Objects.requireNonNull(privateKey, "privateKey");
      Objects.requireNonNull(publicKey, "publicKey");
    }
  }
}
