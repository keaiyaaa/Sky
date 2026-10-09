package com.example.msdksample.asuka

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.msdksample.R

class FilterMenuFragment(private var currentFilter: String) : DialogFragment() {

    private var listener: OnFilterSelectedListener? = null

    interface OnFilterSelectedListener {
        fun onFilterSelected(filterType: String)
    }

    fun setListener(listener: OnFilterSelectedListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_filter_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateButtonState(view)

        view.findViewById<LinearLayout>(R.id.btn_priority).setOnClickListener {
            currentFilter = "priority"
            updateButtonState(view)
        }

        view.findViewById<LinearLayout>(R.id.btn_time_desc).setOnClickListener {
            currentFilter = "time-desc"
            updateButtonState(view)
        }

        view.findViewById<LinearLayout>(R.id.btn_time_asc).setOnClickListener {
            currentFilter = "time-asc"
            updateButtonState(view)
        }

        view.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            listener?.onFilterSelected(currentFilter)
            dismiss()
        }
    }

    private fun updateButtonState(view: View) {
        updateButton(view.findViewById(R.id.tv_priority), view.findViewById(R.id.iv_check_priority), "priority")
        updateButton(view.findViewById(R.id.tv_time_desc), view.findViewById(R.id.iv_check_time_desc), "time-desc")
        updateButton(view.findViewById(R.id.tv_time_asc), view.findViewById(R.id.iv_check_time_asc), "time-asc")
    }

    private fun updateButton(textView: TextView, checkView: ImageView, filterType: String) {
        if (currentFilter == filterType) {
            textView.setTextColor(resources.getColor(R.color.primary))
            checkView.visibility = View.VISIBLE
        } else {
            textView.setTextColor(resources.getColor(R.color.text_secondary))
            checkView.visibility = View.GONE
        }
    }
}
