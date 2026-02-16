package com.example.aiwithlove.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.viewmodel.SupportViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    onNavigateBack: () -> Unit,
    viewModel: SupportViewModel = koinViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentTicketId by viewModel.currentTicketId.collectAsState()
    val taskContext by viewModel.taskContext.collectAsState()
    val availableUsers by viewModel.availableUsers.collectAsState()
    val selectedUser by viewModel.selectedUser.collectAsState()
    val showCreateTaskDialog by viewModel.showCreateTaskDialog.collectAsState()
    val taskFormState by viewModel.taskFormState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var userDropdownExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Task creation dialog
    if (showCreateTaskDialog) {
        CreateTaskDialog(
            initialState = taskFormState,
            onDismiss = { viewModel.toggleCreateTaskDialog() },
            onCreateTask = { formState ->
                viewModel.createTaskFromDialog(formState)
                viewModel.toggleCreateTaskDialog()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🎫 Ассистент команды")

                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentTicketId?.let {
                                Text(
                                    text = "Тикет #$it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            taskContext?.let { context ->
                                if (context.activeCount > 0) {
                                    Text(
                                        text = "• ${context.activeCount} задач",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (context.highPriorityCount > 0) {
                                    Text(
                                        text = "🔴 ${context.highPriorityCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearSupportSession() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Начать новый диалог")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            // Messages list
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }

            // Input area
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
            ) {
                // User selector dropdown
                if (availableUsers.isNotEmpty()) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "👤 Член команды:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                selectedUser?.let { user ->
                                    Text(
                                        text = "${user.name} (${user.role})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }

                            IconButton(onClick = { userDropdownExpanded = !userDropdownExpanded }) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Выбрать пользователя",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }

                            DropdownMenu(
                                expanded = userDropdownExpanded,
                                onDismissRequest = { userDropdownExpanded = false }
                            ) {
                                availableUsers.forEach { user ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = user.name,
                                                    fontWeight =
                                                        if (user.id == selectedUser?.id) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        }
                                                )
                                                Text(
                                                    text = "${user.role} • ${user.skills.joinToString(", ")}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectUser(user)
                                            userDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Ticket indicator (if exists)
                currentTicketId?.let { ticketId ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                    ) {
                        Text(
                            text = "📝 Активный тикет: #$ticketId",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Input field + Send button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Опишите вашу проблему...") },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = false,
                        maxLines = 4,
                        enabled = !isLoading
                    )

                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }
        }
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
}

@Composable
fun CreateTaskDialog(
    initialState: SupportViewModel.TaskFormState = SupportViewModel.TaskFormState(),
    onDismiss: () -> Unit,
    onCreateTask: (SupportViewModel.TaskFormState) -> Unit
) {
    var title by remember { mutableStateOf(initialState.title) }
    var description by remember { mutableStateOf(initialState.description) }
    var priority by remember { mutableStateOf(initialState.priority) }
    var showPriorityMenu by remember { mutableStateOf(false) }

    // Form validation
    val isFormValid = title.isNotBlank() && description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "✅ Создать задачу",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название задачи *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = title.isBlank(),
                    supportingText = {
                        if (title.isBlank()) {
                            Text("Обязательное поле", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    isError = description.isBlank(),
                    supportingText = {
                        if (description.isBlank()) {
                            Text("Обязательное поле", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                // Priority dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showPriorityMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Приоритет: ${getPriorityDisplayName(priority)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать приоритет"
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showPriorityMenu,
                        onDismissRequest = { showPriorityMenu = false }
                    ) {
                        listOf("high", "medium", "low").forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = getPriorityDisplayName(p),
                                        fontWeight = if (p == priority) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    priority = p
                                    showPriorityMenu = false
                                },
                                leadingIcon = {
                                    if (p == priority) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Выбрано",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isFormValid) {
                        onCreateTask(SupportViewModel.TaskFormState(title, description, priority))
                    }
                },
                enabled = isFormValid
            ) {
                Text(
                    text = "Создать",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// Helper function for priority display names
private fun getPriorityDisplayName(priority: String): String =
    when (priority) {
        "high" -> "🔴 Высокий"
        "medium" -> "🟡 Средний"
        "low" -> "🟢 Низкий"
        else -> priority
    }
