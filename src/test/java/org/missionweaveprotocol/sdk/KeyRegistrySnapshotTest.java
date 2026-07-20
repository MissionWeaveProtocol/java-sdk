package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KeyRegistrySnapshotTest {
  @Test
  void constructorClonesRegistryBytes() {
    byte[] registryBytes = {1, 2, 3};
    KeyRegistrySnapshot snapshot =
        new KeyRegistrySnapshot(registryBytes, KeyRegistryCompleteness.PARTIAL);

    registryBytes[0] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, snapshot.registryBytes());
    assertEquals(KeyRegistryCompleteness.PARTIAL, snapshot.completeness());
  }

  @Test
  void registryBytesReturnsAFreshClone() {
    KeyRegistrySnapshot snapshot =
        new KeyRegistrySnapshot(new byte[] {1, 2, 3}, KeyRegistryCompleteness.UNSPECIFIED);

    byte[] firstCopy = snapshot.registryBytes();
    firstCopy[0] = 9;
    byte[] secondCopy = snapshot.registryBytes();

    assertNotSame(firstCopy, secondCopy);
    assertArrayEquals(new byte[] {1, 2, 3}, secondCopy);
    assertEquals(KeyRegistryCompleteness.UNSPECIFIED, snapshot.completeness());
  }

  @Test
  void organizationWideSetsOrganizationWideCompleteness() {
    KeyRegistrySnapshot snapshot = KeyRegistrySnapshot.organizationWide(new byte[] {1, 2, 3});

    assertEquals(KeyRegistryCompleteness.ORGANIZATION_WIDE, snapshot.completeness());
  }

  @Test
  void rejectsNullRegistryBytes() {
    assertThrows(
        NullPointerException.class,
        () -> new KeyRegistrySnapshot(null, KeyRegistryCompleteness.ORGANIZATION_WIDE));
  }

  @Test
  void rejectsNullCompleteness() {
    assertThrows(
        NullPointerException.class, () -> new KeyRegistrySnapshot(new byte[] {1, 2, 3}, null));
  }
}
