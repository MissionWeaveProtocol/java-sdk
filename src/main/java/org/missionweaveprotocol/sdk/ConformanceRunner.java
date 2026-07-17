package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Runs the implementation-neutral schema conformance manifest. */
public final class ConformanceRunner {
  private static final String MANIFEST_PATH = "conformance/manifest.json";

  private ConformanceRunner() {}

  /** Run all vectors packaged with the SDK. */
  public static ConformanceReport runPackaged() throws IOException {
    return runPackaged(ConformanceRunner.class.getClassLoader());
  }

  /** Run packaged vectors visible to a specific class loader. */
  public static ConformanceReport runPackaged(ClassLoader classLoader) throws IOException {
    Objects.requireNonNull(classLoader, "classLoader");
    SchemaCatalog catalog = SchemaCatalog.packaged(classLoader);
    return run(
        catalog,
        resource -> {
          try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
              throw new FileNotFoundException("Packaged protocol resource is missing: " + resource);
            }
            return input.readAllBytes();
          }
        });
  }

  /** Run vectors from a protocol repository or release-bundle root. */
  public static ConformanceReport run(Path bundleRoot) throws IOException {
    Path root = Objects.requireNonNull(bundleRoot, "bundleRoot").toAbsolutePath().normalize();
    SchemaCatalog catalog = SchemaCatalog.from(root);
    return run(catalog, resource -> Files.readAllBytes(resolveResource(root, resource)));
  }

  private static ConformanceReport run(SchemaCatalog catalog, ResourceReader resources)
      throws IOException {
    JsonNode manifest = StrictJson.parse(resources.read(MANIFEST_PATH));
    if (!manifest.isArray() || manifest.isEmpty()) {
      throw new IllegalArgumentException("Conformance manifest must be a non-empty JSON array");
    }

    List<VectorResult> results = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (int index = 0; index < manifest.size(); index++) {
      JsonNode entry = manifest.get(index);
      if (!entry.isObject()) {
        throw new IllegalArgumentException(
            "Conformance manifest entry " + index + " is not an object");
      }
      String name = requiredText(entry, "name", index);
      String schema = requiredText(entry, "schema", index);
      String instance = requiredText(entry, "instance", index);
      JsonNode valid = entry.get("valid");
      if (valid == null || !valid.isBoolean()) {
        throw new IllegalArgumentException(
            "Conformance manifest entry " + index + " has no boolean valid field");
      }
      if (!names.add(name)) {
        throw new IllegalArgumentException("Duplicate conformance vector name: " + name);
      }

      byte[] document = resources.read(requireProtocolPath(instance));
      boolean actualValid;
      String error = null;
      try {
        catalog.validate(schema, document);
        actualValid = true;
      } catch (SchemaValidationException | IOException validationError) {
        actualValid = false;
        error = validationError.getMessage();
      }
      results.add(new VectorResult(name, valid.booleanValue(), actualValid, error));
    }
    return new ConformanceReport(results);
  }

  private static String requiredText(JsonNode entry, String field, int index) {
    JsonNode value = entry.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException(
          "Conformance manifest entry " + index + " has no " + field);
    }
    return value.textValue();
  }

  private static Path resolveResource(Path root, String resource) {
    String normalized = requireProtocolPath(resource);
    Path resolved = root.resolve(normalized).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Protocol resource escapes bundle root: " + resource);
    }
    return resolved;
  }

  private static String requireProtocolPath(String resource) {
    Objects.requireNonNull(resource, "resource");
    if (resource.isBlank()
        || resource.startsWith("/")
        || resource.contains("\\")
        || resource.equals("..")
        || resource.startsWith("../")
        || resource.contains("/../")
        || resource.endsWith("/..")) {
      throw new IllegalArgumentException("Invalid protocol resource path: " + resource);
    }
    return resource;
  }

  @FunctionalInterface
  private interface ResourceReader {
    byte[] read(String resource) throws IOException;
  }
}
