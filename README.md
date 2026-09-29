# 智能运维 Agent

用于 Java 后端与 AI Agent 学习、演示的本地智能运维项目。第一阶段先建立可重复的故障现场，再逐步加入观测、运维接口和 Agent。

## 当前里程碑：观测链路、只读诊断 Agent 与标准服务网关

当前包含三个 Spring Boot 模拟业务服务和订单服务依赖的业务 Redis：

- `GET /actuator/health`：三个服务均提供健康检查；order-service 的检查包含 Redis 连通性。
- `GET /api/orders/demo`：在 Redis 中累加演示请求计数，返回服务名和计数。这个接口不创建真实订单。
- `GET /api/users/demo`：返回用户服务的示例响应。
- `GET /api/payments/demo?delayMs=1500`：返回支付服务的示例响应，可模拟 0 到 3000 毫秒的请求延迟。

本机需要 Java 17、Maven 3.9+；容器运行需要 Docker Compose。

`java-platform/pom.xml` 是 Java 微服务平台的多模块父 POM，统一 Spring Boot 和 Spring Cloud 版本；旗下包含三个模拟业务服务、运维服务和标准 Spring Cloud Gateway。`python-agent/` 独立存放 Python + FastAPI + LangGraph Agent，不属于 Maven 模块。根目录的 `compose.yaml`、`infra/`、`scenarios/` 由两边共用。网关与 Agent 的使用说明分别见 `java-platform/gateway-service/README.md`、`python-agent/README.md`。

```text
Agent_Codex/
├─ java-platform/          # Java 微服务总目录，含父 POM
│  ├─ gateway-service/
│  ├─ operation-service/
│  ├─ order-service/
│  ├─ user-service/
│  └─ payment-service/
├─ python-agent/           # Python Agent，独立于 Maven
│  ├─ app/
│  └─ tests/
├─ web-frontend/           # Vue 3 运维工作台
├─ infra/                  # Prometheus、Loki、Alloy 配置
├─ scenarios/              # 故障演示步骤
└─ compose.yaml            # 跨 Java/Python 的本地编排
```

### 本地运行

先启动可访问的 Redis，再运行：

```powershell
mvn -f java-platform/order-service/pom.xml spring-boot:run
```

默认连接 `localhost:6379`，也可通过 `SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT` 指定 Redis。

### Docker Compose 运行

```powershell
mvn -f java-platform/pom.xml package
docker compose up --build -d
```

三个模拟业务服务分别使用本机端口 8080、8081、8082；均提供 `/actuator/health`。运维服务使用 `127.0.0.1:8083`，Compose 中的 Agent 服务使用 `127.0.0.1:18000`，标准网关使用 `127.0.0.1:9000`，Vue 工作台使用 `127.0.0.1:13000`。访问 `http://localhost:8080/api/orders/demo` 多次后，`requestCount` 应递增。

标准网关在 `127.0.0.1:9000` 提供统一入口：`/ai/**` 转发到 Python Agent，`/op/**` 转发到 operation-service，两条路由都在 `application-dev.yml` 中使用 `StripPrefix=1` 配置。Agent 的 `POST http://127.0.0.1:8000/diagnose` 接收 `{"service":"order-service","question":"为什么刚才大量 500？"}`，通过 LangGraph 循环调用只读工具并返回中文结论与证据。真实模型调用需要配置 `OPENAI_API_KEY`；服务仍只适合本机学习，不能直接对外开放。

### 运维接口

`operation-service` 当前仅允许查询 `order-service`、`user-service`、`payment-service`：

| 用途 | 示例 |
| --- | --- |
| Docker 容器状态 | `GET http://127.0.0.1:8083/api/operations/services/order-service/status` |
| 近 15 分钟 Loki 日志，最多 100 行 | `GET http://127.0.0.1:8083/api/operations/services/order-service/logs` |
| Prometheus 指标 | `GET http://127.0.0.1:8083/api/operations/services/order-service/metrics/http_5xx_rate` |

指标名称固定为 `scrape_up`、`request_rate`、`http_5xx_rate`、`avg_latency_seconds`、`process_cpu_usage`、`jvm_heap_used_bytes`。接口返回 Prometheus/Loki 原始查询结果，空 `result` 表示没有样本，不能直接解释为健康或 0。状态接口返回当前 Compose 项目中匹配服务标签的容器列表；空数组表示没有匹配容器。若通过 `docker compose -p` 更改项目名，也要设置 `COMPOSE_PROJECT_NAME` 环境变量后再启动 Compose，以便状态查询使用相同的项目名。

上表的查询接口没有鉴权，只供本地学习和 Agent 的只读工具调用。operation-service 仍提供受控重启接口，但标准网关只负责转发，不再保存用户、任务或审批状态，也不在 Java 网关中实现重启业务。Docker Socket 的 `:ro` 挂载只限制容器内文件写入，**不能**把 Docker API 变成只读；运维服务持有的 Docker 权限仍很高。整个接口不能直接对外开放，生产环境需更严格的网络和权限隔离。

### 指标与日志

三个服务均通过 `/actuator/prometheus` 提供 HTTP 请求、进程 CPU 和 JVM 内存指标。Compose 还包含 Prometheus、Loki 和 Alloy：

- Prometheus：`http://localhost:19090`，采集三个服务的请求量、错误率、响应时间、进程 CPU 使用率和 JVM 内存使用量。
- Loki：`http://localhost:13100`，Alloy 仅采集三个模拟业务服务的容器日志。

Prometheus 中可先查询 `up{job="spring-services"}`，确认三个服务都为 `1`；再查询 `http_server_requests_seconds_count{service="order-service",uri="/api/orders/demo"}`、`process_cpu_usage{service="order-service"}` 和 `sum by (service) (jvm_memory_used_bytes{area="heap"})`。这些是 Java 进程与 JVM 指标，不代表整个 Docker 容器的资源用量。Loki 中可用 `{service="order-service"} |= "Redis operation failed"` 查找 Redis 故障日志。

`operation-service` 已基于以下 PromQL 实现固定的指标工具（使用 1 分钟窗口）：

| 指标 | PromQL |
| --- | --- |
| 每秒请求量 | `sum by (service) (rate(http_server_requests_seconds_count{job="spring-services",uri!~"/actuator/.*"}[1m]))` |
| HTTP 500 错误率 | `sum by (service) (rate(http_server_requests_seconds_count{job="spring-services",status=~"5..",uri!~"/actuator/.*"}[1m])) / sum by (service) (rate(http_server_requests_seconds_count{job="spring-services",uri!~"/actuator/.*"}[1m]))` |
| 平均响应时间（秒） | `sum by (service) (rate(http_server_requests_seconds_sum{job="spring-services",uri!~"/actuator/.*"}[1m])) / sum by (service) (rate(http_server_requests_seconds_count{job="spring-services",uri!~"/actuator/.*"}[1m]))` |
| 进程 CPU 使用率 | `process_cpu_usage{job="spring-services"}` |
| JVM 已用内存（字节） | `sum by (service) (jvm_memory_used_bytes{job="spring-services",area="heap"})` |

低流量时错误率的分母可能为零，查询接口需要把这种结果标为“样本不足”，不能直接解释成 0% 错误率。

### 故障验证

完整步骤见 `scenarios/`。例如，停止业务 Redis：

```powershell
docker compose stop redis-app
```

再次请求 `/api/orders/demo` 应返回 HTTP 500；`docker compose logs order-service` 应出现 Redis 访问失败或超时日志。恢复方式：

```powershell
docker compose start redis-app
```

待 Redis 重新就绪后，接口应恢复成功。此 Redis 只供模拟业务使用；Agent 后续的会话状态使用单独的平台 Redis。

## 后续顺序

1. 已完成：标准 Spring Cloud Gateway 路由、全局 CORS 和全局过滤器扩展点。
2. 已完成：operation-service 的只读观测接口和受控重启接口；网关不处理业务逻辑。
3. 下一阶段：在独立平台服务中补充用户鉴权、任务持久化和人工审批，再由网关统一转发。

第一阶段不使用 Kubernetes，也不向 Agent 提供任意 Shell 执行能力。
