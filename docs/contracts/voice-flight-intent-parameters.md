# 语音指令到 FlightIntent 参数契约（当前实现）

## 1. 链路和边界

语音识别产生文本后，`Aerial_photography` 根据 `NetworkUtil.isOnline` 分流：有网时调用 `LLMService.analyzeIntent`，向 DeepSeek `deepseek-chat` 发送系统约束和用户文本；服务返回单个 JSON 对象后，由 `FlightIntentParser` 做字段检查、`FlightIntentValidator` 做动作与参数检查，再交给确认页。无网时只做 `LocalCommandMatcher` 的有限本地匹配。

解析成功仅表示参数符合**本 App 的预览契约**。当前没有接入机型能力、飞行状态、空域、障碍物、操作者权限等执行前检查。大部分扩展动作只供参数预览；`takeoff`、`land`、`return_home`、无定时 `hover` 仍走已有模拟执行器；`photo` 和 `video` 走现有相机流程。此文档不代表已完成真实飞行控制。

## 2. JSON 格式

```json
{"flightType":"fly_to","parameters":{"referenceFrame":"body","direction":"forward","distance":10.0,"speed":2.0}}
```

只允许顶层 `flightType` 和 `parameters`。`parameters` 必须为对象，不允许未知字段、无关字段、非有限数值。一个输入只对应一个动作；含多个连续动作、否定、条件、目标不明或缺少必需参数时，模型应输出 `unknown` 并给出 `parameters.reason`，解析器拒绝进入确认流程。服务端或模型输出不能绕过本地校验。

## 3. 动作与参数

| 动作 | 必需参数及语义 | 当前处理 |
| --- | --- | --- |
| `takeoff` | 空对象；自动起飞没有目标高度参数 | 模拟执行 |
| `stop_takeoff` | 空对象 | 参数预览 |
| `land` | 空对象 | 模拟执行 |
| `stop_landing` | 空对象 | 参数预览 |
| `confirm_landing` | 空对象 | 参数预览 |
| `return_home` | 空对象 | 模拟执行 |
| `stop_return_home` | 空对象 | 参数预览 |
| `hover` | 空对象；可选 `duration`，单位秒 | 无时长可模拟；定时仅预览 |
| `photo` | 空对象；可选 `count=1` | 现有相机流程 |
| `video` | `action=start` 或 `stop` | 现有相机流程 |
| `fly_to` | `referenceFrame=body`，`direction=forward/backward/left/right`，`distance` 米，`speed` 米/秒 | 参数预览 |
| `change_altitude` | `altitudeReference=takeoff`，目标 `altitude` 米，`speed` 米/秒 | 参数预览 |
| `rotate` | `referenceFrame=body`，相对机头 `yaw` 度 | 参数预览 |
| `set_return_home_height` | `altitudeReference=takeoff`，`altitude` 米 | 参数预览 |
| `gimbal_control` | `pitch` 度；可选 `yaw` 度 | 参数预览 |
| `orbit` | `radius` 米、`speed` 米/秒、`laps` 圈、`direction=clockwise/counterclockwise` | 参数预览 |
| `waypoint` | `pattern=line/rectangle/zigzag`，`altitudeReference=takeoff`，`altitude` 米、`speed` 米/秒、`length` 米；矩形另需 `width` 米 | 参数预览 |

`fly_to` 是本 App 的“机体坐标系相对方向和距离”意图，**不是** DJI SDK 的原生单次飞行命令。后续要落地，应先转成受控轨迹并持续闭环发送，再按机型查询支持能力。`orbit`、`waypoint` 也是规划意图，不能直接映射为一个通用 SDK 调用。

## 4. 本地预览范围

- 高度：大于 0 且不超过 120 米；距离及航线尺寸：大于 0 且不超过 500 米；环绕半径：大于 0 且不超过 200 米。
- 速度：大于 0 且不超过 15 米/秒；环绕圈数：1 至 10；悬停时长：1 至 300 秒。
- 旋转角：-180 至 180 度；云台俯仰：-90 至 30 度。

这些是应用端防止异常模型输出进入预览的上限，**不是** DJI 通用机型能力、飞行安全保证或法规限值。实际执行还必须按所连机型和当前状态取得可用范围；用户没有给出必需数值时不得填默认值。

## 5. 与 DJI Mobile SDK 对照

- DJI `FlightControllerKey` 包含自动起飞、停止起飞、自动降落、停止降落、确认降落、返航和停止返航等独立 Key；其自动起飞说明约为 1.2 米，没有“指定目标高度”的同一个参数。参见 [DJI FlightControllerKey](https://developer.dji.com/api-reference-v5/Components/IKeyManager/Key_FlightController_FlightControllerKey.html)。
- 连续平移、升降、偏航属于虚拟摇杆控制范畴，需要启用与持续发送控制量，并明确坐标系和控制模式。参见 [DJI IVirtualStickManager](https://developer.dji.com/api-reference-v5/Components/IVirtualStickManager/IVirtualStickManager.html) 和 [VirtualStickFlightControlParam](https://developer.dji.com/api-reference-v5/android-api/Components/IVirtualStickManager/Value_FlightController_Struct_VirtualStickFlightControlParam.html)。

## 6. 当前核查与待验收

已对在线接口做独立请求冒烟测试：指定方向、距离和速度可得到 `fly_to`；“起飞到10米”被标为复合指令；“停止返航”得到 `stop_return_home`；明确相对起飞点的返航高度得到 `set_return_home_height`。本地解析器和校验器的单元测试通过。尚未在 Android 真机上验证网络切换、语音识别到文本、确认页全链路，也尚未接入真实 DJI 飞行控制器。

真机验收至少应覆盖：联网成功、离线、接口超时、接口鉴权失败、非 JSON、缺参、越界、复合指令；逐条核对展示参数与原话一致，并确认参数预览动作不会触发飞行执行。
