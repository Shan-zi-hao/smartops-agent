# Agent Service

Python + FastAPI + LangGraph 的只读诊断服务。`POST /diagnose` 接收目标业务服务和问题，模型可循环调用三个只读工具：`get_service_status`、`query_logs`、`query_metrics`。工具实际通过 Java `operation-service` 查询 Docker、Loki、Prometheus；模型没有重启容器或执行 Shell 的能力。

当前每个请求独立运行，尚无会话记忆、用户鉴权或人工确认流程。响应中的 `evidence` 记录本次工具调用及结果；没有取到证据时不会输出确定的故障原因。

## 配置与启动

项目已默认配置你提供的 `qwen3.8-27b` 模型名和阿里云兼容接口地址；仍可通过 `OPENAI_MODEL`、`OPENAI_BASE_URL` 覆盖。需要单独设置 `OPENAI_API_KEY`，密钥不保存在项目文件中。模型必须支持工具调用。诊断期间会把问题和查询到的日志、指标发送给所配置的模型服务，请勿接入含敏感信息的生产日志。

在项目根目录运行：

```powershell
python -m venv python-agent/.venv
python-agent/.venv/Scripts/python -m pip install -r python-agent/requirements.txt
$secureKey = Read-Host "输入 API Key（不要写入项目文件）" -AsSecureString
$env:OPENAI_API_KEY = [System.Net.NetworkCredential]::new("", $secureKey).Password
python-agent/.venv/Scripts/python -m uvicorn app.main:app --app-dir python-agent --host 127.0.0.1 --port 18000
```

本机运行时默认连接 `http://127.0.0.1:8083` 的 `operation-service`；可用 `OPERATION_SERVICE_URL` 更改。也可以在设置 API Key 后从项目根目录执行 `docker compose up --build -d`，Agent 的容器端口 8000 映射到本机 `127.0.0.1:18000`，通过 Compose 网络访问运维服务。未配置 Key 时健康检查仍返回 `UP`，但诊断接口返回 HTTP 503。

你此前在聊天中贴出的 Key 应先在服务商控制台轮换。若使用 Compose，输入新 Key 后运行 `docker compose up -d --force-recreate agent-service`，不要把密钥写进 `.env` 或提交到仓库。注意本地 Docker 管理员仍可通过容器配置查看注入的环境变量；这里只适用于学习环境。

```powershell
Invoke-RestMethod 'http://127.0.0.1:18000/health'
Invoke-RestMethod 'http://127.0.0.1:18000/diagnose' -Method Post -ContentType 'application/json' -Body '{"service":"order-service","question":"为什么刚才大量 500？"}'
```

`service` 只能是 `order-service`、`user-service` 或 `payment-service`。指标名也由工具限制为固定列表，不接受模型自造的查询语句。

## 测试

```powershell
cd python-agent
.venv/Scripts/python -m pytest -q tests
```

测试使用模拟模型和模拟运维接口，验证 LangGraph 的工具循环及“无证据不下结论”；没有 API Key 时不会调用真实模型。
