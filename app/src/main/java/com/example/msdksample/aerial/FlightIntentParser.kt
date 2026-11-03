package com.example.msdksample.aerial

import com.google.gson.Gson
import com.google.gson.JsonParser

/** Converts model text to a validated command preview. Never dispatches a flight action. */
object FlightIntentParser {
    private val gson = Gson()

    fun parse(content: String): FlightIntent {
        val trimmed = content.trim()
        val jsonText = if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
            trimmed.removePrefix("```json").removeSuffix("```").trim()
        } else trimmed
        try {
            val json = JsonParser.parseString(jsonText).asJsonObject
            if (!json.has("flightType") || !json.has("parameters") || !json.get("parameters").isJsonObject) {
                throw IllegalArgumentException("缺少 flightType 或 parameters")
            }
            if (json.keySet() != setOf("flightType", "parameters")) throw IllegalArgumentException("顶层包含未知字段")
            if (!json.get("flightType").isJsonPrimitive || !json.get("flightType").asJsonPrimitive.isString) {
                throw IllegalArgumentException("flightType 必须是字符串")
            }
            val knownFields = setOf("altitude", "speed", "distance", "direction", "referenceFrame",
                "altitudeReference", "duration", "count", "radius", "laps", "pitch", "yaw",
                "pattern", "length", "width", "action", "reason", "description")
            val parameters = json.getAsJsonObject("parameters")
            if (!knownFields.containsAll(parameters.keySet())) {
                throw IllegalArgumentException("参数包含未知字段")
            }
            val numericFields = setOf("altitude", "speed", "distance", "radius", "pitch", "yaw", "length", "width", "duration", "count", "laps")
            for ((field, value) in parameters.entrySet()) {
                if (!value.isJsonPrimitive || if (field in numericFields) !value.asJsonPrimitive.isNumber else !value.asJsonPrimitive.isString) {
                    throw IllegalArgumentException("参数 $field 的类型无效")
                }
            }
            val intent = gson.fromJson(json, FlightIntent::class.java)
            val validation = FlightIntentValidator.validate(intent)
            if (!validation.valid) throw IllegalArgumentException(validation.message)
            return intent
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("模型输出不是有效的飞控参数 JSON", e)
        }
    }
}
