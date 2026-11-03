# 航线地图控制器

## 作用

`DjiV5MapLibreRouteMapController` 封装 MapLibre 10.3.5，绘制航点、相邻航段、示例区域和来源由调用方决定的位置。坐标统一使用 WGS84，默认不混用 GCJ02 瓦片。

## 参数与最小示例

构造参数 `context: Context` 为宿主上下文，`mapView: MapView` 为布局中的地图视图。创建视图之前必须初始化 MapLibre。

```kotlin
Mapbox.getInstance(this, null, WellKnownTileServer.MapLibre)
setContentView(R.layout.activity_route_planning)
val savedWaypoints = emptyList<Waypoint>()
val statusView: TextView = findViewById(R.id.tvRouteMapStatus)
val controller = DjiV5MapLibreRouteMapController(this, findViewById(R.id.mapView))
controller.setCallbacks(object : RouteMapCallbacks {
    override fun onMapReady() { controller.renderWaypoints(savedWaypoints) }
    override fun onMapPointSelected(latitude: Double, longitude: Double) {
        // 将候选坐标交给业务层校验和持久化，通过后再绘制。
    }
    override fun onWaypointDragged(waypointId: String, latitude: Double, longitude: Double) {
        // 拖拽结束只回调一次；校验失败时重绘原来的航点列表。
        controller.renderWaypoints(savedWaypoints)
    }
    override fun onFlightZoneSelected(zone: FlightZone) { }
    override fun onMapStatusChanged(message: String) { statusView.text = message }
})
controller.onCreate(savedInstanceState)
```

## 回调说明

| 回调 | 参数 | 含义 |
|---|---|---|
| `onMapReady()` | 无 | 样式或离线空白样式可用于叠加层绘制 |
| `onMapPointSelected()` | 纬度、经度 `Double` | 地图选点候选，不表示保存成功 |
| `onWaypointDragged()` | 航点标识 `String`、纬度和经度 `Double` | 松手后的候选坐标，不在拖动每帧写库 |
| `onFlightZoneSelected()` | `FlightZone` | 查询区域展示信息 |
| `onMapStatusChanged()` | `String` | 底图加载、离线降级和重试结果 |

## 公开方法与生命周期

`renderWaypoints(List<Waypoint>)` 更新稳定标识对应的标记；`renderRoute(List<Waypoint>, Int)` 用已到达航点下标绘制进度；`renderFlightZones(List<FlightZone>)` 绘制圆形规则；`showAircraftPosition(Double, Double)` 只绘制调用方提供的位置，不保证其来自真机；`moveTo()` 移动镜头；`clear()` 清理航线和位置叠加层；`retryStyle()` 重新加载底图。

宿主应依次转发 `onCreate(Bundle?)`、`onStart()`、`onResume()`、`onPause()`、`onStop()`、`onDestroy()`，并向 MapView 转发 `onSaveInstanceState(Bundle)`。`enableMyLocation()` 仅在调用方已获得定位权限后生效。

## 设计约束

底图加载失败时使用无外部资源的背景样式，仍允许坐标交互，但必须保留“底图不可用”的提示。拖动只改变标记预览，松手后交给业务层保存，系统取消手势则恢复起始坐标。业务校验和 SQLite 事务不在地图封装中实现。销毁后清空回调，防止网络请求迟到后操作已关闭页面。
