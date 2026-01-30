package com.example.aiwithlove.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiwithlove.ui.theme.AIWithLoveTheme
import com.example.aiwithlove.viewmodel.ChatViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = koinViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()
    val showMcpDialog by viewModel.showMcpDialog.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isKeyboardVisible by remember { mutableStateOf(false) }

    if (showMcpDialog) {
        McpServerDialog(
            servers = mcpServers,
            onDismiss = { viewModel.toggleMcpDialog() },
            onToggleServer = { serverId -> viewModel.toggleMcpServer(serverId) }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            listState.animateScrollToItem(lastIndex)
        }
    }

    LaunchedEffect(messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            kotlinx.coroutines.delay(5)
            try {
                listState.scrollToItem(
                    index = lastIndex,
                    scrollOffset = Int.MIN_VALUE,
                )
            } catch (e: Exception) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Чат with Love",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    val enabledCount = mcpServers.count { it.isEnabled }
                    IconButton(onClick = { viewModel.toggleMcpDialog() }) {
                        if (enabledCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text("$enabledCount")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "MCP Tools"
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "MCP Tools"
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages.filterNot { it.isSummary }) { message ->
                    MessageBubble(message = message)
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .imePadding()
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    isKeyboardVisible = focusState.isFocused
                                },
                        placeholder = { Text("Введите сообщение...") },
                        shape = RoundedCornerShape(24.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        singleLine = false,
                        maxLines = 4,
                        enabled = !isLoading
                    )
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val userMessage = inputText
                                inputText = ""
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                viewModel.sendMessage(userMessage)
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Отправить"
                            )
                        }
                    }
                }

                if (!isKeyboardVisible) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearChat()
                                inputText = ""
                                focusManager.clearFocus()
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Новый чат",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Новый чат",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatViewModel.Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isFromUser) 4.dp else 16.dp
                        ),
                    )
                    .background(
                        if (message.isFromUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                color =
                    if (message.isFromUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
            if (!message.isFromUser && message.mcpToolInfo != null && message.mcpToolInfo.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.mcpToolInfo.forEach { toolInfo ->
                        Row(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔧",
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Tool: ${toolInfo.toolName}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            if (!message.isFromUser && (message.promptTokens != null || message.completionTokens != null)) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.promptTokens?.let {
                        Box(
                            modifier =
                                Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 4.dp,
                                            bottomStart = 12.dp,
                                            bottomEnd = 12.dp
                                        )
                                    )
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape =
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 4.dp,
                                                bottomStart = 12.dp,
                                                bottomEnd = 12.dp
                                            )
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📤",
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "$it tokens (request)",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    message.completionTokens?.let {
                        Box(
                            modifier =
                                Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 4.dp,
                                            bottomStart = 12.dp,
                                            bottomEnd = 12.dp
                                        )
                                    )
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape =
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 4.dp,
                                                bottomStart = 12.dp,
                                                bottomEnd = 12.dp
                                            )
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📥",
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "$it tokens (response)",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    AIWithLoveTheme {
        ChatScreen()
    }
}
