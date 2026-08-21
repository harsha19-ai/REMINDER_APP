package com.example.reminderapp.ui.main

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.reminderapp.AddTask
import com.example.reminderapp.TaskDetails
import com.example.reminderapp.data.RepositoryProvider
import com.example.reminderapp.data.Task
import com.example.reminderapp.data.TaskStatus
import com.example.reminderapp.data.ImportanceLevel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(RepositoryProvider.getRepository(context.applicationContext))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Notification permission launcher
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
        }
    )

    // Exact Alarm permission check
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var hasExactAlarmPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        )
    }

    // Refresh permissions on resume
    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasExactAlarmPermission = alarmManager.canScheduleExactAlarms()
        }
        onDispose {}
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onItemClick(AddTask) },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task") },
                text = { Text("Add Task") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Greeting, Date, Stats
            DashboardHeader()

            // Permission Warning Banners
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionBanner(
                    text = "🔔 Click here to allow notifications to receive your task reminders.",
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            } else if (!hasExactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionBanner(
                    text = "⏰ Click here to enable exact alarms in system settings for on-time reminders.",
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            when (val currentState = state) {
                MainScreenUiState.Loading -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is MainScreenUiState.Error -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error loading data: ${currentState.throwable.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is MainScreenUiState.Success -> {
                    DashboardContent(
                        tasks = currentState.tasks,
                        stats = currentState.stats,
                        searchQuery = viewModel.searchQuery.collectAsState().value,
                        onSearchQueryChange = { viewModel.searchQuery.value = it },
                        selectedFilter = viewModel.selectedFilter.collectAsState().value,
                        onFilterSelect = { viewModel.selectedFilter.value = it },
                        onDoneClick = { task -> viewModel.markAsDone(context, task) },
                        onTaskClick = { task -> onItemClick(TaskDetails(task.id)) },
                        onAddTaskClick = { onItemClick(AddTask) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardHeader() {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour in 5..11 -> "Good Morning 👋"
        hour in 12..16 -> "Good Afternoon 👋"
        hour in 17..20 -> "Good Evening 👋"
        else -> "Good Night 🌙"
    }

    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()) }
    val currentDateStr = dateFormatter.format(calendar.time)

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = currentDateStr,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun PermissionBanner(text: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun DashboardContent(
    tasks: List<Task>,
    stats: TaskStats,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: FilterType,
    onFilterSelect: (FilterType) -> Unit,
    onDoneClick: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onAddTaskClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Statistics Cards Row
        StatsRow(stats)

        // Search Text Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search tasks...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Filter Chips Row
        FilterRow(selectedFilter, onFilterSelect)

        // Task List with Urgency Grouping
        if (tasks.isEmpty()) {
            EmptyState(onAddTaskClick)
        } else {
            TaskList(
                tasks = tasks,
                selectedFilter = selectedFilter,
                onTaskClick = onTaskClick,
                onDoneClick = onDoneClick
            )
        }
    }
}

@Composable
fun StatsRow(stats: TaskStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(title = "Pending", count = stats.pendingCount, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f))
        StatCard(title = "Due Today", count = stats.dueTodayCount, containerColor = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f))
        StatCard(title = "Overdue", count = stats.overdueCount, containerColor = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.weight(1f))
        StatCard(title = "Completed", count = stats.completedCount, containerColor = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StatCard(
    title: String,
    count: Int,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FilterRow(
    selectedFilter: FilterType,
    onFilterSelect: (FilterType) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(FilterType.values()) { filter ->
            val label = when (filter) {
                FilterType.ALL -> "All"
                FilterType.PENDING -> "Pending"
                FilterType.DUE_TODAY -> "Due Today"
                FilterType.UPCOMING -> "Upcoming"
                FilterType.OVERDUE -> "Overdue"
                FilterType.COMPLETED -> "Completed"
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelect(filter) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun TaskList(
    tasks: List<Task>,
    selectedFilter: FilterType,
    onTaskClick: (Task) -> Unit,
    onDoneClick: (Task) -> Unit
) {
    // If filtering by Completed, or search query is present, do not do heavy grouping, just show plain chronological list
    if (selectedFilter == FilterType.COMPLETED) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tasks) { task ->
                TaskCard(task = task, onCardClick = onTaskClick, onDoneClick = onDoneClick)
            }
        }
        return
    }

    val now = System.currentTimeMillis()

    // Partition tasks
    val overdue = tasks.filter { it.status == TaskStatus.OVERDUE || (it.status == TaskStatus.PENDING && it.deadlineDateTime <= now) }
    val dueToday = tasks.filter { it.status == TaskStatus.PENDING && isToday(it.deadlineDateTime) && it.deadlineDateTime > now }
    val dueTomorrow = tasks.filter { it.status == TaskStatus.PENDING && isTomorrow(it.deadlineDateTime) }
    val upcoming = tasks.filter { it.status == TaskStatus.PENDING && !isToday(it.deadlineDateTime) && !isTomorrow(it.deadlineDateTime) && it.deadlineDateTime > now }
    val completed = tasks.filter { it.status == TaskStatus.COMPLETED }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (overdue.isNotEmpty()) {
            item { SectionHeader("🔴 Overdue") }
            items(overdue) { task ->
                TaskCard(task = task, onCardClick = onTaskClick, onDoneClick = onDoneClick)
            }
        }
        if (dueToday.isNotEmpty()) {
            item { SectionHeader("🧡 Due Today") }
            items(dueToday) { task ->
                TaskCard(task = task, onCardClick = onTaskClick, onDoneClick = onDoneClick)
            }
        }
        if (dueTomorrow.isNotEmpty()) {
            item { SectionHeader("💛 Due Tomorrow") }
            items(dueTomorrow) { task ->
                TaskCard(task = task, onCardClick = onTaskClick, onDoneClick = onDoneClick)
            }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("💙 Upcoming") }
            items(upcoming) { task ->
                TaskCard(task = task, onCardClick = onTaskClick, onDoneClick = onDoneClick)
            }
        }
        if (completed.isNotEmpty() && selectedFilter == FilterType.ALL) {
            item { SectionHeader("✓ Completed") }
            items(completed) { task ->
                TaskCard(task = task, onCardClick = onTaskClick, onDoneClick = onDoneClick)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(
    task: Task,
    onCardClick: (Task) -> Unit,
    onDoneClick: (Task) -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    val isOverdue = task.status == TaskStatus.OVERDUE || (task.status == TaskStatus.PENDING && task.deadlineDateTime <= System.currentTimeMillis())

    val outlineColor = when {
        task.status == TaskStatus.COMPLETED -> MaterialTheme.colorScheme.outlineVariant
        isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    OutlinedCard(
        onClick = { onCardClick(task) },
        colors = CardDefaults.cardColors(
            containerColor = if (task.status == TaskStatus.COMPLETED) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(outlineColor)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                // Title with Importance Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (task.status == TaskStatus.COMPLETED) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (task.status != TaskStatus.COMPLETED) {
                        val importanceColor = when (task.importance) {
                            ImportanceLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ImportanceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            ImportanceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        }
                        val importanceTextColor = when (task.importance) {
                            ImportanceLevel.LOW -> MaterialTheme.colorScheme.onSecondaryContainer
                            ImportanceLevel.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
                            ImportanceLevel.HIGH -> MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = importanceColor),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = task.importance.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = importanceTextColor
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))

                // Time/Status Line
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.status == TaskStatus.COMPLETED) {
                        Text(
                            text = "✓ Completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isOverdue) {
                        Text(
                            text = "Was due: ${dateFormatter.format(task.deadlineDateTime)} at ${timeFormatter.format(task.deadlineDateTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isToday(task.deadlineDateTime)) {
                        Text(
                            text = "Due Today at ${timeFormatter.format(task.deadlineDateTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "Due: ${dateFormatter.format(task.deadlineDateTime)} at ${timeFormatter.format(task.deadlineDateTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action Button
            if (task.status != TaskStatus.COMPLETED) {
                IconButton(
                    onClick = { onDoneClick(task) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Mark as Done",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(onAddTaskClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✨ You're all caught up!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No pending tasks. Enjoy your free time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddTaskClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Task")
        }
    }
}
