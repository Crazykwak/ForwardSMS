package com.odinu.forwardsms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odinu.forwardsms.utils.LogCollector
import android.content.Intent
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val logs by LogCollector.logs.collectAsStateWithLifecycle()
    var selectedLevel by remember { mutableStateOf<LogCollector.LogLevel?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val filteredLogs = selectedLevel?.let { level ->
        logs.filter { it.level == level }
    } ?: logs

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("로그 레벨 필터") },
            text = {
                Column(Modifier.selectableGroup()) {
                    val levels = listOf(null) + LogCollector.LogLevel.values()
                    levels.forEach { level ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (level == selectedLevel),
                                    onClick = { selectedLevel = level },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (level == selectedLevel),
                                onClick = null
                            )
                            Text(
                                text = level?.name ?: "전체",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("디버그 로그")
                        Text(
                            text = "${filteredLogs.size}개 항목${selectedLevel?.let { " (${it.name})" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "필터")
                    }
                    IconButton(onClick = {
                        LogCollector.clearLogs()
                        Toast.makeText(context, "로그가 삭제되었습니다", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "로그 삭제")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "로그가 없습니다",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SMS/RCS 메시지를 받으면 여기에 디버그 로그가 표시됩니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredLogs) { logEntry ->
                    LogEntryCard(logEntry = logEntry)
                }
            }
        }

        // 파일 내보내기 버튼
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            if (logs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val filePath = LogCollector.exportLogsToFile(context)
                        if (filePath != null) {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "로그 파일: $filePath")
                                putExtra(Intent.EXTRA_SUBJECT, "ForwardSMS 로그")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "로그 공유"))
                        } else {
                            Toast.makeText(context, "로그 내보내기 실패", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Share, contentDescription = "로그 내보내기")
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(logEntry: LogCollector.LogEntry) {
    val backgroundColor = when (logEntry.level) {
        LogCollector.LogLevel.DEBUG -> MaterialTheme.colorScheme.surface
        LogCollector.LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        LogCollector.LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        LogCollector.LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    }

    val textColor = when (logEntry.level) {
        LogCollector.LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        LogCollector.LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogCollector.LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogCollector.LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = logEntry.level.shortName,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                textColor.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = logEntry.tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = logEntry.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = logEntry.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            )
        }
    }
}