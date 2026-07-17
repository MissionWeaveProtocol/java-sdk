package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtocolBundleTest {
  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final ClassLoader CLASS_LOADER = ProtocolBundleTest.class.getClassLoader();

  @TempDir Path temporaryDirectory;

  @Test
  void verifiesSourceBundle() throws IOException {
    ProtocolBundle.Verification verification = ProtocolBundle.verify(ROOT);

    assertVerification(verification);
  }

  @Test
  void verifiesPackagedBundle() throws IOException {
    ProtocolBundle.Verification verification = ProtocolBundle.verifyPackaged();

    assertVerification(verification);
  }

  @Test
  void packagedResourcesAreByteIdenticalToVendoredSources() throws IOException {
    for (String path : ProtocolBundle.resourcePaths(CLASS_LOADER)) {
      try (InputStream packaged = CLASS_LOADER.getResourceAsStream(path)) {
        assertTrue(packaged != null, () -> "Missing packaged resource: " + path);
        assertArrayEquals(Files.readAllBytes(ROOT.resolve(path)), packaged.readAllBytes(), path);
      }
    }
  }

  @Test
  void rejectsTamperedSchema() throws IOException {
    copyBundle(temporaryDirectory);
    Files.writeString(
        temporaryDirectory.resolve("schemas/agent-card.schema.json"),
        " ",
        StandardCharsets.UTF_8,
        StandardOpenOption.APPEND);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> ProtocolBundle.verify(temporaryDirectory));

    assertTrue(error.getMessage().startsWith("schemas digest mismatch:"));
  }

  @Test
  void rejectsTamperedPinIdentity() throws IOException {
    copyBundle(temporaryDirectory);
    Path pin = temporaryDirectory.resolve(ProtocolBundle.PIN_RESOURCE);
    Files.writeString(
        pin,
        Files.readString(pin, StandardCharsets.UTF_8)
            .replace(ProtocolBundle.COMMIT, "0000000000000000000000000000000000000000"),
        StandardCharsets.UTF_8);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> ProtocolBundle.verify(temporaryDirectory));

    assertTrue(error.getMessage().startsWith("Protocol pin commit expected"));
  }

  @Test
  void rejectsMissingConformanceVector() throws IOException {
    copyBundle(temporaryDirectory);
    Files.delete(temporaryDirectory.resolve("conformance/vectors/valid/agent-card.json"));

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> ProtocolBundle.verify(temporaryDirectory));

    assertEquals("conformance expected 44 JSON files, found 43", error.getMessage());
  }

  private static void assertVerification(ProtocolBundle.Verification verification) {
    assertEquals(ProtocolBundle.COMMIT, verification.protocolCommit());
    assertEquals(ProtocolBundle.PROTOCOL_VERSION, verification.protocolVersion());
    assertEquals(21, verification.schemaFiles());
    assertEquals(44, verification.conformanceFiles());
    assertEquals(ProtocolBundle.BUNDLE_SHA256, verification.bundleSha256());
  }

  private static void copyBundle(Path destination) throws IOException {
    Files.copy(
        ROOT.resolve(ProtocolBundle.PIN_RESOURCE),
        destination.resolve(ProtocolBundle.PIN_RESOURCE));
    for (String path : ProtocolBundle.resourcePaths(CLASS_LOADER)) {
      Path target = destination.resolve(path);
      Files.createDirectories(target.getParent());
      Files.copy(ROOT.resolve(path), target);
    }
  }
}
