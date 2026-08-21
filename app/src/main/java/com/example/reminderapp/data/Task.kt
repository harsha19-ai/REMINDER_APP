package com.example.reminderapp.data

enum class TaskStatus {
    PENDING,
    COMPLETED,
    OVERDUE
}

enum class ImportanceLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val deadlineDateTime: Long, // Epoch milliseconds
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val snoozeCount: Int = 0,
    val importance: ImportanceLevel = ImportanceLevel.MEDIUM
)
