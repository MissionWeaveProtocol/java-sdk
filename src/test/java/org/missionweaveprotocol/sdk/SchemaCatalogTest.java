package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaCatalogTest {
  @TempDir Path temporaryDirectory;

  @Test
  void validatesPackagedDocumentsAndFormatAssertions() throws IOException {
    SchemaCatalog catalog = SchemaCatalog.packaged();
    byte[] valid = resource("conformance/vectors/valid/command.json");
    byte[] invalidTime =
        new String(valid, StandardCharsets.UTF_8)
            .replace("2026-07-15T00:00:00Z", "not-a-date-time")
            .getBytes(StandardCharsets.UTF_8);

    catalog.validate("command.schema.json", valid);

    assertEquals(21, catalog.schemaNames().size());
    assertThrows(
        SchemaValidationException.class,
        () -> catalog.validate("schemas/command.schema.json", invalidTime));
    assertThrows(
        IllegalArgumentException.class, () -> catalog.validate("unknown.schema.json", valid));
  }

  @Test
  void acceptsTheFullRfc3339TimestampProfile() throws IOException {
    SchemaCatalog catalog = SchemaCatalog.packaged();
    String valid =
        new String(resource("conformance/vectors/valid/command.json"), StandardCharsets.UTF_8);
    byte[] lowercaseTime =
        valid
            .replace("2026-07-15T00:00:00Z", "2026-07-15t00:00:00Z")
            .getBytes(StandardCharsets.UTF_8);
    byte[] arbitraryPrecisionTime =
        valid
            .replace("2026-07-15T00:00:00Z", "2026-07-15T00:00:00.1234560000000000001Z")
            .getBytes(StandardCharsets.UTF_8);

    catalog.validate("command.schema.json", lowercaseTime);
    catalog.validate("command.schema.json", arbitraryPrecisionTime);
  }

  @Test
  void usesStrictJsonForInputDocuments() throws IOException {
    SchemaCatalog catalog = SchemaCatalog.packaged();

    assertThrows(
        IOException.class,
        () ->
            catalog.validate(
                "group.schema.json",
                "{\"id\":\"urn:x:1\",\"id\":\"urn:x:2\"}".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void unresolvedReferencesFailOffline() throws IOException {
    Path schemas = Files.createDirectories(temporaryDirectory.resolve("schemas"));
    Files.writeString(
        schemas.resolve("offline.schema.json"),
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "https://missionweaveprotocol.dev/schemas/0.1/offline.schema.json",
          "$ref": "https://network-access.invalid/remote.schema.json"
        }
        """,
        StandardCharsets.UTF_8);

    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () -> {
          SchemaCatalog catalog = SchemaCatalog.from(temporaryDirectory);
          assertThrows(
              RuntimeException.class,
              () -> catalog.validate("offline.schema.json", "{}".getBytes(StandardCharsets.UTF_8)));
        });
  }

  private static byte[] resource(String path) throws IOException {
    try (var input = SchemaCatalogTest.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }
}
