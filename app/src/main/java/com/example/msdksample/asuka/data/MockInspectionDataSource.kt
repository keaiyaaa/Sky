package com.example.msdksample.asuka.data

class MockInspectionDataSource : InspectionDataSource {

    private val defaultInspectionData: Map<String, InspectionDateGroup> = mapOf(
        "2026-04-12" to InspectionDateGroup(
            date = "2026-04-12",
            title = "2026年4月12日 巡检详情",
            tasks = listOf(
                InspectionTask(
                    routeName = "A区变电站巡检",
                    name = "变电站设备缺陷排查",
                    time = "10:30:15 - 11:45:32",
                    status = "Urgent",
                    priority = 3,
                    videos = 3,
                    images = 24,
                    defects = 2,
                    videoPoster = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=1000&auto=format&fit=crop",
                    videoSrc = "",
                    defectId = "#882",
                    duration = "02:34",
                    thumbnails = listOf(
                        "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=800&auto=format&fit=crop",
                        "https://images.unsplash.com/photo-1509062522246-375597727027?q=80&w=800&auto=format&fit=crop",
                        "https://images.unsplash.com/photo-1635805737707-575885ab0820?q=80&w=800&auto=format&fit=crop"
                    ),
                    analysis = AnalysisInfo(
                        defectType = "关键紧固件松动",
                        location = "31.23°N, 121.47°E (塔基 04B)",
                        suggestion = "建议近期安排人工地面核验，并对螺栓进行紧固处理，该隐患等级评定为\"紧急\"。",
                        aiCount = "2/3"
                    )
                ),
                InspectionTask(
                    routeName = "B区线塔巡检",
                    name = "线塔绝缘子积污监测",
                    time = "14:00:05 - 15:20:18",
                    status = "Normal",
                    priority = 1,
                    videos = 0,
                    images = 12,
                    defects = 0,
                    videoPoster = "https://images.unsplash.com/photo-1509062522246-375597727027?q=80&w=1000&auto=format&fit=crop",
                    videoSrc = "",
                    defectId = "",
                    duration = "",
                    thumbnails = listOf(
                        "https://images.unsplash.com/photo-1509062522246-375597727027?q=80&w=800&auto=format&fit=crop",
                        "https://images.unsplash.com/photo-1635805737707-575885ab0820?q=80&w=800&auto=format&fit=crop"
                    ),
                    analysis = AnalysisInfo(
                        defectType = "无异常",
                        location = "31.25°N, 121.50°E (线塔 A12)",
                        suggestion = "本次巡检未发现异常情况，设备运行正常。建议继续保持定期巡检。",
                        aiCount = "3/3"
                    )
                )
            )
        ),
        "2026-04-10" to InspectionDateGroup(
            date = "2026-04-10",
            title = "2026年4月10日 巡检详情",
            tasks = listOf(
                InspectionTask(
                    routeName = "C区防火通道巡检",
                    name = "森林防火通道例行检查",
                    time = "09:00:00 - 10:30:45",
                    status = "Normal",
                    priority = 1,
                    videos = 0,
                    images = 56,
                    defects = 0,
                    videoPoster = "https://images.unsplash.com/photo-1635805737707-575885ab0820?q=80&w=1000&auto=format&fit=crop",
                    videoSrc = "",
                    defectId = "",
                    duration = "",
                    thumbnails = listOf(
                        "https://images.unsplash.com/photo-1635805737707-575885ab0820?q=80&w=800&auto=format&fit=crop",
                        "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=800&auto=format&fit=crop",
                        "https://images.unsplash.com/photo-1509062522246-375597727027?q=80&w=800&auto=format&fit=crop",
                        "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=800&auto=format&fit=crop"
                    ),
                    analysis = AnalysisInfo(
                        defectType = "无异常",
                        location = "31.20°N, 121.45°E (防火通道 B区)",
                        suggestion = "防火通道畅通，周边植被状况良好，无火灾隐患。",
                        aiCount = "3/3"
                    )
                )
            )
        )
    )

    override fun getAllDateGroups(): List<InspectionDateGroup> {
        return defaultInspectionData.values.toList()
    }

    override fun getDateGroup(date: String): InspectionDateGroup? {
        return defaultInspectionData[date]
    }

    override fun getLatestDateGroup(): InspectionDateGroup? {
        return defaultInspectionData.values.maxByOrNull { it.date }
    }

    override fun getAllDates(): List<String> {
        return defaultInspectionData.keys.toList()
    }

    override fun sortDateGroups(filterType: String): List<InspectionDateGroup> {
        val groups = defaultInspectionData.values.toList()
        return when (filterType) {
            "priority" -> groups.sortedWith { a, b ->
                val aHasUrgent = a.tasks.any { it.status == "Urgent" }
                val bHasUrgent = b.tasks.any { it.status == "Urgent" }
                if (aHasUrgent && !bHasUrgent) -1
                else if (!aHasUrgent && bHasUrgent) 1
                else b.date.compareTo(a.date)
            }
            "time-asc" -> groups.sortedBy { it.date }
            else -> groups.sortedByDescending { it.date }
        }
    }

    override fun getTask(date: String, taskIndex: Int): InspectionTask? {
        return defaultInspectionData[date]?.tasks?.getOrNull(taskIndex)
    }
}
