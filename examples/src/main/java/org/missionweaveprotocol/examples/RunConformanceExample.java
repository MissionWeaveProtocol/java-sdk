package org.missionweaveprotocol.examples;

import java.io.PrintStream;
import java.util.Objects;
import org.missionweaveprotocol.sdk.ConformanceReport;
import org.missionweaveprotocol.sdk.ConformanceRunner;

/** Run the complete packaged schema-and-vector conformance manifest. */
public final class RunConformanceExample {
  private RunConformanceExample() {}

  public static void main(String[] arguments) throws Exception {
    run(System.out);
  }

  public static void run(PrintStream output) throws Exception {
    Objects.requireNonNull(output, "output");
    ConformanceReport report = ConformanceRunner.runPackaged();
    output.println(report.summary());
    if (!report.passed()) {
      throw new IllegalStateException("One or more conformance vectors failed");
    }
  }
}
