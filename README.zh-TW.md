[English](README.md) | [简体中文](README.zh-CN.md) | **繁體中文** | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)

# MissionWeaveProtocol Java SDK

用於驗證、正規化、簽署與測試
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1 資料的官方 Java 21 SDK。

> 符合性聲明：僅限 **schema-and-vector conformance only**。本 SDK 不聲明完整的執行期協定符合性。

## 需求與相依套件

- Java 21
- Maven 3.9，或儲存庫內附的 Maven Wrapper

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 協定相容性

| 項目 | 固定值 |
| --- | --- |
| SDK 座標 | `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT` |
| 協定版本 | `0.1` |
| Wire namespace | `missionweaveprotocol` |
| 協定 commit | [`00964ea9064cbf1f0eca8af21a0c57367ee14752`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/00964ea9064cbf1f0eca8af21a0c57367ee14752) |
| JSON Schema | 21 個 |
| 符合性向量 | 43 個：22 個有效，21 個無效 |

JAR 包含完整的離線成品包。[PROTOCOL_PIN.json](PROTOCOL_PIN.json)
記錄其來源、檔案數量與 SHA-256 樹狀摘要。

## 能力

- `StrictJson` 在信任邊界拒絕重複物件成員、無效 UTF-8 與尾隨資料。
- `SchemaCatalog` 將 21 個 Draft 2020-12 Schema 編譯為完全離線的登錄庫，並啟用 format 斷言。
- `FrameCodec` 嚴格解碼、驗證並正規編碼通用 MissionWeaveProtocol WebSocket frame；它不建立連線。
- `CanonicalJson` 提供 RFC 8785 JCS 與 SHA-256 識別碼。
- `Ed25519`、`Base64Url` 與 `DocumentSignatures` 提供 JDK Ed25519 簽署、無填補 base64url，以及頂層 `signature` 省略。
- `ConformanceRunner` 與 `ConformanceCli` 執行全部 43 個內建向量。

## 快速開始

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

## 可執行範例

建置流程會編譯並測試儲存庫中的三個範例：

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

## 符合性執行器

執行內建向量，或指定另一個協定成品包：

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java

./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

內建結果為 `43/43 conformance vectors passed`。

## 文件

- [用法與 API 指南](docs/usage.md)
- [相容性與符合性範圍](docs/conformance.md)

## 安全與行為邊界

- Schema 驗證檢查文件結構與 format；它不會授予權限、驗證 Agent 身分，或證明某個動作獲准。
- 簽章輔助工具不提供金鑰信任、儲存、探索、撤銷、時間戳記政策、重播防護，也不處理 session 與 membership fencing。
- `FrameCodec` 是序列化器，不是 transport、coordinator、worker scheduler、持久化儲存、重試引擎或狀態機實作。
- `43/43` 結果僅證明 schema-and-vector conformance；它不代表互通性、完整行為、安全性或生產就緒性。

## 開發

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

若本機沒有 JDK 或 Maven：

```bash
docker run --rm \
  -v missionweaveprotocol-java-m2:/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw -B -ntp verify
```

## 授權條款

Apache-2.0。請參閱 [LICENSE](LICENSE)。
