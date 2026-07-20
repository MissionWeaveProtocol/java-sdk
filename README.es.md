[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | **Español** | [Français](README.fr.md) | [Deutsch](README.de.md)

# SDK de Java de MissionWeaveProtocol

SDK oficial para Java 21 destinado a validar, canonicalizar, firmar y probar
datos de [MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1.

> Declaración de conformidad: únicamente **conformidad con esquemas y vectores**. El SDK no declara conformidad completa del protocolo en tiempo de ejecución.

## Requisitos y dependencia

- Java 21
- Maven 3.9 o el Maven Wrapper incluido

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Compatibilidad del protocolo

| Elemento | Valor fijado |
| --- | --- |
| Coordenadas del SDK | `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT` |
| Versión del protocolo | `0.1` |
| Wire namespace | `missionweaveprotocol` |
| Commit del protocolo | [`33e47ad8a7318f942de77fb72dbb054d85881b40`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/33e47ad8a7318f942de77fb72dbb054d85881b40) |
| JSON Schema | 21 |
| Vectores de conformidad | 56: 26 válidos y 30 no válidos |

El JAR contiene el paquete completo para uso sin conexión. [PROTOCOL_PIN.json](PROTOCOL_PIN.json)
registra su origen, el número de archivos y los resúmenes SHA-256 del árbol.

## Capacidades

- `StrictJson` rechaza miembros de objeto duplicados, UTF-8 no válido y datos sobrantes en el límite de confianza.
- `SchemaCatalog` compila los 21 esquemas de JSON Schema Draft 2020-12 en un registro totalmente sin conexión con las aserciones de formato habilitadas.
- `FrameCodec` decodifica, valida y codifica canónicamente tramas WebSocket genéricas de MissionWeaveProtocol; no crea una conexión.
- `CanonicalJson` proporciona JCS RFC 8785 e identificadores SHA-256.
- `Ed25519`, `Base64Url` y `DocumentSignatures` proporcionan firmas Ed25519 del JDK, base64url sin relleno y omisión del `signature` de nivel superior.
- `SignedDocumentCodec` ejecuta el perfil completo de documento firmado en seis etapas; recibe un `SignedDocumentKind` explícito, un `SigningKey` o un adaptador `KeyResolver` para un Agent Registry controlado por la organización.
- `ConformanceRunner` y `ConformanceCli` ejecutan los 56 vectores incluidos.

## Inicio rápido

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

Para objetos firmados duraderos, usa `SignedDocumentCodec.sign(kind, unsigned, signingKey)` y
`verify(kind, receivedBytes, keyResolver)`; el códec no infiere el tipo y devuelve evidencia de verificación inmutable.

## Ejemplos ejecutables

Los tres ejemplos del repositorio se compilan y prueban durante el proceso de compilación:

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

## Ejecutor de conformidad

Ejecuta los vectores incluidos o un paquete de protocolo separado:

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java

./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

El resultado incluido es `56/56 conformance vectors passed`.

## Documentación

- [Guía de uso y API](docs/usage.md)
- [Compatibilidad y alcance de conformidad](docs/conformance.md)

## Límites de seguridad y comportamiento

- La validación de Schema comprueba la estructura y los formatos del documento. No concede autoridad, no autentica un Agent ni demuestra que una acción esté permitida.
- Los auxiliares de firma no gestionan la confianza en las claves, su almacenamiento, descubrimiento o revocación; tampoco proporcionan políticas de marcas de tiempo, prevención de repetición ni fencing mediante Session Epoch y Membership Epoch que invalide las autoridades obsoletas.
- `FrameCodec` es un serializador, no un transporte, coordinador, planificador de Workers, almacén duradero, motor de reintentos ni implementación de máquina de estados.
- Un resultado `56/56` demuestra únicamente conformidad con esquemas y vectores; no establece interoperabilidad, comportamiento completo, seguridad ni preparación para producción.

## Desarrollo

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

Sin una instalación local de JDK o Maven:

```bash
docker run --rm \
  -v missionweaveprotocol-java-m2:/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw -B -ntp verify
```

## Licencia

Apache-2.0. Consulta [LICENSE](LICENSE).
