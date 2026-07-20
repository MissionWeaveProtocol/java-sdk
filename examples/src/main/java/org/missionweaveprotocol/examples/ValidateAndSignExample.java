package org.missionweaveprotocol.examples;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;
import org.missionweaveprotocol.sdk.Base64Url;
import org.missionweaveprotocol.sdk.CanonicalJson;
import org.missionweaveprotocol.sdk.Ed25519;
import org.missionweaveprotocol.sdk.KeyRegistrySnapshot;
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
    var signingFixture =
        org.missionweaveprotocol.sdk.StrictJson.parse(
            resource("cryptography/keys/signing-coordinator.json"));
    byte[] registry = resource("cryptography/keys/registry-valid.json");

    SigningKey signingKey =
        new SigningKey() {
          @Override
          public String keyId() {
            return signingFixture.path("keyId").textValue();
          }

          @Override
          public byte[] sign(byte[] signingBytes) {
            return Base64Url.decode(
                Ed25519.sign(signingBytes, signingFixture.path("seed").textValue()));
          }
        };
    SignedDocumentCodec codec = new SignedDocumentCodec();
    var signed = codec.sign(SignedDocumentKind.COMMAND, command, signingKey);
    byte[] encoded = CanonicalJson.canonicalize(signed);
    var verified =
        codec.verify(
            SignedDocumentKind.COMMAND,
            encoded,
            request -> KeyRegistrySnapshot.organizationWide(registry));

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
