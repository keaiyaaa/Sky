package com.example.msdksample.asuka.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MsdkFlightDataSource(private val context: Context) : InspectionDataSource {

    private val TAG = "MsdkFlightDataSource"

    private var cachedDateGroups: List<InspectionDateGroup> = emptyList()
    private var lastUpdateTime: Long = 0

    override fun getAllDateGroups(): List<InspectionDateGroup> {
        updateCacheIfNeeded()
        return cachedDateGroups
    }

    override fun getDateGroup(date: String): InspectionDateGroup? {
        updateCacheIfNeeded()
        return cachedDateGroups.find { it.date == date }
    }

    override fun getLatestDateGroup(): InspectionDateGroup? {
        updateCacheIfNeeded()
        return cachedDateGroups.maxByOrNull { it.date }
    }

    override fun getAllDates(): List<String> {
        updateCacheIfNeeded()
        return cachedDateGroups.map { it.date }
    }

    override fun sortDateGroups(filterType: String): List<InspectionDateGroup> {
        updateCacheIfNeeded()
        return when (filterType) {
            "priority" -> cachedDateGroups.sortedWith { a, b ->
                val aHasUrgent = a.tasks.any { it.status == "Urgent" }
                val bHasUrgent = b.tasks.any { it.status == "Urgent" }
                if (aHasUrgent && !bHasUrgent) -1
                else if (!aHasUrgent && bHasUrgent) 1
                else b.date.compareTo(a.date)
            }
            "time-asc" -> cachedDateGroups.sortedBy { it.date }
            else -> cachedDateGroups.sortedByDescending { it.date }
        }
    }

    override fun getTask(date: String, taskIndex: Int): InspectionTask? {
        return getDateGroup(date)?.tasks?.getOrNull(taskIndex)
    }

    private fun updateCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > 60000) {
            cachedDateGroups = fetchFlightRecords()
            lastUpdateTime = now
        }
    }

    private fun fetchFlightRecords(): List<InspectionDateGroup> {
        val dateGroupMap = mutableMapOf<String, MutableList<InspectionTask>>()

        try {
            val localRecords = getLocalFlightRecords()
            if (localRecords.isNotEmpty()) {
                Log.i(TAG, "Found ${localRecords.size} local flight records")
                localRecords.forEach { record ->
                    val tasks = dateGroupMap.getOrPut(record.date) { mutableListOf() }
                    tasks.add(convertRecordToTask(record))
                }
                return dateGroupMap.entries.map { (date, tasks) ->
                    InspectionDateGroup(
                        date = date,
                        title = "${formatDate(date)} 巡检详情",
                        tasks = tasks
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching flight records", e)
        }

        return getMockDataAsFallback()
    }

    private fun getLocalFlightRecords(): List<LocalFlightRecord> {
        val records = mutableListOf<LocalFlightRecord>()

        val djiDirs = listOf(
            File(Environment.getExternalStorageDirectory(), "DJI/dji.go.v5/FlightRecord"),
            File(Environment.getExternalStorageDirectory(), "DJI/FlightRecord"),
            File(Environment.getExternalStorageDirectory(), "DJI/dji.go.v4/FlightRecord"),
            File(Environment.getExternalStorageDirectory(), "Android/data/com.dji.gosdk/files/FlightRecord")
        )

        djiDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                Log.i(TAG, "Scanning flight record directory: ${dir.path}")
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension == "txt" || file.extension == "dat")) {
                        try {
                            val flightLog = parseLocalFlightLog(file)
                            if (flightLog != null) {
                                records.add(flightLog)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse flight log: ${file.name}", e)
                        }
                    }
                }
            }
        }

        return records.sortedByDescending { it.date }
    }

    private fun parseLocalFlightLog(file: File): LocalFlightRecord? {
        val content = file.readText()

        val startTime = extractValue(content, "GStartTime")
            ?: extractValue(content, "StartTime")
            ?: extractValue(content, "Start")
        val endTime = extractValue(content, "GEndTime")
            ?: extractValue(content, "EndTime")
            ?: extractValue(content, "End")
        val routeName = extractValue(content, "FlightName")
            ?: extractValue(content, "MissionName")
            ?: "航线巡检"
        val latitude = extractValue(content, "GLatitude")
            ?: extractValue(content, "Latitude")
            ?: "31.23"
        val longitude = extractValue(content, "GLongitude")
            ?: extractValue(content, "Longitude")
            ?: "121.47"
        val distance = extractValue(content, "Distance")?.toDoubleOrNull() ?: 0.0
        val duration = extractValue(content, "Duration")?.toLongOrNull() ?: 0L
        val maxAltitude = extractValue(content, "MaxAltitude")?.toDoubleOrNull() ?: 0.0

        val dateStr = startTime?.let { parseDateFromTimestamp(it) } ?: getCurrentDate()

        return LocalFlightRecord(
            date = dateStr,
            time = formatTimeRange(startTime, endTime),
            routeName = routeName,
            location = "$latitude°N, $longitude°E",
            videoCount = countMediaFiles(file.parentFile, "mp4"),
            imageCount = countMediaFiles(file.parentFile, "jpg") + countMediaFiles(file.parentFile, "jpeg") + countMediaFiles(file.parentFile, "png"),
            duration = formatDuration(duration),
            maxAltitude = maxAltitude,
            distance = distance
        )
    }

    private fun countMediaFiles(dir: File?, extension: String): Int {
        if (dir == null || !dir.exists()) return 0
        return dir.listFiles { f -> f.isFile && f.extension.equals(extension, ignoreCase = true) }?.size ?: 0
    }

    private fun extractValue(content: String, key: String): String? {
        val patterns = listOf(
            Regex("$key[:=]\\s*([^\n]+)"),
            Regex("\"$key\"\\s*[:=]\\s*\"([^\"]+)\""),
            Regex("$key\\s+([^\\s]+)")
        )
        patterns.forEach { regex ->
            val match = regex.find(content)
            if (match != null) {
                return match.groupValues.getOrNull(1)?.trim()
            }
        }
        return null
    }

    private fun convertRecordToTask(record: LocalFlightRecord): InspectionTask {
        val hasDefects = record.imageCount > 10 || record.distance > 5000
        val status = if (hasDefects) "Urgent" else "Normal"
        val defects = if (hasDefects) 1 else 0

        return InspectionTask(
            routeName = record.routeName,
            name = "无人机巡检任务",
            time = record.time,
            status = status,
            priority = if (status == "Urgent") 3 else 1,
            videos = record.videoCount,
            images = record.imageCount,
            defects = defects,
            videoPoster = "",
            videoSrc = "",
            defectId = if (defects > 0) "#${System.currentTimeMillis().toString().takeLast(3)}" else "",
            duration = record.duration,
            thumbnails = getDefaultThumbnails(),
            analysis = AnalysisInfo(
                defectType = if (hasDefects) "疑似设备异常" else "无异常",
                location = record.location,
                suggestion = if (hasDefects) "建议人工核验巡检区域，确认是否存在设备缺陷。" else "本次巡检未发现异常情况，设备运行正常。",
                aiCount = "3/3"
            )
        )
    }

    private fun getDefaultThumbnails(): List<String> {
        return listOf(
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=800&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1509062522246-375597727027?q=80&w=800&auto=format&fit=crop"
        )
    }

    private fun getMockDataAsFallback(): List<InspectionDateGroup> {
        return MockInspectionDataSource().getAllDateGroups()
    }

    private fun formatDate(dateStr: String): String {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            return "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
        }
        return dateStr
    }

    private fun parseDateFromTimestamp(timestamp: String): String {
        return try {
            val ts = timestamp.toLong()
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
        } catch (e: Exception) {
            getCurrentDate()
        }
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun formatTimeRange(startTime: String?, endTime: String?): String {
        return try {
            val startTs = startTime?.toLong() ?: 0
            val endTs = endTime?.toLong() ?: 0
            val startDate = Date(startTs)
            val endDate = Date(endTs)
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            "${sdf.format(startDate)} - ${sdf.format(endDate)}"
        } catch (e: Exception) {
            "00:00:00 - 00:00:00"
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs < 60) {
            val seconds = (durationMs / 1000).toInt()
            return String.format("00:%02d", seconds)
        }
        val minutes = (durationMs / 60000).toInt()
        val seconds = ((durationMs % 60000) / 1000).toInt()
        return String.format("%02d:%02d", minutes, seconds)
    }

    private data class LocalFlightRecord(
        val date: String,
        val time: String,
        val routeName: String,
        val location: String,
        val videoCount: Int,
        val imageCount: Int,
        val duration: String,
        val maxAltitude: Double = 0.0,
        val distance: Double = 0.0
    )
}
