package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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

/**
 * Access to the exact MissionWeaveProtocol protocol and cryptography bundles shipped by the SDK.
 */
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

  /** Pinned signed-document cryptography manifest path. */
  public static final String CRYPTOGRAPHY_PATH = "cryptography/manifest.json";

  /** Source commit of the pinned signed-document cryptography bundle. */
  public static final String CRYPTOGRAPHY_SOURCE_COMMIT =
      "235aee85ba88934641822e1639e08efd2c9e29b6";

  /** Profile ID of the pinned signed-document cryptography bundle. */
  public static final String CRYPTOGRAPHY_PROFILE_ID =
      "missionweaveprotocol.signed-document-verification.v0.1";

  /** Manifest version of the pinned signed-document cryptography bundle. */
  public static final int CRYPTOGRAPHY_MANIFEST_VERSION = 1;

  /** Artifact digest of the pinned signed-document cryptography bundle. */
  public static final String CRYPTOGRAPHY_ARTIFACT_DIGEST =
      "sha256:487e18c1ea7053432953f28d1496ae4fdb8e9d42c2eeb8e94f9b21f8cc2596a2";

  /** Number of digest-protected artifacts in the cryptography manifest. */
  public static final int CRYPTOGRAPHY_ARTIFACT_COUNT = 94;

  /** Number of cases in the cryptography manifest. */
  public static final int CRYPTOGRAPHY_CASE_COUNT = 22;

  /** Number of evaluations in the cryptography manifest. */
  public static final int CRYPTOGRAPHY_EVALUATION_COUNT = 58;

  static final String PIN_RESOURCE = "PROTOCOL_PIN.json";
  static final String INDEX_RESOURCE = "META-INF/missionweaveprotocol/protocol-bundle.index";

  private static final List<String> ARTIFACT_NAMES = List.of("schemas", "conformance");
  private static final Set<String> CRYPTOGRAPHY_MANIFEST_FIELDS =
      Set.of(
          "$schema",
          "manifestVersion",
          "protocolVersion",
          "profileId",
          "semanticStages",
          "fixtureSchemas",
          "artifactDigest",
          "profiles",
          "artifacts",
          "cases");
  private static final Set<String> CRYPTOGRAPHY_ARTIFACT_FIELDS =
      Set.of("path", "byteLength", "sha256");

  private ProtocolBundle() {}

  /** Verify protocol resources in a source checkout rooted at {@code root}. */
  public static Verification verify(Path root) throws IOException {
    Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    Pin pin;
    try (InputStream input = Files.newInputStream(normalizedRoot.resolve(PIN_RESOURCE))) {
      pin = readPin(input);
    }

    validateProtocolPin(pin);
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

    validateProtocolPin(pin);
    Map<String, byte[]> resources = new LinkedHashMap<>();
    for (String path : resourcePaths(classLoader)) {
      try (InputStream input = openRequired(classLoader, path)) {
        putUnique(resources, path, input.readAllBytes());
      }
    }
    return verifyResources(pin, resources);
  }

  /** Verify the signed-document cryptography bundle in a source checkout. */
  public static CryptographyVerification verifyCryptographyBundle(Path root) throws IOException {
    Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    Pin pin;
    try (InputStream input = Files.newInputStream(normalizedRoot.resolve(PIN_RESOURCE))) {
      pin = readPin(input);
    }
    validateCryptographyPin(pin);
    byte[] manifest = readSourceManifest(normalizedRoot, pin.cryptography().path());
    return verifyCryptographyResources(
        pin.cryptography(), manifest, path -> readSourceResource(normalizedRoot, path));
  }

  /** Verify the signed-document cryptography bundle packaged with this SDK. */
  public static CryptographyVerification verifyPackagedCryptographyBundle() throws IOException {
    return verifyPackagedCryptographyBundle(ProtocolBundle.class.getClassLoader());
  }

  /** Verify packaged cryptography resources visible to {@code classLoader}. */
  public static CryptographyVerification verifyPackagedCryptographyBundle(ClassLoader classLoader)
      throws IOException {
    Objects.requireNonNull(classLoader, "classLoader");
    Pin pin;
    try (InputStream input = openRequired(classLoader, PIN_RESOURCE)) {
      pin = readPin(input);
    }
    validateCryptographyPin(pin);
    byte[] manifest;
    try (InputStream input = openRequired(classLoader, pin.cryptography().path())) {
      manifest = input.readAllBytes();
    }
    return verifyCryptographyResources(
        pin.cryptography(),
        manifest,
        path -> {
          try (InputStream input = openRequired(classLoader, path)) {
            return input.readAllBytes();
          }
        });
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

  private static void validateProtocolPin(Pin pin) {
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

  private static void validateCryptographyPin(Pin pin) {
    requireEqual("repository", REPOSITORY, pin.repository());
    requireEqual("protocolVersion", PROTOCOL_VERSION, pin.protocolVersion());
    requireEqual("wireNamespace", WIRE_NAMESPACE, pin.wireNamespace());
    CryptographyPin cryptography = pin.cryptography();
    if (cryptography == null) {
      throw new IllegalStateException("Protocol pin is missing cryptography");
    }
    requireEqual("cryptography.path", CRYPTOGRAPHY_PATH, cryptography.path());
    requireEqual(
        "cryptography.sourceCommit", CRYPTOGRAPHY_SOURCE_COMMIT, cryptography.sourceCommit());
    requireEqual("cryptography.profileId", CRYPTOGRAPHY_PROFILE_ID, cryptography.profileId());
    requireCount(
        "cryptography.manifestVersion",
        CRYPTOGRAPHY_MANIFEST_VERSION,
        cryptography.manifestVersion());
    requireEqual(
        "cryptography.artifactDigest", CRYPTOGRAPHY_ARTIFACT_DIGEST, cryptography.artifactDigest());
    requireCount(
        "cryptography.artifactCount", CRYPTOGRAPHY_ARTIFACT_COUNT, cryptography.artifactCount());
    requireCount("cryptography.caseCount", CRYPTOGRAPHY_CASE_COUNT, cryptography.caseCount());
    requireCount(
        "cryptography.evaluationCount",
        CRYPTOGRAPHY_EVALUATION_COUNT,
        cryptography.evaluationCount());
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

  private static CryptographyVerification verifyCryptographyResources(
      CryptographyPin pin, byte[] manifestBytes, ResourceReader resources) throws IOException {
    JsonNode parsed = StrictJson.parse(manifestBytes);
    if (!(parsed instanceof ObjectNode manifest)) {
      throw new IllegalStateException("Cryptography manifest must be a JSON object");
    }
    requireFields("Cryptography manifest", manifest, CRYPTOGRAPHY_MANIFEST_FIELDS);
    requireCount(
        "cryptography manifest manifestVersion",
        pin.manifestVersion(),
        requiredInteger(manifest, "manifestVersion"));
    requireEqual(
        "cryptography manifest protocolVersion",
        PROTOCOL_VERSION,
        requiredText(manifest, "protocolVersion"));
    requireEqual(
        "cryptography manifest profileId", pin.profileId(), requiredText(manifest, "profileId"));
    requireEqual(
        "cryptography manifest artifactDigest",
        pin.artifactDigest(),
        requiredText(manifest, "artifactDigest"));

    JsonNode artifacts = requiredArray(manifest, "artifacts");
    JsonNode cases = requiredArray(manifest, "cases");
    requireCount("cryptography manifest artifactCount", pin.artifactCount(), artifacts.size());
    requireCount("cryptography manifest caseCount", pin.caseCount(), cases.size());

    int evaluationCount = 0;
    for (JsonNode item : cases) {
      if (!item.isObject()) {
        throw new IllegalStateException("Cryptography manifest case must be an object");
      }
      evaluationCount += requiredArray(item, "evaluations").size();
    }
    requireCount("cryptography manifest evaluationCount", pin.evaluationCount(), evaluationCount);

    List<CryptographyArtifact> declaredArtifacts = new ArrayList<>();
    Set<String> paths = new java.util.HashSet<>();
    for (JsonNode item : artifacts) {
      if (!(item instanceof ObjectNode artifact)) {
        throw new IllegalStateException("Cryptography manifest artifact must be an object");
      }
      requireFields("Cryptography manifest artifact", artifact, CRYPTOGRAPHY_ARTIFACT_FIELDS);
      String path = requiredText(artifact, "path");
      validateRepositoryPath(path);
      if (!paths.add(path)) {
        throw new IllegalStateException("Duplicate cryptography artifact path: " + path);
      }
      long byteLength = requiredLong(artifact, "byteLength");
      String sha256 = requiredText(artifact, "sha256");
      if (!sha256.matches("sha256:[0-9a-f]{64}")) {
        throw new IllegalStateException(
            "Cryptography artifact has invalid SHA-256 identifier: " + path);
      }
      declaredArtifacts.add(new CryptographyArtifact(path, byteLength, sha256));
    }

    ObjectNode digestInput = manifest.deepCopy();
    digestInput.remove("artifactDigest");
    String actualArtifactDigest = CanonicalJson.canonicalHash(digestInput);
    if (!actualArtifactDigest.equals(pin.artifactDigest())) {
      throw new IllegalStateException(
          "cryptography artifactDigest mismatch: expected "
              + pin.artifactDigest()
              + ", got "
              + actualArtifactDigest);
    }

    for (CryptographyArtifact artifact : declaredArtifacts) {
      byte[] contents = resources.read(artifact.path());
      if (contents.length != artifact.byteLength()) {
        throw new IllegalStateException(
            artifact.path()
                + " byteLength mismatch: expected "
                + artifact.byteLength()
                + ", got "
                + contents.length);
      }
      String actualSha256 = sha256Identifier(contents);
      if (!actualSha256.equals(artifact.sha256())) {
        throw new IllegalStateException(
            artifact.path()
                + " SHA-256 mismatch: expected "
                + artifact.sha256()
                + ", got "
                + actualSha256);
      }
    }

    return new CryptographyVerification(
        pin.sourceCommit(),
        pin.profileId(),
        pin.manifestVersion(),
        actualArtifactDigest,
        declaredArtifacts.size(),
        cases.size(),
        evaluationCount);
  }

  private static void requireFields(String label, ObjectNode object, Set<String> expected) {
    Set<String> actual = new java.util.HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw new IllegalStateException(label + " fields expected " + expected + ", got " + actual);
    }
  }

  private static JsonNode requiredArray(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isArray()) {
      throw new IllegalStateException("Cryptography manifest " + field + " must be an array");
    }
    return value;
  }

  private static String requiredText(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isTextual()) {
      throw new IllegalStateException("Cryptography manifest " + field + " must be a string");
    }
    return value.textValue();
  }

  private static int requiredInteger(JsonNode object, String field) {
    long value = requiredLong(object, field);
    if (value > Integer.MAX_VALUE) {
      throw new IllegalStateException("Cryptography manifest " + field + " is too large");
    }
    return (int) value;
  }

  private static long requiredLong(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new IllegalStateException("Cryptography manifest " + field + " must be an integer");
    }
    long result = value.longValue();
    if (result < 0) {
      throw new IllegalStateException("Cryptography manifest " + field + " must be non-negative");
    }
    return result;
  }

  private static void validateRepositoryPath(String path) {
    if (!path.matches("[a-z0-9][a-z0-9._-]*(?:/[a-z0-9][a-z0-9._-]*)*")
        || path.length() > 512
        || !(path.startsWith("cryptography/") || path.startsWith("schemas/"))
        || path.equals(CRYPTOGRAPHY_PATH)
        || path.equals("cryptography/README.md")) {
      throw new IllegalStateException("Unsafe cryptography artifact path: " + path);
    }
  }

  private static byte[] readSourceResource(Path root, String logicalPath) throws IOException {
    validateRepositoryPath(logicalPath);
    return Files.readAllBytes(sourceFile(root, logicalPath, "Cryptography artifact"));
  }

  private static byte[] readSourceManifest(Path root, String logicalPath) throws IOException {
    requireEqual("cryptography manifest path", CRYPTOGRAPHY_PATH, logicalPath);
    return Files.readAllBytes(sourceFile(root, logicalPath, "Cryptography manifest"));
  }

  private static Path sourceFile(Path root, String logicalPath, String label) throws IOException {
    Path resource = root;
    for (String segment : logicalPath.split("/")) {
      resource = resource.resolve(segment);
      if (Files.isSymbolicLink(resource)) {
        throw new IllegalStateException(
            label + " must not traverse a symbolic link: " + logicalPath);
      }
    }
    resource = resource.toAbsolutePath().normalize();
    if (!resource.startsWith(root) || !Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileNotFoundException(label + " is missing: " + logicalPath);
    }
    return resource;
  }

  private static String sha256Identifier(byte[] contents) {
    return "sha256:" + HexFormat.of().formatHex(sha256().digest(contents));
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

  private static void requireCount(String field, int expected, int actual) {
    if (expected != actual) {
      throw new IllegalStateException(field + " expected " + expected + ", got " + actual);
    }
  }

  /** Successful verification details for a source or packaged protocol bundle. */
  public record Verification(
      String protocolCommit,
      String protocolVersion,
      int schemaFiles,
      int conformanceFiles,
      String bundleSha256) {}

  /** Successful verification details for the signed-document cryptography bundle. */
  public record CryptographyVerification(
      String sourceCommit,
      String profileId,
      int manifestVersion,
      String artifactDigest,
      int artifactCount,
      int caseCount,
      int evaluationCount) {}

  private record Pin(
      String repository,
      String commit,
      String protocolVersion,
      String wireNamespace,
      CryptographyPin cryptography,
      Map<String, Artifact> artifacts,
      String bundleSha256) {}

  private record CryptographyPin(
      String path,
      String sourceCommit,
      String profileId,
      int manifestVersion,
      String artifactDigest,
      int artifactCount,
      int caseCount,
      int evaluationCount) {}

  private record Artifact(String path, int files, String sha256) {}

  private record CryptographyArtifact(String path, long byteLength, String sha256) {}

  @FunctionalInterface
  private interface ResourceReader {
    byte[] read(String path) throws IOException;
  }
}
