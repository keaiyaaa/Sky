package com.example.msdksample.asuka

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.msdksample.R
import com.example.msdksample.asuka.data.InspectionRepository
import com.example.msdksample.asuka.data.InspectionTask
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repository: InspectionRepository
    private var currentFilter = "time-desc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_asuka)

        repository = InspectionRepository(this)

        initStats()
        initTasksList()
        initFilter()

        findViewById<LinearLayout>(R.id.card_today).setOnClickListener {
            lifecycleScope.launch {
                val latestGroup = repository.getLatestDateGroup()
                val firstTask = latestGroup?.tasks?.firstOrNull()
                if (latestGroup != null && firstTask != null) {
                    val intent = Intent(this@MainActivity, MissionDetailActivity::class.java)
                    intent.putExtra("date", latestGroup.date)
                    intent.putExtra("taskIndex", 0)
                    intent.putExtra("taskId", firstTask.id)
                    startActivity(intent)
                }
            }
        }

        findViewById<LinearLayout>(R.id.card_pending).setOnClickListener {
            lifecycleScope.launch {
                val latestGroup = repository.getLatestDateGroup()
                if (latestGroup != null) {
                    val urgentIndex = latestGroup.tasks.indexOfFirst { it.status == "Urgent" }
                    val targetIndex = if (urgentIndex >= 0) urgentIndex else 0
                    val targetTask = latestGroup.tasks.getOrNull(targetIndex)
                    val intent = Intent(this@MainActivity, MissionDetailActivity::class.java)
                    intent.putExtra("date", latestGroup.date)
                    intent.putExtra("taskIndex", targetIndex)
                    intent.putExtra("taskId", targetTask?.id ?: 0L)
                    startActivity(intent)
                }
            }
        }
    }

    /** 筛选菜单（F14）：切换首页巡检列表排序方式 */
    private fun initFilter() {
        findViewById<android.view.View>(R.id.btn_filter).setOnClickListener {
            val options = arrayOf("按优先级排序", "时间倒序（最新优先）", "时间正序（最早优先）")
            val values = arrayOf("priority", "time-desc", "time-asc")
            val checkedIndex = values.indexOf(currentFilter).coerceAtLeast(1)
            var pendingIndex = checkedIndex

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("筛选条件 · 排序方式")
                .setSingleChoiceItems(options, checkedIndex) { _, which -> pendingIndex = which }
                .setPositiveButton("确定") { _, _ ->
                    currentFilter = values[pendingIndex]
                    initTasksList()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun initStats() {
        lifecycleScope.launch {
            val groups = repository.getAllDateGroups()
            val todayCount = groups.firstOrNull()?.tasks?.size ?: 0
            val defectsCount = groups.sumBy { it.tasks.sumBy { t -> t.defects } }
            val aiCount = groups.firstOrNull()?.tasks?.firstOrNull()?.analysis?.aiCount ?: "3/3"

            val cardToday = findViewById<LinearLayout>(R.id.card_today)
            cardToday.getChildAt(1)?.let {
                (it as android.widget.TextView).text = todayCount.toString()
            }

            val cardPending = findViewById<LinearLayout>(R.id.card_pending)
            cardPending.getChildAt(1)?.let {
                (it as android.widget.TextView).text = defectsCount.toString()
            }

            val cardAiCount = findViewById<LinearLayout>(R.id.card_ai_count)
            cardAiCount.getChildAt(1)?.let {
                (it as android.widget.TextView).text = aiCount
            }
        }
    }

    private fun initTasksList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_tasks)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val allTasks = mutableListOf<Triple<String, Int, InspectionTask>>()
            val groups = repository.sortDateGroups(currentFilter)

            groups.forEach { group ->
                group.tasks.forEachIndexed { taskIndex, task ->
                    allTasks.add(Triple(group.date, taskIndex, task))
                }
            }

            recyclerView.adapter = TaskAdapter(allTasks) { date, taskIndex, taskId ->
                val intent = Intent(this@MainActivity, MissionDetailActivity::class.java)
                intent.putExtra("date", date)
                intent.putExtra("taskIndex", taskIndex)
                intent.putExtra("taskId", taskId)
                startActivity(intent)
            }
        }
    }

    class TaskAdapter(
        private val tasks: List<Triple<String, Int, InspectionTask>>,
        private val onItemClick: (String, Int, Long) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (date, taskIndex, task) = tasks[position]
            holder.bind(date, taskIndex, task)
        }

        override fun getItemCount() = tasks.size

        inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            fun bind(date: String, taskIndex: Int, task: InspectionTask) {
                itemView.findViewById<android.widget.TextView>(R.id.tv_route_name).text = task.routeName
                itemView.findViewById<android.widget.TextView>(R.id.tv_task_name).text = task.name
                itemView.findViewById<android.widget.TextView>(R.id.tv_time).text = date + " " + task.time.split(" - ")[0]

                val statusView = itemView.findViewById<android.widget.TextView>(R.id.tv_status)
                if (task.status == "Urgent") {
                    statusView.text = itemView.context.getString(R.string.status_urgent)
                    statusView.setBackgroundColor(itemView.context.resources.getColor(R.color.danger))
                } else {
                    statusView.text = itemView.context.getString(R.string.status_normal)
                    statusView.setBackgroundColor(itemView.context.resources.getColor(R.color.success))
                }

                if (task.videos > 0) {
                    itemView.findViewById<android.widget.TextView>(R.id.tv_videos).text = "${task.videos}段视频"
                }
                itemView.findViewById<android.widget.TextView>(R.id.tv_images).text = "${task.images}张图片"

                val defectsView = itemView.findViewById<android.widget.TextView>(R.id.tv_defects)
                if (task.defects > 0) {
                    defectsView.text = "· ${task.defects}个隐患"
                    defectsView.setTextColor(itemView.context.resources.getColor(R.color.danger))
                } else {
                    defectsView.text = "· 无异常"
                    defectsView.setTextColor(itemView.context.resources.getColor(R.color.success))
                }

                itemView.setOnClickListener {
                    onItemClick(date, taskIndex, task.id)
                }
            }
        }
    }
}
