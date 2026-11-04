# 后端版本与依赖基线

状态：T02 骨架所需生产依赖和 Maven Wrapper 已获用户授权；以下版本用于本轮独立后端工程。`backend` 的 Maven `compile` 已通过；这不表示已安装 JDK 21、拉取数据库镜像或完成部署。

核查日期：2026-11-04。

## 1. 版本基线

| 层次 | 本轮采用版本/方式 | 说明 |
|---|---|---|
| Java 编译目标 | Java 21 | 后端字节码目标；Android 工程保持原配置。当前终端有 OpenJDK 25.0.2，Maven 默认进程使用 Java 17.0.14；本轮将临时使用 JDK 25 编译 `--release 21`，不改系统环境。 |
| Spring Boot | 3.5.16 | 与技术方案 3.5 系列一致；由 Boot BOM 管理各 Spring/Hibernate 依赖版本。 |
| Maven Wrapper | Maven 3.9.16 | 仅位于 `backend/`，不加入 Android Gradle 工程。 |
| MySQL | 8.4.13 LTS | 仅作为目标数据库和后续测试/部署镜像基线；本轮不拉取镜像、不连接数据库。 |
| JDBC 驱动 | `com.mysql:mysql-connector-j` | 运行时依赖，版本由 Spring Boot BOM 管理。 |
| 数据迁移 | Flyway Core + MySQL 支持模块 | 使用 Boot BOM 管理版本；骨架默认关闭启动时迁移，避免未经授权写入数据库。 |
| API 规范 | OpenAPI 3.1.0 YAML | 契约见 `docs/backend/openapi.yaml`；不增加 Swagger UI 或生成器依赖。 |

候选版本来源：

- [Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Boot 3.5 依赖版本管理](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/properties.html)
- [Apache Maven 发布历史](https://maven.apache.org/docs/history.html)
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)
- [MySQL 8.4 LTS 发布策略](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)
- [MySQL 当前版本文档](https://dev.mysql.com/doc/)
- [Flyway MySQL 驱动说明](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)

## 2. 本机环境只读观察

- `java -version`：OpenJDK 25.0.2。
- `mvn -version`：Apache Maven 3.9.15，Maven 进程使用 Java 17.0.14。
- `docker --version`：Docker CLI 29.9.2。该输出只证明 CLI 可调用，不证明 Docker 服务已启动或可拉取镜像。
- 校园网宿主机、独立 JDK 21、Docker daemon、目标网络及 HTTPS 证书均未在本轮验证。

## 3. 本轮授权依赖

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-actuator`
- `org.flywaydb:flyway-core`、`org.flywaydb:flyway-mysql`
- `com.mysql:mysql-connector-j`（runtime）

不增加 Redis、消息队列、Swagger UI、独立网关、微服务组件或 Android 侧依赖。测试依赖与集成测试不属于本次授权范围，未加入。

## 4. 操作状态

| 操作 | 状态 |
|---|---|
| 在目标副本新增独立 Maven 工程和上述依赖声明 | 本轮执行 |
| 生成 Maven 3.9.16 Wrapper | 本轮执行 |
| 运行一次后端 `compile` | 已通过；15 个 Java 源文件使用 JDK 25、`--release 21` 编译，Maven Wrapper 3.9.16，耗时约 2 分 26 秒 |
| 添加或运行单元/集成测试 | 未执行 |
| 安装 JDK、修改系统环境变量 | 未执行；编译只在当前进程临时选择 JDK 25 |
| 拉取 MySQL 镜像、建库、迁移、部署或连接校园网 | 未执行 |
