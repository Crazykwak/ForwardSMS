package com.odinu.forwardsms.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odinu.forwardsms.utils.AppSettings
import com.odinu.forwardsms.utils.SystemStateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemOptimizationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val systemStateManager = remember { SystemStateManager(context) }
    val appSettings = remember { AppSettings.getInstance(context) }
    var diagnosis by remember { mutableStateOf(systemStateManager.getSystemDiagnosis()) }
    var notificationMonitoringEnabled by remember { mutableStateOf(appSettings.isNotificationMonitoringEnabled) }

    // 주기적으로 시스템 상태 업데이트
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000) // 2초마다 업데이트
        diagnosis = systemStateManager.getSystemDiagnosis()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시스템 최적화") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            diagnosis = systemStateManager.getSystemDiagnosis()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 전체 상태 카드
            item {
                SystemStatusCard(diagnosis)
            }

            // 네트워크 상태
            item {
                NetworkStatusCard(diagnosis)
            }

            // 배터리 최적화 설정
            if (!diagnosis.isBatteryOptimizationIgnored) {
                item {
                    BatteryOptimizationCard(
                        onOpenSettings = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                // Fallback to general battery settings
                                val intent = systemStateManager.openBatteryOptimizationSettings()
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // 자동 시작 설정 (제조사별)
            if (diagnosis.hasAutoStartSupport) {
                item {
                    AutoStartCard(
                        manufacturer = diagnosis.deviceManufacturer,
                        onOpenSettings = {
                            systemStateManager.openAutoStartSettings()?.let { intent ->
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // 제조사별 설정 앱이 없는 경우 일반 설정으로
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        }
                    )
                }
            }

            // 알림 기반 메시지 감지 (RCS) on/off
            item {
                NotificationMonitoringCard(
                    enabled = notificationMonitoringEnabled,
                    onToggle = { enabled ->
                        notificationMonitoringEnabled = enabled
                        appSettings.isNotificationMonitoringEnabled = enabled
                    }
                )
            }

            // 추가 권한 가이드
            item {
                PermissionGuideCard()
            }

            // 문제 해결 팁
            item {
                TroubleshootingCard(diagnosis)
            }
        }
    }
}

@Composable
private fun SystemStatusCard(diagnosis: SystemStateManager.SystemDiagnosis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (diagnosis.overallStatus) {
                "정상" -> MaterialTheme.colorScheme.primaryContainer
                "일부 문제" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = when (diagnosis.overallStatus) {
                    "정상" -> Icons.Default.CheckCircle
                    "일부 문제" -> Icons.Default.Warning
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = when (diagnosis.overallStatus) {
                    "정상" -> MaterialTheme.colorScheme.primary
                    "일부 문제" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "시스템 상태: ${diagnosis.overallStatus}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "기기: ${diagnosis.deviceManufacturer} (Android ${diagnosis.androidVersion})",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            if (diagnosis.hasBackgroundIssues) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "백그라운드 제한으로 인해 SMS 수신이 지연될 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun NetworkStatusCard(diagnosis: SystemStateManager.SystemDiagnosis) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (diagnosis.isNetworkAvailable) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (diagnosis.isNetworkAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "네트워크 상태",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "연결: ${diagnosis.networkType}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (!diagnosis.isNetworkAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "네트워크에 연결되지 않아 웹훅 호출이 실패할 수 있습니다. WiFi 또는 모바일 데이터를 확인해주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "배터리 최적화 해제 필요",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "백그라운드에서 SMS를 정상적으로 수신하려면 배터리 최적화를 해제해야 합니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("배터리 설정 열기")
            }
        }
    }
}

@Composable
private fun AutoStartCard(
    manufacturer: String,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "자동 시작 설정 ($manufacturer)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${manufacturer} 기기에서는 자동 시작을 허용해야 백그라운드에서 정상 동작합니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("자동 시작 설정 열기")
            }
        }
    }
}

@Composable
private fun NotificationMonitoringCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "알림 기반 메시지 감지 (RCS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "RCS 메시지는 알림을 읽어서 감지합니다. 기기/앱에 따라 긴 메시지(LMS)의 알림 미리보기가 잘려 전체 내용이 전달되지 않을 수 있습니다. " +
                        "끄면 RCS 메시지는 감지되지 않지만, SMS/LMS는 항상 전체 내용으로 정확하게 감지됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun PermissionGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "권한 확인 사항",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val permissions = listOf(
                "SMS 수신 권한" to "SMS 메시지 감지를 위해 필요",
                "알림 접근 권한" to "RCS 메시지 감지를 위해 필요",
                "인터넷 권한" to "웹훅 호출을 위해 필요"
            )

            permissions.forEach { (title, description) ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TroubleshootingCard(diagnosis: SystemStateManager.SystemDiagnosis) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "문제 해결 팁",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val tips = mutableListOf<String>()

            if (!diagnosis.isBatteryOptimizationIgnored) {
                tips.add("• 배터리 최적화를 해제하세요")
            }

            if (!diagnosis.isNetworkAvailable) {
                tips.add("• 네트워크 연결을 확인하세요")
            }

            if (diagnosis.hasAutoStartSupport) {
                tips.add("• 자동 시작을 허용하세요 (${diagnosis.deviceManufacturer})")
            }

            tips.addAll(listOf(
                "• 앱을 최근 사용 앱에서 제거하지 마세요",
                "• 절전 모드에서 앱을 제외하세요",
                "• 알림이 차단되지 않았는지 확인하세요"
            ))

            tips.forEach { tip ->
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}