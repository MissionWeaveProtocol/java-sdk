package org.missionweaveprotocol.sdk;

/** Stable protected reason for one Admission-stage rejection. */
public enum AdmissionReason {
  RECORD_MISSING("record-missing"),
  RECORD_BINDING_MISMATCH("record-binding-mismatch"),
  TRUSTED_TIME_OUTSIDE_KEY_INTERVAL("trusted-time-outside-key-interval"),
  MALFORMED_TRUSTED_TIME("malformed-trusted-time"),
  RECORD_CONFLICT("record-conflict"),
  RECORD_SCHEMA_INVALID("record-schema-invalid"),
  LOG_AUTHENTICATION_FAILED("log-authentication-failed"),
  APPEND_INTEGRITY_NOT_ESTABLISHED("append-integrity-not-established"),
  LOG_UNAVAILABLE("log-unavailable"),
  LOG_INDETERMINATE("log-indeterminate"),
  COMMIT_FAILED("commit-failed"),
  EVENT_SELF_ANCHORING("event-self-anchoring");

  private final String id;

  AdmissionReason(String id) {
    this.id = id;
  }

  /** Stable protected identifier from the Admission profile. */
  public String id() {
    return id;
  }
}
