# ActionGate

ActionGate 是一个面向高风险工具调用的 Agent 发布门禁与可恢复执行参考实现。项目以企业售后工单为演示场景，围绕工具选择、参数校验、人工审批、幂等副作用、轨迹评测和故障恢复建立可复现闭环。

## 当前状态

当前版本为 **MVP 契约骨架**，采用 Java 21、Spring Boot 3 和 Maven 多模块结构：

- Workflow、Tool、Policy 使用 Draft 2020-12 JSON Schema，通过 networknt 校验器验证。
- 工作流校验节点唯一性、入口、跳转引用、可达性和无环结构；支持关联工具目录校验。
- Tool 契约及实际参数均可校验，副作用工具必须声明必填、非空的幂等键。
- Policy 沿用方案书的 `when / assert / on_violation` 结构，支持四种初始规则，拒绝未知字段和重复规则 ID。
- Java records 表达工作流版本、工具、策略、运行、审批、审计事件、评测案例和发布决策。
- 控制面提供状态接口和 Actuator 健康检查。

**本轮没有实现工作流执行或策略求值。** Schema 合法不代表动作获准执行；审批有效性、退款金额与订单金额比较、订单状态检查、实际幂等副作用、Temporal、数据库、模型调用和评测 CLI 均属于后续阶段。

## 快速开始

需要 JDK 21，设置 `JAVA_HOME` 并将其 `bin` 加入 `PATH`。仓库包含 Maven Wrapper，自动下载并校验 Maven 3.9.11，无需单独安装 Maven。

Windows PowerShell，在仓库根目录执行：

```powershell
.\mvnw.cmd -B -ntp clean verify
java -jar apps/control-plane/target/control-plane-0.2.0-SNAPSHOT.jar
```

Linux / macOS：

```bash
./mvnw -B -ntp clean verify
java -jar apps/control-plane/target/control-plane-0.2.0-SNAPSHOT.jar
```

状态接口：[http://localhost:8080/api/v1/status](http://localhost:8080/api/v1/status)；
健康检查：[http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)。

默认端口被占用时，启动命令追加 `--server.port=8081`。在终端按 Ctrl+C 停止服务。

需要通过 Maven 启动时，先安装库模块：

```powershell
.\mvnw.cmd -B -ntp install
.\mvnw.cmd -pl apps/control-plane spring-boot:run
```

父工程只负责聚合，不能在根目录直接执行 `mvn spring-boot:run`。

若 Windows / JDK 21 报 `Unable to establish loopback connection` 且堆栈包含 `UnixDomainSockets.connect`，可在当前终端使用普通路径的临时目录后重试：

```powershell
New-Item -ItemType Directory -Force build/tmp | Out-Null
$env:TEMP = (Resolve-Path build/tmp).Path
$env:TMP = $env:TEMP
```

MVP 只支持售后咨询、换货、退款和人工处理四类意图。项目使用合成数据，不连接真实支付或订单系统；副作用工具采用 at-least-once 语义并强制 `idempotency_key`。

## 仓库结构

```text
apps/control-plane/     Spring Boot API and health endpoints
libs/contract-core/     JSON Schema validation, workflow and tool contracts
libs/policy-contract/   Policy contracts and evaluation data records
libs/trace-contract/    AgentRun, Approval and RunEvent
tools/                 Three versioned after-sales ToolSpec examples
workflows/             After-sales workflow example
policies/              Four-rule after-sales safety policy
docs/                  Architecture, contracts and verification notes
```

Schema 随各模块 JAR 发布，位于 `src/main/resources/schemas/*.schema.json`；示例文件放在顶层 `tools/`、`workflows/`、`policies/`。测试直接读取这些示例，避免文档与测试各维护一套不同样本。

契约说明见 [docs/contracts.md](docs/contracts.md)，模块边界见 [docs/architecture.md](docs/architecture.md)，本轮验证记录见 [docs/verification.md](docs/verification.md)。

## 验证

`clean verify` 编译所有模块、执行契约与真实 HTTP 测试，并生成可运行的控制面 JAR。测试报告位于各模块 `target/surefire-reports/`。GitHub Actions 配置了 Java 21 的 Windows / Linux 构建。

示例工单与工具均面向合成售后数据；不要提交生产凭据或真实客户数据。

## 开源许可

项目计划以 Apache-2.0 License 开源。
