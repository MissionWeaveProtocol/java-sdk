package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import org.erdtman.jcs.JsonCanonicalizer;

/** RFC 8785 JSON Canonicalization Scheme and SHA-256 helpers. */
public final class CanonicalJson {
  private static final byte[] WRAPPER_PREFIX = "{\"\":".getBytes(StandardCharsets.UTF_8);

  private CanonicalJson() {}

  /** Strictly parse and canonicalize one UTF-8 JSON value. */
  public static byte[] canonicalize(byte[] document) throws IOException {
    return canonicalize(StrictJson.parse(document));
  }

  /** Strictly parse and canonicalize one JSON value. */
  public static byte[] canonicalize(String document) throws IOException {
    return canonicalize(StrictJson.parse(document));
  }

  /** Canonicalize a JSON tree according to RFC 8785. */
  public static byte[] canonicalize(JsonNode value) throws IOException {
    Objects.requireNonNull(value, "value");
    validateIJson(value);
    if (value.isContainerNode()) {
      return new JsonCanonicalizer(StrictJson.write(value)).getEncodedUTF8();
    }

    ObjectNode wrapper = StrictJson.mapper().createObjectNode();
    wrapper.set("", value);
    byte[] canonicalWrapper = new JsonCanonicalizer(StrictJson.write(wrapper)).getEncodedUTF8();
    if (!startsWith(canonicalWrapper, WRAPPER_PREFIX)
        || canonicalWrapper[canonicalWrapper.length - 1] != '}') {
      throw new IOException("Unable to canonicalize top-level JSON value");
    }
    return Arrays.copyOfRange(canonicalWrapper, WRAPPER_PREFIX.length, canonicalWrapper.length - 1);
  }

  /** Canonicalize a JSON tree and return UTF-8 text. */
  public static String canonicalString(JsonNode value) throws IOException {
    return new String(canonicalize(value), StandardCharsets.UTF_8);
  }

  /** Return the raw SHA-256 digest of arbitrary bytes. */
  public static byte[] sha256(byte[] value) {
    return newDigest().digest(Objects.requireNonNull(value, "value"));
  }

  /** Return a lowercase SHA-256 digest of arbitrary bytes. */
  public static String sha256Hex(byte[] value) {
    return HexFormat.of().formatHex(sha256(value));
  }

  /** Return a {@code sha256:} content identifier over canonical JSON. */
  public static String canonicalHash(byte[] document) throws IOException {
    return "sha256:" + sha256Hex(canonicalize(document));
  }

  /** Return a {@code sha256:} content identifier over a JSON tree. */
  public static String canonicalHash(JsonNode value) throws IOException {
    return "sha256:" + sha256Hex(canonicalize(value));
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException error) {
      throw new AssertionError("SHA-256 is required by the Java platform", error);
    }
  }

  private static void validateIJson(JsonNode value) {
    if (value.isMissingNode()) {
      throw new IllegalArgumentException("MissingNode is not a JSON value");
    }
    if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) {
      throw new IllegalArgumentException("Canonical JSON numbers must be finite");
    }
    if (value.isTextual()) {
      requireValidUnicode(value.textValue());
    }
    if (value.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        requireValidUnicode(field.getKey());
        validateIJson(field.getValue());
      }
    } else if (value.isArray()) {
      value.forEach(CanonicalJson::validateIJson);
    }
  }

  private static void requireValidUnicode(String value) {
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isHighSurrogate(current)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new IllegalArgumentException("Canonical JSON strings must contain valid Unicode");
        }
        index++;
      } else if (Character.isLowSurrogate(current)) {
        throw new IllegalArgumentException("Canonical JSON strings must contain valid Unicode");
      }
    }
  }

  private static boolean startsWith(byte[] value, byte[] prefix) {
    if (value.length < prefix.length) {
      return false;
    }
    for (int index = 0; index < prefix.length; index++) {
      if (value[index] != prefix[index]) {
        return false;
      }
    }
    return true;
  }
}
