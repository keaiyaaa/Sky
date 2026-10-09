package com.example.msdksample.asuka

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.msdksample.R
import com.example.msdksample.asuka.data.InspectionDateGroup
import com.example.msdksample.asuka.data.InspectionRepository
import kotlinx.coroutines.launch

class HistoryBottomSheetFragment : DialogFragment() {

    private lateinit var repository: InspectionRepository
    private var taskSelectedListener: ((String, Int) -> Unit)? = null

    fun setOnTaskSelectedListener(listener: (String, Int) -> Unit) {
        this.taskSelectedListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
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

        view.findViewById<android.widget.ImageView>(R.id.iv_close).setOnClickListener { dismiss() }

        renderHistoryList()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window
        val displayMetrics = requireContext().resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val width = (screenWidth * 2 / 3).toInt()
        window?.setLayout(width, screenHeight)
        window?.setGravity(android.view.Gravity.END)
    }

    private fun renderHistoryList() {
        lifecycleScope.launch {
            val historyList = view?.findViewById<LinearLayout>(R.id.history_list) ?: return@launch
            historyList.removeAllViews()

            val dateGroups = repository.sortDateGroups("time-desc")

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
        fragment.setOnTaskSelectedListener { taskIndex ->
            savedListener?.invoke(group.date, taskIndex)
        }
        fragment.show(requireActivity().supportFragmentManager, "DateTasksDialog")
    }
}
