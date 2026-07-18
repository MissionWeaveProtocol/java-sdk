package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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
  void verifiesSourceCryptographyBundle() throws IOException {
    ProtocolBundle.CryptographyVerification verification =
        ProtocolBundle.verifyCryptographyBundle(ROOT);

    assertCryptographyVerification(verification);
  }

  @Test
  void verifiesPackagedCryptographyBundle() throws IOException {
    ProtocolBundle.CryptographyVerification verification =
        ProtocolBundle.verifyPackagedCryptographyBundle();

    assertCryptographyVerification(verification);
  }

  @Test
  void packagedResourcesAreByteIdenticalToVendoredSources() throws IOException {
    for (String path : ProtocolBundle.resourcePaths(CLASS_LOADER)) {
      try (InputStream packaged = CLASS_LOADER.getResourceAsStream(path)) {
        assertTrue(packaged != null, () -> "Missing packaged resource: " + path);
        assertArrayEquals(Files.readAllBytes(ROOT.resolve(path)), packaged.readAllBytes(), path);
      }
    }
    for (Path source : cryptographyFiles()) {
      String path = ROOT.relativize(source).toString().replace('\\', '/');
      try (InputStream packaged = CLASS_LOADER.getResourceAsStream(path)) {
        assertTrue(packaged != null, () -> "Missing packaged resource: " + path);
        assertArrayEquals(Files.readAllBytes(source), packaged.readAllBytes(), path);
      }
    }
  }

  @Test
  void cryptographyReadmeIsNotADigestProtectedArtifact() throws IOException {
    JsonNode manifest =
        StrictJson.parse(Files.readAllBytes(ROOT.resolve(ProtocolBundle.CRYPTOGRAPHY_PATH)));

    assertFalse(
        StreamSupport.stream(manifest.get("artifacts").spliterator(), false)
            .map(entry -> entry.get("path").textValue())
            .anyMatch("cryptography/README.md"::equals));
  }

  @Test
  void rejectsTamperedCryptographyArtifact() throws IOException {
    copyBundle(temporaryDirectory);
    Files.writeString(
        temporaryDirectory.resolve("cryptography/vectors/canonicalization/agent-card.signing.jcs"),
        " ",
        StandardCharsets.UTF_8,
        StandardOpenOption.APPEND);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> ProtocolBundle.verifyCryptographyBundle(temporaryDirectory));

    assertTrue(error.getMessage().contains("byteLength mismatch:"));
  }

  @Test
  void rejectsMissingBinaryCryptographyArtifact() throws IOException {
    copyBundle(temporaryDirectory);
    Files.delete(
        temporaryDirectory.resolve(
            "cryptography/vectors/signed-documents/invalid/command-invalid-utf8.bin"));

    IOException error =
        assertThrows(
            IOException.class, () -> ProtocolBundle.verifyCryptographyBundle(temporaryDirectory));

    assertTrue(error.getMessage().startsWith("Cryptography artifact is missing:"));
  }

  @Test
  void rejectsUnsafeCryptographyArtifactPathBeforeReadingIt() throws IOException {
    copyBundle(temporaryDirectory);
    Path manifest = temporaryDirectory.resolve(ProtocolBundle.CRYPTOGRAPHY_PATH);
    Files.writeString(
        manifest,
        Files.readString(manifest, StandardCharsets.UTF_8)
            .replace("cryptography/keys/registry-key-alias.json", "../registry-key-alias.json"),
        StandardCharsets.UTF_8);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> ProtocolBundle.verifyCryptographyBundle(temporaryDirectory));

    assertTrue(error.getMessage().startsWith("Unsafe cryptography artifact path:"));
  }

  @Test
  void rejectsDuplicateCryptographyManifestMembers() throws IOException {
    copyBundle(temporaryDirectory);
    Path manifest = temporaryDirectory.resolve(ProtocolBundle.CRYPTOGRAPHY_PATH);
    String profile = "\"profileId\": \"missionweaveprotocol.signed-document-verification.v0.1\",";
    Files.writeString(
        manifest,
        Files.readString(manifest, StandardCharsets.UTF_8)
            .replace(profile, profile + System.lineSeparator() + "  " + profile),
        StandardCharsets.UTF_8);

    assertThrows(
        IOException.class, () -> ProtocolBundle.verifyCryptographyBundle(temporaryDirectory));
  }

  @Test
  void rejectsCryptographyPinCountMismatch() throws IOException {
    copyBundle(temporaryDirectory);
    Path pin = temporaryDirectory.resolve(ProtocolBundle.PIN_RESOURCE);
    Files.writeString(
        pin,
        Files.readString(pin, StandardCharsets.UTF_8)
            .replace("\"caseCount\": 22", "\"caseCount\": 23"),
        StandardCharsets.UTF_8);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> ProtocolBundle.verifyCryptographyBundle(temporaryDirectory));

    assertTrue(error.getMessage().startsWith("cryptography.caseCount expected"));
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

    assertEquals("conformance expected 53 JSON files, found 52", error.getMessage());
  }

  private static void assertVerification(ProtocolBundle.Verification verification) {
    assertEquals(ProtocolBundle.COMMIT, verification.protocolCommit());
    assertEquals(ProtocolBundle.PROTOCOL_VERSION, verification.protocolVersion());
    assertEquals(21, verification.schemaFiles());
    assertEquals(53, verification.conformanceFiles());
    assertEquals(ProtocolBundle.BUNDLE_SHA256, verification.bundleSha256());
  }

  private static void assertCryptographyVerification(
      ProtocolBundle.CryptographyVerification verification) {
    assertEquals(ProtocolBundle.CRYPTOGRAPHY_SOURCE_COMMIT, verification.sourceCommit());
    assertEquals(ProtocolBundle.CRYPTOGRAPHY_PROFILE_ID, verification.profileId());
    assertEquals(ProtocolBundle.CRYPTOGRAPHY_MANIFEST_VERSION, verification.manifestVersion());
    assertEquals(ProtocolBundle.CRYPTOGRAPHY_ARTIFACT_DIGEST, verification.artifactDigest());
    assertEquals(ProtocolBundle.CRYPTOGRAPHY_ARTIFACT_COUNT, verification.artifactCount());
    assertEquals(ProtocolBundle.CRYPTOGRAPHY_CASE_COUNT, verification.caseCount());
    assertEquals(ProtocolBundle.CRYPTOGRAPHY_EVALUATION_COUNT, verification.evaluationCount());
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
    for (Path source : cryptographyFiles()) {
      Path target = destination.resolve(ROOT.relativize(source));
      Files.createDirectories(target.getParent());
      Files.copy(source, target);
    }
  }

  private static List<Path> cryptographyFiles() throws IOException {
    try (Stream<Path> files = Files.walk(ROOT.resolve("cryptography"))) {
      return files.filter(Files::isRegularFile).sorted().toList();
    }
  }
}
