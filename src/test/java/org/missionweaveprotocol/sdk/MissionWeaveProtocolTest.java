package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MissionWeaveProtocolTest {
  @Test
  void exposesCanonicalProtocolIdentity() {
    assertEquals("MissionWeaveProtocol", MissionWeaveProtocol.NAME);
    assertEquals("0.1", MissionWeaveProtocol.WIRE_VERSION);
  }
}
