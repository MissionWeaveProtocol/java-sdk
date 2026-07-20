package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SignedDocumentCodecRegistryTest {
  private static final String GOLDEN_COMMAND =
      "cryptography/vectors/signed-documents/valid/command.json";
  private static final String VALID_REGISTRY = "cryptography/keys/registry-valid.json";

  @Test
  void rejectsInvalidKeyIdInUnselectedBinding() throws Exception {
    assertRegistryMutationFails(
        registry -> unselectedBinding(registry).put("keyId", "relative/key"));
  }

  @ParameterizedTest
  @MethodSource("invalidProtocolUris")
  void rejectsInvalidOrganizationId(String invalidId) throws Exception {
    assertRegistryMutationFails(registry -> registry.put("organizationId", invalidId));
  }

  @ParameterizedTest
  @MethodSource("invalidProtocolUris")
  void rejectsOtherInvalidKeyIdsInUnselectedBinding(String invalidId) throws Exception {
    assertRegistryMutationFails(registry -> unselectedBinding(registry).put("keyId", invalidId));
  }

  @ParameterizedTest
  @MethodSource("invalidProtocolUris")
  void rejectsInvalidPrincipalIdsInUnselectedBinding(String invalidId) throws Exception {
    assertRegistryMutationFails(registry -> unselectedPrincipal(registry).put("id", invalidId));
  }

  @ParameterizedTest
  @MethodSource("validProtocolUris")
  void acceptsProtocolUriAsOrganizationId(String validId) throws Exception {
    assertRegistryMutationAccepted(registry -> registry.put("organizationId", validId));
  }

  @ParameterizedTest
  @MethodSource("validProtocolUris")
  void acceptsProtocolUriAsUnselectedBindingKeyId(String validId) throws Exception {
    assertRegistryMutationAccepted(registry -> unselectedBinding(registry).put("keyId", validId));
  }

  @ParameterizedTest
  @MethodSource("validProtocolUris")
  void acceptsProtocolUriAsUnselectedPrincipalId(String validId) throws Exception {
    assertRegistryMutationAccepted(registry -> unselectedPrincipal(registry).put("id", validId));
  }

  @Test
  void acceptsRegistryIdentifiersBeyondFixtureSchemaStringLimit() throws Exception {
    String longComponent = "a".repeat(600);

    assertRegistryMutationAccepted(
        registry -> {
          registry.put("organizationId", "example:organization:" + longComponent);
          unselectedBinding(registry).put("keyId", "example:key:" + longComponent);
          unselectedPrincipal(registry).put("id", "example:principal:" + longComponent);
        });
  }

  @Test
  void rejectsNonObjectRegistryRoot() throws Exception {
    ObjectNode registry = validRegistry();

    assertRegistryFails(StrictJson.write(bindings(registry)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"organizationId", "bindings"})
  void rejectsMissingRegistryRootFields(String field) throws Exception {
    assertRegistryMutationFails(registry -> registry.remove(field));
  }

  @Test
  void rejectsUnknownRegistryRootField() throws Exception {
    assertRegistryMutationFails(registry -> registry.put("unknown", true));
  }

  @Test
  void rejectsEmptyRegistryBindings() throws Exception {
    assertRegistryMutationFails(registry -> bindings(registry).removeAll());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"keyId", "principal", "algorithm", "publicKey", "validFrom", "validityHistory"})
  void rejectsMissingFieldInUnselectedBinding(String field) throws Exception {
    assertRegistryMutationFails(registry -> unselectedBinding(registry).remove(field));
  }

  @Test
  void rejectsUnknownFieldInUnselectedBinding() throws Exception {
    assertRegistryMutationFails(registry -> unselectedBinding(registry).put("unknown", true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"type", "id"})
  void rejectsMissingFieldInUnselectedPrincipal(String field) throws Exception {
    assertRegistryMutationFails(registry -> unselectedPrincipal(registry).remove(field));
  }

  @Test
  void rejectsUnknownFieldInUnselectedPrincipal() throws Exception {
    assertRegistryMutationFails(registry -> unselectedPrincipal(registry).put("unknown", true));
  }

  @Test
  void rejectsUnsupportedTypeInUnselectedPrincipal() throws Exception {
    assertRegistryMutationFails(
        registry -> unselectedPrincipal(registry).put("type", "organization"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"sequence", "recordedAt"})
  void rejectsMissingFieldInUnselectedHistoryRecord(String field) throws Exception {
    assertRegistryMutationFails(registry -> appendUnselectedHistory(registry).remove(field));
  }

  @Test
  void rejectsUnknownFieldInUnselectedHistoryRecord() throws Exception {
    assertRegistryMutationFails(registry -> appendUnselectedHistory(registry).put("unknown", true));
  }

  @Test
  void rejectsNullRegistrySnapshot() throws Exception {
    assertKeyResolutionFailure(request -> null);
  }

  @Test
  void rejectsPartialRegistrySnapshot() throws Exception {
    byte[] malformedRegistry = new byte[] {'{'};

    SignedDocumentVerificationException error =
        assertKeyResolutionFailure(
            request -> new KeyRegistrySnapshot(malformedRegistry, KeyRegistryCompleteness.PARTIAL));

    assertEquals(
        "Registry evidence is not Organization-wide complete", error.diagnostic().reason());
  }

  @Test
  void rejectsRegistrySnapshotWithoutCompletenessAssertion() throws Exception {
    byte[] malformedRegistry = new byte[] {'{'};

    SignedDocumentVerificationException error =
        assertKeyResolutionFailure(
            request ->
                new KeyRegistrySnapshot(malformedRegistry, KeyRegistryCompleteness.UNSPECIFIED));

    assertEquals(
        "Registry evidence is not Organization-wide complete", error.diagnostic().reason());
  }

  @Test
  void rejectsMalformedOrganizationWideRegistrySnapshot() throws Exception {
    byte[] malformedRegistry = new byte[] {'{'};

    assertKeyResolutionFailure(request -> KeyRegistrySnapshot.organizationWide(malformedRegistry));
  }

  @Test
  void rejectsCheckedRegistryAcquisitionFailure() throws Exception {
    assertKeyResolutionFailure(
        request -> {
          throw new KeyResolutionException("Registry unavailable");
        });
  }

  @Test
  void rejectsRuntimeRegistryAdapterFailure() throws Exception {
    assertKeyResolutionFailure(
        request -> {
          throw new IllegalStateException("Registry adapter failed");
        });
  }

  private static SignedDocumentVerificationException assertKeyResolutionFailure(
      KeyResolver resolver) throws Exception {
    byte[] received = goldenCommand();

    SignedDocumentVerificationException error =
        assertThrows(
            SignedDocumentVerificationException.class,
            () -> new SignedDocumentCodec().verify(SignedDocumentKind.COMMAND, received, resolver));

    assertEquals(VerificationStage.KEY_RESOLUTION, error.diagnostic().stage());
    assertEquals("AUTH_INVALID_SIGNATURE", error.wireCode());
    assertEquals("Signed Document verification failed: AUTH_INVALID_SIGNATURE", error.getMessage());
    assertNull(error.getCause());
    return error;
  }

  private static void assertRegistryMutationFails(Consumer<ObjectNode> mutation) throws Exception {
    byte[] registry = registryWith(mutation);
    assertRegistryFails(registry);
  }

  private static void assertRegistryMutationAccepted(Consumer<ObjectNode> mutation)
      throws Exception {
    byte[] registry = registryWith(mutation);
    new SignedDocumentCodec()
        .verify(
            SignedDocumentKind.COMMAND,
            goldenCommand(),
            request -> KeyRegistrySnapshot.organizationWide(registry));
  }

  private static byte[] registryWith(Consumer<ObjectNode> mutation) throws IOException {
    ObjectNode registry = validRegistry();
    mutation.accept(registry);
    return StrictJson.write(registry);
  }

  private static ObjectNode validRegistry() throws IOException {
    return (ObjectNode) StrictJson.parse(resource(VALID_REGISTRY));
  }

  private static ArrayNode bindings(ObjectNode registry) {
    return (ArrayNode) registry.path("bindings");
  }

  private static ObjectNode unselectedBinding(ObjectNode registry) {
    return (ObjectNode) bindings(registry).get(1);
  }

  private static ObjectNode unselectedPrincipal(ObjectNode registry) {
    return (ObjectNode) unselectedBinding(registry).path("principal");
  }

  private static ObjectNode appendUnselectedHistory(ObjectNode registry) {
    return ((ArrayNode) unselectedBinding(registry).path("validityHistory"))
        .addObject()
        .put("sequence", 1)
        .put("recordedAt", "2026-07-02T00:00:00Z");
  }

  private static SignedDocumentVerificationException assertRegistryFails(byte[] registry)
      throws Exception {
    return assertKeyResolutionFailure(request -> KeyRegistrySnapshot.organizationWide(registry));
  }

  private static byte[] goldenCommand() throws IOException {
    return resource(GOLDEN_COMMAND);
  }

  private static Stream<String> invalidProtocolUris() {
    return Stream.of(
        "actions/run",
        "1:",
        "//",
        "urn:example:key\n",
        "https://例え.テスト/actions/run",
        "example:%",
        "example:%Z",
        "example:%ZZ",
        "https://example.test/?q=%GG",
        "https://example.test/?q=[x]");
  }

  private static Stream<String> validProtocolUris() {
    return Stream.of(
        "example:", "https://example.test/actions/%E4%BE%8B", "https://example.test/?q=%5Bx%5D");
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream input =
        SignedDocumentCodecRegistryTest.class.getClassLoader().getResourceAsStream(name)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + name);
      }
      return input.readAllBytes();
    }
  }
}
