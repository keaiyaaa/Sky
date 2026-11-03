package com.example.msdksample.aerial

data class FlightIntentValidation(val valid: Boolean, val message: String, val executableNow: Boolean)

/** Validates the app's command vocabulary, not aircraft capability or airspace permission. */
object FlightIntentValidator {
    // Conservative app preview bounds; actual aircraft limits must be queried separately.
    private const val MAX_HEIGHT_METERS = 120.0
    private const val MAX_DISTANCE_METERS = 500.0
    private const val MAX_RADIUS_METERS = 200.0
    private const val MAX_SPEED_METERS_PER_SECOND = 15.0
    private const val MAX_LAPS = 10
    private val simpleActions = setOf("takeoff", "land", "return_home", "stop_takeoff",
        "stop_landing", "confirm_landing", "stop_return_home")
    private val allowedFields = mapOf(
        "hover" to setOf("duration"), "photo" to setOf("count"), "video" to setOf("action"),
        "fly_to" to setOf("direction", "distance", "speed", "referenceFrame"),
        "change_altitude" to setOf("altitude", "speed", "altitudeReference"),
        "rotate" to setOf("yaw", "referenceFrame"),
        "set_return_home_height" to setOf("altitude", "altitudeReference"),
        "gimbal_control" to setOf("pitch", "yaw"),
        "orbit" to setOf("radius", "speed", "laps", "direction"),
        "waypoint" to setOf("pattern", "altitude", "speed", "length", "width", "altitudeReference")
    )
    fun validate(intent: FlightIntent): FlightIntentValidation {
        val p = intent.parameters
        val numbers = listOf(p.altitude, p.speed, p.distance, p.radius, p.pitch, p.yaw, p.length, p.width)
        if (numbers.any { it != null && !it.isFinite() }) return reject("参数包含非有限数值")
        if (listOf(p.duration, p.count, p.laps).any { it != null && it <= 0 }) return reject("次数或时长必须大于零")
        if (p.altitude != null && p.altitude !in 0.0..MAX_HEIGHT_METERS ||
            p.speed != null && p.speed !in 0.0..MAX_SPEED_METERS_PER_SECOND ||
            listOf(p.distance, p.length, p.width).any { it != null && it !in 0.0..MAX_DISTANCE_METERS } ||
            p.radius != null && p.radius !in 0.0..MAX_RADIUS_METERS ||
            p.laps != null && p.laps > MAX_LAPS
        ) return reject("参数超出本地预览范围")
        val activeFields = mapOf(
            "altitude" to p.altitude, "speed" to p.speed, "distance" to p.distance,
            "direction" to p.direction, "referenceFrame" to p.referenceFrame,
            "altitudeReference" to p.altitudeReference, "duration" to p.duration,
            "count" to p.count, "radius" to p.radius, "laps" to p.laps,
            "pitch" to p.pitch, "yaw" to p.yaw, "pattern" to p.pattern,
            "length" to p.length, "width" to p.width, "action" to p.action,
            "reason" to p.reason, "description" to p.description
        ).filterValues { it != null }.keys
        if (intent.flightType in simpleActions) {
            if (activeFields.isNotEmpty()) {
                return reject("此动作不接受附加飞行参数，请拆分指令")
            }
            return accept(intent.flightType in setOf("takeoff", "land", "return_home"))
        }
        if (intent.flightType == "unknown") return reject(p.reason ?: "无法识别指令")
        val permitted = allowedFields[intent.flightType] ?: return reject("不支持的动作类型：${intent.flightType}")
        if (!permitted.containsAll(activeFields)) return reject("包含与动作无关的参数")
        return when (intent.flightType) {
            "hover" -> if (p.duration == null || p.duration in 1..300) accept(p.duration == null)
                else reject("悬停时长超出本地预览范围")
            "photo" -> if (p.count == null || p.count == 1) accept(true)
                else reject("当前只支持单张拍照")
            "video" -> if (p.action in setOf("start", "stop")) accept(true)
                else reject("录像动作必须为 start 或 stop")
            "fly_to" -> if (p.referenceFrame == "body" && p.direction in setOf("forward", "backward", "left", "right") &&
                p.distance != null && p.distance > 0 && p.speed != null && p.speed > 0) accept(false)
                else reject("方向移动需要机体坐标系、方向、正距离和正速度")
            "change_altitude" -> if (p.altitudeReference == "takeoff" && p.altitude != null && p.altitude > 0 && p.speed != null && p.speed > 0)
                accept(false) else reject("改变高度需要相对起飞点的目标高度和正速度")
            "rotate" -> if (p.referenceFrame == "body" && p.yaw != null && p.yaw in -180.0..180.0)
                accept(false) else reject("旋转需要机体坐标系和 -180 至 180 度角")
            "set_return_home_height" -> if (p.altitudeReference == "takeoff" && p.altitude != null && p.altitude > 0)
                accept(false) else reject("返航高度必须相对起飞点且大于零")
            "gimbal_control" -> if (p.pitch != null && p.pitch in -90.0..30.0 &&
                (p.yaw == null || p.yaw in -180.0..180.0)) accept(false)
                else reject("云台角度不在预览范围")
            "orbit" -> if (p.radius != null && p.radius > 0 && p.speed != null && p.speed > 0 &&
                p.laps != null && p.direction in setOf("clockwise", "counterclockwise")) accept(false)
                else reject("环绕缺少半径、速度、圈数或方向")
            "waypoint" -> if (p.pattern in setOf("line", "rectangle", "zigzag") && p.altitudeReference == "takeoff" &&
                p.altitude != null && p.altitude > 0 && p.speed != null && p.speed > 0 && p.length != null && p.length > 0 &&
                (p.pattern != "rectangle" || p.width != null && p.width > 0)) accept(false)
                else reject("航线缺少形状、高度、速度或尺寸")
            else -> reject("不支持的动作类型：${intent.flightType}")
        }
    }

    private fun accept(executableNow: Boolean) = FlightIntentValidation(true, "参数格式有效", executableNow)
    private fun reject(message: String) = FlightIntentValidation(false, message, false)
}
