package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CanonicalJsonTest {
  @Test
  void matchesRfc8785NumberRenderingAndObjectOrdering() throws IOException {
    byte[] input =
        "{\"numbers\":[333333333.33333329,1E30,4.50,2e-3,1e-27],\"z\":2,\"a\":true}"
            .getBytes(StandardCharsets.UTF_8);
    String expected = "{\"a\":true,\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],\"z\":2}";

    assertEquals(expected, new String(CanonicalJson.canonicalize(input), StandardCharsets.UTF_8));
    assertEquals(
        "sha256:34276b3b846ce43d2329b0bedc0db23c6840e3f01496fd9e2f7690b9ce517f3b",
        CanonicalJson.canonicalHash(input));
  }

  @Test
  void canonicalHashIgnoresObjectMemberOrder() throws IOException {
    byte[] left = "{\"z\":2,\"a\":{\"enabled\":true}}".getBytes(StandardCharsets.UTF_8);
    byte[] right = "{\"a\":{\"enabled\":true},\"z\":2}".getBytes(StandardCharsets.UTF_8);

    assertEquals(CanonicalJson.canonicalHash(left), CanonicalJson.canonicalHash(right));
  }

  @Test
  void canonicalizesTopLevelPrimitiveValues() throws IOException {
    assertEquals("4.5", CanonicalJson.canonicalString(StrictJson.parse("4.50")));
    assertEquals("\"value\"", CanonicalJson.canonicalString(TextNode.valueOf("value")));
  }

  @Test
  void rejectsDuplicateMembersAndInvalidUnicode() {
    assertThrows(IOException.class, () -> CanonicalJson.canonicalize("{\"a\":1,\"a\":2}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CanonicalJson.canonicalize(TextNode.valueOf("\ud800")));
  }
}
