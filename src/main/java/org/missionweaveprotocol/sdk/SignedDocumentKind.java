package org.missionweaveprotocol.sdk;

/** The fixed MissionWeaveProtocol 0.1 Signed Document profiles. */
public enum SignedDocumentKind {
  AGENT_CARD("agent-card", "agent-card.schema.json", "/issuedAt", null, true),
  APPROVAL("approval", "approval.schema.json", "/occurredAt", "/approver", false),
  ARTIFACT("artifact", "artifact.schema.json", "/createdAt", "/producer/agentId", false),
  COMMAND("command", "command.schema.json", "/issuedAt", "/actor", false),
  CONTEXT_PACKAGE(
      "context-package", "context-package.schema.json", "/generatedAt", "/generatedBy", false),
  EVENT("event", "event.schema.json", "/occurredAt", "/acceptedBy", false),
  EVIDENCE("evidence", "evidence.schema.json", "/createdAt", "/generatedBy", false),
  EXTENSION_PROFILE(
      "extension-profile", "extension-profile.schema.json", "/approvedAt", "/approvedBy", false),
  GROUP_SNAPSHOT("group-snapshot", "group-snapshot.schema.json", "/createdAt", "/createdBy", false);

  private final String id;
  private final String schemaName;
  private final String protectedTimePointer;
  private final String signerPointer;
  private final boolean servicePrincipalRequired;

  SignedDocumentKind(
      String id,
      String schemaName,
      String protectedTimePointer,
      String signerPointer,
      boolean servicePrincipalRequired) {
    this.id = id;
    this.schemaName = schemaName;
    this.protectedTimePointer = protectedTimePointer;
    this.signerPointer = signerPointer;
    this.servicePrincipalRequired = servicePrincipalRequired;
  }

  /** Stable profile identifier used by the cryptography bundle. */
  public String id() {
    return id;
  }

  String schemaName() {
    return schemaName;
  }

  String protectedTimePointer() {
    return protectedTimePointer;
  }

  String signerPointer() {
    return signerPointer;
  }

  boolean servicePrincipalRequired() {
    return servicePrincipalRequired;
  }
}
