package com.example.msdksample.aerial
import android.content.Intent import android.net.Uri import android.os.Bundle import android.os.Environment import android.os.Handler import android.os.Looper import android.provider.MediaStore import android.util.Log import android.view.MotionEvent import android.view.View import android.widget.Toast import androidx.activity.enableEdgeToEdge import androidx.activity.result.contract.ActivityResultContracts import androidx.appcompat.app.AlertDialog import androidx.appcompat.app.AppCompatActivity import androidx.core.content.FileProvider import androidx.core.view.ViewCompat import androidx.core.view.WindowInsetsCompat import com.example.msdksample.databinding.ActivityAerialPhotographyBinding import com.permissionx.guolindev.PermissionX import java.io.File import java.io.IOException import java.text.SimpleDateFormat import java.util.Date import java.util.Locale import com.example.msdksample.R
import com.example.msdksample.asuka.MainActivity
import com.example.msdksample.asuka.data.InspectionRepository
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import kotlin.math.min
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.msdksample.asuka.data.InspectionTask
import com.example.msdksample.kami.RoutePlanningActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Aerial_photography : AppCompatActivity() {
    private lateinit var binding: ActivityAerialPhotographyBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isControlMode = false
    private var voiceTimer: Long = 0
    private var currentPhotoUri: Uri? = null
    private var isListening = false
    private var isAnalyzingIntent = false
    private var llmTimeoutRunnable: Runnable? = null

    private var isVideoRecording = false

    /** 演示视频文件名：需把 test.mp4 放到 App 专属外部目录（见 initDemoVideoIfPresent） */
    private val demoVideoFileName = "test.mp4"

    /** 演示视频是否已成功开始播放（决定 onPause/onResume 行为） */
    private var demoVideoStarted = false

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

            initVoskRecognizer()

            binding.flVideoPreview.setOnClickListener {
                if (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0) {
                    showSystemUI()
                } else {
                    hideSystemUI()
                }
            }

            // 可选：在 FPV 区域播放演示视频（test.mp4），找不到则维持静态占位图
            initDemoVideoIfPresent()

            setupListeners()
            setupQuickCommandListeners()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        releaseDemoVideo()
        // 注意：不能使用 “?: return” —— 一旦 llmTimeoutRunnable 为 null 会提前 return，
        // 跳过 VoskHelper.destroy() 与 super.onDestroy()，抛出 SuperNotCalledException。
        llmTimeoutRunnable?.let { handler.removeCallbacks(it) }
        VoskHelper.destroy()
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

    // ══════════════════════════════════════════════════════════════
    //  演示视频（FPV 区域播放 test.mp4）
    // ══════════════════════════════════════════════════════════════

    /**
     * 尝试在 [flVideoPreview] 容器内播放演示视频（test.mp4）。
     *
     * 视频需预先放入 App 专属外部目录（免权限，可在 Debug 下用 adb 推送）：
     *   adb push test.mp4 /sdcard/Android/data/com.example.msdksample/files/test.mp4
     *
     * 找不到文件 / 加载或播放失败时**静默降级**为原静态占位图，不崩溃。
     */
    private fun initDemoVideoIfPresent() {
        val videoFile = File(getExternalFilesDir(null), demoVideoFileName)
        if (!videoFile.exists()) {
            Log.w(TAG, "演示视频不存在: ${videoFile.absolutePath}，保持静态占位图")
            return
        }
        val vv = binding.vvDemoVideo
        binding.ivVideoFeed.visibility = View.GONE
        vv.visibility = View.VISIBLE
        binding.flVideoPreview.setBackgroundColor(Color.BLACK)

        vv.setOnPreparedListener { mp ->
            mp.isLooping = true
            fitDemoVideoToContainer(vv, mp.videoWidth, mp.videoHeight)
            mp.start()
            demoVideoStarted = true
            Log.i(TAG, "演示视频已开始播放: ${videoFile.absolutePath}")
        }
        vv.setOnErrorListener { _, _, _ ->
            Log.w(TAG, "演示视频播放失败，降级为静态占位图")
            showStaticFallback()
            true
        }
        try {
            vv.setVideoURI(Uri.fromFile(videoFile))
        } catch (t: Throwable) {
            Log.w(TAG, "加载演示视频异常: ${t.message}")
            showStaticFallback()
        }
    }

    /** 将视频等比例缩放到容器内并居中（不裁剪、不变形） */
    private fun fitDemoVideoToContainer(vv: VideoView, videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) return
        vv.post {
            val container = binding.flVideoPreview
            val cw = container.width
            val ch = container.height
            if (cw <= 0 || ch <= 0) return@post
            val scale = min(cw.toFloat() / videoWidth, ch.toFloat() / videoHeight)
            val lp = FrameLayout.LayoutParams(
                (videoWidth * scale).toInt().coerceAtLeast(1),
                (videoHeight * scale).toInt().coerceAtLeast(1),
                Gravity.CENTER
            )
            vv.layoutParams = lp
        }
    }

    /** 播放失败/降级：隐藏播放器，恢复原静态占位图与容器背景 */
    private fun showStaticFallback() {
        demoVideoStarted = false
        binding.flVideoPreview.setBackgroundColor(Color.TRANSPARENT)
        binding.vvDemoVideo.visibility = View.GONE
        binding.ivVideoFeed.visibility = View.VISIBLE
    }

    private fun pauseDemoVideo() {
        if (demoVideoStarted && binding.vvDemoVideo.isPlaying) {
            binding.vvDemoVideo.pause()
        }
    }

    private fun resumeDemoVideo() {
        if (demoVideoStarted && !binding.vvDemoVideo.isPlaying) {
            binding.vvDemoVideo.start()
        }
    }

    private fun releaseDemoVideo() {
        demoVideoStarted = false
        if (::binding.isInitialized) {
            binding.vvDemoVideo.stopPlayback()
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
                    startVoiceInput()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

    private fun initVoskRecognizer() {
        VoskHelper.init(this, object : VoskHelper.VoskInitCallback {
            override fun onDownloadStart() {
                showToast("正在下载离线语音模型 (约42MB)...")
            }

            override fun onDownloadProgress(percent: Int) {
                if (percent % 20 == 0) {
                    showToast("模型下载中... $percent%")
                }
            }

            override fun onDownloadComplete() {
                showToast("下载完成，正在解压模型...")
            }

            override fun onExtracting() {
            }

            override fun onInitialized() {
                showToast("离线语音引擎已就绪")
            }

            override fun onError(message: String) {
                showToast(message)
            }
        })
    }

    private fun startVoiceInput() {
        if (!VoskHelper.isModelReady()) {
            if (VoskHelper.isInitializing()) {
                showToast("语音引擎正在初始化，请稍后...")
            } else {
                showToast("语音引擎未就绪，请稍后重试")
            }
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
                if (allGranted) {
                    beginSpeechRecognition()
                } else {
                    showToast("需要录音权限才能使用语音输入")
                }
            }
    }

    private fun beginSpeechRecognition() {
        binding.cvVoice.alpha = 0.7f
        showToast("正在聆听...")

        isListening = true

        VoskHelper.startListening(this, object : VoskHelper.VoskRecognitionListener {
            override fun onPartialResult(text: String) {
                binding.cvVoice.alpha = 0.6f
            }

            override fun onFinalResult(text: String) {
                isListening = false
                handler.removeCallbacksAndMessages(null)
                binding.cvVoice.alpha = 1.0f
                if (text.isNotBlank()) {
                    showCommandConfirmDialog(text)
                } else {
                    showToast("未识别到语音，请重试")
                }
            }

            override fun onError(message: String) {
                isListening = false
                handler.removeCallbacksAndMessages(null)
                binding.cvVoice.alpha = 1.0f
                showToast("语音识别出错: $message")
            }

            override fun onTimeout() {
                isListening = false
                handler.removeCallbacksAndMessages(null)
                binding.cvVoice.alpha = 1.0f
                showToast("语音输入超时，请重试")
            }
        })

        voiceTimer = System.currentTimeMillis()
        handler.postDelayed({
            stopVoiceInput()
        }, 5000)
    }

    private fun stopVoiceInput() {
        binding.cvVoice.alpha = 1.0f
        handler.removeCallbacksAndMessages(null)
        if (isListening) {
            isListening = false
            VoskHelper.stopListening()
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

    private val simCb = object : AerialDroneManager.SimulatorCallback {
        override fun onSuccess() {
            showToast("操作成功")
        }
        override fun onFailure(message: String) {
            showToast(message)
        }
    }

    private fun captureDronePhoto() {
        AerialDroneManager.setCameraModeToPhoto(object : AerialDroneManager.SimulatorCallback {
            override fun onSuccess() {
                AerialDroneManager.startShootPhoto(simCb)
            }
            override fun onFailure(message: String) {
                AerialDroneManager.startShootPhoto(simCb)
            }
        })
    }

    private fun startVideoRecording() {
        AerialDroneManager.setCameraModeToVideo(object : AerialDroneManager.SimulatorCallback {
            override fun onSuccess() {
                AerialDroneManager.startRecordVideo(object : AerialDroneManager.SimulatorCallback {
                    override fun onSuccess() {
                        isVideoRecording = true
                        binding.cvRecord.setCardBackgroundColor(getColor(R.color.mode_active))
                        showToast("开始录像")
                    }
                    override fun onFailure(message: String) {
                        showToast(message)
                    }
                })
            }
            override fun onFailure(message: String) {
                AerialDroneManager.startRecordVideo(object : AerialDroneManager.SimulatorCallback {
                    override fun onSuccess() {
                        isVideoRecording = true
                        binding.cvRecord.setCardBackgroundColor(getColor(R.color.mode_active))
                        showToast("开始录像")
                    }
                    override fun onFailure(message: String) {
                        showToast(message)
                    }
                })
            }
        })
    }

    private fun stopVideoRecording() {
        AerialDroneManager.stopRecordVideo(object : AerialDroneManager.SimulatorCallback {
            override fun onSuccess() {
                isVideoRecording = false
                binding.cvRecord.setCardBackgroundColor(getColor(R.color.mode_inactive))
                showToast("停止录像")
            }
            override fun onFailure(message: String) {
                showToast(message)
            }
        })
    }

    private fun showCommandConfirmDialog(command: String) {
        if (isAnalyzingIntent) return

        if (NetworkUtil.isOnline(this)) {
            analyzeWithLLM(command)
        } else {
            val matched = matchLocalCommand(command)
            if (matched != null) {
                showLocalCommandConfirmDialog(command, matched)
            } else {
                showToast("离线模式仅支持简单指令（返航、悬停、拍照、起飞、降落、录像）")
            }
        }
    }

    private fun analyzeWithLLM(command: String) {
        isAnalyzingIntent = true
        showToast("正在分析飞行意图...")

        var isCompleted = false

        llmTimeoutRunnable = Runnable {
            if (!isCompleted) {
                isCompleted = true
                isAnalyzingIntent = false
                showToast("意图分析超时，切换为离线模式")
                val matched = matchLocalCommand(command)
                if (matched != null) {
                    showLocalCommandConfirmDialog(command, matched)
                } else {
                    showToast("离线模式仅支持简单指令（返航、悬停、拍照、起飞、降落、录像）")
                }
            }
        }
        handler.postDelayed(llmTimeoutRunnable!!, LLMConfig.INTENT_TIMEOUT * 1000)

        LLMService.analyzeIntent(command) { result ->
            runOnUiThread {
                if (isCompleted) return@runOnUiThread
                isCompleted = true
                handler.removeCallbacks(llmTimeoutRunnable!!)
                isAnalyzingIntent = false
                when (result) {
                    is LLMResult.Success -> {
                        showFlightIntentConfirmDialog(result.intent, result.summary)
                    }
                    is LLMResult.Unknown -> {
                        AlertDialog.Builder(this)
                            .setTitle("无法识别飞行意图")
                            .setMessage("${result.reason}\n\n原文: \"$command\"\n\n是否仍要执行原指令？")
                            .setPositiveButton("仍执行") { _, _ ->
                                executeRawCommand(command)
                            }
                            .setNegativeButton("取消", null)
                            .setCancelable(false)
                            .show()
                    }
                    is LLMResult.Error -> {
                        AlertDialog.Builder(this)
                            .setTitle("意图分析失败")
                            .setMessage("${result.message}\n\n是否切换离线模式重试？")
                            .setPositiveButton("离线匹配") { _, _ ->
                                val matched = matchLocalCommand(command)
                                if (matched != null) {
                                    showLocalCommandConfirmDialog(command, matched)
                                } else {
                                    showToast("离线模式仅支持简单指令（返航、悬停、拍照、起飞、降落、录像）")
                                }
                            }
                            .setNegativeButton("取消", null)
                            .setCancelable(false)
                            .show()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun matchLocalCommand(command: String): String? {
        val text = command.trim().lowercase()
        return when {
            text.contains("返航") || text.contains("回来") || text.contains("回家") -> "返航"
            text.contains("悬停") || text.contains("停") || text.contains("不动") -> "悬停"
            text.contains("拍照") || text.contains("拍一张") || text.contains("照相") -> "拍照"
            text.contains("起飞") || text.contains("升空") || text.contains("飞起来") -> "起飞"
            text.contains("降落") || text.contains("下去") || text.contains("着陆") -> "降落"
            text.contains("停止录像") || text.contains("不录") || text.contains("结束录像") || text.contains("停止录制") -> "停止录像"
            text.contains("录像") || text.contains("录制") || text.contains("开始录") -> "录像"
            else -> null
        }
    }

    private fun showLocalCommandConfirmDialog(command: String, matched: String) {
        binding.tvConfirmTitle.text = matched
        binding.tvConfirmMessage.text = "是否执行以下命令？"
        binding.cvConfirmExecute.setOnClickListener {
            hideCommandConfirmDialog()
            executeLocalCommand(matched)
        }
        binding.cvConfirmCancel.setOnClickListener { hideCommandConfirmDialog() }
        binding.clCommandConfirmDialog.visibility = View.VISIBLE
    }

    private fun executeLocalCommand(matched: String) {
        hideQuickCommandDialog()
        when (matched) {
            "返航" -> showToast("返航功能需要连接真实无人机")
            "悬停" -> showToast("悬停功能需要连接真实无人机")
            "拍照" -> captureDronePhoto()
            "起飞" -> showToast("起飞功能需要连接真实无人机")
            "降落" -> showToast("降落功能需要连接真实无人机")
            "录像" -> startVideoRecording()
            "停止录像" -> stopVideoRecording()
            else -> showToast("执行指令: $matched")
        }
    }

    private fun showFlightIntentConfirmDialog(intent: FlightIntent, summary: String) {
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
        binding.tvConfirmMessage.text = "$summary\n\n是否执行以下命令？"
        binding.cvConfirmExecute.setOnClickListener {
            hideCommandConfirmDialog()
            executeFlightIntent(intent)
        }
        binding.cvConfirmCancel.setOnClickListener { hideCommandConfirmDialog() }
        binding.clCommandConfirmDialog.visibility = View.VISIBLE
    }

    private fun hideCommandConfirmDialog() {
        binding.clCommandConfirmDialog.visibility = View.GONE
        binding.cvConfirmExecute.setOnClickListener(null)
        binding.cvConfirmCancel.setOnClickListener(null)
    }

    private fun executeFlightIntent(intent: FlightIntent) {
        hideQuickCommandDialog()
        showToast("开始执行飞行指令")

        Log.d("FlightIntent", "执行飞行: type=${intent.flightType}, params=${intent.parameters}")

        when (intent.flightType) {
            "takeoff" -> {
                val alt = intent.parameters.altitude ?: 10.0
                showToast("起飞至 ${alt} 米 (需连接真实无人机)")
            }
            "land" -> showToast("降落 (需连接真实无人机)")
            "return_home" -> showToast("返航 (需连接真实无人机)")
            "hover" -> {
                val dur = intent.parameters.duration ?: 5
                showToast("悬停 ${dur} 秒 (需连接真实无人机)")
            }
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
            "gimbal_control" -> {
                val pitch = intent.parameters.pitch
                val yaw = intent.parameters.yaw
                showToast("云台控制: pitch=${pitch}, yaw=${yaw} (需连接真实无人机)")
            }
            "fly_to" -> {
                val alt = intent.parameters.altitude
                val dist = intent.parameters.distance
                val dir = intent.parameters.direction
                showToast("飞行动作: 方向=$dir, 距离=$dist, 高度=$alt (需连接真实无人机)")
            }
            "orbit" -> {
                showToast("环绕飞行: 半径=${intent.parameters.radius}m (需连接真实无人机)")
            }
            "waypoint" -> {
                showToast("航点飞行: 模式=${intent.parameters.pattern} (需连接真实无人机)")
            }
            else -> showToast("未知飞行类型: ${intent.flightType}")
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
        resumeDemoVideo()
    }

    override fun onPause() {
        pauseDemoVideo()
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