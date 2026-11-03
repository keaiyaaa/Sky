# Phase 04 执行计划：无人机与后端集成

## A. 阶段基本信息

| 项 | 内容 |
|---|---|
| 阶段名称 | 无人机与后端集成（Phase 04，含可拆分的 P04b 后端子阶段） |
| 目标 | 真机闭环：打通并验证 KMZ 上传→起飞→巡拍→返航链路（D-01/D-04/D-05 实测）；真机遥测接入（KeyManager.listen）；LLM Key 配置化（D-07 完整核销）；P04b：最小后端（登录/任务/记录）对接 |
| 对应总体计划章节 | `docs/02-总体开发计划.md` §5 Phase 04 行、§6 里程碑 M4、§8 风险 R1/R2 |
| 依赖的前置阶段 | Phase 03 合并（FlightControlApi、PreflightChecker、actionGroup 导出） |
| 当前输入条件 | P03 真机验证清单；外部资源（无人机、App Key 有效性、后端资源）的到位确认 |
| 阶段边界 | 不做真实 AI 识别服务；不做会员支付；后端仅最小集（§C Task 07 范围）；所有真机飞行须在合规空域并由持证操作者执行，**AI 代理不直接发出任何物理飞控指令**——指令由人工在 App 上执行，代理仅负责代码与测试 |

## B. Skill 调用声明

| Skill | 调用时机 | 目的 | 输入 | 预期输出 | 验证方法 |
|---|---|---|---|---|---|
| using-superpowers | 会话开始 | 纪律入口 | — | 声明 | 对话记录 |
| brainstorming（bounded/architectural 视任务） | Task 02/03/07 前 | 确认改动边界（真机适配多为 bounded；后端对接为 architectural，需 spec） | 验证清单结果 | 设计/短设计 + 批准 | 批准记录 |
| writing-plans / executing-plans | spec 后 | 计划与执行 | spec | 计划+台账 | task-done |
| subagent-driven-development | Task 04/05/08 等独立任务 | 并行 | 简报 | 审查记录 | reviewer 结论 |
| test-driven-development | 全部编码任务 | 铁律（真机不可测部分以 Fake/录制回放测试固化） | — | 红绿记录 | 输出 |
| systematic-debugging | **真机联调主 Skill**：每次真机失败按四阶段出证据（日志抓取、分层定位：注册层/连接层/上传层/指令层） | 根因 | logcat/SDK 回调 | 根因报告 | 证据链 |
| verification-before-completion | 每任务与里程碑 | 真机验证以"清单逐项输出"为准 | 清单 | 核销表 | 清单文件 |
| requesting-code-review / receiving-code-review | 阶段末/收到反馈 | 审查 | SHA | findings | 闭环 |
| using-git-worktrees | 开工 | 隔离 | — | worktree | 分支状态 |

## C. 原子任务拆分

### Task 01：真机联调准备（进入条件核验）

| 项 | 内容 |
|---|---|
| 编号/名称 | 01 / 设备与凭证就绪检查 |
| 目标 | 形成可执行的联调环境清单，未达标项明确升级路径 |
| 前置条件 | **进入条件（未满足则本阶段真机任务全部挂起，仅推进 Task 04/05/06）**：① DJI 机型到位（App Key 对应机型，Mavic 系列）② App Key 有效且与包名 `com.example.msdksample` 绑定（或完成包名/Key 决策与重注册）③ 遥控器/电池/合规空域/操作者 ④ 联调记录表模板 |
| 涉及文件 | 新建 `docs/phases/checklists/phase-04-device-checklist.md`（**建议新建路径**） |
| 具体操作 | 逐项核验并记录（Key 有效性以一次成功 registerApp 日志为准） |
| 应调用 Skill | verification-before-completion |
| 测试/验证命令 | 冒烟包安装：`adb install -r app-debug.apk` → 首页设备卡连点 5 次 → SDK 测试面板 initSDK → 观察 registerApp 成功日志 |
| 预期结果/完成标准 | 清单全绿或明确升级路径；基线 commit 固化 |
| 回滚/恢复 | 仅文档，无回滚需求 |

### Task 02：真机链路打通（D-01 验证与修复）

| 项 | 内容 |
|---|---|
| 编号/名称 | 02 / KMZ 上传与任务启动真机验证 |
| 目标 | P03 已编码的反射网关在真机上完成 `pushKMZFileToAircraft → startMission` 全链路 |
| 前置条件 | Task 01 全绿 |
| 涉及文件 | `kami/mission/DjiV5ReflectiveMissionGateway.kt`（预期需修：反射签名与真机行为差异）、`kami/mission/FlightProgressTracker.kt` |
| 具体操作 | ① 连接真机 → 生成 2 航点 KMZ → 一键起飞（**人工执行**）② 按 systematic-debugging 分层取证：isRegistered？管理器实例？上传回调？startMission 错误码 ③ 修复后回归 ④ 遥测接入顺路验证（Task 03） |
| 应调用 Skill | systematic-debugging、test-driven-development（每处修复以 Fake 固化） |
| 测试 | 反射签名适配的单测（类名/方法名常量表） |
| 验证命令 | 真机日志记录表逐项输出；`./gradlew :app:testDebugUnitTest` 全绿 |
| 预期结果 | 真机完成一次 2 航点任务飞行 |
| 完成标准 | 清单核销 + 修复提交 |
| 回滚/恢复 | 网关改动独立提交可 revert；飞行中止按 WPML 失控返航 + 遥控器人工接管（现场规程，非代码问题） |

### Task 03：真机遥测接入（D-04 完整核销）

| 项 | 内容 |
|---|---|
| 编号/名称 | 03 / KeyManager.listen 接入 TelemetryBus |
| 目标 | 电量/信号/高度/速度/距离/位置来自真机；首页 OSD 与 kami 进度条显示真机数据；低电量监听接入（`KeyLowBatteryWarning`）触发 Preflight 层告警 |
| 前置条件 | Task 01；P03 TelemetryBus |
| 涉及文件 | `drone/DroneManager.kt:789-790`（TODO 分支实现）、`drone/DroneManager.kt:471-474`（真实 Key 读取）、`drone/flight/PreflightChecker.kt`（低电告警接入） |
| 具体操作 | ① listen 各 Key → bus ② 移除模拟钳制（保留模拟模式自身语义）③ TDD：Fake KeyListener → bus 字段映射测试 |
| 应调用 Skill | test-driven-development、systematic-debugging、verification-before-completion |
| 测试 | 映射单测 + 真机日志核销 |
| 验证命令 | 真机：OSD 数值与 DJI 遥控器显示对照记录 |
| 完成标准/回滚 | 对照误差可接受（Ruling 定标）+ 核销；revert 可回模拟 |

### Task 04：安全设置真机下发（D-05 完整核销）

| 项 | 内容 |
|---|---|
| 编号/名称 | 04 / 返航高度/失控行为/新手模式/灵敏度真机下发 |
| 目标 | 8 个 stub 中具备 MSDK 支持的项接通（返航高度 `KeyGoHomeHeight`、失控行为 `KeyDisconnectProtectedAction`/RTH 设置、新手模式等以 5.18.0 provided jar 实际 Key 为准），并真机验证；无 SDK 支持的项如实降级为本地项 |
| 前置条件 | Task 01 |
| 涉及文件 | `drone/DroneManager.kt:863-1111`（stub 实现）、`ui/settings/`（结果展示已由 P02 Task 04 就绪） |
| 具体操作 | 每项 TDD（Fake 序列）+ 真机核销（设置后读回验证 round-trip） |
| 应调用 Skill | test-driven-development、subagent-driven-development（各设置项独立）、verification-before-completion |
| 测试 | 每设置项调用序列单测 |
| 验证命令 | 真机 round-trip 记录表 |
| 完成标准/回滚 | 逐项核销或明确"SDK 不支持"结论记录 |

### Task 05：LLM Key 配置化（D-07 完整核销）

| 项 | 内容 |
|---|---|
| 编号/名称 | 05 / DeepSeek Key 走构建配置 |
| 目标 | Key 不再硬编码：`local.properties`/环境变量 → `BuildConfig`（不入库）；空 Key 时明确走离线模式并提示 |
| 前置条件 | Task 01（无需设备也可先行） |
| 涉及文件 | `app/build.gradle.kts`（buildConfigField）、`aerial/LLMConfig.kt`（读 BuildConfig）、`.gitignore` 核对 |
| 具体操作 | ① gradle 读取 `LLM_API_KEY` 属性注入 BuildConfig ② LLMConfig 引用 ③ 空值分支单测 |
| 应调用 Skill | subagent-driven-development、test-driven-development |
| 测试 | 配置注入与空值降级单测 |
| 验证命令 | 全量绿 + 真机/模拟器在线意图识别一次成功记录（需用户提供有效 Key） |
| 完成标准/回滚 | 源码 grep 无明文 Key；`git revert` |

### Task 06：真机验证清单执行与问题收敛

| 项 | 内容 |
|---|---|
| 编号/名称 | 06 / P03 清单全量核销 |
| 目标 | 起降/返航/悬停、禁飞区、拍照动作、任务进度、失控与低电行为（对空安全环境）逐项核销 |
| 前置条件 | Task 02-05 完成 |
| 涉及文件 | `docs/phases/checklists/phase-04-device-checklist.md`（核销记录） |
| 具体操作 | 每项按"预期结果/实测结果/证据（日志/录像引用）"记录；失败项回流 systematic-debugging |
| 应调用 Skill | verification-before-completion、systematic-debugging |
| 验证命令 | 清单文件核销率 100%（含明确接受项） |
| 完成标准 | 全核销 |

### Task 07（P04b 子阶段）：最小后端对接

| 项 | 内容 |
|---|---|
| 编号/名称 | 07 / 登录 + 任务 + 记录对接 |
| 目标 | 按 `docs/04-API接口与调用说明.md` §5 规划落地最小集：`/auth/login`、`/tasks` 列表与详情、`/records` 上报；App 侧登录页 + Token 存储 + Repository 远端数据源实现 |
| 前置条件 | **外部条件**：后端服务与接口文档由用户/组员提供（未提供则本任务挂起，App 侧仅完成数据源接口与 DTO 层预备） |
| 涉及文件 | 新建 `data/remote/ApiClient.kt`、`data/remote/AuthApi.kt`、`data/remote/TaskApi.kt` 等（**建议新建路径**，Retrofit 或沿用 OkHttp）；`asuka/data/InspectionRepository` 增加远端数据源分支；新建登录 Activity |
| 具体操作 | ① architectural spec（数据源三级：远端→本地→演示）② DTO 按 14 表映射 ③ TDD：JSON 解析与数据源切换 ④ 联调（后端可用时） |
| 应调用 Skill | brainstorming(architectural)、writing-plans、test-driven-development、systematic-debugging（联调） |
| 测试 | DTO 解析、Token 刷新、离线回退单测 |
| 验证命令 | 全量绿 + 联调记录（可用时） |
| 完成标准 | spec 批准 + 代码落地 + （条件满足时）联调核销 |
| 回滚/恢复 | 远端分支不启用时由演示模式开关兜底 |

### Task 08：阶段收尾

同前（全量验证 → 整分支审查 → 修复 → 真机验证清单与后端联调记录归档 → 停止等待指令）。

## D. 阶段完成标准

1. 真机完成 ≥3 次完整巡检任务（上传→起飞→巡拍→返航），记录可追溯；
2. 真机遥测对照核销（D-04）、安全设置 round-trip 核销（D-05）、D-01/D-07 核销；
3. 审计矩阵 D-01/D-04/D-05/D-07 全部"已修复[已验证]"；
4. P04b：spec 与 App 侧代码完成；联调核销视外部条件（未满足时明确记录为阻塞移交）；
5. 全量构建/测试绿；整分支审查 Critical/Important 清零。

## E. 阻塞与升级处理

| 阻塞情形 | 处理 |
|---|---|
| **设备/Key 不到位（R1）** | 真机任务（02/03/04/06）挂起；仅推进 Task 05/07 与 P04b 预备；向用户升级资源请求；不阻塞 P05 中不依赖真机的项 |
| 后端未提供（R2） | Task 07 仅完成 spec+DTO+接口层；联调项挂起移交 |
| 真机验证中出现固件级行为与预期不符 | 记录证据升级用户决策（不猜测固件行为）；必要时在 WPML/设置层规避 |
| 加固包反射签名差异导致网关失效 | systematic-debugging 分层定位；最坏方案 Ruling：网关由反射改为直接 import（运行时类可用前提下），验证 VerifyError 风险后切换 |
| 安全事件（任何失控/异常飞行迹象） | **立即停止全部真机任务**，人工接管（遥控器），事件记录归档，复盘后才可恢复 |

## F. 下一阶段交接

- 代码：真机验证通过的全链路、远端数据源（P04b）；
- 文档：真机验证清单（全核销）、联调记录、固件差异备注（含 Key 名实测表）、审计矩阵终态更新；
- 测试：Fake 层回归套件 + 真机记录表；
- 技术决策：SDK 不支持设置项的最终清单、LLM Key 分发方式、后端接口基线版本；
- 未决事项：真实 AI 识别服务（backlog）、支付（backlog）、离线地图包（backlog）——移交 P05 交付说明的"限制与后续"章节。
