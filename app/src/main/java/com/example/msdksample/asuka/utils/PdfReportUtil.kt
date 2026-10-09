package com.example.msdksample.asuka.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.example.msdksample.asuka.data.InspectionDateGroup
import com.example.msdksample.asuka.data.InspectionTask
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF 报告生成工具。
 *
 * 使用 Android 系统自带 [PdfDocument]（无第三方依赖 / 无需 iText 许可），
 * 输出到 App 专属外部目录（[FileStorageUtil.getPdfFilePath]），
 * 该目录无需运行时权限且天然适配 Android 10+ 分区存储。
 *
 * 之前为空壳（直接返回 null）导致“导出失败”；现恢复完整实现。
 */
object PdfReportUtil {

    private const val TAG = "PdfReportUtil"

    // A4 尺寸（单位 pt）
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    /**
     * 生成巡检报告 PDF。
     * @return 成功返回文件绝对路径；失败返回 null（调用方据此提示“导出失败”）。
     * 应在 IO 线程调用。
     */
    fun generateReport(
        context: Context,
        date: String,
        task: InspectionTask,
        historyGroups: List<InspectionDateGroup> = emptyList()
    ): String? {
        val fileName = FileStorageUtil.generateReportFileName(date, task.name)
        val targetPath = FileStorageUtil.getPdfFilePath(context, fileName)
        return try {
            writePdf(File(targetPath), date, task, historyGroups)
            Log.i(TAG, "PDF 导出成功: $targetPath")
            targetPath
        } catch (t: Throwable) {
            Log.e(TAG, "PDF 生成失败: ${t.message}", t)
            null
        }
    }

    private fun writePdf(
        file: File,
        date: String,
        task: InspectionTask,
        historyGroups: List<InspectionDateGroup>
    ) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()

        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
        }
        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.GRAY
        }
        val headerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.parseColor("#1F4E79")
        }
        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.DKGRAY
        }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.DKGRAY
        }
        val rulePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var page = pdf.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = MARGIN

        fun ensureSpace(lineHeight: Float) {
            if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
                pdf.finishPage(page)
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }
        }

        /** 可换行文本（按字符宽度折行），随后推进 y */
        fun text(content: String, paint: Paint, lineHeight: Float = paint.textSize * 1.7f) {
            if (content.isEmpty()) {
                y += lineHeight * 0.6f
                return
            }
            for (line in wrap(content, paint)) {
                ensureSpace(lineHeight)
                canvas.drawText(line, MARGIN, y + paint.textSize, paint)
                y += lineHeight
            }
        }

        /** 段落标题 + 分隔线 */
        fun section(title: String) {
            ensureSpace(30f)
            y += 14f
            canvas.drawText(title, MARGIN, y + headerPaint.textSize, headerPaint)
            y += headerPaint.textSize * 1.9f
            canvas.drawLine(MARGIN, y - 5f, MARGIN + CONTENT_WIDTH, y - 5f, rulePaint)
        }

        /** “键：值”单行 */
        fun keyValue(key: String, value: String) {
            text("$key：$value", bodyPaint)
        }

        // ── 标题区 ────────────────────────────────────────────────
        text("天枢巡检 - 巡检报告", titlePaint)
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        text("生成时间：$generatedAt    巡检日期：$date", subtitlePaint)

        // ── 任务信息 ──────────────────────────────────────────────
        section("任务信息")
        keyValue("任务名称", task.name)
        keyValue("航线", task.routeName)
        keyValue("时间", task.time)
        keyValue("状态", task.status)
        keyValue("优先级", task.priority.toString())
        keyValue("时长", task.duration)
        if (task.defectId.isNotBlank()) keyValue("缺陷编号", task.defectId)
        keyValue("视频", "${task.videos} 个")
        keyValue("图片", "${task.images} 个")
        keyValue("缺陷数", "${task.defects} 个")

        // ── AI 智能分析 ───────────────────────────────────────────
        section("AI 智能分析")
        keyValue("缺陷类型", task.analysis.defectType)
        keyValue("位置", task.analysis.location)
        text("处置建议：${task.analysis.suggestion}", bodyPaint)
        keyValue("AI 分析次数", task.analysis.aiCount)

        // ── 历史巡检记录摘要 ──────────────────────────────────────
        if (historyGroups.isNotEmpty()) {
            section("历史巡检记录")
            val totalTasks = historyGroups.sumOf { it.tasks.size }
            text("近 ${historyGroups.size} 个日期，共 $totalTasks 条航线任务", subtitlePaint)
            for (group in historyGroups) {
                text("【${group.date}】${group.title}", labelPaint)
                for (t in group.tasks) {
                    text("  · ${t.name}（${t.routeName}）  状态：${t.status}  缺陷：${t.defects}", bodyPaint)
                }
            }
        }

        y += 10f
        text("— 报告结束 —", subtitlePaint)

        pdf.finishPage(page)
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    /** 按画布可用宽度折行（逐字符，兼容中文） */
    private fun wrap(text: String, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            val candidate = sb.toString() + ch
            if (sb.isNotEmpty() && paint.measureText(candidate) > CONTENT_WIDTH) {
                lines.add(sb.toString())
                sb.setLength(0)
            }
            sb.append(ch)
        }
        if (sb.isNotEmpty()) lines.add(sb.toString())
        return lines.ifEmpty { listOf(text) }
    }
}
