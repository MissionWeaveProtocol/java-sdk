package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignedDocumentCodecRegistryInvariantTest {
  private static final String GOLDEN_COMMAND =
      "cryptography/vectors/signed-documents/valid/command.json";
  private static final String UNKNOWN_KEY_COMMAND =
      "cryptography/vectors/signed-documents/invalid/command-unknown-key.json";
  private static final String VALID_REGISTRY = "cryptography/keys/registry-valid.json";
  private static final String ORGANIZATION_ID = "urn:missionweaveprotocol:organization:acme";
  private static final String SELECTED_KEY_ID =
      "urn:missionweaveprotocol:key:crypto-vector-rfc8032-1";
  private static final String IDENTITY_PUBLIC_KEY = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String UNUSED_PUBLIC_KEY = "psVxeGOMD-E3E37lpVbCl3CxBQMv3fa9M6EyGytWTEs";

  @ParameterizedTest(name = "{0}")
  @MethodSource("strictRegistryFailures")
  void rejectsNonStrictRegistryBytesAtKeyResolution(
      String description, byte[] registry, String expectedReasonFragment) throws Exception {
    SignedDocumentVerificationException error = assertRegistryFails(goldenCommand(), registry);

    assertTrue(
        error.diagnostic().reason().contains(expectedReasonFragment),
        () -> "Unexpected protected reason: " + error.diagnostic().reason());
  }

  @Test
  void rejectsInvalidPublicKeyInUnselectedBinding() throws Exception {
    ObjectNode registry = validRegistry();
    unselectedBinding(registry).put("publicKey", IDENTITY_PUBLIC_KEY);

    SignedDocumentVerificationException error =
        assertRegistryFails(goldenCommand(), writeRegistry(registry));

    assertEquals(
        "Registry public key encodes the Ed25519 identity point", error.diagnostic().reason());
  }

  @Test
  void rejectsUnselectedKeyIdRebinding() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode rebound = unselectedBinding(registry).deepCopy();
    rebound.put("publicKey", UNUSED_PUBLIC_KEY);
    bindings(registry).add(rebound);

    SignedDocumentVerificationException error =
        assertRegistryFails(goldenCommand(), writeRegistry(registry));

    assertEquals("Registry reuses a key ID for another binding", error.diagnostic().reason());
  }

  @Test
  void rejectsCrossPrincipalPublicKeyOwnershipConflict() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode conflicting = selectedBinding(registry).deepCopy();
    conflicting.put("keyId", "urn:missionweaveprotocol:key:conflicting-owner");
    ((ObjectNode) conflicting.path("principal"))
        .put("id", "urn:missionweaveprotocol:agent:conflicting-owner");
    bindings(registry).add(conflicting);

    SignedDocumentVerificationException error =
        assertRegistryFails(goldenCommand(), writeRegistry(registry));

    assertEquals("Registry reuses a public key", error.diagnostic().reason());
  }

  @Test
  void rejectsPrincipalAlgorithmPublicKeyTupleAlias() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode alias = selectedBinding(registry).deepCopy();
    alias.put("keyId", "urn:missionweaveprotocol:key:selected-alias");
    bindings(registry).add(alias);

    SignedDocumentVerificationException error =
        assertRegistryFails(goldenCommand(), writeRegistry(registry));

    assertEquals("Registry contains a key-ID alias", error.diagnostic().reason());
  }

  @Test
  void validatesEveryBindingBeforeReportingAnUnknownKey() throws Exception {
    ObjectNode registry = validRegistry();
    finalBinding(registry).put("publicKey", IDENTITY_PUBLIC_KEY);

    SignedDocumentVerificationException error =
        assertRegistryFails(unknownKeyCommand(), writeRegistry(registry));

    assertEquals(
        "Registry public key encodes the Ed25519 identity point", error.diagnostic().reason());
  }

  @Test
  void reportsUnknownKeyOnlyAfterValidatingTheCompleteRegistry() throws Exception {
    SignedDocumentVerificationException error =
        assertRegistryFails(unknownKeyCommand(), resource(VALID_REGISTRY));

    assertEquals("signature.keyId is unknown", error.diagnostic().reason());
  }

  @Test
  void acceptsMoreThanSixtyFourRegistryBindings() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode selected = selectedBinding(registry).deepCopy();
    ArrayNode registryBindings = bindings(registry).removeAll();
    for (int index = 0; index < 65; index++) {
      registryBindings.add(selected.deepCopy());
    }

    VerifiedSignedDocument verified = verifyAccepted(writeRegistry(registry));

    assertEquals(ORGANIZATION_ID, verified.resolvedKey().organizationId());
    assertEquals(SELECTED_KEY_ID, verified.resolvedKey().keyId());
  }

  @Test
  void registrySnapshotRemainsImmutableThroughVerification() throws Exception {
    byte[] callerBytes = resource(VALID_REGISTRY);
    KeyRegistrySnapshot snapshot = KeyRegistrySnapshot.organizationWide(callerBytes);
    Arrays.fill(callerBytes, (byte) 0);
    Arrays.fill(snapshot.registryBytes(), (byte) 0);

    VerifiedSignedDocument verified =
        new SignedDocumentCodec()
            .verify(SignedDocumentKind.COMMAND, goldenCommand(), request -> snapshot);

    assertEquals(ORGANIZATION_ID, verified.resolvedKey().organizationId());
    assertEquals(SELECTED_KEY_ID, verified.resolvedKey().keyId());
  }

  @Test
  void rejectsPaddedPublicKeyInUnselectedBinding() throws Exception {
    ObjectNode registry = validRegistry();
    ObjectNode unselected = unselectedBinding(registry);
    unselected.put("publicKey", unselected.path("publicKey").textValue() + "=");

    SignedDocumentVerificationException error =
        assertRegistryFails(goldenCommand(), writeRegistry(registry));

    assertEquals("Value is not unpadded base64url", error.diagnostic().reason());
  }

  private static Stream<Arguments> strictRegistryFailures() throws IOException {
    byte[] valid = resource(VALID_REGISTRY);
    byte[] invalidUtf8 = Arrays.copyOf(valid, valid.length + 1);
    invalidUtf8[invalidUtf8.length - 1] = (byte) 0xff;

    byte[] byteOrderMark = new byte[valid.length + 3];
    byteOrderMark[0] = (byte) 0xef;
    byteOrderMark[1] = (byte) 0xbb;
    byteOrderMark[2] = (byte) 0xbf;
    System.arraycopy(valid, 0, byteOrderMark, 3, valid.length);

    String validJson = new String(valid, StandardCharsets.UTF_8);
    String bindingsMember = "\"bindings\"";
    int bindingsIndex = validJson.indexOf(bindingsMember);
    String duplicateMemberName = "\"organization" + '\\' + "u0049d\"";
    String duplicateDecodedMember =
        validJson.substring(0, bindingsIndex)
            + duplicateMemberName
            + ":\""
            + ORGANIZATION_ID
            + "\","
            + validJson.substring(bindingsIndex);

    byte[] trailingData = Arrays.copyOf(valid, valid.length + 5);
    System.arraycopy(" true".getBytes(StandardCharsets.UTF_8), 0, trailingData, valid.length, 5);

    return Stream.of(
        Arguments.of("invalid UTF-8", invalidUtf8, "Input length"),
        Arguments.of("UTF-8 byte-order mark", byteOrderMark, "byte-order mark"),
        Arguments.of(
            "duplicate decoded member",
            duplicateDecodedMember.getBytes(StandardCharsets.UTF_8),
            "Duplicate field"),
        Arguments.of("trailing JSON data", trailingData, "trailing data"));
  }

  private static SignedDocumentVerificationException assertRegistryFails(
      byte[] document, byte[] registry) throws Exception {
    SignedDocumentVerificationException error =
        assertThrows(
            SignedDocumentVerificationException.class,
            () ->
                new SignedDocumentCodec()
                    .verify(
                        SignedDocumentKind.COMMAND,
                        document,
                        request -> KeyRegistrySnapshot.organizationWide(registry)));

    assertEquals(VerificationStage.KEY_RESOLUTION, error.diagnostic().stage());
    assertEquals("AUTH_INVALID_SIGNATURE", error.wireCode());
    assertEquals("Signed Document verification failed: AUTH_INVALID_SIGNATURE", error.getMessage());
    assertNull(error.getCause());
    return error;
  }

  private static VerifiedSignedDocument verifyAccepted(byte[] registry) throws Exception {
    return new SignedDocumentCodec()
        .verify(
            SignedDocumentKind.COMMAND,
            goldenCommand(),
            request -> KeyRegistrySnapshot.organizationWide(registry));
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

  private static ObjectNode finalBinding(ObjectNode registry) {
    ArrayNode registryBindings = bindings(registry);
    return (ObjectNode) registryBindings.get(registryBindings.size() - 1);
  }

  private static byte[] writeRegistry(ObjectNode registry) throws IOException {
    return StrictJson.write(registry);
  }

  private static byte[] goldenCommand() throws IOException {
    return resource(GOLDEN_COMMAND);
  }

  private static byte[] unknownKeyCommand() throws IOException {
    return resource(UNKNOWN_KEY_COMMAND);
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream input =
        SignedDocumentCodecRegistryInvariantTest.class.getClassLoader().getResourceAsStream(name)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + name);
      }
      return input.readAllBytes();
    }
  }
}
