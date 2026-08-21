# REMINDER_APP

A reliable, production-quality Personal Task Reminder Android mobile application built with Jetpack Compose, Material 3, and SQLite.

## Features
- **Dashboard Screen:** Greeting, productivity summary statistics, urgency-based task grouping, status filter chips, and title/description search.
- **Form Screen:** Add and edit tasks with native date/time pickers, title validation, and warning indicators for past deadlines.
- **Details Screen:** Status and importance badges, precise remaining/overdue countdowns, snooze options, task deletion, and mark-as-done actions.
- **Reliable Notification Scheduling:** Automatically schedules alarms using `AlarmManager` for critical checkpoints (deadline, 1 day before, 3 days before).
- **Adaptive Random Reminders:** Periodically triggers task notifications in morning, afternoon, evening, and night windows based on Low/Medium/High task importance levels.
- **Boot Recovery:** Uses a BroadcastReceiver to restore all pending task alarms automatically on device reboot.
- **Custom Launcher Icon:** Clean square and round circular cropped branding.
