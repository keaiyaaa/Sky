# 本地巡检执行器

## 作用与接入

`LocalMissionRuntime` 提供进程级本地模拟任务，复用现有飞行引擎，以真实 SQLite 事务保存状态。它不调用 DJI 飞行或相机 API，不产生真实媒体记录。仅使用 `get(context)` 获取实例，不由页面自行构造。

```kotlin
val runtime = LocalMissionRuntime.get(applicationContext)
lifecycleScope.launch {
    runtime.state.collect { run ->
        statusView.text = run?.state?.label ?: "暂无模拟任务"
    }
}
lifecycleScope.launch {
    // routeId 必须来自 RouteRepository 中已保存的航线。
    runtime.start(routeId, MissionScenario.NORMAL)
}
```

`context: Context` 转换为应用上下文，不持有页面。无 UI props 或 Activity emits。

## 方法

| 方法 | 入参 | 返回值和约束 |
|---|---|---|
| `start()` | `routeId: String`、`scenario: MissionScenario` | `suspend Unit`；重新查询航线、复查安全并保存快照；已有活动任务则失败 |
| `togglePause()` | 无 | `suspend Unit`；只允许执行与暂停间转换 |
| `cancel()` | 无 | `suspend Unit`；取消结果落库后停止引擎；终态不允许重复取消 |
| `history()` | `offset: Int = 0` | `suspend List<MissionRun>`；每页 50 条倒序历史 |
| `events()` | `runId: String` | `suspend List<MissionRunEvent>`；运行的完整事件时间线 |

## 订阅

`state: StateFlow<MissionRun?>` 发布已成功提交数据库的任务。`failure: StateFlow<String?>` 报告执行记录无法保存的存储故障；此时不会用内存中的“成功”或“失败”覆盖数据库事实。调用页面应同时显示该故障，并允许用户处理存储问题后取消任务。

## 场景与状态

`NORMAL` 正常推进；`UPLOAD_FAILURE` 在模拟上传阶段以失败结束；`DISCONNECT` 在模拟执行开始后注入断连失败。用户必须明确选择本地模拟，不能从真机失败自动降级执行。

状态、暂停、取消与推进在同一互斥锁下串行操作。数据库额外提供唯一活动任务索引、航线编辑锁和修订号校验。每次执行读取不可变航点快照。页面关闭不会重新创建引擎；进程重启只将未完成任务标记为中断，不自动重飞。

完整存储与跨队员查询约定见 [模块接口契约](../../../../../../../../../docs/contracts/fzp-route-mission-contracts.md)。
