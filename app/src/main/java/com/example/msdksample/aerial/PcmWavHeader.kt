package com.example.msdksample.aerial

object PcmWavHeader {

    fun create(dataLength: Int): ByteArray {
        require(dataLength >= 0)
        val header = ByteArray(44)
        writeAscii(header, 0, "RIFF")
        writeInt(header, 4, dataLength + HEADER_SIZE - 8)
        writeAscii(header, 8, "WAVE")
        writeAscii(header, 12, "fmt ")
        writeInt(header, 16, 16)
        writeShort(header, 20, PCM_FORMAT)
        writeShort(header, 22, CHANNELS)
        writeInt(header, 24, SAMPLE_RATE)
        writeInt(header, 28, SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8)
        writeShort(header, 32, CHANNELS * BITS_PER_SAMPLE / 8)
        writeShort(header, 34, BITS_PER_SAMPLE)
        writeAscii(header, 36, "data")
        writeInt(header, 40, dataLength)
        return header
    }

    private fun writeAscii(destination: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(destination, offset)
    }

    private fun writeInt(destination: ByteArray, offset: Int, value: Int) {
        for (index in 0 until 4) destination[offset + index] = (value ushr (index * 8)).toByte()
    }

    private fun writeShort(destination: ByteArray, offset: Int, value: Int) {
        destination[offset] = value.toByte()
        destination[offset + 1] = (value ushr 8).toByte()
    }

    private const val HEADER_SIZE = 44
    private const val SAMPLE_RATE = 16_000
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16
    private const val PCM_FORMAT = 1
}
