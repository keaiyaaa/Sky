package com.example.msdksample.ui.settings

/** Collects out-of-order callbacks. Duplicate and late replies cannot change a final result. */
class SyncBatch(
    private val fields: List<String>,
    private val onComplete: (Map<String, String?>) -> Unit
) {
    private val results = linkedMapOf<String, String?>()
    private var completed = false

    init { require(fields.isNotEmpty() && fields.distinct().size == fields.size) }

    @Synchronized
    fun reply(field: String, success: Boolean, error: String?) {
        if (completed || field !in fields || results.containsKey(field)) return
        results[field] = if (success) null else error ?: "同步失败"
        finishIfReady()
    }

    @Synchronized
    fun timeout() {
        if (completed) return
        fields.filterNot { results.containsKey(it) }.forEach { results[it] = "等待设备确认超时" }
        finishIfReady()
    }

    private fun finishIfReady() {
        if (results.size != fields.size) return
        completed = true
        onComplete(fields.associateWith { results[it] })
    }
}
