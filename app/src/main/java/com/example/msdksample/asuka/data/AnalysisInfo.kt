package com.example.msdksample.asuka.data

data class AnalysisInfo(
    val defectType: String,
    val location: String,
    val suggestion: String,
    val aiCount: String,
    // ===== AI 真实分析结果（analyzedAt=0 表示预置/种子数据，未经真实分析）=====
    val confidence: Double = 0.0,
    val tokenCost: Int = 0,
    val totalTokens: Int = 0,
    val analysisType: String = "",
    val analyzedAt: Long = 0L
)