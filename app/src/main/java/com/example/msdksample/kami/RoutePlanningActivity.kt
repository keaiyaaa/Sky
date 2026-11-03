package com.example.msdksample.kami

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import android.database.sqlite.SQLiteException
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.msdksample.R
import com.example.msdksample.kami.adapter.RouteTabAdapter
import com.example.msdksample.kami.adapter.WaypointAdapter
import com.example.msdksample.kami.data.RouteRepository
import com.example.msdksample.kami.map.RouteMapCallbacks
import com.example.msdksample.kami.map.dji.DjiV5MapLibreRouteMapController
import com.example.msdksample.kami.mission.DjiWpmlFlightControlBridge
import com.example.msdksample.kami.mission.LocalMissionRuntime
import com.example.msdksample.kami.mission.MissionRun
import com.example.msdksample.kami.mission.MissionRunState
import com.example.msdksample.kami.mission.MissionScenario
import com.example.msdksample.kami.mission.WpmlRouteExporter
import com.example.msdksample.kami.model.DefaultFlightZones
import com.example.msdksample.kami.model.FlightZone
import com.example.msdksample.kami.model.GeoMath
import com.example.msdksample.kami.model.RouteMission
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RoutePriority
import com.example.msdksample.kami.model.RouteSafetyValidator
import com.example.msdksample.kami.model.RouteSummary
import com.example.msdksample.kami.model.Waypoint
import com.example.msdksample.kami.optimization.LocalNearestNeighborRouteOptimizer
import com.google.android.material.button.MaterialButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.WellKnownTileServer
import com.mapbox.mapboxsdk.maps.MapView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 航线规划实际入口，统一本地编辑、持久化、安全校验、优化和模拟巡检。
 * @see RoutePlanningActivity.md 页面接入和执行边界说明。
 */
class RoutePlanningActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var mapController: DjiV5MapLibreRouteMapController
    private lateinit var repository: RouteRepository
    private lateinit var runtime: LocalMissionRuntime
    private lateinit var waypointAdapter: WaypointAdapter
    private lateinit var routeAdapter: RouteTabAdapter
    private lateinit var sidebar: View
    private lateinit var tvPosition: TextView
    private lateinit var tvCoordinate: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvMapStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnAdd: ImageButton
    private lateinit var btnUndo: MaterialButton
    /** 页面缓存只用于渲染，所有保存成功结果均来自事务提交后的数据库查询。 */
    private var mission: RouteMission? = null
    private val waypoints = mutableListOf<Waypoint>()
    /** 在途页面操作阻止重复点击；任务执行锁另外由数据库维护。 */
    private var busy = false
    private var addPointMode = true
    private var undoWaypoints: List<Waypoint>? = null
    private val scope = MainScope()
    private val editingControls = mutableListOf<View>()

    /**
     * 创建页面并加载持久化航线，地图加载不阻塞坐标编辑。
     * @param {Bundle?} savedInstanceState 系统恢复的地图状态。
     * @return {Unit} 无返回值。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, null, WellKnownTileServer.MapLibre)
        setContentView(R.layout.activity_route_planning)
        repository = RouteRepository(applicationContext)
        runtime = LocalMissionRuntime.get(applicationContext)
        bindViews()
        setupMap(savedInstanceState)
        setupListsAndButtons()
        scope.launch { runtime.state.collect { renderMissionRun(it) } }
        scope.launch { runtime.failure.collect { renderMissionRun(runtime.state.value) } }
        perform({
            val lastRun = runtime.history().firstOrNull()
            val activeId = lastRun?.takeIf { it.state.isActive }?.routeId
            val savedId = getPreferences(MODE_PRIVATE).getString("selected_route_id", null)
            val initial = (activeId ?: savedId)?.let { repository.getRoute(it) }
                ?: repository.loadOrCreateInitialRoute()
            pageState(initial)
        }) { applyPage(it) }
    }

    /**
     * 绑定控件并收集执行期间需要锁定的编辑入口。
     * @return {Unit} 无返回值。
     */
    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        sidebar = findViewById(R.id.sidebarPanel)
        tvPosition = findViewById(R.id.tvCurrentPosition)
        tvCoordinate = findViewById(R.id.tvCurrentCoord)
        tvStatus = findViewById(R.id.tvObstacleStatus)
        tvMapStatus = findViewById(R.id.tvRouteMapStatus)
        btnAction = findViewById(R.id.btnAction)
        btnCancel = findViewById(R.id.btnCancel)
        btnPause = findViewById(R.id.btnPauseMission)
        btnAdd = findViewById(R.id.btnAddPoint)
        btnUndo = findViewById(R.id.btnUndoOptimization)
        listOf(R.id.btnNewRoute, R.id.btnEditRoute, R.id.btnOptimize, R.id.btnUndoOptimization,
            R.id.btnCoordinatePoint, R.id.btnAddPoint, R.id.btnErasePoint, R.id.btnSyncToMission)
            .forEach { editingControls.add(findViewById(it)) }
    }

    /**
     * 注册地图事件，拖拽结束才请求保存，失败时恢复数据库位置。
     * @param {Bundle?} savedInstanceState 系统地图状态。
     * @return {Unit} 无返回值。
     */
    private fun setupMap(savedInstanceState: Bundle?) {
        mapController = DjiV5MapLibreRouteMapController(this, mapView)
        mapController.setCallbacks(object : RouteMapCallbacks {
            /**
             * 地图就绪后重放航线和示例区域。
             * @return {Unit} 无返回值。
             */
            override fun onMapReady() {
                mapController.renderFlightZones(DefaultFlightZones.zones)
                renderRoute()
                waypoints.firstOrNull()?.let { mapController.moveTo(it.latitude, it.longitude) }
                if (ContextCompat.checkSelfPermission(this@RoutePlanningActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) mapController.enableMyLocation()
            }

            /**
             * 将地图点击转换为正式保存请求。
             * @param {Double} latitude 点击纬度。
             * @param {Double} longitude 点击经度。
             * @return {Unit} 编辑锁定时忽略点击。
             */
            override fun onMapPointSelected(latitude: Double, longitude: Double) {
                if (!canEdit() || !addPointMode) return
                val route = mission?.route ?: return
                commitWaypoints(waypoints + Waypoint(routeId = route.id, latitude = latitude, longitude = longitude), "已添加航点")
            }

            /**
             * 保存拖拽候选航线，任意规则失败时恢复地图。
             * @param {String} waypointId 航点稳定标识。
             * @param {Double} latitude 新纬度。
             * @param {Double} longitude 新经度。
             * @return {Unit} 无返回值。
             */
            override fun onWaypointDragged(waypointId: String, latitude: Double, longitude: Double) {
                if (!canEdit()) { renderRoute(); return }
                commitWaypoints(waypoints.map {
                    if (it.id == waypointId) it.copy(latitude = latitude, longitude = longitude) else it.copy()
                }, "航点位置已保存")
            }

            /**
             * 显示区域的示例性质。
             * @param {FlightZone} zone 用户选择的区域。
             * @return {Unit} 无返回值。
             */
            override fun onFlightZoneSelected(zone: FlightZone) { notifyUser(zone.name + "，仅用于本地规则演示") }

            /**
             * 显示底图加载结果，失败时保留离线编辑入口。
             * @param {String} message 面向用户的可用性提示。
             * @return {Unit} 无返回值。
             */
            override fun onMapStatusChanged(message: String) { tvMapStatus.text = message }
        })
        mapController.onCreate(savedInstanceState)
    }

    /**
     * 连接列表和按钮，业务修改均先落库再回显。
     * @return {Unit} 无返回值。
     */
    private fun setupListsAndButtons() {
        waypointAdapter = WaypointAdapter(waypoints) { point ->
            if (canEdit()) { mapController.moveTo(point.latitude, point.longitude); showWaypointEditor(point) }
        }
        findViewById<RecyclerView>(R.id.rvWaypointList).apply {
            layoutManager = LinearLayoutManager(this@RoutePlanningActivity); adapter = waypointAdapter
        }
        routeAdapter = RouteTabAdapter(
            onClick = { summary -> if (canEdit()) loadRoute(summary.route.id) },
            onLongClick = { summary -> if (canEdit()) showRouteEditor(summary.route) })
        findViewById<RecyclerView>(R.id.rvRouteTabs).apply {
            layoutManager = LinearLayoutManager(this@RoutePlanningActivity); adapter = routeAdapter
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnToggleSidebar).setOnClickListener {
            sidebar.visibility = if (sidebar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btnRetryMap).setOnClickListener { mapController.retryStyle() }
        findViewById<View>(R.id.btnCoordinatePoint).setOnClickListener { if (canEdit()) showWaypointEditor(null) }
        btnAdd.setOnClickListener { addPointMode = !addPointMode; updateControls() }
        findViewById<View>(R.id.btnErasePoint).setOnClickListener {
            if (canEdit() && waypoints.isNotEmpty()) commitWaypoints(waypoints.dropLast(1), "已删除最后一个航点")
        }
        findViewById<View>(R.id.btnNewRoute).setOnClickListener {
            if (canEdit()) perform({ pageState(repository.createRoute()) }) {
                undoWaypoints = null; applyPage(it); notifyUser("航线已新建")
            }
        }
        findViewById<View>(R.id.btnEditRoute).setOnClickListener {
            if (canEdit()) mission?.route?.let { showRouteEditor(it) }
        }
        findViewById<View>(R.id.btnOptimize).setOnClickListener { if (canEdit()) optimizeRoute() }
        btnUndo.setOnClickListener {
            undoWaypoints?.let { previous -> if (canEdit()) commitWaypoints(previous, "已撤销本次优化") }
        }
        findViewById<View>(R.id.btnSyncToMission).setOnClickListener { if (canEdit()) exportRoute() }
        btnAction.setOnClickListener { if (canEdit()) showMissionScenarios() }
        btnPause.setOnClickListener { perform({ runtime.togglePause() }) {} }
        btnCancel.setOnClickListener { confirmCancelOrClear() }
        findViewById<View>(R.id.btnMissionHistory).setOnClickListener { showHistory() }
        findViewById<View>(R.id.btnDeviceMission).setOnClickListener {
            AlertDialog.Builder(this).setTitle("真机执行尚未开放")
                .setMessage("当前仅支持本地模拟。真实航线执行需要兼容设备和正式空域规则，目前暂不可用。")
                .setPositiveButton("知道了", null).show()
        }
        updateControls()
    }

    /**
     * 对取消执行或清空航点要求明确确认，避免误触丢失编辑成果。
     * @return {Unit} 无返回值。
     */
    private fun confirmCancelOrClear() {
        if (runtime.state.value?.state?.isActive == true) {
            AlertDialog.Builder(this).setTitle("取消本地模拟？").setMessage("执行状态和事件将保留在执行记录中。")
                .setNegativeButton("继续执行", null).setPositiveButton("取消任务") { _, _ -> perform({ runtime.cancel() }) {} }.show()
        } else if (canEdit() && waypoints.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("清空当前航线航点？").setMessage("清空将保存到本地，不能撤销。")
                .setNegativeButton("返回", null).setPositiveButton("清空") { _, _ ->
                    commitWaypoints(emptyList(), "航点已清空")
                }.show()
        }
    }

    /**
     * 加载指定航线；编辑已自动保存，切换时不重复写入。
     * @param {String} routeId 航线稳定标识。
     * @return {Unit} 无返回值。
     */
    private fun loadRoute(routeId: String) = perform({
        pageState(repository.getRoute(routeId) ?: error("航线不存在"))
    }) {
        undoWaypoints = null; applyPage(it)
        waypoints.firstOrNull()?.let { point -> mapController.moveTo(point.latitude, point.longitude) }
    }

    /**
     * 查询当前页面所需的数据库快照。
     * @param {RouteMission} current 当前完整航线。
     * @return {Pair<RouteMission,List<RouteSummary>>} 航线与摘要列表。
     */
    private fun pageState(current: RouteMission): Pair<RouteMission, List<RouteSummary>> =
        current to repository.getRouteSummaries()

    /**
     * 把已提交的数据应用到页面，不发布尚未保存的候选内容。
     * @param {Pair<RouteMission,List<RouteSummary>>} state 最新航线和摘要。
     * @return {Unit} 无返回值。
     */
    private fun applyPage(state: Pair<RouteMission, List<RouteSummary>>) {
        // 地图样式和数据库加载没有固定先后顺序，任一先完成都要恢复初始航点视野。
        val shouldRestoreCamera = mission?.route?.id != state.first.route.id || waypoints.isEmpty()
        mission = state.first
        waypoints.clear(); waypoints.addAll(state.first.waypoints.map { it.copy() })
        getPreferences(MODE_PRIVATE).edit().putString("selected_route_id", state.first.route.id).apply()
        routeAdapter.submitList(state.second, state.first.route.id)
        waypointAdapter.notifyDataSetChanged()
        tvPosition.text = state.first.route.name + " · " + waypoints.size + "/99 点"
        tvCoordinate.text = String.format(Locale.CHINA, "相邻航段 %.1f 米 · 自动保存", GeoMath.routeDistanceMeters(waypoints))
        renderRoute(); updateControls()
        if (shouldRestoreCamera) waypoints.firstOrNull()?.let { mapController.moveTo(it.latitude, it.longitude) }
    }

    /**
     * 保存完整候选航点并回读数据库，失败时不修改缓存。
     * @param {List<Waypoint>} candidate 完整候选快照。
     * @param {String} message 成功提示。
     * @param {Function0<Unit>?} onSaved 保存成功后的弹窗关闭或撤销记录动作。
     * @return {Unit} 无返回值。
     */
    private fun commitWaypoints(candidate: List<Waypoint>, message: String, onSaved: (() -> Unit)? = null) {
        val route = mission?.route?.copy() ?: return
        val snapshot = candidate.mapIndexed { index, point -> point.copy(routeId = route.id, sequence = index + 1) }
        perform({
            repository.saveRoute(route, snapshot)
            pageState(repository.getRoute(route.id) ?: error("保存后无法读取航线"))
        }) {
            undoWaypoints = null; applyPage(it); onSaved?.invoke(); notifyUser(message)
        }
    }

    /**
     * 提供不依赖地图和定位权限的坐标编辑入口。
     * @param {Waypoint?} existing 待修改航点；null 表示新增。
     * @return {Unit} 非法参数不会关闭表单。
     */
    private fun showWaypointEditor(existing: Waypoint?) {
        val form = layoutInflater.inflate(R.layout.dialog_route_waypoint, null)
        val lat = form.findViewById<EditText>(R.id.etWaypointLatitude)
        val lon = form.findViewById<EditText>(R.id.etWaypointLongitude)
        val alt = form.findViewById<EditText>(R.id.etWaypointAltitude)
        existing?.let { lat.setText(it.latitude.toString()); lon.setText(it.longitude.toString()) }
        alt.setText((existing?.altitude ?: 30f).toString())
        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "坐标添加航点" else "编辑航点 " + existing.sequence)
            .setView(form).setNegativeButton("取消", null).setPositiveButton("保存", null)
        if (existing != null) builder.setNeutralButton("删除航点") { _, _ ->
            if (canEdit()) commitWaypoints(waypoints.filter { it.id != existing.id }, "航点已删除")
        }
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!canEdit()) return@setOnClickListener
            val latitude = lat.text.toString().trim().toDoubleOrNull()
            val longitude = lon.text.toString().trim().toDoubleOrNull()
            val altitude = alt.text.toString().trim().toFloatOrNull()
            if (latitude == null || longitude == null || altitude == null) {
                notifyUser("请输入有效的经度、纬度和高度"); return@setOnClickListener
            }
            val point = (existing ?: Waypoint(latitude = latitude, longitude = longitude))
                .copy(latitude = latitude, longitude = longitude, altitude = altitude)
            val candidate = if (existing == null) waypoints + point else waypoints.map {
                if (it.id == point.id) point else it.copy()
            }
            commitWaypoints(candidate, "航点已保存") { dialog.dismiss() }
        }
    }

    /**
     * 使用副本编辑航线信息，数据库失败时保留旧显示。
     * @param {RoutePlan} route 待编辑航线。
     * @return {Unit} 无返回值。
     */
    private fun showRouteEditor(route: RoutePlan) {
        val form = layoutInflater.inflate(R.layout.dialog_edit_route, null)
        val name = form.findViewById<EditText>(R.id.etRouteName).apply { setText(route.name) }
        val task = form.findViewById<EditText>(R.id.etTaskName).apply { setText(route.taskName) }
        val time = form.findViewById<EditText>(R.id.etTaskTime).apply { setText(route.taskTime) }
        val priority = form.findViewById<Spinner>(R.id.spinnerPriority)
        priority.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, RoutePriority.entries.map { it.displayName })
        priority.setSelection(route.priority.ordinal)
        val dialog = AlertDialog.Builder(this).setView(form).create()
        form.findViewById<View>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        form.findViewById<View>(R.id.btnDialogSave).setOnClickListener {
            if (!canEdit()) return@setOnClickListener
            val edited = route.copy(name = name.text.toString().trim(), taskName = task.text.toString().trim(),
                taskTime = time.text.toString().trim(), priority = RoutePriority.entries[priority.selectedItemPosition])
            if (edited.name.isBlank()) { notifyUser("航线名称不能为空"); return@setOnClickListener }
            val selectedId = mission?.route?.id ?: route.id
            perform({
                val saved = repository.getRoute(route.id) ?: error("航线不存在")
                repository.saveRoute(edited, saved.waypoints)
                pageState(repository.getRoute(selectedId) ?: error("航线不存在"))
            }) { applyPage(it); dialog.dismiss(); notifyUser("航线信息已保存") }
        }
        form.findViewById<View>(R.id.btnDialogDelete).setOnClickListener {
            if (canEdit()) AlertDialog.Builder(this).setTitle("删除“" + route.name + "”？")
                .setMessage("航点将一并删除，历史执行记录会保留。此操作不能撤销。")
                .setNegativeButton("返回", null).setPositiveButton("删除") { _, _ ->
                    val selectedId = mission?.route?.id.orEmpty()
                    perform({
                        repository.deleteRoute(route.id)
                        pageState(repository.getRoute(selectedId) ?: repository.loadOrCreateInitialRoute())
                    }) { undoWaypoints = null; applyPage(it); dialog.dismiss(); notifyUser("航线已删除") }
                }.show()
        }
        dialog.show()
    }

    /**
     * 比较本地优化前后距离，用户确认后保存，支持撤销本次优化。
     * @return {Unit} 无返回值；不调用在线 AI。
     */
    private fun optimizeRoute() {
        val current = mission ?: return
        if (waypoints.size < 2) { notifyUser("至少需要 2 个航点"); return }
        val previous = waypoints.map { it.copy() }
        perform({
            LocalNearestNeighborRouteOptimizer().optimizeRoute(current.route, previous).also {
                RouteSafetyValidator().requireSafe(it.waypoints)
            }
        }) { result ->
            AlertDialog.Builder(this).setTitle("本地最近邻优化")
                .setMessage(String.format(Locale.CHINA,
                    "保留起点，以相邻航段总距离为目标。\n优化前：%.1f 米\n优化后：%.1f 米\n保存后可撤销本次优化。",
                    GeoMath.routeDistanceMeters(previous), GeoMath.routeDistanceMeters(result.waypoints)))
                .setNegativeButton("取消", null).setPositiveButton("保存优化") { _, _ ->
                    if (canEdit()) commitWaypoints(result.waypoints, "本地优化结果已保存") {
                        undoWaypoints = previous; updateControls()
                    }
                }.show()
        }
    }

    /**
     * 复查安全规则并复用已有导出器，明确区分生成文件和设备同步。
     * @return {Unit} 无返回值。
     */
    private fun exportRoute() {
        val current = mission ?: return
        perform({
            RouteSafetyValidator().requireSafe(current.waypoints, forExecution = true)
            DjiWpmlFlightControlBridge(WpmlRouteExporter(File(filesDir, "missions")))
                .prepareMission(current.route.copy(), current.waypoints.map { it.copy() })
        }) { result ->
            if (!result.success) { notifyUser(result.message); return@perform }
            AlertDialog.Builder(this).setTitle("航线文件已生成")
                .setMessage(result.kmzFile?.name.orEmpty() + "\n文件已保存到本机。当前导出文件不包含完整的巡拍动作，请仅作航线预览使用。")
                .setPositiveButton("知道了", null).show()
        }
    }

    /**
     * 要求选择并确认本地场景，运行时重新查询已保存航线后启动。
     * @return {Unit} 无返回值。
     */
    private fun showMissionScenarios() {
        val current = mission ?: return
        if (waypoints.size < 2) { notifyUser("至少需要 2 个航点才能执行任务"); return }
        AlertDialog.Builder(this).setTitle("选择本地模拟场景")
            .setItems(MissionScenario.entries.map { it.label }.toTypedArray()) { _, index ->
                val scenario = MissionScenario.entries[index]
                AlertDialog.Builder(this).setTitle("开始" + scenario.label + "？")
                    .setMessage("航线：" + current.route.name + "\n航点：" + waypoints.size +
                        " 个\n本地模拟不连接无人机，不执行真实拍摄。执行进度和结果会保存到执行记录中。")
                    .setNegativeButton("取消", null).setPositiveButton("开始模拟") { _, _ ->
                        if (canEdit()) perform({ runtime.start(current.route.id, scenario) }) {
                            undoWaypoints = null; updateControls()
                        }
                    }.show()
            }.setNegativeButton("取消", null).show()
    }

    /**
     * 分页显示已落库的执行记录；空数据库不填充演示种子。
     * @param {Int} offset 当前页起点。
     * @return {Unit} 无返回值。
     */
    private fun showHistory(offset: Int = 0): Unit = perform({ runtime.history(offset) }) { runs ->
        val builder = AlertDialog.Builder(this).setTitle("本地模拟执行记录")
        if (runs.isEmpty()) builder.setMessage("暂无执行记录") else builder.setItems(runs.map {
            formatTime(it.startedAt) + "  " + it.routeName + "\n" + it.state.label + " · " + it.reachedPoints + "/" + it.totalPoints + " 点"
        }.toTypedArray()) { _, index -> showRunDetails(runs[index]) }
        if (runs.size == 50) builder.setNeutralButton("下一页") { _, _ -> showHistory(offset + 50) }
        builder.setPositiveButton("关闭", null).show()
    }

    /**
     * 显示持久化任务标识、来源和事件时间线。
     * @param {MissionRun} run 用户选择的执行记录。
     * @return {Unit} 无返回值。
     */
    private fun showRunDetails(run: MissionRun) = perform({ runtime.events(run.id) }) { events ->
        val details = "记录编号：" + run.id + "\n来源：本地模拟\n航线：" + run.routeName +
            "\n场景：" + run.scenario.label + "\n状态：" + run.state.label + "\n进度：" + run.reachedPoints +
            "/" + run.totalPoints + "\n开始：" + formatTime(run.startedAt) +
            (if (run.endedAt > 0) "\n结束：" + formatTime(run.endedAt) else "") + "\n\n" +
            events.joinToString("\n") { formatTime(it.occurredAt) + "  " + it.message }
        AlertDialog.Builder(this).setTitle("执行详情").setMessage(details).setPositiveButton("关闭", null).show()
    }

    /**
     * 用已提交的执行结果刷新进度，显示本地模拟数据来源。
     * @param {MissionRun?} run 最新执行记录；不存在时为 null。
     * @return {Unit} 无返回值。
     */
    private fun renderMissionRun(run: MissionRun?) {
        tvStatus.text = runtime.failure.value ?: run?.let {
            "本地模拟 · " + it.routeName + " · " + it.state.label + " · " + it.reachedPoints + "/" +
                it.totalPoints + " 点\n" + it.message
        } ?: "本地模拟未运行 · 执行记录会自动保存"
        renderRoute(); updateControls()
    }

    /**
     * 重绘航点和同航线的执行进度，其他航线的遥测不复用。
     * @return {Unit} 无返回值。
     */
    private fun renderRoute() {
        if (!::mapController.isInitialized) return
        val run = runtime.state.value?.takeIf { it.routeId == mission?.route?.id }
        mapController.renderWaypoints(waypoints)
        mapController.renderRoute(waypoints, run?.reachedPoints?.minus(1) ?: -1)
        if (run != null && run.state != MissionRunState.PREPARING && run.state != MissionRunState.UPLOADING)
            mapController.showAircraftPosition(run.latitude, run.longitude)
    }

    /**
     * 同步编辑锁、模拟控制和撤销按钮。
     * @return {Unit} 无返回值。
     */
    private fun updateControls() {
        val state = runtime.state.value?.state
        val active = state?.isActive == true
        val editable = !busy && !active && mission != null
        editingControls.forEach { it.isEnabled = editable }
        btnUndo.isEnabled = editable && undoWaypoints != null
        btnAction.isEnabled = editable
        btnCancel.isEnabled = !busy && (active || waypoints.isNotEmpty())
        btnCancel.text = if (active) "取消模拟任务" else "清空航点"
        btnPause.isEnabled = !busy && state in setOf(MissionRunState.RUNNING, MissionRunState.PAUSED)
        btnPause.text = if (state == MissionRunState.PAUSED) "继续模拟" else "暂停模拟"
        btnAdd.contentDescription = if (addPointMode) "关闭地图加点" else "开启地图加点"
        btnAdd.alpha = if (addPointMode) 1f else 0.45f
    }

    /**
     * 检查页面编辑锁，数据库事务内还会再次检查任务锁。
     * @return {Boolean} 当前是否允许编辑。
     */
    private fun canEdit(): Boolean = !busy && mission != null && runtime.state.value?.state?.isActive != true

    /**
     * 在 IO 线程执行操作，只有提交成功后才回显结果，失败时恢复地图。
     * @param {suspend Function0<T>} work 不访问 View 的后台业务操作。
     * @param {Function1<T,Unit>} success 主线程成功回显。
     * @return {Unit} 无返回值。
     */
    private fun <T> perform(work: suspend () -> T, success: (T) -> Unit) {
        if (busy) { notifyUser("正在处理，请稍候"); return }
        busy = true; updateControls()
        scope.launch {
            try { val result = withContext(Dispatchers.IO) { work() }; success(result) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("RoutePlanning", "航线操作失败", error)
                notifyUser(if (error is SQLiteException) "本地数据读写失败，请检查存储空间后重试" else error.message ?: "操作失败，请重试")
                renderRoute()
            }
            finally { busy = false; updateControls() }
        }
    }

    /**
     * 显示业务操作结果。
     * @param {String} message 面向用户的提示。
     * @return {Unit} 无返回值。
     */
    private fun notifyUser(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    /**
     * 按设备时区格式化执行记录时间。
     * @param {Long} timestamp Unix 毫秒时间戳。
     * @return {String} 可读时间。
     */
    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(timestamp))

    /**
     * 恢复地图运行状态。
     * @return {Unit} 无返回值。
     */
    override fun onResume() { super.onResume(); if (::mapController.isInitialized) mapController.onResume() }

    /**
     * 暂停地图渲染，应用级模拟任务继续持久化。
     * @return {Unit} 无返回值。
     */
    override fun onPause() { if (::mapController.isInitialized) mapController.onPause(); super.onPause() }

    /**
     * 通知地图进入可见生命周期。
     * @return {Unit} 无返回值。
     */
    override fun onStart() { super.onStart(); if (::mapController.isInitialized) mapController.onStart() }

    /**
     * 通知地图离开可见生命周期。
     * @return {Unit} 无返回值。
     */
    override fun onStop() { if (::mapController.isInitialized) mapController.onStop(); super.onStop() }

    /**
     * 保存地图视野；航线数据已经逐次持久化。
     * @param {Bundle} outState 系统恢复状态容器。
     * @return {Unit} 无返回值。
     */
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    /**
     * 等待页面在途 IO 结束再关闭连接，应用级模拟执行器独立存活。
     * @return {Unit} 无返回值。
     */
    override fun onDestroy() {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            CoroutineScope(Dispatchers.IO).launch { repository.close() }
        }
        scope.cancel()
        if (::mapController.isInitialized) mapController.onDestroy()
        super.onDestroy()
    }
}
