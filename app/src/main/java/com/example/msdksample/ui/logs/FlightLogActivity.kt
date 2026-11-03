package com.example.msdksample.ui.logs

import android.content.pm.ApplicationInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.AdapterView
import android.view.View
import android.widget.TextView
import com.example.msdksample.R
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.ui.local.LocalToolsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.msdksample.kami.mission.MissionRunRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlightLogActivity : LocalToolsActivity() {
    private val store by lazy { LocalDataStore.getInstance(this) }
    private lateinit var entries: LinearLayout
    private lateinit var summary: TextView
    private lateinit var keyword: android.widget.EditText
    private var source: String? = null
    /** 每次筛选取消旧请求，避免迟到结果覆盖当前筛选。 */
    private var refreshJob: Job? = null

    override fun initViews() {
        setupPage("飞行日志", "记录本机连接、设置同步和已接入的模拟飞行事件。演示记录单独标识；尚未接入的真机指令不会生成成功记录。")
        keyword = field("按指令、任务编号或错误原因检索", "", R.id.log_keyword)
        val filter = Spinner(this).apply {
            adapter = ArrayAdapter(this@FlightLogActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("全部来源", "本地演示", "设备连接"))
            binding.content.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
        }
        actions("查询／刷新" to { refresh() }, "清空筛选" to {
            keyword.setText(""); filter.setSelection(0); source = null; refresh()
        })
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            button("生成演示飞行事件") {
                val taskId = "DEMO-${System.currentTimeMillis()}"
                runCatching {
                    store.record("指令", "模拟起飞", "成功", "本地演示", "仅生成测试事件，未执行飞控", taskId)
                    store.record("任务", "模拟巡检", "中断", "本地演示", "演示：低电量触发任务中断", taskId)
                    store.record("指令", "模拟返航", "失败", "本地演示", "演示：连接丢失", taskId)
                }.onSuccess { refresh() }.onFailure { toast("保存演示日志失败") }
            }
        }
        summary = text("")
        entries = section("事件记录（最近200条）")
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                source = listOf(null, "本地演示", "设备连接")[position]; refresh()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    /**
     * 从正式事件表合并连接、设置和巡检日志，在 IO 线程查询并保留各来源事实。
     * @return {Unit} 刷新最近 200 条记录；失败时显示可恢复提示。
     */
    private fun refresh() {
        if (!::entries.isInitialized) return
        val query = keyword.text.toString().trim()
        val selectedSource = source
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            try {
                val logs = withContext(Dispatchers.IO) {
                    val localEvents = store.logs(query, selectedSource)
                    val missionEvents = MissionRunRepository(applicationContext).use { it.logs(query, selectedSource) }
                    // 按时间稳定排序，各个来源内部同毫秒事件的顺序保持不变。
                    (localEvents + missionEvents).sortedByDescending { it.time }.take(200)
                }
                summary.text = "找到 ${logs.size} 条记录"
                entries.removeAllViews()
                if (logs.isEmpty()) text("暂无符合条件的日志", entries)
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                logs.forEach { entry ->
                    text("${format.format(Date(entry.time))} · ${entry.source}\n${entry.category} / ${entry.action} · ${entry.result}\n" +
                        (if (entry.taskId.isNotBlank()) "任务：${entry.taskId}\n" else "") + entry.detail, entries)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("FlightLogActivity", "日志读取失败", error)
                summary.text = "日志读取失败，请重试"
            }
        }
    }
}
