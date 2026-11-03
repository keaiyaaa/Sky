# 本地模拟飞行引擎

## 作用

`SimulatedFlightEngine` 是无 Android 控件和无 SDK 指令调用的推进器，用于航线模拟和避障验证。调用方按固定的模拟时间节拍串行调用 `tick()`，并负责调度和持久化；引擎本身不写数据库。

## 参数和回调

| 构造参数 | 类型与默认值 | 含义 |
|---|---|---|
| `waypoints` | `List<NavPoint>` | 有序航点；至少两个 |
| `monitor` | `ObstacleAvoidanceMonitor` | 障碍决策器 |
| `speedMetersPerSecond` | `Double = 18.0` | 每一模拟秒移动距离 |
| `bypassTravelDistanceMeters` | `Double = 70.0` | 绕行距离 |
| `arrivalThresholdMeters` | `Double = 15.0` | 到点容差；精确航线执行使用 `0.05` |
| `telemetryListener` | `(AircraftTelemetry) -> Unit` | 模拟位置和高度回调，不是真机遥测 |
| `statusListener` | `(String) -> Unit` | 避障及生命周期状态提示 |

无 UI emits，以上两个函数参数是对外通知接口。

## 方法和最小示例

`configure(action, obstacles)` 在启动前配置 `brake` 或 `bypass` 及障碍列表；`start()` 从首点启动，第二点为首个移动目标；`tick()` 推进一步；`stop()` 停止且不声明成功。`isRunning`、`isFinished` 和 `targetIndex` 是只读运行状态。

```kotlin
val points = listOf(NavPoint(31.0, 121.0, 30f), NavPoint(31.0001, 121.0001, 30f))
val positions = mutableListOf<AircraftTelemetry>()
val engine = SimulatedFlightEngine(
    waypoints = points,
    monitor = ObstacleAvoidanceMonitor(),
    arrivalThresholdMeters = 0.05,
    telemetryListener = { positions.add(it) },
    statusListener = { }
)
engine.configure("brake", emptyList())
engine.start()
while (engine.isRunning) engine.tick()
check(engine.isFinished)
```

## 设计约束

首点为初始位置，不朝自身前进一步。剩余距离小于步长时直接到达目标并更新目标高度，避免短航段和重复坐标发生振荡。自动巡检使用精确到点容差，只有到达全部快照航点才提交完成。避障回调和原有刹车、绕行行为继续保留。
