package org.missionweaveprotocol.examples;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;
import org.missionweaveprotocol.sdk.CanonicalJson;
import org.missionweaveprotocol.sdk.DocumentSignatures;
import org.missionweaveprotocol.sdk.Ed25519;
import org.missionweaveprotocol.sdk.SchemaCatalog;

/** Validate, hash, sign, and verify a packaged MissionWeaveProtocol Command. */
public final class ValidateAndSignExample {
  private ValidateAndSignExample() {}

  public static void main(String[] arguments) throws Exception {
    run(System.out);
  }

  public static void run(PrintStream output) throws Exception {
    Objects.requireNonNull(output, "output");
    byte[] command = resource("conformance/vectors/valid/command.json");
    SchemaCatalog.packaged().validate("command.schema.json", command);

    Ed25519.EncodedKeyPair keys = Ed25519.generateKeyPair();
    String signature = DocumentSignatures.sign(command, keys.privateKey());
    boolean verified = DocumentSignatures.verify(command, signature, keys.publicKey());

    output.println("Validated command.schema.json");
    output.println("Signature verified: " + verified);
    output.println("Canonical hash: " + CanonicalJson.canonicalHash(command));
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
