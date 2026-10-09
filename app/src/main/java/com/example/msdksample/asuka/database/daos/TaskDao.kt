package com.example.msdksample.asuka.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.msdksample.asuka.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY date DESC, createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY createdAt DESC")
    fun getTasksByDate(date: String): Flow<List<TaskEntity>>

    @Query("SELECT DISTINCT date FROM tasks ORDER BY date DESC")
    fun getAllDates(): Flow<List<String>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE date = :date")
    suspend fun deleteTasksByDate(date: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE date = :date")
    suspend fun getTaskCountByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'Urgent'")
    suspend fun getUrgentTaskCount(): Int
}
