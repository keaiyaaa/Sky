package com.example.msdksample.aerial

import com.example.msdksample.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class InspectionFinding(
    val status: String,
    val title: String,
    val description: String
)

sealed class VoiceAnalysisResult {
    data class Success(val transcript: String, val intent: FlightIntent, val summary: String) : VoiceAnalysisResult()
    data class Unknown(val transcript: String, val reason: String) : VoiceAnalysisResult()
    data class Error(val message: String) : VoiceAnalysisResult()
}

object LLMService {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(LLMConfig.TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isCloudConfigured(): Boolean = endpoint("/api/v1/health") != null

    fun analyzeIntent(userText: String, callback: (LLMResult) -> Unit) {
        val endpoint = endpoint("/api/v1/intent/analyze")
        if (endpoint == null) {
            callback(LLMResult.Error("请先配置 HTTPS 后端地址和访问令牌"))
            return
        }
        val body = mapOf("text" to userText).let(gson::toJson).toRequestBody(jsonMediaType)
        executeJson(endpoint, body) { responseBody ->
            try {
                callback(parseIntentResult(responseBody).second)
            } catch (_: Exception) {
                callback(LLMResult.Error("云端意图结果解析失败"))
            }
        }
    }

    fun analyzeVoice(file: File, callback: (VoiceAnalysisResult) -> Unit) {
        val endpoint = endpoint("/api/v1/voice/analyze")
        if (endpoint == null) {
            callback(VoiceAnalysisResult.Error("请先配置 HTTPS 后端地址和访问令牌"))
            return
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .build()
        val request = request(endpoint).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                callback(VoiceAnalysisResult.Error("联网语音识别失败，请检查网络后重试"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(VoiceAnalysisResult.Error(errorMessage(responseBody, it.code)))
                        return
                    }
                    try {
                        val (transcript, result) = parseIntentResult(responseBody)
                        callback(
                            when (result) {
                                is LLMResult.Success -> VoiceAnalysisResult.Success(transcript, result.intent, result.summary)
                                is LLMResult.Unknown -> VoiceAnalysisResult.Unknown(transcript, result.reason)
                                is LLMResult.Error -> VoiceAnalysisResult.Error(result.message)
                            }
                        )
                    } catch (_: Exception) {
                        callback(VoiceAnalysisResult.Error("云端语音结果解析失败"))
                    }
                }
            }
        })
    }

    fun analyzeInspection(jpegBytes: ByteArray, callback: (Result<InspectionFinding>) -> Unit) {
        val endpoint = endpoint("/api/v1/inspection/analyze")
        if (endpoint == null) {
            callback(Result.failure(IllegalStateException("请先配置 HTTPS 后端地址和访问令牌")))
            return
        }
        val imageBody = jpegBytes.toRequestBody("image/jpeg".toMediaType())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "inspection-frame.jpg", imageBody)
            .build()
        val request = request(endpoint).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                callback(Result.failure(IOException("云端巡检分析请求失败")))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException(errorMessage(responseBody, it.code))))
                        return
                    }
                    try {
                        val json = JsonParser.parseString(responseBody).asJsonObject
                        callback(
                            Result.success(
                                InspectionFinding(
                                    status = json.stringOrEmpty("status"),
                                    title = json.stringOrEmpty("title"),
                                    description = json.stringOrEmpty("description")
                                )
                            )
                        )
                    } catch (_: Exception) {
                        callback(Result.failure(IOException("云端巡检结果解析失败")))
                    }
                }
            }
        })
    }

    private fun executeJson(endpoint: String, body: okhttp3.RequestBody, callback: (String) -> Unit) {
        val request = request(endpoint).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                callback(gson.toJson(mapOf("status" to "error", "message" to "网络请求失败，请检查网络后重试")))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(gson.toJson(mapOf(
                            "status" to "error",
                            "message" to errorMessage(responseBody, it.code)
                        )))
                    } else {
                        callback(responseBody)
                    }
                }
            }
        })
    }

    private fun parseIntentResult(responseBody: String): Pair<String, LLMResult> {
        val root = JsonParser.parseString(responseBody).asJsonObject
        val transcript = root.stringOrEmpty("transcript")
        if (root.stringOrEmpty("status") == "error") {
            return transcript to LLMResult.Error(root.stringOrEmpty("message").ifBlank { "云端模型调用失败" })
        }
        if (root.stringOrEmpty("status") != "success") {
            return transcript to LLMResult.Unknown(root.stringOrEmpty("reason").ifBlank { "指令不明确或缺少必要参数" })
        }
        val intentJson: JsonObject = root.getAsJsonObject("flightIntent")
            ?: throw IllegalArgumentException("missing flight intent")
        val intent = FlightIntentParser.parse(intentJson.toString())
        return transcript to LLMResult.Success(intent, root.stringOrEmpty("summary").ifBlank { "飞行参数预览" })
    }

    private fun request(url: String): Request.Builder = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer ${BuildConfig.AERIAL_API_TOKEN}")

    private fun endpoint(path: String): String? {
        val baseUrl = BuildConfig.AERIAL_BACKEND_URL.trim().trimEnd('/')
        val token = BuildConfig.AERIAL_API_TOKEN
        if (baseUrl.isBlank() || token.isBlank() || !BackendUrlPolicy.isAllowed(baseUrl, BuildConfig.DEBUG)) {
            return null
        }
        return "$baseUrl$path"
    }

    private fun errorMessage(body: String, statusCode: Int): String {
        return try {
            JsonParser.parseString(body).asJsonObject.stringOrEmpty("message")
                .ifBlank { "后端请求失败（$statusCode）" }
        } catch (_: Exception) {
            "后端请求失败（$statusCode）"
        }
    }

    private fun JsonObject.stringOrEmpty(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
}
