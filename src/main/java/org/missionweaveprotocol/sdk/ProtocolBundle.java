package org.missionweaveprotocol.sdk;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Access to the exact MissionWeaveProtocol schema and conformance bundle shipped by the SDK. */
public final class ProtocolBundle {
  /** Source repository recorded by the protocol pin. */
  public static final String REPOSITORY =
      "https://github.com/missionweaveprotocol/missionweaveprotocol";

  /** Exact source commit recorded by the protocol pin. */
  public static final String COMMIT = "6f10987627d62fb296e3490ceceb5539b1e94b70";

  /** Protocol version recorded by the protocol pin. */
  public static final String PROTOCOL_VERSION = "0.1";

  /** Wire namespace recorded by the protocol pin. */
  public static final String WIRE_NAMESPACE = "missionweaveprotocol";

  /** SHA-256 digest of the pinned schema tree. */
  public static final String SCHEMAS_SHA256 =
      "a225900a2c2a6c0d03de38ffa7d67dd16fd1586ca63b8ce1d019159fba5f0413";

  /** SHA-256 digest of the pinned conformance tree. */
  public static final String CONFORMANCE_SHA256 =
      "21badf03fc8b05874a744a2d66d064265c635512dd49378b8d24ab1aa0e958da";

  /** SHA-256 digest of the complete pinned protocol bundle. */
  public static final String BUNDLE_SHA256 =
      "b5590fae29ae09e8c2ec77973405878f4dcb13d23e8acdfb888d563ec770bba7";

  static final String PIN_RESOURCE = "PROTOCOL_PIN.json";
  static final String INDEX_RESOURCE = "META-INF/missionweaveprotocol/protocol-bundle.index";

  private static final List<String> ARTIFACT_NAMES = List.of("schemas", "conformance");

  private ProtocolBundle() {}

  /** Verify protocol resources in a source checkout rooted at {@code root}. */
  public static Verification verify(Path root) throws IOException {
    Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    Pin pin;
    try (InputStream input = Files.newInputStream(normalizedRoot.resolve(PIN_RESOURCE))) {
      pin = readPin(input);
    }

    validatePin(pin);
    Map<String, byte[]> resources = new LinkedHashMap<>();
    for (String name : ARTIFACT_NAMES) {
      Artifact artifact = pin.artifacts().get(name);
      Path directory = normalizedRoot.resolve(artifact.path());
      if (!Files.isDirectory(directory)) {
        throw new FileNotFoundException("Protocol artifact directory is missing: " + directory);
      }

      List<Path> files;
      try (Stream<Path> candidates = Files.walk(directory)) {
        files =
            candidates
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> logicalPath(normalizedRoot, path)))
                .toList();
      }
      for (Path file : files) {
        putUnique(resources, logicalPath(normalizedRoot, file), Files.readAllBytes(file));
      }
    }
    return verifyResources(pin, resources);
  }

  /** Verify the protocol resources packaged with this SDK. */
  public static Verification verifyPackaged() throws IOException {
    return verifyPackaged(ProtocolBundle.class.getClassLoader());
  }

  /** Verify packaged protocol resources visible to {@code classLoader}. */
  public static Verification verifyPackaged(ClassLoader classLoader) throws IOException {
    Objects.requireNonNull(classLoader, "classLoader");
    Pin pin;
    try (InputStream input = openRequired(classLoader, PIN_RESOURCE)) {
      pin = readPin(input);
    }

    validatePin(pin);
    Map<String, byte[]> resources = new LinkedHashMap<>();
    for (String path : resourcePaths(classLoader)) {
      try (InputStream input = openRequired(classLoader, path)) {
        putUnique(resources, path, input.readAllBytes());
      }
    }
    return verifyResources(pin, resources);
  }

  static List<String> resourcePaths(ClassLoader classLoader) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                openRequired(classLoader, INDEX_RESOURCE), StandardCharsets.UTF_8))) {
      return reader.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }
  }

  private static Pin readPin(InputStream input) throws IOException {
    return StrictJson.mapper().treeToValue(StrictJson.parse(input.readAllBytes()), Pin.class);
  }

  private static void validatePin(Pin pin) {
    requireEqual("repository", REPOSITORY, pin.repository());
    requireEqual("commit", COMMIT, pin.commit());
    requireEqual("protocolVersion", PROTOCOL_VERSION, pin.protocolVersion());
    requireEqual("wireNamespace", WIRE_NAMESPACE, pin.wireNamespace());
    requireEqual("bundleSha256", BUNDLE_SHA256, pin.bundleSha256());

    if (pin.artifacts() == null || !pin.artifacts().keySet().equals(Set.copyOf(ARTIFACT_NAMES))) {
      throw new IllegalStateException(
          "Protocol pin must define only schemas and conformance artifacts");
    }
    validateArtifact(pin.artifacts().get("schemas"), "schemas", 21, SCHEMAS_SHA256);
    validateArtifact(pin.artifacts().get("conformance"), "conformance", 53, CONFORMANCE_SHA256);
  }

  private static void validateArtifact(
      Artifact artifact, String expectedPath, int expectedFiles, String expectedSha256) {
    if (artifact == null) {
      throw new IllegalStateException("Protocol pin is missing the " + expectedPath + " artifact");
    }
    requireEqual(expectedPath + ".path", expectedPath, artifact.path());
    if (artifact.files() != expectedFiles) {
      throw new IllegalStateException(
          expectedPath + ".files expected " + expectedFiles + ", got " + artifact.files());
    }
    requireEqual(expectedPath + ".sha256", expectedSha256, artifact.sha256());
  }

  private static Verification verifyResources(Pin pin, Map<String, byte[]> resources) {
    List<Map.Entry<String, byte[]>> allFiles = new ArrayList<>();
    for (String name : ARTIFACT_NAMES) {
      Artifact artifact = pin.artifacts().get(name);
      String prefix = artifact.path() + "/";
      List<Map.Entry<String, byte[]>> files =
          resources.entrySet().stream()
              .filter(entry -> entry.getKey().startsWith(prefix))
              .filter(entry -> entry.getKey().endsWith(".json"))
              .toList();
      if (files.size() != artifact.files()) {
        throw new IllegalStateException(
            name + " expected " + artifact.files() + " JSON files, found " + files.size());
      }

      String actualDigest = treeDigest(files);
      if (!actualDigest.equals(artifact.sha256())) {
        throw new IllegalStateException(
            name + " digest mismatch: expected " + artifact.sha256() + ", got " + actualDigest);
      }
      allFiles.addAll(files);
    }

    if (allFiles.size() != resources.size()) {
      throw new IllegalStateException("Protocol bundle index contains an unexpected resource path");
    }
    String actualBundleDigest = treeDigest(allFiles);
    if (!actualBundleDigest.equals(pin.bundleSha256())) {
      throw new IllegalStateException(
          "bundle digest mismatch: expected " + pin.bundleSha256() + ", got " + actualBundleDigest);
    }

    return new Verification(
        pin.commit(),
        pin.protocolVersion(),
        pin.artifacts().get("schemas").files(),
        pin.artifacts().get("conformance").files(),
        actualBundleDigest);
  }

  private static String treeDigest(List<Map.Entry<String, byte[]>> files) {
    MessageDigest digest = sha256();
    files.stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
              digest.update((byte) 0);
              digest.update(entry.getValue());
              digest.update((byte) 0);
            });
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException error) {
      throw new AssertionError("SHA-256 is required by the Java platform", error);
    }
  }

  private static String logicalPath(Path root, Path file) {
    Path relative = root.relativize(file.toAbsolutePath().normalize());
    return StreamSupport.stream(relative.spliterator(), false)
        .map(Path::toString)
        .collect(Collectors.joining("/"));
  }

  private static InputStream openRequired(ClassLoader classLoader, String resource)
      throws IOException {
    InputStream input = classLoader.getResourceAsStream(resource);
    if (input == null) {
      throw new FileNotFoundException("Packaged protocol resource is missing: " + resource);
    }
    return input;
  }

  private static void putUnique(Map<String, byte[]> resources, String path, byte[] contents) {
    if (resources.putIfAbsent(path, contents) != null) {
      throw new IllegalStateException("Duplicate protocol resource path: " + path);
    }
  }

  private static void requireEqual(String field, Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) {
      throw new IllegalStateException(
          "Protocol pin " + field + " expected " + expected + ", got " + actual);
    }
  }

  /** Successful verification details for a source or packaged protocol bundle. */
  public record Verification(
      String protocolCommit,
      String protocolVersion,
      int schemaFiles,
      int conformanceFiles,
      String bundleSha256) {}

  private record Pin(
      String repository,
      String commit,
      String protocolVersion,
      String wireNamespace,
      Map<String, Artifact> artifacts,
      String bundleSha256) {}

  private record Artifact(String path, int files, String sha256) {}
}
