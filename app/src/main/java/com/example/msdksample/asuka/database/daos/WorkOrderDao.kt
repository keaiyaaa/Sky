package com.example.msdksample.asuka.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.msdksample.asuka.database.entities.WorkOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderDao {

    @Insert
    suspend fun insert(workOrder: WorkOrderEntity): Long

    @Update
    suspend fun update(workOrder: WorkOrderEntity): Int

    @Query("SELECT * FROM work_orders WHERE id = :id")
    suspend fun getById(id: Long): WorkOrderEntity?

    @Query("SELECT * FROM work_orders WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getByTaskId(taskId: Long): Flow<List<WorkOrderEntity>>

    @Query("SELECT * FROM work_orders WHERE taskId = :taskId ORDER BY createdAt DESC")
    suspend fun getByTaskIdOnce(taskId: Long): List<WorkOrderEntity>

    @Query("SELECT * FROM work_orders ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WorkOrderEntity>>

    @Query("SELECT * FROM work_orders ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<WorkOrderEntity>

    @Query("SELECT COUNT(*) FROM work_orders WHERE taskId = :taskId")
    suspend fun countByTaskId(taskId: Long): Int

    @Query("UPDATE work_orders SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long): Int

    @Query("DELETE FROM work_orders WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
