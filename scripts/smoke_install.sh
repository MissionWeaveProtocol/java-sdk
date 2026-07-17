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
import org.missionweaveprotocol.sdk.ConformanceReport;
import org.missionweaveprotocol.sdk.ConformanceRunner;
import org.missionweaveprotocol.sdk.FrameCodec;
import org.missionweaveprotocol.sdk.ProtocolBundle;

public final class Consumer {
  private Consumer() {}

  public static void main(String[] arguments) throws Exception {
    var bundle = ProtocolBundle.verifyPackaged();
    if (bundle.schemaFiles() != 21 || bundle.conformanceFiles() != 44) {
      throw new IllegalStateException("Installed protocol bundle is incomplete");
    }

    ConformanceReport report = ConformanceRunner.runPackaged();
    if (!report.passed() || report.results().size() != 43) {
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
    System.out.println("Installed SDK smoke passed: " + report.summary());
  }
}
EOF

"$MAVEN" -B -ntp -f "$CONSUMER/pom.xml" verify
