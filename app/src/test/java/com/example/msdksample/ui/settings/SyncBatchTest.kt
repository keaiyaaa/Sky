package com.example.msdksample.ui.settings

import org.junit.Assert.*
import org.junit.Test

class SyncBatchTest {
    @Test fun sessionChangeInvalidatesEarlierAcknowledgementsAndPendingFields() {
        var result: Map<String, String?>? = null
        var completions = 0
        val batch = SyncBatch(listOf("a", "b")) { result = it; completions++ }
        batch.reply("a", true, null)
        batch.invalidate("STALE_GENERATION")
        batch.reply("b", true, null)
        batch.invalidate("重复")
        assertEquals(1, completions)
        assertEquals(setOf("STALE_GENERATION"), result!!.values.toSet())
    }
    @Test fun waitsForEveryFieldAndPreservesPartialFailure() {
        var result: Map<String, String?>? = null
        val batch = SyncBatch(listOf("高度", "避障", "画质")) { result = it }
        batch.reply("画质", true, null)
        batch.reply("高度", false, "拒绝参数")
        assertNull(result)
        batch.reply("避障", true, null)
        assertEquals("拒绝参数", result!!["高度"])
        assertNull(result!!["避障"])
        assertEquals(listOf("高度", "避障", "画质"), result!!.keys.toList())
    }

    @Test fun duplicateOrUnknownCallbackCannotFinishTheBatchEarly() {
        var completions = 0
        val batch = SyncBatch(listOf("a", "b")) { completions++ }
        batch.reply("a", true, null)
        batch.reply("a", false, "重复")
        batch.reply("unknown", true, null)
        assertEquals(0, completions)
        batch.reply("b", true, null)
        batch.reply("b", true, null)
        batch.timeout()
        assertEquals(1, completions)
    }

    @Test fun timeoutMarksOnlyUnansweredFieldsAndIgnoresLateSuccess() {
        var result: Map<String, String?>? = null
        var completions = 0
        val batch = SyncBatch(listOf("a", "b")) { result = it; completions++ }
        batch.reply("a", true, null)
        batch.timeout()
        batch.reply("b", true, null)
        assertNull(result!!["a"])
        assertEquals("等待设备确认超时", result!!["b"])
        assertEquals(1, completions)
    }
}
