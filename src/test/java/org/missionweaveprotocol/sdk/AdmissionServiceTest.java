package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdmissionServiceTest {
  private static final String ADMISSION_SERVICE_ID =
      "urn:missionweaveprotocol:service:admission";

  @Test
  void firstAdmissionReturnsOnlyAfterCommittedRecordValidation() throws Exception {
    byte[] committed = resource("admission/records/valid/command.json");
    RecordingAdmissionLog log = RecordingAdmissionLog.authoritativeAbsenceThenCommit(committed);
    FixedTrustedContext context = fixedTrustedContext("2026-07-15T00:05:00Z");

    AdmittedSignedDocument admitted =
        new AdmissionService()
            .admitFirst(
                SignedDocumentKind.COMMAND,
                goldenCommand(),
                currentRegistry(),
                log,
                context);

    assertEquals(admitted.verified().signingHash(), admitted.record().signingHash());
    assertArrayEquals(committed, admitted.record().bytes());
    assertEquals(List.of("lookup", "appendOrReturnExisting"), log.calls());
    assertEquals(1, context.issueCalls());
    assertArrayEquals(CanonicalJson.canonicalize(committed), log.appendedCandidate());
  }

  @Test
  void trustedAcceptanceEqualToValidUntilFailsAdmission() throws Exception {
    AdmissionException error =
        assertThrows(
            AdmissionException.class,
            () ->
                new AdmissionService()
                    .prepareFirstAdmission(
                        verifiedCommand(), fixedTrustedContext("2026-07-16T00:00:00Z")));

    assertAdmission(error, AdmissionReason.TRUSTED_TIME_OUTSIDE_KEY_INTERVAL);
  }

  @Test
  void existingRecordBindingMismatchFailsAdmission() throws Exception {
    RecordingAdmissionLog log =
        RecordingAdmissionLog.found(resource("admission/records/invalid/key-id-mismatch.json"));

    AdmissionException error =
        assertThrows(
            AdmissionException.class,
            () ->
                new AdmissionService()
                    .verifyHistoricalAdmission(
                        SignedDocumentKind.COMMAND,
                        goldenCommand(),
                        historicalRegistry(),
                        log));

    assertAdmission(error, AdmissionReason.RECORD_BINDING_MISMATCH);
    assertEquals(List.of("lookup"), log.calls());
  }

  @Test
  void historicalReplayAcceptsRetainedLaterRevocation() throws Exception {
    RecordingAdmissionLog log =
        RecordingAdmissionLog.found(resource("admission/records/valid/command.json"));

    AdmittedSignedDocument admitted =
        new AdmissionService()
            .verifyHistoricalAdmission(
                SignedDocumentKind.COMMAND,
                goldenCommand(),
                registry("admission/registries/registry-later-revocation.json"),
                log);

    assertEquals("2026-07-15T00:05:00Z", admitted.record().trustedAcceptedAt());
    assertEquals("2026-07-15T08:00:00+08:00", admitted.verified().resolvedKey().validFrom());
    assertEquals("2026-07-16T00:00:00Z", admitted.verified().resolvedKey().validUntil());
    assertEquals("2026-07-15T01:00:00Z", admitted.verified().resolvedKey().revokedAt());
    assertEquals(List.of("lookup"), log.calls());
    assertEquals(0, log.appendCalls());
  }

  @Test
  void unavailableLogFailsFirstAdmission() throws Exception {
    RecordingAdmissionLog log = RecordingAdmissionLog.unavailable();
    FixedTrustedContext context = fixedTrustedContext("2026-07-15T00:05:00Z");

    AdmissionException error =
        assertThrows(
            AdmissionException.class,
            () ->
                new AdmissionService()
                    .admitFirst(
                        SignedDocumentKind.COMMAND,
                        goldenCommand(),
                        currentRegistry(),
                        log,
                        context));

    assertAdmission(error, AdmissionReason.LOG_UNAVAILABLE);
    assertEquals(List.of("lookup"), log.calls());
    assertEquals(0, context.issueCalls());
  }

  @Test
  void historicalReplayNeverCreatesAMissingRecord() throws Exception {
    RecordingAdmissionLog log = RecordingAdmissionLog.authoritativeAbsence();

    AdmissionException error =
        assertThrows(
            AdmissionException.class,
            () ->
                new AdmissionService()
                    .verifyHistoricalAdmission(
                        SignedDocumentKind.COMMAND,
                        goldenCommand(),
                        historicalRegistry(),
                        log));

    assertAdmission(error, AdmissionReason.RECORD_MISSING);
    assertEquals(List.of("lookup"), log.calls());
    assertEquals(0, log.appendCalls());
  }

  @Test
  void appendReturnValueIsRevalidatedBeforeAdmission() throws Exception {
    RecordingAdmissionLog log =
        RecordingAdmissionLog.authoritativeAbsenceThenCommit(
            resource("admission/records/invalid/key-id-mismatch.json"));

    AdmissionException error =
        assertThrows(
            AdmissionException.class,
            () ->
                new AdmissionService()
                    .admitFirst(
                        SignedDocumentKind.COMMAND,
                        goldenCommand(),
                        currentRegistry(),
                        log,
                        fixedTrustedContext("2026-07-15T00:05:00Z")));

    assertAdmission(error, AdmissionReason.RECORD_BINDING_MISMATCH);
    assertEquals(List.of("lookup", "appendOrReturnExisting"), log.calls());
  }

  @Test
  void eventSelfAnchoringRejectsCanonicallyEquivalentBytes() throws Exception {
    byte[] event = resource("cryptography/vectors/signed-documents/valid/event.json");
    byte[] canonicalEvent = CanonicalJson.canonicalize(event);
    RecordingAdmissionLog log = RecordingAdmissionLog.found(canonicalEvent);

    AdmissionException error =
        assertThrows(
            AdmissionException.class,
            () ->
                new AdmissionService()
                    .verifyHistoricalAdmission(
                        SignedDocumentKind.EVENT, event, historicalRegistry(), log));

    assertAdmission(error, AdmissionReason.EVENT_SELF_ANCHORING);
    assertEquals(List.of("lookup"), log.calls());
  }

  @Test
  void signedDocumentFailurePrecedesAdmissionLogAccess() throws Exception {
    RecordingAdmissionLog log = RecordingAdmissionLog.authoritativeAbsence();
    FixedTrustedContext context = fixedTrustedContext("2026-07-15T00:05:00Z");

    SignedDocumentVerificationException error =
        assertThrows(
            SignedDocumentVerificationException.class,
            () ->
                new AdmissionService()
                    .admitFirst(
                        SignedDocumentKind.COMMAND,
                        new byte[] {'{', '}'},
                        currentRegistry(),
                        log,
                        context));

    assertEquals(VerificationStage.SCHEMA, error.diagnostic().stage());
    assertEquals("SCHEMA_VALIDATION_FAILED", error.wireCode());
    assertEquals(List.of(), log.calls());
    assertEquals(0, context.issueCalls());
  }

  private static void assertAdmission(AdmissionException error, AdmissionReason reason) {
    assertEquals("AUTH_INVALID_SIGNATURE", error.wireCode());
    assertEquals("Signed Document admission failed: AUTH_INVALID_SIGNATURE", error.getMessage());
    assertEquals("admission", error.diagnostic().stage());
    assertEquals(reason, error.diagnostic().reason());
  }

  private static byte[] goldenCommand() throws IOException {
    return resource("cryptography/vectors/signed-documents/valid/command.json");
  }

  private static AdmissionCurrentKeyResolver currentRegistry() throws IOException {
    byte[] registry = resource("cryptography/keys/registry-valid.json");
    return request -> KeyRegistrySnapshot.organizationWide(registry);
  }

  private static KeyResolver historicalRegistry() throws IOException {
    return registry("cryptography/keys/registry-valid.json");
  }

  private static KeyResolver registry(String path) throws IOException {
    byte[] registry = resource(path);
    return request -> KeyRegistrySnapshot.organizationWide(registry);
  }

  private static VerifiedSignedDocument verifiedCommand() throws Exception {
    return new SignedDocumentCodec()
        .verify(SignedDocumentKind.COMMAND, goldenCommand(), historicalRegistry());
  }

  private static FixedTrustedContext fixedTrustedContext(String trustedAcceptedAt) {
    return new FixedTrustedContext(
        new AdmissionContextValue(
            "urn:missionweaveprotocol:admission-record:crypto-vector-command",
            trustedAcceptedAt,
            new Principal("service", ADMISSION_SERVICE_ID)));
  }

  private static byte[] resource(String path) throws IOException {
    try (InputStream input =
        AdmissionServiceTest.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }

  private static final class FixedTrustedContext implements TrustedAdmissionContext {
    private final AdmissionContextValue value;
    private int issueCalls;

    private FixedTrustedContext(AdmissionContextValue value) {
      this.value = value;
    }

    @Override
    public AdmissionContextValue issue(String organizationId, String signingHash) {
      issueCalls++;
      return value;
    }

    int issueCalls() {
      return issueCalls;
    }
  }

  private static final class RecordingAdmissionLog implements AdmissionLog {
    private final AdmissionLookup lookup;
    private final AuthenticatedAdmissionRecord committed;
    private final AdmissionAdapterException lookupError;
    private final List<String> calls = new ArrayList<>();
    private byte[] appendedCandidate;

    private RecordingAdmissionLog(
        AdmissionLookup lookup,
        AuthenticatedAdmissionRecord committed,
        AdmissionAdapterException lookupError) {
      this.lookup = lookup;
      this.committed = committed;
      this.lookupError = lookupError;
    }

    static RecordingAdmissionLog authoritativeAbsence() {
      return new RecordingAdmissionLog(new AdmissionLookup.AuthoritativeAbsence(), null, null);
    }

    static RecordingAdmissionLog authoritativeAbsenceThenCommit(byte[] record) {
      return new RecordingAdmissionLog(
          new AdmissionLookup.AuthoritativeAbsence(), authenticated(record), null);
    }

    static RecordingAdmissionLog found(byte[] record) {
      return new RecordingAdmissionLog(
          new AdmissionLookup.Found(authenticated(record)), null, null);
    }

    static RecordingAdmissionLog unavailable() {
      return new RecordingAdmissionLog(
          null,
          null,
          new AdmissionAdapterException(
              AdmissionReason.LOG_UNAVAILABLE, "fixture Admission Log is unavailable"));
    }

    @Override
    public AdmissionLookup lookup(String organizationId, String signingHash)
        throws AdmissionAdapterException {
      calls.add("lookup");
      if (lookupError != null) {
        throw lookupError;
      }
      return lookup;
    }

    @Override
    public AuthenticatedAdmissionRecord appendOrReturnExisting(
        String organizationId, String signingHash, byte[] candidateBytes) {
      calls.add("appendOrReturnExisting");
      appendedCandidate = candidateBytes.clone();
      return assertInstanceOf(AuthenticatedAdmissionRecord.class, committed);
    }

    List<String> calls() {
      return List.copyOf(calls);
    }

    int appendCalls() {
      return Math.toIntExact(calls.stream().filter("appendOrReturnExisting"::equals).count());
    }

    byte[] appendedCandidate() {
      return appendedCandidate == null ? null : appendedCandidate.clone();
    }

    private static AuthenticatedAdmissionRecord authenticated(byte[] record) {
      return new AuthenticatedAdmissionRecord(
          record, new Principal("service", ADMISSION_SERVICE_ID));
    }
  }
}
