package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FrameCodecTest {
  @Test
  void roundTripsSchemaValidCanonicalJson() throws IOException {
    FrameCodec codec = new FrameCodec();
    byte[] document = resource("conformance/vectors/valid/websocket-frame.json");

    ObjectNode frame = codec.decode(document);
    byte[] encoded = codec.encode(frame);

    assertEquals(frame, codec.decode(encoded));
    assertEquals(
        new String(CanonicalJson.canonicalize(document), StandardCharsets.UTF_8),
        new String(encoded, StandardCharsets.UTF_8));
  }

  @Test
  void rejectsDuplicateUnknownAndInvalidUtf8Frames() throws IOException {
    FrameCodec codec = new FrameCodec();
    assertThrows(
        IOException.class,
        () ->
            codec.decode(
                "{\"protocolVersion\":\"0.1\",\"protocolVersion\":\"0.1\","
                    + "\"frameId\":\"urn:x:1\",\"frameType\":\"UNKNOWN\"}"));
    assertThrows(
        SchemaValidationException.class,
        () ->
            codec.decode(
                "{\"protocolVersion\":\"0.1\",\"frameId\":\"urn:x:1\","
                    + "\"frameType\":\"UNKNOWN\"}"));

    byte[] prefix =
        "{\"protocolVersion\":\"0.1\",\"frameId\":\"urn:x:1\",\"frameType\":\""
            .getBytes(StandardCharsets.UTF_8);
    byte[] invalidUtf8 = new byte[prefix.length + 1];
    System.arraycopy(prefix, 0, invalidUtf8, 0, prefix.length);
    invalidUtf8[prefix.length] = (byte) 0xff;
    assertThrows(IOException.class, () -> codec.decode(invalidUtf8));
  }

  @Test
  void preservesExtensionDataWithoutPromotingCoreFields() throws IOException {
    FrameCodec codec = new FrameCodec();
    ObjectNode command =
        (ObjectNode) StrictJson.parse(resource("conformance/vectors/valid/command.json"));
    ObjectNode extensions = StrictJson.mapper().createObjectNode();
    ObjectNode profile = extensions.putObject("https://profiles.example/audit");
    profile.put("version", "1.2.3");
    profile.put("critical", false);
    ObjectNode data = profile.putObject("data");
    data.put("kind", "mission.approved");
    data.put("groupId", "urn:missionweaveprotocol:group:forged");
    data.putObject("payload").put("forged", true);
    command.set("extensions", extensions);

    ObjectNode frame = StrictJson.mapper().createObjectNode();
    frame.put("protocolVersion", "0.1");
    frame.put("frameId", "urn:uuid:00000000-0000-4000-8000-000000000010");
    frame.put("frameType", "COMMAND");
    frame.set("command", command);

    ObjectNode decoded = codec.decode(codec.encode(frame));
    ObjectNode decodedCommand = (ObjectNode) decoded.get("command");
    assertEquals(extensions, decodedCommand.get("extensions"));
    assertEquals(command.get("kind"), decodedCommand.get("kind"));
    assertEquals(command.get("groupId"), decodedCommand.get("groupId"));
  }

  private static byte[] resource(String path) throws IOException {
    try (var input = FrameCodecTest.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }
}
