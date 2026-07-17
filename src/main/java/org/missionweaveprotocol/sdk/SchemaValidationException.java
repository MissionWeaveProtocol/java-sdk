package org.missionweaveprotocol.sdk;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/** A strict JSON document did not satisfy one named normative schema. */
public final class SchemaValidationException extends IllegalArgumentException {
  @Serial private static final long serialVersionUID = 1L;

  private final String schemaName;
  private final String[] errors;

  SchemaValidationException(String schemaName, List<String> errors) {
    super(message(schemaName, errors));
    this.schemaName = Objects.requireNonNull(schemaName, "schemaName");
    this.errors = errors.toArray(String[]::new);
  }

  /** Normalized schema filename. */
  public String schemaName() {
    return schemaName;
  }

  /** Stable validation error details. */
  public List<String> errors() {
    return List.of(errors.clone());
  }

  private static String message(String schemaName, List<String> errors) {
    Objects.requireNonNull(schemaName, "schemaName");
    Objects.requireNonNull(errors, "errors");
    String detail = errors.isEmpty() ? "unknown validation error" : errors.getFirst();
    return schemaName + " validation failed: " + detail;
  }
}
