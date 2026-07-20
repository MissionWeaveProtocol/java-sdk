package org.missionweaveprotocol.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Complete stage-4 validation and key selection over one Registry snapshot. */
final class AgentRegistryKeyResolution {
  private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
  private static final Set<String> ROOT_FIELDS = Set.of("organizationId", "bindings");
  private static final Set<String> BINDING_FIELDS =
      Set.of("keyId", "principal", "algorithm", "publicKey", "validFrom", "validityHistory");
  private static final Set<String> PRINCIPAL_FIELDS = Set.of("type", "id");
  private static final Set<String> STATUS_REQUIRED_FIELDS = Set.of("sequence", "recordedAt");
  private static final Set<String> STATUS_FIELDS =
      Set.of("sequence", "recordedAt", "validUntil", "revokedAt");

  private AgentRegistryKeyResolution() {}

  static ResolvedKey resolve(byte[] registryBytes, KeyResolutionRequest request)
      throws IOException {
    Objects.requireNonNull(registryBytes, "registryBytes");
    Objects.requireNonNull(request, "request");

    JsonNode registry = StrictJson.parse(registryBytes);
    requireExactObject(registry, ROOT_FIELDS, ROOT_FIELDS, "Registry");
    String organizationId = text(registry, "organizationId");
    JsonNode rawBindings = registry.path("bindings");
    if (!rawBindings.isArray() || rawBindings.size() == 0) {
      throw new IllegalArgumentException("Registry bindings must be a non-empty array");
    }

    Map<String, Binding> bindings = new HashMap<>();
    Map<String, KeyOwner> publicKeyOwners = new HashMap<>();
    Map<KeyTuple, String> tupleIds = new HashMap<>();
    for (JsonNode rawBinding : rawBindings) {
      Binding candidate = parseBinding(rawBinding);
      Binding binding = bindings.get(candidate.keyId);
      if (binding == null) {
        bindings.put(candidate.keyId, candidate);
        binding = candidate;
      } else {
        if (!binding.sameImmutableBinding(candidate)) {
          throw new IllegalArgumentException("Registry reuses a key ID for another binding");
        }
        binding.mergeHistory(candidate);
      }

      KeyOwner owner = new KeyOwner(candidate.keyId, candidate.principal);
      KeyOwner previousOwner = publicKeyOwners.putIfAbsent(candidate.publicKey, owner);
      if (previousOwner != null && !previousOwner.equals(owner)) {
        throw new IllegalArgumentException("Registry reuses a public key");
      }

      KeyTuple tuple = new KeyTuple(candidate.principal, candidate.algorithm, candidate.publicKey);
      String previousId = tupleIds.putIfAbsent(tuple, candidate.keyId);
      if (previousId != null && !previousId.equals(candidate.keyId)) {
        throw new IllegalArgumentException("Registry contains a key-ID alias");
      }
    }

    for (Binding binding : bindings.values()) {
      binding.foldHistory();
    }

    Binding selected = bindings.get(request.keyId());
    if (selected == null) {
      throw new IllegalArgumentException("signature.keyId is unknown");
    }
    if (request.servicePrincipalRequired()) {
      if (!selected.principal.type().equals("service")) {
        throw new IllegalArgumentException("Agent Card signer is not a service Principal");
      }
    } else if (!selected.principal.equals(request.expectedPrincipal())) {
      throw new IllegalArgumentException("resolved key is bound to the wrong Principal");
    }
    selected.requireValidAt(request.protectedInstant());
    return selected.resolved(organizationId);
  }

  private static Binding parseBinding(JsonNode rawBinding) {
    requireExactObject(rawBinding, BINDING_FIELDS, BINDING_FIELDS, "Registry binding");
    String keyId = text(rawBinding, "keyId");
    Principal principal = parsePrincipal(rawBinding.path("principal"));
    String algorithm = text(rawBinding, "algorithm");
    if (!algorithm.equals("Ed25519")) {
      throw new IllegalArgumentException("Registry key algorithm is not Ed25519");
    }

    String publicKey = text(rawBinding, "publicKey");
    byte[] publicKeyBytes = Base64Url.decode(publicKey);
    if (publicKeyBytes.length != 32) {
      throw new IllegalArgumentException("Registry public key is not 32 bytes");
    }
    Ed25519Point.validate(publicKeyBytes, false, "Registry public key");

    String validFromText = text(rawBinding, "validFrom");
    ExactInstant validFrom = ExactInstant.parse(validFromText);
    JsonNode rawHistory = rawBinding.path("validityHistory");
    if (!rawHistory.isArray()) {
      throw new IllegalArgumentException("Registry validityHistory is not an array");
    }
    TreeMap<Long, Status> history = new TreeMap<>();
    for (JsonNode rawStatus : rawHistory) {
      Status status = parseStatus(rawStatus);
      Status previous = history.putIfAbsent(status.sequence, status);
      if (previous != null && !previous.sameSemanticStatus(status)) {
        throw new IllegalArgumentException("Registry rewrites validity history");
      }
    }
    return new Binding(keyId, principal, algorithm, publicKey, validFromText, validFrom, history);
  }

  private static Principal parsePrincipal(JsonNode rawPrincipal) {
    requireExactObject(
        rawPrincipal, PRINCIPAL_FIELDS, PRINCIPAL_FIELDS, "Registry binding Principal");
    return new Principal(text(rawPrincipal, "type"), text(rawPrincipal, "id"));
  }

  private static Status parseStatus(JsonNode rawStatus) {
    requireExactObject(
        rawStatus, STATUS_REQUIRED_FIELDS, STATUS_FIELDS, "Registry validity status");
    long sequence = positiveSafeInteger(rawStatus.path("sequence"));
    ExactInstant recordedAt = ExactInstant.parse(text(rawStatus, "recordedAt"));
    String validUntilText = optionalText(rawStatus, "validUntil");
    ExactInstant validUntil = parseOptionalInstant(validUntilText);
    String revokedAtText = optionalText(rawStatus, "revokedAt");
    ExactInstant revokedAt = parseOptionalInstant(revokedAtText);
    return new Status(sequence, recordedAt, validUntilText, validUntil, revokedAtText, revokedAt);
  }

  private static void requireExactObject(
      JsonNode value, Set<String> required, Set<String> allowed, String label) {
    if (!value.isObject()) {
      throw new IllegalArgumentException(label + " is not an object");
    }
    for (String field : required) {
      if (!value.has(field)) {
        throw new IllegalArgumentException(label + " is missing field: " + field);
      }
    }
    Iterator<String> fields = value.fieldNames();
    while (fields.hasNext()) {
      String field = fields.next();
      if (!allowed.contains(field)) {
        throw new IllegalArgumentException(label + " contains unknown field: " + field);
      }
    }
  }

  private static String text(JsonNode object, String field) {
    JsonNode value = object.path(field);
    if (!value.isTextual()) {
      throw new IllegalArgumentException("Registry field is not text: " + field);
    }
    return value.textValue();
  }

  private static String optionalText(JsonNode object, String field) {
    return object.has(field) ? text(object, field) : null;
  }

  private static ExactInstant parseOptionalInstant(String text) {
    return text == null ? null : ExactInstant.parse(text);
  }

  private static long positiveSafeInteger(JsonNode value) {
    if (!value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new IllegalArgumentException("Registry sequence is not an integer");
    }
    long sequence = value.longValue();
    if (sequence < 1 || sequence > MAX_SAFE_INTEGER) {
      throw new IllegalArgumentException("Registry sequence is outside the safe range");
    }
    return sequence;
  }

  private static final class Binding {
    private final String keyId;
    private final Principal principal;
    private final String algorithm;
    private final String publicKey;
    private final String validFromText;
    private final ExactInstant validFrom;
    private final TreeMap<Long, Status> history;
    private String validUntilText;
    private ExactInstant validUntil;
    private String revokedAtText;
    private ExactInstant revokedAt;

    private Binding(
        String keyId,
        Principal principal,
        String algorithm,
        String publicKey,
        String validFromText,
        ExactInstant validFrom,
        TreeMap<Long, Status> history) {
      this.keyId = keyId;
      this.principal = principal;
      this.algorithm = algorithm;
      this.publicKey = publicKey;
      this.validFromText = validFromText;
      this.validFrom = validFrom;
      this.history = history;
    }

    private boolean sameImmutableBinding(Binding other) {
      return keyId.equals(other.keyId)
          && principal.equals(other.principal)
          && algorithm.equals(other.algorithm)
          && publicKey.equals(other.publicKey)
          && validFrom.equals(other.validFrom);
    }

    private void mergeHistory(Binding other) {
      for (Map.Entry<Long, Status> entry : other.history.entrySet()) {
        Status previous = history.putIfAbsent(entry.getKey(), entry.getValue());
        if (previous != null && !previous.sameSemanticStatus(entry.getValue())) {
          throw new IllegalArgumentException("Registry rewrites validity history");
        }
      }
    }

    private void foldHistory() {
      long expectedSequence = 1;
      ExactInstant previousRecorded = null;
      for (Status status : history.values()) {
        if (status.sequence != expectedSequence++) {
          throw new IllegalArgumentException("Registry validity history is not contiguous");
        }
        if (previousRecorded != null && status.recordedAt.compareTo(previousRecorded) < 0) {
          throw new IllegalArgumentException("Registry validity history is not append ordered");
        }
        previousRecorded = status.recordedAt;
        applyValidUntil(status);
        applyRevokedAt(status);
      }
    }

    private void applyValidUntil(Status status) {
      if (status.validUntil == null) {
        return;
      }
      if (validUntil != null) {
        int ordering = status.validUntil.compareTo(validUntil);
        if (ordering > 0) {
          throw new IllegalArgumentException("Registry moves validUntil later");
        }
        if (ordering == 0) {
          return;
        }
      }
      validUntil = status.validUntil;
      validUntilText = status.validUntilText;
    }

    private void applyRevokedAt(Status status) {
      if (status.revokedAt == null) {
        return;
      }
      if (revokedAt != null) {
        int ordering = status.revokedAt.compareTo(revokedAt);
        if (ordering > 0) {
          throw new IllegalArgumentException("Registry moves revokedAt later");
        }
        if (ordering == 0) {
          return;
        }
      }
      revokedAt = status.revokedAt;
      revokedAtText = status.revokedAtText;
    }

    private void requireValidAt(ExactInstant protectedInstant) {
      if (protectedInstant.compareTo(validFrom) < 0) {
        throw new IllegalArgumentException("signing key is not yet valid at the protected time");
      }
      if (validUntil != null && protectedInstant.compareTo(validUntil) >= 0) {
        throw new IllegalArgumentException("signing key is expired at the protected time");
      }
      if (revokedAt != null && protectedInstant.compareTo(revokedAt) >= 0) {
        throw new IllegalArgumentException("signing key is revoked at the protected time");
      }
    }

    private ResolvedKey resolved(String organizationId) {
      return new ResolvedKey(
          organizationId,
          keyId,
          principal,
          algorithm,
          publicKey,
          validFromText,
          validUntilText,
          revokedAtText);
    }
  }

  private record Status(
      long sequence,
      ExactInstant recordedAt,
      String validUntilText,
      ExactInstant validUntil,
      String revokedAtText,
      ExactInstant revokedAt) {
    private Status {
      Objects.requireNonNull(recordedAt, "recordedAt");
    }

    private boolean sameSemanticStatus(Status other) {
      return sequence == other.sequence
          && recordedAt.equals(other.recordedAt)
          && Objects.equals(validUntil, other.validUntil)
          && Objects.equals(revokedAt, other.revokedAt);
    }
  }

  private record KeyOwner(String keyId, Principal principal) {}

  private record KeyTuple(Principal principal, String algorithm, String publicKey) {}
}
