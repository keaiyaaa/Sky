package com.example.msdksample.asuka

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.msdksample.R
import com.example.msdksample.asuka.data.InspectionDateGroup
import com.example.msdksample.asuka.data.InspectionRepository
import kotlinx.coroutines.launch

class HistoryBottomSheetFragment : DialogFragment() {

    private lateinit var repository: InspectionRepository
    private var taskSelectedListener: ((String, Int, Long) -> Unit)? = null
    /** 排序方式：priority / time-desc / time-asc，由详情页筛选菜单传入 */
    private var sortFilter: String = "time-desc"

    fun setOnTaskSelectedListener(listener: (String, Int, Long) -> Unit) {
        this.taskSelectedListener = listener
    }

    fun setSortFilter(filter: String) {
        this.sortFilter = filter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 右侧抽屉：全屏透明窗口 + 自绘遮罩与滑入动画
        setStyle(STYLE_NORMAL, R.style.RightDrawerDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history_panel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = InspectionRepository(requireContext())

        val panel = view.findViewById<View>(R.id.drawer_panel)
        val scrim = view.findViewById<View>(R.id.drawer_scrim)

        view.findViewById<android.widget.ImageView>(R.id.iv_close)
            .setOnClickListener { closeDrawer(panel, scrim) }
        scrim.setOnClickListener { closeDrawer(panel, scrim) }

        // 面板自右侧滑入，遮罩同步淡入（先置位避免首帧闪现）
        panel.translationX = resources.displayMetrics.widthPixels * 2f / 3f
        scrim.alpha = 0f
        panel.post {
            panel.animate()
                .translationX(0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            scrim.animate().alpha(1f).setDuration(220).start()
        }

        renderHistoryList()
    }

    private var closing = false

    /** 面板滑出后关闭，避免直接 dismiss 时的突兀跳变 */
    private fun closeDrawer(panel: View, scrim: View) {
        if (closing) return
        closing = true
        panel.animate()
            .translationX(panel.width.toFloat())
            .setDuration(180)
            .start()
        scrim.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction { dismissAllowingStateLoss() }
            .start()
    }

    private fun renderHistoryList() {
        lifecycleScope.launch {
            val historyList = view?.findViewById<LinearLayout>(R.id.history_list) ?: return@launch
            historyList.removeAllViews()

            val dateGroups = repository.sortDateGroups(sortFilter)

            dateGroups.forEach { group ->
                val itemView = layoutInflater.inflate(R.layout.item_history_date, null)
                itemView.findViewById<android.widget.TextView>(R.id.tv_date).text = formatDate(group.date)

                val totalDefects = group.tasks.sumBy { it.defects }
                val defectText = if (totalDefects > 0) "${totalDefects}个隐患" else getString(R.string.no_defect)
                itemView.findViewById<android.widget.TextView>(R.id.tv_info).text =
                    "共 ${group.tasks.size} 条航线任务 · $defectText"

                itemView.setOnClickListener { showDateTasks(group) }
                historyList.addView(itemView)
            }

            if (dateGroups.isEmpty()) {
                val emptyView = layoutInflater.inflate(R.layout.item_history_date, null)
                emptyView.findViewById<android.widget.TextView>(R.id.tv_date).text = "暂无历史记录"
                emptyView.findViewById<android.widget.TextView>(R.id.tv_info).text = ""
                historyList.addView(emptyView)
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        val parts = dateStr.split("-")
        return "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
    }

    private fun showDateTasks(group: InspectionDateGroup) {
        val savedListener = taskSelectedListener
        dismiss()
        val fragment = DateTasksDialogFragment(group)
        fragment.setOnTaskSelectedListener { taskIndex, taskId ->
            savedListener?.invoke(group.date, taskIndex, taskId)
        }
        fragment.show(requireActivity().supportFragmentManager, "DateTasksDialog")
    }
}
