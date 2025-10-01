package com.odinu.forwardsms.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun EditFilterScreen(
    filterId: Int,
    viewModel: FilterViewModel,
    onNavigateBack: () -> Unit
) {
    var filter by remember { mutableStateOf<Filter?>(null) }
    var keyword by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("POST") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTemplateHelp by remember { mutableStateOf(false) }
    var keywordError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(filterId) {
        val foundFilter = viewModel.getFilterById(filterId)
        foundFilter?.let {
            filter = it
            keyword = it.keyword
            url = it.url
            method = it.method
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && errorMessage == null) {
            // Successfully updated or deleted, navigate back
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("필터 삭제") },
            text = { Text("정말로 이 필터를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        filter?.let { viewModel.deleteFilter(it) }
                        onNavigateBack()
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("필터 수정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = filter != null && !isLoading
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = if (filter != null && !isLoading) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (filter == null && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("필터를 찾을 수 없습니다")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                    placeholder = { Text("https://example.com/webhook") },
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
                                Text("메시지를 전송할 URL을 입력하세요")
                                Text(
                                    "보안을 위해 HTTPS 사용을 권장합니다",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )

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

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        // Validate before submitting
                        val tempFilter = Filter(
                            keyword = keyword.trim(),
                            url = url.trim(),
                            method = method
                        )

                        val errors = tempFilter.getValidationErrors()
                        if (errors.isNotEmpty()) {
                            keywordError = errors.find { it.contains("키워드") }
                            urlError = errors.find { it.contains("URL") || it.contains("유효") || it.contains("HTTP") || it.contains("안전") }
                            return@Button
                        }

                        viewModel.clearErrorMessage()
                        filter?.let { currentFilter ->
                            val updatedFilter = currentFilter.copy(
                                keyword = keyword.trim(),
                                url = tempFilter.getNormalizedUrl(),
                                method = method.uppercase().trim()
                            )
                            viewModel.updateFilter(updatedFilter)
                            if (viewModel.errorMessage.value == null) {
                                onNavigateBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && filter != null && keyword.isNotBlank() && url.isNotBlank() && keywordError == null && urlError == null
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("저장")
                    }
                }
            }
        }
    }
}