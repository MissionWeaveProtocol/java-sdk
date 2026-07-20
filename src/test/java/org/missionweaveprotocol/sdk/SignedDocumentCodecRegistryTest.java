package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class SignedDocumentCodecRegistryTest {
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
    byte[] received = resource("cryptography/vectors/signed-documents/valid/command.json");

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
