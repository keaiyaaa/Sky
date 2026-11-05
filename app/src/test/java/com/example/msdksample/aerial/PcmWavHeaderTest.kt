package com.example.msdksample.aerial

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWavHeaderTest {

    @Test
    fun writesPcmMono16KhzHeaderWithCorrectDataLength() {
        val header = PcmWavHeader.create(32_000)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals(32_036, buffer.getInt(4))
        assertEquals(16_000, buffer.getInt(24))
        assertEquals(32_000, buffer.getInt(40))
        assertEquals(1, buffer.getShort(22).toInt())
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals(44, header.size)
    }
}
