package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConformanceRunnerTest {
  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

  @Test
  void packagedManifestPassesAllFortyThreeVectors() throws IOException {
    ConformanceReport report = ConformanceRunner.runPackaged();

    assertEquals(43, report.results().size());
    assertTrue(report.passed(), () -> failures(report));
    assertEquals("43/43 conformance vectors passed", report.summary());
  }

  @Test
  void sourceManifestPassesAllFortyThreeVectors() throws IOException {
    ConformanceReport report = ConformanceRunner.run(ROOT);

    assertEquals(43, report.results().size());
    assertTrue(report.passed(), () -> failures(report));
  }

  private static String failures(ConformanceReport report) {
    return report.results().stream()
        .filter(result -> !result.passed())
        .map(result -> result.name() + ": " + result.error())
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("unknown failure");
  }
}
