package com.example.reminderapp.ui.main

import com.example.reminderapp.data.DataRepository
import com.example.reminderapp.data.Task
import com.example.reminderapp.data.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun uiState_initiallyLoadingOrSuccess() = runTest {
        val viewModel = MainScreenViewModel(FakeMyModelRepository())
        val firstState = viewModel.uiState.first()
        assert(firstState is MainScreenUiState.Success || firstState is MainScreenUiState.Loading)
    }
}

private class FakeMyModelRepository : DataRepository {
    private val tasks = mutableListOf<Task>()
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun getTasks(statusFilter: TaskStatus?, searchQuery: String?): Flow<List<Task>> {
        return flow {
            emit(Unit)
            refreshSignal.collect { emit(Unit) }
        }.map {
            tasks.filter { task ->
                (statusFilter == null || task.status == statusFilter) &&
                (searchQuery.isNullOrBlank() || task.title.contains(searchQuery, ignoreCase = true))
            }
        }
    }

    override fun getTask(id: Long): Flow<Task?> {
        return flow {
            emit(Unit)
            refreshSignal.collect { emit(Unit) }
        }.map {
            tasks.find { it.id == id }
        }
    }

    override fun addTask(task: Task): Long {
        val id = (tasks.size + 1).toLong()
        tasks.add(task.copy(id = id))
        refreshSignal.tryEmit(Unit)
        return id
    }

    override fun updateTask(task: Task): Boolean {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            refreshSignal.tryEmit(Unit)
            return true
        }
        return false
    }

    override fun deleteTask(id: Long): Boolean {
        val removed = tasks.removeIf { it.id == id }
        if (removed) {
            refreshSignal.tryEmit(Unit)
            return true
        }
        return false
    }
}
