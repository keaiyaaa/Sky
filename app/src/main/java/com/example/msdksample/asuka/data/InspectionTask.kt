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
    val analysis: AnalysisInfo
)