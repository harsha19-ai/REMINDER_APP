package com.example.reminderapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class TaskDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "TaskDatabaseHelper"
        private const val DATABASE_NAME = "tasks.db"
        private const val DATABASE_VERSION = 2 // Bumped from 1 to 2
        private const val TABLE_TASKS = "tasks"

        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_DESCRIPTION = "description"
        private const val COLUMN_DEADLINE = "deadline_date_time"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_UPDATED_AT = "updated_at"
        private const val COLUMN_COMPLETED_AT = "completed_at"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_SNOOZE_COUNT = "snooze_count"
        private const val COLUMN_IMPORTANCE = "importance" // New column
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_TASKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_DEADLINE INTEGER NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_COMPLETED_AT INTEGER,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_SNOOZE_COUNT INTEGER DEFAULT 0,
                $COLUMN_IMPORTANCE TEXT NOT NULL DEFAULT 'MEDIUM'
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "Upgrading database from $oldVersion to $newVersion")
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COLUMN_IMPORTANCE TEXT NOT NULL DEFAULT 'MEDIUM'")
                Log.d(TAG, "Successfully added importance column to tasks table.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add importance column, it may already exist.", e)
            }
        }
    }

    fun addTask(task: Task): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, task.title)
            put(COLUMN_DESCRIPTION, task.description)
            put(COLUMN_DEADLINE, task.deadlineDateTime)
            put(COLUMN_CREATED_AT, task.createdAt)
            put(COLUMN_UPDATED_AT, task.updatedAt)
            if (task.completedAt != null) {
                put(COLUMN_COMPLETED_AT, task.completedAt)
            } else {
                putNull(COLUMN_COMPLETED_AT)
            }
            put(COLUMN_STATUS, task.status.name)
            put(COLUMN_SNOOZE_COUNT, task.snoozeCount)
            put(COLUMN_IMPORTANCE, task.importance.name)
        }
        return db.insert(TABLE_TASKS, null, values)
    }

    fun updateTask(task: Task): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, task.title)
            put(COLUMN_DESCRIPTION, task.description)
            put(COLUMN_DEADLINE, task.deadlineDateTime)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
            if (task.completedAt != null) {
                put(COLUMN_COMPLETED_AT, task.completedAt)
            } else {
                putNull(COLUMN_COMPLETED_AT)
            }
            put(COLUMN_STATUS, task.status.name)
            put(COLUMN_SNOOZE_COUNT, task.snoozeCount)
            put(COLUMN_IMPORTANCE, task.importance.name)
        }
        val affected = db.update(TABLE_TASKS, values, "$COLUMN_ID = ?", arrayOf(task.id.toString()))
        return affected > 0
    }

    fun deleteTask(id: Long): Boolean {
        val db = writableDatabase
        val affected = db.delete(TABLE_TASKS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        return affected > 0
    }

    fun getTask(id: Long): Task? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS, null, "$COLUMN_ID = ?", arrayOf(id.toString()),
            null, null, null
        )
        var task: Task? = null
        if (cursor.moveToFirst()) {
            task = parseTask(cursor)
        }
        cursor.close()

        // Auto-update to OVERDUE if pending and deadline passed
        if (task != null && task.status == TaskStatus.PENDING && task.deadlineDateTime <= System.currentTimeMillis()) {
            val updatedTask = task.copy(status = TaskStatus.OVERDUE, updatedAt = System.currentTimeMillis())
            updateTask(updatedTask)
            return updatedTask
        }
        return task
    }

    fun getAllTasks(statusFilter: TaskStatus? = null, searchQuery: String? = null): List<Task> {
        val db = writableDatabase
        val selectionClauses = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (statusFilter != null) {
            selectionClauses.add("$COLUMN_STATUS = ?")
            selectionArgs.add(statusFilter.name)
        }

        if (!searchQuery.isNullOrBlank()) {
            selectionClauses.add("($COLUMN_TITLE LIKE ? OR $COLUMN_DESCRIPTION LIKE ?)")
            val queryParam = "%$searchQuery%"
            selectionArgs.add(queryParam)
            selectionArgs.add(queryParam)
        }

        val selection = if (selectionClauses.isEmpty()) null else selectionClauses.joinToString(" AND ")
        val args = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

        val cursor = db.query(
            TABLE_TASKS, null, selection, args,
            null, null, "$COLUMN_DEADLINE ASC"
        )

        val tasks = mutableListOf<Task>()
        val now = System.currentTimeMillis()
        val tasksToUpdate = mutableListOf<Task>()

        while (cursor.moveToNext()) {
            var task = parseTask(cursor)
            // Auto check overdue status
            if (task.status == TaskStatus.PENDING && task.deadlineDateTime <= now) {
                task = task.copy(status = TaskStatus.OVERDUE, updatedAt = now)
                tasksToUpdate.add(task)
            }
            tasks.add(task)
        }
        cursor.close()

        // Batch update overdue tasks
        if (tasksToUpdate.isNotEmpty()) {
            db.beginTransaction()
            try {
                for (t in tasksToUpdate) {
                    val values = ContentValues().apply {
                        put(COLUMN_STATUS, TaskStatus.OVERDUE.name)
                        put(COLUMN_UPDATED_AT, now)
                    }
                    db.update(TABLE_TASKS, values, "$COLUMN_ID = ?", arrayOf(t.id.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        return tasks
    }

    private fun parseTask(cursor: android.database.Cursor): Task {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE))
        val description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION))
        val deadline = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DEADLINE))
        val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
        val updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))
        val completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_AT))) null 
                            else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_AT))
        val statusStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS))
        val status = try { TaskStatus.valueOf(statusStr) } catch(e: Exception) { TaskStatus.PENDING }
        val snoozeCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SNOOZE_COUNT))
        
        // Defensive read of importance column
        val importanceIndex = cursor.getColumnIndex(COLUMN_IMPORTANCE)
        val importanceStr = if (importanceIndex != -1) cursor.getString(importanceIndex) else "MEDIUM"
        val importance = try { ImportanceLevel.valueOf(importanceStr) } catch(e: Exception) { ImportanceLevel.MEDIUM }

        return Task(id, title, description, deadline, createdAt, updatedAt, completedAt, status, snoozeCount, importance)
    }
}
