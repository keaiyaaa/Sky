# 航拍 AI 后端部署

该服务提供在线语音转写、飞控意图分析和巡检图像复核。服务无状态，不保存上传的音频和图片，也不保存模型返回内容。

## 环境配置

1. 将 `.env.example` 复制为 `.env`，只在部署主机上填写 API key、随机生成的 `AERIAL_API_TOKEN` 和百炼 Workspace API 地址。
2. DeepSeek 文本与视觉能力使用 `DEEPSEEK_API_KEY`；语音转写使用 `DASHSCOPE_API_KEY`。
3. `BAILIAN_BASE_URL` 必须是百炼 Workspace 专属兼容地址，并与 key 的地域一致。北京和新加坡的 URL 形态见阿里云 Model Studio 的 Qwen ASR 文档。
4. 将有效 TLS 证书放入 `deploy/certs/fullchain.pem` 和 `deploy/certs/privkey.pem`，再运行 `docker compose --profile https -f deploy/docker-compose.yml up -d --build`。容器健康状态可用 `docker compose --profile https -f deploy/docker-compose.yml ps` 查看。

## Android 配置

Android 构建时通过 Gradle 用户级属性配置 `AERIAL_BACKEND_URL` 和 `AERIAL_API_TOKEN`，例如写入 Windows 的 `%USERPROFILE%\.gradle\gradle.properties`，不要提交到仓库。Android 客户端强制使用 HTTPS。API token 会随 Android 包进入客户端，适合封闭校园网原型，不是用户身份认证；公开部署前应接入账号登录/JWT、按用户限流，并在 HTTPS 反向代理后开放访问。

Compose 默认把 API 绑定到本机回环地址，`https` profile 中的 Nginx 容器提供 TLS 入口并限制请求频率。证书、域名和校园网访问策略由部署者提供；将 `AERIAL_BACKEND_URL` 指向配置好的 HTTPS 地址。不要直接把无 TLS 的 8080 端口暴露到公网。

## API

- `GET /api/v1/health`：容器健康检查。
- `POST /api/v1/intent/analyze`：JSON `{ "text": "..." }`，先做关键词候选，再调用 DeepSeek。
- `POST /api/v1/voice/analyze`：multipart 字段 `audio`，上传 WAV，后端调用百炼 Qwen3-ASR-Flash，再调用 DeepSeek。
- `POST /api/v1/inspection/analyze`：multipart 字段 `image`，上传 JPEG，后端调用 DeepSeek 视觉模型。

业务 API 使用 `Authorization: Bearer <AERIAL_API_TOKEN>`。错误响应不会返回上游原始正文或 API key。音频上限 512 KiB，图像上限 768 KiB；超限或格式不符时拒绝请求。

视觉输出只表示“疑似异常/未发现明显异常/需人工复核”，没有校准置信度或检测框，不可替代现场巡检判断。默认检测频率由 Android 端限制为每数秒最多一帧，以控制网络和模型费用。
