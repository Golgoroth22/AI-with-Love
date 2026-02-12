package com.example.aiwithlove.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiwithlove.data.model.Message
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
                // Threshold control panel
                val threshold by viewModel.searchThreshold.collectAsState()
                ThresholdControlPanel(
                    threshold = threshold,
                    onThresholdChange = { viewModel.updateSearchThreshold(it) }
                )

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
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.7f)
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
                    .padding(horizontal = 20.dp, vertical = 16.dp)
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
                lineHeight = 21.sp
            )

            // Show help command badge for user messages
            if (message.isFromUser && message.isHelpCommand && message.helpDocsFound != null) {
                Row(
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📚", fontSize = 14.sp)
                    Text(
                        text = "${message.helpDocsFound} docs found",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Show attached log file if present

            if (!message.isFromUser && message.mcpToolInfo != null && message.mcpToolInfo.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.mcpToolInfo.forEach { toolInfo ->
                        when (toolInfo.toolName) {
                            "semantic_search" -> {
                                // Show detailed semantic search results
                                toolInfo.semanticSearchResult?.let { result ->
                                    SemanticSearchResultCard(result = result)
                                } ?: run {
                                    // Fallback to simple badge if parsing failed
                                    SimpleToolBadge(toolName = toolInfo.toolName)
                                }
                            }

                            else -> {
                                // Other tools: show simple badge
                                SimpleToolBadge(toolName = toolInfo.toolName)
                            }
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

/**
 * Simple tool badge for non-semantic-search tools
 */
@Composable
private fun SimpleToolBadge(toolName: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔧", fontSize = 14.sp)
        Text(
            text = "Tool: $toolName",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Card displaying semantic search results with similarity scores
 */
@Composable
private fun SemanticSearchResultCard(
    result: com.example.aiwithlove.data.model.SemanticSearchResult,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header with threshold info
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌐", fontSize = 12.sp)
                    Text(
                        text = "Search Results",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                if (result.threshold != null) {
                    Text(
                        text = "${(result.threshold * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Comparison mode or single result
            if (result.unfiltered != null && result.filteredResults != null) {
                ComparisonView(
                    unfiltered = result.unfiltered,
                    filtered = result.filteredResults,
                    threshold = result.threshold ?: 0.7
                )
            } else {
                // Single result view
                result.documents?.let { docs ->
                    if (docs.isEmpty()) {
                        Text(
                            text = "No relevant documents found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        DocumentList(documents = docs)
                    }
                }
            }
        }
    }
}

/**
 * List of document items
 */
@Composable
private fun DocumentList(
    documents: List<com.example.aiwithlove.data.model.SemanticSearchDocument>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        documents.forEachIndexed { index, doc ->
            DocumentItem(
                document = doc,
                index = index + 1
            )
        }
    }
}

/**
 * Single document item with similarity score
 */
@Composable
private fun DocumentItem(
    document: com.example.aiwithlove.data.model.SemanticSearchDocument,
    index: Int
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    RoundedCornerShape(6.dp)
                )
                .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Header: index + similarity score
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "#$index",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            SimilarityScoreBar(similarity = document.similarity)
        }

        // Document content (truncated to 100 chars)
        Text(
            text = document.content.take(100) + if (document.content.length > 100) "..." else "",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 14.sp
        )
    }
}

/**
 * Visual similarity score bar with percentage
 */
@Composable
private fun SimilarityScoreBar(similarity: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Percentage text
        Text(
            text = "${(similarity * 100).toInt()}%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color =
                when {
                    similarity >= 0.8 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)

                    // Green
                    similarity >= 0.6 -> androidx.compose.ui.graphics.Color(0xFFFF9800)

                    // Orange
                    else -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
                }
        )

        // Progress bar
        Box(
            modifier =
                Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .fillMaxWidth(similarity.toFloat())
                        .background(
                            when {
                                similarity >= 0.8 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                similarity >= 0.6 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                                else -> androidx.compose.ui.graphics.Color(0xFFF44336)
                            }
                        )
            )
        }
    }
}

/**
 * Comparison view showing unfiltered vs filtered results side-by-side
 */
@Composable
private fun ComparisonView(
    unfiltered: com.example.aiwithlove.data.model.DocumentSet,
    filtered: com.example.aiwithlove.data.model.DocumentSet,
    threshold: Double
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Summary stats
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(
                label = "Unfiltered",
                value = unfiltered.count.toString(),
                color = MaterialTheme.colorScheme.secondary
            )

            androidx.compose.material3.HorizontalDivider(
                modifier =
                    Modifier
                        .size(width = 1.dp, height = 30.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            StatItem(
                label = "Filtered",
                value = filtered.count.toString(),
                color = MaterialTheme.colorScheme.primary
            )

            androidx.compose.material3.HorizontalDivider(
                modifier =
                    Modifier
                        .size(width = 1.dp, height = 30.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            StatItem(
                label = "Removed",
                value = (unfiltered.count - filtered.count).toString(),
                color = androidx.compose.ui.graphics.Color(0xFFF44336)
            )
        }

        // Side-by-side comparison
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Unfiltered column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Unfiltered",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                CompactDocumentList(unfiltered.documents, threshold)
            }

            // Filtered column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Filtered",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                CompactDocumentList(filtered.documents, threshold)
            }
        }
    }
}

/**
 * Statistic item for comparison summary
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compact document list for comparison view
 */
@Composable
private fun CompactDocumentList(
    documents: List<com.example.aiwithlove.data.model.SemanticSearchDocument>,
    threshold: Double
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        documents.take(3).forEachIndexed { index, doc ->
            CompactDocumentItem(
                document = doc,
                index = index + 1,
                isBelowThreshold = doc.similarity < threshold
            )
        }

        if (documents.size > 3) {
            Text(
                text = "+${documents.size - 3} more...",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/**
 * Compact document item for comparison view
 */
@Composable
private fun CompactDocumentItem(
    document: com.example.aiwithlove.data.model.SemanticSearchDocument,
    index: Int,
    isBelowThreshold: Boolean
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (isBelowThreshold) {
                        androidx.compose.ui.graphics.Color(0xFFF44336).copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
                .border(
                    0.5.dp,
                    if (isBelowThreshold) {
                        androidx.compose.ui.graphics.Color(0xFFF44336).copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    },
                    RoundedCornerShape(3.dp)
                )
                .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$index: ${document.content.take(25)}...",
            fontSize = 9.sp,
            color =
                if (isBelowThreshold) {
                    androidx.compose.ui.graphics.Color(0xFFF44336)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${(document.similarity * 100).toInt()}%",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color =
                if (isBelowThreshold) {
                    androidx.compose.ui.graphics.Color(0xFFF44336)
                } else {
                    androidx.compose.ui.graphics.Color(0xFF4CAF50)
                }
        )
    }
}

/**
 * Expandable threshold control panel with slider
 */
@Composable
private fun ThresholdControlPanel(
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header with expand/collapse
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎚️",
                    fontSize = 16.sp
                )
                Text(
                    text = "Search Threshold",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(threshold * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Expandable slider section
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Slider
                androidx.compose.material3.Slider(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    valueRange = 0.3f..0.95f,
                    steps = 12, // 0.05 increments
                    modifier = Modifier.fillMaxWidth()
                )

                // Visual guide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ThresholdLabel("30%", "More results", androidx.compose.ui.graphics.Color(0xFFF44336))
                    ThresholdLabel("70%", "Balanced", androidx.compose.ui.graphics.Color(0xFFFF9800))
                    ThresholdLabel("95%", "High quality", androidx.compose.ui.graphics.Color(0xFF4CAF50))
                }

                // Description
                Text(
                    text =
                        when {
                            threshold >= 0.8f -> "High precision: Only very relevant documents"
                            threshold >= 0.6f -> "Balanced: Good mix of relevance and coverage"
                            else -> "High recall: More results, some may be less relevant"
                        },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Label for threshold guide
 */
@Composable
private fun ThresholdLabel(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    AIWithLoveTheme {
        ChatScreen()
    }
}
