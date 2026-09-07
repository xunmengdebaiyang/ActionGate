# ActionGate

ActionGate 是一个面向高风险工具调用的 Agent 发布门禁与可恢复执行参考实现。项目以企业售后工单为演示场景，围绕工具选择、参数校验、人工审批、幂等副作用、轨迹评测和故障恢复建立可复现闭环。

## 当前状态

当前版本完成 Spring Boot 3 / Java 21 控制面骨架，提供基础状态接口。后续将按项目计划接入 Temporal、PostgreSQL、Policy-as-Code、模拟售后工具、OpenTelemetry 和评测 CLI。

## 快速开始

需要 Java 21 和 Maven 3.9+：

```bash
mvn spring-boot:run
curl http://localhost:8080/api/v1/status
curl http://localhost:8080/actuator/health
```

MVP 只支持售后咨询、换货、退款和人工处理四类意图。项目使用合成数据，不连接真实支付或订单系统；副作用工具采用 at-least-once 语义并强制 `idempotency_key`。

## 规划目录

```text
apps/       control-plane, worker, model-gateway
libs/       policy-contract, trace-contract
workflows/  workflow definitions
datasets/   golden traces and failure cases
docs/       architecture and evaluation reports
```

## 开源许可

项目计划以 Apache-2.0 License 开源。
