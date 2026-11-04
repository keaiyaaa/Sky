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

骨架没有业务实体或初始化迁移。Hibernate 使用 `ddl-auto=validate`，Flyway 默认关闭；服务不会自动建表或修改数据库。后续迁移须使用受控的一次性流程。

## 当前端点

- `GET /api/v1/system/ping`：公开示例，仅返回服务状态和 API 版本。
- `/actuator/health/liveness`：进程存活探针。
- `/actuator/health/readiness`：包含数据库健康状态的就绪探针。
- `/api/v1/auth/*` 及 `/api/v1/sync/*` 已列入共享契约，但控制器尚未实现。
- 其余路径默认要求认证；认证提供者将在后续 T03 实现。

公开约定和接口草案不代表业务接口或真实认证已经完成。校园网访问、HTTPS 证书、容器部署和多设备验收均未包含在当前骨架中。
