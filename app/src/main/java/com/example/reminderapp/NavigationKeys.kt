package com.example.reminderapp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object AddTask : NavKey

@Serializable data class TaskDetails(val taskId: Long) : NavKey

@Serializable data class EditTask(val taskId: Long) : NavKey
