package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignedDocumentCodecRegistryHistoryTest {
  private static final String GOLDEN_COMMAND =
      "cryptography/vectors/signed-documents/valid/command.json";
  private static final String VALID_REGISTRY = "cryptography/keys/registry-valid.json";
  private static final String SELECTED_KEY_ID =
      "urn:missionweaveprotocol:key:crypto-vector-rfc8032-1";

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidUnselectedHistories")
  void rejectsInvalidHistoryInUnselectedBindingBeforeSelectedKeyUse(
      String description, Consumer<ObjectNode> mutation, String expectedReason) throws Exception {
    ObjectNode registry = validRegistry();
    mutation.accept(unselectedBinding(registry));

    assertRegistryFails(writeRegistry(registry), expectedReason);
  }

  @Test
  void rejectsChangedValidFromOnRepeatedUnselectedBinding() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode duplicate = unselectedBinding(registry).deepCopy();
    duplicate.put("validFrom", "2026-07-02T00:00:00Z");
    bindings(registry).add(duplicate);

    assertRegistryFails(writeRegistry(registry), "Registry reuses a key ID for another binding");
  }

  @Test
  void acceptsSemanticValidFromAliasOnRepeatedUnselectedBinding() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode duplicate = unselectedBinding(registry).deepCopy();
    duplicate.put("validFrom", "2026-06-30T16:00:00-08:00");
    bindings(registry).add(duplicate);

    VerifiedSignedDocument verified = verifyAccepted(registry);

    assertEquals(SELECTED_KEY_ID, verified.resolvedKey().keyId());
  }

  @Test
  void retainsFirstSelectedTimestampTextAcrossSemanticAliases() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode selected = selectedBinding(registry);
    ObjectNode firstStatus = (ObjectNode) history(selected).get(0);
    firstStatus.put("revokedAt", "2026-07-17T00:00:00Z");

    ObjectNode duplicate = selected.deepCopy();
    duplicate.put("validFrom", "2026-07-15T00:00:00.000Z");
    ObjectNode duplicateStatus = (ObjectNode) history(duplicate).get(0);
    duplicateStatus.put("recordedAt", "2026-07-15T16:00:00-08:00");
    duplicateStatus.put("validUntil", "2026-07-15T16:00:00-08:00");
    duplicateStatus.put("revokedAt", "2026-07-16T16:00:00-08:00");
    bindings(registry).add(duplicate);

    ResolvedKey resolved = verifyAccepted(registry).resolvedKey();

    assertEquals("2026-07-15T08:00:00+08:00", resolved.validFrom());
    assertEquals("2026-07-16T00:00:00Z", resolved.validUntil());
    assertEquals("2026-07-17T00:00:00Z", resolved.revokedAt());
  }

  @Test
  void acceptsAddedAndEarlierBoundariesWithoutReplacingEarliestTimestampText() throws Exception {
    ObjectNode registry = validRegistry();
    setHistory(
        unselectedBinding(registry),
        status(1, "2026-07-02T00:00:00Z"),
        status(2, "2026-07-03T00:00:00Z")
            .put("validUntil", "2026-07-20T00:00:00Z")
            .put("revokedAt", "2026-07-19T00:00:00Z"),
        status(3, "2026-07-04T00:00:00Z")
            .put("validUntil", "2026-07-18T00:00:00Z")
            .put("revokedAt", "2026-07-17T00:00:00Z"),
        status(4, "2026-07-05T00:00:00Z")
            .put("validUntil", "2026-07-17T16:00:00-08:00")
            .put("revokedAt", "2026-07-16T16:00:00-08:00"));

    ObjectNode selected = selectedBinding(registry);
    ((ObjectNode) history(selected).get(0)).put("revokedAt", "2026-07-18T00:00:00Z");
    history(selected)
        .add(
            status(2, "2026-07-17T00:00:00Z")
                .put("validUntil", "2026-07-15T12:00:00Z")
                .put("revokedAt", "2026-07-16T00:00:00Z"));
    history(selected)
        .add(
            status(3, "2026-07-18T00:00:00Z")
                .put("validUntil", "2026-07-15T04:00:00-08:00")
                .put("revokedAt", "2026-07-15T16:00:00-08:00"));

    ResolvedKey resolved = verifyAccepted(registry).resolvedKey();

    assertEquals("2026-07-15T12:00:00Z", resolved.validUntil());
    assertEquals("2026-07-16T00:00:00Z", resolved.revokedAt());
  }

  @Test
  void acceptsMoreThanSixtyFourHistoryRecords() throws Exception {
    ObjectNode registry = validRegistry();
    ArrayNode selectedHistory = history(selectedBinding(registry)).removeAll();
    Instant firstRecordedAt = Instant.parse("2026-07-16T00:00:00Z");
    for (int sequence = 1; sequence <= 65; sequence++) {
      ObjectNode record = status(sequence, firstRecordedAt.plusSeconds(sequence - 1L).toString());
      if (sequence == 65) {
        record.put("revokedAt", "2026-07-17T00:00:00Z");
      }
      selectedHistory.add(record);
    }

    VerifiedSignedDocument verified = verifyAccepted(registry);

    assertEquals(SELECTED_KEY_ID, verified.resolvedKey().keyId());
    assertNull(verified.resolvedKey().validUntil());
    assertEquals("2026-07-17T00:00:00Z", verified.resolvedKey().revokedAt());
  }

  @Test
  void mergesComplementaryHistoryAcrossRepeatedSelectedBindings() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode secondDeclaration = selectedBinding(registry).deepCopy();
    setHistory(
        secondDeclaration,
        status(2, "2026-07-17T00:00:00Z").put("revokedAt", "2026-07-17T00:00:00Z"));
    bindings(registry).add(secondDeclaration);

    ResolvedKey resolved = verifyAccepted(registry).resolvedKey();

    assertEquals("2026-07-16T00:00:00Z", resolved.validUntil());
    assertEquals("2026-07-17T00:00:00Z", resolved.revokedAt());
  }

  private static Stream<Arguments> invalidUnselectedHistories() {
    return Stream.of(
        Arguments.of(
            "noncontiguous sequence",
            mutation(status(2, "2026-07-02T00:00:00Z")),
            "Registry validity history is not contiguous"),
        Arguments.of(
            "recordedAt moves backward",
            mutation(status(1, "2026-07-03T00:00:00Z"), status(2, "2026-07-02T00:00:00Z")),
            "Registry validity history is not append ordered"),
        Arguments.of(
            "validUntil moves later",
            mutation(
                status(1, "2026-07-02T00:00:00Z").put("validUntil", "2026-07-10T00:00:00Z"),
                status(2, "2026-07-03T00:00:00Z").put("validUntil", "2026-07-11T00:00:00Z")),
            "Registry moves validUntil later"),
        Arguments.of(
            "revokedAt moves later",
            mutation(
                status(1, "2026-07-02T00:00:00Z").put("revokedAt", "2026-07-10T00:00:00Z"),
                status(2, "2026-07-03T00:00:00Z").put("revokedAt", "2026-07-11T00:00:00Z")),
            "Registry moves revokedAt later"),
        Arguments.of(
            "duplicate sequence rewrites recordedAt",
            mutation(status(1, "2026-07-02T00:00:00Z"), status(1, "2026-07-03T00:00:00Z")),
            "Registry rewrites validity history"),
        Arguments.of(
            "duplicate sequence rewrites a boundary",
            mutation(
                status(1, "2026-07-02T00:00:00Z").put("validUntil", "2026-07-10T00:00:00Z"),
                status(1, "2026-07-02T00:00:00Z").put("validUntil", "2026-07-09T00:00:00Z")),
            "Registry rewrites validity history"));
  }

  private static Consumer<ObjectNode> mutation(ObjectNode... statuses) {
    return binding -> setHistory(binding, statuses);
  }

  private static ObjectNode status(long sequence, String recordedAt) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("sequence", sequence)
        .put("recordedAt", recordedAt);
  }

  private static void setHistory(ObjectNode binding, ObjectNode... statuses) {
    ArrayNode bindingHistory = history(binding).removeAll();
    for (ObjectNode status : statuses) {
      bindingHistory.add(status);
    }
  }

  private static SignedDocumentVerificationException assertRegistryFails(
      byte[] registry, String expectedReason) throws Exception {
    SignedDocumentVerificationException error =
        assertThrows(
            SignedDocumentVerificationException.class,
            () ->
                new SignedDocumentCodec()
                    .verify(
                        SignedDocumentKind.COMMAND,
                        goldenCommand(),
                        request -> KeyRegistrySnapshot.organizationWide(registry)));

    assertEquals(VerificationStage.KEY_RESOLUTION, error.diagnostic().stage());
    assertEquals(expectedReason, error.diagnostic().reason());
    assertEquals("AUTH_INVALID_SIGNATURE", error.wireCode());
    assertEquals("Signed Document verification failed: AUTH_INVALID_SIGNATURE", error.getMessage());
    assertNull(error.getCause());
    return error;
  }

  private static VerifiedSignedDocument verifyAccepted(ObjectNode registry) throws Exception {
    byte[] registryBytes = writeRegistry(registry);
    return new SignedDocumentCodec()
        .verify(
            SignedDocumentKind.COMMAND,
            goldenCommand(),
            request -> KeyRegistrySnapshot.organizationWide(registryBytes));
  }

  private static ObjectNode validRegistry() throws IOException {
    return (ObjectNode) StrictJson.parse(resource(VALID_REGISTRY));
  }

  private static ArrayNode bindings(ObjectNode registry) {
    return (ArrayNode) registry.path("bindings");
  }

  private static ObjectNode selectedBinding(ObjectNode registry) {
    return (ObjectNode) bindings(registry).get(0);
  }

  private static ObjectNode unselectedBinding(ObjectNode registry) {
    return (ObjectNode) bindings(registry).get(1);
  }

  private static ArrayNode history(ObjectNode binding) {
    return (ArrayNode) binding.path("validityHistory");
  }

  private static byte[] writeRegistry(ObjectNode registry) throws IOException {
    return StrictJson.write(registry);
  }

  private static byte[] goldenCommand() throws IOException {
    return resource(GOLDEN_COMMAND);
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream input =
        SignedDocumentCodecRegistryHistoryTest.class.getClassLoader().getResourceAsStream(name)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + name);
      }
      return input.readAllBytes();
    }
  }
}
