package com.example.msdksample.aerial.inspection

enum class FrameFilterDecision {
    ACCEPT,
    BUSY,
    INVALID_FRAME,
    TOO_SOON,
    LOW_LIGHT,
    OVEREXPOSED,
    LOW_CONTRAST,
    DUPLICATE
}

/** 只筛帧与画质，不在端侧执行缺陷模型。 */
class InspectionFrameFilter(
    private val minimumIntervalMs: Long = 4_000,
    private val duplicateThreshold: Int = 2
) {
    private var lastSampledAtMs: Long? = null
    private var lastAcceptedHash: Long? = null

    @Synchronized
    fun evaluate(
        rgba: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int,
        nowMs: Long,
        uploadInFlight: Boolean
    ): FrameFilterDecision {
        if (uploadInFlight) return FrameFilterDecision.BUSY
        if (width < 9 || height < 8 || offset < 0 || length < 0) {
            return FrameFilterDecision.INVALID_FRAME
        }
        val expectedBytes = width.toLong() * height * 4
        if (expectedBytes > length || offset.toLong() + expectedBytes > rgba.size) {
            return FrameFilterDecision.INVALID_FRAME
        }
        if (lastSampledAtMs?.let { nowMs - it < minimumIntervalMs } == true) {
            return FrameFilterDecision.TOO_SOON
        }
        lastSampledAtMs = nowMs

        val sample = sampleLuminance(rgba, offset, width, height)
        val mean = sample.average()
        if (mean < MIN_MEAN_BRIGHTNESS) return FrameFilterDecision.LOW_LIGHT
        if (mean > MAX_MEAN_BRIGHTNESS) return FrameFilterDecision.OVEREXPOSED
        val variance = sample.sumOf { (it - mean) * (it - mean) } / sample.size
        if (variance < MIN_VARIANCE) return FrameFilterDecision.LOW_CONTRAST

        val hash = differenceHash(sample)
        val previousHash = lastAcceptedHash
        if (previousHash != null && java.lang.Long.bitCount(previousHash xor hash) <= duplicateThreshold) {
            return FrameFilterDecision.DUPLICATE
        }
        lastAcceptedHash = hash
        return FrameFilterDecision.ACCEPT
    }

    private fun sampleLuminance(rgba: ByteArray, offset: Int, width: Int, height: Int): DoubleArray {
        val sample = DoubleArray(HASH_ROWS * HASH_COLUMNS)
        var index = 0
        for (row in 0 until HASH_ROWS) {
            val y = ((row + 0.5) * height / HASH_ROWS).toInt().coerceIn(0, height - 1)
            for (column in 0 until HASH_COLUMNS) {
                val x = ((column + 0.5) * width / HASH_COLUMNS).toInt().coerceIn(0, width - 1)
                val pixelOffset = offset + (y * width + x) * 4
                val red = rgba[pixelOffset].toInt() and 0xff
                val green = rgba[pixelOffset + 1].toInt() and 0xff
                val blue = rgba[pixelOffset + 2].toInt() and 0xff
                sample[index++] = 0.299 * red + 0.587 * green + 0.114 * blue
            }
        }
        return sample
    }

    private fun differenceHash(sample: DoubleArray): Long {
        var hash = 0L
        var bit = 0
        for (row in 0 until HASH_ROWS) {
            val rowStart = row * HASH_COLUMNS
            for (column in 0 until HASH_COLUMNS - 1) {
                if (sample[rowStart + column] > sample[rowStart + column + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    private companion object {
        const val HASH_COLUMNS = 9
        const val HASH_ROWS = 8
        const val MIN_MEAN_BRIGHTNESS = 18.0
        const val MAX_MEAN_BRIGHTNESS = 238.0
        const val MIN_VARIANCE = 20.0
    }
}
