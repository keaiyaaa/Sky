package com.example.msdksample.aerial

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class OnlinePttRecorder(
    context: Context,
    private val onComplete: (Result<File>) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopping = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    fun start(): Boolean {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        if (recordingThread?.isAlive == true) return false

        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimumBufferSize <= 0) return false

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minimumBufferSize * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        return try {
            stopping.set(false)
            recorder.startRecording()
            audioRecord = recorder
            recordingThread = Thread({ capture(recorder, minimumBufferSize) }, "aerial-ptt-recorder").apply {
                start()
            }
            true
        } catch (_: RuntimeException) {
            recorder.release()
            false
        }
    }

    fun stop() {
        stopping.set(true)
        try {
            audioRecord?.let { recorder ->
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
        } catch (_: IllegalStateException) {
            // 录音线程可能刚好已结束。
        }
    }

    private fun capture(recorder: AudioRecord, bufferSize: Int) {
        val pcm = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize.coerceIn(MIN_READ_BUFFER, MAX_READ_BUFFER))
        val startedAt = SystemClock.elapsedRealtime()
        var failed = false
        try {
            while (!stopping.get() && SystemClock.elapsedRealtime() - startedAt < MAX_DURATION_MS) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    pcm.write(buffer, 0, read)
                } else if (read < 0 && !stopping.get()) {
                    failed = true
                    break
                }
            }
        } catch (_: RuntimeException) {
            failed = true
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: IllegalStateException) {
                // 录音可能由 stop() 提前结束。
            }
            recorder.release()
            if (audioRecord === recorder) audioRecord = null
        }

        val pcmBytes = pcm.toByteArray()
        pcm.reset()
        val result = if (failed || pcmBytes.isEmpty()) {
            pcmBytes.fill(0)
            Result.failure(IOException("没有录到有效语音"))
        } else {
            try {
                Result.success(writeWav(pcmBytes))
            } catch (_: IOException) {
                Result.failure(IOException("录音文件生成失败"))
            } finally {
                pcmBytes.fill(0)
            }
        }
        mainHandler.post { onComplete(result) }
    }

    @Throws(IOException::class)
    private fun writeWav(pcm: ByteArray): File {
        val file = File.createTempFile("aerial-voice-", ".wav", appContext.cacheDir)
        try {
            FileOutputStream(file).use { output ->
                output.write(wavHeader(pcm.size))
                output.write(pcm)
            }
            return file
        } catch (exception: IOException) {
            file.delete()
            throw exception
        }
    }

    private fun wavHeader(dataLength: Int): ByteArray = PcmWavHeader.create(dataLength)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_DURATION_MS = 10_000L
        const val MIN_READ_BUFFER = 2_048
        const val MAX_READ_BUFFER = 16_384
    }
}
