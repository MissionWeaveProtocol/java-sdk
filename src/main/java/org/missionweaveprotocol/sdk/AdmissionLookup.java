package org.missionweaveprotocol.sdk;

import java.util.Objects;

/** Authenticated Admission Log lookup result. */
public sealed interface AdmissionLookup
    permits AdmissionLookup.Found, AdmissionLookup.AuthoritativeAbsence {
  /** One authenticated record was found. */
  record Found(AuthenticatedAdmissionRecord record) implements AdmissionLookup {
    public Found {
      Objects.requireNonNull(record, "record");
    }
  }

  /** The log authoritatively established that no record exists. */
  record AuthoritativeAbsence() implements AdmissionLookup {}
}
