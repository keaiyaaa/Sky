package com.example.msdksample.data.logs

import java.text.SimpleDateFormat
import java.util.*

object FlightLogPolicy {
    const val LEGACY_OWNER = "legacy-unassigned"
    val sources = listOf("DEVICE", "DJI_SIMULATOR", "LOCAL_SIMULATION", "MANUAL", "UNKNOWN")
    fun sourceLabel(code: String) = when(code) {
        "DEVICE" -> "设备上报"; "DJI_SIMULATOR" -> "DJI模拟器"; "LOCAL_SIMULATION" -> "本地演示"
        "MANUAL" -> "人工记录"; else -> "来源未确认"
    }
    fun category(code: String) = when(code) { "连接" -> "CONNECTION"; "设置" -> "SETTING"; "指令" -> "COMMAND"; "任务" -> "MISSION"; else -> code }
    fun categoryLabel(code: String) = when(code) { "CONNECTION" -> "连接"; "SETTING" -> "设置"; "COMMAND" -> "指令"; "MISSION" -> "任务"; else -> code }
    fun clean(value: String): String = value
        .replace(Regex("Bearer\\s+[^\\s,;]+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
        .replace(Regex("(password|passwd|accessToken|refreshToken|authorization|db_password|密码|令牌)[\"']?\\s*[=:：]\\s*(\"[^\"]*\"|'[^']*'|[^\\s,;]+)", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]}=[REDACTED]" }
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]")
        .replace(Regex("(?<![0-9])1[3-9][0-9]{9}(?![0-9])"), "[PHONE]")
    fun utc(time: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone=TimeZone.getTimeZone("UTC") }.format(Date(time))
    fun parseUtc(value: String): Long {
        require(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z").matches(value))
        val parts=value.removeSuffix("Z").split('.')
        val millis=parts.getOrNull(1)?.padEnd(3,'0')?.take(3) ?: "000"
        val normalized="${parts[0]}.$millis" + "Z"
        val format=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("UTC"); isLenient=false }
        return requireNotNull(format.parse(normalized)).time
    }
    fun day(value: String, exclusiveEnd: Boolean=false): Long? {
        if(value.isBlank()) return null
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) { "日期使用yyyy-MM-dd格式" }
        val format=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("Asia/Shanghai"); isLenient=false }
        val parsed=requireNotNull(format.parse(value))
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply { time=parsed; if(exclusiveEnd) add(Calendar.DAY_OF_MONTH,1) }.timeInMillis
    }
}
