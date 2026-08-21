package com.example.reminderapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.reminderapp.data.RepositoryProvider
import com.example.reminderapp.data.TaskStatus
import java.util.Calendar

class ActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActionReceiver"

        const val ACTION_DONE = "com.example.reminderapp.ACTION_DONE"
        const val ACTION_SNOOZE_1H = "com.example.reminderapp.ACTION_SNOOZE_1H"
        const val ACTION_SNOOZE_TOMORROW = "com.example.reminderapp.ACTION_SNOOZE_TOMORROW"

        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val action = intent.action

        if (taskId == -1L || action == null) {
            Log.e(TAG, "Invalid action intent: taskId=$taskId, action=$action")
            return
        }

        Log.d(TAG, "Received notification action $action for task $taskId")

        val repository = RepositoryProvider.getRepository(context)
        // Retrieve the task synchronously to inspect it
        val dbHelper = com.example.reminderapp.data.TaskDatabaseHelper(context)
        val task = dbHelper.getTask(taskId)

        if (task == null) {
            Log.w(TAG, "Task $taskId not found. Action ignored.")
            NotificationHelper.dismissNotification(context, notificationId)
            return
        }

        when (action) {
            ACTION_DONE -> {
                // 1. Update task to completed
                val completedTask = task.copy(
                    status = TaskStatus.COMPLETED,
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateTask(completedTask)
                
                // 2. Cancel all current and future alarms
                ReminderScheduler.cancelAlarmsForTask(context, taskId)
                
                // 3. Dismiss active notification
                NotificationHelper.dismissNotification(context, notificationId)
                Log.d(TAG, "Task $taskId marked as DONE from notification action.")
            }
            ACTION_SNOOZE_1H -> {
                // 1. Dismiss current notification
                NotificationHelper.dismissNotification(context, notificationId)

                // 2. Schedule snooze alarm for 1 hour from now
                val oneHourMillis = 60 * 60 * 1000L
                val updatedTask = task.copy(
                    snoozeCount = task.snoozeCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateTask(updatedTask)
                ReminderScheduler.scheduleSnoozeAlarm(context, updatedTask, oneHourMillis)
                Log.d(TAG, "Task $taskId snoozed for 1 hour.")
            }
            ACTION_SNOOZE_TOMORROW -> {
                // 1. Dismiss current notification
                NotificationHelper.dismissNotification(context, notificationId)

                // 2. Calculate snooze time until 9:00 AM tomorrow
                val tomorrow9am = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val durationMillis = tomorrow9am - System.currentTimeMillis()
                val updatedTask = task.copy(
                    snoozeCount = task.snoozeCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateTask(updatedTask)
                ReminderScheduler.scheduleSnoozeAlarm(context, updatedTask, durationMillis)
                Log.d(TAG, "Task $taskId snoozed until 9:00 AM tomorrow.")
            }
        }
    }
}
