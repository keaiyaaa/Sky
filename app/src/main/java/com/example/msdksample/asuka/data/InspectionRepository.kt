package com.example.msdksample.asuka.data

import android.content.Context
import com.example.msdksample.asuka.database.DatabaseDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class InspectionRepository(context: Context) {

    private val msdkDataSource: InspectionDataSource = MsdkFlightDataSource(context)
    private val databaseDataSource: DatabaseDataSource = DatabaseDataSource(context)

    suspend fun initializeData() {
        databaseDataSource.initializeMockDataIfNeeded()
    }

    fun getAllDateGroupsFlow(): Flow<List<InspectionDateGroup>> {
        return databaseDataSource.getAllDateGroupsFlow()
    }

    fun getAllDatesFlow(): Flow<List<String>> {
        return databaseDataSource.getAllDatesFlow()
    }

    suspend fun getAllDateGroups(): List<InspectionDateGroup> {
        return withContext(Dispatchers.IO) {
            val dbGroups = databaseDataSource.getAllDateGroups()
            if (dbGroups.isEmpty()) {
                val msdkGroups = msdkDataSource.getAllDateGroups()
                databaseDataSource.insertTasks(msdkGroups)
                msdkGroups
            } else {
                dbGroups
            }
        }
    }

    suspend fun getDateGroup(date: String): InspectionDateGroup? {
        return withContext(Dispatchers.IO) {
            var dbGroup = databaseDataSource.getDateGroup(date)
            if (dbGroup == null) {
                val msdkGroup = msdkDataSource.getDateGroup(date)
                if (msdkGroup != null) {
                    databaseDataSource.insertTasks(listOf(msdkGroup))
                    msdkGroup
                } else {
                    null
                }
            } else {
                dbGroup
            }
        }
    }

    suspend fun getLatestDateGroup(): InspectionDateGroup? {
        return withContext(Dispatchers.IO) {
            val dbGroup = databaseDataSource.getAllDateGroups().firstOrNull()
            if (dbGroup != null) {
                dbGroup
            } else {
                msdkDataSource.getLatestDateGroup()
            }
        }
    }

    suspend fun getAllDates(): List<String> {
        return withContext(Dispatchers.IO) {
            val dbDates = databaseDataSource.getAllDates()
            if (dbDates.isEmpty()) {
                msdkDataSource.getAllDates()
            } else {
                dbDates
            }
        }
    }

    suspend fun sortDateGroups(filterType: String): List<InspectionDateGroup> {
        return withContext(Dispatchers.IO) {
            var dateGroups = databaseDataSource.getAllDateGroups()
            if (dateGroups.isEmpty()) {
                dateGroups = msdkDataSource.getAllDateGroups()
            }
            when (filterType) {
                "priority" -> dateGroups.sortedWith { a, b ->
                    val aHasUrgent = a.tasks.any { it.status == "Urgent" }
                    val bHasUrgent = b.tasks.any { it.status == "Urgent" }
                    if (aHasUrgent && !bHasUrgent) -1
                    else if (!aHasUrgent && bHasUrgent) 1
                    else b.date.compareTo(a.date)
                }
                "time-asc" -> dateGroups.sortedBy { it.date }
                else -> dateGroups.sortedByDescending { it.date }
            }
        }
    }

    suspend fun getTask(date: String, taskIndex: Int): InspectionTask? {
        return withContext(Dispatchers.IO) {
            var dateGroup = databaseDataSource.getDateGroup(date)
            if (dateGroup == null) {
                val msdkGroup = msdkDataSource.getDateGroup(date)
                if (msdkGroup != null) {
                    databaseDataSource.insertTasks(listOf(msdkGroup))
                    dateGroup = msdkGroup
                }
            }
            
            if (dateGroup == null) {
                val allGroups = msdkDataSource.getAllDateGroups()
                if (allGroups.isNotEmpty()) {
                    databaseDataSource.insertTasks(allGroups)
                    dateGroup = allGroups.firstOrNull { it.date == date } ?: allGroups[0]
                }
            }
            
            dateGroup?.tasks?.getOrNull(taskIndex)
        }
    }

    suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            databaseDataSource.deleteAllTasks()
            val msdkGroups = msdkDataSource.getAllDateGroups()
            databaseDataSource.insertTasks(msdkGroups)
        }
    }

    suspend fun getTaskCount(): Int {
        return databaseDataSource.getTaskCount()
    }

    suspend fun getUrgentTaskCount(): Int {
        return databaseDataSource.getUrgentTaskCount()
    }
}
