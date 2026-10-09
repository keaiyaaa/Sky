package com.example.msdksample.asuka.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
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
    val thumbnails: String,
    val createdAt: Long = System.currentTimeMillis()
)
