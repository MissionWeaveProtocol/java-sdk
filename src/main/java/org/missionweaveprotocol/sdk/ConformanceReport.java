package org.missionweaveprotocol.sdk;

import java.util.List;
import java.util.Objects;

/** Results from the protocol schema-and-vector conformance manifest. */
public record ConformanceReport(List<VectorResult> results) {
  public ConformanceReport {
    results = List.copyOf(Objects.requireNonNull(results, "results"));
  }

  /** Whether every observed validity matched the manifest. */
  public boolean passed() {
    return results.stream().allMatch(VectorResult::passed);
  }

  /** Stable human-readable result count. */
  public String summary() {
    long passed = results.stream().filter(VectorResult::passed).count();
    return passed + "/" + results.size() + " conformance vectors passed";
  }
}
