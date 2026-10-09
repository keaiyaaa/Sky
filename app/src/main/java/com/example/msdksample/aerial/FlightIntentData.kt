package com.example.msdksample.aerial
import com.google.gson.annotations.SerializedName
/**
LLM 返回的飞行意图完整数据 */ data class FlightIntent( @SerializedName("flightType") val flightType: String,
                                                       @SerializedName("parameters") val parameters: FlightParameters )
/**
飞行参数 */ data class FlightParameters( @SerializedName("altitude") val altitude: Double? = null,
                                         @SerializedName("speed") val speed: Double? = null,
                                         @SerializedName("distance") val distance: Double? = null,
                                         @SerializedName("direction") val direction: String? = null,
                                         @SerializedName("duration") val duration: Int? = null,
                                         @SerializedName("count") val count: Int? = null,
                                         @SerializedName("radius") val radius: Double? = null,
                                         @SerializedName("laps") val laps: Int? = null,
                                         @SerializedName("pitch") val pitch: Double? = null,
                                         @SerializedName("yaw") val yaw: Double? = null,
                                         @SerializedName("pattern") val pattern: String? = null,
                                         @SerializedName("length") val length: Double? = null,
                                         @SerializedName("width") val width: Double? = null,
                                         @SerializedName("action") val action: String? = null,
                                         @SerializedName("reason") val reason: String? = null,
                                         @SerializedName("description") val description: String? = null )
/**
LLM 分析结果 */ sealed class LLMResult { data class Success( val intent: FlightIntent, val summary: String ) : LLMResult()
    data class Error( val message: String ) : LLMResult()
    data class Unknown( val reason: String ) : LLMResult() }