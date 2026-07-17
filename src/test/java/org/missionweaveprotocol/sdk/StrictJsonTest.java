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
    byte[] prefix = "{\"value\":\"".getBytes(StandardCharsets.UTF_8);
    byte[] document = new byte[prefix.length + 3];
    System.arraycopy(prefix, 0, document, 0, prefix.length);
    document[prefix.length] = (byte) 0xff;
    document[prefix.length + 1] = '"';
    document[prefix.length + 2] = '}';

    assertThrows(IOException.class, () -> StrictJson.parse(document));
  }
}
