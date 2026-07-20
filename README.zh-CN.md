[English](README.md) | **简体中文** | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)

# MissionWeaveProtocol Java SDK

用于验证、规范化、签名和测试
[MissionWeaveProtocol](https://github.com/missionweaveprotocol/missionweaveprotocol)
0.1 数据的官方 Java 21 SDK。

> 符合性声明：**仅限 Schema 与测试向量符合性**。本 SDK 不声明完整的运行时协议符合性。

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
| 协议 commit | [`33e47ad8a7318f942de77fb72dbb054d85881b40`](https://github.com/missionweaveprotocol/missionweaveprotocol/commit/33e47ad8a7318f942de77fb72dbb054d85881b40) |
| JSON Schema | 21 个 |
| 符合性向量 | 56 个：26 个有效，30 个无效 |

JAR 包含完整的离线产物包。[PROTOCOL_PIN.json](PROTOCOL_PIN.json)
记录其来源、文件数量和 SHA-256 树摘要。

## 能力

- `StrictJson` 在信任边界拒绝重复对象成员、无效 UTF-8 和尾随数据。
- `SchemaCatalog` 将 21 个 Draft 2020-12 Schema 编译为完全离线的注册表，并启用格式断言。
- `FrameCodec` 严格解码、验证并规范编码通用 MissionWeaveProtocol WebSocket 帧；它不创建连接。
- `CanonicalJson` 提供 RFC 8785 JCS 和 SHA-256 标识符。
- `Ed25519`、`Base64Url` 与 `DocumentSignatures` 提供 JDK Ed25519 签名、无填充 base64url，以及顶层 `signature` 省略。
- `SignedDocumentCodec` 执行完整的六阶段签名文档流程。`KeyResolver` 接收
  `KeyResolutionRequest`，并返回通过 `KeyRegistrySnapshot.organizationWide(registryBytes)`
  创建、包含完整 Registry 字节的 `KeyRegistrySnapshot`，而不是已选定的 `ResolvedKey`。
- `ORGANIZATION_WIDE` 是受信任适配器作出的断言，而不是完整性证明。它表示这些字节
  代表由组织控制的单个 Agent Registry 的一个自洽、权威且适用于本次验证决策的 Registry
  修订版本，并涵盖组织范围内的全部密钥绑定和完整留存的有效性历史记录。
  `request.keyId()` 仅提供路由上下文，不得用于筛选 Registry 或只返回局部投影。
- 编解码器将这些字节视为不受信任的数据；它先验证每个密钥绑定、全局不得复用密钥或
  建立别名的约束，以及完整的有效性历史记录，再选择密钥。`KeyRegistrySnapshot` 为 `null`、
  完整性为 `PARTIAL` 或 `UNSPECIFIED`，或 Registry 证据为空、不可用或格式错误时，
  均会在密钥解析阶段安全拒绝（fail closed）。编解码器生成的 `ResolvedKey` 会保留
  Registry 的 `organizationId`。
- `ConformanceRunner` 与 `ConformanceCli` 运行全部 56 个内置向量。

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

对于持久化签名对象，调用 `SignedDocumentCodec.sign(kind, unsigned, signingKey)` 与
`verify(kind, receivedBytes, keyResolver)`；编解码器不会推断文档种类，并返回不可变的
验签证据，包括接收的原始字节、签名输入字节及其哈希，以及完整文档的规范字节及其哈希。

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

内置结果为 `56/56 conformance vectors passed`。

## 文档

- [用法与 API 指南](docs/usage.md)
- [兼容性与符合性范围](docs/conformance.md)

## 安全与行为边界

- Schema 验证检查文档结构和格式；它不会授予权限、认证 Agent，或证明某个动作被允许。
- 签名辅助工具不提供密钥信任、存储、发现、吊销、时间戳策略、重放防护，也不提供通过 Session Epoch 与 Membership Epoch 使旧 Session 权限或旧 Membership 权限失效的 fencing。
- `FrameCodec` 是序列化器，不是传输层、Coordinator、Worker Scheduler、持久化存储、重试引擎或状态机实现。
- `56/56` 结果仅证明 Schema 与测试向量符合性；它不代表互操作性、完整行为、安全性或生产就绪性。

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
