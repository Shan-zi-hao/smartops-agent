# SmartOps Vue 工作台

这是项目的 Vue 3 + Vite 前端，浏览器只访问 Java `gateway-service`，不直接访问 Python Agent。

## 本地开发

```powershell
cd web-frontend
npm install
npm run dev
```

打开 `http://127.0.0.1:5173`。Vite 会把 `/ai` 和 `/op` 请求代理到 `http://127.0.0.1:9000`，因此需要先启动 Python Agent、operation-service 和网关。

## Compose 运行

在项目根目录执行：

```powershell
docker compose up --build -d
```

工作台地址为 `http://127.0.0.1:13000`。Nginx 会把 `/api` 代理到 Compose 网络内的 `gateway-service:8080`。

工作台当前包含：

- 业务服务选择和状态查询；
- 通过 `/ai/**` 提交诊断；
- AI 结论与工具证据展示；
- 通过 `/op/**` 访问运维查询接口。
