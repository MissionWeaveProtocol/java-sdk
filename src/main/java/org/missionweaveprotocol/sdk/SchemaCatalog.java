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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** One fully resolved, offline Draft 2020-12 catalog of normative protocol schemas. */
public final class SchemaCatalog {
  private static final String SCHEMA_PREFIX = "schemas/";
  private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
  private static final Pattern URI_SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:");
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
  private static final Format URI_FORMAT =
      new Format() {
        @Override
        public String getName() {
          return "uri";
        }

        @Override
        public String getMessageKey() {
          return "format.uri";
        }

        @Override
        public boolean matches(ExecutionContext executionContext, String value) {
          return isProtocolUri(value);
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
    Dialect dialect =
        Dialect.builder(Draft202012.getInstance())
            .format(DATE_TIME_FORMAT)
            .format(URI_FORMAT)
            .build();
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

  static boolean isProtocolUri(String value) {
    if (!isVisibleAscii(value) || !hasValidPercentTriplets(value)) {
      return false;
    }
    var scheme = URI_SCHEME.matcher(value);
    if (!scheme.lookingAt()) {
      return false;
    }

    int colon = scheme.end() - 1;
    int fragmentDelimiter = value.indexOf('#', colon + 1);
    int beforeFragmentEnd = fragmentDelimiter == -1 ? value.length() : fragmentDelimiter;
    int queryDelimiter = value.indexOf('?', colon + 1);
    boolean hasQuery = queryDelimiter != -1 && queryDelimiter < beforeFragmentEnd;
    int hierPartEnd = hasQuery ? queryDelimiter : beforeFragmentEnd;
    if (!isValidHierPart(value.substring(colon + 1, hierPartEnd))) {
      return false;
    }
    if (hasQuery
        && !isValidQueryOrFragment(value.substring(queryDelimiter + 1, beforeFragmentEnd))) {
      return false;
    }
    return fragmentDelimiter == -1
        || isValidQueryOrFragment(value.substring(fragmentDelimiter + 1));
  }

  private static boolean hasValidPercentTriplets(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) != '%') {
        continue;
      }
      if (index + 2 >= value.length()
          || !isHexDigit(value.charAt(index + 1))
          || !isHexDigit(value.charAt(index + 2))) {
        return false;
      }
      index += 2;
    }
    return true;
  }

  private static boolean isValidHierPart(String hierPart) {
    if (hierPart.startsWith("//")) {
      int pathStart = hierPart.indexOf('/', 2);
      String authority = pathStart == -1 ? hierPart.substring(2) : hierPart.substring(2, pathStart);
      String path = pathStart == -1 ? "" : hierPart.substring(pathStart);
      return isValidAuthority(authority) && isValidPathAbempty(path);
    }
    if (hierPart.isEmpty()) {
      return true;
    }
    return hierPart.charAt(0) == '/'
        ? isValidPathAbsolute(hierPart)
        : isValidPathRootless(hierPart);
  }

  private static boolean isValidAuthority(String authority) {
    String hostPort = authority;
    int at = authority.indexOf('@');
    if (at != -1) {
      if (authority.indexOf('@', at + 1) != -1
          || !componentMatches(
              authority.substring(0, at),
              character ->
                  isUnreserved(character) || isSubDelimiter(character) || character == ':')) {
        return false;
      }
      hostPort = authority.substring(at + 1);
    }

    if (hostPort.startsWith("[")) {
      int close = hostPort.indexOf(']');
      if (close <= 1
          || hostPort.indexOf('[', 1) != -1
          || hostPort.indexOf(']', close + 1) != -1
          || !isIpLiteral(hostPort.substring(1, close))) {
        return false;
      }
      String remainder = hostPort.substring(close + 1);
      return remainder.isEmpty()
          || (remainder.charAt(0) == ':' && isValidPort(remainder.substring(1)));
    }

    if (hostPort.indexOf('[') != -1 || hostPort.indexOf(']') != -1) {
      return false;
    }

    String host = hostPort;
    String port = "";
    int colon = hostPort.indexOf(':');
    if (colon != -1) {
      if (hostPort.indexOf(':', colon + 1) != -1) {
        return false;
      }
      host = hostPort.substring(0, colon);
      port = hostPort.substring(colon + 1);
    }
    return componentMatches(host, character -> isUnreserved(character) || isSubDelimiter(character))
        && isValidPort(port);
  }

  private static boolean isIpLiteral(String value) {
    return isIpvFuture(value) || isIpv6Address(value);
  }

  private static boolean isIpvFuture(String value) {
    if (value.length() < 4 || (value.charAt(0) != 'v' && value.charAt(0) != 'V')) {
      return false;
    }
    int dot = value.indexOf('.', 1);
    if (dot <= 1 || dot + 1 == value.length()) {
      return false;
    }
    for (int index = 1; index < dot; index++) {
      if (!isHexDigit(value.charAt(index))) {
        return false;
      }
    }
    for (int index = dot + 1; index < value.length(); index++) {
      int character = value.charAt(index);
      if (!isUnreserved(character) && !isSubDelimiter(character) && character != ':') {
        return false;
      }
    }
    return true;
  }

  private static boolean isIpv6Address(String value) {
    if (value.indexOf(':') == -1 || value.indexOf('%') != -1) {
      return false;
    }
    String embeddedIpv4 = value.substring(value.lastIndexOf(':') + 1);
    if (embeddedIpv4.indexOf('.') != -1 && !isIpv4Address(embeddedIpv4)) {
      return false;
    }
    try {
      InetAddress.getByName(value);
      return true;
    } catch (UnknownHostException error) {
      return false;
    }
  }

  private static boolean isIpv4Address(String value) {
    String[] octets = value.split("\\.", -1);
    if (octets.length != 4) {
      return false;
    }
    for (String octet : octets) {
      if (octet.isEmpty() || (octet.length() > 1 && octet.charAt(0) == '0')) {
        return false;
      }
      for (int index = 0; index < octet.length(); index++) {
        if (!isDigit(octet.charAt(index))) {
          return false;
        }
      }
      if (octet.length() > 3 || Integer.parseInt(octet) > 255) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidPort(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (!isDigit(value.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidPathAbempty(String value) {
    return (value.isEmpty() || value.charAt(0) == '/')
        && componentMatches(value, character -> isPchar(character) || character == '/');
  }

  private static boolean isValidPathAbsolute(String value) {
    if (value.isEmpty() || value.charAt(0) != '/') {
      return false;
    }
    if (value.length() == 1) {
      return true;
    }
    return value.charAt(1) != '/'
        && componentMatches(
            value.substring(1), character -> isPchar(character) || character == '/');
  }

  private static boolean isValidPathRootless(String value) {
    return !value.isEmpty()
        && value.charAt(0) != '/'
        && componentMatches(value, character -> isPchar(character) || character == '/');
  }

  private static boolean isValidQueryOrFragment(String value) {
    return componentMatches(
        value, character -> isPchar(character) || character == '/' || character == '?');
  }

  private static boolean componentMatches(String value, IntPredicate allowed) {
    for (int index = 0; index < value.length(); ) {
      int character = value.charAt(index);
      if (character == '%') {
        if (index + 2 >= value.length()
            || !isHexDigit(value.charAt(index + 1))
            || !isHexDigit(value.charAt(index + 2))) {
          return false;
        }
        index += 3;
        continue;
      }
      if (!allowed.test(character)) {
        return false;
      }
      index++;
    }
    return true;
  }

  private static boolean isPchar(int value) {
    return isUnreserved(value) || isSubDelimiter(value) || value == ':' || value == '@';
  }

  private static boolean isUnreserved(int value) {
    return isAlpha(value)
        || isDigit(value)
        || value == '-'
        || value == '.'
        || value == '_'
        || value == '~';
  }

  private static boolean isSubDelimiter(int value) {
    return value == '!'
        || value == '$'
        || value == '&'
        || value == '\''
        || value == '('
        || value == ')'
        || value == '*'
        || value == '+'
        || value == ','
        || value == ';'
        || value == '=';
  }

  private static boolean isAlpha(int value) {
    return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
  }

  private static boolean isDigit(int value) {
    return value >= '0' && value <= '9';
  }

  private static boolean isHexDigit(int value) {
    return isDigit(value) || (value >= 'A' && value <= 'F') || (value >= 'a' && value <= 'f');
  }

  private static boolean isVisibleAscii(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character < 0x21 || character > 0x7e) {
        return false;
      }
    }
    return true;
  }
}
