package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.missionweaveprotocol.sdk.cli.ConformanceCli;

class ConformanceCliTest {
  @Test
  void runsPackagedAndSourceConformance() {
    assertSuccessfulRun(new String[0]);
    assertSuccessfulRun(
        new String[] {"--root", Path.of("").toAbsolutePath().normalize().toString()});
  }

  @Test
  void rejectsUnknownArguments() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int exitCode =
        ConformanceCli.run(
            new String[] {"--unknown"},
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertEquals("", output.toString(StandardCharsets.UTF_8));
    assertEquals(
        "Usage: missionweaveprotocol-conformance [--root PATH]" + System.lineSeparator(),
        error.toString(StandardCharsets.UTF_8));
  }

  private static void assertSuccessfulRun(String[] arguments) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int exitCode =
        ConformanceCli.run(
            arguments,
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode, () -> error.toString(StandardCharsets.UTF_8));
    assertEquals(
        "52/52 conformance vectors passed" + System.lineSeparator(),
        output.toString(StandardCharsets.UTF_8));
    assertEquals("", error.toString(StandardCharsets.UTF_8));
  }
}
