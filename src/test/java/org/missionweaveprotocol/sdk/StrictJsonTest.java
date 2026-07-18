package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StrictJsonTest {
  @Test
  void parsesExactlyOneUtf8JsonValue() throws IOException {
    assertEquals(1, StrictJson.parse("{\"value\":1}").get("value").intValue());
    assertThrows(IOException.class, () -> StrictJson.parse("{\"value\":1} true"));
    assertThrows(IOException.class, () -> StrictJson.parse(new byte[0]));
  }

  @Test
  void rejectsDuplicateMembersAtEveryDepth() {
    assertThrows(
        IOException.class, () -> StrictJson.parse("{\"outer\":{\"value\":1,\"value\":2}}"));
  }

  @Test
  void rejectsInvalidUtf8() {
    assertThrows(IOException.class, () -> StrictJson.parse(stringDocument((byte) 0xff)));
    assertThrows(
        IOException.class, () -> StrictJson.parse(stringDocument((byte) 0xc0, (byte) 0x80)));
    assertThrows(
        IOException.class,
        () -> StrictJson.parse(stringDocument((byte) 0xed, (byte) 0xa0, (byte) 0x80)));
    assertThrows(
        IOException.class,
        () -> StrictJson.parse(stringDocument((byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80)));
  }

  @Test
  void rejectsUtf8ByteOrderMarks() {
    byte[] document = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '{', '}'};
    assertThrows(IOException.class, () -> StrictJson.parse(document));
  }

  private static byte[] stringDocument(byte... encodedValue) {
    byte[] prefix = "{\"value\":\"".getBytes(StandardCharsets.UTF_8);
    byte[] document = new byte[prefix.length + encodedValue.length + 2];
    System.arraycopy(prefix, 0, document, 0, prefix.length);
    System.arraycopy(encodedValue, 0, document, prefix.length, encodedValue.length);
    document[document.length - 2] = '"';
    document[document.length - 1] = '}';
    return document;
  }
}
