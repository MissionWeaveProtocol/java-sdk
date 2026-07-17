package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Objects;

/** Strict, schema-validating codec for generic MissionWeaveProtocol WebSocket frames. */
public final class FrameCodec {
  private static final String FRAME_SCHEMA = "websocket-frame.schema.json";

  private final SchemaCatalog schemas;

  /** Construct a codec using the normative schemas packaged with the SDK. */
  public FrameCodec() throws IOException {
    this(SchemaCatalog.packaged());
  }

  /** Construct a codec over an explicit schema catalog. */
  public FrameCodec(SchemaCatalog schemas) {
    this.schemas = Objects.requireNonNull(schemas, "schemas");
  }

  /** Strictly parse and validate one UTF-8 WebSocket frame. */
  public ObjectNode decode(byte[] document) throws IOException {
    JsonNode frame = StrictJson.parse(document);
    if (!(frame instanceof ObjectNode object)) {
      throw new IllegalArgumentException("WebSocket frame must be a JSON object");
    }
    schemas.validate(FRAME_SCHEMA, object);
    return object;
  }

  /** Strictly parse and validate one WebSocket frame. */
  public ObjectNode decode(String document) throws IOException {
    JsonNode frame = StrictJson.parse(document);
    if (!(frame instanceof ObjectNode object)) {
      throw new IllegalArgumentException("WebSocket frame must be a JSON object");
    }
    schemas.validate(FRAME_SCHEMA, object);
    return object;
  }

  /** Validate and canonically encode one generic WebSocket frame. */
  public byte[] encode(JsonNode frame) throws IOException {
    if (frame == null || !frame.isObject()) {
      throw new IllegalArgumentException("WebSocket frame must be a JSON object");
    }
    schemas.validate(FRAME_SCHEMA, frame);
    return CanonicalJson.canonicalize(frame);
  }
}
