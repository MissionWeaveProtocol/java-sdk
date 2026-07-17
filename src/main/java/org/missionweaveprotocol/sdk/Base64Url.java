package org.missionweaveprotocol.sdk;

import java.util.Base64;
import java.util.Objects;

/** Unpadded RFC 4648 base64url encoding used by MissionWeaveProtocol keys and signatures. */
public final class Base64Url {
  private Base64Url() {}

  /** Encode bytes without padding. */
  public static String encode(byte[] value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Objects.requireNonNull(value, "value"));
  }

  /** Decode an unpadded base64url value. */
  public static byte[] decode(String value) {
    Objects.requireNonNull(value, "value");
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      boolean valid =
          (character >= 'A' && character <= 'Z')
              || (character >= 'a' && character <= 'z')
              || (character >= '0' && character <= '9')
              || character == '-'
              || character == '_';
      if (!valid) {
        throw new IllegalArgumentException("Value is not unpadded base64url");
      }
    }
    try {
      return Base64.getUrlDecoder().decode(value);
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Value is not unpadded base64url", error);
    }
  }
}
