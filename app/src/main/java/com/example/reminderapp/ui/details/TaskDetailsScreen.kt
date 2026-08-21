package com.example.reminderapp.ui.details

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reminderapp.data.DataRepository
import com.example.reminderapp.data.RepositoryProvider
import com.example.reminderapp.data.Task
import com.example.reminderapp.data.TaskStatus
import com.example.reminderapp.data.ImportanceLevel
import com.example.reminderapp.receiver.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TaskDetailsViewModel(
    private val repository: DataRepository,
    private val taskId: Long
) : ViewModel() {
    private val _uiState = MutableStateFlow<TaskDetailsUiState>(TaskDetailsUiState.Loading)
    val uiState: StateFlow<TaskDetailsUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.getTask(taskId).collect { task ->
                if (task != null) {
                    _uiState.value = TaskDetailsUiState.Success(task)
                } else {
                    _uiState.value = TaskDetailsUiState.Error("Task not found")
                }
            }
        }
    }

    fun markAsDone(context: Context) {
        val currentState = _uiState.value
        if (currentState is TaskDetailsUiState.Success) {
            val task = currentState.task
            val updatedTask = task.copy(
                status = TaskStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            viewModelScope.launch {
                val success = repository.updateTask(updatedTask)
                if (success) {
                    ReminderScheduler.cancelAlarmsForTask(context, task.id)
                    Toast.makeText(context, "Task completed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun snooze(context: Context, durationMillis: Long) {
        val currentState = _uiState.value
        if (currentState is TaskDetailsUiState.Success) {
            val task = currentState.task
            val updatedTask = task.copy(
                snoozeCount = task.snoozeCount + 1,
                updatedAt = System.currentTimeMillis()
            )
            viewModelScope.launch {
                val success = repository.updateTask(updatedTask)
                if (success) {
                    ReminderScheduler.scheduleSnoozeAlarm(context, updatedTask, durationMillis)
                    val minutes = durationMillis / 60000
                    val snoozeText = if (minutes >= 60) "${minutes / 60} hour(s)" else "$minutes minutes"
                    Toast.makeText(context, "Snoozed for $snoozeText", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteTask(context: Context, onDeleteConfirmed: () -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteTask(taskId)
            if (success) {
                ReminderScheduler.cancelAlarmsForTask(context, taskId)
                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                onDeleteConfirmed()
            }
        }
    }
}

sealed interface TaskDetailsUiState {
    object Loading : TaskDetailsUiState
    data class Success(val task: Task) : TaskDetailsUiState
    data class Error(val message: String) : TaskDetailsUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsScreen(
    taskId: Long,
    onBack: () -> Unit,
    onEditClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { RepositoryProvider.getRepository(context) }
    val viewModel: TaskDetailsViewModel = viewModel(
        key = "task_details_vm_$taskId",
        initializer = { TaskDetailsViewModel(repository, taskId) }
    )

    val uiState by viewModel.uiState.collectAsState()

    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSnoozeMenu by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to permanently delete this task? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTask(context, onDeleteConfirmed = onBack)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is TaskDetailsUiState.Success) {
                        val task = (uiState as TaskDetailsUiState.Success).task
                        if (task.status != TaskStatus.COMPLETED) {
                            IconButton(onClick = { onEditClick(taskId) }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        when (val state = uiState) {
            TaskDetailsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is TaskDetailsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is TaskDetailsUiState.Success -> {
                val task = state.task
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Task Title
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Status, Importance & Relative Time Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status Badge
                        val statusColor = when (task.status) {
                            TaskStatus.PENDING -> MaterialTheme.colorScheme.primaryContainer
                            TaskStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
                            TaskStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
                        }
                        val statusTextColor = when (task.status) {
                            TaskStatus.PENDING -> MaterialTheme.colorScheme.onPrimaryContainer
                            TaskStatus.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
                            TaskStatus.COMPLETED -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = statusColor)
                        ) {
                            Text(
                                text = task.status.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusTextColor
                            )
                        }

                        // Importance Badge
                        val importanceColor = when (task.importance) {
                            ImportanceLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ImportanceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            ImportanceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        }
                        val importanceTextColor = when (task.importance) {
                            ImportanceLevel.LOW -> MaterialTheme.colorScheme.onSecondaryContainer
                            ImportanceLevel.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
                            ImportanceLevel.HIGH -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = importanceColor)
                        ) {
                            Text(
                                text = "${task.importance.name} IMPORTANCE",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = importanceTextColor
                            )
                        }
                    }

                    // Relative Time text
                    Text(
                        text = getRemainingTimeText(task.deadlineDateTime, task.completedAt, task.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.status == TaskStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )

                    HorizontalDivider()

                    // Deadline Info
                    Column {
                        Text(
                            text = "DEADLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${dateFormatter.format(task.deadlineDateTime)} at ${timeFormatter.format(task.deadlineDateTime)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Description Info
                    if (task.description.isNotBlank()) {
                        Column {
                            Text(
                                text = "NOTES",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Metadata
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Created: ${dateFormatter.format(task.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (task.snoozeCount > 0) {
                            Text(
                                text = "Snoozed count: ${task.snoozeCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Buttons
                    if (task.status != TaskStatus.COMPLETED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Snooze Button
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showSnoozeMenu = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    Text("Snooze", fontWeight = FontWeight.Bold)
                                }

                                DropdownMenu(
                                    expanded = showSnoozeMenu,
                                    onDismissRequest = { showSnoozeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Snooze for 1 hour") },
                                        onClick = {
                                            showSnoozeMenu = false
                                            viewModel.snooze(context, 60 * 60 * 1000L)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Snooze until tomorrow") },
                                        onClick = {
                                            showSnoozeMenu = false
                                            val tomorrow9am = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_YEAR, 1)
                                                set(Calendar.HOUR_OF_DAY, 9)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            val duration = tomorrow9am - System.currentTimeMillis()
                                            viewModel.snooze(context, duration)
                                        }
                                    )
                                }
                            }

                            // Mark as Done Button
                            Button(
                                onClick = { viewModel.markAsDone(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Text("✓ Done", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getRemainingTimeText(deadline: Long, completedAt: Long?, status: TaskStatus): String {
    if (status == TaskStatus.COMPLETED) {
        if (completedAt == null) return "Completed"
        val df = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        return "Completed on ${df.format(Date(completedAt))}"
    }

    val now = System.currentTimeMillis()
    val diff = deadline - now

    if (diff < 0) {
        val absDiff = Math.abs(diff)
        val minutes = absDiff / (60 * 1000)
        val hours = absDiff / (60 * 60 * 1000)
        val days = absDiff / (24 * 60 * 60 * 1000)

        return when {
            days > 0 -> "Overdue by $days ${if (days == 1L) "day" else "days"}"
            hours > 0 -> "Overdue by $hours ${if (hours == 1L) "hour" else "hours"}"
            else -> "Overdue by $minutes ${if (minutes == 1L) "minute" else "minutes"}"
        }
    } else {
        val minutes = diff / (60 * 1000)
        val hours = diff / (60 * 60 * 1000)
        val days = diff / (24 * 60 * 60 * 1000)

        return when {
            days > 0 -> "Due in $days ${if (days == 1L) "day" else "days"}"
            hours > 0 -> "Due in $hours ${if (hours == 1L) "hour" else "hours"}"
            else -> "Due in $minutes ${if (minutes == 1L) "minute" else "minutes"}"
        }
    }
}
