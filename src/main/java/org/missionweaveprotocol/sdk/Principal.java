package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Exact MissionWeaveProtocol Principal identity. */
public record Principal(String type, String id) {
  public Principal {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(id, "id");
    if (!type.equals("agent") && !type.equals("human") && !type.equals("service")) {
      throw new IllegalArgumentException("Unsupported Principal type: " + type);
    }
  }
}
