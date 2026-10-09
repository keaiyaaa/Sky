package com.example.msdksample.asuka

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.example.msdksample.R
import com.example.msdksample.asuka.data.InspectionDateGroup

class DateTasksDialogFragment(private val group: InspectionDateGroup) : DialogFragment() {

    private var taskSelectedListener: ((Int) -> Unit)? = null

    fun setOnTaskSelectedListener(listener: (Int) -> Unit) {
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
        return inflater.inflate(R.layout.fragment_date_tasks_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<android.widget.ImageView>(R.id.iv_close).setOnClickListener { dismiss() }

        view.findViewById<android.widget.TextView>(R.id.tv_title).text = formatDate(group.date)
        view.findViewById<android.widget.TextView>(R.id.tv_subtitle).text =
            "当天共 ${group.tasks.size} 条航线任务"

        renderTasksList()
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

    private fun formatDate(dateStr: String): String {
        val parts = dateStr.split("-")
        return "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
    }

    private fun renderTasksList() {
        val tasksList = view?.findViewById<LinearLayout>(R.id.tasks_list) ?: return
        tasksList.removeAllViews()

        group.tasks.forEachIndexed { index, task ->
            val itemView = layoutInflater.inflate(R.layout.item_task, null)

            itemView.findViewById<android.widget.TextView>(R.id.tv_route_name).text = task.routeName
            itemView.findViewById<android.widget.TextView>(R.id.tv_task_name).text = task.name
            itemView.findViewById<android.widget.TextView>(R.id.tv_time).text = task.time

            val statusView = itemView.findViewById<android.widget.TextView>(R.id.tv_status)
            if (task.status == "Urgent") {
                statusView.text = getString(R.string.status_urgent)
                statusView.setBackgroundColor(resources.getColor(R.color.danger))
            } else {
                statusView.text = getString(R.string.status_normal)
                statusView.setBackgroundColor(resources.getColor(R.color.success))
            }

            if (task.videos > 0) {
                itemView.findViewById<android.widget.TextView>(R.id.tv_videos).text = "${task.videos}段视频"
            }
            itemView.findViewById<android.widget.TextView>(R.id.tv_images).text = "${task.images}张图片"

            val defectsView = itemView.findViewById<android.widget.TextView>(R.id.tv_defects)
            if (task.defects > 0) {
                defectsView.text = "· ${task.defects}个隐患"
                defectsView.setTextColor(resources.getColor(R.color.danger))
            } else {
                defectsView.text = "· 无异常"
                defectsView.setTextColor(resources.getColor(R.color.success))
            }

            itemView.setOnClickListener {
                taskSelectedListener?.invoke(index)
                dismiss()
            }

            tasksList.addView(itemView)
        }
    }
}
