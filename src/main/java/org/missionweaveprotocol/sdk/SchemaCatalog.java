package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Draft202012;
import com.networknt.schema.format.Format;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** One fully resolved, offline Draft 2020-12 catalog of normative protocol schemas. */
public final class SchemaCatalog {
  private static final String SCHEMA_PREFIX = "schemas/";
  private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
  private static final Format DATE_TIME_FORMAT =
      new Format() {
        @Override
        public String getName() {
          return "date-time";
        }

        @Override
        public String getMessageKey() {
          return "format.date-time";
        }

        @Override
        public boolean matches(ExecutionContext executionContext, String value) {
          try {
            ExactInstant.parse(value);
            return true;
          } catch (IllegalArgumentException error) {
            return false;
          }
        }
      };

  private final Map<String, Schema> schemas;

  private SchemaCatalog(Map<String, Schema> schemas) {
    this.schemas = Map.copyOf(schemas);
  }

  /** Compile the schemas packaged with this SDK. */
  public static SchemaCatalog packaged() throws IOException {
    return packaged(SchemaCatalog.class.getClassLoader());
  }

  /** Compile packaged schemas visible to a specific class loader. */
  public static SchemaCatalog packaged(ClassLoader classLoader) throws IOException {
    Objects.requireNonNull(classLoader, "classLoader");
    Map<String, byte[]> documents = new LinkedHashMap<>();
    for (String resource : ProtocolBundle.resourcePaths(classLoader)) {
      if (!resource.startsWith(SCHEMA_PREFIX) || !resource.endsWith(".json")) {
        continue;
      }
      String name = resource.substring(SCHEMA_PREFIX.length());
      try (InputStream input = classLoader.getResourceAsStream(resource)) {
        if (input == null) {
          throw new FileNotFoundException("Packaged schema is missing: " + resource);
        }
        documents.put(name, input.readAllBytes());
      }
    }
    return compile(documents);
  }

  /** Compile schemas from {@code bundleRoot/schemas}. */
  public static SchemaCatalog from(Path bundleRoot) throws IOException {
    Path root = Objects.requireNonNull(bundleRoot, "bundleRoot").toAbsolutePath().normalize();
    Path schemaRoot = root.resolve("schemas");
    if (!Files.isDirectory(schemaRoot)) {
      throw new FileNotFoundException("Schema directory is missing: " + schemaRoot);
    }

    List<Path> paths;
    try (Stream<Path> candidates = Files.list(schemaRoot)) {
      paths =
          candidates
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted(Comparator.comparing(path -> path.getFileName().toString()))
              .toList();
    }
    Map<String, byte[]> documents = new LinkedHashMap<>();
    for (Path path : paths) {
      documents.put(path.getFileName().toString(), Files.readAllBytes(path));
    }
    return compile(documents);
  }

  /** Strictly parse and validate a UTF-8 document against a named schema. */
  public void validate(String schemaName, byte[] document) throws IOException {
    validate(schemaName, StrictJson.parse(document));
  }

  /** Validate a JSON tree against a named schema. */
  public void validate(String schemaName, JsonNode document) {
    String name = normalizeSchemaName(schemaName);
    Schema schema = schemas.get(name);
    if (schema == null) {
      throw new IllegalArgumentException("Unknown schema: " + schemaName);
    }
    List<String> errors =
        schema.validate(Objects.requireNonNull(document, "document")).stream()
            .map(com.networknt.schema.Error::toString)
            .sorted()
            .toList();
    if (!errors.isEmpty()) {
      throw new SchemaValidationException(name, errors);
    }
  }

  /** Names of all compiled normative schemas. */
  public Set<String> schemaNames() {
    return schemas.keySet();
  }

  private static SchemaCatalog compile(Map<String, byte[]> documents) throws IOException {
    if (documents.isEmpty()) {
      throw new IllegalArgumentException("Schema source contains no JSON schemas");
    }

    Map<String, String> schemaDataById = new LinkedHashMap<>();
    Map<String, String> schemaIdByName = new LinkedHashMap<>();
    for (Map.Entry<String, byte[]> entry : documents.entrySet()) {
      String name = normalizeSchemaName(entry.getKey());
      JsonNode schemaNode = StrictJson.parse(entry.getValue());
      if (!schemaNode.isObject()) {
        throw new IllegalArgumentException("Schema is not a JSON object: " + name);
      }
      JsonNode dialect = schemaNode.get("$schema");
      if (dialect == null || !DRAFT_2020_12.equals(dialect.textValue())) {
        throw new IllegalArgumentException("Schema does not declare Draft 2020-12: " + name);
      }
      JsonNode identifier = schemaNode.get("$id");
      if (identifier == null || !identifier.isTextual() || identifier.textValue().isBlank()) {
        throw new IllegalArgumentException("Schema is missing $id: " + name);
      }

      String id = identifier.textValue();
      if (schemaDataById.putIfAbsent(id, new String(entry.getValue(), StandardCharsets.UTF_8))
          != null) {
        throw new IllegalArgumentException("Duplicate schema $id: " + id);
      }
      if (schemaIdByName.putIfAbsent(name, id) != null) {
        throw new IllegalArgumentException("Duplicate schema name: " + name);
      }
    }

    SchemaRegistryConfig config =
        SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build();
    Dialect dialect = Dialect.builder(Draft202012.getInstance()).format(DATE_TIME_FORMAT).build();
    SchemaRegistry registry =
        SchemaRegistry.withDefaultDialect(
            dialect,
            builder ->
                builder
                    .schemas(schemaDataById)
                    .schemaRegistryConfig(config)
                    .nodeReader(nodeReader -> nodeReader.jsonMapper(StrictJson.mapper())));

    Map<String, Schema> compiled = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : schemaIdByName.entrySet()) {
      compiled.put(entry.getKey(), registry.getSchema(SchemaLocation.of(entry.getValue())));
    }
    return new SchemaCatalog(compiled);
  }

  private static String normalizeSchemaName(String schemaName) {
    Objects.requireNonNull(schemaName, "schemaName");
    String name =
        schemaName.startsWith(SCHEMA_PREFIX)
            ? schemaName.substring(SCHEMA_PREFIX.length())
            : schemaName;
    if (name.isBlank()
        || name.contains("/")
        || name.contains("\\")
        || !name.endsWith(".schema.json")) {
      throw new IllegalArgumentException("Invalid schema name: " + schemaName);
    }
    return name;
  }
}
