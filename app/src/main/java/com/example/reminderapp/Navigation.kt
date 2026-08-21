package com.example.reminderapp

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.reminderapp.ui.add.AddTaskScreen
import com.example.reminderapp.ui.details.TaskDetailsScreen
import com.example.reminderapp.ui.main.MainScreen

@Composable
fun MainNavigation(initialTaskId: Long? = null) {
  val backStack = rememberNavBackStack(Main)

  // Handle deep link when launched from notification
  LaunchedEffect(initialTaskId) {
    if (initialTaskId != null && initialTaskId > 0L) {
      // Navigate to details on top of the main screen
      backStack.add(TaskDetails(initialTaskId))
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<AddTask> {
          AddTaskScreen(
            onBack = { backStack.removeLastOrNull() }
          )
        }
        entry<TaskDetails> { key ->
          TaskDetailsScreen(
            taskId = key.taskId,
            onBack = { backStack.removeLastOrNull() },
            onEditClick = { id -> backStack.add(EditTask(id)) }
          )
        }
        entry<EditTask> { key ->
          AddTaskScreen(
            taskId = key.taskId,
            onBack = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
