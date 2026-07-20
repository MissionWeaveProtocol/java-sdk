#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
MAVEN="$ROOT/mvnw"
CONSUMER=$(mktemp -d "${TMPDIR:-/tmp}/missionweaveprotocol-java-consumer.XXXXXX")
trap 'rm -rf "$CONSUMER"' EXIT HUP INT TERM

"$MAVEN" -B -ntp -f "$ROOT/pom.xml" -DskipTests install

mkdir -p "$CONSUMER/src/main/java/example"
cat >"$CONSUMER/pom.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.missionweaveprotocol.smoke</groupId>
  <artifactId>java-sdk-consumer-smoke</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.missionweaveprotocol</groupId>
      <artifactId>missionweaveprotocol-sdk</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-nop</artifactId>
      <version>2.0.17</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.14.1</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.0</version>
        <executions>
          <execution>
            <id>run-consumer-smoke</id>
            <phase>verify</phase>
            <goals>
              <goal>java</goal>
            </goals>
            <configuration>
              <mainClass>example.Consumer</mainClass>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
EOF

cat >"$CONSUMER/src/main/java/example/Consumer.java" <<'EOF'
package example;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import org.missionweaveprotocol.sdk.ConformanceReport;
import org.missionweaveprotocol.sdk.ConformanceRunner;
import org.missionweaveprotocol.sdk.FrameCodec;
import org.missionweaveprotocol.sdk.KeyRegistryCompleteness;
import org.missionweaveprotocol.sdk.KeyRegistrySnapshot;
import org.missionweaveprotocol.sdk.ProtocolBundle;
import org.missionweaveprotocol.sdk.SignedDocumentCodec;
import org.missionweaveprotocol.sdk.SignedDocumentKind;

public final class Consumer {
  private Consumer() {}

  public static void main(String[] arguments) throws Exception {
    var bundle = ProtocolBundle.verifyPackaged();
    if (bundle.schemaFiles() != 21 || bundle.conformanceFiles() != 57) {
      throw new IllegalStateException("Installed protocol bundle is incomplete");
    }

    ConformanceReport report = ConformanceRunner.runPackaged();
    if (!report.passed() || report.results().size() != 56) {
      throw new IllegalStateException(report.summary());
    }

    byte[] ping = """
        {
          "protocolVersion": "0.1",
          "frameId": "urn:uuid:00000000-0000-4000-8000-000000000099",
          "frameType": "PING",
          "nonce": "cGluZw",
          "sentAt": "2026-07-17T08:00:00Z"
        }
        """.getBytes(StandardCharsets.UTF_8);
    new FrameCodec().decode(ping);

    byte[] command =
        resource(
            "cryptography/vectors/signed-documents/valid/command.json",
            "Installed JAR is missing the golden Command");
    byte[] registryBytes =
        resource(
            "cryptography/keys/registry-valid.json",
            "Installed JAR is missing the complete Registry fixture");
    KeyRegistrySnapshot snapshot = KeyRegistrySnapshot.organizationWide(registryBytes);
    if (snapshot.completeness() != KeyRegistryCompleteness.ORGANIZATION_WIDE) {
      throw new IllegalStateException("Installed SDK lost the Registry completeness assertion");
    }
    var verified =
        new SignedDocumentCodec()
            .verify(
                SignedDocumentKind.COMMAND,
                command,
                request -> snapshot);
    if (!verified.signingHash().equals(
        "sha256:6655c5d67ae3ecc19a4ed04bda7f1372aeaafc7adf939a77715de96ef2100695")) {
      throw new IllegalStateException("Installed JAR failed golden Command verification");
    }
    if (!verified
        .resolvedKey()
        .organizationId()
        .equals("urn:missionweaveprotocol:organization:acme")) {
      throw new IllegalStateException("Installed SDK lost the resolved Organization evidence");
    }
    System.out.println(
        "Installed SDK smoke passed: " + report.summary() + "; " + verified.signingHash());
  }

  private static byte[] resource(String path, String missingMessage) throws Exception {
    try (InputStream input = Consumer.class.getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IllegalStateException(missingMessage);
      }
      return input.readAllBytes();
    }
  }
}
EOF

"$MAVEN" -B -ntp -f "$CONSUMER/pom.xml" verify
