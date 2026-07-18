package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Strict UTF-8 JSON parsing with duplicate-member and trailing-data rejection. */
public final class StrictJson {
  private static final ObjectMapper MAPPER =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  private StrictJson() {}

  /** Parse exactly one UTF-8 JSON value. */
  public static JsonNode parse(byte[] document) throws IOException {
    Objects.requireNonNull(document, "document");
    if (document.length >= 3
        && document[0] == (byte) 0xef
        && document[1] == (byte) 0xbb
        && document[2] == (byte) 0xbf) {
      throw new IOException("JSON document starts with a UTF-8 byte-order mark");
    }
    String decoded =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(document))
            .toString();
    return parse(decoded);
  }

  /** Parse exactly one JSON value from a Java string. */
  public static JsonNode parse(String document) throws IOException {
    Objects.requireNonNull(document, "document");
    try (JsonParser parser = MAPPER.createParser(document)) {
      return readOne(parser);
    }
  }

  /** Serialize a JSON tree without insignificant whitespace. */
  public static byte[] write(JsonNode value) throws IOException {
    Objects.requireNonNull(value, "value");
    if (value.isMissingNode()) {
      throw new IllegalArgumentException("MissingNode is not a JSON value");
    }
    return MAPPER.writeValueAsBytes(value);
  }

  static ObjectMapper mapper() {
    return MAPPER;
  }

  private static JsonNode readOne(JsonParser parser) throws IOException {
    JsonNode value = MAPPER.readTree(parser);
    if (value == null) {
      throw new EOFException("JSON document is empty");
    }
    if (parser.nextToken() != null) {
      throw new IOException("JSON document contains trailing data");
    }
    return value;
  }
}
