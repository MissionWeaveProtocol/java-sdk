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
| Protokoll-Commit | [`f7e70a72c76bbeb5014c186cd820aac2112f0dde`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/f7e70a72c76bbeb5014c186cd820aac2112f0dde) |
| JSON-Schemata | 22 |
| Konformitätsvektoren | 58: 27 gültig und 31 ungültig |
| Kryptografie-Auswertungen | [62](cryptography/README.md) |
| Admission-Auswertungen | [30: 12 vollständig und 18 abgelehnt](admission/README.md) |

Das JAR enthält das vollständige Offline-Bündel. [PROTOCOL_PIN.json](PROTOCOL_PIN.json)
dokumentiert Quelle, Dateianzahlen und SHA-256-Baum-Digests.

## Funktionen

- `StrictJson` verwirft doppelte Objektmitglieder, ungültiges UTF-8 und nachgestellte Daten an der Vertrauensgrenze.
- `SchemaCatalog` kompiliert die 22 Draft-2020-12-Schemata in eine vollständig offline arbeitende Registry mit aktivierten Format-Assertions.
- `FrameCodec` dekodiert, validiert und kanonisch kodiert generische MissionWeaveProtocol-WebSocket-Frames; er stellt keine Verbindung her.
- `CanonicalJson` bietet RFC 8785 JCS und SHA-256-Bezeichner.
- `Ed25519`, `Base64Url` und `DocumentSignatures` bieten JDK-Ed25519-Signaturen, ungepolstertes base64url und das Auslassen des obersten `signature`-Feldes.
- `SignedDocumentCodec` führt das vollständige sechsstufige Profil für signierte Dokumente aus.
  Ein `KeyResolver` erhält einen `KeyResolutionRequest` und gibt einen mit
  `KeyRegistrySnapshot.organizationWide(registryBytes)` erzeugten `KeyRegistrySnapshot` zurück,
  der die vollständigen Registry-Bytes enthält, niemals einen bereits ausgewählten `ResolvedKey`.
- `ORGANIZATION_WIDE` ist die Zusicherung eines vertrauenswürdigen Adapters, kein
  Vollständigkeitsnachweis. Sie besagt, dass diese Bytes eine kohärente, maßgebliche und für die
  Prüfentscheidung anwendbare Revision genau einer organisationskontrollierten Agent Registry mit
  allen organisationsweiten Bindungen und der vollständig aufbewahrten Gültigkeitshistorie
  abdecken. `request.keyId()` dient ausschließlich als Routing-Kontext und darf niemals dazu
  verwendet werden, die Registry zu filtern oder eine Teilprojektion davon zurückzugeben.
- Der Codec behandelt die Bytes als nicht vertrauenswürdig und validiert vor der Schlüsselauswahl
  jede Bindung, die globalen Invarianten gegen Wiederverwendung und Aliase sowie die vollständige
  Gültigkeitshistorie. Ist der Nachweis `PARTIAL`, `UNSPECIFIED`, `null`, leer, nicht verfügbar oder
  fehlerhaft, bricht die Schlüsselauflösung nach dem Fail-Closed-Prinzip ab; vom Codec erzeugte
  Nachweise behalten `organizationId` bei.
- `AdmissionService.admitFirst` bezieht über `AdmissionCurrentKeyResolver` vollständige aktuelle
  Registry-Nachweise für eine neue Admission; `verifyHistoricalAdmission` verwendet den vorhandenen
  historischen `KeyResolver`. Beide Pfade führen vor Entscheidungen des authentifizierten Admission
  Log alle sechs Stufen erneut aus und akzeptieren keine vom Aufrufer gelieferten Wahrheitswerte für
  Vertrauen, Authentifizierung oder Integrität.
- `ConformanceRunner` und `ConformanceCli` führen alle 58 enthaltenen Vektoren aus; die Admission-Suite
  führt 30 Auswertungen mit 12 vollständigen und 18 abgelehnten Ergebnissen aus.

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
`verify(kind, receivedBytes, keyResolver)`; der Codec leitet den Typ nie ab und liefert
unveränderliche Prüfnachweise, die die empfangenen Bytes, die zum Signieren verwendeten Bytes und
deren Hash sowie die kanonischen Bytes des vollständigen Dokuments und deren Hash enthalten.

Für dauerhafte First-Admission-Nachweise verwende `AdmissionService.admitFirst` mit aktuellen
Registry-Nachweisen, einem authentifizierten, nur anhängenden `AdmissionLog` und einem
`TrustedAdmissionContext`. `verifyHistoricalAdmission` verwendet aufbewahrte historische
Registry-Nachweise, verlangt einen vorhandenen validierten Datensatz und hängt keinen fehlenden an.

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

Das enthaltene Ergebnis lautet `58/58 conformance vectors passed`.

## Dokumentation

- [Nutzungs- und API-Leitfaden](docs/usage.md)
- [Kompatibilitäts- und Konformitätsumfang](docs/conformance.md)

## Sicherheits- und Verhaltensgrenzen

- Schema-Validierung prüft Dokumentstruktur und Formate. Sie gewährt keine Autorität, authentifiziert keinen Agent und beweist nicht, dass eine Aktion erlaubt ist.
- Signaturhilfen bieten weder Vertrauensverwaltung für Schlüssel noch deren Speicherung, Auffindung oder Widerruf; ebenso wenig bieten sie Zeitstempelrichtlinien, Replay-Schutz oder Fencing mittels Session Epoch und Membership Epoch.
- Admission hängt davon ab, dass Deployment-Adapter authentifizierte Serviceidentität, autorisierte
  Schreibvorgänge und Nur-Anhängen-Integrität gewährleisten; eine erfolgreiche SDK-Validierung
  beweist diese Deployment-Eigenschaften nicht selbst.
- `FrameCodec` ist ein Serialisierer, kein Transport, Koordinator, Worker-Scheduler, dauerhafter Speicher, Retry-Engine oder Zustandsmaschinenimplementierung.
- Ein `58/58`-Ergebnis zusammen mit 62 Kryptografie- und 30 Admission-Auswertungen belegt nur
  begrenzte Konformität; es stellt keine Interoperabilität, kein vollständiges Verhalten, keine
  Sicherheit und keine Produktionsreife fest.

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
