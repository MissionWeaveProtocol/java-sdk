package org.missionweaveprotocol.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdmissionConformanceTest {
  @Test
  void satisfiesAllVendoredAdmissionManifestEvaluations() throws Exception {
    JsonNode manifest = StrictJson.parse(resource("admission/manifest.json"));
    int evaluations = 0;
    int complete = 0;
    int rejected = 0;
    Map<String, Integer> callTotals = new HashMap<>();

    for (JsonNode testCase : manifest.path("cases")) {
      for (JsonNode evaluation : testCase.path("evaluations")) {
        evaluations++;
        List<String> calls = new ArrayList<>();
        ManifestRegistry registry =
            new ManifestRegistry(resource(evaluation.path("registry").textValue()), calls);
        ManifestAdmissionLog log = new ManifestAdmissionLog(evaluation, calls);
        ManifestTrustedContext context = new ManifestTrustedContext(evaluation, calls);

        try {
          AdmittedSignedDocument admitted = execute(evaluation, registry, log, context);
          assertEquals("complete", evaluation.path("expect").path("stage").textValue());
          assertArrayEquals(
              resource(evaluation.path("expect").path("record").textValue()),
              admitted.record().bytes(),
              testCase.path("id").textValue() + "/" + evaluation.path("id").textValue());
          complete++;
        } catch (AdmissionException error) {
          assertEquals("admission", evaluation.path("expect").path("stage").textValue());
          assertEquals(evaluation.path("expect").path("wireCode").textValue(), error.wireCode());
          assertEquals(
              admissionReason(evaluation.path("expect").path("reason").textValue()),
              error.diagnostic().reason());
          rejected++;
        }

        assertEquals(expectedCalls(evaluation), calls, evaluation.path("id").textValue());
        calls.forEach(call -> callTotals.merge(call, 1, Integer::sum));
        if (context.organizationId != null) {
          assertEquals(log.organizationId, context.organizationId);
          assertEquals(log.signingHash, context.signingHash);
        }
      }
    }

    assertEquals(30, evaluations);
    assertEquals(12, complete);
    assertEquals(18, rejected);
    assertEquals(18, callTotals.getOrDefault("resolveCurrent", 0));
    assertEquals(12, callTotals.getOrDefault("resolve", 0));
    assertEquals(30, callTotals.getOrDefault("lookup", 0));
    assertEquals(17, callTotals.getOrDefault("issue", 0));
    assertEquals(11, callTotals.getOrDefault("appendOrReturnExisting", 0));
  }

  private static AdmittedSignedDocument execute(
      JsonNode evaluation,
      ManifestRegistry registry,
      ManifestAdmissionLog log,
      ManifestTrustedContext context)
      throws Exception {
    SignedDocumentKind kind = signedDocumentKind(evaluation.path("profileId").textValue());
    byte[] document = resource(evaluation.path("document").textValue());
    AdmissionService service = new AdmissionService();
    if (evaluation.path("mode").textValue().equals("first-admission")) {
      return service.admitFirst(kind, document, registry, log, context);
    }
    return service.verifyHistoricalAdmission(kind, document, registry, log);
  }

  private static List<String> expectedCalls(JsonNode evaluation) {
    if (evaluation.path("mode").textValue().equals("historical-replay")) {
      return List.of("resolve", "lookup");
    }
    if (!evaluation.path("lookup").path("status").textValue().equals("authoritative-absence")) {
      return List.of("resolveCurrent", "lookup");
    }
    if (evaluation.path("append").isNull()) {
      return List.of("resolveCurrent", "lookup", "issue");
    }
    return List.of("resolveCurrent", "lookup", "issue", "appendOrReturnExisting");
  }

  private static SignedDocumentKind signedDocumentKind(String id) {
    for (SignedDocumentKind kind : SignedDocumentKind.values()) {
      if (kind.id().equals(id)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("Unknown Signed Document kind: " + id);
  }

  private static AdmissionReason admissionReason(String id) {
    for (AdmissionReason reason : AdmissionReason.values()) {
      if (reason.id().equals(id)) {
        return reason;
      }
    }
    throw new IllegalArgumentException("Unknown Admission reason: " + id);
  }

  private static byte[] resource(String path) throws IOException {
    try (InputStream input =
        AdmissionConformanceTest.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }

  private static final class ManifestRegistry implements KeyResolver, AdmissionCurrentKeyResolver {
    private final byte[] registry;
    private final List<String> calls;

    private ManifestRegistry(byte[] registry, List<String> calls) {
      this.registry = registry.clone();
      this.calls = calls;
    }

    @Override
    public KeyRegistrySnapshot resolve(KeyResolutionRequest request) {
      calls.add("resolve");
      return snapshot();
    }

    @Override
    public KeyRegistrySnapshot resolveCurrent(KeyResolutionRequest request) {
      calls.add("resolveCurrent");
      return snapshot();
    }

    private KeyRegistrySnapshot snapshot() {
      return KeyRegistrySnapshot.organizationWide(registry);
    }
  }

  private static final class ManifestTrustedContext implements TrustedAdmissionContext {
    private final JsonNode trustedContext;
    private final List<String> calls;
    private String organizationId;
    private String signingHash;

    private ManifestTrustedContext(JsonNode evaluation, List<String> calls) {
      this.trustedContext = evaluation.path("trustedContext");
      this.calls = calls;
    }

    @Override
    public AdmissionContextValue issue(String organizationId, String signingHash)
        throws AdmissionAdapterException {
      calls.add("issue");
      this.organizationId = organizationId;
      this.signingHash = signingHash;
      if (trustedContext.isNull()) {
        throw new AdmissionAdapterException(
            AdmissionReason.COMMIT_FAILED, "trusted context was not declared");
      }
      return new AdmissionContextValue(
          trustedContext.path("admissionRecordId").textValue(),
          trustedContext.path("trustedAcceptedAt").textValue(),
          principal(trustedContext.path("acceptedBy")));
    }
  }

  private static final class ManifestAdmissionLog implements AdmissionLog {
    private final JsonNode evaluation;
    private final List<String> calls;
    private String organizationId;
    private String signingHash;

    private ManifestAdmissionLog(JsonNode evaluation, List<String> calls) {
      this.evaluation = evaluation;
      this.calls = calls;
    }

    @Override
    public AdmissionLookup lookup(String organizationId, String signingHash)
        throws AdmissionAdapterException {
      calls.add("lookup");
      this.organizationId = organizationId;
      this.signingHash = signingHash;
      JsonNode lookup = evaluation.path("lookup");
      return switch (lookup.path("status").textValue()) {
        case "found" ->
            new AdmissionLookup.Found(
                authenticated(
                    lookup.path("record").textValue(), lookup.path("authenticatedService")));
        case "authoritative-absence" -> new AdmissionLookup.AuthoritativeAbsence();
        case "unauthenticated", "integrity-failed" ->
            throw adapterFailure(AdmissionReason.LOG_AUTHENTICATION_FAILED, lookup);
        case "unavailable" -> throw adapterFailure(AdmissionReason.LOG_UNAVAILABLE, lookup);
        case "indeterminate" -> throw adapterFailure(AdmissionReason.LOG_INDETERMINATE, lookup);
        default -> throw new IllegalArgumentException("Unknown lookup outcome: " + lookup);
      };
    }

    @Override
    public AuthenticatedAdmissionRecord appendOrReturnExisting(
        String organizationId, String signingHash, byte[] candidateBytes)
        throws AdmissionAdapterException {
      calls.add("appendOrReturnExisting");
      assertEquals(this.organizationId, organizationId);
      assertEquals(this.signingHash, signingHash);
      assertArrayEquals(
          canonicalize(
              resourceUnchecked(
                  "admission/records/valid/" + evaluation.path("profileId").textValue() + ".json")),
          candidateBytes);

      JsonNode append = evaluation.path("append");
      assertNotNull(append);
      return switch (append.path("status").textValue()) {
        case "committed", "existing" ->
            authenticated(append.path("record").textValue(), append.path("authenticatedService"));
        case "conflict" -> throw adapterFailure(AdmissionReason.RECORD_CONFLICT, append);
        case "unauthenticated" ->
            throw adapterFailure(AdmissionReason.LOG_AUTHENTICATION_FAILED, append);
        case "integrity-failed" ->
            throw adapterFailure(AdmissionReason.APPEND_INTEGRITY_NOT_ESTABLISHED, append);
        case "unavailable" -> throw adapterFailure(AdmissionReason.LOG_UNAVAILABLE, append);
        case "indeterminate" -> throw adapterFailure(AdmissionReason.LOG_INDETERMINATE, append);
        case "commit-failed" -> throw adapterFailure(AdmissionReason.COMMIT_FAILED, append);
        default -> throw new IllegalArgumentException("Unknown append outcome: " + append);
      };
    }

    private static AdmissionAdapterException adapterFailure(
        AdmissionReason reason, JsonNode outcome) {
      return new AdmissionAdapterException(reason, "manifest outcome " + outcome.path("status"));
    }

    private static AuthenticatedAdmissionRecord authenticated(String path, JsonNode service) {
      return new AuthenticatedAdmissionRecord(resourceUnchecked(path), principal(service));
    }
  }

  private static byte[] resourceUnchecked(String path) {
    try {
      return resource(path);
    } catch (IOException error) {
      throw new UncheckedIOException(error);
    }
  }

  private static byte[] canonicalize(byte[] value) {
    try {
      return CanonicalJson.canonicalize(value);
    } catch (IOException error) {
      throw new UncheckedIOException(error);
    }
  }

  private static Principal principal(JsonNode value) {
    return new Principal(value.path("type").textValue(), value.path("id").textValue());
  }
}
