package com.example.msdksample.asuka.database

import android.content.Context
import com.example.msdksample.asuka.database.daos.TaskDao
import com.example.msdksample.asuka.database.daos.AnalysisDao
import com.example.msdksample.asuka.database.entities.TaskEntity
import com.example.msdksample.asuka.database.entities.AnalysisEntity
import com.example.msdksample.asuka.data.AnalysisInfo
import com.example.msdksample.asuka.data.InspectionDateGroup
import com.example.msdksample.asuka.data.InspectionTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DatabaseDataSource(context: Context) {

    private val taskDao: TaskDao = AppDatabase.getInstance(context).taskDao()
    private val analysisDao: AnalysisDao = AppDatabase.getInstance(context).analysisDao()

    suspend fun initializeMockDataIfNeeded() {
        withContext(Dispatchers.IO) {
            if (taskDao.getTaskCount() == 0) {
                val mockData = com.example.msdksample.asuka.data.MockInspectionDataSource().getAllDateGroups()
                insertTasks(mockData)
            }
        }
    }

    suspend fun insertTasks(dateGroups: List<InspectionDateGroup>) {
        withContext(Dispatchers.IO) {
            dateGroups.forEach { dateGroup ->
                dateGroup.tasks.forEach { task ->
                    val taskEntity = convertToEntity(task, dateGroup.date)
                    val taskId = taskDao.insertTask(taskEntity)
                    val analysisEntity = convertToEntity(task.analysis, taskId)
                    analysisDao.insertAnalysis(analysisEntity)
                }
            }
        }
    }

    suspend fun insertTask(task: InspectionTask, date: String) {
        withContext(Dispatchers.IO) {
            val taskEntity = convertToEntity(task, date)
            val taskId = taskDao.insertTask(taskEntity)
            val analysisEntity = convertToEntity(task.analysis, taskId)
            analysisDao.insertAnalysis(analysisEntity)
        }
    }

    fun getAllDateGroupsFlow(): Flow<List<InspectionDateGroup>> {
        return taskDao.getAllTasks().map { taskEntities ->
            convertToDateGroups(taskEntities)
        }
    }

    fun getDateGroupFlow(date: String): Flow<InspectionDateGroup?> {
        return taskDao.getTasksByDate(date).map { taskEntities ->
            if (taskEntities.isEmpty()) {
                null
            } else {
                InspectionDateGroup(
                    date = date,
                    title = "${formatDate(date)} 巡检详情",
                    tasks = convertToTasks(taskEntities)
                )
            }
        }
    }

    fun getAllDatesFlow(): Flow<List<String>> {
        return taskDao.getAllDates()
    }

    suspend fun getAllDateGroups(): List<InspectionDateGroup> {
        return withContext(Dispatchers.IO) {
            val taskEntities = taskDao.getAllTasks().first()
            convertToDateGroups(taskEntities)
        }
    }

    suspend fun getDateGroup(date: String): InspectionDateGroup? {
        return withContext(Dispatchers.IO) {
            val taskEntities = taskDao.getTasksByDate(date).first()
            if (taskEntities.isEmpty()) {
                null
            } else {
                InspectionDateGroup(
                    date = date,
                    title = "${formatDate(date)} 巡检详情",
                    tasks = convertToTasks(taskEntities)
                )
            }
        }
    }

    suspend fun getAllDates(): List<String> {
        return withContext(Dispatchers.IO) {
            taskDao.getAllDates().first()
        }
    }

    suspend fun getTaskCount(): Int {
        return withContext(Dispatchers.IO) {
            taskDao.getTaskCount()
        }
    }

    suspend fun getUrgentTaskCount(): Int {
        return withContext(Dispatchers.IO) {
            taskDao.getUrgentTaskCount()
        }
    }

    suspend fun deleteAllTasks() {
        withContext(Dispatchers.IO) {
            taskDao.deleteAllTasks()
            analysisDao.deleteAllAnalysis()
        }
    }

    private suspend fun convertToTasks(taskEntities: List<TaskEntity>): List<InspectionTask> {
        return taskEntities.map { entity ->
            val analysis = analysisDao.getAnalysisByTaskId(entity.id)
            InspectionTask(
                routeName = entity.routeName,
                name = entity.name,
                time = entity.time,
                status = entity.status,
                priority = entity.priority,
                videos = entity.videos,
                images = entity.images,
                defects = entity.defects,
                videoPoster = entity.videoPoster,
                videoSrc = entity.videoSrc,
                defectId = entity.defectId,
                duration = entity.duration,
                thumbnails = parseThumbnails(entity.thumbnails),
                analysis = analysis?.let {
                    AnalysisInfo(
                        defectType = it.defectType,
                        location = it.location,
                        suggestion = it.suggestion,
                        aiCount = it.aiCount
                    )
                } ?: AnalysisInfo("", "", "", "")
            )
        }
    }

    private suspend fun convertToDateGroups(taskEntities: List<TaskEntity>): List<InspectionDateGroup> {
        val dateMap = taskEntities.groupBy { it.date }
        return dateMap.entries.map { (date, entities) ->
            InspectionDateGroup(
                date = date,
                title = "${formatDate(date)} 巡检详情",
                tasks = convertToTasks(entities)
            )
        }.sortedByDescending { it.date }
    }

    private fun convertToEntity(task: InspectionTask, date: String): TaskEntity {
        return TaskEntity(
            date = date,
            routeName = task.routeName,
            name = task.name,
            time = task.time,
            status = task.status,
            priority = task.priority,
            videos = task.videos,
            images = task.images,
            defects = task.defects,
            videoPoster = task.videoPoster,
            videoSrc = task.videoSrc,
            defectId = task.defectId,
            duration = task.duration,
            thumbnails = task.thumbnails.joinToString("|")
        )
    }

    private fun convertToEntity(analysis: AnalysisInfo, taskId: Long): AnalysisEntity {
        return AnalysisEntity(
            taskId = taskId,
            defectType = analysis.defectType,
            location = analysis.location,
            suggestion = analysis.suggestion,
            aiCount = analysis.aiCount
        )
    }

    private fun parseThumbnails(thumbnails: String): List<String> {
        return if (thumbnails.isEmpty()) {
            emptyList()
        } else {
            thumbnails.split("|")
        }
    }

    private fun formatDate(dateStr: String): String {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            return "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
        }
        return dateStr
    }
}
