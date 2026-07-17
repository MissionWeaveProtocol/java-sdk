package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class Ed25519Test {
  @Test
  void matchesRfc8032VectorOne() {
    byte[] seed =
        HexFormat.of().parseHex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
    byte[] publicBytes =
        HexFormat.of().parseHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
    byte[] expectedSignature =
        HexFormat.of()
            .parseHex(
                "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                    + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
    PrivateKey privateKey = Ed25519.loadPrivateKey(Base64Url.encode(seed));
    PublicKey publicKey = Ed25519.loadPublicKey(Base64Url.encode(publicBytes));

    String signature = Ed25519.sign(new byte[0], privateKey);

    assertEquals(Base64Url.encode(expectedSignature), signature);
    assertTrue(Ed25519.verify(new byte[0], signature, publicKey));
    assertEquals(Base64Url.encode(seed), Ed25519.encodePrivateKey(privateKey));
    assertEquals(Base64Url.encode(publicBytes), Ed25519.encodePublicKey(publicKey));
  }

  @Test
  void generatedKeysRoundTripAndRejectTampering() {
    Ed25519.EncodedKeyPair keys = Ed25519.generateKeyPair();
    byte[] message = "MissionWeaveProtocol".getBytes(StandardCharsets.UTF_8);
    String signature = Ed25519.sign(message, keys.privateKey());

    assertFalse(keys.privateKey().contains("="));
    assertFalse(keys.publicKey().contains("="));
    assertFalse(signature.contains("="));
    assertTrue(Ed25519.verify(message, signature, keys.publicKey()));
    assertFalse(
        Ed25519.verify("tampered".getBytes(StandardCharsets.UTF_8), signature, keys.publicKey()));
    assertFalse(Ed25519.verify(message, signature + "=", keys.publicKey()));
  }

  @Test
  void rejectsPaddedOrWrongLengthKeys() {
    assertThrows(IllegalArgumentException.class, () -> Base64Url.decode("YQ=="));
    assertThrows(IllegalArgumentException.class, () -> Ed25519.loadPrivateKey("YQ"));
    assertThrows(IllegalArgumentException.class, () -> Ed25519.loadPublicKey("YQ"));
  }
}
