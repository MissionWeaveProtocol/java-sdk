[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | **日本語** | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)

# MissionWeaveProtocol Java SDK

[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1 データの検証、正規化、署名、テストを行う公式 Java 21 SDK です。

> 適合性の表明範囲は **Schema とテストベクトルへの適合のみ**です。完全なランタイムプロトコル適合性は表明しません。

## 要件と依存関係

- Java 21
- Maven 3.9、または同梱の Maven Wrapper

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## プロトコル互換性

| 項目 | 固定値 |
| --- | --- |
| SDK 座標 | `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT` |
| プロトコルバージョン | `0.1` |
| Wire namespace | `missionweaveprotocol` |
| プロトコル commit | [`6f10987627d62fb296e3490ceceb5539b1e94b70`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/6f10987627d62fb296e3490ceceb5539b1e94b70) |
| JSON Schema | 21 |
| 適合性ベクトル | 52：有効 25、無効 27 |

JAR には完全なオフラインバンドルが含まれます。[PROTOCOL_PIN.json](PROTOCOL_PIN.json)
に取得元、ファイル数、SHA-256 ツリーダイジェストを記録しています。

## 機能

- `StrictJson` は信頼境界で、重複したオブジェクトメンバー、不正な UTF-8、末尾データを拒否します。
- `SchemaCatalog` は 21 個の Draft 2020-12 Schema を、format assertion を有効にした完全オフラインのレジストリへコンパイルします。
- `FrameCodec` は汎用 MissionWeaveProtocol WebSocket フレームを厳密にデコード、検証、正規エンコードします。接続自体は作成しません。
- `CanonicalJson` は RFC 8785 JCS と SHA-256 識別子を提供します。
- `Ed25519`、`Base64Url`、`DocumentSignatures` は JDK Ed25519 署名、パディングなし base64url、トップレベル `signature` の除外を提供します。
- `ConformanceRunner` と `ConformanceCli` は同梱された 52 ベクトルをすべて実行します。

## クイックスタート

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

## 実行可能なサンプル

ビルドはリポジトリ内の 3 つのサンプルをコンパイルし、テストします。

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

## 適合性ランナー

同梱ベクトル、または別のプロトコルバンドルを実行します。

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java

./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

同梱結果は `52/52 conformance vectors passed` です。

## ドキュメント

- [使用方法と API ガイド](docs/usage.md)
- [互換性と適合性の範囲](docs/conformance.md)

## セキュリティと動作上の境界

- Schema 検証は文書構造と形式を確認します。権限の付与、Agent の認証、アクションの許可証明は行いません。
- 署名ヘルパーは、鍵の信頼、保管、探索、失効、タイムスタンプポリシー、リプレイ防止、Session Epoch と Membership Epoch のフェンシングを提供しません。
- `FrameCodec` はシリアライザーであり、トランスポート、Coordinator、Worker Scheduler、永続ストア、再試行エンジン、状態機械の実装ではありません。
- `52/52` は Schema とテストベクトルへの適合のみを示します。相互運用性、完全な動作、セキュリティ、本番準備完了を保証しません。

## 開発

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

ローカルに JDK または Maven がない場合：

```bash
docker run --rm \
  -v missionweaveprotocol-java-m2:/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw -B -ntp verify
```

## ライセンス

Apache-2.0。[LICENSE](LICENSE) を参照してください。
