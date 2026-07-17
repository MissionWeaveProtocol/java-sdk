[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | **Español** | [Français](README.fr.md) | [Deutsch](README.de.md)

# SDK de Java de MissionWeaveProtocol

SDK oficial para Java 21 destinado a validar, canonicalizar, firmar y probar
datos de [MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1.

> Declaración de conformidad: únicamente **schema-and-vector conformance only**. El SDK no declara conformidad completa del protocolo en tiempo de ejecución.

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
| Commit del protocolo | [`00964ea9064cbf1f0eca8af21a0c57367ee14752`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/00964ea9064cbf1f0eca8af21a0c57367ee14752) |
| JSON Schema | 21 |
| Vectores de conformidad | 43: 22 válidos y 21 no válidos |

El JAR contiene el paquete completo para uso sin conexión. [PROTOCOL_PIN.json](PROTOCOL_PIN.json)
registra su origen, el número de archivos y los resúmenes SHA-256 del árbol.

## Capacidades

- `StrictJson` rechaza miembros de objeto duplicados, UTF-8 no válido y datos sobrantes en el límite de confianza.
- `SchemaCatalog` compila los 21 Schema Draft 2020-12 en un registro totalmente sin conexión con las aserciones de format habilitadas.
- `FrameCodec` decodifica, valida y codifica canónicamente frame WebSocket genéricos de MissionWeaveProtocol; no crea una conexión.
- `CanonicalJson` proporciona JCS RFC 8785 e identificadores SHA-256.
- `Ed25519`, `Base64Url` y `DocumentSignatures` proporcionan firmas Ed25519 del JDK, base64url sin relleno y omisión del `signature` de nivel superior.
- `ConformanceRunner` y `ConformanceCli` ejecutan los 43 vectores incluidos.

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

## Ejemplos ejecutables

La compilación compila y prueba los tres ejemplos del repositorio:

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

El resultado incluido es `43/43 conformance vectors passed`.

## Documentación

- [Guía de uso y API](docs/usage.md)
- [Compatibilidad y alcance de conformidad](docs/conformance.md)

## Límites de seguridad y comportamiento

- La validación de Schema comprueba la estructura y los format del documento. No concede autoridad, no autentica un Agent ni demuestra que una acción esté permitida.
- Los auxiliares de firma no proporcionan confianza, almacenamiento, descubrimiento o revocación de claves, política de marcas de tiempo, prevención de repetición ni fencing de session y membership.
- `FrameCodec` es un serializador, no un transport, coordinator, worker scheduler, almacén duradero, motor de reintentos ni implementación de máquina de estados.
- Un resultado `43/43` demuestra únicamente schema-and-vector conformance; no establece interoperabilidad, comportamiento completo, seguridad ni preparación para producción.

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
