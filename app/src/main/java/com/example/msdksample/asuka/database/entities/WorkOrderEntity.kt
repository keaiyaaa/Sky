package com.example.msdksample.asuka.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 工单实体（F17）。
 * 记录从任务详情创建的工单，与任务通过 taskId 关联。
 */
@Entity(tableName = "work_orders")
data class WorkOrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 关联任务的数据库主键 */
    val taskId: Long,

    /** 冗余任务日期，便于列表按日期分组显示 */
    val taskDate: String,

    /** 任务名称（冗余，列表直接展示用） */
    val taskName: String,

    /** 工单标题 */
    val title: String,

    /** 缺陷描述/工单内容 */
    val description: String,

    /** 优先级：Low / Medium / High / Critical */
    val priority: String,

    /** 状态：Open / InProgress / Resolved / Closed */
    val status: String = "Open",

    /** 负责人/受理人（可空） */
    val assignee: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
