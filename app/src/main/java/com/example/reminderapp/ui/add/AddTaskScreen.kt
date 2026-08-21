package com.example.reminderapp.ui.add

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
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

class AddTaskViewModel(private val repository: DataRepository, private val taskId: Long?) : ViewModel() {
    private val _uiState = MutableStateFlow<AddTaskUiState>(AddTaskUiState.Idle)
    val uiState: StateFlow<AddTaskUiState> = _uiState

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var importance by mutableStateOf(ImportanceLevel.MEDIUM)
    var deadlineCalendar by mutableStateOf(Calendar.getInstance().apply {
        // Default to tomorrow at 9:00 AM
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    })

    init {
        if (taskId != null && taskId > 0) {
            _uiState.value = AddTaskUiState.Loading
            viewModelScope.launch {
                repository.getTask(taskId).collect { task ->
                    if (task != null) {
                        title = task.title
                        description = task.description
                        importance = task.importance
                        deadlineCalendar = Calendar.getInstance().apply {
                            timeInMillis = task.deadlineDateTime
                        }
                        _uiState.value = AddTaskUiState.Loaded(task)
                    } else {
                        _uiState.value = AddTaskUiState.Error("Task not found")
                    }
                }
            }
        }
    }

    fun saveTask(context: Context, onSaveSuccess: () -> Unit) {
        if (title.isBlank()) {
            Toast.makeText(context, "Task title is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val deadlineTime = deadlineCalendar.timeInMillis
            val now = System.currentTimeMillis()
            
            val status = if (deadlineTime < now) TaskStatus.OVERDUE else TaskStatus.PENDING

            if (taskId != null && taskId > 0) {
                // Edit existing task
                val existingTask = (uiState.value as? AddTaskUiState.Loaded)?.task
                if (existingTask != null) {
                    val updatedTask = existingTask.copy(
                        title = title.trim(),
                        description = description.trim(),
                        deadlineDateTime = deadlineTime,
                        status = status,
                        updatedAt = now,
                        importance = importance
                    )
                    val success = repository.updateTask(updatedTask)
                    if (success) {
                        ReminderScheduler.scheduleAlarmsForTask(context, updatedTask)
                        onSaveSuccess()
                    } else {
                        Toast.makeText(context, "Failed to update task", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Create new task
                val newTask = Task(
                    title = title.trim(),
                    description = description.trim(),
                    deadlineDateTime = deadlineTime,
                    status = status,
                    createdAt = now,
                    updatedAt = now,
                    importance = importance
                )
                val newId = repository.addTask(newTask)
                if (newId > 0) {
                    val taskWithId = newTask.copy(id = newId)
                    ReminderScheduler.scheduleAlarmsForTask(context, taskWithId)
                    onSaveSuccess()
                } else {
                    Toast.makeText(context, "Failed to save task", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

sealed interface AddTaskUiState {
    object Idle : AddTaskUiState
    object Loading : AddTaskUiState
    data class Loaded(val task: Task) : AddTaskUiState
    data class Error(val message: String) : AddTaskUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    taskId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { RepositoryProvider.getRepository(context) }
    val viewModel: AddTaskViewModel = viewModel(
        key = "add_task_vm_${taskId ?: 0}",
        initializer = { AddTaskViewModel(repository, taskId) }
    )

    val uiState by viewModel.uiState.collectAsState()

    val dateFormatter = remember { SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    var showTitleError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (taskId != null) "Edit Task" else "Add Task",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (uiState is AddTaskUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Task Title Input
                OutlinedTextField(
                    value = viewModel.title,
                    onValueChange = { 
                        viewModel.title = it
                        if (it.isNotBlank()) showTitleError = false
                    },
                    label = { Text("Task Title") },
                    placeholder = { Text("e.g. Complete Java Assignment") },
                    isError = showTitleError,
                    supportingText = {
                        if (showTitleError) {
                            Text("Title cannot be empty", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Task Description Input
                OutlinedTextField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it },
                    label = { Text("Description / Notes (Optional)") },
                    placeholder = { Text("Add details about the task...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Importance Level Selector
                Text(
                    text = "Importance Level",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ImportanceLevel.values().forEach { level ->
                        val selected = viewModel.importance == level
                        val containerColor = if (selected) {
                            when (level) {
                                ImportanceLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
                                ImportanceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                                ImportanceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer
                            }
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                        val contentColor = if (selected) {
                            when (level) {
                                ImportanceLevel.LOW -> MaterialTheme.colorScheme.onSecondaryContainer
                                ImportanceLevel.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
                                ImportanceLevel.HIGH -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = containerColor,
                                contentColor = contentColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.importance = level }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Deadline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Date Picker trigger
                OutlinedCard(
                    onClick = {
                        val cal = viewModel.deadlineCalendar
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val newCal = Calendar.getInstance().apply {
                                    timeInMillis = viewModel.deadlineCalendar.timeInMillis
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                }
                                viewModel.deadlineCalendar = newCal
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Due Date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dateFormatter.format(viewModel.deadlineCalendar.time),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }

                // Time Picker trigger
                OutlinedCard(
                    onClick = {
                        val cal = viewModel.deadlineCalendar
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val newCal = Calendar.getInstance().apply {
                                    timeInMillis = viewModel.deadlineCalendar.timeInMillis
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                viewModel.deadlineCalendar = newCal
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false // 12-hour format with AM/PM
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Due Time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = timeFormatter.format(viewModel.deadlineCalendar.time),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Select Time")
                    }
                }

                // Deadline in Past Warning
                val isPast = viewModel.deadlineCalendar.timeInMillis < System.currentTimeMillis()
                if (isPast) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠️ Warning: The selected deadline is in the past! This task will immediately be marked as Overdue.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Save button
                Button(
                    onClick = {
                        if (viewModel.title.isBlank()) {
                            showTitleError = true
                        } else {
                            viewModel.saveTask(context, onSaveSuccess = onBack)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (taskId != null) "Update Task" else "Save Task",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
