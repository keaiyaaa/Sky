# Phase 02 执行计划：架构整理与基础模块

## A. 阶段基本信息

| 项 | 内容 |
|---|---|
| 阶段名称 | 架构整理与基础模块（Phase 02） |
| 目标 | 统一 DJI 注册状态源（A1/D-01 的代码前提）、建立统一遥测总线（A3/D-04 的 App 侧前提）、消除设置"同步成功"假象（D-08）、Mock 演示模式开关（D-12）、数据库升级策略（D-14）、drone 包健壮性（D-13）、避障半径缺陷（D-17） |
| 对应总体计划章节 | `docs/02-总体开发计划.md` §5 Phase 02 行、§6 里程碑 M2 |
| 依赖的前置阶段 | Phase 01 合并（Task 02-07 的修复与隔离成果） |
| 当前输入条件 | Phase 01 交接清单；审计报告 §11 问题矩阵；架构建议 A1/A3/A4（`docs/03-技术架构.md` §5） |
| 阶段边界 | 不实现真机遥测（Phase 04 实测）、不做 KMZ 真机链路（Phase 04）、不引入 DI 框架、不改 WPML 模板结构（Phase 03） |

## B. Skill 调用声明

（Skill 均位于 `E:\WorkBuddy_International\skill\`，调用方式同 Phase 01 §B：会话内读取 SKILL.md 并遵循其流程。）

| Skill | 完整路径 | 调用时机 | 目的 | 输入 | 预期输出 | 验证方法 |
|---|---|---|---|---|---|---|
| using-superpowers | `using-superpowers\SKILL.md` | 会话开始 | Skill 纪律入口 | — | 使用计划声明 | 对话声明 |
| brainstorming（**architectural 路径**） | `brainstorming\SKILL.md` | Task 02（SdkRegistry）与 Task 03（TelemetryBus）动代码前 | 本阶段含接口级改动，需书面 spec：`docs/superpowers/specs/2026-XX-XX-sky-registry-telemetry-design.md`（**建议新建路径**），用户审阅批准后才进入 writing-plans | 审计证据 + 接口草案 | 设计 spec 文件 + 用户批准 | spec 文件存在且用户明确批准 |
| writing-plans | `writing-plans\SKILL.md` | spec 批准后 | 为 Task 02/03 生成任务级实施计划（保存 `docs/superpowers/plans/`） | spec | 实施计划文档 | 计划头含 Global Constraints/Review Focus |
| subagent-driven-development | `subagent-driven-development\SKILL.md` | Task 04-07（相互独立） | 每任务派实现+审查双子代理 | 任务简报 | 分任务审查记录 | 每任务有 reviewer 结论 |
| executing-plans | `executing-plans\SKILL.md` | Task 02/03（依赖 spec 的串行任务） | 内联执行 | 计划+spec | 台账 | task-done 记录 |
| test-driven-development | `test-driven-development\SKILL.md` | 全部任务 | 先测试后实现 | — | 红绿记录 | 输出留证 |
| systematic-debugging | `systematic-debugging\SKILL.md` | 遇缺陷 | 根因优先 | 失败输出 | 根因报告 | Phase 1 证据 |
| verification-before-completion | `verification-before-completion\SKILL.md` | 每任务收尾 | 证据先于声明 | — | 命令+输出 | 台账 |
| requesting-code-review | `requesting-code-review\SKILL.md` | 阶段末 | 整分支审查 | SHA 范围 | findings | 修复闭环 |

## C. 原子任务拆分

### Task 01：阶段基线复验

| 项 | 内容 |
|---|---|
| 编号/名称 | 01 / P02 分支建立与基线复验 |
| 目标/前置条件 | 沿用 Phase 01 流程；`feature/phase-02` 基于合并后的 master |
| 涉及文件 | 无业务文件 |
| 具体操作 | worktree/分支 + `assembleDebug + testDebugUnitTest` + 手工冒烟（首页/规划/任务中心可进） |
| 应调用 Skill | using-git-worktrees、verification-before-completion |
| 测试/验证命令 | 全量绿；冒烟通过 |
| 预期结果/完成标准/回滚 | 同 Phase 01 Task 01 模式 |

### Task 02：统一 SDK 注册状态源（A1，打通 D-01 的代码前提）

| 项 | 内容 |
|---|---|
| 编号/名称 | 02 / 建立 SdkRegistry，kami 网关读真实注册状态 |
| 目标 | `DjiV5ReflectiveMissionGateway` 的注册检查从"自建 DjiSdkManager（恒 false）"改为"DroneManager 真实状态"（经接口，避免 kami→drone 直接耦合加深） |
| 前置条件 | Task 01；architectural spec 已获用户批准（brainstorming 路径：问题澄清 → 2-3 方案对比 → 书面 spec → 用户审阅 → writing-plans） |
| 涉及文件 | 新建 `drone/SdkRegistry.kt`（接口，**建议新建路径**）；修改 `drone/DroneManager.kt`（实现该接口）；修改 `kami/mission/DjiV5ReflectiveMissionGateway.kt:27-29`（注入 SdkRegistry）；`kami/RoutePlanningActivity.kt`（装配点传参）；`kami/mission/DjiSdkManager.kt` 摘除使用（类文件加 @Deprecated，物理删除待清单复审） |
| 具体操作 | ① spec：两方案对比（A. SdkRegistry 接口注入；B. kami 直接 import DroneManager），推荐 A ② 接口 TDD：`isRegistered(): Boolean` + 状态快照；用假实现测网关分支（注册成功→继续反射路径；失败→原报错文案） ③ 装配 ④ 反射路径本次不实测真机（Phase 04），仅验证检查逻辑正确 |
| 应调用 Skill | brainstorming(architectural)、writing-plans、executing-plans、test-driven-development |
| 测试 | 网关注册检查分支单测（Fake SdkRegistry）；DroneManager 实现的映射测试 |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；模拟器冒烟：OFFLINE 下点一键起飞 → 报"DJI SDK 尚未注册"（与真实状态一致） |
| 预期结果 | 注册检查与真实状态一致，D-01 的"恒 false"根因消除 |
| 完成标准 | spec 批准记录 + 红绿 + 冒烟 + 提交 |
| 回滚/恢复 | `git revert`（回到自建状态机，链路维持"必然失败"现状——无行为恶化） |

### Task 03：统一遥测总线（A3，App 侧）

| 项 | 内容 |
|---|---|
| 编号/名称 | 03 / 建立 TelemetryBus（StateFlow）替换双轨遥测 |
| 目标 | 首页 OSD、电量/信号、kami 进度共享一条遥测流；真机数据源留 TODO 挂点（Phase 04 接 KeyManager.listen） |
| 前置条件 | Task 01；spec 与 Task 02 同文档或单独 spec（architectural） |
| 涉及文件 | 新建 `drone/TelemetryBus.kt`、`drone/TelemetryModels.kt`（**建议新建路径**）；修改 `drone/DroneManager.kt`（模拟循环改发 bus）、`HomeActivity.kt:122-157`（订阅）、`kami/RoutePlanningActivity.kt:168-170,626-649`（订阅 gateway/模拟引擎 → bus） |
| 具体操作 | ① 定义 `Telemetry(alt,dist,speed,battery,signal,lat,lng,heading,ts)` ② bus 用 `MutableStateFlow` + `shareIn` ③ HomeActivity 订阅替换随机游走协程与 onSimulatorTelemetry 双轨 ④ 泄漏修复顺带完成：DroneManager 遥测回调改走 bus（Task 06 关联 D-13 泄漏窗口） |
| 应调用 Skill | brainstorming(architectural)、writing-plans、executing-plans、test-driven-development |
| 测试 | bus 合流/去重/线程切换单测（Turbine 或手写 collector） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；模拟器冒烟：开启模拟器后首页 OSD 数值变化、规划页 SIM 演示进度正常 |
| 预期结果 | 单一遥测源；OSD/进度数据一致 |
| 完成标准 | spec 批准 + 红绿 + 冒烟 + 提交 |
| 回滚/恢复 | `git revert`（双轨回退，无数据风险） |

### Task 04：设置同步去假象（D-08/A4）

| 项 | 内容 |
|---|---|
| 编号/名称 | 04 / 设置页如实反馈下发结果 |
| 目标 | 8 个 stub 的同步结果如实提示"该设置项尚未接入无人机，仅保存本机"；`setObstacleAction` 真实结果保留 |
| 前置条件 | Task 01（可与其他 Task 并行，适合 subagent 模式） |
| 涉及文件 | `ui/settings/FlightSettingsActivity.kt:98-144`、`ui/settings/VideoSettingsActivity.kt:58-97` |
| 具体操作 | ① 汇总每次保存的真实回调结果列表 ② 全部成功 → "已同步到无人机"；存在失败 → 明确列出哪些项仅本地生效 ③ 移除无条件成功 Toast |
| 应调用 Skill | subagent-driven-development（实现+审查）、test-driven-development（结果聚合纯函数单测） |
| 测试 | `SyncResultAggregator` 单测（**建议新建** `app/src/test/java/com/example/msdksample/ui/settings/`） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；冒烟：飞控设置保存后 Toast 含"尚未接入"字样 |
| 完成标准/回滚 | 红绿+冒烟；`git revert` |

### Task 05：演示数据模式开关（D-12）

| 项 | 内容 |
|---|---|
| 编号/名称 | 05 / Mock 数据不再静默固化 |
| 目标 | inspection_db 首启种子数据受"演示模式"控制；演示态在 UI 有标识 |
| 前置条件 | Task 01 |
| 涉及文件 | `asuka/database/DataSource.kt:22-29`、`asuka/data/InspectionRepository.kt:26-37`、`asuka/MainActivity.kt`（标识角标）、`utils/PreferenceManager.kt`（新 key `demo_mode`，Bool 默认 **true** 保持现有演示体验） |
| 具体操作 | ① `initializeMockDataIfNeeded` 增加 demo_mode 门 ② 关闭演示模式且库空 → 列表显示空态文案 ③ MainActivity 顶部演示态横幅 |
| 应调用 Skill | subagent-driven-development、test-driven-development（门逻辑单测） |
| 测试 | 空库+demo off → 不插入；demo on → 插入（内存 Room 或接口桩） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；冒烟两种模式切换 |
| 完成标准/回滚 | 同模式；`git revert` |

### Task 06：drone 包健壮性修复（D-13）

| 项 | 内容 |
|---|---|
| 编号/名称 | 06 / 轮询清理、遥测泄漏窗口、模拟电量语义 |
| 目标 | ① `handleProductDisconnected` 移除挂起轮询与回调 ② HomeActivity onDestroy 调 `stopTelemetry` ③ 模拟电量下限与"低电量"语义解耦（保持演示钳制，但注释+常量收口，Phase 03 真实低电监听后移除） |
| 前置条件 | Task 01、Task 03（遥测回调改 bus 后 ② 自动消解，保留轮询修复为独立提交） |
| 涉及文件 | `drone/DroneManager.kt:569-576,811-845`；`HomeActivity.kt:350-357` |
| 具体操作 | ① 轮询 runnable 与标志位原子清理 + 幂等测试 ② stopTelemetry 时机修正 |
| 应调用 Skill | subagent-driven-development、test-driven-development、systematic-debugging（轮询双跑复现） |
| 测试 | 轮询幂等单测（Fake Handler 或注入 Runner） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；冒烟：连接模拟器→断开→再连接，无重复轮询日志 |
| 完成标准/回滚 | 同模式；`git revert` |

### Task 07：数据安全与模拟逻辑修复（D-14 + D-17）

| 项 | 内容 |
|---|---|
| 编号/名称 | 07 / 数据库升级策略 + 避障半径参与判定 |
| 目标 | ① kami SQLite onUpgrade 由 DROP 重建改为版本化迁移（v1→v2 预留空迁移框架）；② asuka 关闭 destructive migration 并补 Migration 框架（版本暂不变，仅关危险开关）③ `ObstacleAvoidanceMonitor` 威胁判定纳入 `radiusMeters`（圆心距-半径 ≤ lookAhead 才算威胁）并修正测试注释 |
| 前置条件 | Task 01 |
| 涉及文件 | `kami/data/RouteDatabaseHelper.kt:53-57`、`asuka/database/AppDatabase.kt:35`、`drone/ObstacleAvoidanceMonitor.kt:143-161`、`drone/ObstacleAvoidanceMonitorTest.kt` |
| 具体操作 | ① 各加迁移注释与框架（空 Migration 列表）② 半径判定 TDD：先写"半径外不刹车"失败测试再改判定 ③ Engine 不可达分支加注释标记（逻辑修正延后，避免扩大面） |
| 应调用 Skill | subagent-driven-development、test-driven-development |
| 测试 | 半径边界单测（0m/15m/50m 半径 × 距离）；迁移框架冒烟（版本 1→1 无损） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；冒烟：旧库数据（Mock 固化）升级后仍在 |
| 完成标准/回滚 | 同模式；`git revert` |

### Task 08：死代码物理删除（Phase 01 移交项）

| 项 | 内容 |
|---|---|
| 编号/名称 | 08 / 删除已隔离的死代码 |
| 目标 | 物理删除 Phase 01 清单中用户批准的文件与 6 个死布局、无引用 DAO 方法、`AiRouteOptimizationClient`/`stopMission` 等；`wpmzsdk` 依赖移除需先确认 WPML 导出不迁移（当前零引用，确认后移除） |
| 前置条件 | Phase 01 Task 07 完成且运行两周无回退（或用户直接批准立即删除） |
| 涉及文件 | Phase 01 §C Task 07 清单 + `app/build.gradle.kts:110` + DAO 未用方法 |
| 具体操作 | 逐文件删除前最后一次 grep 引用确认 → 删除 → 全量构建测试 |
| 应调用 Skill | verification-before-completion、requesting-code-review（删除 diff 单独审） |
| 测试/验证命令 | 全量绿 + APK 体积对比记录 |
| 完成标准/回滚 | 删除清单与批准一致；`git revert` 恢复文件 |

### Task 09：阶段收尾

同 Phase 01 Task 08 模式：全量验证 → 整分支审查（BASE=Phase 01 合并点）→ Critical/Important 修复 → 台账与 Rulings 汇总 → 停止等待指令。

## D. 阶段完成标准

1. 注册状态源统一且 kami 网关消费真实状态（D-01 根因消除代码侧完成）；
2. 单一遥测总线投用，双轨遥测与 TODO 空分支被替换为明确挂点；
3. 设置页无无条件成功提示（D-08 核销）；Mock 受演示模式控制（D-12 核销）；
4. 两库升级策略为非破坏式框架（D-14 核销）；D-13/D-17 修复并有测试；
5. 死代码物理删除完成（或用户批准延期并记录）；
6. spec 文件、实施计划、审查报告齐备；全量构建/测试绿。

## E. 阻塞与升级处理

| 阻塞情形 | 处理 |
|---|---|
| architectural spec 未获用户批准 | Task 02/03 挂起，先并行推进 Task 04-07（相互独立），Ruling 记录顺序调整 |
| StateFlow 改造引发既有页面行为回归 | `systematic-debugging` 根因排查；影响面大时回退该页改造（bus 保留，页面暂用旧回调适配器） |
| Room 关闭 destructive migration 后旧设备崩溃 | 降级方案：保留 destructive 但加版本日志与数据导出提示，Ruling 记录 |
| wpmzsdk 移除导致编译失败 | 说明存在隐性引用（未审计到的），恢复依赖并立案 |

## F. 下一阶段交接

- 代码：`feature/phase-02` 合并后基线；`SdkRegistry`/`TelemetryBus` 两个新接口（供 Phase 03 FlightControlApi 与 Phase 04 真机接入消费）；
- 文档：新增 spec 与计划文件路径清单；审计矩阵 D-08/D-12/D-13/D-14/D-17 状态更新；
- 测试：常驻回归扩展（遥测、注册分支、同步聚合、半径判定）；
- 技术决策：TelemetryBus 字段集与更新频率；注册失败文案与重试策略；
- 未决事项：真机遥测挂点的 KeyManager.listen 实现属 Phase 04；kami Engine 不可达分支的逻辑修正随 Phase 03 模拟链路完善一并处理。
