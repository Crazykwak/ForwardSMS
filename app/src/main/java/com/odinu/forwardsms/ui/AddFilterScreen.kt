package com.odinu.forwardsms.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odinu.forwardsms.data.Filter
import com.odinu.forwardsms.utils.UrlValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterScreen(
    viewModel: FilterViewModel,
    onNavigateBack: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("KEYWORD") }
    var url by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("POST") }
    var showTemplateHelp by remember { mutableStateOf(false) }
    var keywordError by remember { mutableStateOf<String?>(null) }
    var phoneNumberError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(isLoading) {
        if (!isLoading && errorMessage == null) {
        }
    }

    // 템플릿 도움말 다이얼로그
    if (showTemplateHelp) {
        AlertDialog(
            onDismissRequest = { showTemplateHelp = false },
            title = {
                Text(
                    "URL 템플릿 가이드",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "템플릿 변수를 사용해 동적으로 SMS 데이터를 전송할 수 있습니다:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    listOf(
                        "\${message} 또는 \${msg}" to "SMS 메시지 본문",
                        "\${sender} 또는 \${from}" to "발신자 번호",
                        "\${timestamp} 또는 \${time}" to "수신 시간"
                    ).forEach { (template, description) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = template,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // 복사 기능은 간소화
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "예시:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "입력: https://api.example.com/sms?msg=\${message}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "실제 호출: https://api.example.com/sms?msg=인증번호%20123456",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateHelp = false }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("필터 추가") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Filter Type Selection
            Column {
                Text(
                    text = "필터 타입",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    val filterTypes = listOf(
                        "KEYWORD" to "키워드 필터",
                        "PHONE_NUMBER" to "전화번호 필터",
                        "BOTH" to "키워드 + 전화번호"
                    )
                    filterTypes.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (value == filterType),
                                    onClick = { filterType = value },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (value == filterType),
                                onClick = null,
                                enabled = !isLoading
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }

            // Keyword input - show based on filter type
            if (filterType == "KEYWORD" || filterType == "BOTH") {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = {
                        keyword = it
                        keywordError = if (it.isBlank()) "키워드를 입력해주세요" else null
                    },
                    label = { Text("키워드") },
                    placeholder = { Text("예: 인증번호") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = keywordError != null,
                    supportingText = {
                        Text(keywordError ?: "SMS 본문에서 찾을 키워드를 입력하세요")
                    }
                )
            }

            // Phone number input - show based on filter type
            if (filterType == "PHONE_NUMBER" || filterType == "BOTH") {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        phoneNumberError = if (it.isBlank()) "전화번호를 입력해주세요" else null
                    },
                    label = { Text("전화번호") },
                    placeholder = { Text("예: 01012345678") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = phoneNumberError != null,
                    supportingText = {
                        Text(phoneNumberError ?: "필터링할 발신자 전화번호를 입력하세요")
                    }
                )
            }

            // URL 입력 섹션
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "웹훅 URL 설정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showTemplateHelp = true }) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "템플릿 도움말",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            urlError = if (it.isBlank()) {
                                "URL을 입력해주세요"
                            } else {
                                val validation = UrlValidator.validateUrl(it)
                                if (!validation.isValid) validation.message else null
                            }
                        },
                        label = { Text("URL") },
                        placeholder = { Text("https://example.com/api?msg=\${message}") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        isError = urlError != null,
                        supportingText = {
                            if (urlError != null) {
                                Text(
                                    urlError!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Column {
                                    Text(
                                        "템플릿 변수: \${message}, \${sender}, \${timestamp}",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "보안을 위해 HTTPS 사용을 권장합니다",
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    )

                    // 템플릿 예시 버튼들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                url = "https://example.com/webhook?msg=\${message}&from=\${sender}"
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Text("기본 템플릿", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                url = "https://api.example.com/sms?content=\${message}&phone=\${sender}&time=\${timestamp}"
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Text("상세 템플릿", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Column {
                Text(
                    text = "HTTP 메서드",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    val methods = listOf("GET", "POST")
                    methods.forEach { text ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (text == method),
                                    onClick = { method = text },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (text == method),
                                onClick = null,
                                enabled = !isLoading
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    // Validate before submitting
                    val tempFilter = Filter(
                        keyword = keyword.trim(),
                        phoneNumber = phoneNumber.trim(),
                        filterType = filterType,
                        url = url.trim(),
                        method = method
                    )

                    val errors = tempFilter.getValidationErrors()
                    if (errors.isNotEmpty()) {
                        keywordError = errors.find { it.contains("키워드") }
                        phoneNumberError = errors.find { it.contains("전화번호") }
                        urlError = errors.find { it.contains("URL") || it.contains("유효") || it.contains("HTTP") || it.contains("안전") }
                        return@Button
                    }

                    viewModel.clearErrorMessage()
                    viewModel.addFilter(
                        keyword = keyword.trim(),
                        url = tempFilter.getNormalizedUrl(),
                        method = method,
                        filterType = filterType,
                        phoneNumber = phoneNumber.trim()
                    )
                    if (viewModel.errorMessage.value == null) {
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && url.isNotBlank() && keywordError == null && phoneNumberError == null && urlError == null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("필터 추가")
                }
            }
        }
    }
}