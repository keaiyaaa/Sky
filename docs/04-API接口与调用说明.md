# 04 - API 接口与调用说明（Sky / 天枢巡检）

> 当前内部接口补充：F07/F08/F09/F11 使用既有 `RouteRepository`、新增 `RouteSafetyValidator` 和 `LocalMissionRuntime` / `MissionRunRepository`。这些接口直接访问本地正式 SQLite 存储，不新增 HTTP 接口。参数、返回结果、状态机和历史查询示例见 [航线与模拟巡检契约](contracts/fzp-route-mission-contracts.md)。原文 DJI 网关继续作为待真机联调接口，页面不会自动切换到该执行链路。

> 依据代码基线 `30772d3` 的真实调用关系整理。本仓库**没有任何业务后端**，本文按五类如实分类：
> ① Android 内部模块接口 ② 无人机控制接口（DJI MSDK 调用面）③ 数据库访问接口 ④ 已使用的外部 HTTP 服务 ⑤ 规划接口（尚未实现，显式标注）。
> 状态标注：`[已验证]`（本次构建/测试或交叉引用核实）/ `[代码可见但未验证]` / `[规划接口，尚未实现]`
> 敏感凭证一律脱敏（`****`），完整值见对应源文件。

---

## 1. Android 内部模块接口（代码层调用契约）

### 1.1 全局 SDK 管理（drone.DroneManager，单例）

唯一初始化链路：`SkyPivotApplication.attachBaseContext → Helper.install` → `onCreate(Handler.post) → DroneManager.getInstance().initSDK()`。

| 方法 | 签名（简化） | 功能 | 实现状态 | 主要调用方 |
|---|---|---|---|---|
| initSDK | `(context, onComplete:(Boolean,String)->Unit)` | SDKManager.init → registerApp（App Key 读自 Manifest） | 真实 DJI 调用 `[代码可见但未验证]` | SkyPivotApplication、HomeActivity 测试面板 |
| isConnected / isRegistered / getSdkState | `()` | 连接/注册/初始化状态查询 | 真实（isConnected 含模拟器短路） | HomeActivity、kami |
| getRuntimeMode | `(): RuntimeMode{REAL,SIM_ONLY,OFFLINE}` | 运行模式判定（simulatorActive 优先） | 本地状态 | kami.RoutePlanningActivity:479 |
| enableSimulator / disableSimulator | `(lat,lng,satellites) / ()` | 模拟器开关（SimulatorManager + Debug 守卫） | 真实 DJI 调用 | HomeActivity 测试面板 |
| startTelemetry / stopTelemetry | `(onAltitude,onDistance,onSpeed)` | 遥测推送 | **SIM：模拟循环；REAL：TODO 空实现**（D-04） | HomeActivity:127 |
| getBatteryPercent / getSignalStrength | `(): Int` | 电量/信号 | **模拟（真机返回 -1）** | HomeActivity |
| setObstacleAction | `(action:"brake"/"bypass", cb)` | 避障开关下发 | **真实 DJI 调用**（CapabilityManager 预检 + bypass→brake 降级） | FlightSettingsActivity |
| setReturnAltitude / setFailsafeAction / setBeginnerMode / setControlSensitivity / setVideoQuality / setImageQuality / setAutoSyncEnabled / setLowBatteryReturnEnabled | `(…, cb:(Boolean)->Unit)` | 8 个设置项下发 | **全部 stub，恒回调 false**（D-05/D-08） | FlightSettings/VideoSettings |
| queryObstacleAvoidingState | `(cb:(Boolean?)->Unit)` | 主动避障状态查询 | 真实 DJI 调用（仅 REAL） | kami 避障状态文本 |
| addStateListener / removeStateListener | `(ConnectionStateListener)` | 连接状态订阅（4 回调含 onSimulatorTelemetry） | 真实 | HomeActivity |
| connectDevice / searchDevices / disconnectDevice | `…` | 连接（15s 轮询）/ 搜索 / **断开仅提示** | 真实/真实/stub | HomeActivity 测试面板 |

### 1.2 航线规划与任务（kami 包）

| 接口 | 所在文件 | 契约 | 状态 |
|---|---|---|---|
| `RouteDao`（接口） | `kami/data/RouteDao.kt:8-14` | `getRouteSummaries(): List<RouteSummary>`、`getRoute(routeId): RouteMission?`、`saveRoute(RoutePlan, List<Waypoint>)`、`deleteRoute(routeId)`、`getRouteCount(): Int`；实现 `SqliteRouteDao`（事务 + 3 条上限 + `RouteLimitExceededException`） | `[已验证]`（被 Activity 调用，无泄漏） |
| `RouteOptimizer`（接口） | `kami/optimization/RouteOptimizer.kt` | `optimize(waypoints): RouteOptimizationResult`；实现 `LocalNearestNeighborRouteOptimizer`（实际生效）、`HybridRouteOptimizer`（aiClient 恒 null） | `[已验证]` |
| `RouteMapController`（接口，11 方法）/ `RouteMapCallbacks`（4 事件） | `kami/map/` | 地图抽象；实现 `DjiV5MapLibreRouteMapController`（MapLibre 10.3.5） | `[已验证]` |
| `DjiV5MissionGateway`（接口） | `kami/mission/DjiV5MissionGateway.kt:12-17` | `setTelemetryListener / importKmz / startMission / stopMission`；实现 `DjiV5ReflectiveMissionGateway`（反射 + Proxy + 90s 超时） | `[代码可见但未验证]`（importKmz 因注册检查恒 false 必失败） |
| `FlightControlBridge`（接口） | `kami/mission/FlightControlBridge.kt:7-16` | `prepareMission(route,waypoints): MissionLaunchResult`、`startPreparedMission(kmzFile): MissionLaunchResult`；实现 `DjiWpmlFlightControlBridge`（不含起降/返航指令） | `[代码可见但未验证]` |
| `WpmlRouteExporter.export` | `kami/mission/WpmlRouteExporter.kt` | 输入 RoutePlan+Waypoints → `filesDir/missions/<名>_<时间戳>.kmz`（wpmz/template.kml + waylines.wpml，WPML 1.0.6，速度 8m/s，finishAction=goHome，executeRCLostAction=goBack，takeOffSecurityHeight=20）；**无拍照 actionGroup** | `[已验证]`（文件生成逻辑完整；真机兼容性未验证） |
| `FlightProgressTracker` | `kami/mission/` | `resolveFlownWaypointIndex(telemetry)`：haversine ≤20m 判定已飞航点（单调） | `[已验证]`（纯函数） |

### 1.3 巡检任务数据（asuka 包）

| 接口 | 所在文件 | 契约 | 状态 |
|---|---|---|---|
| `InspectionDataSource`（接口） | `asuka/data/InspectionDataSource.kt` | 同步签名：getAllDateGroups/getDateGroup/getTask/getLatestDateGroup/getAllDates/sortDateGroups 等 | `[已验证]` |
| `InspectionRepository` | `asuka/data/InspectionRepository.kt` | 数据源选择：Room 优先 → 空库灌 Mock 并落库 → Msdk 文件扫描兜底；`withContext(Dispatchers.IO)` | `[代码可见但未验证]` |
| `TaskDao`（Room，12 方法）/ `AnalysisDao`（8 方法） | `asuka/database/daos/` | Flow 查询 + suspend 写入；**10 个方法无调用者** | `[已验证]` |
| `PdfReportUtil.writePdf` | `asuka/utils/PdfReportUtil.kt` | 输入 task + 全部日期组 → `…/TianshuInspection/reports/report_*.pdf`（A4 纯文本，系统 PdfDocument） | `[代码可见但未验证]` |

### 1.4 航拍与语音（aerial 包）

| 接口 | 所在文件 | 契约 | 状态 |
|---|---|---|---|
| `AerialDroneManager`（object） | `aerial/AerialDroneManager.kt` | `enableSimulator/disableSimulator`、`setCameraModeToPhoto/Video`、`startShootPhoto`、`startRecord/stopRecord`（全部 KeyManager，组件 LEFT_OR_MAIN） | `[代码可见但未验证]` |
| `LLMService.analyzeIntent` | `aerial/LLMService.kt:25` | 输入语音文本 → OkHttp POST DeepSeek → `FlightIntent`（11 种 flightType）；**Key 占位符必 401**；围栏剥离损坏必 JsonSyntaxException（D-07） | `[代码可见但未验证]`（在线链路必失败） |
| `VoskHelper.startListening/destroy` | `aerial/VoskHelper.kt` | 16kHz 中文模型（首次联网下载 42MB 至 `getExternalFilesDir`）→ JSON 结果 text 字段回调 | `[代码可见但未验证]` |
| `NetworkUtil.isOnline` | `aerial/NetworkUtil.kt` | ConnectivityManager 在线判定（LLM/离线分流用） | `[已验证]` |

### 1.5 设置存储（utils.PreferenceManager）

`tianshu_prefs` 键位契约 `[已验证]`：`return_altitude`(Int,默认120,范围UI 20-400)、`failsafe_action`(String,"rth"/"hover"/"land")、`beginner_mode`(Bool)、`obstacle_action`("brake"/"bypass")、`control_sensitivity`(Int,0-100)、`video_quality`("smooth"/"hd"/"ultra"/"4k")、`image_quality`("standard"/"hd"/"ultra")、`auto_sync`(Bool,true)、`low_battery_return`(Bool,true)、`current_device_id`(Long,-1,**无使用者**)。

---

## 2. 无人机控制接口（对 DJI MSDK V5 5.18.0 的真实调用面）

> 全部经 secneo 加固运行时加载（须先 `Helper.install`）。 kami 网关走**反射**，drone/aerial 走**编译期 API（compileOnly provided）**。

| # | 调用点（代码位置） | DJI API | 用途 | 状态 |
|---|---|---|---|---|
| DJ-01 | `DroneManager.kt:244,270` | `SDKManager.getInstance().init(context, callback)` / `.registerApp()` | SDK 初始化与 App 注册（Key：Manifest `com.dji.sdk.API_KEY = a4c1****`） | 真实调用 |
| DJ-02 | `DroneManager.kt:396-410,444-453` | `SimulatorManager…enableSimulator(InitializationSettings)/disableSimulator()` | 模拟器开关（Debug 守卫） | 真实调用 |
| DJ-03 | `DroneManager.kt:608-639` | `KeyManager.getValue(DJIProductKey.KeyProductType/KeyFirmwareVersion/KeySerialNumber)` | 机型/固件/SN | 真实调用 |
| DJ-04 | `DroneManager.kt:941-988` | `KeyManager.setValue(KeyObstacleAvoidanceEnabled / KeyActiveObstacleAvoidanceEnabled)` + `CapabilityManager` 预检 | 避障开关下发（含降级） | 真实调用 |
| DJ-05 | `DroneManager.kt:1001-1002` | `KeyManager.getValue(DJIFlightAssistantKey.KeyIsActivelyAvoidingObstacle)` | 避障状态查询 | 真实调用 |
| DJ-06 | `AerialDroneManager.kt:61-118` | `KeyManager`：`CameraKey`（模式/拍照/录像）、`FlightControllerKey.KeyStartSimulator/KeyStopSimulator` | 相机控制、（未用的）模拟器 Key | 真实调用 |
| DJ-07 | `DjiV5ReflectiveMissionGateway.kt:34-98` | 反射 `dji.v5.manager.aircraft.waypoint3.WaypointMissionManager.getInstance().pushKMZFileToAircraft(path, cb)` / `startMission(name, waylineIds, cb)` / `stopMission`（无调用方） | KMZ 上传与任务启动 | **被注册检查恒 false 拦截（断链 D-01）** |

**当前不存在的 DJI 调用**（全仓 grep 证实）：`KeyStartTakeoff`、`KeyStartLanding`、`KeyStartGoHome`、虚拟摇杆 `VirtualStickManager`、`KeyAutoRTHLongitude/Latitude`（Home 设置）、低电量 Key（`KeyLowBatteryWarning` 等）、图传解码（`MediaManager` / `decodeVideoFrame` / `IVideoFeed`）。

**飞行控制指令的调用链现状**：不存在"App→起飞/降落/返航"的软件指令路径。返航完全寄托于 WPML 元数据（`finishAction=goHome`、`executeRCLostAction=goBack`、`takeOffSecurityHeight=20`）交由 DJI 固件执行（`WpmlRouteExporter.kt:64-71`）。

---

## 3. 数据库访问接口

### 3.1 asuka Room（`inspection_db` v1）`[已验证]`

- 实体：`TaskEntity`（tasks：id/date/routeName/name/time/status/priority/videos/images/defects/videoPoster/videoSrc/defectId/duration/thumbnails/createdAt）、`AnalysisEntity`（analysis：id/taskId FK级联/defectType/location/suggestion/aiCount）。
- 访问方式：`AppDatabase.getInstance(context)` 单例；DAO 方法全部 `suspend` 或返回 `Flow`；`fallbackToDestructiveMigration` 开启。
- **注意**：本库与后端设计文档的 14 表**不对应**（详见审计报告 §6.5），status 为自由字符串（"Urgent"/"Normal"，SQL 硬编码于 `TaskDao.kt:50`）。

### 3.2 kami SQLite（`route_planning.db` v1）`[已验证]`

- 建表：`routes(id TEXT PK, name, task_name, task_time, priority, created_at, updated_at)`、`waypoints(id TEXT PK, route_id FK级联, sequence, latitude, longitude, altitude, action)`，索引 `(route_id, sequence)`。
- 访问方式：`RouteDatabaseHelper`（SQLiteOpenHelper）+ `SqliteRouteDao`（事务 + CONFLICT_REPLACE + 航点先删后插 + 上限 3 条）。`onUpgrade` 破坏式 DROP 重建。
- `waypoints.action` 存了"拍照"但**从不导出**到 WPML（D-03）。

### 3.3 后端 MySQL（14 表设计）

`天枢巡检系统数据库字段设计_v2.md` 描述的 users/drones/flight_routes/waypoints/inspection_tasks/inspection_records/inspection_record_details/ai_records/work_orders/vip_packages/token_transactions/orders/user_settings/system_configs——**均为 `[规划接口，尚未实现]`，无部署实例、无访问代码。**

---

## 4. 已使用的外部 HTTP 服务 `[已验证]`（无业务后端）

| 服务 | 端点 | 认证 | 调用方 | 状态 |
|---|---|---|---|---|
| DeepSeek Chat | `https://api.deepseek.com/v1/chat/completions`（模型 deepseek-chat，temperature 0.3，超时 30s/意图 15s） | `Authorization: Bearer <API_KEY>`——**Key 为占位符 `your_api_key_here`** | `LLMService.analyzeIntent` | 调用必 401 → 降级 |
| Vosk 模型下载 | `https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip`（~42MB） | 无 | `VoskHelper`（首次使用） | 无哈希校验 |
| MapLibre 底图瓦片 | 高德 `webrd01-04.is.autonavi.com/...style=8`（**未拼 Key**）→ 天地图 `t0-7.tianditu.gov.cn/DataServer`（**token 未配置，死路**）→ `demotiles.maplibre.org/style.json` 兜底 | 无 | `DjiV5MapLibreRouteMapController` | 高德 Key 为无效配置（S-02） |
| Unsplash 图片 | Mock 数据缩略图 URL ×3 | 无 | Glide（MissionDetailActivity） | 演示数据 |
| DJI SDK 注册 | SDK 内部请求（绑定包名+App Key） | App Key | SDKManager | `[待确认]` Key 有效性 |

**登录与用户身份认证**：不存在（无登录页、无 Token 存储、抽屉"退出登录"仅 `finishAffinity()`）。

---

## 5. 规划接口（尚未实现）——依据真实业务需求与 14 表设计整理

> 以下全部为 `[规划接口，尚未实现]`，供后端立项与 Phase 04 对接使用；不虚构任何 URL/部署信息，服务地址待后端确定后回填。

### 5.1 认证与用户

| 接口 | 方法（建议） | 说明 | 对应表 |
|---|---|---|---|
| 注册 / 登录 / 登出 / 刷新 Token | `POST /auth/register`、`POST /auth/login`、`POST /auth/logout`、`POST /auth/refresh` | 账号+密码，返回访问/刷新令牌；当前 App 无任何登录 UI，需先行补页面 | users |
| 获取/修改个人信息 | `GET/PUT /user/profile` | 头像、昵称、手机号；替换 AccountFragment 假数据 | users |
| 修改密码 | `PUT /user/password` | — | users |

### 5.2 设备与航线

| 接口 | 方法（建议） | 说明 | 对应表 |
|---|---|---|---|
| 设备绑定/列表/详情 | `POST /drones/bind`、`GET /drones`、`GET /drones/{id}` | SN 绑定，电量/存储/在线状态上报 | drones |
| 航线保存/列表/删除 | `POST/GET/DELETE /routes(/{id})` | 本地 kami SQLite 与云端同步；route_config 存 JSON 或 WPML | flight_routes、waypoints |
| 用户设置同步 | `GET/PUT /user/settings` | 承接现有 `tianshu_prefs` 10 键（字段已在 user_settings 表设计中一一对应） | user_settings |

### 5.3 任务与记录

| 接口 | 方法（建议） | 说明 | 对应表 |
|---|---|---|---|
| 任务创建/列表/详情/取消 | `POST/GET /tasks(/{id})`、`POST /tasks/{id}/cancel` | 对接 asuka 任务中心，替换 Mock | inspection_tasks |
| 巡检记录上报/查询 | `POST /records`、`GET /records(/{id})` | 飞行结束后回传实际距离/时长/状态 | inspection_records |
| 记录明细（媒体/告警）上传与查询 | `POST /records/{id}/details`、`GET /records/{id}/details` | 图片/视频 + AI 识别结果；当前 AI 为硬编码文案，需真实识别服务支撑 | inspection_record_details、ai_records |
| 工单创建/流转 | `POST /work-orders`、`PUT /work-orders/{id}` | 替换 MissionDetailActivity 假工单 | work_orders |

### 5.4 会员与 Token

| 接口 | 方法（建议） | 说明 | 对应表 |
|---|---|---|---|
| 套餐列表 / 下单 / 支付回调 / 查询订单 | `GET /vip/packages`、`POST /orders`、`POST /orders/{id}/callback`、`GET /orders` | 替换 VipActivity 硬编码套餐与假成功弹窗 | vip_packages、orders |
| Token 余额 / 流水 | `GET /token/balance`、`GET /token/transactions` | AI 调用按 token_used 扣减 | token_transactions、ai_records |

### 5.5 系统

| 接口 | 方法（建议） | 说明 | 对应表 |
|---|---|---|---|
| 版本检查 | `GET /system/version` | 替换 UpdateCheckActivity 占位页 | system_configs（software_version 等字段） |

### 5.6 规划的 App 内部接口（架构建议，源自 docs/03 §5）

| 接口 | 说明 | 状态 |
|---|---|---|
| `FlightControlApi`（takeoff/land/goHome/hover/virtualStick） | 抽象飞控指令，DJI 实现与模拟实现分离 | `[规划接口，尚未实现]` |
| `SdkRegistry`（注册状态统一源） | 替换 kami 自建 DjiSdkManager 状态机 | `[规划接口，尚未实现]` |
| `TelemetryBus`（统一遥测流） | 替换 TODO 遥测与悬空 telemetryListener | `[规划接口，尚未实现]` |
| WPML actionGroup 导出 | 把 waypoints.action 写入 `wpml:actionGroup`（拍照） | `[规划接口，尚未实现]` |

---

## 6. 调用示例（当前真实代码形态）

**示例 1：反射网关上传 KMZ（kami，当前被注册检查拦截）** — `DjiV5ReflectiveMissionGateway.kt:21-60`
```kotlin
val result = gateway.importKmz(kmzFile)          // → 内部先查 DjiSdkManager.isRegistered()（恒 false → "DJI SDK 尚未注册成功"）
val result2 = gateway.startMission(kmzFile)      // 设计路径：反射 pushKMZFileToAircraft(path, progressCb) → startMission(name, waylineIds, cb)
```

**示例 2：拍照（aerial，真实 KeyManager）** — `AerialDroneManager.kt:103-118`
```kotlin
KeyManager.getInstance().setValue(KeyTools.createKey(CameraKey.KeyCameraMode), CameraMode.PhotoNormal, cb)  // 先切模式
KeyManager.getInstance().action(KeyTools.createKey(CameraKey.KeyStartShootPhoto), cb)                       // 再拍摄
```

**示例 3：避障开关下发（drone，真实 + 能力预检）** — `DroneManager.kt:941-988`
```kotlin
// CapabilityManager 检查机型支持 → setBooleanKey(KeyObstacleAvoidanceEnabled, true) → KeyActiveObstacleAvoidanceEnabled（bypass 不支持时降级 brake）
```

**示例 4：Room 数据读取（asuka）** — `InspectionRepository.kt:26-37`
```kotlin
suspend fun getAllDateGroups(): List<InspectionDateGroup> = withContext(Dispatchers.IO) {
    databaseDataSource.getAllDateGroups().ifEmpty {           // Room 优先
        val groups = msdkDataSource.getAllDateGroups()        // "MSDK"实为文件扫描，实际回落 Mock
        databaseDataSource.insertTasks(groups); groups        // 首次固化入库
    }
}
```

---

## 7. 依赖条件与验证状态汇总

| 交互链路（任务书指定优先链路） | 现状 | 验证状态 |
|---|---|---|
| 登录与用户身份认证 | 不存在 | — |
| 用户信息管理 | 假数据（AccountFragment） | `[代码可见但未验证]` |
| 飞行状态获取 | 模拟/TODO 双轨 | `[代码可见但未验证]` |
| 飞行控制指令 | 不存在（仅 WPML 元数据寄望固件） | — |
| 航线任务 | 规划/导出可用；上传与起飞断链 | `[代码可见但未验证]` |
| 巡检任务 | Mock 数据 + 索引 Bug | `[代码可见但未验证]` |
| 飞行日志 | Toast"开发中"；"MSDK 数据源"实为文件扫描 | `[代码可见但未验证]` |
| 图传与媒体 | 无真机图传；拍照/录像真实 | `[代码可见但未验证]` |
| 设置参数持久化 | SharedPreferences 真实；下发 stub | `[已验证]`（键位） |
| 会员与 Token 操作 | 假购买，无 Token 逻辑 | `[代码可见但未验证]` |
