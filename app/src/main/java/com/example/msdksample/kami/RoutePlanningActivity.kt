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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.WellKnownTileServer
import com.mapbox.mapboxsdk.maps.MapView
import com.example.msdksample.R
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.NavPoint
import com.example.msdksample.drone.ObstacleAvoidanceMonitor
import com.example.msdksample.drone.SimulatedFlightEngine
import com.example.msdksample.drone.SimulatedObstacleFactory
import com.example.msdksample.kami.adapter.RouteTabAdapter
import com.example.msdksample.kami.adapter.WaypointAdapter
import com.example.msdksample.kami.data.RouteLimitExceededException
import com.example.msdksample.kami.data.RouteRepository
import com.example.msdksample.kami.map.RouteMapCallbacks
import com.example.msdksample.kami.map.RouteMapController
import com.example.msdksample.kami.map.dji.DjiV5MapLibreRouteMapController
import com.example.msdksample.kami.mission.AircraftTelemetry
import com.example.msdksample.kami.mission.DjiV5ReflectiveMissionGateway
import com.example.msdksample.kami.mission.DjiWpmlFlightControlBridge
import com.example.msdksample.kami.mission.FlightControlBridge
import com.example.msdksample.kami.mission.FlightProgressTracker
import com.example.msdksample.kami.mission.WpmlRouteExporter
import com.example.msdksample.kami.model.DefaultFlightZones
import com.example.msdksample.kami.model.FlightZone
import com.example.msdksample.kami.model.RouteMission
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RoutePriority
import com.example.msdksample.kami.model.RouteSummary
import com.example.msdksample.kami.model.Waypoint
import com.example.msdksample.kami.model.ZoneType
import com.example.msdksample.kami.optimization.HybridRouteOptimizer
import com.example.msdksample.kami.optimization.RouteOptimizer
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 航线规划页面，负责串联地图交互、航线数据库、航点编辑、航线优化与飞控任务交接。
 */
class RoutePlanningActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var sidebarPanel: View
    private lateinit var btnToggleSidebar: ImageButton
    private lateinit var btnAddPoint: ImageButton
    private lateinit var btnErasePoint: ImageButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnAction: MaterialButton
    private lateinit var btnNewRoute: MaterialButton
    private lateinit var btnEditRoute: MaterialButton
    private lateinit var btnOptimize: MaterialButton
    private lateinit var btnSyncToMission: MaterialButton
    private lateinit var rvWaypointList: RecyclerView
    private lateinit var rvRouteTabs: RecyclerView
    private lateinit var tvCurrentPosition: TextView
    private lateinit var tvCurrentCoord: TextView
    private lateinit var tvObstacleStatus: TextView

    private lateinit var mapController: RouteMapController
    private lateinit var routeRepository: RouteRepository
    private lateinit var routeOptimizer: RouteOptimizer
    private lateinit var flightControlBridge: FlightControlBridge
    private lateinit var flightProgressTracker: FlightProgressTracker
    private lateinit var waypointAdapter: WaypointAdapter
    private lateinit var routeTabAdapter: RouteTabAdapter

    private var currentRoute: RoutePlan? = null
    private val waypointList: MutableList<Waypoint> = mutableListOf()
    private val routeSummaries: MutableList<RouteSummary> = mutableListOf()
    private val activityScope = MainScope()
    private var isSidebarOpen = false
    private var isAddPointMode = true
    private var flownWaypointIndex = -1

    // ── 避障：航线阶段监控 + 模拟器模式演示 ─────────────────────
    private var simFlightEngine: SimulatedFlightEngine? = null
    private var simFlightJob: Job? = null
    private var obstacleQueryCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 MapLibre（必须在 inflate MapView 之前调用）
        Mapbox.getInstance(this, null, WellKnownTileServer.MapLibre)
        setContentView(R.layout.activity_route_planning)

        bindViews()
        setupServices()
        setupMap(savedInstanceState)
        setupRecyclerViews()
        setupButtons()
        loadInitialRoute()
    }

    override fun onResume() {
        super.onResume()
        mapController.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapController.onPause()
    }

    override fun onDestroy() {
        stopSimulatedDemo()
        activityScope.cancel()
        mapController.onDestroy()
        super.onDestroy()
    }

    /**
     * 绑定页面 XML 中的控件。
     * @returns {Unit} 无返回值；失败时 Android 会抛出明确的资源 ID 异常。
     */
    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        sidebarPanel = findViewById(R.id.sidebarPanel)
        btnToggleSidebar = findViewById(R.id.btnToggleSidebar)
        btnAddPoint = findViewById(R.id.btnAddPoint)
        btnErasePoint = findViewById(R.id.btnErasePoint)
        btnCancel = findViewById(R.id.btnCancel)
        btnAction = findViewById(R.id.btnAction)
        btnNewRoute = findViewById(R.id.btnNewRoute)
        btnEditRoute = findViewById(R.id.btnEditRoute)
        btnOptimize = findViewById(R.id.btnOptimize)
        btnSyncToMission = findViewById(R.id.btnSyncToMission)
        rvWaypointList = findViewById(R.id.rvWaypointList)
        rvRouteTabs = findViewById(R.id.rvRouteTabs)
        tvCurrentPosition = findViewById(R.id.tvCurrentPosition)
        tvCurrentCoord = findViewById(R.id.tvCurrentCoord)
        tvObstacleStatus = findViewById(R.id.tvObstacleStatus)
    }

    /**
     * 初始化页面依赖的业务服务。
     * @returns {Unit} 无返回值；服务对象保存在 Activity 成员变量中。
     */
    private fun setupServices() {
        routeRepository = RouteRepository(applicationContext)
        routeOptimizer = HybridRouteOptimizer()
        flightProgressTracker = FlightProgressTracker()
        val exporter = WpmlRouteExporter(File(filesDir, "missions"))
        val djiGateway = DjiV5ReflectiveMissionGateway()
        djiGateway.setTelemetryListener { telemetry ->
            runOnUiThread { handleAircraftTelemetry(telemetry) }
        }
        flightControlBridge = DjiWpmlFlightControlBridge(exporter, djiGateway)
    }

    /**
     * 初始化地图控制器并注册地图事件回调。
     * @param {Bundle?} savedInstanceState Activity 恢复地图状态所需的 Bundle。
     * @returns {Unit} 无返回值；地图异步就绪后刷新禁飞区和当前航线。
     */
    private fun setupMap(savedInstanceState: Bundle?) {
        mapController = DjiV5MapLibreRouteMapController(this, mapView)
        mapController.setCallbacks(object : RouteMapCallbacks {
            override fun onMapReady() {
                mapController.renderFlightZones(DefaultFlightZones.zones)
                checkLocationPermissionAndEnable()
                renderCurrentRoute()
            }

            override fun onMapPointSelected(latitude: Double, longitude: Double) {
                handleMapPointSelected(latitude, longitude)
            }

            override fun onWaypointDragged(
                waypointId: String,
                latitude: Double,
                longitude: Double
            ) {
                updateWaypointPosition(waypointId, latitude, longitude)
            }

            override fun onFlightZoneSelected(zone: FlightZone) {
                showZoneMessage(zone)
            }
        })
        mapController.onCreate(savedInstanceState)
    }

    /**
     * 初始化航线列表和航点列表。
     * @returns {Unit} 无返回值；列表点击会触发航线切换或地图定位。
     */
    private fun setupRecyclerViews() {
        waypointAdapter = WaypointAdapter(waypointList) { waypoint ->
            mapController.moveTo(waypoint.latitude, waypoint.longitude)
        }
        rvWaypointList.layoutManager = LinearLayoutManager(this)
        rvWaypointList.adapter = waypointAdapter

        routeTabAdapter = RouteTabAdapter(
            onClick = { summary -> switchRoute(summary.route.id) },
            onLongClick = { summary -> showRouteEditDialog(summary.route) }
        )
        rvRouteTabs.layoutManager = LinearLayoutManager(this)
        rvRouteTabs.adapter = routeTabAdapter
    }

    /**
     * 绑定页面按钮行为。
     * @returns {Unit} 无返回值；每个按钮入口都会进入真实数据保存或任务生成流程。
     */
    private fun setupButtons() {
        btnToggleSidebar.setOnClickListener {
            isSidebarOpen = !isSidebarOpen
            sidebarPanel.translationX =
                if (isSidebarOpen) 0f else -SIDEBAR_WIDTH_DP * resources.displayMetrics.density
        }

        btnAddPoint.setOnClickListener {
            isAddPointMode = !isAddPointMode
            updateAddPointModeUI()
        }

        btnErasePoint.setOnClickListener {
            removeLastWaypoint()
        }

        btnCancel.setOnClickListener {
            clearCurrentWaypoints()
        }

        btnAction.setOnClickListener {
            startAutomaticFlightTask()
        }

        btnNewRoute.setOnClickListener {
            createNewRoute()
        }

        btnEditRoute.setOnClickListener {
            currentRoute?.let { showRouteEditDialog(it) }
        }

        btnOptimize.setOnClickListener {
            optimizeCurrentRoute()
        }

        btnSyncToMission.setOnClickListener {
            prepareMissionFile()
        }
    }

    private fun loadInitialRoute() {
        val mission = routeRepository.loadOrCreateInitialRoute()
        applyMission(mission)
        refreshRouteSummaries()
    }

    private fun createNewRoute() {
        try {
            if (!saveCurrentRoute()) {
                return
            }
            val mission = routeRepository.createRoute()
            applyMission(mission)
            refreshRouteSummaries()
            Toast.makeText(this, "已新建 ${mission.route.name}", Toast.LENGTH_SHORT).show()
        } catch (error: RouteLimitExceededException) {
            Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchRoute(routeId: String) {
        val mission = routeRepository.getRoute(routeId) ?: return
        if (!saveCurrentRoute()) {
            return
        }
        applyMission(mission)
        refreshRouteSummaries()
    }

    private fun applyMission(mission: RouteMission) {
        currentRoute = mission.route
        waypointList.clear()
        waypointList.addAll(mission.waypoints.sortedBy { it.sequence })
        flownWaypointIndex = -1
        refreshWaypointSequences()
        updateInfoBar()
        updateWaypointListUI()
        renderCurrentRoute()
        currentRoute?.let { route ->
            routeTabAdapter.submitList(routeSummaries, route.id)
        }
    }

    private fun refreshRouteSummaries() {
        routeSummaries.clear()
        routeSummaries.addAll(routeRepository.getRouteSummaries())
        routeTabAdapter.submitList(routeSummaries, currentRoute?.id)
    }

    /**
     * 保存当前航线和航点到本地数据库。
     * @returns {Boolean} true 表示保存成功或当前无航线可保存；false 表示校验或数据库写入失败。
     */
    private fun saveCurrentRoute(): Boolean {
        val route = currentRoute ?: return true
        refreshWaypointSequences()
        return runCatching {
            routeRepository.saveRoute(route, waypointList)
            refreshRouteSummaries()
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                Toast.makeText(this, "保存航线失败: ${error.message}", Toast.LENGTH_LONG).show()
                false
            }
        )
    }

    private fun handleMapPointSelected(latitude: Double, longitude: Double) {
        val route = currentRoute ?: return
        if (!isAddPointMode) {
            return
        }

        if (waypointList.size >= MAX_WAYPOINTS) {
            Toast.makeText(this, "已达到最大航点数($MAX_WAYPOINTS)", Toast.LENGTH_SHORT).show()
            return
        }

        val zone = findZone(latitude, longitude)
        if (zone?.type == ZoneType.NO_FLY) {
            Toast.makeText(this, "此位置在禁飞区内，无法添加航点", Toast.LENGTH_SHORT).show()
            return
        }
        if (zone?.type == ZoneType.RESTRICTED) {
            Toast.makeText(this, "此位置在限高区内，请谨慎飞行", Toast.LENGTH_SHORT).show()
        }

        waypointList.add(
            Waypoint(
                routeId = route.id,
                sequence = waypointList.size + 1,
                latitude = latitude,
                longitude = longitude
            )
        )
        if (!saveCurrentRoute()) {
            waypointList.removeAt(waypointList.lastIndex)
            updateWaypointListUI()
            renderCurrentRoute()
            return
        }
        updateInfoBar()
        updateWaypointListUI()
        renderCurrentRoute()
        updateAddPointModeUI()
        Toast.makeText(this, "已添加航点 ${waypointList.size}", Toast.LENGTH_SHORT).show()
    }

    private fun updateWaypointPosition(
        waypointId: String,
        latitude: Double,
        longitude: Double
    ) {
        val waypoint = waypointList.firstOrNull { it.id == waypointId } ?: return
        val oldLatitude = waypoint.latitude
        val oldLongitude = waypoint.longitude
        waypoint.latitude = latitude
        waypoint.longitude = longitude
        if (!saveCurrentRoute()) {
            waypoint.latitude = oldLatitude
            waypoint.longitude = oldLongitude
            updateWaypointListUI()
            renderCurrentRoute()
            return
        }
        updateInfoBar()
        updateWaypointListUI()
        renderCurrentRoute()
    }

    private fun removeLastWaypoint() {
        if (waypointList.isEmpty()) {
            Toast.makeText(this, "没有可删除的航点", Toast.LENGTH_SHORT).show()
            return
        }
        val removed = waypointList.removeAt(waypointList.lastIndex)
        if (!saveCurrentRoute()) {
            waypointList.add(removed)
            updateWaypointListUI()
            renderCurrentRoute()
            return
        }
        updateInfoBar()
        updateWaypointListUI()
        renderCurrentRoute()
        updateAddPointModeUI()
        Toast.makeText(this, "已删除最后一个航点", Toast.LENGTH_SHORT).show()
    }

    private fun clearCurrentWaypoints() {
        if (waypointList.isEmpty()) {
            return
        }
        val previousWaypoints = waypointList.map { it.copy() }
        waypointList.clear()
        if (!saveCurrentRoute()) {
            waypointList.addAll(previousWaypoints)
            updateWaypointListUI()
            renderCurrentRoute()
            return
        }
        updateInfoBar()
        updateWaypointListUI()
        renderCurrentRoute()
        updateAddPointModeUI()
        Toast.makeText(this, "已清除当前航线航点", Toast.LENGTH_SHORT).show()
    }

    private fun optimizeCurrentRoute() {
        val route = currentRoute ?: return
        if (waypointList.size < 2) {
            Toast.makeText(this, "至少需要 2 个航点才能优化", Toast.LENGTH_SHORT).show()
            return
        }

        val result = routeOptimizer.optimizeRoute(route, waypointList)
        val previousWaypoints = waypointList.map { it.copy() }
        waypointList.clear()
        waypointList.addAll(result.waypoints)
        if (!saveCurrentRoute()) {
            waypointList.clear()
            waypointList.addAll(previousWaypoints)
            updateWaypointListUI()
            renderCurrentRoute()
            return
        }
        updateInfoBar()
        updateWaypointListUI()
        renderCurrentRoute()
        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
    }

    private fun prepareMissionFile() {
        val route = currentRoute ?: return
        val result = flightControlBridge.prepareMission(route, waypointList)
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
    }

    private fun startAutomaticFlightTask() {
        val route = currentRoute ?: return
        if (waypointList.size < 2) {
            Toast.makeText(this, R.string.obstacle_need_two_waypoints, Toast.LENGTH_SHORT).show()
            return
        }

        // 运行时模式分流：sim-only 演示分支放在 prepareMission 之前，
        // 不先走真机链路失败后再降级
        when (DroneManager.getInstance().getRuntimeMode()) {
            DroneManager.RuntimeMode.SIM_ONLY -> {
                startSimulatedObstacleDemo()
                return
            }
            DroneManager.RuntimeMode.OFFLINE -> {
                Toast.makeText(this, R.string.obstacle_not_connected, Toast.LENGTH_LONG).show()
                return
            }
            DroneManager.RuntimeMode.REAL -> Unit // 继续下方真机链路
        }

        val routeSnapshot = route.copy()
        val waypointSnapshot = waypointList.map { it.copy() }

        btnAction.isEnabled = false
        Toast.makeText(this, "正在生成并同步 DJI 航线任务", Toast.LENGTH_SHORT).show()

        activityScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                flightControlBridge.prepareMission(routeSnapshot, waypointSnapshot)
            }
            if (!prepared.success || prepared.kmzFile == null) {
                Toast.makeText(this@RoutePlanningActivity, prepared.message, Toast.LENGTH_LONG).show()
                btnAction.isEnabled = true
                return@launch
            }

            val launched = withContext(Dispatchers.IO) {
                flightControlBridge.startPreparedMission(prepared.kmzFile)
            }
            Toast.makeText(this@RoutePlanningActivity, launched.message, Toast.LENGTH_LONG).show()
            btnAction.isEnabled = true
        }
    }

    private fun showRouteEditDialog(route: RoutePlan) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_route, null)
        val etRouteName = dialogView.findViewById<EditText>(R.id.etRouteName)
        val etTaskName = dialogView.findViewById<EditText>(R.id.etTaskName)
        val etTaskTime = dialogView.findViewById<EditText>(R.id.etTaskTime)
        val spinnerPriority = dialogView.findViewById<Spinner>(R.id.spinnerPriority)
        val btnDialogCancel = dialogView.findViewById<MaterialButton>(R.id.btnDialogCancel)
        val btnDialogSave = dialogView.findViewById<MaterialButton>(R.id.btnDialogSave)
        val btnDialogDelete = dialogView.findViewById<MaterialButton>(R.id.btnDialogDelete)

        etRouteName.setText(route.name)
        etTaskName.setText(route.taskName)
        etTaskTime.setText(route.taskTime)

        val priorities = RoutePriority.entries.map { it.displayName }
        spinnerPriority.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            priorities
        )
        spinnerPriority.setSelection(route.priority.ordinal)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDialogSave.setOnClickListener {
            route.name = etRouteName.text.toString().ifBlank { route.name }
            route.taskName = etTaskName.text.toString()
            route.taskTime = etTaskTime.text.toString()
            route.priority = RoutePriority.fromDisplayName(spinnerPriority.selectedItem?.toString())
            val waypointsToSave = if (route.id == currentRoute?.id) {
                waypointList
            } else {
                routeRepository.getRoute(route.id)?.waypoints.orEmpty()
            }
            val saved = runCatching {
                routeRepository.saveRoute(route, waypointsToSave)
            }.fold(
                onSuccess = { true },
                onFailure = { error ->
                    Toast.makeText(this, "保存航线失败: ${error.message}", Toast.LENGTH_LONG).show()
                    false
                }
            )
            if (!saved) {
                return@setOnClickListener
            }
            if (route.id == currentRoute?.id) {
                currentRoute = route
            }
            refreshRouteSummaries()
            dialog.dismiss()
            Toast.makeText(this, "航线任务已保存", Toast.LENGTH_SHORT).show()
        }

        btnDialogDelete.setOnClickListener {
            dialog.dismiss()
            confirmDeleteRoute(route)
        }

        dialog.show()
    }

    private fun confirmDeleteRoute(route: RoutePlan) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val btnDeleteCancel = dialogView.findViewById<MaterialButton>(R.id.btnDeleteCancel)
        val btnDeleteConfirm = dialogView.findViewById<MaterialButton>(R.id.btnDeleteConfirm)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnDeleteCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDeleteConfirm.setOnClickListener {
            val deletedCurrentRoute = route.id == currentRoute?.id
            routeRepository.deleteRoute(route.id)
            if (deletedCurrentRoute) {
                val nextMission = routeRepository.getRouteSummaries()
                    .firstOrNull()
                    ?.let { routeRepository.getRoute(it.route.id) }
                    ?: routeRepository.loadOrCreateInitialRoute()
                applyMission(nextMission)
            }
            refreshRouteSummaries()
            dialog.dismiss()
            Toast.makeText(this, "航线已删除", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun renderCurrentRoute() {
        if (!::mapController.isInitialized) {
            return
        }
        mapController.renderWaypoints(waypointList)
        mapController.renderRoute(waypointList, flownWaypointIndex)
    }

    /**
     * 处理 DJI V5 飞机遥测位置，更新飞机标记和已飞/未飞航段。
     * @param {AircraftTelemetry} telemetry DJI V5 回传的飞机经纬度、高度、航向与时间戳。
     * @returns {Unit} 无返回值；内部会刷新地图上的飞机位置和航线分段。
     */
    private fun handleAircraftTelemetry(telemetry: AircraftTelemetry) {
        mapController.showAircraftPosition(telemetry.latitude, telemetry.longitude)
        flownWaypointIndex = flightProgressTracker.resolveFlownWaypointIndex(
            waypoints = waypointList,
            telemetry = telemetry,
            currentFlownIndex = flownWaypointIndex
        )
        mapController.renderRoute(waypointList, flownWaypointIndex)

        // REAL 模式：周期采样飞控主动避障状态用于 UI 上报（不干预 DJI 自主避障）
        if (DroneManager.getInstance().getRuntimeMode() == DroneManager.RuntimeMode.REAL &&
            obstacleQueryCounter++ % 10 == 0
        ) {
            DroneManager.getInstance().queryObstacleAvoidingState { avoiding ->
                runOnUiThread {
                    tvObstacleStatus.text = when (avoiding) {
                        true -> getString(R.string.obstacle_status_dji_active)
                        false -> getString(R.string.obstacle_status_idle)
                        null -> tvObstacleStatus.text
                    }
                }
            }
        }
    }

    // ── 避障：模拟器模式（SIM_ONLY）演示 ──────────────────────────

    /** 模拟器模式下演示“航线自动飞行 + 自动避障”完整闭环 */
    private fun startSimulatedObstacleDemo() {
        // 防重入：已有演示运行则忽略
        if (simFlightEngine?.isRunning == true) return
        if (waypointList.size < 2) {
            Toast.makeText(this, R.string.obstacle_need_two_waypoints, Toast.LENGTH_SHORT).show()
            return
        }

        val navPoints = waypointList.map { NavPoint(it.latitude, it.longitude, it.altitude) }
        val monitor = ObstacleAvoidanceMonitor()
        val engine = SimulatedFlightEngine(
            waypoints = navPoints,
            monitor = monitor,
            telemetryListener = { telemetry ->
                runOnUiThread { handleAircraftTelemetry(telemetry) }
            },
            statusListener = { msg ->
                runOnUiThread { tvObstacleStatus.text = msg }
            }
        )
        engine.configure(
            DroneManager.getInstance().getObstacleActionSetting(),
            SimulatedObstacleFactory.createDemoObstacles(navPoints)
        )
        simFlightEngine = engine

        btnAction.isEnabled = false
        tvObstacleStatus.text = getString(R.string.obstacle_status_demo_started)
        engine.start()
        simFlightJob = activityScope.launch {
            while (engine.isRunning) {
                engine.tick()
                delay(1000L)
            }
            btnAction.isEnabled = true
            if (engine.isFinished) {
                tvObstacleStatus.text = getString(R.string.obstacle_status_demo_finished)
            }
        }
    }

    /** 停止模拟避障演示并复位状态（onDestroy / 取消时调用） */
    private fun stopSimulatedDemo() {
        simFlightJob?.cancel()
        simFlightJob = null
        simFlightEngine?.stop()
        simFlightEngine = null
        if (::tvObstacleStatus.isInitialized) {
            tvObstacleStatus.text = getString(R.string.obstacle_status_idle)
        }
    }

    private fun updateInfoBar() {
        if (waypointList.isEmpty()) {
            tvCurrentPosition.text = "未设置航点"
            tvCurrentCoord.text = "--"
        } else {
            val last = waypointList.last()
            tvCurrentPosition.text = getString(R.string.current_waypoint_format, waypointList.size)
            tvCurrentCoord.text =
                String.format(Locale.US, "(%.6f, %.6f)", last.latitude, last.longitude)
        }
    }

    private fun updateWaypointListUI() {
        waypointAdapter.notifyDataSetChanged()
    }

    private fun updateAddPointModeUI() {
        if (isAddPointMode) {
            btnAddPoint.setColorFilter(android.graphics.Color.parseColor("#5B8FF9"))
            btnErasePoint.visibility = View.GONE
        } else {
            btnAddPoint.clearColorFilter()
            btnErasePoint.visibility =
                if (waypointList.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun refreshWaypointSequences() {
        waypointList.forEachIndexed { index, waypoint ->
            waypoint.routeId = currentRoute?.id.orEmpty()
            waypoint.sequence = index + 1
        }
    }

    private fun findZone(latitude: Double, longitude: Double): FlightZone? {
        return DefaultFlightZones.zones.firstOrNull { it.contains(latitude, longitude) }
    }

    private fun showZoneMessage(zone: FlightZone) {
        if (!isAddPointMode) {
            return
        }

        when (zone.type) {
            ZoneType.NO_FLY -> Toast.makeText(this, "此位置在禁飞区内，无法添加航点", Toast.LENGTH_SHORT).show()
            ZoneType.RESTRICTED -> Toast.makeText(this, "此位置在限高区内，请谨慎飞行", Toast.LENGTH_SHORT).show()
            ZoneType.SAFE -> Toast.makeText(this, "此区域为可飞区", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermissionAndEnable() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mapController.enableMyLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mapController.enableMyLocation()
            } else {
                Toast.makeText(this, "定位权限被拒绝，无法显示当前位置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val MAX_WAYPOINTS = 99
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val SIDEBAR_WIDTH_DP = 224f
    }
}
