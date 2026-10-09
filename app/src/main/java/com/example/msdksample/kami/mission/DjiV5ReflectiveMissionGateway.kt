package com.example.msdksample.kami.mission

import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DjiV5ReflectiveMissionGateway(
    private val timeoutSeconds: Long = 90
) : DjiV5MissionGateway {
    private var telemetryListener: AircraftTelemetryListener? = null
    private var currentMissionName: String? = null
    private var currentMissionPath: String? = null

    override fun setTelemetryListener(listener: AircraftTelemetryListener?) {
        telemetryListener = listener
    }

    override fun importKmz(kmzFile: File): MissionLaunchResult {
        if (!kmzFile.exists()) {
            return MissionLaunchResult(false, "航线文件不存在")
        }
        val manager = waypointMissionManager()
            ?: return MissionLaunchResult(false, "DJI 航线任务管理器不可用: ${DjiSdkManager.state.userMessage()}", kmzFile)
        if (!DjiSdkManager.isRegistered()) {
            return MissionLaunchResult(false, "DJI SDK 尚未注册成功，请检查 App Key、网络和飞行器连接", kmzFile)
        }

        currentMissionName = kmzFile.nameWithoutExtension
        currentMissionPath = kmzFile.absolutePath
        val callbackClass = Class.forName(COMPLETION_WITH_PROGRESS_CLASS)
        val method = manager.javaClass.getMethod(
            "pushKMZFileToAircraft",
            String::class.java,
            callbackClass
        )

        return awaitCallback(kmzFile, callbackClass, "KMZ 已上传到飞行器") { callback ->
            method.invoke(manager, kmzFile.absolutePath, callback)
        }
    }

    override fun startMission(): MissionLaunchResult {
        val missionName = currentMissionName
            ?: return MissionLaunchResult(false, "请先生成并上传 KMZ 航线")
        val manager = waypointMissionManager()
            ?: return MissionLaunchResult(false, "DJI 航线任务管理器不可用: ${DjiSdkManager.state.userMessage()}")
        val callbackClass = Class.forName(COMPLETION_CLASS)
        val waylineIds = getAvailableWaylineIds(manager)
        val method = manager.javaClass.getMethod(
            "startMission",
            String::class.java,
            List::class.java,
            callbackClass
        )

        return awaitCallback(null, callbackClass, "自动飞行任务已启动") { callback ->
            method.invoke(manager, missionName, waylineIds, callback)
        }
    }

    override fun stopMission(): MissionLaunchResult {
        val missionName = currentMissionName
            ?: return MissionLaunchResult(false, "当前没有正在管理的 DJI 航线任务")
        val manager = waypointMissionManager()
            ?: return MissionLaunchResult(false, "DJI 航线任务管理器不可用: ${DjiSdkManager.state.userMessage()}")
        val callbackClass = Class.forName(COMPLETION_CLASS)
        val method = manager.javaClass.getMethod("stopMission", String::class.java, callbackClass)

        return awaitCallback(null, callbackClass, "自动飞行任务已停止") { callback ->
            method.invoke(manager, missionName, callback)
        }
    }

    private fun waypointMissionManager(): Any? {
        return runCatching {
            val managerClass = Class.forName(WAYPOINT_MISSION_MANAGER_CLASS)
            managerClass.getMethod("getInstance").invoke(null)
        }.getOrNull()
    }

    private fun getAvailableWaylineIds(manager: Any): List<Int> {
        val missionPath = currentMissionPath ?: return listOf(0)
        return runCatching {
            val value = manager.javaClass
                .getMethod("getAvailableWaylineIDs", String::class.java)
                .invoke(manager, missionPath) as? List<*>
            value.orEmpty().mapNotNull { item ->
                when (item) {
                    is Number -> item.toInt()
                    is String -> item.toIntOrNull()
                    else -> null
                }
            }.ifEmpty { listOf(0) }
        }.getOrDefault(listOf(0))
    }

    private fun awaitCallback(
        kmzFile: File?,
        callbackClass: Class<*>,
        successMessage: String,
        action: (Any) -> Unit
    ): MissionLaunchResult {
        val latch = CountDownLatch(1)
        var result: MissionLaunchResult? = null
        val callback = Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass),
            MissionCallbackHandler(kmzFile, successMessage) { callbackResult ->
                result = callbackResult
                latch.countDown()
            }
        )

        return runCatching {
            action(callback)
            val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)
            if (completed) {
                result ?: MissionLaunchResult(false, "DJI 任务返回了空结果", kmzFile)
            } else {
                MissionLaunchResult(false, "等待 DJI 任务响应超时，请检查飞行器连接", kmzFile)
            }
        }.getOrElse { error ->
            MissionLaunchResult(false, "调用 DJI 任务接口失败: ${error.readableMessage()}", kmzFile)
        }
    }

    private class MissionCallbackHandler(
        private val kmzFile: File?,
        private val successMessage: String,
        private val onResult: (MissionLaunchResult) -> Unit
    ) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            when (method.name) {
                "onSuccess" -> onResult(MissionLaunchResult(true, successMessage, kmzFile))
                "onFailure" -> onResult(
                    MissionLaunchResult(
                        success = false,
                        message = "DJI 任务失败: ${args.firstErrorMessage()}",
                        kmzFile = kmzFile
                    )
                )
            }
            return null
        }
    }

    companion object {
        private const val WAYPOINT_MISSION_MANAGER_CLASS =
            "dji.v5.manager.aircraft.waypoint3.WaypointMissionManager"
        private const val COMPLETION_CLASS =
            "dji.v5.common.callback.CommonCallbacks\$CompletionCallback"
        private const val COMPLETION_WITH_PROGRESS_CLASS =
            "dji.v5.common.callback.CommonCallbacks\$CompletionCallbackWithProgress"
    }
}
