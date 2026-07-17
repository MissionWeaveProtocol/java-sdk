package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Expected and observed validity for one implementation-neutral conformance vector. */
public record VectorResult(String name, boolean expectedValid, boolean actualValid, String error) {
  public VectorResult {
    Objects.requireNonNull(name, "name");
  }

  /** Whether observed validity matched the manifest. */
  public boolean passed() {
    return expectedValid == actualValid;
  }
}
