package com.example.msdksample.kami.mission

import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.Waypoint
import java.io.File

class DjiWpmlFlightControlBridge(
    private val exporter: WpmlRouteExporter,
    private val djiGateway: DjiV5MissionGateway? = null
) : FlightControlBridge {
    /**
     * 生成可交付给 DJI V5 任务模块的 KMZ/WPML 文件。
     * @param {RoutePlan} route 当前航线元数据，包含名称、任务、时间和优先级。
     * @param {List<Waypoint>} waypoints 当前航线航点列表，必须至少包含一个航点。
     * @returns {MissionLaunchResult} 文件生成结果；成功时包含 kmzFile。
     */
    override fun prepareMission(
        route: RoutePlan,
        waypoints: List<Waypoint>
    ): MissionLaunchResult {
        if (waypoints.isEmpty()) {
            return MissionLaunchResult(false, "请先添加航点")
        }

        return runCatching {
            exporter.export(route, waypoints)
        }.fold(
            onSuccess = { file ->
                MissionLaunchResult(
                    success = true,
                    message = "KMZ/WPML 航线文件已生成",
                    kmzFile = file
                )
            },
            onFailure = { error ->
                MissionLaunchResult(
                    success = false,
                    message = "生成 KMZ/WPML 失败: ${error.message}"
                )
            }
        )
    }

    /**
     * 启动已生成的 DJI 航线任务。
     * @param {File} kmzFile 已生成的 KMZ/WPML 航线文件。
     * @returns {MissionLaunchResult} 真实 DJI Gateway 的导入/启动结果；未接入 Gateway 时明确失败。
     */
    override fun startPreparedMission(kmzFile: File): MissionLaunchResult {
        if (!kmzFile.exists()) {
            return MissionLaunchResult(false, "航线文件不存在")
        }

        val gateway = djiGateway
            ?: return MissionLaunchResult(
                success = false,
                message = "DJI V5 飞控模块尚未接入，无法启动自动飞行",
                kmzFile = kmzFile
            )

        val importResult = gateway.importKmz(kmzFile)
        if (!importResult.success) {
            return importResult
        }
        return gateway.startMission()
    }
}
