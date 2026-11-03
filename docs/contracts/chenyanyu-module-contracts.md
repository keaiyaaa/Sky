# 陈彦宇模块接口约定

状态：本地阶段草案，等待模块负责人确认。版本 0.1，2026-11-02。

本文对应 F04、F05、F06、F12、F24。接口名称为拟议内部 Kotlin 类型，尚未落入源码；不构成后端 API 或稳定公共 API。本地模拟可以独立开发。真实设备和服务端调用必须等相应负责人确认契约后接入。

## 当前事实和范围

| 模块 | 当前代码事实 | 此阶段输出 |
|---|---|---|
| F04 图传 | 有演示画面；无 DJI 图传解码链路 | 本地授权视频播放和故障状态；持续标注演示来源 |
| F05 相机 | `AerialDroneManager` 封装 DJI 相机 Key；模式失败回调仍继续发送拍摄命令 | 统一相机请求、Fake 和真实适配器；模式失败时零拍摄调用 |
| F06 指令 | Vosk 转写、OkHttp 意图分析和本地词匹配已有代码；本地“停”可能先于“停止录像”匹配 | 经校验的单动作确认、取消和过期结果抑制 |
| F12 飞行 | App 没有 DJI 起飞、降落、返航指令链路；`FlightControlActivity` 为空壳 | 四种指令的纯本地可控模拟；DEVICE 模式未接入时拒绝执行 |
| F24 会话 | 无认证页面或令牌会话；退出菜单关闭应用；业务后端无已实现服务 | 明确标注的内存演示会话和 Fake 同步，不保存真实凭据 |

代码依据：`app/src/main/java/com/example/msdksample/aerial/AerialDroneManager.kt`、`Aerial_photography.kt`、`LLMService.kt`、`VoskHelper.kt`、`drone/SimulatedFlightEngine.kt`、`ui/flight/FlightControlActivity.kt`、`HomeActivity.kt`。项目 API 基线参见 `docs/04-API接口与调用说明.md`。

## 公共内部数据类型草案

```kotlin
enum class ExecutionMode { DEMO, DEVICE }
enum class OperationStatus { SUCCEEDED, FAILED, REJECTED, TIMED_OUT, CANCELLED }
data class OperationResult(
    val requestId: String,
    val mode: ExecutionMode,
    val status: OperationStatus,
    val message: String
)
fun interface CancelHandle { fun cancel() }
```

`requestId` 由请求发起方生成，在异步边界原样传递。同一请求最多发出一个终态。仅 DEMO Fake 可在本地确定性报告模拟成功。DEVICE 模式只能在设备状态源和能力确认后执行；缺少连接、状态或能力时返回 `REJECTED`。

取消尚未发出的请求后不得发送动作。真实设备或相机已经接受命令后，取消本地等待不能撤销物理动作；若未收到最终状态，返回或显示 `TIMED_OUT`/`UNKNOWN` 语义，等待状态回读，禁止自动重发。

## 模块接口草案

### F04 视频预览

建议 `PreviewSource` 提供 `prepare(callback)`、`play()`、`pause()`、`release()`，并报告 `PREPARING`、`READY`、`PLAYING`、`ERROR`、`ENDED`。实现可以是授权的本地 raw 视频，也可以是测试 Fake。界面标注 `演示视频`。未来 DJI 视频输入另设设备适配器；不得把本地文件断流测试记录为真实图传测试。

### F05 相机

```kotlin
enum class CameraAction { PHOTO, START_RECORD, STOP_RECORD }
interface CameraGateway {
    fun execute(
        requestId: String,
        action: CameraAction,
        callback: (OperationResult) -> Unit
    ): CancelHandle
}
```

DJI 适配器对 PHOTO 先设照片模式，成功后才拍照；START_RECORD 先设录像模式，成功后才开始录像。设模式失败时不得调用后续动作。重复开始、非录像状态下停止及操作忙碌的响应由 `CameraController` 明确拒绝。Fake 通过注入成功、延迟、断连、存储错误和失败控制结果。

### F06 意图和确认

建议纯 Kotlin `LocalCommandMatcher.matchCamera(text): CameraAction?` 仅匹配无歧义单个相机动作；`FlightIntent` 在内部保持现有字段 `flightType` 和 `parameters`。解析器对空响应、缺字段、类型不符和不支持动作返回明确错误。不得透传未校验原文供执行。

协调器管理 `IDLE → LISTENING/ANALYZING → AWAITING_CONFIRMATION → EXECUTING → terminal`。用户取消、页面销毁、超时或新请求使旧请求失效。离线回退仍须让用户重新确认；否定和多动作文本返回歧义，执行次数为零。

### F12 基础飞行指令

```kotlin
enum class BasicFlightAction { TAKEOFF, LAND, RETURN_HOME, HOVER }
interface FlightCommandExecutor {
    fun execute(
        requestId: String,
        action: BasicFlightAction,
        callback: (OperationResult) -> Unit
    ): CancelHandle
}
```

模拟状态采用 `ON_GROUND / TAKING_OFF / AIRBORNE / LANDING / RETURNING / HOVERING / UNKNOWN`。起飞只允许从地面态；降落、返航、悬停要求已知空中态；返航需已有模拟起点。异步延时通过可控时钟推进。不得让 `SimulatedFlightEngine` 被误认为具备 DJI 飞行控制或真实避障能力。DEVICE 实现另行评审，需确认 F02 注册/连接源、F03 遥测年龄及机型能力。

### F24 演示会话和同步

演示 `SessionState` 为 `SIGNED_OUT / SIGNING_IN / SIGNED_IN / EXPIRED`。本阶段只接受虚构演示身份，不采集真实密码，不保存令牌，进程重启后回到未登录。注销使当前会话代次失效并取消本地请求；旧用户迟到的回调不得恢复其身份或影响新用户。

演示同步记录至少包括本地记录 ID、修订版本、幂等键和会话代次；Fake 返回成功、未授权、网络失败或版本冲突。重复幂等键不能重复写，冲突不能覆盖本地新内容。离线或超时必须显示未同步/失败，禁止报成功。

真实后端尚无部署实例。建议服务端负责人确认登录、注销、令牌过期/刷新、用户资料、待同步实体、版本冲突及错误格式后再定义 HTTP 客户端；本文件不指定 URL，不引入 schema 迁移。生产令牌存储与网络权限不属于演示 Fake 的验收范围。

## 负责人对接与当前待确认项

| 对接对象 | 待确认输入 | 待确认输出 | 当前状态 |
|---|---|---|---|
| F02/F03 负责人 | 设备模式、注册/连接状态源、遥测有效期、能力查询 | 相机/飞控可用状态和状态回读 | 尚未取得确认 |
| F11 负责人 | 任务启动时的指令请求及取消语义 | 请求 ID、进度和终态事件 | 尚未取得确认 |
| F13 负责人 | 复用现有模拟引擎的方式和模式标识 | 可重放模拟场景及状态映射 | 尚未取得确认 |
| F15/F16 相关负责人 | 真实或演示媒体的来源元数据 | 媒体记录与报告的数据来源字段 | 尚未取得确认 |
| 后端负责人 | 认证及会话接口、同步实体和冲突规则、测试环境 | 最小可用 API 契约 | 尚无已验证后端契约 |

确认后更新本表和接口版本，并在测试中加入对应适配器。确认之前可实现纯本地 Fake 和与设备无关的状态逻辑；不得推断共享 SDK 管理器或真实服务端行为。

## T00 基线运行记录

记录日期：2026-11-02。工作区开始时已有用户改动：`gradlew.bat`（删除固定 `JAVA_HOME` 两行）、未跟踪 `docs/`、`test_artifacts/` 和已填写 Word 文档。本轮未覆盖这些内容。

| 检查 | 结果 |
|---|---|
| `git status --short` | 已记录如上；`docs/` 与本计划同属已有未跟踪目录，未将其当作干净基线 |
| `git diff --stat` | 现有已跟踪差异只有 `gradlew.bat` 两行删除 |
| 相关 API 文档核对 | 认证、App 登录和四种基础飞控控制调用列为尚未实现；图传解码链路不存在；后端 URL 尚未确定 |
| `java -version` | 可用 Zulu OpenJDK 25.0.2；项目基线文档记录要求 JDK 17 |
| `./gradlew.bat --version` | 失败，退出码 1。Wrapper 尝试访问 `D:\dev\android\gradle\wrapper\dists\...` 并在锁文件创建阶段返回 `FileNotFoundException/拒绝访问`，尚未进入 Gradle 任务 |
| `./gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks` | 已运行；退出码 1，Wrapper 在同一外部 Gradle 缓存锁文件处失败，测试任务没有启动 |
| `./gradlew.bat :app:lintDebug --offline` | 已运行；退出码 1，Wrapper 在同一外部 Gradle 缓存锁文件处失败，Lint 任务没有启动 |
| `./gradlew.bat :app:assembleDebug --offline` | 已运行；退出码 1，Wrapper 在同一外部 Gradle 缓存锁文件处失败，构建任务没有启动 |

**初次基线结论：** Wrapper 因外部缓存访问失败而未进入 Gradle 任务，初次尝试没有验证结论。后续进展及复跑结果记于下文。跨队接口确认仍待对应负责人提供；当前未改动机器级环境或仓库 `gradlew.bat`。

### 后续执行进展（2026-11-02）

- 使用已有 `D:\dev\JDK\jbr-17.0.14` 作为本进程 `JAVA_HOME` 后，Gradle 8.13 Wrapper 正常启动；未修改机器环境或仓库 Wrapper。
- 基线单元测试 `:app:testDebugUnitTest --offline --rerun-tasks` 通过；基线 `:app:assembleDebug --offline` 通过。
- 基线 `:app:lintDebug --offline` 未能完成：离线缓存没有 `com.android.tools.lint:lint-gradle:31.12.0`。没有联网下载或安装依赖。
- 新增 `LocalCommandMatcher`，避免“停止录像”误判为“悬停”，对否定和多动作文本拒绝匹配；航拍页相机模式切换失败时不再继续拍摄；未知 LLM 意图不再允许执行未校验原文。
- 新增 `SimulatedFlightCommandExecutor` 和状态迁移单测；航拍页四种基本动作目前使用页面实例内的本地模拟状态，不调用 DJI SDK。
- 航拍页为现有可选 `test.mp4` 演示源加入常驻来源说明、文件缺失/播放失败提示及重试入口；尚未在 Android 设备验证播放器或随包资源。
- 新增内存 `DemoSessionController`、`DemoSession` 和标注为演示的 `DemoLoginActivity`；首页要求有当前进程内的演示用户名，退出清除内存会话后返回演示登录。该流程不校验账号、不保存密码或令牌，不构成真实认证。网络同步 Fake、幂等与冲突处理尚未实现。
- 新增代码后 `:app:testDebugUnitTest --offline --rerun-tasks` 通过，`:app:assembleDebug --offline` 通过。JUnit XML 位于 Gradle 默认报告目录；命令输出另存于 `test_artifacts/chenyanyu-20261102-unit-final.log` 和 `test_artifacts/chenyanyu-20261102-build-final.log`。完整 lint 尚受上述离线缓存阻塞。
- F24 另增加内存 `FakeSyncGateway`，覆盖会话代次、重复幂等键和版本冲突；没有联网请求或真实服务端写入。

**当前验收边界：** F05/F06 的本地逻辑缺少设备 UI 回归；F12 只完成即时确定性状态模拟，不含异步进度、连接状态适配及真实飞控；F04 未提供打包示例视频，错误处理尚未设备实测；F24 有演示登录/退出 UI，但没有后端认证、服务端会话或同步网关。F02/F03、F11、F13、F15 以及后端负责人均未在本记录中确认跨队接口。以上五项都不能标记为完整验收通过。

### F05 收尾进展（2026-11-03）

- 新增 `aerial/camera/CameraGateway` 接口和 `CameraOperationController.kt`，由状态机串行执行模式切换与拍照/开始/停止录像。
- 新增 `DjiCameraGateway` 将既有 `AerialDroneManager` SDK 调用封装到接口后面；航拍页统一经控制器操作相机。模式切换失败不下发拍摄；busy 和录像状态下的重复开始会被拒绝；停止失败保留 RECORDING 状态。
- 新增 JVM Fake 控制器测试：照片模式失败、视频模式失败、忙碌期间重复开始、录像中再次开始、停止失败。
- 当前仅验证 Fake 控制器逻辑编译与单测。尚未在 Android 设备/Fake UI 上执行相机流程；设备断连回调、超时后状态确认仍待 F02 设备状态契约接入。
