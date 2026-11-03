# 冯梓鹏负责模块接口与完成边界

适用范围为分工表中的 F07、F08、F09、F11 无无人机阶段。业务入口是首页连接的 `kami.RoutePlanningActivity`。独立旧项目中的航线数据模型、MapLibre 标记复用、最近邻优化和 SQLite 事务设计已与主仓库现有实现对照复用；保持团队包名和首页入口。

## 航线与安全规则

`RouteRepository` 保持原调用接口。保存会同时检查数量、坐标、高度、航点标识、所属航线和示例安全区域；正在执行的航线不可修改或删除。`SqliteRouteDao` 使用抛错插入和事务，任何一条航点写入失败都会回滚整条航线，不使用 REPLACE 静默覆盖其他航点。

`RouteSafetyValidator.requireSafe(waypoints, forExecution)` 检查所有航点和相邻航段。`forExecution=true` 额外要求至少 2 点。示例区域是本地教学规则，不是正式空域授权；正式区域数据缺失时不允许真机执行。添加、拖拽、保存、优化、文件生成和模拟任务创建都经过规则检查。

`LocalNearestNeighborRouteOptimizer` 固定首个航点，保留每个航点标识和动作，按开放航线的相邻航段总距离比较结果。最近邻候选更长时保留原顺序。没有在线 AI 优化服务。

## 本地巡检执行

```kotlin
val runtime = LocalMissionRuntime.get(context)
// 从已保存的航线读取并固定快照，不直接执行页面的临时列表。
runtime.start(routeId, MissionScenario.NORMAL)
// 暂停和继续共用状态受限入口。
runtime.togglePause()
runtime.cancel()
```

上述接口均为 suspend 函数；页面通过协程调用。`state: StateFlow<MissionRun?>` 中的结果只在数据库提交成功后发布。存储故障由 `failure: StateFlow<String?>` 单独报告，不发布未落库终态。`MissionScenario` 包含正常、上传失败和执行中断连三类确定性场景，全部使用本地执行器，不接触 DJI 指令 API。

状态流转为 `PREPARING → UPLOADING → RUNNING → COMPLETED`。`RUNNING ↔ PAUSED` 支持暂停继续。任意活动状态可进入 `CANCELLED`、`FAILED` 或 `INTERRUPTED` 终态。终态不能再次变更；版本号校验拒绝取消后的迟到结果；完成要求到达全部航点。数据库唯一索引保证同时最多一个活动任务。

应用进程内由单一执行器继续推进，不依赖页面存活。进程结束后，下次进入将遗留活动任务标记为 `INTERRUPTED`，保留历史并释放航线锁，不会自动重放任务。

## 正式存储结构

`route_planning.db` 从版本 1 增量迁移到版本 2，保留 `routes`、`waypoints` 的既有数据和结构，新增以下表。

| 表 | 内容 | 一致性约束 |
|---|---|---|
| `mission_runs` | 运行标识、原航线标识和名称、任务名称、模式、场景、状态、航点总数、已到点数、位置、高度、原因、起止时间和版本号 | 模式固定 `LOCAL_SIMULATION`；唯一活动任务；合法状态和进度约束 |
| `mission_run_waypoints` | 启动时的有序航点快照和原航点标识、坐标、高度、动作 | 与运行记录外键关联；不随原航线修改或删除 |
| `mission_run_events` | 状态转换、到点结果、业务原因及发生时间 | 与状态和进度在同一事务内提交；运行内序号唯一 |

`MissionRunRepository.history(limit, offset)` 查询分页历史；`get(runId)` 查询单条结果；`waypoints(runId)` 获取启动快照；`events(runId)` 获取时间线。F14/F21 可直接读取这些正式记录，必须保留 `LOCAL_SIMULATION` 来源，不复制为真实飞行记录，也不自动生成真实拍照或 AI 结论。

## 本轮完成范围与外部条件

本轮实现本地编辑保存恢复、本地优化及撤销、示例安全校验、本地任务正常/暂停/取消/失败/中断流程及持久化历史。当前不依赖业务后端，不新增假 HTTP API，不将内存状态当作执行记录。

正式区域数据、DJI 注册及遥测、实际 WPML 动作执行、起降返航、真实巡拍和任务中心的完整媒体报告仍依赖 F02/F03/F05/F10/F12/F14 及设备联调。真机入口保持禁止执行，不能据本轮模拟通过声明可实飞。F10 导出器沿用现有实现，其动作完整性由负责人继续验收。
