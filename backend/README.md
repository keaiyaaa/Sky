# Sky Backend

独立于 Android Gradle 工程的 Spring Boot 服务端骨架。公共传输规则见 [`../docs/backend/public-contracts.md`](../docs/backend/public-contracts.md)，机器可读接口见 [`../docs/backend/openapi.yaml`](../docs/backend/openapi.yaml)。

## 构建

需要可运行 Maven Wrapper 的 JDK。工程字节码目标为 Java 21。

Windows：

```powershell
.\mvnw.cmd compile
```

Linux/macOS：

```sh
./mvnw compile
```

## 本地运行

准备可用的 MySQL 8.4 数据库，并通过环境变量提供连接信息：

- `DB_URL`：JDBC URL，例如本机开发用 `jdbc:mysql://localhost:3306/tianshu?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`（可选，默认 `8080`）

然后运行：

```powershell
.\mvnw.cmd spring-boot:run
```

已有 F19/F20 的 `V002__profile_and_devices.sql`，但 T03 的账号核心 `V001` 尚未交付。V002 在唯一的 `users` 表上增加资料字段，并创建 `drones` 及共享幂等/变化表；**不能对空库直接执行 V002**。账号核心至少提供 `users.id BIGINT PRIMARY KEY`、`username`，昵称等资料字段由 V002 添加，避免重复建列。

Hibernate 使用 `ddl-auto=validate`，Flyway 默认关闭；应用启动不会自动建表或修改数据库。部署前由迁移负责人将 T03 与 V002 一起评审、使用独立迁移账号执行。旧版14表设计不能直接作为迁移基线。就绪探针同时检查数据库和本模块所需 schema，缺表/缺列会报告未就绪。

## 当前端点

- `GET /api/v1/system/ping`：公开示例，仅返回服务状态和 API 版本。
- `/actuator/health/liveness`：进程存活探针。
- `/actuator/health/readiness`：包含数据库健康状态的就绪探针。
- `/api/v1/auth/*` 及 `/api/v1/sync/*` 已列入共享契约，但控制器尚未实现。
- `GET/PATCH /api/v1/me`：当前用户资料及乐观版本更新。
- `GET/POST /api/v1/devices`、`GET/PUT/DELETE /api/v1/devices/{id}`：当前用户的设备记录；写入持久化去重，删除保留墓碑。
- `GET/POST /api/v1/flight-logs`：当前用户的事件检索及幂等追加；运行事件通过T06提供的可选只读视图聚合，未接入时明确返回覆盖范围。
- `GET/PUT /api/v1/settings`：当前用户唯一一份账户偏好；未保存GET只读返回默认值，PUT完整提交九字段、expectedVersion与idempotencyKey。deviceApplied恒为null，不下发飞机。
- 其余路径默认要求认证；认证提供者将在后续 T03 实现。

公开约定和接口草案不代表业务接口或真实认证已经完成。校园网访问、HTTPS 证书、容器部署和多设备验收均未包含在当前骨架中。

## F19/F20 验证与 Android 对接

新增测试依赖仅位于独立后端：Boot Test、Security Test、Testcontainers MySQL，版本沿用 Boot BOM。执行 `./mvnw verify`（Windows：`mvnw.cmd verify`），默认启动 `mysql:8.4.13` 测试容器；没有 Docker 时测试失败，不静默跳过。也可在本进程设置 `MYSQL_TEST_BINARY` 为现有 `mysqld` 路径，测试会创建临时数据目录、随机端口和独立用户，结束后关闭临时进程，不连接任何已有数据库。测试夹具中的账号表和 MockMvc 身份仅用于测试，不能作为正式 V001 或认证实现。

完整字段、F24 接入代码、重试与验收范围见 [`../docs/F19-F20开发与验收.md`](../docs/F19-F20开发与验收.md)。OpenAPI 入口继续是 `docs/backend/openapi.yaml`，通过相对引用包含 `f19-f20.openapi.yaml`；发布契约时必须同时保留两份文件。

F21字段、V005、日志本地升级与运行视图对接见 [`../docs/F21开发与验收.md`](../docs/F21开发与验收.md)。OpenAPI入口还引用 `f21.openapi.yaml`。应用自动迁移继续关闭；正式部署需整合V001/V002及各业务迁移，不能把临时测试夹具当作认证实现。

## F18 账户设置与本轮交付

V006创建user_settings并依赖V001账号核心及V002共享幂等/变化表，V002、V005、V006目前只在隔离测试库运行。readiness同时核对账户设置必需列。正式执行前需协调团队版本，Flyway自动迁移仍关闭。

OpenAPI根入口同时引用f18.openapi.yaml、f19-f20.openapi.yaml与f21.openapi.yaml，发布时应一起保留。九字段、默认值和归属已获用户确认；没有新增生产或Android依赖。30项MySQL集成测试覆盖三个模块组的迁移重复运行、隔离、校验、并发冲突、幂等重放与事务回滚。

Android账户设置使用既有BackendSessionRegistry、BackendAccountRuntime及账户缓存/待确认请求存储。F24先配置正式HTTPS /api/v1入口，再安装已验证主体；退出、过期或换号使旧页面失效。没有真实认证服务时不会创建云端身份或报告保存成功。完整交接见 [`../docs/本人模块交付与待联调.md`](../docs/本人模块交付与待联调.md)。
