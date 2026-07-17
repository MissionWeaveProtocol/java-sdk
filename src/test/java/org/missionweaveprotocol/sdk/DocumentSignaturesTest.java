package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentSignaturesTest {
  @Test
  void excludesOnlyTheTopLevelSignatureField() throws IOException {
    Ed25519.EncodedKeyPair keys = Ed25519.generateKeyPair();
    byte[] first =
        bytes(
            "{\"payload\":{\"signature\":\"nested\"},\"signature\":{\"value\":\"old\"},\"value\":1}");
    byte[] second =
        bytes(
            "{\"signature\":{\"value\":\"different\"},\"value\":1,\"payload\":{\"signature\":\"nested\"}}");

    assertArrayEquals(
        bytes("{\"payload\":{\"signature\":\"nested\"},\"value\":1}"),
        DocumentSignatures.signingPayload(first));
    String left = DocumentSignatures.sign(first, keys.privateKey());
    String right = DocumentSignatures.sign(second, keys.privateKey());

    assertEquals(left, right);
    assertTrue(DocumentSignatures.verify(second, left, keys.publicKey()));
    assertFalse(
        DocumentSignatures.verify(
            bytes("{\"value\":2,\"payload\":{\"signature\":\"nested\"}}"), left, keys.publicKey()));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
