# Gateway Service

这是标准的 Spring Cloud Gateway 微服务网关。它只负责路由、跨域和全局过滤器扩展，不包含诊断任务、用户、审批、数据库或重启业务逻辑。

## 路由

默认启用 `dev` profile，配置位于 `src/main/resources/application-dev.yml`：

| 外部路径 | 转发地址 | 处理方式 |
| --- | --- | --- |
| `/ai/**` | `http://127.0.0.1:8000` | `StripPrefix=1`，例如 `/ai/diagnose` 转为 `/diagnose` |
| `/op/**` | `http://127.0.0.1:8083` | `StripPrefix=1`，例如 `/op/api/...` 转为 `/api/...` |

网关监听 `9000` 端口。目标地址可以通过 `AI_SERVICE_URL` 和 `OPERATION_SERVICE_URL` 覆盖，Compose 中使用容器服务名：

```yaml
AI_SERVICE_URL: http://agent-service:8000
OPERATION_SERVICE_URL: http://operation-service:8080
```

这是容器网络需要的配置；本机直接运行网关时，默认值仍然是 `127.0.0.1:8000` 和 `127.0.0.1:8083`。

## 启动

先构建 Java 多模块项目：

```powershell
mvn -f java-platform/pom.xml package
```

本机运行网关：

```powershell
mvn -f java-platform/gateway-service/pom.xml spring-boot:run
```

完整 Compose 环境：

```powershell
docker compose up --build -d
```

访问入口：

- 网关健康检查：`http://127.0.0.1:9000/actuator/health`
- Python Agent：`POST http://127.0.0.1:9000/ai/diagnose`
- operation-service：`GET http://127.0.0.1:9000/op/api/operations/services/order-service/status`

## 设计边界

- 路由必须写在 YAML 中，不在 Java 中拼接目标地址或手写转发 Controller。
- `GlobalFilterTemplate` 是全局过滤器扩展点，只放认证、追踪、日志等横切逻辑。
- 业务任务、审批、数据持久化应放在独立业务服务中。
- 当前路由没有鉴权，适合本机开发环境；对外部署前需要增加标准 Gateway 安全过滤器和网络限制。
