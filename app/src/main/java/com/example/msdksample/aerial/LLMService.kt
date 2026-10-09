package com.example.msdksample.aerial

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit

object LLMService {

    private const val TAG = "LLMService"

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(LLMConfig.TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(LLMConfig.TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(LLMConfig.TIMEOUT, TimeUnit.SECONDS)
        .build()

    fun analyzeIntent(userText: String, callback: (LLMResult) -> Unit) {
        val messages = listOf(
            mapOf("role" to "system", "content" to LLMConfig.SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userText)
        )

        val requestBody = mapOf(
            "model" to LLMConfig.MODEL,
            "messages" to messages,
            "temperature" to LLMConfig.TEMPERATURE
        )

        val jsonBody = gson.toJson(requestBody)
        Log.d(TAG, "发送请求: $jsonBody")

        val body = RequestBody.create(jsonMediaType, jsonBody)
        val request = Request.Builder()
            .url(LLMConfig.API_URL)
            .addHeader("Authorization", "Bearer ${LLMConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "LLM请求失败", e)
                callback(LLMResult.Error("网络请求失败: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    Log.d(TAG, "LLM响应: $responseBody")

                    if (!response.isSuccessful) {
                        callback(LLMResult.Error("API返回错误 (${response.code})"))
                        return
                    }

                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices == null || choices.size() == 0) {
                        callback(LLMResult.Error("API未返回有效结果"))
                        return
                    }

                    val content = choices[0].asJsonObject
                        .getAsJsonObject("message")
                        ?.get("content")?.asString ?: ""

                    if (content.isBlank()) {
                        callback(LLMResult.Error("LLM返回内容为空"))
                        return
                    }

                    parseIntent(content, callback)
                } catch (e: Exception) {
                    Log.e(TAG, "解析响应失败", e)
                    callback(LLMResult.Error("解析响应失败: ${e.message}"))
                }
            }
        })
    }

    private fun parseIntent(content: String, callback: (LLMResult) -> Unit) {
        try {
            val cleanJson = content
                .trim()
                .removePrefix("json") .removePrefix("")
                .removeSuffix("") .trim()
            val intent = gson.fromJson(cleanJson, FlightIntent::class.java)

            if (intent.flightType == "unknown") {
                val reason = intent.parameters.reason ?: "无法识别飞行意图"
                callback(LLMResult.Unknown(reason))
                return
            }

            val summary = buildSummary(intent)
            callback(LLMResult.Success(intent, summary))
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON解析失败: $content", e)
            callback(LLMResult.Error("LLM返回格式异常，请重试"))
        }
    }

    private fun buildSummary(intent: FlightIntent): String {
        val p = intent.parameters
        return when (intent.flightType) {
            "takeoff" -> buildString {
                append("起飞\n")
                p.altitude?.let { append("目标高度：${it} 米") }
            }
            "land" -> "降落"
            "return_home" -> "返航至起飞点"
            "hover" -> buildString {
                append("悬停\n")
                append("时长：${p.duration ?: 5} 秒")
            }
            "photo" -> buildString {
                append("拍照\n")
                append("张数：${p.count ?: 1}")
            }
            "video" -> if (p.action == "stop") "停止录像" else "开始录像"
            "gimbal_control" -> buildString {
                append("云台控制")
                p.pitch?.let { append("\n俯仰：${it}°") }
                p.yaw?.let { append("\n偏航：${it}°") }
            }
            "fly_to" -> buildString {
                append("定向飞行")
                p.direction?.let {
                    val dirName = when (it) {
                        "forward" -> "前方"
                        "backward" -> "后方"
                        "left" -> "左侧"
                        "right" -> "右侧"
                        else -> it
                    }
                    append("\n方向：${dirName}")
                }
                p.distance?.let { append("\n距离：${it} 米") }
                p.altitude?.let { append("\n高度：${it} 米") }
                p.speed?.let { append("\n速度：${it} m/s") }
            }
            "orbit" -> buildString {
                append("环绕飞行")
                p.radius?.let { append("\n半径：${it} 米") }
                p.altitude?.let { append("\n高度：${it} 米") }
                append("\n方向：${if (p.direction == "counterclockwise") "逆时针" else "顺时针"}")
                p.laps?.let { append("\n圈数：${it}") }
                p.speed?.let { append("\n速度：${it} m/s") }
            }
            "waypoint" -> buildString {
                val patternName = when (p.pattern) {
                    "line" -> "直线"
                    "rectangle" -> "矩形"
                    "zigzag" -> "Z字形"
                    else -> p.pattern ?: "自定义"
                }
                append("${patternName}航线")
                p.altitude?.let { append("\n高度：${it} 米") }
                p.length?.let { append("\n长度：${it} 米") }
                p.width?.let { append("\n宽度：${it} 米") }
                p.speed?.let { append("\n速度：${it} m/s") }
            }
            else -> "飞行指令"
        }
    }
}