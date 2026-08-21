package com.example.reminderapp.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reminderapp.data.DataRepository
import com.example.reminderapp.data.Task
import com.example.reminderapp.data.TaskStatus
import com.example.reminderapp.receiver.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar

class MainScreenViewModel(private val repository: DataRepository) : ViewModel() {
    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow(FilterType.PENDING)

    private val allTasksFlow = repository.getTasks(statusFilter = null)

    val uiState: StateFlow<MainScreenUiState> = combine(
        allTasksFlow,
        searchQuery,
        selectedFilter
    ) { tasks, query, filter ->
        val now = System.currentTimeMillis()
        
        // 1. Calculate Stats (from the full tasks list, ignore search query/status filters)
        val totalPending = tasks.count { it.status == TaskStatus.PENDING }
        val totalOverdue = tasks.count { it.status == TaskStatus.OVERDUE }
        val totalCompleted = tasks.count { it.status == TaskStatus.COMPLETED }
        val totalDueToday = tasks.count { it.status == TaskStatus.PENDING && isToday(it.deadlineDateTime) }

        val stats = TaskStats(
            pendingCount = totalPending,
            dueTodayCount = totalDueToday,
            overdueCount = totalOverdue,
            completedCount = totalCompleted
        )

        // 2. Filter tasks based on Search Query and Status Filter
        var filteredTasks = tasks

        // Search text filter
        if (query.isNotBlank()) {
            filteredTasks = filteredTasks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }

        // Dropdowns / status filters
        filteredTasks = when (filter) {
            FilterType.ALL -> filteredTasks
            FilterType.PENDING -> filteredTasks.filter { it.status == TaskStatus.PENDING }
            FilterType.DUE_TODAY -> filteredTasks.filter { it.status == TaskStatus.PENDING && isToday(it.deadlineDateTime) }
            FilterType.UPCOMING -> filteredTasks.filter { 
                it.status == TaskStatus.PENDING && 
                !isToday(it.deadlineDateTime) && 
                !isTomorrow(it.deadlineDateTime) && 
                it.deadlineDateTime > now 
            }
            FilterType.OVERDUE -> filteredTasks.filter { it.status == TaskStatus.OVERDUE }
            FilterType.COMPLETED -> filteredTasks.filter { it.status == TaskStatus.COMPLETED }
        }

        MainScreenUiState.Success(filteredTasks, stats)
    }.catch {
        MainScreenUiState.Error(it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

    fun markAsDone(context: Context, task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                status = TaskStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val success = repository.updateTask(updatedTask)
            if (success) {
                ReminderScheduler.cancelAlarmsForTask(context, task.id)
            }
        }
    }
}


enum class FilterType {
    ALL, PENDING, DUE_TODAY, UPCOMING, OVERDUE, COMPLETED
}

data class TaskStats(
    val pendingCount: Int,
    val dueTodayCount: Int,
    val overdueCount: Int,
    val completedCount: Int
)

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Success(val tasks: List<Task>, val stats: TaskStats) : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
}

fun isToday(timeMillis: Long): Boolean {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
           now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

fun isTomorrow(timeMillis: Long): Boolean {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timeMillis }
    if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) {
        return target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) + 1
    } else {
        return target.get(Calendar.YEAR) == now.get(Calendar.YEAR) + 1 &&
               now.get(Calendar.DAY_OF_YEAR) == now.getActualMaximum(Calendar.DAY_OF_YEAR) &&
               target.get(Calendar.DAY_OF_YEAR) == 1
    }
}
