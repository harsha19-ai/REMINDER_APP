package com.example.reminderapp.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.reminderapp.data.Task
import com.example.reminderapp.data.TaskStatus
import com.example.reminderapp.data.ImportanceLevel
import java.util.Calendar
import java.util.Random

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    // Reminder Types mapped to the last digit of notificationId
    const val TYPE_DEADLINE = 0
    const val TYPE_1_DAY_BEFORE = 1
    const val TYPE_3_DAYS_BEFORE = 2
    const val TYPE_SNOOZE = 3
    const val TYPE_OVERDUE = 4
    const val TYPE_RANDOM_REMINDER = 5 // New type for random notifications

    private class RandomPeriod(val startHour: Int, val startMinute: Int, val durationMinutes: Int)

    fun getNotificationId(taskId: Long, type: Int): Int {
        return (taskId * 10 + type).toInt()
    }

    fun scheduleAlarmsForTask(context: Context, task: Task) {
        // 1. First, cancel any existing alarms (both standard and random) for this task
        cancelAlarmsForTask(context, task.id)

        // If task is already completed, do not schedule any alarms
        if (task.status == TaskStatus.COMPLETED) {
            return
        }

        val now = System.currentTimeMillis()
        val deadline = task.deadlineDateTime

        // If the task is overdue, only schedule the overdue repeating alarm and random alarms
        if (task.status == TaskStatus.OVERDUE || deadline <= now) {
            scheduleOverdueAlarm(context, task, now)
            scheduleRandomAlarmsForTask(context, task)
            return
        }

        val alarmTimes = mutableListOf<Pair<Int, Long>>()

        // Calculate differences
        val diffMillis = deadline - now
        val diffDays = diffMillis.toDouble() / (24 * 60 * 60 * 1000)

        // Calendar-based check for today vs tomorrow vs upcoming
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val deadlineCal = Calendar.getInstance().apply { timeInMillis = deadline }

        val nowYear = nowCal.get(Calendar.YEAR)
        val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)
        val deadlineYear = deadlineCal.get(Calendar.YEAR)
        val deadlineDay = deadlineCal.get(Calendar.DAY_OF_YEAR)

        val isSameYear = nowYear == deadlineYear
        val isToday = isSameYear && nowDay == deadlineDay
        val isTomorrow = (isSameYear && deadlineDay == nowDay + 1) ||
                (deadlineYear == nowYear + 1 && nowCal.getActualMaximum(Calendar.DAY_OF_YEAR) == nowDay && deadlineDay == 1)

        if (isToday) {
            if (diffMillis > 60 * 60 * 1000L) {
                val halfwayTime = now + (diffMillis / 2)
                alarmTimes.add(Pair(TYPE_1_DAY_BEFORE, halfwayTime))
            }
            alarmTimes.add(Pair(TYPE_DEADLINE, deadline))
        } else if (isTomorrow) {
            val oneDayBefore = deadline - 24 * 60 * 60 * 1000L
            val reminderTime = if (oneDayBefore > now) oneDayBefore else (now + 5000L)
            alarmTimes.add(Pair(TYPE_1_DAY_BEFORE, reminderTime))
            alarmTimes.add(Pair(TYPE_DEADLINE, deadline))
        } else {
            if (diffDays > 3.0) {
                alarmTimes.add(Pair(TYPE_3_DAYS_BEFORE, deadline - 3 * 24 * 60 * 60 * 1000L))
                alarmTimes.add(Pair(TYPE_1_DAY_BEFORE, deadline - 24 * 60 * 60 * 1000L))
                alarmTimes.add(Pair(TYPE_DEADLINE, deadline))
            } else {
                alarmTimes.add(Pair(TYPE_1_DAY_BEFORE, deadline - 24 * 60 * 60 * 1000L))
                alarmTimes.add(Pair(TYPE_DEADLINE, deadline))
            }
        }

        // Schedule all calculated standard alarms that are in the future
        for ((type, time) in alarmTimes) {
            if (time > now) {
                scheduleAlarm(context, task.id, type, task.title, time, getNotificationId(task.id, type))
            }
        }

        // 2. Schedule adaptive random reminders
        scheduleRandomAlarmsForTask(context, task)
    }

    fun scheduleSnoozeAlarm(context: Context, task: Task, durationMillis: Long) {
        val now = System.currentTimeMillis()
        val triggerTime = now + durationMillis
        
        cancelAlarm(context, task.id, TYPE_SNOOZE)
        scheduleAlarm(context, task.id, TYPE_SNOOZE, task.title, triggerTime, getNotificationId(task.id, TYPE_SNOOZE))
        Log.d(TAG, "Scheduled snooze alarm for task ${task.id} in ${durationMillis / 60000} minutes")
    }

    private fun scheduleOverdueAlarm(context: Context, task: Task, now: Long) {
        val triggerTime = now + 12 * 60 * 60 * 1000L
        scheduleAlarm(context, task.id, TYPE_OVERDUE, task.title, triggerTime, getNotificationId(task.id, TYPE_OVERDUE))
    }

    fun scheduleRandomAlarmsForTask(context: Context, task: Task) {
        cancelRandomAlarmsForTask(context, task.id)

        if (task.status == TaskStatus.COMPLETED) {
            return
        }

        val now = System.currentTimeMillis()
        val deadline = task.deadlineDateTime
        val isOverdue = task.status == TaskStatus.OVERDUE || deadline <= now

        val random = Random()
        var alarmIndex = 0

        // Define our 4 periods: Morning, Afternoon, Evening, Night
        val periods = listOf(
            RandomPeriod(8, 0, 120),    // Morning: 8:00 AM - 10:00 AM (120 mins)
            RandomPeriod(13, 30, 120),  // Afternoon: 1:30 PM - 3:30 PM (120 mins)
            RandomPeriod(18, 30, 120),  // Evening: 6:30 PM - 8:30 PM (120 mins)
            RandomPeriod(21, 30, 90)    // Night: 9:30 PM - 11:00 PM (90 mins)
        )

        // Schedule random reminders for the next 7 days
        for (dayOffset in 0..6) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }

            val selectedPeriods = when (task.importance) {
                ImportanceLevel.HIGH -> periods // All 4 periods
                ImportanceLevel.MEDIUM -> {
                    // Randomly select 2 of the 4 periods
                    periods.shuffled(random).take(2).sortedWith(compareBy({ it.startHour }, { it.startMinute }))
                }
                ImportanceLevel.LOW -> {
                    // Randomly select 1 of the 4 periods
                    periods.shuffled(random).take(1)
                }
            }

            for (period in selectedPeriods) {
                val alarmCal = Calendar.getInstance().apply {
                    timeInMillis = dayCal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, period.startHour)
                    set(Calendar.MINUTE, period.startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Choose a random minute offset within the period
                val offset = random.nextInt(period.durationMinutes)
                alarmCal.add(Calendar.MINUTE, offset)

                val triggerTime = alarmCal.timeInMillis

                // Check constraints: must be in the future, and if not overdue, must be before task deadline
                if (triggerTime > now && (isOverdue || triggerTime < deadline)) {
                    val notificationId = (task.id * 100 + 10 + alarmIndex).toInt()
                    scheduleAlarm(context, task.id, TYPE_RANDOM_REMINDER, task.title, triggerTime, notificationId)
                    alarmIndex++
                }
            }
        }
        Log.d(TAG, "Scheduled $alarmIndex random reminders for task ${task.id} (${task.importance.name} importance)")
    }

    private fun scheduleAlarm(context: Context, taskId: Long, type: Int, taskTitle: String, triggerTime: Long, notificationId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.reminderapp.ACTION_REMINDER"
            putExtra("EXTRA_TASK_ID", taskId)
            putExtra("EXTRA_REMINDER_TYPE", type)
            putExtra("EXTRA_TASK_TITLE", taskTitle)
            putExtra("EXTRA_NOTIFICATION_ID", notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelAlarmsForTask(context: Context, taskId: Long) {
        val types = arrayOf(TYPE_DEADLINE, TYPE_1_DAY_BEFORE, TYPE_3_DAYS_BEFORE, TYPE_SNOOZE, TYPE_OVERDUE)
        for (type in types) {
            cancelAlarm(context, taskId, type)
        }
        cancelRandomAlarmsForTask(context, taskId)
    }

    private fun cancelAlarm(context: Context, taskId: Long, type: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.reminderapp.ACTION_REMINDER"
        }
        val notificationId = getNotificationId(taskId, type)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for task $taskId of type $type")
        }
    }

    fun cancelRandomAlarmsForTask(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.reminderapp.ACTION_REMINDER"
        }
        // Sweep all possible slots (0 to 27)
        for (idx in 0..27) {
            val notificationId = (taskId * 100 + 10 + idx).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        Log.d(TAG, "Cancelled all random alarms for task $taskId")
    }
}
