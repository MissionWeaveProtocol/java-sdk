package org.missionweaveprotocol.sdk;

/** Completeness asserted by a Registry acquisition adapter for supplied snapshot evidence. */
public enum KeyRegistryCompleteness {
  /** The adapter asserts Organization-wide bindings and complete retained history. */
  ORGANIZATION_WIDE,

  /** The adapter reports that the evidence may omit bindings or retained history. */
  PARTIAL,

  /** The adapter makes no completeness assertion about the evidence. */
  UNSPECIFIED
}
