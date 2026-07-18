package org.missionweaveprotocol.examples;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;
import org.missionweaveprotocol.sdk.Base64Url;
import org.missionweaveprotocol.sdk.CanonicalJson;
import org.missionweaveprotocol.sdk.Ed25519;
import org.missionweaveprotocol.sdk.Principal;
import org.missionweaveprotocol.sdk.ResolvedKey;
import org.missionweaveprotocol.sdk.SignedDocumentCodec;
import org.missionweaveprotocol.sdk.SignedDocumentKind;
import org.missionweaveprotocol.sdk.SigningKey;

/** Validate, hash, sign, and verify a packaged MissionWeaveProtocol Command. */
public final class ValidateAndSignExample {
  private ValidateAndSignExample() {}

  public static void main(String[] arguments) throws Exception {
    run(System.out);
  }

  public static void run(PrintStream output) throws Exception {
    Objects.requireNonNull(output, "output");
    var command =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            org.missionweaveprotocol.sdk.StrictJson.parse(
                resource("cryptography/vectors/signed-documents/valid/command.json"));
    command.remove("signature");

    Ed25519.EncodedKeyPair keys = Ed25519.generateKeyPair();
    SigningKey signingKey =
        new SigningKey() {
          @Override
          public String keyId() {
            return "urn:missionweaveprotocol:key:example";
          }

          @Override
          public byte[] sign(byte[] signingBytes) {
            return Base64Url.decode(Ed25519.sign(signingBytes, keys.privateKey()));
          }
        };
    SignedDocumentCodec codec = new SignedDocumentCodec();
    var signed = codec.sign(SignedDocumentKind.COMMAND, command, signingKey);
    byte[] encoded = CanonicalJson.canonicalize(signed);
    var verified =
        codec.verify(
            SignedDocumentKind.COMMAND,
            encoded,
            request ->
                new ResolvedKey(
                    request.keyId(),
                    new Principal(
                        "agent", "urn:missionweaveprotocol:agent:crypto-vector-coordinator"),
                    "Ed25519",
                    keys.publicKey(),
                    "2026-01-01T00:00:00Z",
                    null,
                    null));

    output.println("Validated command.schema.json");
    output.println("Signature verified: " + verified.resolvedPrincipal().type().equals("agent"));
    output.println("Canonical hash: " + verified.canonicalHash());
  }

  private static byte[] resource(String path) throws IOException {
    try (InputStream input =
        ValidateAndSignExample.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing packaged protocol resource: " + path);
      }
      return input.readAllBytes();
    }
  }
}
