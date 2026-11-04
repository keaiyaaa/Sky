package com.example.msdksample.asuka.data

data class InspectionTask(
    val routeName: String,
    val name: String,
    val time: String,
    val status: String,
    val priority: Int,
    val videos: Int,
    val images: Int,
    val defects: Int,
    val videoPoster: String,
    val videoSrc: String,
    val defectId: String,
    val duration: String,
    val thumbnails: List<String>,
    val analysis: AnalysisInfo,
    /** 数据库主键；0 表示非数据库来源（如 Mock/MSDK 实时数据） */
    val id: Long = 0,
    /** 任务所属日期（yyyy-MM-dd）；按 ID 加载详情时用于标题 */
    val date: String = ""
)