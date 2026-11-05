package com.example.msdksample.aerial.inspection

import org.junit.Assert.assertEquals
import org.junit.Test

class InspectionFrameFilterTest {

    @Test
    fun rejectsLowContrastFrames() {
        val filter = InspectionFrameFilter()
        val flatFrame = rgbaFrame(160, 90) { _, _ -> 120 }

        assertEquals(
            FrameFilterDecision.LOW_CONTRAST,
            filter.evaluate(flatFrame, 0, flatFrame.size, 160, 90, 1_000, false)
        )
    }

    @Test
    fun limitsUploadFrequencyAndSkipsNearDuplicateFrames() {
        val filter = InspectionFrameFilter(minimumIntervalMs = 3_000)
        val frame = rgbaFrame(160, 90) { x, _ -> 40 + x * 170 / 159 }

        assertEquals(
            FrameFilterDecision.ACCEPT,
            filter.evaluate(frame, 0, frame.size, 160, 90, 1_000, false)
        )
        assertEquals(
            FrameFilterDecision.TOO_SOON,
            filter.evaluate(frame, 0, frame.size, 160, 90, 2_000, false)
        )
        assertEquals(
            FrameFilterDecision.DUPLICATE,
            filter.evaluate(frame, 0, frame.size, 160, 90, 4_500, false)
        )
    }

    private fun rgbaFrame(width: Int, height: Int, luma: (Int, Int) -> Int): ByteArray {
        val bytes = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = luma(x, y).toByte()
                val offset = (y * width + x) * 4
                bytes[offset] = value
                bytes[offset + 1] = value
                bytes[offset + 2] = value
                bytes[offset + 3] = 0xff.toByte()
            }
        }
        return bytes
    }
}
