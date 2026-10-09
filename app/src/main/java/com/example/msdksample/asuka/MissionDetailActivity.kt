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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMissionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = InspectionRepository(this)

        binding.ivBack.setOnClickListener { finish() }
        binding.btnHistory.setOnClickListener { showHistory() }
        binding.btnExport.setOnClickListener { exportPdfReport() }
        binding.btnCreateOrder.setOnClickListener { showSuccessModal() }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
        binding.playOverlay.setOnClickListener { playVideo() }

        lifecycleScope.launch {
            repository.initializeData()

            val date = intent.getStringExtra("date")
            val taskIndex = intent.getIntExtra("taskIndex", -1)

            if (date != null && taskIndex >= 0) {
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

    private suspend fun loadTaskDetail(date: String, taskIndex: Int) {
        currentDate = date
        currentTaskIndex = taskIndex

        val task = repository.getTask(date, taskIndex) ?: return

        updateHeader(task, date)
        updateVideo(task)
        updateThumbnails(task)
        updateAnalysis(task)
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

        addAnalysisItem(R.string.defect_type, task.analysis.defectType)
        addAnalysisItem(R.string.location, task.analysis.location)
        addAnalysisItem(R.string.suggestion, task.analysis.suggestion)

        addAiCountItem(task.analysis.aiCount)
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

    private fun toggleFullscreen() {
        val decorView = window.decorView
        if ((decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        } else {
            decorView.systemUiVisibility = 0
        }
    }

    private fun showHistory() {
        val fragment = HistoryBottomSheetFragment()
        fragment.setOnTaskSelectedListener { date, taskIndex ->
            lifecycleScope.launch {
                loadTaskDetail(date, taskIndex)
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

            val task = repository.getTask(currentDate, currentTaskIndex)
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
