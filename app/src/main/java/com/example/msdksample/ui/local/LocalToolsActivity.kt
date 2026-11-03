package com.example.msdksample.ui.local

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.msdksample.R
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityLocalToolsBinding
import com.example.msdksample.utils.PreferenceManager

abstract class LocalToolsActivity : BaseActivity<ActivityLocalToolsBinding>() {
    final override fun getViewBinding() = ActivityLocalToolsBinding.inflate(layoutInflater)

    protected fun setupPage(title: String, explanation: String) {
        binding.toolbar.title = title
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        text(explanation)
    }

    protected fun text(value: String, parent: LinearLayout = binding.content, heading: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = if (heading) 17f else 14f
            setTextColor(getColor(R.color.gray_800))
            if (heading) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(10))
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

    protected fun button(label: String, parent: LinearLayout = binding.content, action: () -> Unit): Button =
        Button(this).apply {
            text = label; isAllCaps = false; minHeight = dp(48)
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

    protected fun field(label: String, value: String, viewId: Int,
                        type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        text(label)
        return EditText(this).apply {
            id = viewId; setText(value); inputType = type; setSingleLine(true)
            setTextColor(getColor(R.color.gray_900)); minHeight = dp(48)
            binding.content.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    protected fun section(label: String): LinearLayout {
        text(label, heading = true)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            binding.content.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    protected fun actions(vararg actions: Pair<String, () -> Unit>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        binding.content.addView(row, LinearLayout.LayoutParams(-1, -2))
        actions.forEach { (label, action) ->
            button(label, row, action).layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        }
    }
    protected fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

fun localProfileOwner(context: Context): String {
    val prefs = PreferenceManager.getInstance(context)
    return if (prefs.isLoggedIn() && prefs.getUserPhone().isNotBlank()) "phone:${prefs.getUserPhone()}" else "guest"
}

fun maskedPhone(value: String): String = if (value.length == 11) value.take(3) + "****" + value.takeLast(4) else value
