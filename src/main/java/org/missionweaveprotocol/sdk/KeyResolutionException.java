package org.missionweaveprotocol.sdk;

import java.io.Serial;

/**
 * Checked failure to acquire Registry evidence or adapt it into the Java SDK representation.
 *
 * <p>This reports Registry acquisition or adapter failures, not malformed-evidence validation.
 */
public final class KeyResolutionException extends Exception {
  @Serial private static final long serialVersionUID = 1L;

  /** Creates a Registry acquisition or adapter failure with the supplied message. */
  public KeyResolutionException(String message) {
    super(message);
  }

  /** Creates a Registry acquisition or adapter failure with the supplied message and cause. */
  public KeyResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
