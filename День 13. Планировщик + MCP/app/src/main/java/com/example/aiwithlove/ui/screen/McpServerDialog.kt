package com.example.aiwithlove.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiwithlove.mcp.McpServerConfig

@Composable
fun McpServerDialog(
    servers: List<McpServerConfig>,
    onDismiss: () -> Unit,
    onToggleServer: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🔧 MCP Серверы")
        },
        text = {
            Column {
                Text(
                    text = "Включите серверы для расширения возможностей чата:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(servers) { server ->
                        ServerItem(
                            server = server,
                            onToggle = { onToggleServer(server.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

@Composable
fun ServerItem(
    server: McpServerConfig,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (server.isEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (server.isEnabled) 2.dp else 0.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = server.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (server.isEnabled || server.benefits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(8.dp)
                    ) {
                        Text(
                            text = "💡 Что даёт включение:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = server.benefits,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (server.tools.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Доступные инструменты:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            server.tools.forEach { tool ->
                                Text(
                                    text = "• $tool",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Checkbox(
                checked = server.isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
