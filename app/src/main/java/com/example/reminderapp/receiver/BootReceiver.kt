package com.example.reminderapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.reminderapp.data.TaskDatabaseHelper
import com.example.reminderapp.data.TaskStatus

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val isBoot = intent.action == Intent.ACTION_BOOT_COMPLETED || 
                     intent.action == "android.intent.action.QUICKBOOT_POWERON"

        if (isBoot) {
            Log.d(TAG, "Device booted. Restoring scheduled reminders...")
            
            val pendingResult = goAsync()
            
            Thread {
                try {
                    val dbHelper = TaskDatabaseHelper(context)
                    // Retrieve pending and overdue tasks
                    val pendingTasks = dbHelper.getAllTasks(TaskStatus.PENDING)
                    val overdueTasks = dbHelper.getAllTasks(TaskStatus.OVERDUE)
                    val allTasks = pendingTasks + overdueTasks
                    
                    Log.d(TAG, "Found ${allTasks.size} tasks to restore alarms for.")
                    
                    for (task in allTasks) {
                        ReminderScheduler.scheduleAlarmsForTask(context, task)
                    }
                    Log.d(TAG, "Finished rescheduling reminders on boot.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule reminders on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        }
    }
}
