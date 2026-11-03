# Phase 03 执行计划：核心功能完善

## A. 阶段基本信息

| 项 | 内容 |
|---|---|
| 阶段名称 | 核心功能完善（Phase 03） |
| 目标 | 建立抽象飞控指令层 `FlightControlApi`（含模拟实现，DJI 实现待 P04 真机验证）；WPML 导出拍照动作（D-03）；飞行控制页实装；禁飞区三时点检查（D-11）；地图底图修正（D-16）；起飞前检查器与低电量监听框架；任务中心与航线数据打通 |
| 对应总体计划章节 | `docs/02-总体开发计划.md` §5 Phase 03 行、§6 里程碑 M3 |
| 依赖的前置阶段 | Phase 02 合并（需要 SdkRegistry、TelemetryBus） |
| 当前输入条件 | P02 交接清单；架构建议 A2（`docs/03-技术架构.md` §5）；API 规划 §5.6 |
| 阶段边界 | DJI 真机指令实现可以编码但**真机验证一律延至 Phase 04**；不接后端；模拟/真实实现必须可切换 |

## B. Skill 调用声明

（Skill 位于 `E:\WorkBuddy_International\skill\`，调用方式：读取 SKILL.md 并遵循流程，同前。）

| Skill | 调用时机 | 目的 | 输入 | 预期输出 | 验证方法 |
|---|---|---|---|---|---|
| using-superpowers | 会话开始 | 纪律入口 | — | 声明 | 对话记录 |
| brainstorming（architectural） | Task 02（FlightControlApi）前 | 飞控指令接口属安全相关架构改动，必须书面 spec + 用户批准（含安全审查：确认模拟实现无任何真实指令出口） | P02 接口、DJI Key 清单 | spec（含安全边界一节） | spec 文件 + 批准记录 |
| writing-plans / executing-plans | spec 批准后 | 计划与内联执行 | spec | 计划+台账 | task-done 记录 |
| subagent-driven-development | Task 04/05/06（独立） | 并行推进 | 任务简报 | 审查记录 | reviewer 结论 |
| test-driven-development | 全部任务 | 铁律 | — | 红绿记录 | 输出留证 |
| systematic-debugging | 异常时 | 根因 | 失败输出 | 根因报告 | 证据 |
| security-audit（辅助） | Task 02 spec 安全节、Task 05 禁飞区 | 安全视角复查（辅助 Skill，不替代 Superpowers 主流程） | 相关代码 | 安全备注 | 记录在 spec/review |
| verification-before-completion | 收尾 | 证据 | — | 命令+输出 | 台账 |
| requesting-code-review | 阶段末 | 整分支审查 | SHA | findings | 闭环 |

## C. 原子任务拆分

### Task 01：阶段基线复验

同 P02 Task 01 模式（`feature/phase-03`、全量绿、冒烟）。

### Task 02：FlightControlApi 抽象 + 双实现（A2/D-05 指令层）

| 项 | 内容 |
|---|---|
| 编号/名称 | 02 / 飞控指令接口与模拟实现 |
| 目标 | 定义 `FlightControlApi: takeoff(alt)/land()/goHome()/hover(duration)/startVirtualStick()/stopVirtualStick()` + `PreflightResult`；提供 `SimFlightControlApi`（纯模拟，经 TelemetryBus 驱动状态机）与 `DjiFlightControlApi`（KeyManager/FlightControllerKey 编码完成，运行验证延后） |
| 前置条件 | Task 01；**spec 安全节用户批准**（含：模拟实现不得 import 任何 `dji.*`；DJI 实现全部调用前必须 `isRegistered` + Preflight 通过；Debug 守卫复用 `DroneManager` 规则） |
| 涉及文件 | 新建 `drone/flight/FlightControlApi.kt`、`drone/flight/SimFlightControlApi.kt`、`drone/flight/DjiFlightControlApi.kt`、`drone/flight/PreflightChecker.kt`（**均建议新建路径**） |
| 具体操作 | ① spec：指令集、前置检查项（电量阈值/GPS 卫星数/返航高度范围/Home 已记录/禁飞区）、失败码枚举 ② PreflightChecker 纯逻辑 TDD ③ Sim 实现接入 TelemetryBus 状态机（待机→起飞→巡航→返航→降落）④ DJI 实现按 MSDK V5 Key 编码（`KeyStartTakeoff/KeyStartAutoLanding/KeyStartGoHome/虚拟摇杆`），单测用 Fake KeyManager 验证调用序列 |
| 应调用 Skill | brainstorming(architectural)+security-audit 辅助、writing-plans、executing-plans、test-driven-development |
| 测试 | Preflight 全分支、Sim 状态机迁移、DJI 调用序列（Fake） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；冒烟：模拟模式下飞控页（Task 04 页面）可完整走"起飞→悬停→返航→降落" |
| 完成标准/回滚 | spec 批准+红绿+冒烟；`git revert`（无任何真实指令出口，回滚安全） |

### Task 03：WPML 拍照动作导出（D-03）

| 项 | 内容 |
|---|---|
| 编号/名称 | 03 / waypoint action 写入 WPML actionGroup |
| 目标 | `waypoints.action=="拍照"` 的航点在 waylines.wpml 中生成 `wpml:actionGroup`（actionActuatorFunc=takePhoto），template.kml 同步 |
| 前置条件 | Task 01 |
| 涉及文件 | `kami/mission/WpmlRouteExporter.kt`（全文追加 action 生成段）；新建测试 `app/src/test/java/com/example/msdksample/kami/WpmlRouteExporterTest.kt`（**建议新建路径**）；评估启用 `wpmzsdk` 做结构校验（Task 08 P02 的去留决策联动：若本任务用它做单测校验则依赖保留） |
| 具体操作 | ① TDD：含拍照航点的 RoutePlan → 导出 KMZ → 解包 XML 断言 actionGroup 存在且 index 对齐 ② 不含拍照航点 → 无 actionGroup ③ 与现有固定模板（速度 8/goHome/失控返航）无回归 |
| 应调用 Skill | test-driven-development、systematic-debugging（XML 断言失败时） |
| 测试 | 导出器 3 组单测（含/不含拍照/混合） |
| 验证命令 | `./gradlew :app:testDebugUnitTest --tests "com.example.msdksample.kami.WpmlRouteExporterTest"`；冒烟：生成的 KMZ 上模拟器不可验，结构正确性以单测+（可选）DJI 官方 sample XML 对照 |
| 完成标准/回滚 | 红绿+提交；`git revert` |

### Task 04：飞行控制页实装

| 项 | 内容 |
|---|---|
| 编号/名称 | 04 / ui.flight.FlightControlActivity 从占位变可用 |
| 目标 | 页面接入 FlightControlApi + TelemetryBus：遥测显示、起飞/悬停/返航/降落按钮（带确认弹窗与 Preflight 结果展示）、模拟/真实模式徽标 |
| 前置条件 | Task 02 完成（接口就绪）；恢复 Manifest 注册（P01 Task 07 摘除项按用户批准恢复）；入口从 HomeActivity 新增或替换"开始飞行"跳转（**用户决策点**：当前跳航拍页，建议保留航拍页 + 新增"飞行控制"二级入口） |
| 涉及文件 | `ui/flight/FlightControlActivity.kt`（重写）、`res/layout/activity_flight_control.xml`（重写）、`HomeActivity.kt`（入口）、`AndroidManifest.xml` |
| 具体操作 | ① 页面状态机绑定 Sim/Dji 实现选择（getRuntimeMode）② 指令按钮全部二次确认 + Preflight 结果回显 ③ 遥测面板订阅 bus |
| 应调用 Skill | subagent-driven-development、test-driven-development（页面 ViewModel 纯逻辑） |
| 测试 | 指令确认流程纯逻辑单测 |
| 验证命令 | 全量 + 冒烟（模拟模式全流程演示） |
| 完成标准/回滚 | 红绿+冒烟+入口可见；`git revert` |

### Task 05：禁飞区三时点检查（D-11）

| 项 | 内容 |
|---|---|
| 编号/名称 | 05 / 添加、拖拽、起飞三处禁飞区复查 |
| 目标 | 拖拽进入 NO_FLY 拒绝落点并回弹；起飞前 Preflight 含全区检查；限高区（RESTRICTED）起飞前警告确认 |
| 前置条件 | Task 01、02（PreflightChecker） |
| 涉及文件 | `kami/RoutePlanningActivity.kt:380-400`（拖拽复查）、`drone/flight/PreflightChecker.kt`（接入 FlightZone）、`kami/model/FlightZone.kt`（区数据外置为可配置，硬编码北京区迁至常量对象并注释"演示数据"） |
| 具体操作 | ① TDD：ZoneChecker 纯函数（点→区判定含半径边界）② 拖拽落点回调接入 ③ Preflight 集成 ④ 区数据外置 |
| 应调用 Skill | subagent-driven-development、test-driven-development、security-audit（辅助） |
| 测试 | ZoneChecker 边界单测（区内/边缘/限高/多区重叠） |
| 验证命令 | 全量 + 冒烟（拖拽进禁飞区被拒） |
| 完成标准/回滚 | 同模式 |

### Task 06：地图底图与航点体验修正（D-16）

| 项 | 内容 |
|---|---|
| 编号/名称 | 06 / 底图链路收敛 |
| 目标 | ① 高德 Key 拼入瓦片 URL 或移除无效 meta（**用户决策**：涉及对外 Key 政策）② 天地图死路移除或补 token 机制 ③ 兜底 demotiles 保留 ④ 单点删除航点（现仅能删最后一个） |
| 前置条件 | Task 01 |
| 涉及文件 | `kami/map/dji/DjiV5MapLibreRouteMapController.kt:416-538`、`kami/RoutePlanningActivity.kt`（长按删点）、`AndroidManifest.xml`（meta 处置） |
| 具体操作 | TDD 可行部分为 URL 构造纯函数；其余冒烟验证 |
| 应调用 Skill | subagent-driven-development、test-driven-development |
| 测试 | 底图 URL 构造单测（Key 拼接/无 Key 回退） |
| 验证命令 | 全量 + 冒烟（地图加载、删点） |
| 完成标准/回滚 | 同模式 |

### Task 07：任务中心与航线打通（数据关系雏形）

| 项 | 内容 |
|---|---|
| 编号/名称 | 07 / 任务关联航线（本地闭环） |
| 目标 | asuka 任务详情可显示关联航线名与航点数（数据来自 kami route_planning.db，只读桥接）；为 P04 后端 flight_routes/inspection_tasks 关系预留映射 |
| 前置条件 | Task 01 |
| 涉及文件 | 新建 `asuka/data/RouteInfoBridge.kt`（只读查询 kami 库，**建议新建路径**）；`asuka/MissionDetailActivity.kt`（展示行）；匹配策略：routeName 精确匹配（演示数据 routeName 与 kami 默认航线名的映射规则写入 Ruling） |
| 具体操作 | ① 只读桥接（不合并两库，避免 schema 大改）② TDD 桥接查询 ③ 详情页展示 |
| 应调用 Skill | subagent-driven-development、test-driven-development |
| 测试 | 桥接查询单测（匹配/不匹配/空库） |
| 验证命令 | 全量 + 冒烟 |
| 完成标准/回滚 | 同模式 |

### Task 08：阶段收尾

同 P01/P02 收尾模式（全量验证 → 整分支审查 → 修复 → 汇报停止）。

## D. 阶段完成标准

1. `FlightControlApi` + 模拟实现落地，模拟模式下"起飞→巡拍→返航→降落"全流程可演示；
2. 导出的 KMZ 含拍照 actionGroup（单测证明）；
3. 飞行控制页可用且所有指令有确认与 Preflight 回显；
4. 禁飞区三时点生效；底图链路明确（含用户对高德 Key 的决策记录）；
5. 任务详情显示关联航线信息；
6. 审计矩阵 D-03/D-11/D-16 状态更新；全量绿；审查 Critical/Important 清零；
7. 真机验证清单（P04 用）已生成：起降返航、遥测、任务上传、失控/低电行为。

## E. 阻塞与升级处理

| 阻塞情形 | 处理 |
|---|---|
| FlightControlApi spec 未批准 | Task 04 挂起；Task 03/05/06/07 并行推进 |
| DJI Key 常量缺失知识（起降/摇杆 Key 名） | 以 MSDK V5 5.18.0 provided jar 的 `FlightControllerKey` 实际定义为准（编译期可见），编码时核对；无法确认的 Key 在 spec 中列为待验证项，P04 实测 |
| 用户对入口调整（Task 04）无决策 | 默认保守方案：保留航拍页入口，飞控页暂从"关于→开发者入口"进入，记录 Ruling |
| 模拟状态机与遥测 bus 频率冲突（性能） | 降低 bus 发射频率（节流），Ruling 记录 |
| 真实 Key 行为无法在本阶段验证 | 全部标记 `[代码可见但未验证]`，进 P04 验证清单，不算完成度缺口 |

## F. 下一阶段交接

- 代码：`FlightControlApi`（Sim/Dji 双实现）、PreflightChecker、WPML actionGroup、飞控页、ZoneChecker——P04 真机联调的全部对象；
- 文档：spec（含安全边界）、真机验证清单（逐项预期结果）、审计矩阵更新；
- 测试：常驻回归 + Fake KeyManager 序列测试（P04 改真机后仍用于回归）；
- 技术决策：指令集与失败码、入口布局、底图方案、航线-任务匹配规则；
- 未决事项：DjiFlightControlApi 全部真机验证；低电量真实监听（依赖 P04 遥测 Key 接入）。
