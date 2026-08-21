package com.example.reminderapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.reminderapp.data.TaskDatabaseHelper
import com.example.reminderapp.data.TaskStatus

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val taskId = intent.getLongExtra("EXTRA_TASK_ID", -1L)
        val type = intent.getIntExtra("EXTRA_REMINDER_TYPE", -1)
        val title = intent.getStringExtra("EXTRA_TASK_TITLE") ?: ""

        if (taskId == -1L || type == -1) {
            Log.e(TAG, "Invalid alarm intent extras: taskId=$taskId, type=$type")
            return
        }

        Log.d(TAG, "Alarm received for task $taskId, type $type, title: $title")

        val dbHelper = TaskDatabaseHelper(context)
        val task = dbHelper.getTask(taskId)

        if (task == null) {
            Log.w(TAG, "Task $taskId not found in database. Ignoring alarm.")
            return
        }

        // If the task is already completed, ignore the alarm and make sure notifications are cancelled
        if (task.status == TaskStatus.COMPLETED) {
            Log.d(TAG, "Task $taskId is already completed. Skipping notification.")
            ReminderScheduler.cancelAlarmsForTask(context, taskId)
            return
        }

        // Determine if we need to update status to OVERDUE
        var updatedTask = task
        if (type == ReminderScheduler.TYPE_DEADLINE) {
            updatedTask = task.copy(status = TaskStatus.OVERDUE, updatedAt = System.currentTimeMillis())
            dbHelper.updateTask(updatedTask)
            Log.d(TAG, "Deadline reached. Updated task $taskId to OVERDUE.")
        }

        // Get unique notification ID, fallback to type-based calculations
        val notificationId = intent.getIntExtra("EXTRA_NOTIFICATION_ID", -1).let {
            if (it == -1) ReminderScheduler.getNotificationId(taskId, type) else it
        }

        // Show the notification
        NotificationHelper.showNotification(context, taskId, type, updatedTask.title, notificationId)

        // If it's a deadline or overdue alarm, schedule the next overdue check (since it remains overdue)
        if (type == ReminderScheduler.TYPE_DEADLINE || type == ReminderScheduler.TYPE_OVERDUE) {
            ReminderScheduler.scheduleAlarmsForTask(context, updatedTask)
        }
    }
}
