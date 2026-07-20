[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | **Français** | [Deutsch](README.de.md)

# SDK Java MissionWeaveProtocol

SDK Java 21 officiel pour valider, canonicaliser, signer et tester les données
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1.

> Déclaration de conformité : **conformité limitée aux schémas et aux vecteurs**. Le SDK ne revendique pas une conformité complète du protocole à l’exécution.

## Prérequis et dépendance

- Java 21
- Maven 3.9 ou le Maven Wrapper inclus

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Compatibilité du protocole

| Élément | Valeur épinglée |
| --- | --- |
| Coordonnées du SDK | `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT` |
| Version du protocole | `0.1` |
| Wire namespace | `missionweaveprotocol` |
| Commit du protocole | [`33e47ad8a7318f942de77fb72dbb054d85881b40`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/33e47ad8a7318f942de77fb72dbb054d85881b40) |
| Schémas JSON | 21 |
| Vecteurs de conformité | 56 : 26 valides et 30 invalides |

Le JAR contient le paquet hors ligne complet. [PROTOCOL_PIN.json](PROTOCOL_PIN.json)
enregistre sa provenance, le nombre de fichiers et les empreintes SHA-256 de l’arbre.

## Capacités

- `StrictJson` rejette les membres d’objet en double, l’UTF-8 invalide et les données superflues en fin d’entrée au niveau de la frontière de confiance.
- `SchemaCatalog` compile les 21 schémas Draft 2020-12 dans un registre entièrement hors ligne avec les assertions de format activées.
- `FrameCodec` décode, valide et encode canoniquement des trames WebSocket MissionWeaveProtocol génériques ; il ne crée pas de connexion.
- `CanonicalJson` fournit JCS RFC 8785 et des identifiants SHA-256.
- `Ed25519`, `Base64Url` et `DocumentSignatures` fournissent les signatures Ed25519 du JDK, base64url sans remplissage et l’omission du `signature` de premier niveau.
- `SignedDocumentCodec` applique le profil complet de document signé en six étapes. Un
  `KeyResolver` reçoit un `KeyResolutionRequest` et renvoie un `KeyRegistrySnapshot` créé avec
  `KeyRegistrySnapshot.organizationWide(registryBytes)` et contenant l’intégralité des octets du
  Registry, jamais un `ResolvedKey` déjà sélectionné.
- `ORGANIZATION_WIDE` est l’assertion d’un adaptateur de confiance, et non une preuve de
  complétude. Elle indique que ces octets couvrent une révision cohérente faisant autorité,
  applicable à la décision de vérification, d’un unique Agent Registry contrôlé par
  l’organisation, avec toutes les liaisons de l’organisation et l’intégralité de son historique
  de validité conservé. `request.keyId()` sert uniquement de contexte de routage et ne doit jamais
  servir à filtrer le Registry ni à en renvoyer une projection partielle.
- Le codec traite les octets comme des données non fiables et valide chaque liaison, les
  invariants globaux interdisant la réutilisation et les alias, ainsi que l’historique de validité
  complet avant de sélectionner la clé. Si la preuve est `PARTIAL`, `UNSPECIFIED`, `null` ou vide,
  si elle est indisponible ou si elle est mal formée, la résolution de clé échoue en mode fermé ;
  les preuves produites par le codec conservent `organizationId`.
- `ConformanceRunner` et `ConformanceCli` exécutent les 56 vecteurs inclus.

## Démarrage rapide

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

Pour les objets signés durables, utilisez `SignedDocumentCodec.sign(kind, unsigned, signingKey)` et
`verify(kind, receivedBytes, keyResolver)` ; le codec n’infère jamais le type et renvoie des
preuves de vérification immuables comprenant les octets reçus, les octets utilisés pour la
signature et leur hachage, ainsi que les octets canoniques du document complet et leur hachage.

## Exemples exécutables

Le processus de compilation construit et teste les trois exemples du dépôt :

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

## Outil de conformité

Exécutez les vecteurs inclus ou un paquet de protocole distinct :

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java

./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

Le résultat inclus est `56/56 conformance vectors passed`.

## Documentation

- [Guide d’utilisation et d’API](docs/usage.md)
- [Compatibilité et portée de conformité](docs/conformance.md)

## Limites de sécurité et de comportement

- La validation des schémas vérifie la forme et les formats du document. Elle n’accorde aucune autorité, n’authentifie pas un Agent et ne prouve pas qu’une action est autorisée.
- Les outils de signature ne gèrent ni la confiance accordée aux clés, ni leur stockage, leur découverte ou leur révocation ; ils n’offrent pas non plus de politique d’horodatage, de prévention des rejeux ou de fencing par Session Epoch et Membership Epoch invalidant les autorités obsolètes.
- `FrameCodec` est un sérialiseur, pas un transport, un coordinateur, un ordonnanceur de Workers, un stockage durable, un moteur de nouvelle tentative ou une implémentation de machine à états.
- Un résultat `56/56` démontre uniquement une conformité limitée aux schémas et aux vecteurs ; il n’établit ni interopérabilité, ni comportement complet, ni sécurité, ni aptitude à la production.

## Développement

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

Sans installation locale du JDK ou de Maven :

```bash
docker run --rm \
  -v missionweaveprotocol-java-m2:/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw -B -ntp verify
```

## Licence

Apache-2.0. Voir [LICENSE](LICENSE).
