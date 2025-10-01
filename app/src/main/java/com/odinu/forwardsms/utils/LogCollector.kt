package com.odinu.forwardsms.utils

import android.content.Context
import android.util.Log
import com.odinu.forwardsms.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogCollector {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val maxLogs = 1000

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    )

    enum class LogLevel(val shortName: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E")
    }

    fun addLog(level: LogLevel, tag: String, message: String) {
        // 보안 필터링: 민감정보가 포함된 메시지는 마스킹
        val sanitizedMessage = sanitizeLogMessage(message)

        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, tag, sanitizedMessage)

        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, entry) // 최신순으로 추가

        // 최대 로그 수 제한
        if (currentLogs.size > maxLogs) {
            currentLogs.removeAt(currentLogs.size - 1)
        }

        _logs.value = currentLogs

        // 안드로이드 로그에도 출력 (운영에서는 제한적으로)
        if (shouldOutputToSystemLog(level)) {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, sanitizedMessage)
                LogLevel.INFO -> Log.i(tag, sanitizedMessage)
                LogLevel.WARN -> Log.w(tag, sanitizedMessage)
                LogLevel.ERROR -> Log.e(tag, sanitizedMessage)
            }
        }
    }

    /**
     * 민감정보 자동 필터링
     */
    private fun sanitizeLogMessage(message: String): String {
        var sanitized = message

        // 전화번호 패턴 마스킹
        sanitized = sanitized.replace(Regex("\\+?[0-9]{10,15}")) { matchResult ->
            val number = matchResult.value
            if (number.length >= 8) {
                "${number.take(3)}****${number.takeLast(3)}"
            } else {
                "***${number.takeLast(2)}"
            }
        }

        // URL 민감정보 마스킹
        sanitized = sanitized.replace(Regex("https?://[^\\s]+")) { matchResult ->
            val url = matchResult.value
            try {
                val uri = java.net.URI(url)
                val host = uri.host ?: "unknown"
                val maskedHost = if (host.length > 10) {
                    "${host.take(5)}***${host.takeLast(5)}"
                } else {
                    "${host.take(3)}***"
                }
                "${uri.scheme}://$maskedHost[...]"
            } catch (e: Exception) {
                "[URL]"
            }
        }

        // 긴 텍스트 절단 (잠재적 메시지 내용)
        if (sanitized.length > 100 && !sanitized.contains("요청") && !sanitized.contains("처리")) {
            sanitized = "${sanitized.take(30)}...[${sanitized.length}자]"
        }

        return sanitized
    }

    /**
     * 시스템 로그 출력 여부 결정
     */
    private fun shouldOutputToSystemLog(level: LogLevel): Boolean {
        return if (BuildConfig.DEBUG) {
            true // 개발 모드에서는 모든 로그 출력
        } else {
            // 운영 모드에서는 INFO 이상만 출력
            level.ordinal >= LogLevel.INFO.ordinal
        }
    }

    fun d(tag: String, message: String) = addLog(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = addLog(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = addLog(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = addLog(LogLevel.ERROR, tag, message)

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun exportLogsToFile(context: Context): String? {
        return try {
            val logFile = File(context.filesDir, "forwardsms_logs_${System.currentTimeMillis()}.txt")
            val content = _logs.value.joinToString("\n") { entry ->
                "${entry.timestamp} ${entry.level.shortName}/${entry.tag}: ${entry.message}"
            }
            logFile.writeText(content)
            logFile.absolutePath
        } catch (e: Exception) {
            Log.e("LogCollector", "Failed to export logs: ${e.message}")
            null
        }
    }
}