# F02/F03 设备状态接口契约草案

**发给：** F02 设备连接与设备切换负责人、F03 飞行遥测负责人
**目的：** 让航拍、相机和后续飞控模块读取同一份设备模式、连接状态及遥测快照，并据此判断何时允许使用真实设备适配器。
**版本：** 0.1 草案，2026-11-03。
**状态：** 提案，待 F02/F03 负责人核对现有 `DroneManager` 行为并确认。本文不是已实现 API；未经确认不要求负责人照此改名或破坏现有调用方。

## 1. 现状和契约原则

当前 `DroneManager` 已提供 `SdkState`、`RuntimeMode`、`DroneDevice`、`ConnectionStateListener`、`getSdkState()`、`isRegistered()`、`isConnected()`、`getConnectedDevice()`、`isSimulatorEnabled()`、`getRuntimeMode()`、`getBatteryPercent()`、`getSignalStrength()`、`addStateListener()` 和 `removeStateListener()`。

现有实现里 `isConnected()` 在 DJI 模拟器激活时也返回 `true`；`getConnectedDevice()` 此时返回名为 Simulator 的设备。因此，调用方不能用 `isConnected()` 单独判断真实飞机已连接。监听器回调没有当前值重放保证；注册状态、设备连接、设备资料和模拟遥测分成多个回调。F03 的高度、距离、速度等其他遥测没有在这组状态接口中形成有时间戳的统一快照。

本契约要保证调用方能辨认 **真实设备、DJI SDK 模拟器、纯本地演示、离线** 四种来源，并能判断快照是否仍新鲜。真实飞行或相机操作必须显式要求 `DEVICE + READY + 遥测足够新鲜 + 能力已确认`；缺数据不能以模拟值或上一次缓存的数值代替。

## 2. 责任边界

| 责任方 | 提供内容 | 不应由对方推断的内容 |
|---|---|---|
| F02 | DJI SDK 初始化/注册、真实产品连接与断开、DJI 模拟器状态、设备身份及能力 | 遥测缺省值、飞行操作是否成功 |
| F03 | 遥测订阅和时间戳、字段有效性、字段来源、可用字段集合 | 是否真实连接；是否已注册；把模拟遥测伪装成真实设备遥测 |
| 消费者（F04/F05/F06/F11/F12 等） | 按指定模式和 freshness policy 检查快照，执行自己的业务校验 | 修改 SDK 全局状态、把无效字段补成零、把请求已受理当成物理动作完成 |

接口提供状态事实，不替代操作级安全检查。不同动作所需字段、合理范围、起飞场地、返航余量等规则由飞控设计和测试方案另行规定，本契约不臆造阈值。

## 3. 建议数据模型

下列名字供讨论，项目可以映射到现有枚举。`generation` 每次进入新的连接会话或模式切换递增，用于丢弃旧会话的迟到回调。

```kotlin
enum class DeviceMode { OFFLINE, DEVICE, DJI_SIMULATOR, LOCAL_DEMO }
enum class SdkRegistrationState {
    NOT_INITIALIZED, INITIALIZING, REGISTERING, REGISTERED, REGISTER_FAILED
}
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }
enum class Readiness { UNAVAILABLE, CONNECTING, READY, DEGRADED, ERROR }
enum class TelemetrySource { DEVICE, DJI_SIMULATOR, LOCAL_DEMO }

data class DeviceIdentity(
    val productId: Int,
    val productType: String?,
    val firmwareVersion: String?,
    val serialNumber: String?
)

data class TelemetryValue<T>(
    val value: T?,
    val source: TelemetrySource?,
    val observedAtEpochMs: Long?,
    val observedAtMonotonicMs: Long?,
    val valid: Boolean,
    val unavailableReason: String? = null
)

data class TelemetrySnapshot(
    val generation: Long,
    val receivedAtEpochMs: Long,
    val receivedAtMonotonicMs: Long,
    val latitudeDeg: TelemetryValue<Double>,
    val longitudeDeg: TelemetryValue<Double>,
    val altitudeMeters: TelemetryValue<Double>,
    val horizontalSpeedMetersPerSecond: TelemetryValue<Double>,
    val headingDegrees: TelemetryValue<Double>,
    val batteryPercent: TelemetryValue<Int>,
    val signalPercent: TelemetryValue<Int>,
    val flying: TelemetryValue<Boolean>
)

data class DeviceCapabilities(
    val generation: Long,
    val observedAtMonotonicMs: Long,
    val photo: Boolean?,
    val video: Boolean?,
    val takeoff: Boolean?,
    val landing: Boolean?,
    val returnHome: Boolean?,
    val hover: Boolean?
)

data class DeviceStateSnapshot(
    val generation: Long,
    val mode: DeviceMode,
    val sdkRegistration: SdkRegistrationState,
    val linkState: LinkState,
    val readiness: Readiness,
    val device: DeviceIdentity?,
    val telemetry: TelemetrySnapshot?,
    val capabilities: DeviceCapabilities?,
    val changedAtEpochMs: Long,
    val changedAtMonotonicMs: Long,
    val errorCode: String? = null,
    val userMessage: String? = null
)
```

If adding each field is not currently possible, the first compatible slice should still provide `mode`, registration, real link state, generation, update times, identity, readiness, and a map/set of valid telemetry fields with source and timestamps. Do not publish a property as valid until its source has been confirmed.

### 字段语义

- `OFFLINE`：没有真实设备或 DJI 模拟器可用。纯本地页面可工作，设备命令应被拒绝。
- `DEVICE`：真实产品连接；SDK 已注册；身份与链路属于当前 generation。只有该模式可以交给真实 DJI 设备适配器。
- `DJI_SIMULATOR`：DJI SDK 模拟器状态，`isConnected()` 在旧实现中会返回 true，但它不是物理设备。仅允许明确支持 SDK 模拟器的能力。
- `LOCAL_DEMO`：应用纯本地 Fake，不能用 `DroneManager` 的 DJI 模拟器标志表达。禁止下发 DJI Key。
- 未知的 `productType`、固件或 SN 表示 `null` 或独立的 `UNKNOWN` 状态，不能伪装成已验证的具体机型。
- 每个遥测量分别带来源和观测时刻。`valid=false` 时 `value` 必须为 null；不提供数值时写明 reason。不要用 `0`、`-1` 同时代表真实值和缺值。
- epoch 时间用于日志和跨进程显示；monotonic 时间用于本进程 freshness 计算，避免系统校时使年龄倒退。跨进程或重启后不得比较不同启动周期的 monotonic 时间。
- 经纬度单位为 WGS84 十进制度；高度单位米（需 F03 明确椭球高/海拔/相对起飞点，本契约暂未指定语义）；速度米/秒；航向度 `[0, 360)`；电量和信号强度整数百分比 `[0, 100]`。
- 每个遥测字段单独判断新鲜度。位置刚更新不能让已经过期的电量或航向看起来新鲜。消费者按字段设 freshness policy，生产者必须给出实际观测时间。
- `readiness` 是连接层综合状态，不能替代对特定 action capability 及该 action 必需遥测字段的检查。

## 4. Kotlin 查询和订阅接口提案

推荐一个查询当前不可变快照、订阅后持续收更新的接口。可用项目已有回调风格实现，不要求新增 Flow 依赖。

```kotlin
interface DeviceStateProvider {
    /** 返回立即可用的不可变快照；不能阻塞等待 SDK。 */
    fun currentSnapshot(): DeviceStateSnapshot

    /** 注册后必须先送出一次当前快照，再送后续更新。 */
    fun subscribe(listener: DeviceStateListener): DeviceStateSubscription
}

fun interface DeviceStateListener {
    fun onSnapshot(snapshot: DeviceStateSnapshot)
}

fun interface DeviceStateSubscription {
    /** 幂等；取消后不得再派发新回调。 */
    fun cancel()
}
```

线程和原子性约定：

1. `currentSnapshot()` 可从任意线程读取，不触发 DJI SDK 阻塞操作；返回同一时刻自洽的快照。
2. 每个订阅者收到 generation 单调不减的快照；同一订阅串行回调，不允许并行回调相互覆盖。
3. 生产者的 SDK 线程不得直接假设消费者在主线程。若调用 UI，消费者切主线程；不要要求整个状态广播固定在 UI 线程执行。
4. `subscribe()` 必须处理“读当前值与刚好发生状态变化”的竞争：注册时先挂监听，再原子获取并派发当前值，或用其他等价办法确保没有漏事件。
5. 取消订阅是幂等的；页面 `onStop/onDestroy` 按生命周期取消。取消之后已经排队的事件应通过 subscription active 标志或 generation 丢弃。
6. SDK 回调有重复、迟到或乱序时，F02/F03 应以会话 generation 与来源时间过滤；旧连接 generation 的数据不更新新设备快照。
7. 内部异常不允许吞掉后继续报告 `READY`。状态流必须能报告 `ERROR`、可恢复性及稳定错误码；用户文案可本地化。

## 5. 推荐状态转换

```text
OFFLINE / NOT_INITIALIZED
  └─ SDK 初始化 → OFFLINE / INITIALIZING
       ├─ 注册成功 → OFFLINE / REGISTERED
       └─ 注册失败 → OFFLINE / REGISTER_FAILED

REGISTERED + 发现物理产品 → DEVICE / CONNECTING
  ├─ 链路稳定且必要身份可读 → DEVICE / CONNECTED / READY
  ├─ 链路断开 → OFFLINE / DISCONNECTED（generation + 1）
  └─ 连接或资料读取失败 → DEVICE / ERROR 或 DEGRADED（明确缺少项）

启用 DJI 模拟器 → DJI_SIMULATOR / READY（独立于 DEVICE）
退出 DJI 模拟器 → 根据物理连接状态回到 DEVICE 或 OFFLINE

切换到纯本地 Fake → LOCAL_DEMO（不触发 SDK 注册或设备命令）
```

状态可以按项目实现简化，但必须保证：模拟器开关不会覆盖真实连接身份；断连会清除当前设备能力和当前 generation 的遥测；下一台设备连接后旧设备回调不能污染它；注册失败与设备未连接是不同事实。

## 6. 消费者的动作前置检查

设备操作消费者按以下顺序同步读取同一快照；任一条件失败就不调用设备 SDK：

1. 期望模式为 `DEVICE`，快照模式也是 `DEVICE`；不将 `isConnected()==true` 当成物理连接证明。
2. SDK registration 为 `REGISTERED`，link 为 `CONNECTED`，readiness 为 `READY`。
3. 所需 action 在当前 generation 的能力快照里为明确 `true`；`false` 或 `null` 都拒绝。
4. 每个动作要求的遥测字段存在、`valid=true`、来源为 `DEVICE`、generation 匹配且年龄不超过消费者声明的最大年龄。
5. 动作参数通过独立的输入边界与业务安全规则校验；设备状态接口本身不会替参数背书。
6. 请求开始前再次核对 generation；若设备切换或 generation 变化，作废待执行请求，不能自动重发给新设备。

这里不定义具体遥测最大年龄或飞行安全阈值。F03 需提供各字段更新频率和丢失行为，F12/相机负责人按 DJI 设备能力、动作要求和测试规程提出阈值，再由对应责任人审定。

## 7. 错误约定

建议错误对象至少包括稳定 `code`、用户可见 `message`、是否可重试的 `retryable`、发生时的 `generation`、发生时间和可选字段名。不要把 API Key、序列号、令牌、原始认证头或任意 SDK 内部对象写入普通日志。

| 条件 | 推荐错误码 | 消费者行为 |
|---|---|---|
| SDK 未注册 | `SDK_NOT_REGISTERED` | 提示注册状态；不下发 |
| 设备未连接 | `DEVICE_DISCONNECTED` | 停止该请求；等待用户显式重试 |
| 当前是 SDK 模拟器 | `SIMULATOR_MODE` | 真实操作拒绝，模拟演示可继续 |
| 当前是本地演示 | `LOCAL_DEMO_MODE` | 不进入 DJI 适配器 |
| 能力未知或不支持 | `CAPABILITY_UNKNOWN` / `CAPABILITY_UNSUPPORTED` | 拒绝对应动作 |
| 遥测缺失或过期 | `TELEMETRY_UNAVAILABLE` / `TELEMETRY_STALE` | 拒绝依赖该字段的动作；显示缺失项 |
| 状态来自旧设备会话 | `STALE_GENERATION` | 丢弃回调并刷新当前快照 |
| 状态读取异常 | `STATE_PROVIDER_ERROR` | 不伪报 READY；可展示可恢复提示 |

## 8. F02/F03 验收与测试清单

**接口验收：**

- [ ] `currentSnapshot()` 启动后立即返回，不阻塞，也不需要先开页面轮询。
- [ ] `subscribe()` 立即得到当前快照；状态变化事件不丢，取消订阅后不再回调。
- [ ] 状态消费者能区别真实设备、DJI 模拟器、纯本地演示和离线状态；现有 `isConnected()` 模拟器语义有明确兼容处理。
- [ ] 设备信息缺失时 readiness/错误字段准确，不把默认值伪装成真实信息。
- [ ] 遥测字段携带独立来源和时间；缺值、无效值、过期值、无效百分比、NaN/Infinity 均不会变成有效的零值。
- [ ] 连接 generation 变化后，旧设备的迟到回调不会覆盖新设备状态。
- [ ] 线程契约和 Android 生命周期用法有文档示例；状态监听可释放，不形成 Activity 泄漏。

**F02 单测场景：** 尚未初始化、注册中、注册成功、注册失败；断连→重连；连接时设备资料晚到；连接后 SDK 模拟器开启/停止；真实连接与模拟器同时发生时模式判定；监听首次快照、取消、重复回调、迟到回调。

**F03 单测场景：** 每字段新值、未支持、丢失、无效数值和过期；时间戳单调年龄；generation 不匹配；设备断连清除设备来源遥测；模拟遥测只能来源于 simulator；同一帧快照版本一致。

**消费者契约测试：** 以 Fake `DeviceStateProvider` 喂入全部四种模式；只有 `DEVICE + REGISTERED + CONNECTED + READY + generation 匹配 + 所需能力 true + 所需遥测 fresh` 才允许调用设备 Fake。其余每种条件单独失败时，设备调用计数必须为 0。状态从 READY 变断连或切换设备后，旧请求不得继续执行。

**设备验收：** 记录无人机型号、固件、DJI SDK 版本、移动设备和 Android 版本；逐项验证发现/连接/断开/重连、设备信息和遥测时间戳。没有兼容设备时只可以声明 Fake/单测通过，真实连接验收保持待设备验证。

## 9. 请 F02/F03 负责人回复确认

请逐项回复“接受 / 修改 / 目前无法提供”，并补充下列信息：

1. 现有 `DroneManager` 哪些 getter、回调已可作为状态源？能否提供原子快照及订阅首帧？若短期不能，请给出过渡接口和迁移期限。
2. 物理连接与 DJI 模拟器的来源如何区分？纯本地 Demo 是否沿用单独模式，哪一层负责切换？
3. 当前已有遥测有哪些字段、单位、DJI Key 来源和刷新周期？缺失/异常如何表达？各字段是否能提供观测时间和有效性？
4. 注册态、产品连接态、设备身份和遥测是否各自异步？回调线程、顺序、重复和解绑规则是什么？
5. 设备 capability 目前是否可查询？首轮能够准确承诺哪些型号和动作；未知型号如何拒绝？
6. `generation`/session ID 是否采用；若不用，如何阻止旧产品迟到回调污染新产品状态？
7. 请提供可用于契约测试的 Fake 或状态事件样例，以及连接/断连/模拟器/缺值场景的验证设备和负责人。

收到确认后，应把本文件提升版本、标注真实字段到提案字段的映射，再由消费者实现适配。若存在分歧，先记录差异和过渡契约，不通过直接改共享 API 让其他模块静默适配。
