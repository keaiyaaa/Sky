package com.example.msdksample.asuka.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.msdksample.asuka.database.entities.AnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {

    @Query("SELECT * FROM analysis WHERE taskId = :taskId")
    suspend fun getAnalysisByTaskId(taskId: Long): AnalysisEntity?

    @Query("SELECT * FROM analysis")
    fun getAllAnalysis(): Flow<List<AnalysisEntity>>

    @Insert
    suspend fun insertAnalysis(analysis: AnalysisEntity)

    @Insert
    suspend fun insertAnalysisList(analysisList: List<AnalysisEntity>)

    @Update
    suspend fun updateAnalysis(analysis: AnalysisEntity)

    @Delete
    suspend fun deleteAnalysis(analysis: AnalysisEntity)

    @Query("DELETE FROM analysis WHERE taskId = :taskId")
    suspend fun deleteAnalysisByTaskId(taskId: Long)

    @Query("DELETE FROM analysis")
    suspend fun deleteAllAnalysis()
}
