# 问爻 AI 协议参考后端

此 FastAPI 项目仅用于本地 Mock、协议参考和错误场景测试，不是 Android App 的生产依赖，不应部署为接收用户问题或 SiliconFlow Key 的正式服务。正式 App 使用用户自己的 Key 从设备直连 SiliconFlow 固定官方 HTTPS API。

## 配置

密钥只能通过服务端环境变量提供，不得写入仓库或 APK：

```text
AI_BASE_URL=https://provider.example.com/v1
AI_API_KEY=<server-secret>
AI_MODEL=<model-name>
```

安装依赖并运行：

```powershell
python -m pip install -r backend/requirements.txt
python -m uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
```

如将其用于本地开发，只使用测试数据和专用开发凭据。当前产品范围不包含该服务的正式部署。
