package com.example.reminderapp.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface DataRepository {
    fun getTasks(statusFilter: TaskStatus? = null, searchQuery: String? = null): Flow<List<Task>>
    fun getTask(id: Long): Flow<Task?>
    fun addTask(task: Task): Long
    fun updateTask(task: Task): Boolean
    fun deleteTask(id: Long): Boolean
}

class DefaultDataRepository(context: Context) : DataRepository {
    private val dbHelper = TaskDatabaseHelper(context.applicationContext)
    
    // Simple refresh mechanism to trigger flows to re-emit when database changes
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun triggerRefresh() {
        refreshSignal.tryEmit(Unit)
    }

    override fun getTasks(statusFilter: TaskStatus?, searchQuery: String?): Flow<List<Task>> {
        return flow {
            emit(Unit)
            refreshSignal.collect { emit(Unit) }
        }.map { 
            dbHelper.getAllTasks(statusFilter, searchQuery) 
        }
    }

    override fun getTask(id: Long): Flow<Task?> {
        return flow {
            emit(Unit)
            refreshSignal.collect { emit(Unit) }
        }.map { 
            dbHelper.getTask(id) 
        }
    }

    override fun addTask(task: Task): Long {
        val id = dbHelper.addTask(task)
        if (id > 0) {
            triggerRefresh()
        }
        return id
    }

    override fun updateTask(task: Task): Boolean {
        val success = dbHelper.updateTask(task)
        if (success) {
            triggerRefresh()
        }
        return success
    }

    override fun deleteTask(id: Long): Boolean {
        val success = dbHelper.deleteTask(id)
        if (success) {
            triggerRefresh()
        }
        return success
    }
}
