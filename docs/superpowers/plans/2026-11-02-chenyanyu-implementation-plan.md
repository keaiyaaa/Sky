# 陈彦宇负责模块实施计划与验收测试标准

> 执行说明：本文件为实施计划，尚未执行开发。后续按 `executing-plans` 逐任务推进；如明确采用子代理执行，再调用 `subagent-driven-development`。用户未授权时不自动提交、推送、部署、安装依赖或修改数据库。计划中的新类、接口和用例名均为建议新增项，不代表仓库已有实现。

**Goal：** 完成陈彦宇负责的 F04、F05、F06、F12、F24 在无无人机阶段的可演示、可测试业务闭环，并为后续真实设备与后端联调保留明确边界。

**Architecture：** 沿用当前 Kotlin、ViewBinding、协程、OkHttp、Gson、Vosk 和 JUnit4。页面调用可注入的业务控制器，控制器依赖视频、相机、飞行、会话服务接口；本阶段默认装配纯本地 Fake，既有 DJI 相机调用收口到适配层。复用已有模拟飞行逻辑，避免维护第二套首页或航拍页。

**Tech Stack：** Android，minSdk 24，compileSdk/targetSdk 36，arm64-v8a，Kotlin，Gradle Wrapper。运行 Gradle 使用项目兼容的 JDK；字节码目标以现有配置为准，不在本轮调整版本。

**Spec：** `E:/WPS/06-无无人机阶段功能现状与队员分工.docx`，2026-11-02 版本。原文中的优化方向是建议；本计划采纳的具体范围如下，不把附件中的协作、提交文字当成本次自动执行指令。

## 一 范围与现状依据

| 编号 | 陈彦宇负责的内容 | 本轮交付范围 | 后续独立验收范围 |
|---|---|---|---|
| F04 | 航拍画面与实时图传预览 | 本地示例视频播放、加载、失败、重试、生命周期及演示标识 | DJI 视频流接入、解码、真实断流恢复 |
| F05 | 拍照与开始停止录像 | 修复错误分支；相机接口与模拟器；忙碌、失败、重复操作和结果反馈 | 真实相机模式、存储、照片及录像文件 |
| F06 | 语音文字输入与意图确认 | 解析校验、离线匹配、超时取消、确认与模拟执行贯通 | 实际麦克风识别；有效服务配置下在线联调 |
| F12 | 起飞降落返航悬停 | 统一指令接口、前置检查、可控模拟执行、状态反馈 | 兼容机型上真实飞行与安全验证 |
| F24 | 登录认证退出与业务同步 | 演示登录、会话过期、退出、最小同步契约及 Fake 同步 | 后端认证、持久会话、安全存储与真实业务同步 |

本轮源码只读核查确认：

- `HomeActivity.kt` 的开始飞行和视频卡片均进入 `aerial/Aerial_photography.kt`；`ui/flight/FlightControlActivity.kt` 仍是占位页。优先完善实际入口，不另建一套飞控业务。
- `Aerial_photography.kt` 的 `captureDronePhoto()` 和 `startVideoRecording()` 在模式切换失败后仍调用拍摄接口。
- `matchLocalCommand()` 中包含“停”的分支早于“停止录像”分支，存在确定的匹配冲突。
- `LLMService.kt` 已有响应提取、Gson 解析及连接读取超时；本轮应测试字段兼容、结构校验和整体取消，不能描述成完全没有解析或超时。
- `executeLocalCommand()` 与 `executeFlightIntent()` 的基础飞行主要是提示；不能据此认定真实飞控已实现。
- `HomeActivity.showLogoutDialog()` 当前调用 `finishAffinity()`，不是会话注销。
- 已有 `SimulatedFlightEngine` 负责沿航点推进及避障，不直接提供起飞、降落、返航、悬停四种指令；需要薄适配及独立指令状态，不能假定现有引擎已具备这些行为。

### 全局约束

1. 仅修改五项任务直接涉及的代码，保留现有视觉风格及用户未提交内容。本轮开始时已有 `gradlew.bat` 修改及未跟踪的 `docs/`、`test_artifacts/` 和 Word 文件。
2. 不安装新依赖、不修改锁文件、数据库结构、CI/CD、部署、权限与密钥逻辑；必要变更单独列明原因并获得确认。
3. 不读取或输出 `.env`、凭据、会话令牌、证书等内容。在线测试只引用配置项名称，不把秘密写进报告或测试夹具。
4. 模拟、设备未连接、数据缺失分别表示。Fake 结果只能显示“模拟成功”，不能显示真实拍摄、飞行或同步成功。
5. 纯本地模拟不调用 DJI SDK，不依赖 SDK 注册。DJI SDK 模拟器与纯本地 Fake 分开标识。
6. 真实图传、拍摄、飞行、认证及同步未验证时统一保留“待设备验证”或“待后端联调”。
7. F12 限定四种基础指令；环绕、定向飞行、航线飞行、云台控制和连续拍摄不在本轮实现。收到这些意图必须提示不支持，不静默降级为其他动作。

## 二 Skill 调用安排

本环境技能根目录为 `C:/Users/大宇/.codex/skills/`，调用方式为读取对应 `SKILL.md` 并遵循流程。现有 `docs/05-Skill调用与执行规范.md` 的旧环境路径不能直接沿用。

### 本次编写计划实际使用

| Skill | 用途 | 本次产物 |
|---|---|---|
| using-superpowers | 识别和加载适用技能 | 本次技能选择与使用声明 |
| brainstorming | 梳理五项任务的范围、依赖、模拟与真实边界 | 范围表与方案说明；本次不进入代码实现 |
| writing-plans | 拆分任务、接口、依赖、验收与测试 | 本计划 |
| verification-before-completion | 检查计划覆盖及交付事实 | 文档结构、文件存在和差异检查；不冒充功能测试 |

### 后续实施按条件调用

| Skill | 触发时机 | 输入 | 必须留下的输出 |
|---|---|---|---|
| brainstorming | 接口设计进入实施前 | 本计划、实际源码、队员接口约定 | 设计评审与范围决定；跨模块接口确认 |
| using-git-worktrees | 工作区脏或需隔离开发时 | 当前分支和完整工作区状态 | 隔离路径、基线版本；不搬走未提交文件 |
| executing-plans | 按依赖串行实施时 | 已确认的设计与本计划 | 任务勾选、实际改动、验证和阻塞记录 |
| test-driven-development | 新增逻辑或修复缺陷时 | 具体行为与回归用例 | 先失败、最小修复、通过的红绿证据 |
| systematic-debugging | 测试失败或行为异常时 | 复现步骤、日志、最小输入 | 根因与最小修复，区分应用和环境问题 |
| requesting-code-review | 每模块收尾和集成前 | 任务要求、准确 diff、验证记录 | 规格符合性及代码问题清单 |
| receiving-code-review | 收到审查反馈时 | 具体问题和证据 | 逐项核实、修复或说明不采纳理由 |
| verification-before-completion | 每次宣布完成之前 | 本轮完整命令输出与人工证据 | 通过、失败、阻塞、未执行的准确结论 |
| finishing-a-development-branch | 全部验证及审查结束后 | 差异、测试报告、遗留项 | 交付与集成建议；提交推送仍遵守用户授权 |

`subagent-driven-development` 仅在明确选择子代理执行后使用；本次未调用子代理。后续技能须执行时读取全文，表中列出不表示已经调用。

## 三 方案选择与协作接口

建议采用“实际入口保留 + 最小控制器 + 可注入适配接口”。直接在 Activity 继续堆 SDK、网络和状态处理，短期修改少，但难以复现异步问题；整体重写 MVVM 或引入新架构会扩大范围，本轮不选。

所有下列类型都是拟新增的内部接口，不是已存在的公共 API。第一项开发任务先冻结命名和语义，再编写实现。

```kotlin
enum class DataMode { DEMO, DEVICE }
enum class BasicFlightAction { TAKEOFF, LAND, RETURN_HOME, HOVER }
enum class CameraAction { PHOTO, START_RECORD, STOP_RECORD }
enum class OperationStatus { SUCCEEDED, FAILED, REJECTED, TIMED_OUT, CANCELLED }
data class OperationResult(
    val requestId: String,
    val mode: DataMode,
    val status: OperationStatus,
    val message: String
)
interface CancelHandle { fun cancel() }
interface CameraGateway {
    fun execute(requestId: String, action: CameraAction,
                callback: (OperationResult) -> Unit): CancelHandle
}
interface FlightCommandExecutor {
    fun execute(requestId: String, action: BasicFlightAction,
                callback: (OperationResult) -> Unit): CancelHandle
}
```

约定：一次请求最多完成一次；取消前未发出的动作不再发出；取消后旧回调不能刷新 UI 或触发新动作。已经交给真实飞机或相机的动作，取消等待不代表物理动作被撤销，须显示“结果未知，等待设备状态确认”。超时同理，不能自动重发真实控制命令。

| 对接人或模块 | 陈彦宇消费的输入 | 陈彦宇提供的输出 | 无前置时的处理 |
|---|---|---|---|
| 彭诗情 F02/F03 | SDK 注册、设备连接、遥测快照、更新时间、设备能力 | 飞行和相机状态、错误原因 | 用 Fake 注入；真实模式缺值或过期则拒绝执行 |
| 冯梓鹏 F11 | 任务标识与基础动作请求 | 请求 ID、状态事件、终态 | 用固定任务 ID 做联调；不重写航线任务调度 |
| F13 负责人 | 现有模拟引擎及可复现场景 | 基础指令适配与确定性结果 | 使用独立 Fake；先确定共同状态语义 |
| 许培杨相关任务媒体模块 | 任务 ID、媒体接收约定 | 模拟媒体引用、来源、操作时间 | 不创建真实采集记录，不修改其数据库 schema |
| 后端与 F19/F20/F21 等消费者 | 用户、设备、任务接口约定 | 会话状态、注销事件、最小同步结果 | 先只交付内存 Fake 与接口文档，不假定服务已存在 |

## 四 实施任务与交付顺序

下列源码路径均相对于仓库 `E:/Codex/Project/sky/`。`main/` 简写为 `app/src/main/java/com/example/msdksample/`，`test/` 简写为 `app/src/test/java/com/example/msdksample/`。所有新增路径在实施时创建，现有同名类型优先复用。

### T00 基线与接口约定 建议半天

**文件：** 只读现有五项模块；新增 `docs/contracts/chenyanyu-module-contracts.md` 和本轮测试记录。

- [ ] 查看 `git status --short`、`git diff --stat`、相关源码 diff，确认归属；必要时隔离开发。
- [ ] 和 F02/F03、F11、F13 负责人确认模式、状态、线程和生命周期语义，记录接口版本。
- [ ] 固定本轮输入：本地视频、意图 JSON、语音转写文本、Fake 用户和同步数据；夹具不得包含真实个人数据。
- [ ] 运行第七节基线命令，记录原有失败及环境；不将旧报告算成本轮结果。

**验收：** 五项范围和接口记录齐全；每个基线命令有退出码及报告位置。构建基线受阻时，先处理环境阻塞，不承诺交付日期。

### T01 F05 相机操作闭环 建议一天

**修改：** `main/aerial/Aerial_photography.kt`、`main/aerial/AerialDroneManager.kt`。
**新增：** `main/aerial/camera/CameraGateway.kt`、`CameraController.kt`、`FakeCameraGateway.kt`、`DjiCameraGateway.kt`；`test/aerial/camera/CameraControllerTest.kt`。

**接口：** 消费 `CameraGateway`、设备状态和模式；产出请求终态及可展示的相机状态。现有模式切换与拍摄方法由 DJI 适配器顺序调用。

- [ ] 先写 `modeSwitchFailureDoesNotShoot`：Fake 模式切换失败，断言拍摄调用数为 0；执行定向单测并确认原错误行为可被捕获。
- [ ] 修复失败分支：模式切换成功才发送拍摄；失败恢复可操作状态并显示原因。
- [ ] 增加 `IDLE / BUSY / RECORDING / UNKNOWN` 状态；正在录像时拒绝重复开始，正在操作时防重复提交。
- [ ] 停止录像成功才显示停止；失败保持已知录像状态，断连或超时标记未知并提示确认设备状态。
- [ ] 注入模拟成功、存储不足、断连、延迟和失败，验证旧回调及页面销毁处理。
- [ ] 重跑相机用例，再做页面人工验证；审查错误分支与线程切换。

**验收：** 失败不续拍；十次快速点击只发送一次有效操作；Fake 与真实入口分离；所有失败均可恢复或明确显示状态未知。真实媒体是否产生另行验收。

### T02 F06 意图解析确认与取消 建议两天

**修改：** `main/aerial/LLMService.kt`、`FlightIntentData.kt`、`Aerial_photography.kt`、必要时 `VoskHelper.kt`。
**新增：** `main/aerial/intent/IntentParser.kt`、`LocalCommandMatcher.kt`、`CommandCoordinator.kt`；`test/aerial/intent/IntentParserTest.kt`、`LocalCommandMatcherTest.kt`、`CommandCoordinatorTest.kt`。

**接口：** 消费转写或文字、现有 `FlightIntent` 和相机/飞行执行器；产出待确认意图与执行结果。`CommandCoordinator` 只接受当前有效请求的一次确认。

- [ ] 先写 `stopRecordingDoesNotMatchHover`，输入“停止录像”，断言结果为停止录像；运行并保留失败证据。
- [ ] 采用完整动作优先匹配；覆盖“停止录制”“不要起飞”“不要拍照”“先起飞再拍照”。否定或多动作歧义须拒绝或要求重述，不能取第一个关键词执行。
- [ ] 用夹具验证现有 `choices[0].message.content` 提取，再验证 JSON、代码围栏、空字段、错误类型和未知动作。
- [ ] 固定内部字段 `flightType`；仅为已明确约定的外部字段提供兼容映射，冲突字段拒绝，不猜测。必需字段与枚举校验失败不得进入执行。
- [ ] 统一录音、分析、待确认、执行、终态；新增取消句柄及请求代次，页面退出、用户取消或整体超时后忽略旧响应。
- [ ] 超时只切换到离线匹配和重新确认，不自动执行。在线失败不透出原始响应或敏感配置。
- [ ] 在线未知意图不再提供绕过结构校验的原文执行路径；不支持的复杂意图明确拒绝。
- [ ] 确认后统一路由至 F05/F12；重复确认只提交一次；取消后执行次数为 0。
- [ ] Android 设备单独验证录音权限、拒绝后重试、模型缺失与录音资源释放；文字测试不能替代语音实测。

**验收：** 同一指令最多执行一次；未知、否定、歧义、取消与过期请求执行次数为 0；“停止录像”准确进入 F05；在线与离线均有明确状态且必须确认。

### T03 F12 基础指令模拟闭环 建议一天半

**修改：** `main/aerial/Aerial_photography.kt` 的实际指令分发。
**新增：** `main/drone/command/FlightCommandExecutor.kt`、`FlightPreflightChecker.kt`、`SimulatedCommandExecutor.kt`；`test/drone/command/FlightPreflightCheckerTest.kt`、`SimulatedCommandExecutorTest.kt`。
**复用但不默认修改：** `main/drone/SimulatedFlightEngine.kt`。不启用占位 `FlightControlActivity` 作为第二业务入口。

**接口：** 消费四种 `BasicFlightAction`、模式、状态快照；产出明确的模拟状态和请求终态。初始状态为地面；返航需要已知模拟起点。

- [ ] 先写缺少有效前置条件时“返回拒绝且执行器调用为 0”的测试。
- [ ] DEMO 模式只消费模拟状态；DEVICE 模式必须消费设备真实状态。注册、连接、必要遥测缺失或过期、不具备能力时拒绝，禁止借用演示数据放行。
- [ ] 实现地面→起飞中→空中，空中→降落中→地面；返航和悬停只接受合适空中状态。模拟返航最终到达模拟起点并标记悬停，不自动追加降落。
- [ ] 使用可注入时钟或手动推进模拟延迟，覆盖起飞中重复起飞、降落中返航、模拟断连和取消，不依赖真实 sleep。
- [ ] F06 确认弹窗调用同一执行器；F11 可消费同一请求结果，不新增其任务编排逻辑。
- [ ] 真实执行器本轮显式返回未接入；不得把 Toast 或 SDK 请求受理当作飞机动作完成。

**验收：** 四动作均有正向、非法状态和异常用例；DEMO 下 SDK 调用为 0；DEVICE 未接入明确拒绝；模拟状态流转可重复；不暗示真实飞行完成。

### T04 F04 演示视频和预览状态 建议一天

**修改：** `main/aerial/Aerial_photography.kt`、`app/src/main/res/layout/activity_aerial_photography.xml`。
**新增：** `main/aerial/video/PreviewController.kt`、`PreviewSource.kt`；`test/aerial/video/PreviewControllerTest.kt`；有授权的短视频夹具放 `app/src/main/res/raw/demo_preview.mp4`。

**接口：** `PreviewSource` 提供播放、停止、释放及准备完成、错误、结束事件；控制器产出 `IDLE / LOADING / PLAYING / ERROR / STOPPED`。首先核查并复用现有播放器能力，不默认引入媒体库。

- [ ] 先测试加载失败进入 ERROR，点击重试只创建一个有效播放会话。
- [ ] 接入授权本地视频；显示“演示视频”，加载和错误提示不覆盖必要控制入口。
- [ ] 用 Fake 视频源注入准备失败、播放中断、重试成功；本地视频中断测试不得表述成真实图传断流测试。
- [ ] 页面离开暂停或停止并释放播放器，返回按明确规则恢复；旧源回调不污染新页面。
- [ ] 连续二十次进入退出页面，检查闪退、重复声音和多播放器问题；保留设备记录。

**验收：** 视频能在无无人机条件播放；失败可重试；演示标识持续可见；页面退出停止声音；真实图传入口明确未接入。

### T05 F24 模拟会话与最小同步契约 建议两天

**修改：** `main/HomeActivity.kt` 的退出行为；与账户页负责人协作修改 `main/ui/settings/AccountFragment.kt` 的演示身份展示。
**新增：** `main/auth/SessionRepository.kt`、`FakeSessionRepository.kt`、`SessionController.kt`、`DemoLoginActivity.kt`；`main/sync/SyncGateway.kt`、`FakeSyncGateway.kt`；演示登录布局及必要 Manifest 页面声明；`test/auth/SessionControllerTest.kt`、`test/sync/SyncGatewayTest.kt`；契约补入 `docs/contracts/chenyanyu-module-contracts.md`。

**接口约定：** `SessionState` 为 `SIGNED_OUT / SIGNING_IN / SIGNED_IN / EXPIRED`；登录输入是虚构演示身份，不接受真实密码；注销同步使当前会话代次失效。`SyncGateway` 输入为记录 ID、版本、幂等键及会话代次，结果为成功、无权限、网络失败或版本冲突。

- [ ] 先写注销使会话失效的测试：已登录→注销→旧请求返回，断言不能重新恢复用户态。
- [ ] 建立明确标注的演示登录，Fake 会话只存内存；重启回到未登录。不存真实令牌，不伪造生产认证安全能力。
- [ ] 将退出改为清理演示会话、取消其请求、回到演示登录；保留无账号本地演示功能，登录门禁仅保护本轮声明的账号业务。
- [ ] 会话过期仅触发一次登录提示；旧用户的延迟回调不能写入新用户状态。
- [ ] 定义最小同步对象和错误映射，用独立内存样例记录演示上传与读取；重复幂等键只接受一次，版本冲突不覆盖本地内容。
- [ ] 离线不显示已同步；超时保留失败或未同步状态，提供显式重试；退出清理会话内的模拟待同步队列。
- [ ] 写明真实接口的登录凭据类型、用户信息、注销、过期语义、记录读写和冲突规则，由后端负责人确认。真实 URL、认证存储、权限规则、数据库或部署改动另外审批后实现。

**验收：** 演示登录、错误、过期、退出及重新登录闭环成立；跨用户数据不串；重复同步不重复写；冲突不丢数据。真实认证、安全持久化及业务同步仍为“待后端联调”。

### T06 集成与交付 建议一天

**文件：** 新增 `docs/testing/chenyanyu-acceptance-2026-11-02.md`；实际证据放 `test_artifacts/chenyanyu/<实际运行时间>/`，不覆盖原有目录。

- [ ] 完成“演示登录→进入航拍→视频播放→文字拍照→确认→模拟结果→开始及停止录像→起飞/悬停/返航/降落→退出”的完整链路。
- [ ] 故障链路至少覆盖视频失败、模式切换失败、解析失败、超时、取消、权限拒绝、会话过期、同步冲突。
- [ ] 执行全部 JVM 测试、Lint、APK 构建及可用设备测试，按第七节留证。
- [ ] 回归首页、航拍、航线、任务中心、设置；确认其他队员模块没有因入口或共享状态改动受损。
- [ ] 完成规格符合性与代码审查；逐项处理阻塞问题，逐条更新五项功能的限定状态。
- [ ] 汇总改动文件、测试结果、未验收项与演示步骤；如需更新原始 Word 或推送仓库，另按用户明确指令操作。

**计划工作量：** 单人约九个工作日，包含接口核对、实现、测试和集成，不包含等待设备、后端、模型下载和环境修复。顺序为 T00→T01→T02→T03→T04→T05→T06；T02 可先接拒绝执行的飞控桩，T03 后再完成飞行闭环。此为估算，非既定交付承诺。

## 五 功能验收标准

功能验收判断用户可观察行为，技术测试判断实现是否具备可复现证据。两者分别记录，不能以编译通过替代功能验收。

| 验收 ID | 功能与操作 | 必须满足的结果 | 证据 |
|---|---|---|---|
| A04-01 | 打开航拍页播放示例视频 | 明示演示；加载后可播放；设备未连接不阻止本地播放 | 设备录屏及状态记录 |
| A04-02 | 注入播放失败后重试 | ERROR→LOADING→PLAYING；旧回调无效 | 单测与录屏 |
| A05-01 | 相机模式切换失败 | 拍照或录像命令发送次数为 0，有失败提示 | Fake 调用记录与单测 |
| A05-02 | 快速重复拍照或开始录像 | 单次忙碌窗口仅一个动作；失败后可重试 | 单测与手工记录 |
| A05-03 | 录像停止失败或中途断连 | 不显示停止成功；保留已知状态或标为未知 | 异常用例 |
| A06-01 | 输入“停止录像”并确认 | 执行停止录像，悬停调用数为 0 | 回归单测 |
| A06-02 | 取消、超时后旧响应、重复确认 | 取消与过期执行 0 次，正常确认最多 1 次 | 时序单测 |
| A06-03 | 未知、否定、多动作、缺失字段 | 不执行；给出重述或不支持提示 | 解析与匹配用例 |
| A06-04 | 麦克风拒绝、模型缺失和正常识别 | 不崩溃，不自动执行；正常识别进入确认 | ARM64 设备记录 |
| A12-01 | 四种基础指令 | 合法状态有模拟结果，非法状态明确拒绝 | 状态迁移测试 |
| A12-02 | 切到 DEVICE 且未接入 | 拒绝执行；不能借用模拟遥测放行 | Fake 调用计数 |
| A24-01 | 演示登录、过期、退出、重进 | 身份状态一致；退出后旧回调无法恢复登录 | 会话单测及录屏 |
| A24-02 | 重复同步、离线、版本冲突 | 不重复写、不假成功、不覆盖冲突内容 | Fake 服务记录 |
| AX-01 | 五项集成与其他入口回归 | 无阻塞崩溃；模式标识和结果一致 | 完整冒烟清单 |

无设备阶段放行条件：所有适用的验收项通过；无未关闭 P0/P1 问题；其他缺陷记录影响和处置。P0 指未确认执行、取消后执行、跨用户泄露或其他安全失控；P1 指关键链路不可用、崩溃、重复命令或假成功；P2 为非阻塞体验问题。

缺少 ARM64 Android 设备时，A06-04 和播放器页面行为等标记“阻塞”；可交付 JVM 逻辑成果，但不宣称完整无设备阶段验收通过。

## 六 测试标准与最小用例集

### 自动测试要求

- 采用现有 JUnit4，使用手写 Fake 和可注入时钟；默认不新增 Mock、协程测试或网络测试依赖。
- JVM 测试只测纯 Kotlin 业务逻辑，避免加载 DJI JNI；播放器、麦克风、真实 SDK 放到合适设备层验证。
- 每个修复先取得针对错误行为的失败断言，再做最小实现，最后重跑通过；编译错误、缺依赖或环境故障不能当作有效红灯。
- 每项业务至少覆盖成功、拒绝或失败、取消或生命周期、重复或迟到回调四类适用场景；不以固定测试数量代替行为覆盖。
- 不预设覆盖率工具和比例；本轮要求下表关键分支逐条有用例 ID、执行结果和报告，无静默跳过。

| 用例组 | 最小测试输入或场景 | 核心断言 |
|---|---|---|
| V01 | 视频正常、加载失败、播放中断、重试 | 状态正确、单一活动源 |
| V02 | 页面释放后准备完成回调 | 不继续播放、不更新已释放页面 |
| C01 | PHOTO 模式切换失败 | 拍摄调用为 0 |
| C02 | START_RECORD 模式切换失败 | 开始录像调用为 0 |
| C03 | 连续十次操作、开始回调重复 | 有效动作最多一次、终态一次 |
| C04 | 停止录像失败、存储不足、断连、超时 | 不显示成功，保留可解释状态 |
| I01 | 正常 JSON、围栏 JSON、空 choices、空 content | 有效内容通过，空内容可解释失败 |
| I02 | 缺失 flightType/parameters、非法类型、未知枚举 | 校验拒绝，无执行调用 |
| I03 | 停止录像、停止录制、悬停 | 动作互不混淆 |
| I04 | 不要起飞、不要拍照、先起飞再拍照 | 不执行，要求重述或确认单一动作 |
| I05 | 超时后在线返回、取消后识别返回、页面销毁 | 不弹旧确认、不执行 |
| I06 | 离线降级与重复确认 | 重新确认后至多一次执行 |
| F01 | 四动作合法状态迁移 | 终态及顺序与约定一致 |
| F02 | 地面悬停、地面返航、重复起飞、降落中返航 | 明确拒绝 |
| F03 | DEVICE 缺遥测、过期、断连、无能力 | 拒绝，真实下发调用为 0 |
| F04 | DEMO 正常、失败、取消、迟到回调 | 不触发 SDK，不重复终态 |
| S01 | 正确演示身份、错误身份、重复登录 | 正确状态转换，无重复提交 |
| S02 | 过期、退出、退出后旧请求返回 | 账号失效，旧请求无权恢复 |
| S03 | A 退出后 B 登录，A 请求迟到 | 不污染 B 用户态 |
| S04 | 同幂等键重复同步、离线、冲突 | 接受一次，不假成功、不丢内容 |

建议首批回归测试形式如下；这些是实施时拟新增的测试契约，不是当前可执行的既有测试：

```kotlin
@Test fun stopRecordingDoesNotMatchHover() {
    assertEquals(CameraAction.STOP_RECORD,
        LocalCommandMatcher().matchCamera("停止录像"))
}
```

`LocalCommandMatcher.matchCamera(text: String): CameraAction?` 为 T02 的拟新增纯函数；否定、歧义和不支持输入返回 null，进一步由协调器给出拒绝原因。相机失败测试以可控 Fake 的调用计数断言为准，禁止只断言 Toast 文案。

### 设备与人工测试要求

1. 记录设备型号、Android 版本、ARM64 架构、构建版本与模式。arm64-v8a 限制已在 Gradle 配置中确认；x86_64 环境不能证明 JNI 或语音媒体链路可运行。
2. 至少覆盖现有目标横屏尺寸中的手机和平板布局；没有对应设备则记录未验证，不虚构覆盖。
3. 拍照、录像和指令快速点击按每组十次检查；视频页面进入退出二十次；应用连续演示十分钟，无闪退、卡死、后台录音或重复播放。
4. 在线意图先用离线夹具测解析；真实外部请求需明确授权和有效服务配置，本计划不自动请求收费服务。
5. 建议本地交互一秒内显示处理中或错误状态；此为拟定体验门槛，设备和测量条件必须随记录说明。网络整体超时采用设计评审确认的有限值，以注入时钟测试边界，不能临时无限等待。

## 七 执行命令与证据标准

以下为后续实施命令，均在 `E:/Codex/Project/sky/` 下执行；本次计划编写没有运行 Gradle，也没有形成测试通过结论。

```powershell
git status --short
git diff --stat
git diff --check
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks
.\gradlew.bat :app:lintDebug --offline
.\gradlew.bat :app:assembleDebug --offline
```

模块定向测试示例，在相应测试文件创建后执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.msdksample.aerial.camera.CameraControllerTest"
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.msdksample.aerial.intent.*"
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.msdksample.drone.command.*"
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.msdksample.auth.*"
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.msdksample.sync.*"
```

接入可用 ARM64 Android 测试设备并核对安装目标后，执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --offline
```

该命令会安装测试包并启动测试，仅用于指定测试设备，不连接无人机执行真实动作。当前默认示例仪器测试不能证明五项业务通过，须先新增有业务断言的设备用例，或逐条执行人工验收清单。

报告位置通常为：

- JVM XML：`app/build/test-results/testDebugUnitTest/`。
- JVM HTML：`app/build/reports/tests/testDebugUnitTest/index.html`。
- Lint：`app/build/reports/lint-results-debug.html` 及实际生成的 XML。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 设备测试：`app/build/reports/androidTests/connected/`，以本轮实际路径为准。

每次完整运行后立即复制本轮结果到独立证据目录；证据至少含命令、时间、退出码、提交 SHA、工作区差异清单、设备和模式、用例通过失败跳过数、截图或录屏、缺陷与阻塞。重试使用新目录，不能覆盖失败记录。

离线缺缓存、JDK/SDK 不可用、JNI 架构不匹配等记录“环境阻塞”；不得自动安装依赖或把未执行写成通过。Lint 如有既有问题，先区分基线和新增，不能通过屏蔽规则制造通过。构建本身承担 Kotlin 编译检查，本工程未声明独立类型检查命令。

## 八 真机与真实后端阶段的追加门槛

| 功能 | 解除前置 | 追加验收要求 |
|---|---|---|
| F04 | 兼容无人机、有效 SDK 配置、真实视频适配 | 首帧、连续播放、断流、重连、页面释放均实测；记录机型与固件 |
| F05 | F02 连接可用、相机能力和存储可用 | 模式切换读回；照片可查看；录像可回放；断连与存储异常结果一致 |
| F06 | ARM64 Android 麦克风、Vosk 模型；在线服务配置 | 实际语音录入和确认链路；真实在线响应、超时和取消；不把文字夹具当语音证据 |
| F12 | F02/F03 真数据、安全检查和明确实飞授权 | 按批准的设备测试方案验证起飞、降落、返航、悬停及状态反馈；不套用模拟高度或安全阈值 |
| F24 | 后端契约、测试账号、测试环境与安全设计 | 真实登录注销、过期、服务端鉴权、跨账号隔离、同步幂等冲突、会话安全存储和恢复 |

真实飞行与真实后端写入不属于本次执行授权。设备到位后须先形成具体环境、参数、风险与恢复方案，再由负责人安排测试。

## 九 交付完成清单

- [ ] 五项功能分别有代码变更、对应测试、演示路径及状态说明。
- [ ] 每条验收项和测试组均有本轮结果；未执行与阻塞明确列出。
- [ ] 新增逻辑与既有模拟飞行、避障测试一起回归，构建和 Lint 结果可追溯。
- [ ] 取消、失败、重复、旧回调和跨用户隔离等关键分支审查通过。
- [ ] 真实硬件、语音实测、在线服务及后端验收状态独立记录。
- [ ] 无新增生产依赖、数据库变更或接口破坏；如实际需要，先重新确认范围和审批。
- [ ] 不覆盖用户改动，不自动 commit/push，不把模拟成功更新为整体已完成。

本文件交付的是可实施与可审查的计划。任务复选框保持未完成，直到对应开发和验证实际发生。
