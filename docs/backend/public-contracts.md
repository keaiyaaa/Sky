# 后端公共约定

状态：首期公共契约，供各模块实现时共同遵循。业务接口未实现前不得据此声称已可联调。机器可读契约见 [openapi.yaml](openapi.yaml)，数据映射见 [schema-mapping.md](schema-mapping.md)。

## 1. 服务边界

- 后端是独立 Java 模块化单体，路径为 `backend/`，不加入 Android Gradle 工程。
- Android 通过 HTTP API 访问后端；客户端不直接连接 MySQL。
- Android 仍负责 DJI 连接、相机、语音识别和飞行执行。服务端保存鉴权后的业务数据与客户端上报事件，不提供起飞、降落、返航或远程航线执行接口。
- 首期只支持用户自己的数据。后端从认证上下文取得 `userId`，忽略请求体或查询参数中试图指定的所有者 ID。
- 服务地址、域名、证书和正式部署端口尚未确认；不得把示例地址当作可用环境。

## 2. HTTP 与 JSON

- 业务 API 前缀为 `/api/v1`，部署时使用 HTTPS；本地开发地址由开发配置提供。
- 请求与响应使用 UTF-8 JSON，字段使用 `camelCase`。未知输入字段应拒绝或明确忽略，不得静默改变已有字段语义。
- 所有 ID 在 JSON 中使用字符串，包括数据库内部为整数的 ID。客户端稳定 ID 与服务端 ID 分开传递，不将本地字符串 ID 强制转换为数据库数字。
- 时间使用 RFC 3339 UTC 字符串并以 `Z` 结尾，例如 `2026-11-04T02:00:00Z`；客户端展示时转换为 Asia/Shanghai。
- 业务失败使用实际 HTTP 状态码，不能将错误包装成 HTTP 200。
- 服务端为每个请求生成 `X-Request-Id` 响应头；错误体中的 `requestId` 与响应头一致。请求编号可写入日志，密码、令牌、原始媒体及敏感个人数据不可写入日志。

## 3. 认证与所有权

- 移动端认证采用 `Authorization: Bearer <accessToken>`。令牌是凭证，只返回给用户本人，日志中不得记录。
- 注册、登录、令牌刷新属于匿名入口；登出及其他业务接口必须认证。未列入匿名白名单的路由默认要求认证。
- 登录失败使用统一提示，避免区分账号不存在和密码错误。认证数据源不可用时拒绝认证。
- 用户密码规则为 10–64 个字符，同时 UTF-8 编码不得超过 BCrypt 的 72 字节输入上限；超出字节上限应明确拒绝，不得截断。
- 访问令牌和刷新会话的期限、轮换、撤销与重放处理按认证接口契约实现；令牌持久化只保存安全哈希。
- 首期不接受客户端传入的 `userId` 作为授权依据，不提供团队共享或角色授权。

## 4. 错误响应

所有 API 错误返回统一结构：

```json
{
  "code": "VALIDATION_FAILED",
  "message": "请求参数无效",
  "requestId": "0192f1be-1234-7abc-8def-0123456789ab",
  "fieldErrors": [
    { "field": "username", "reason": "长度不符合要求" }
  ]
}
```

`fieldErrors` 没有字段级错误时返回空数组。`message` 面向客户端展示，不包含 SQL、堆栈、密码、令牌或内部配置。`code` 是稳定机器码；客户端按 `code` 分支，不解析自然语言消息。

| HTTP 状态 | 用途 | 常见 `code` |
|---|---|---|
| 400 | JSON、字段或业务参数无效 | `VALIDATION_FAILED` |
| 401 | 缺少、无效或过期的认证凭证 | `UNAUTHENTICATED` |
| 403 | 已认证但无权执行操作 | `FORBIDDEN` |
| 404 | 资源不存在；隐藏他人资源存在性时也用此状态 | `NOT_FOUND` |
| 405 | 请求方法不受支持 | `METHOD_NOT_ALLOWED` |
| 409 | 版本冲突、幂等键冲突或游标过期 | `VERSION_CONFLICT`、`IDEMPOTENCY_KEY_REUSED`、`CURSOR_EXPIRED` |
| 413 | 请求体或上传文件超过限制 | `PAYLOAD_TOO_LARGE` |
| 429 | 登录或请求频率受限 | `RATE_LIMITED` |
| 503 | 数据库或必要依赖不可用 | `SERVICE_UNAVAILABLE` |

## 5. 分页与版本冲突

- 列表参数 `page` 从 0 开始，默认 `0`；`size` 默认 `20`，最大 `100`。服务端拒绝负数页码和超限页大小。
- 分页响应采用 `{ "items": [], "page": 0, "size": 20, "total": 0 }`。排序需稳定；若接口支持排序，必须列出允许字段和默认顺序。
- 可修改资源带整数 `version`。更新和删除必须提交 `expectedVersion`；新建使用 `expectedVersion: 0`，已有资源提交当前版本。服务端在事务中执行条件更新，失败时返回 409，不静默覆盖。
- 冲突响应只包含当前用户有权读取的资源版本信息；不得泄漏其他用户数据。

## 6. 同步约定

- `POST /api/v1/sync/push` 一次提交一个业务聚合；航线及其全部航点作为一个聚合。媒体二进制通过文件接口传输，不放入同步 JSON。
- 同步实体白名单首期为 `ROUTE`、`DEVICE`、`USER_SETTING`、`MISSION_RUN`、`FLIGHT_LOG`。资料使用资料 API；新增类型须先更新契约。
- 每个请求含 `entityType`、`clientId`、`operation`、`expectedVersion`、`idempotencyKey` 和 `payload`。创建、更新、删除都使用幂等键；删除通过 `operation: DELETE` 和稳定 ID 表达。
- 幂等作用域为当前用户与 `idempotencyKey`。相同键和相同规范化请求返回先前结果；同键不同请求返回 409 `IDEMPOTENCY_KEY_REUSED`。
- 幂等收据、业务写入和变化游标必须在同一数据库事务提交。仅用内存 Map 不算持久幂等。
- 成功响应包含 `serverId`、`clientId`、`version` 和 `changeCursor`。客户端以服务端版本处理后续更新，保留稳定 `clientId`。
- `GET /api/v1/sync/changes` 使用不透明游标及最多 100 条的页大小，返回 `items`、`nextCursor`、`hasMore`。变化按用户隔离，删除以墓碑记录传播；过期游标返回 409 `CURSOR_EXPIRED`，客户端需按约定重建数据。
- 变化游标必须代表已提交变化，不能使用客户端时间或裸自增主键推断提交顺序。普通业务 API 和同步入口调用相同业务服务，并登记同一变化流。
- Mock 种子数据默认不上传；用户首次关联旧数据时须明确选择归属。运行来源必须保留 `LOCAL_SIMULATION` 等标记，客户端上报不构成真实飞行证明。

## 7. 健康检查与管理端点

- 存活状态表示进程运行；就绪状态还须确认数据库连接及所需 schema 可用。
- Actuator 只暴露健康检查所需端点。环境、配置、Bean、日志、映射、堆转储和关停端点不对校园网公开。
- `/actuator/health/liveness` 与 `/actuator/health/readiness` 是运维探针，不属于 `/api/v1` 业务契约。健康响应不输出凭证、连接串或详细内部配置。

## 8. 当前范围与版本规则

- 以上约定是各模块共享规则，不代表注册、同步或任何业务接口已经实现。
- `/api/v1` 内已发布字段不复用为其他含义。兼容性破坏需提升 API 主版本，并同步 Android 与 OpenAPI。
- 表和本地数据对应关系以 [schema-mapping.md](schema-mapping.md) 为准；实际迁移由后续业务任务另行评审，不在公共骨架中直接创建全部业务表。
- 工程依赖和镜像版本以 [version-baseline.md](version-baseline.md) 记录为准。
