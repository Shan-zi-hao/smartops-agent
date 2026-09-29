# 本地故障场景

先按项目根目录的 `README.md` 打包并启动 Compose。以下场景只操作本地模拟服务；每次验证后按“恢复”步骤还原。

## 场景 1：业务 Redis 不可用

1. 请求 `http://localhost:8080/api/orders/demo`，预期 HTTP 200。
2. 执行 `docker compose stop redis-app`。
3. 再次请求该接口，预期 HTTP 500、响应体包含 `REDIS_UNAVAILABLE`。
4. 执行 `docker compose logs --tail 100 order-service`，预期出现 `Redis operation failed` 和连接失败或命令超时信息。
   接入观测组件后，也可在 Loki 查询 `{service="order-service"} |= "Redis operation failed"`，并在 Prometheus 查询该服务 HTTP 500 的请求计数。
   现在也可通过 `http://127.0.0.1:8083/api/operations/services/order-service/logs` 和 `http://127.0.0.1:8083/api/operations/services/order-service/metrics/http_5xx_rate` 读取相同证据；指标可能需要等待下一次采集。
5. 执行 `docker compose start redis-app`，稍等后再次请求，预期恢复 HTTP 200。

## 场景 2：业务容器停止

1. 请求 `http://localhost:8081/actuator/health`，预期状态 `UP`。
2. 执行 `docker compose stop user-service`。
3. 执行 `docker compose ps -a`，预期 `user-service` 状态为 `Exited`；健康检查无法连接。
   `http://127.0.0.1:8083/api/operations/services/user-service/status` 也应返回 `exited` 状态。
4. 执行 `docker compose logs --tail 100 user-service`，查看停止前日志。容器正常停止时不一定有错误堆栈，诊断应以容器状态为主要证据。
5. 执行 `docker compose start user-service`，待应用启动后健康检查恢复 `UP`。

## 场景 3：支付请求延迟

1. 请求 `http://localhost:8082/api/payments/demo`，预期快速返回。
2. 请求 `http://localhost:8082/api/payments/demo?delayMs=1500`，预期约 1.5 秒后返回 HTTP 200。
3. `delayMs` 仅用于本地模拟，范围为 0 到 3000；超出范围预期 HTTP 400。
4. 在 Prometheus 查询 `http_server_requests_seconds_sum{service="payment-service",uri="/api/payments/demo"}` 和对应的 `_count`，可以计算平均响应时间。
