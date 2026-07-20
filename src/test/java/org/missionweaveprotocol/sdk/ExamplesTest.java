package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.missionweaveprotocol.examples.FrameRoundTripExample;
import org.missionweaveprotocol.examples.RunConformanceExample;
import org.missionweaveprotocol.examples.ValidateAndSignExample;

class ExamplesTest {
  @Test
  void validateAndSignExampleRuns() throws Exception {
    String output = capture(ValidateAndSignExample::run);

    assertTrue(output.contains("Validated command.schema.json"));
    assertTrue(output.contains("Signature verified: true"));
    assertTrue(output.contains("Canonical hash: sha256:"));
  }

  @Test
  void frameRoundTripExampleRuns() throws Exception {
    String output = capture(FrameRoundTripExample::run);

    assertTrue(output.startsWith("PING" + System.lineSeparator()));
    assertTrue(output.contains("\"frameType\":\"PING\""));
  }

  @Test
  void conformanceExampleRuns() throws Exception {
    assertEquals(
        "56/56 conformance vectors passed" + System.lineSeparator(),
        capture(RunConformanceExample::run));
  }

  private static String capture(Example example) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    example.run(new PrintStream(bytes, true, StandardCharsets.UTF_8));
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @FunctionalInterface
  private interface Example {
    void run(PrintStream output) throws Exception;
  }
}
