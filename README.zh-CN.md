[English](README.md) | **简体中文** | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)

# MissionWeaveProtocol Java SDK

用于验证、规范化、签名和测试
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1 数据的官方 Java 21 SDK。

> 符合性声明：仅限 **schema-and-vector conformance only**。本 SDK 不声明完整的运行时协议符合性。

## 要求与依赖

- Java 21
- Maven 3.9，或仓库内置的 Maven Wrapper

```xml
<dependency>
  <groupId>org.missionweaveprotocol</groupId>
  <artifactId>missionweaveprotocol-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 协议兼容性

| 项目 | 固定值 |
| --- | --- |
| SDK 坐标 | `org.missionweaveprotocol:missionweaveprotocol-sdk:0.1.0-SNAPSHOT` |
| 协议版本 | `0.1` |
| Wire namespace | `missionweaveprotocol` |
| 协议 commit | [`6f10987627d62fb296e3490ceceb5539b1e94b70`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/6f10987627d62fb296e3490ceceb5539b1e94b70) |
| JSON Schema | 21 个 |
| 符合性向量 | 52 个：25 个有效，27 个无效 |

JAR 包含完整的离线产物包。[PROTOCOL_PIN.json](PROTOCOL_PIN.json)
记录其来源、文件数量和 SHA-256 树摘要。

## 能力

- `StrictJson` 在信任边界拒绝重复对象成员、无效 UTF-8 和尾随数据。
- `SchemaCatalog` 将 21 个 Draft 2020-12 Schema 编译为完全离线的注册表，并启用 format 断言。
- `FrameCodec` 严格解码、验证并规范编码通用 MissionWeaveProtocol WebSocket frame；它不创建连接。
- `CanonicalJson` 提供 RFC 8785 JCS 和 SHA-256 标识符。
- `Ed25519`、`Base64Url` 与 `DocumentSignatures` 提供 JDK Ed25519 签名、无填充 base64url，以及顶层 `signature` 省略。
- `ConformanceRunner` 与 `ConformanceCli` 运行全部 52 个内置向量。

## 快速开始

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

## 可运行示例

构建会编译并测试仓库中的三个示例：

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

## 符合性运行器

运行内置向量，或指定另一个协议产物包：

```bash
./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  exec:java

./mvnw -q \
  -Dexec.mainClass=org.missionweaveprotocol.sdk.cli.ConformanceCli \
  -Dexec.args="--root ../missionweaveprotocol" \
  exec:java
```

内置结果为 `52/52 conformance vectors passed`。

## 文档

- [用法与 API 指南](docs/usage.md)
- [兼容性与符合性范围](docs/conformance.md)

## 安全与行为边界

- Schema 验证检查文档结构和 format；它不会授予权限、认证 Agent，或证明某个动作被允许。
- 签名辅助工具不提供密钥信任、存储、发现、吊销、时间戳策略、重放防护，也不处理 session 与 membership fencing。
- `FrameCodec` 是序列化器，不是 transport、coordinator、worker scheduler、持久化存储、重试引擎或状态机实现。
- `52/52` 结果仅证明 schema-and-vector conformance；它不代表互操作性、完整行为、安全性或生产就绪性。

## 开发

```bash
python3 scripts/check_repository_policy.py
python3 scripts/check_documentation.py
./mvnw -B -ntp verify
scripts/smoke_install.sh
```

如果本机没有 JDK 或 Maven：

```bash
docker run --rm \
  -v missionweaveprotocol-java-m2:/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw -B -ntp verify
```

## 许可证

Apache-2.0。参见 [LICENSE](LICENSE)。
