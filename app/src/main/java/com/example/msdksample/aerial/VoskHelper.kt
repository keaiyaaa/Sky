package com.example.msdksample.aerial

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object VoskHelper {

    private const val TAG = "VoskHelper"
    private const val SAMPLE_RATE = 16000.0f
    private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"
    private const val MODEL_ZIP_NAME = "vosk-model-small-cn-0.22.zip"
    private const val MODEL_DOWNLOAD_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    private var isReady = false
    private var isInitializing = false
    private var isDownloading = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    interface VoskInitCallback {
        fun onDownloadStart()
        fun onDownloadProgress(percent: Int)
        fun onDownloadComplete()
        fun onExtracting()
        fun onInitialized()
        fun onError(message: String)
    }

    fun isModelReady(): Boolean = isReady

    fun isInitializing(): Boolean = isInitializing

    fun init(context: Context, callback: VoskInitCallback) {
        if (isReady) {
            callback.onInitialized()
            return
        }
        if (isInitializing) {
            return
        }

        isInitializing = true

        Thread {
            try {
                val modelDir = getModelDir(context)
                if (isModelValid(modelDir)) {
                    Log.d(TAG, "模型已存在: ${modelDir.absolutePath}")
                    loadModel(modelDir, callback)
                    return@Thread
                }

                Log.d(TAG, "模型不存在，开始下载...")
                Handler(Looper.getMainLooper()).post { callback.onDownloadStart() }

                val zipFile = downloadModel(context, callback)

                Handler(Looper.getMainLooper()).post {
                    callback.onDownloadComplete()
                    callback.onExtracting()
                }

                extractModel(zipFile, modelDir)
                zipFile.delete()

                if (isModelValid(modelDir)) {
                    loadModel(modelDir, callback)
                } else {
                    Handler(Looper.getMainLooper()).post {
                        isInitializing = false
                        callback.onError("模型解压后校验失败，请重试")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Vosk 初始化失败", e)
                Handler(Looper.getMainLooper()).post {
                    isInitializing = false
                    isDownloading = false
                    callback.onError("初始化失败: ${e.message}")
                }
            }
        }.start()
    }

    private fun downloadModel(context: Context, callback: VoskInitCallback): File {
        isDownloading = true

        val zipFile = File(context.getExternalFilesDir(null), MODEL_ZIP_NAME)
        val request = Request.Builder().url(MODEL_DOWNLOAD_URL).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载失败，HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("响应体为空")
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(zipFile)

            inputStream.use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var lastReportedPercent = -1
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(buffer, 0, n)
                        bytesRead += n
                        if (contentLength > 0) {
                            val percent = (bytesRead * 100 / contentLength).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                Handler(Looper.getMainLooper()).post {
                                    callback.onDownloadProgress(percent)
                                }
                            }
                        }
                    }
                }
            }
        }

        isDownloading = false
        Log.d(TAG, "模型下载完成: ${zipFile.length()} bytes")
        return zipFile
    }

    private fun extractModel(zipFile: File, destDir: File) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            val stripPrefix: String? = if (entry != null && entry.name.contains('/')) {
                entry.name.substringBefore('/') + "/"
            } else null

            while (entry != null) {
                val relativeName = if (stripPrefix != null && entry.name.startsWith(stripPrefix)) {
                    entry.name.removePrefix(stripPrefix)
                } else {
                    entry.name
                }

                if (relativeName.isEmpty()) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                val entryFile = File(destDir, relativeName)
                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        Log.d(TAG, "模型解压完成")
    }

    private fun loadModel(modelDir: File, callback: VoskInitCallback) {
        model = Model(modelDir.absolutePath)
        recognizer = Recognizer(model, SAMPLE_RATE)

        Handler(Looper.getMainLooper()).post {
            isReady = true
            isInitializing = false
            callback.onInitialized()
        }
    }

    private fun isModelValid(modelDir: File): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        val amDir = File(modelDir, "am")
        val confDir = File(modelDir, "conf")
        return amDir.exists() && amDir.isDirectory && confDir.exists() && confDir.isDirectory
    }

    private fun getModelDir(context: Context): File {
        return File(context.getExternalFilesDir(null), MODEL_DIR_NAME)
    }

    fun startListening(context: Context, listener: VoskRecognitionListener) {
        val currentModel = model
        if (!isReady || currentModel == null) {
            listener.onError("Vosk 尚未初始化完成")
            return
        }

        try {
            recognizer?.close()
            recognizer = Recognizer(currentModel, SAMPLE_RATE)
            val currentRecognizer = recognizer!!

            speechService?.stop()
            speechService = SpeechService(currentRecognizer, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {
                    val text = parseText(hypothesis)
                    if (text.isNotEmpty()) {
                        listener.onPartialResult(text)
                    }
                }

                override fun onResult(hypothesis: String) {
                    val text = parseText(hypothesis)
                    if (text.isNotEmpty()) {
                        listener.onPartialResult(text)
                    }
                }

                override fun onFinalResult(hypothesis: String) {
                    val text = parseText(hypothesis)
                    if (text.isNotEmpty()) {
                        listener.onFinalResult(text)
                    }
                }

                override fun onError(exception: Exception) {
                    Log.e(TAG, "识别错误", exception)
                    listener.onError(exception.message ?: "未知错误")
                }

                override fun onTimeout() {
                    listener.onTimeout()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败", e)
            listener.onError(e.message ?: "启动失败")
        }
    }

    fun stopListening() {
        try {
            speechService?.stop()
            speechService = null
        } catch (e: Exception) {
            Log.e(TAG, "停止识别失败", e)
        }
    }

    fun destroy() {
        stopListening()
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        isReady = false
    }

    private fun parseText(hypothesis: String): String {
        return try {
            val json = org.json.JSONObject(hypothesis)
            json.optString("text", "")
        } catch (e: Exception) {
            val match = Regex("\"text\"\\s*:\\s*\"(.*?)\"").find(hypothesis)
            match?.groupValues?.getOrNull(1) ?: hypothesis.trim()
        }
    }

    interface VoskRecognitionListener {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(message: String)
        fun onTimeout()
    }
}
