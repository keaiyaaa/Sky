package com.example.msdksample.asuka.data

data class InspectionDateGroup(
    val date: String,
    val title: String,
    val tasks: List<InspectionTask>
)