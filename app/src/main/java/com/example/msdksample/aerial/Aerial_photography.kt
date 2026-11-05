package com.example.msdksample.aerial
import android.content.Intent import android.net.Uri import android.os.Bundle import android.os.Environment import android.os.Handler import android.os.Looper import android.provider.MediaStore import android.util.Log import android.view.MotionEvent import android.view.View import android.widget.Toast import androidx.activity.enableEdgeToEdge import androidx.activity.result.contract.ActivityResultContracts import androidx.appcompat.app.AlertDialog import androidx.appcompat.app.AppCompatActivity import androidx.core.content.FileProvider import androidx.core.view.ViewCompat import androidx.core.view.WindowInsetsCompat import com.example.msdksample.databinding.ActivityAerialPhotographyBinding import com.permissionx.guolindev.PermissionX import java.io.File import java.io.IOException import java.text.SimpleDateFormat import java.util.Date import java.util.Locale import com.example.msdksample.R
import com.example.msdksample.asuka.MainActivity
import com.example.msdksample.asuka.data.InspectionRepository
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.os.SystemClock
import android.view.SurfaceView
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.msdksample.asuka.data.InspectionTask
import com.example.msdksample.kami.RoutePlanningActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.msdksample.drone.command.BasicFlightAction
import com.example.msdksample.drone.command.SimulatedFlightCommandExecutor
import com.example.msdksample.aerial.camera.CameraAction
import com.example.msdksample.aerial.camera.CameraOperationController
import com.example.msdksample.aerial.camera.DjiCameraGateway
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.TelemetryDisplay
import com.example.msdksample.drone.TelemetryUiObserver
import com.example.msdksample.drone.state.DeviceStateSubscription
import com.example.msdksample.aerial.inspection.DjiInspectionStream
import com.example.msdksample.aerial.inspection.FrameFilterDecision
import com.example.msdksample.aerial.inspection.InspectionFrameFilter
import com.example.msdksample.aerial.inspection.RgbaFrameEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class Aerial_photography : AppCompatActivity() {
    private lateinit var binding: ActivityAerialPhotographyBinding
    private val handler = Handler(Looper.getMainLooper())
    private var telemetrySubscription: DeviceStateSubscription? = null

    private fun renderFlightTelemetry(sample: TelemetryDisplay) {
        fun value(number: Float?) = number?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "--"
        binding.tvHeight.text = "${value(sample.altitudeMeters)}m"
        binding.tvDistance.text = "${value(sample.distanceMeters)}m"
        binding.tvSpeed.text = "${value(sample.speedMetersPerSecond)}m/s"
        binding.tvBattery.text = sample.batteryPercent?.let { "$it%" } ?: "--%"
        binding.tvTelemetrySource.text = "${sample.sourceLabel} · 信号${sample.signalPercent?.let { "$it%" } ?: "--%"} · 画面为演示"
    }

    override fun onStart() {
        super.onStart()
        if (::binding.isInitialized) telemetrySubscription = TelemetryUiObserver(DroneManager.getInstance().deviceStateProvider) { _, display ->
            if (!isDestroyed) renderFlightTelemetry(display)
        }
    }

    override fun onStop() {
        telemetrySubscription?.cancel()
        telemetrySubscription = null
        super.onStop()
    }
    private var isControlMode = false
    private var currentPhotoUri: Uri? = null
    private var isListening = false
    private var isVoicePressActive = false
    private var cancelVoiceUpload = false
    private var voiceRecorder: OnlinePttRecorder? = null
    private var voiceMaxDurationRunnable: Runnable? = null
    private var isAnalyzingIntent = false
    private var llmTimeoutRunnable: Runnable? = null
    private var inspectionStream: DjiInspectionStream? = null
    private val inspectionFrameFilter = InspectionFrameFilter()
    private val inspectionExecutor = Executors.newSingleThreadExecutor()
    private val inspectionInFlight = AtomicBoolean(false)
    private val hasReceivedInspectionFrame = AtomicBoolean(false)

    private var isVideoRecording = false
    private val simulatedFlight = SimulatedFlightCommandExecutor()
    private fun executeSimulatedFlight(action: BasicFlightAction): String {
        val owner=com.example.msdksample.data.remote.AccountIdentity.owner(applicationContext)
        val result=simulatedFlight.executeResult(action)
        val label=when(action) {
            BasicFlightAction.TAKEOFF -> "模拟起飞"; BasicFlightAction.LAND -> "模拟降落"
            BasicFlightAction.RETURN_HOME -> "模拟返航"; BasicFlightAction.HOVER -> "模拟悬停"
        }
        runCatching { com.example.msdksample.data.local.LocalDataStore.getInstance(applicationContext)
            .record("指令",label,if(result.accepted) "模拟完成" else "已拒绝","本地演示",result.message,
                owner=owner,sourceCode="LOCAL_SIMULATION") }
            .onFailure { android.util.Log.w("F21","保存模拟指令日志失败") }
        return result.message
    }
    private val cameraController = CameraOperationController(DjiCameraGateway())

    /** 本页日志标签 */
    private val TAG = "Aerial_photography"

    /** 当前位置名称，后续可接入真实定位数据动态更新 */
    private var currentLocationName: String = "张江A区"

    /** 巡检任务仓库，与 asuka 模块共享同一 Room 数据库 */
    private lateinit var inspectionRepository: InspectionRepository

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                handleUploadedFile(uri, "照片")
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleUploadedFile(it, "图片") }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleUploadedFile(it, "文件") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            binding = ActivityAerialPhotographyBinding.inflate(layoutInflater)
            setContentView(binding.root)

            hideSystemUI()

            // 动态设置位置名称（后续接入真实定位数据后替换 currentLocationName 即可）
            binding.tvLocation.text = currentLocationName

            // 初始化巡检任务仓库（与 asuka 模块共享 inspection_db 数据库）
            inspectionRepository = InspectionRepository(this)
            lifecycleScope.launch {
                inspectionRepository.initializeData()
            }

            binding.flVideoPreview.setOnClickListener {
                if (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0) {
                    showSystemUI()
                } else {
                    hideSystemUI()
                }
            }

            initInspectionStream()

            setupListeners()
            setupQuickCommandListeners()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        cancelVoiceUpload = true
        voiceRecorder?.stop()
        voiceMaxDurationRunnable?.let { handler.removeCallbacks(it) }
        llmTimeoutRunnable?.let { handler.removeCallbacks(it) }
        inspectionStream?.release()
        inspectionExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
    }

    private fun initInspectionStream() {
        val surfaceView = SurfaceView(this)
        binding.ivVideoFeed.visibility = View.GONE
        binding.flVideoPreview.setBackgroundColor(Color.BLACK)
        binding.flVideoPreview.addView(
            surfaceView,
            0,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        binding.tvAiDetectionStatus.text = "等待 DJI 图传"
        binding.tvAiDetectionDetail.text = "连接飞机后将筛选画面并上传云端复核"
        inspectionStream = DjiInspectionStream(
            surfaceView,
            ::onInspectionRgbaFrame
        ) {
            runOnUiThread {
                updateInspectionStatus("图传初始化失败", "请检查 DJI SDK 与相机连接")
            }
        }
    }

    private fun onInspectionRgbaFrame(frameData: ByteArray, offset: Int, length: Int, width: Int, height: Int) {
        if (hasReceivedInspectionFrame.compareAndSet(false, true)) {
            runOnUiThread {
                updateInspectionStatus("图传已连接", "正在筛选画面")
            }
        }
        val decision = inspectionFrameFilter.evaluate(
            frameData,
            offset,
            length,
            width,
            height,
            SystemClock.elapsedRealtime(),
            inspectionInFlight.get()
        )
        if (decision != FrameFilterDecision.ACCEPT || !inspectionInFlight.compareAndSet(false, true)) return

        if (!LLMService.isCloudConfigured()) {
            inspectionInFlight.set(false)
            updateInspectionStatus("云端服务未配置", "请配置后端 HTTPS 地址与访问令牌")
            return
        }
        if (!NetworkUtil.isOnline(this)) {
            inspectionInFlight.set(false)
            updateInspectionStatus("网络不可用", "巡检图像需要联网后才能分析")
            return
        }

        val frameSize = width.toLong() * height * 4
        if (frameSize > Int.MAX_VALUE) {
            inspectionInFlight.set(false)
            updateInspectionStatus("图像格式无效", "未上传该画面")
            return
        }
        val rgbaCopy = frameData.copyOfRange(offset, offset + frameSize.toInt())
        updateInspectionStatus("云端复核中", "画面已筛选，正在上传分析")
        try {
            inspectionExecutor.execute {
                val jpeg = RgbaFrameEncoder.toJpeg(rgbaCopy, 0, rgbaCopy.size, width, height)
                if (jpeg == null) {
                    inspectionInFlight.set(false)
                    updateInspectionStatus("图像压缩失败", "已跳过当前画面")
                    return@execute
                }
                LLMService.analyzeInspection(jpeg) { result ->
                    inspectionInFlight.set(false)
                    runOnUiThread {
                        result.fold(
                            onSuccess = ::showInspectionFinding,
                            onFailure = { updateInspectionStatus("云端服务不可用", it.message ?: "请稍后重试") }
                        )
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            inspectionInFlight.set(false)
        }
    }

    private fun showInspectionFinding(finding: InspectionFinding) {
        val status = when (finding.status) {
            "suspected_anomaly" -> "疑似异常，需人工复核"
            "no_obvious_anomaly" -> "未发现明显异常"
            else -> "画面证据不足，需人工复核"
        }
        val detail = listOf(finding.title, finding.description)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")
        updateInspectionStatus(status, detail.ifBlank { "模型没有返回可复核的描述" })
    }

    private fun updateInspectionStatus(status: String, detail: String) {
        if (!::binding.isInitialized || isFinishing || isDestroyed) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            binding.tvAiDetectionStatus.text = status
            binding.tvAiDetectionDetail.text = detail
        } else {
            runOnUiThread { updateInspectionStatus(status, detail) }
        }
    }

    private fun setupListeners() {
        binding.cvClose.setOnClickListener {
            finish()
        }

        binding.cvTaskList.setOnClickListener {
            toggleTaskSidebar()
        }

        binding.cvRouteSelect.setOnClickListener {
            toggleRouteSidebar()
        }

        binding.ivCloseTask.setOnClickListener {
            hideAllSidebars()
        }

        binding.ivCloseRoute.setOnClickListener {
            hideAllSidebars()
        }

        binding.scrimOverlay.setOnClickListener {
            hideAllSidebars()
        }

        binding.cvRecord.setOnClickListener {
            if (isVideoRecording) {
                stopVideoRecording()
            } else {
                startVideoRecording()
            }
        }

        binding.cvCamera.setOnClickListener {
            captureDronePhoto()
        }

        binding.cvModeToggle.setOnClickListener {
            toggleControlMode()
        }

        binding.cvAddAttachment.setOnClickListener {
            requestPermissionsAndShowUploadOptions()
        }

        binding.ivCloseUpload.setOnClickListener {
            hideUploadDialog()
        }

        binding.clUploadDialog.setOnClickListener {
            hideUploadDialog()
        }

        binding.cvUploadCamera.setOnClickListener {
            hideUploadDialog()
            requestCameraAndTakePicture()
        }

        binding.cvUploadImage.setOnClickListener {
            hideUploadDialog()
            pickImageLauncher.launch("image/*")
        }

        binding.cvUploadFile.setOnClickListener {
            hideUploadDialog()
            pickFileLauncher.launch("*/*")
        }

        binding.cvVoice.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isVoicePressActive || isListening || isAnalyzingIntent) return@setOnTouchListener true
                    isVoicePressActive = true
                    cancelVoiceUpload = false
                    startVoiceInput()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isVoicePressActive = false
                    stopVoiceInput()
                    true
                }
                else -> false
            }
        }

        binding.etCommandInput.setOnClickListener {
            showQuickCommandDialog()
        }

        binding.cvLocation.setOnClickListener {
            startActivity(Intent(this, RoutePlanningActivity::class.java))
        }

        // 「查看全部任务」→ 跳转到 asuka 巡检任务中心
        binding.btnViewAllTasks.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            hideAllSidebars()
        }
    }

    private fun toggleTaskSidebar() {
        if (binding.clTaskSidebar.visibility == View.VISIBLE) {
            binding.scrimOverlay.visibility = View.GONE
            binding.clTaskSidebar.visibility = View.GONE
        } else {
            binding.scrimOverlay.visibility = View.VISIBLE
            binding.clTaskSidebar.visibility = View.VISIBLE
            binding.clRouteSidebar.visibility = View.GONE
            loadTaskList()
        }
    }

    /**
     * 从 InspectionRepository 加载今日任务并动态渲染到侧边栏。
     * 数据源与 asuka 巡检模块共享同一 Room 数据库 (inspection_db)。
     */
    private fun loadTaskList() {
        lifecycleScope.launch {
            val dateGroups = withContext(Dispatchers.IO) {
                inspectionRepository.getAllDateGroups()
            }
            val tasks = dateGroups.flatMap { group ->
                group.tasks.map { it to group.date }
            }
            renderTaskCards(tasks)
        }
    }

    private fun renderTaskCards(tasks: List<Pair<InspectionTask, String>>) {
        val container = binding.llTaskList
        container.removeAllViews()

        if (tasks.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "暂无任务数据\n点击下方按钮前往任务中心创建"
                textSize = 10f
                setTextColor(Color.parseColor("#80FFFFFF"))
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            container.addView(emptyView)
            return
        }

        tasks.take(5).forEach { (task, date) ->
            val card = buildTaskCard(task, date)
            container.addView(card)
        }
    }

    private fun buildTaskCard(task: InspectionTask, date: String): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            setCardBackgroundColor(Color.parseColor("#0DFFFFFF"))
            radius = dpToPx(12).toFloat()
            cardElevation = 0f
            isClickable = true
            setOnClickListener {
                // 点击单个任务 → 跳转到巡检任务中心
                startActivity(Intent(this@Aerial_photography, MainActivity::class.java))
            }
        }

        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        // 标题行：任务名称 + 状态标签
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = task.name
            setTextColor(Color.WHITE)
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(titleText)

        val statusBadge = TextView(this).apply {
            text = task.status
            setTextColor(when (task.status) {
                "Urgent" -> Color.parseColor("#EF4444")
                "High" -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#10B981")
            })
            textSize = 8f
        }
        titleRow.addView(statusBadge)
        innerLayout.addView(titleRow)

        // 副标题：航线名称
        innerLayout.addView(TextView(this).apply {
            text = "航线: ${task.routeName}"
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 8f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
        })

        // 详情行：时间 + 媒体统计
        val detailRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val timeText = "$date  ${task.time}"
        detailRow.addView(TextView(this).apply {
            text = if (task.time.isNotBlank()) timeText else date
            setTextColor(Color.parseColor("#66FFFFFF"))
            textSize = 8f
        })

        if (task.videos > 0 || task.images > 0 || task.defects > 0) {
            detailRow.addView(TextView(this).apply {
                text = buildString {
                    if (task.videos > 0) append("  ${task.videos}段视频")
                    if (task.images > 0) append("  ${task.images}张图片")
                    if (task.defects > 0) append("  ${task.defects}个隐患")
                }
                setTextColor(Color.parseColor("#66FFFFFF"))
                textSize = 8f
            })
        }
        innerLayout.addView(detailRow)

        card.addView(innerLayout)
        return card
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun toggleRouteSidebar() {
        if (binding.clRouteSidebar.visibility == View.VISIBLE) {
            binding.scrimOverlay.visibility = View.GONE
            binding.clRouteSidebar.visibility = View.GONE
        } else {
            binding.scrimOverlay.visibility = View.VISIBLE
            binding.clRouteSidebar.visibility = View.VISIBLE
            binding.clTaskSidebar.visibility = View.GONE
            renderRouteTabs()
        }
    }

    private fun renderRouteTabs() {
        binding.llRouteList.removeAllViews()

        val routes = listOf(
            Route(1, "航线 1", "变电站设备检查", 5, true, false),
            Route(2, "航线 2", "", 0, false, true),
            Route(3, "航线 3", "", 0, false, true)
        )

        routes.forEach { route ->
            val routeView = layoutInflater.inflate(R.layout.item_route, binding.llRouteList, false)
            val tvRouteName = routeView.findViewById<android.widget.TextView>(R.id.tvRouteName)
            val tvWaypoints = routeView.findViewById<android.widget.TextView>(R.id.tvWaypoints)
            val tvTask = routeView.findViewById<android.widget.TextView>(R.id.tvTask)

            tvRouteName.text = route.name
            tvWaypoints.text = "${route.waypoints} 个航点"
            tvTask.text = if (route.taskName.isNotEmpty()) route.taskName else ""

            routeView.setOnClickListener {
                if (!route.isLocked) {
                    showToast("已切换到 ${route.name}")
                } else {
                    showToast("该航线尚未解锁")
                }
            }

            binding.llRouteList.addView(routeView)
        }
    }

    private fun toggleControlMode() {
        isControlMode = !isControlMode

        if (isControlMode) {
            binding.tvModeToggle.text = "自动"
            binding.cvModeToggle.setCardBackgroundColor(getColor(R.color.mode_active))
            showToast("已切换到自动模式")
        } else {
            binding.tvModeToggle.text = "接管"
            binding.cvModeToggle.setCardBackgroundColor(getColor(R.color.mode_inactive))
            showToast("已切换到手动模式")
        }
    }

    private fun startVoiceInput() {
        if (!NetworkUtil.isOnline(this)) {
            isVoicePressActive = false
            showToast("语音识别必须联网，请连接网络后重试")
            return
        }
        if (!LLMService.isCloudConfigured()) {
            isVoicePressActive = false
            showToast("请先配置 HTTPS 后端地址与访问令牌")
            return
        }

        PermissionX.init(this)
            .permissions(android.Manifest.permission.RECORD_AUDIO)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "需要录音权限才能使用语音输入",
                    "允许",
                    "拒绝"
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "请在设置中手动开启录音权限",
                    "去设置",
                    "取消"
                )
            }
            .request { allGranted, _, _ ->
                if (allGranted && isVoicePressActive) {
                    beginVoiceRecording()
                } else {
                    if (!allGranted) showToast("需要录音权限才能使用语音输入")
                }
            }
    }

    private fun beginVoiceRecording() {
        val recorder = OnlinePttRecorder(this) { result ->
            voiceRecorder = null
            isListening = false
            if (cancelVoiceUpload || isDestroyed) {
                result.getOrNull()?.delete()
                cancelVoiceUpload = false
                return@OnlinePttRecorder
            }
            result.fold(
                onSuccess = ::uploadVoiceForAnalysis,
                onFailure = { showToast("没有录到有效语音，请按住按钮后再说话") }
            )
        }
        if (!recorder.start()) {
            isVoicePressActive = false
            showToast("无法启动录音，请检查麦克风权限和设备状态")
            return
        }

        voiceRecorder = recorder
        isListening = true
        binding.cvVoice.alpha = 0.7f
        showToast("正在录音，松开后联网识别")
        voiceMaxDurationRunnable = Runnable {
            if (isListening) {
                isVoicePressActive = false
                showToast("录音已达到 10 秒上限，正在上传")
                stopVoiceInput()
            }
        }
        handler.postDelayed(voiceMaxDurationRunnable!!, 10_000)
    }

    private fun stopVoiceInput() {
        binding.cvVoice.alpha = 1.0f
        voiceMaxDurationRunnable?.let { handler.removeCallbacks(it) }
        voiceMaxDurationRunnable = null
        if (isListening) {
            isListening = false
            voiceRecorder?.stop()
        }
    }

    private fun uploadVoiceForAnalysis(audioFile: File) {
        if (!NetworkUtil.isOnline(this)) {
            audioFile.delete()
            showToast("网络已断开，语音未识别；请联网后重新录制")
            return
        }
        isAnalyzingIntent = true
        showToast("正在上传语音并调用云端 AI 分析")
        val completed = AtomicBoolean(false)
        llmTimeoutRunnable = Runnable {
            if (completed.compareAndSet(false, true)) {
                isAnalyzingIntent = false
                showToast("云端语音处理超时，请检查网络后重试")
            }
        }
        handler.postDelayed(llmTimeoutRunnable!!, LLMConfig.INTENT_TIMEOUT * 1000)
        LLMService.analyzeVoice(audioFile) { result ->
            audioFile.delete()
            runOnUiThread {
                if (isDestroyed || !completed.compareAndSet(false, true)) return@runOnUiThread
                llmTimeoutRunnable?.let { handler.removeCallbacks(it) }
                isAnalyzingIntent = false
                when (result) {
                    is VoiceAnalysisResult.Success -> {
                        binding.etCommandInput.setText(result.transcript)
                        showFlightIntentConfirmDialog(result.intent, result.summary)
                    }
                    is VoiceAnalysisResult.Unknown -> {
                        binding.etCommandInput.setText(result.transcript)
                        AlertDialog.Builder(this)
                            .setTitle("无法识别飞行意图")
                            .setMessage("${result.reason}\n\n请修改指令后重试。")
                            .setPositiveButton("知道了", null)
                            .setCancelable(false)
                            .show()
                    }
                    is VoiceAnalysisResult.Error -> showToast(result.message)
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hideAllSidebars() {
        binding.scrimOverlay.visibility = View.GONE
        binding.clRouteSidebar.visibility = View.GONE
        binding.clTaskSidebar.visibility = View.GONE
    }

    private fun showQuickCommandDialog() {
        binding.clQuickCommand.visibility = View.VISIBLE
        binding.clCustomCommandDialog.visibility = View.GONE
    }

    private fun hideQuickCommandDialog() {
        binding.clQuickCommand.visibility = View.GONE
        binding.clCustomCommandDialog.visibility = View.GONE
        binding.etCustomCommand.text.clear()
        hideKeyboard()
    }

    private fun showCustomCommandDialog() {
        binding.clCustomCommandDialog.visibility = View.VISIBLE
        binding.etCustomCommand.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.etCustomCommand, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideCustomCommandDialog() {
        binding.clCustomCommandDialog.visibility = View.GONE
        binding.etCustomCommand.text.clear()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etCustomCommand.windowToken, 0)
    }

    // ==================== MSDK 实际调用 ====================

    private fun captureDronePhoto() {
        executeCameraAction(CameraAction.PHOTO)
    }

    private fun startVideoRecording() {
        executeCameraAction(CameraAction.START_RECORD)
    }

    private fun stopVideoRecording() {
        executeCameraAction(CameraAction.STOP_RECORD)
    }

    private fun executeCameraAction(action: CameraAction) {
        cameraController.execute(action) { result ->
            runOnUiThread {
                isVideoRecording = cameraController.state == com.example.msdksample.aerial.camera.CameraState.RECORDING
                binding.cvRecord.setCardBackgroundColor(
                    getColor(if (isVideoRecording) R.color.mode_active else R.color.mode_inactive)
                )
                val message = if (result.status == com.example.msdksample.aerial.camera.CameraOperationStatus.SUCCEEDED) {
                    when (action) {
                        CameraAction.PHOTO -> "拍照成功"
                        CameraAction.START_RECORD -> "开始录像"
                        CameraAction.STOP_RECORD -> "停止录像"
                    }
                } else result.message
                showToast(message)
            }
        }
    }

    private fun showCommandConfirmDialog(command: String) {
        if (isAnalyzingIntent) return

        if (!NetworkUtil.isOnline(this)) {
            showToast("飞行意图分析必须联网，请连接网络后重试")
            return
        }
        if (!LLMService.isCloudConfigured()) {
            showToast("请先配置 HTTPS 后端地址与访问令牌")
            return
        }
        analyzeWithLLM(command)
    }

    private fun analyzeWithLLM(command: String) {
        isAnalyzingIntent = true
        showToast("正在分析飞行意图...")

        val isCompleted = AtomicBoolean(false)

        llmTimeoutRunnable = Runnable {
            if (isCompleted.compareAndSet(false, true)) {
                isAnalyzingIntent = false
                showToast("云端意图分析超时，请检查网络后重试")
            }
        }
        handler.postDelayed(llmTimeoutRunnable!!, LLMConfig.INTENT_TIMEOUT * 1000)

        LLMService.analyzeIntent(command) { result ->
            runOnUiThread {
                if (!isCompleted.compareAndSet(false, true)) return@runOnUiThread
                llmTimeoutRunnable?.let { handler.removeCallbacks(it) }
                isAnalyzingIntent = false
                when (result) {
                    is LLMResult.Success -> {
                        showFlightIntentConfirmDialog(result.intent, result.summary)
                    }
                    is LLMResult.Unknown -> {
                        AlertDialog.Builder(this)
                            .setTitle("无法识别飞行意图")
                            .setMessage("${result.reason}\n\n请修改指令后重试。")
                            .setPositiveButton("知道了", null)
                            .setCancelable(false)
                            .show()
                    }
                    is LLMResult.Error -> {
                        AlertDialog.Builder(this)
                            .setTitle("意图分析失败")
                            .setMessage("${result.message}\n\n请检查联网状态和后端配置后重试。")
                            .setPositiveButton("知道了", null)
                            .setCancelable(false)
                            .show()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun showFlightIntentConfirmDialog(intent: FlightIntent, summary: String) {
        val validation = FlightIntentValidator.validate(intent)
        if (!validation.valid) {
            showToast("飞控参数无效：${validation.message}")
            return
        }
        val title = when (intent.flightType) {
            "takeoff" -> "起飞"
            "land" -> "降落"
            "return_home" -> "返航"
            "hover" -> "悬停"
            "photo" -> "拍照"
            "video" -> if (intent.parameters.action == "stop") "停止录像" else "录像"
            "gimbal_control" -> "云台控制"
            "fly_to" -> "定向飞行"
            "orbit" -> "环绕飞行"
            "waypoint" -> "航线飞行"
            else -> "飞行指令"
        }
        binding.tvConfirmTitle.text = title
        binding.tvConfirmMessage.text = if (validation.executableNow) "$summary\n\n是否执行以下命令？"
            else "$summary\n\n仅生成参数预览，当前不支持执行。"
        binding.cvConfirmExecute.visibility = if (validation.executableNow) View.VISIBLE else View.GONE
        binding.cvConfirmExecute.setOnClickListener {
            hideCommandConfirmDialog()
            if (validation.executableNow) executeFlightIntent(intent)
        }
        binding.cvConfirmCancel.setOnClickListener { hideCommandConfirmDialog() }
        binding.clCommandConfirmDialog.visibility = View.VISIBLE
    }

    private fun hideCommandConfirmDialog() {
        binding.clCommandConfirmDialog.visibility = View.GONE
        binding.cvConfirmExecute.visibility = View.VISIBLE
        binding.cvConfirmExecute.setOnClickListener(null)
        binding.cvConfirmCancel.setOnClickListener(null)
    }

    private fun executeFlightIntent(intent: FlightIntent) {
        hideQuickCommandDialog()
        showToast("开始执行飞行指令")

        Log.d("FlightIntent", "执行飞行: type=${intent.flightType}, params=${intent.parameters}")

        when (intent.flightType) {
            "takeoff" -> showToast(executeSimulatedFlight(BasicFlightAction.TAKEOFF))
            "land" -> showToast(executeSimulatedFlight(BasicFlightAction.LAND))
            "return_home" -> showToast(executeSimulatedFlight(BasicFlightAction.RETURN_HOME))
            "hover" -> showToast("${executeSimulatedFlight(BasicFlightAction.HOVER)}（${intent.parameters.duration ?: 5} 秒仅为演示）")
            "photo" -> {
                val count = intent.parameters.count ?: 1
                showToast("拍摄 ${count} 张照片")
                captureDronePhoto()
            }
            "video" -> {
                val action = intent.parameters.action ?: "start"
                if (action == "stop") {
                    stopVideoRecording()
                } else {
                    startVideoRecording()
                }
            }
            else -> showToast("当前演示模式不支持此指令：${intent.flightType}")
        }
    }

    private fun executeRawCommand(command: String) {
        executeCommand(command)
    }

    private fun executeCommand(command: String) {
        hideQuickCommandDialog()
        showToast("执行指令: $command")
    }

    private fun setupQuickCommandListeners() {
        binding.ivCloseQuickCommand.setOnClickListener {
            hideQuickCommandDialog()
        }

        binding.clQuickCommand.setOnClickListener {
            hideQuickCommandDialog()
        }

        binding.cvCommandCustom.setOnClickListener {
            showCustomCommandDialog()
        }

        binding.ivCloseCustomCommand.setOnClickListener {
            hideCustomCommandDialog()
        }

        binding.clCustomCommandDialog.setOnClickListener {
            hideCustomCommandDialog()
        }

        binding.btnExecuteCustomCommand.setOnClickListener {
            val command = binding.etCustomCommand.text.toString().trim()
            if (command.isNotEmpty()) {
                processCommand(command)
            } else {
                showToast("请输入指令内容")
            }
        }

        binding.etCustomCommand.setOnEditorActionListener { _, _, _ ->
            val command = binding.etCustomCommand.text.toString().trim()
            if (command.isNotEmpty()) {
                processCommand(command)
            }
            true
        }

        binding.cvCommandReturn.setOnClickListener {
            executeCommand("返航")
        }

        binding.cvCommandHover.setOnClickListener {
            executeCommand("悬停")
        }

        binding.cvCommandPhoto.setOnClickListener {
            captureDronePhoto()
        }
    }

    override fun onResume() {
        super.onResume()
        inspectionStream?.resume()
    }

    override fun onPause() {
        isVoicePressActive = false
        cancelVoiceUpload = true
        stopVoiceInput()
        inspectionStream?.pause()
        super.onPause()
    }

    override fun onBackPressed() {
        when {
            binding.clCustomCommandDialog.visibility == View.VISIBLE -> hideCustomCommandDialog()
            binding.clQuickCommand.visibility == View.VISIBLE -> hideQuickCommandDialog()
            binding.clRouteSidebar.visibility == View.VISIBLE || binding.clTaskSidebar.visibility == View.VISIBLE -> hideAllSidebars()
            else -> super.onBackPressed()
        }
    }

    private fun requestPermissionsAndShowUploadOptions() {
        showUploadOptionsDialog()
    }

    private fun showUploadOptionsDialog() {
        binding.clUploadDialog.visibility = View.VISIBLE
    }

    private fun hideUploadDialog() {
        binding.clUploadDialog.visibility = View.GONE
    }

    private fun requestCameraAndTakePicture() {
        PermissionX.init(this)
            .permissions(android.Manifest.permission.CAMERA)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "需要相机权限才能拍照",
                    "允许",
                    "拒绝"
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "请在设置中手动开启相机权限",
                    "去设置",
                    "取消"
                )
            }
            .request { allGranted, _, _ ->
                if (allGranted) {
                    dispatchTakePicture()
                } else {
                    showToast("需要相机权限才能拍照")
                }
            }
    }

    private fun dispatchTakePicture() {
        val photoFile = try {
            createImageFile()
        } catch (e: IOException) {
            showToast("创建图片文件失败: ${e.message}")
            return
        }
        currentPhotoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(currentPhotoUri)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageDir = externalCacheDir ?: cacheDir
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            imageDir
        )
    }

    private fun handleUploadedFile(uri: Uri, fileType: String) {
        showToast("已选择${fileType}: ${uri.lastPathSegment ?: "未知"}")
    }

    private fun processCommand(command: String) {
        showCommandConfirmDialog(command)
    }

    data class Route(
        val id: Int,
        val name: String,
        val taskName: String,
        val waypoints: Int,
        val isActive: Boolean,
        val isLocked: Boolean
    )
}
