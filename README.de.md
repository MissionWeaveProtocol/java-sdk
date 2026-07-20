[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | **Deutsch**

# MissionWeaveProtocol Java SDK

Das offizielle Java-21-SDK zum Validieren, Kanonisieren, Signieren und Testen von
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1-Daten.

> Konformitätsaussage: ausschließlich **Schema- und Vektorkonformität**. Das SDK beansprucht keine vollständige Laufzeit-Protokollkonformität.

## Voraussetzungen und Abhängigkeit

- Java 21
- Maven 3.9 oder der enthaltene Maven Wrapper

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Protokollkompatibilität

| Element | Festgelegter Wert |
| --- | --- |
| SDK-Koordinaten | `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT` |
| Protokollversion | `0.1` |
| Wire namespace | `missionweaveprotocol` |
| Protokoll-Commit | [`33e47ad8a7318f942de77fb72dbb054d85881b40`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/33e47ad8a7318f942de77fb72dbb054d85881b40) |
| JSON-Schemata | 21 |
| Konformitätsvektoren | 56: 26 gültig und 30 ungültig |

Das JAR enthält das vollständige Offline-Bündel. [PROTOCOL_PIN.json](PROTOCOL_PIN.json)
dokumentiert Quelle, Dateianzahlen und SHA-256-Baum-Digests.

## Funktionen

- `StrictJson` verwirft doppelte Objektmitglieder, ungültiges UTF-8 und nachgestellte Daten an der Vertrauensgrenze.
- `SchemaCatalog` kompiliert die 21 Draft-2020-12-Schemata in eine vollständig offline arbeitende Registry mit aktivierten Format-Assertions.
- `FrameCodec` dekodiert, validiert und kanonisch kodiert generische MissionWeaveProtocol-WebSocket-Frames; er stellt keine Verbindung her.
- `CanonicalJson` bietet RFC 8785 JCS und SHA-256-Bezeichner.
- `Ed25519`, `Base64Url` und `DocumentSignatures` bieten JDK-Ed25519-Signaturen, ungepolstertes base64url und das Auslassen des obersten `signature`-Feldes.
- `SignedDocumentCodec` führt das vollständige sechsstufige Profil für signierte Dokumente aus; übergib explizit einen `SignedDocumentKind` sowie einen `SigningKey` oder einen `KeyResolver`-Adapter für eine organisationskontrollierte Agent Registry.
- `ConformanceRunner` und `ConformanceCli` führen alle 56 enthaltenen Vektoren aus.

## Schnellstart

```java
import java.nio.charset.StandardCharsets;
import org.missionweaveprotocol.sdk.FrameCodec;

public final class QuickStart {
  public static void main(String[] args) throws Exception {
    byte[] incoming = """
        {
          "protocolVersion": "0.1",
          "frameId": "urn:uuid:00000000-0000-4000-8000-000000000010",
          "frameType": "PING",
          "nonce": "cGluZw",
          "sentAt": "2026-07-17T08:00:00Z"
        }
        """.getBytes(StandardCharsets.UTF_8);

    FrameCodec codec = new FrameCodec();
    var frame = codec.decode(incoming);
    byte[] canonical = codec.encode(frame);

    System.out.println(frame.get("frameType").textValue());
    System.out.println(new String(canonical, StandardCharsets.UTF_8));
  }
}
```

Für dauerhafte signierte Objekte verwende `SignedDocumentCodec.sign(kind, unsigned, signingKey)` und
`verify(kind, receivedBytes, keyResolver)`; der Codec leitet den Typ nie ab und liefert unveränderliche Prüfnachweise.

## Ausführbare Beispiele

Der Build kompiliert und testet alle drei Repository-Beispiele:

```bash
./mvnw -q -Dexec.classpathScope=test \
  -Dexec.mainClass=org.missionweaveprotocol.examples.ValidateAndSignExample \
  exec:java

./mvnw -q -Dexec.classpathScope=test \
  -Dexec.mainClass=org.missionweaveprotocol.examples.FrameRoundTripExample \
  exec:java

./mvnw -q -Dexec.classpathScope=test \
  -Dexec.mainClass=org.missionweaveprotocol.examples.RunConformanceExample \
  exec:java
```

## Konformitäts-Runner

Führen Sie die enthaltenen Vektoren oder ein separates Protokollbündel aus:

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java

./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

Das enthaltene Ergebnis lautet `56/56 conformance vectors passed`.

## Dokumentation

- [Nutzungs- und API-Leitfaden](docs/usage.md)
- [Kompatibilitäts- und Konformitätsumfang](docs/conformance.md)

## Sicherheits- und Verhaltensgrenzen

- Schema-Validierung prüft Dokumentstruktur und Formate. Sie gewährt keine Autorität, authentifiziert keinen Agent und beweist nicht, dass eine Aktion erlaubt ist.
- Signaturhilfen bieten weder Vertrauensverwaltung für Schlüssel noch deren Speicherung, Auffindung oder Widerruf; ebenso wenig bieten sie Zeitstempelrichtlinien, Replay-Schutz oder Fencing mittels Session Epoch und Membership Epoch.
- `FrameCodec` ist ein Serialisierer, kein Transport, Koordinator, Worker-Scheduler, dauerhafter Speicher, Retry-Engine oder Zustandsmaschinenimplementierung.
- Ein `56/56`-Ergebnis belegt nur Schema- und Vektorkonformität; es stellt keine Interoperabilität, kein vollständiges Verhalten, keine Sicherheit und keine Produktionsreife fest.

## Entwicklung

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

Ohne lokale JDK- oder Maven-Installation:

```bash
docker run --rm \
  -v missionweaveprotocol-java-m2:/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw -B -ntp verify
```

## Lizenz

Apache-2.0. Siehe [LICENSE](LICENSE).
