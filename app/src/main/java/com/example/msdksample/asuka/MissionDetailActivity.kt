package com.example.msdksample.asuka

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.msdksample.R
import com.bumptech.glide.Glide
import com.example.msdksample.asuka.data.InspectionRepository
import com.example.msdksample.asuka.data.InspectionTask
import com.example.msdksample.databinding.ActivityMissionDetailBinding
import com.example.msdksample.asuka.utils.PdfReportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MissionDetailActivity : AppCompatActivity() {

    private lateinit var repository: InspectionRepository
    private lateinit var binding: ActivityMissionDetailBinding
    private var currentDate: String = ""
    private var currentTaskIndex: Int = 0
    private var currentTaskId: Long = 0
    private var currentTaskName: String = ""
    private var currentFilter: String = "time-desc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMissionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = InspectionRepository(this)

        binding.ivBack.setOnClickListener { finish() }
        binding.btnHistory.setOnClickListener { showHistory() }
        binding.btnExport.setOnClickListener { exportPdfReport() }
        binding.btnCreateOrder.setOnClickListener { openWorkOrders() }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
        binding.playOverlay.setOnClickListener { playVideo() }
        binding.btnFilter.setOnClickListener { showFilterMenu() }

        lifecycleScope.launch {
            repository.initializeData()

            val date = intent.getStringExtra("date")
            val taskIndex = intent.getIntExtra("taskIndex", -1)
            val taskId = intent.getLongExtra("taskId", 0L)

            // 优先使用稳定任务 ID（F14：跨日期分组点击不再依赖索引，杜绝错位）
            if (taskId > 0) {
                loadTaskDetailById(taskId, date)
            } else if (date != null && taskIndex >= 0) {
                loadTaskDetail(date, taskIndex)
            } else {
                loadLatestTask()
            }
        }
    }

    private suspend fun loadLatestTask() {
        val latestGroup = repository.getLatestDateGroup()
        if (latestGroup != null && latestGroup.tasks.isNotEmpty()) {
            val urgentIndex = latestGroup.tasks.indexOfFirst { it.status == "Urgent" }
            val index = if (urgentIndex >= 0) urgentIndex else 0
            loadTaskDetail(latestGroup.date, index)
        }
    }

    /**
     * 通过任务 ID 加载详情（F14 修复核心）：不依赖列表顺序，天然无错位。
     * 日期优先取任务实体自身的 date，仅在实体缺失日期时才使用 Intent 传入值。
     */
    private suspend fun loadTaskDetailById(taskId: Long, fallbackDate: String?) {
        val task = repository.getTaskById(taskId) ?: run {
            Toast.makeText(this@MissionDetailActivity, "任务不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val date = task.date.ifEmpty { fallbackDate ?: "" }
        if (date.isEmpty()) {
            Toast.makeText(this@MissionDetailActivity, "任务日期缺失，无法显示详情", Toast.LENGTH_SHORT).show()
            return
        }
        loadTaskDetail(date, task, taskIndex = -1)
    }

    private suspend fun loadTaskDetail(date: String, taskIndex: Int) {
        val task = repository.getTask(date, taskIndex) ?: return
        loadTaskDetail(date, task, taskIndex)
    }

    private fun loadTaskDetail(date: String, task: InspectionTask, taskIndex: Int) {
        currentDate = date
        currentTaskIndex = taskIndex
        currentTaskId = task.id
        currentTaskName = task.name

        updateHeader(task, date)
        updateVideo(task)
        updateThumbnails(task)
        updateAnalysis(task)
        updateCreateOrderButton()
    }

    private fun updateHeader(task: InspectionTask, date: String) {
        val timeParts = task.time.split(" - ")
        binding.tvHeaderTitle.text = "$date ${timeParts[0]}"
        binding.tvHeaderSubtitle.text = "${task.routeName} - ${task.name}"
    }

    private fun updateVideo(task: InspectionTask) {
        binding.tvDefectId.visibility = if (task.defectId.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvDefectId.text = "缺陷 ID: ${task.defectId}"

        binding.tvDuration.text = task.duration

        if (task.videoSrc.isNotEmpty()) {
            binding.videoFrame.visibility = View.VISIBLE
            binding.ivVideoPoster.visibility = View.VISIBLE
            Glide.with(this)
                .load(task.videoPoster)
                .into(binding.ivVideoPoster)
            binding.playOverlay.visibility = View.VISIBLE
        } else {
            binding.videoFrame.visibility = View.GONE
            binding.ivVideoPoster.visibility = View.VISIBLE
            Glide.with(this)
                .load(task.videoPoster)
                .into(binding.ivVideoPoster)
            binding.playOverlay.visibility = View.VISIBLE
        }
    }

    private fun updateThumbnails(task: InspectionTask) {
        binding.thumbnailsContainer.removeAllViews()

        task.thumbnails.forEachIndexed { index, url ->
            val imageView = ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginStart = 4
                    marginEnd = 4
                }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = resources.getDrawable(R.drawable.bg_gray_light, null)
                clipToOutline = true
                setPadding(0, 0, 0, 0)

                Glide.with(this@MissionDetailActivity)
                    .load(url)
                    .into(this)

                setOnClickListener { switchThumbnail(it as ImageView, url) }
                setOnLongClickListener {
                    toggleFullscreen()
                    true
                }

                if (index == 0) {
                    background = resources.getDrawable(R.drawable.bg_thumbnail_selected, null)
                }
            }
            binding.thumbnailsContainer.addView(imageView)
        }
    }

    private fun switchThumbnail(imageView: ImageView, url: String) {
        for (i in 0 until binding.thumbnailsContainer.childCount) {
            val child = binding.thumbnailsContainer.getChildAt(i) as ImageView
            child.background = resources.getDrawable(R.drawable.bg_gray_light, null)
        }
        imageView.background = resources.getDrawable(R.drawable.bg_thumbnail_selected, null)

        binding.videoFrame.visibility = View.GONE
        binding.ivVideoPoster.visibility = View.VISIBLE
        Glide.with(this)
            .load(url)
            .into(binding.ivVideoPoster)
        binding.playOverlay.visibility = View.VISIBLE
    }

    private fun updateAnalysis(task: InspectionTask) {
        binding.analysisContainer.removeAllViews()

        val a = task.analysis
        // F15：右上角来源标签，区分真实 AI 分析与预置演示数据
        binding.tvAnalysisSource.text = if (a.analyzedAt > 0) "真实分析" else "演示数据"

        if (a.analyzedAt > 0) {
            // 真实 AI 分析结果：TODO 后续接入后展示置信度/tokens/分析时间等元信息
            addAnalysisItem(R.string.defect_type, a.defectType)
            addAnalysisItem(R.string.location, a.location)
            addAnalysisItem(R.string.suggestion, a.suggestion)
        } else {
            // 预置/种子数据：F15 明确标注演示来源，避免误认为真实分析结论
            addAnalysisSourceBanner()
            addAnalysisItem(R.string.defect_type, a.defectType)
            addAnalysisItem(R.string.location, a.location)
            addAnalysisItem(R.string.suggestion, a.suggestion)
            addAiCountItem(a.aiCount)
        }
    }

    /** F15：在分析结果顶部插入演示来源提示横幅 */
    private fun addAnalysisSourceBanner() {
        val banner = android.widget.TextView(this).apply {
            text = "以下为预置演示数据，非真实 AI 分析结果。"
            textSize = 10f
            setTextColor(resources.getColor(R.color.text_hint, null))
            setBackgroundColor(resources.getColor(R.color.bg_page, null))
            setPadding(24, 16, 24, 16)
        }
        binding.analysisContainer.addView(banner)
    }

    private fun addAnalysisItem(titleRes: Int, content: String) {
        val view = layoutInflater.inflate(R.layout.item_analysis, null)
        view.findViewById<TextView>(R.id.tv_title).setText(titleRes)
        view.findViewById<TextView>(R.id.tv_content).text = content
        binding.analysisContainer.addView(view)
    }

    private fun addAiCountItem(aiCount: String) {
        val view = layoutInflater.inflate(R.layout.item_ai_count, null)
        view.findViewById<TextView>(R.id.tv_count).text = aiCount
        view.setOnClickListener {
            startActivity(Intent(this, VipActivity::class.java))
        }
        binding.analysisContainer.addView(view)
    }

    private fun playVideo() {
        binding.playOverlay.visibility = View.GONE
    }

    // ==================== 工单（F17） ====================

    /** 刷新"创建维修工单"按钮：显示当前任务已有工单数，无 ID 时禁用 */
    private fun updateCreateOrderButton() {
        if (currentTaskId <= 0) {
            binding.btnCreateOrder.isEnabled = false
            binding.btnCreateOrder.alpha = 0.5f
            binding.btnCreateOrder.text = "创建维修工单"
            return
        }
        binding.btnCreateOrder.isEnabled = true
        binding.btnCreateOrder.alpha = 1f
        lifecycleScope.launch {
            val count = repository.countWorkOrdersByTask(currentTaskId)
            binding.btnCreateOrder.text = if (count > 0) "创建维修工单（已有 $count）" else "创建维修工单"
        }
    }

    /**
     * 工单入口：已有工单则先展示列表（可推进状态/查看），否则直接进入创建。
     */
    private fun openWorkOrders() {
        if (currentTaskId <= 0) {
            Toast.makeText(this, "当前任务无本地记录，无法创建工单", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val count = repository.countWorkOrdersByTask(currentTaskId)
            if (count > 0) showWorkOrdersDialog() else showCreateWorkOrderDialog()
        }
    }

    /** 弹出创建工单对话框：输入标题/描述/优先级/负责人，落库保存 */
    private fun showCreateWorkOrderDialog() {
        if (currentTaskId <= 0) {
            Toast.makeText(this, "当前任务无本地记录，无法创建工单", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_create_work_order, null)
        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.et_wo_title)
        val etDesc = dialogView.findViewById<android.widget.EditText>(R.id.et_wo_desc)
        val etAssignee = dialogView.findViewById<android.widget.EditText>(R.id.et_wo_assignee)
        val spinnerPriority = dialogView.findViewById<android.widget.Spinner>(R.id.sp_wo_priority)

        val priorities = arrayOf("Low", "Medium", "High", "Critical")
        spinnerPriority.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, priorities
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        // 默认按任务状态给一个合理优先级
        val defaultIdx = if (binding.tvDefectId.visibility == View.VISIBLE) 2 else 1
        spinnerPriority.setSelection(defaultIdx)

        // 预填标题：任务名 + 缺陷 ID
        val defectPart = if (binding.tvDefectId.visibility == View.VISIBLE)
            " [${binding.tvDefectId.text.toString().removePrefix("缺陷 ID: ")}]" else ""
        etTitle.setText("$currentTaskName 工单$defectPart")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("创建工单 - $currentTaskName")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                val assignee = etAssignee.text.toString().trim()
                val priority = priorities[spinnerPriority.selectedItemPosition]

                if (title.isEmpty()) {
                    Toast.makeText(this, "工单标题不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val newId = repository.createWorkOrder(
                        taskId = currentTaskId,
                        taskDate = currentDate,
                        taskName = currentTaskName,
                        title = title,
                        description = desc,
                        priority = priority,
                        assignee = assignee
                    )
                    withContext(Dispatchers.Main) {
                        if (newId > 0) {
                            updateCreateOrderButton()
                            showWorkOrdersDialog()
                        } else {
                            Toast.makeText(this@MissionDetailActivity, "工单保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 展示当前任务的工单列表（含创建时间/状态/负责人），点击可推进状态 */
    private fun showWorkOrdersDialog() {
        lifecycleScope.launch {
            val orders = repository.getWorkOrdersByTask(currentTaskId)
            withContext(Dispatchers.Main) {
                if (orders.isEmpty()) {
                    Toast.makeText(this@MissionDetailActivity, "暂无工单", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                val items = orders.map { o ->
                    val time = sdf.format(java.util.Date(o.createdAt))
                    val assigneePart = if (o.assignee.isNotEmpty()) " · ${o.assignee}" else ""
                    "[${
                        when (o.status) {
                            "Open" -> "待处理"
                            "InProgress" -> "处理中"
                            "Resolved" -> "已解决"
                            "Closed" -> "已关闭"
                            else -> o.status
                        }
                    }] ${o.title}\n  ${o.priority}$assigneePart · $time"
                }.toTypedArray()

                androidx.appcompat.app.AlertDialog.Builder(this@MissionDetailActivity)
                    .setTitle("工单列表（${orders.size}）- 点击推进状态")
                    .setItems(items) { _, which ->
                        val order = orders[which]
                        val nextStatus = when (order.status) {
                            "Open" -> "InProgress"
                            "InProgress" -> "Resolved"
                            "Resolved" -> "Closed"
                            else -> null
                        }
                        if (nextStatus != null) {
                            lifecycleScope.launch {
                                repository.updateWorkOrderStatus(order.id, nextStatus)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MissionDetailActivity,
                                        "工单已推进：${order.status} → $nextStatus",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showWorkOrdersDialog()
                                }
                            }
                        } else {
                            Toast.makeText(this@MissionDetailActivity, "工单已关闭", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("关闭", null)
                    .setNeutralButton("新建") { _, _ -> showCreateWorkOrderDialog() }
                    .show()
            }
        }
    }

    private fun toggleFullscreen() {
        val decorView = window.decorView
        if ((decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        } else {
            decorView.systemUiVisibility = 0
        }
    }

    /**
     * 筛选菜单（F14）：历史记录排序方式，选项与设计稿一致。
     */
    private fun showFilterMenu() {
        val options = arrayOf("按优先级排序", "时间倒序（最新优先）", "时间正序（最早优先）")
        val values = arrayOf("priority", "time-desc", "time-asc")
        val checkedIndex = values.indexOf(currentFilter).coerceAtLeast(1)
        var pendingIndex = checkedIndex

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("筛选条件 · 排序方式")
            .setSingleChoiceItems(options, checkedIndex) { _, which -> pendingIndex = which }
            .setPositiveButton("确定") { _, _ ->
                currentFilter = values[pendingIndex]
                Toast.makeText(this, "已切换：${options[pendingIndex]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHistory() {
        val fragment = HistoryBottomSheetFragment()
        fragment.setSortFilter(currentFilter)
        fragment.setOnTaskSelectedListener { date, taskIndex, taskId ->
            lifecycleScope.launch {
                // F14：优先按 ID 加载，杜绝跨日期索引错位
                if (taskId > 0) {
                    loadTaskDetailById(taskId, date)
                } else {
                    loadTaskDetail(date, taskIndex)
                }
            }
        }
        fragment.show(supportFragmentManager, "HistoryBottomSheet")
    }

    private fun showVipModal() {
        VipModalFragment().show(supportFragmentManager, "VipModal")
    }

    private fun showSuccessModal() {
        SuccessModalFragment.newInstance(getString(R.string.success_create_order))
            .show(supportFragmentManager, "SuccessModal")
    }

    private fun exportPdfReport() {
        lifecycleScope.launch {
            android.util.Log.d("PDF_EXPORT", "开始导出PDF")

            // 已按 ID 加载时索引为 -1，必须改用 ID 取任务，否则导出会失败
            val task = if (currentTaskId > 0) {
                repository.getTaskById(currentTaskId)
            } else {
                repository.getTask(currentDate, currentTaskIndex)
            }
            if (task == null) {
                android.util.Log.e("PDF_EXPORT", "任务数据为空")
                Toast.makeText(this@MissionDetailActivity, "无法获取任务数据", Toast.LENGTH_SHORT).show()
                return@launch
            }

            android.util.Log.d("PDF_EXPORT", "任务数据: name=${task.name}, date=$currentDate, defects=${task.defects}")

            val historyGroups = repository.getAllDateGroups()
            android.util.Log.d("PDF_EXPORT", "历史记录数量: ${historyGroups.size}")

            withContext(Dispatchers.IO) {
                android.util.Log.d("PDF_EXPORT", "开始生成PDF...")
                val pdfPath = PdfReportUtil.generateReport(this@MissionDetailActivity, currentDate, task, historyGroups)

                withContext(Dispatchers.Main) {
                    if (pdfPath != null) {
                        android.util.Log.d("PDF_EXPORT", "PDF生成成功: $pdfPath")
                        val file = java.io.File(pdfPath)
                        android.util.Log.d("PDF_EXPORT", "文件大小: ${file.length()} bytes")
                        SuccessModalFragment.newInstance("报告已导出：$pdfPath")
                            .show(supportFragmentManager, "SuccessModal")
                    } else {
                        android.util.Log.e("PDF_EXPORT", "PDF生成失败")
                        Toast.makeText(this@MissionDetailActivity, "导出失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
