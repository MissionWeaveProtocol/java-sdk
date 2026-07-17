package org.missionweaveprotocol.sdk.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import org.missionweaveprotocol.sdk.ConformanceReport;
import org.missionweaveprotocol.sdk.ConformanceRunner;
import org.missionweaveprotocol.sdk.VectorResult;

/** Command-line runner for MissionWeaveProtocol schema-and-vector conformance. */
public final class ConformanceCli {
  private ConformanceCli() {}

  /** Run the CLI and terminate the process only when it fails. */
  public static void main(String[] arguments) {
    int exitCode = run(arguments, System.out, System.err);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /** Run the CLI with injectable output streams. */
  public static int run(String[] arguments, PrintStream output, PrintStream error) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");

    final Path root;
    if (arguments.length == 0) {
      root = null;
    } else if (arguments.length == 2 && "--root".equals(arguments[0])) {
      root = Path.of(arguments[1]);
    } else {
      error.println("Usage: missionweaveprotocol-conformance [--root PATH]");
      return 2;
    }

    final ConformanceReport report;
    try {
      report = root == null ? ConformanceRunner.runPackaged() : ConformanceRunner.run(root);
    } catch (IOException | RuntimeException runError) {
      error.println("Conformance run failed: " + runError.getMessage());
      return 1;
    }

    output.println(report.summary());
    if (report.passed()) {
      return 0;
    }
    for (VectorResult result : report.results()) {
      if (!result.passed()) {
        error.printf(
            "FAIL %s: expected valid=%s actual valid=%s: %s%n",
            result.name(), result.expectedValid(), result.actualValid(), result.error());
      }
    }
    return 1;
  }
}
