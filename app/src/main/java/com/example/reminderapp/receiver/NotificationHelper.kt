package com.example.reminderapp.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.reminderapp.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "task_reminders_channel"
    private const val CHANNEL_NAME = "Task Reminders"
    private const val CHANNEL_DESC = "Notifications for task deadlines and reminders"

    fun showNotification(context: Context, taskId: Long, type: Int, title: String, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action Intents
        val doneIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_DONE
            putExtra(ActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100000,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze1hIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_SNOOZE_1H
            putExtra(ActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze1hPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 200000,
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeTomorrowIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_SNOOZE_TOMORROW
            putExtra(ActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozeTomorrowPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 300000,
            snoozeTomorrowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Content Intent (Open App & go to task details)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NAV_TASK_ID", taskId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 400000,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Customize text based on type
        val contentText = when (type) {
            ReminderScheduler.TYPE_DEADLINE -> "This task is due now!"
            ReminderScheduler.TYPE_1_DAY_BEFORE -> "Your task is due tomorrow."
            ReminderScheduler.TYPE_3_DAYS_BEFORE -> "Your task is due in 3 days."
            ReminderScheduler.TYPE_SNOOZE -> "Snoozed reminder for this task."
            ReminderScheduler.TYPE_OVERDUE -> "This task is overdue! Complete it now."
            ReminderScheduler.TYPE_RANDOM_REMINDER -> "Friendly reminder to complete your task."
            else -> "You have a task reminder."
        }

        val notificationTitle = when (type) {
            ReminderScheduler.TYPE_OVERDUE -> "🚨 Overdue Task"
            ReminderScheduler.TYPE_DEADLINE -> "🔔 Task Deadline"
            ReminderScheduler.TYPE_RANDOM_REMINDER -> "💡 Task Reminder"
            else -> "🔔 Task Reminder"
        }

        // Build notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Using native system alarm icon
            .setContentTitle("$notificationTitle: $title")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Done", donePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 1h", snooze1hPendingIntent)
            .addAction(android.R.drawable.ic_menu_today, "Snooze Tomorrow", snoozeTomorrowPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    fun dismissNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
