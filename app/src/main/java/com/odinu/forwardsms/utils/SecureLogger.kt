package com.odinu.forwardsms.utils

import android.util.Log
import com.odinu.forwardsms.BuildConfig
import java.security.MessageDigest

object SecureLogger {

    private const val MAX_MESSAGE_LENGTH = 20
    private const val HASH_LENGTH = 8

    /**
     * 로그 레벨 설정
     */
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR, NONE
    }

    /**
     * 운영/개발 환경별 로그 레벨
     */
    private val currentLogLevel: LogLevel = if (BuildConfig.DEBUG) {
        LogLevel.DEBUG
    } else {
        LogLevel.INFO // 운영에서는 INFO 이상만 로깅
    }

    /**
     * 민감정보 마스킹 타입
     */
    enum class SensitiveType {
        MESSAGE_CONTENT,    // SMS 메시지 내용
        PHONE_NUMBER,       // 전화번호
        URL,               // 웹훅 URL
        EMAIL,             // 이메일
        GENERIC            // 일반 민감정보
    }

    /**
     * 안전한 디버그 로그
     */
    fun d(tag: String, message: String, sensitiveData: Map<String, SensitiveType> = emptyMap()) {
        if (shouldLog(LogLevel.DEBUG)) {
            val safeMessage = sanitizeMessage(message, sensitiveData)
            Log.d(tag, safeMessage)
        }
    }

    /**
     * 안전한 정보 로그
     */
    fun i(tag: String, message: String, sensitiveData: Map<String, SensitiveType> = emptyMap()) {
        if (shouldLog(LogLevel.INFO)) {
            val safeMessage = sanitizeMessage(message, sensitiveData)
            Log.i(tag, safeMessage)
        }
    }

    /**
     * 안전한 경고 로그
     */
    fun w(tag: String, message: String, sensitiveData: Map<String, SensitiveType> = emptyMap()) {
        if (shouldLog(LogLevel.WARN)) {
            val safeMessage = sanitizeMessage(message, sensitiveData)
            Log.w(tag, safeMessage)
        }
    }

    /**
     * 안전한 에러 로그
     */
    fun e(tag: String, message: String, throwable: Throwable? = null, sensitiveData: Map<String, SensitiveType> = emptyMap()) {
        if (shouldLog(LogLevel.ERROR)) {
            val safeMessage = sanitizeMessage(message, sensitiveData)
            if (throwable != null) {
                Log.e(tag, safeMessage, throwable)
            } else {
                Log.e(tag, safeMessage)
            }
        }
    }

    /**
     * SMS 메시지 로깅 (특별 처리)
     */
    fun logSmsReceived(tag: String, sender: String?, messageBody: String) {
        if (shouldLog(LogLevel.INFO)) {
            val maskedSender = maskPhoneNumber(sender)
            val maskedMessage = maskMessageContent(messageBody)
            val messageHash = hashString(messageBody)

            Log.i(tag, "SMS 수신: $maskedSender -> $maskedMessage [hash:$messageHash]")
        }
    }

    /**
     * 웹훅 호출 로깅
     */
    fun logWebhookCall(tag: String, url: String, method: String, success: Boolean, responseCode: Int? = null) {
        if (shouldLog(LogLevel.INFO)) {
            val maskedUrl = maskUrl(url)
            val status = if (success) "성공" else "실패"
            val codeInfo = responseCode?.let { " (HTTP $it)" } ?: ""

            Log.i(tag, "웹훅 호출 $status: $method $maskedUrl$codeInfo")
        }
    }

    /**
     * 필터 매칭 로깅
     */
    fun logFilterMatch(tag: String, keyword: String, messageBody: String) {
        if (shouldLog(LogLevel.DEBUG)) {
            val maskedMessage = maskMessageContent(messageBody)
            val messageHash = hashString(messageBody)

            Log.d(tag, "키워드 '${keyword}' 매칭: $maskedMessage [hash:$messageHash]")
        }
    }

    /**
     * 메시지 내용 마스킹
     */
    private fun maskMessageContent(message: String): String {
        return when {
            message.isEmpty() -> "[빈 메시지]"
            message.length <= MAX_MESSAGE_LENGTH -> {
                // 짧은 메시지는 부분만 표시
                "${message.take(5)}***${if (message.length > 5) message.takeLast(2) else ""}"
            }
            else -> {
                // 긴 메시지는 앞뒤만 표시
                "${message.take(10)}...${message.takeLast(5)} (${message.length}자)"
            }
        }
    }

    /**
     * 전화번호 마스킹
     */
    private fun maskPhoneNumber(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty()) return "[알 수 없음]"

        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")

        return when {
            cleaned.length <= 4 -> "***"
            cleaned.startsWith("+82") && cleaned.length >= 8 -> {
                // 한국 번호: +8201*****678
                "+8201*****${cleaned.takeLast(3)}"
            }
            cleaned.startsWith("010") && cleaned.length >= 8 -> {
                // 010 번호: 010****5678
                "010****${cleaned.takeLast(4)}"
            }
            cleaned.length >= 8 -> {
                // 일반 번호: 앞 3자리, 뒤 3자리만 표시
                "${cleaned.take(3)}****${cleaned.takeLast(3)}"
            }
            else -> "***${cleaned.takeLast(2)}"
        }
    }

    /**
     * URL 마스킹
     */
    private fun maskUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host
            val maskedHost = when {
                host.isNullOrEmpty() -> "[알 수 없는 호스트]"
                host.length <= 10 -> "${host.take(3)}***${host.takeLast(2)}"
                else -> "${host.take(5)}***${host.takeLast(5)}"
            }

            val path = uri.path?.let {
                if (it.length > 20) "${it.take(10)}...${it.takeLast(5)}" else it
            } ?: ""

            "${uri.scheme}://$maskedHost$path"
        } catch (e: Exception) {
            "[잘못된 URL]"
        }
    }

    /**
     * 문자열 해시 생성 (디버깅 추적용)
     */
    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
        } catch (e: Exception) {
            "hash_error"
        }
    }

    /**
     * 메시지 정리 (민감정보 치환)
     */
    private fun sanitizeMessage(message: String, sensitiveData: Map<String, SensitiveType>): String {
        var sanitized = message

        sensitiveData.forEach { (value, type) ->
            val masked = when (type) {
                SensitiveType.MESSAGE_CONTENT -> maskMessageContent(value)
                SensitiveType.PHONE_NUMBER -> maskPhoneNumber(value)
                SensitiveType.URL -> maskUrl(value)
                SensitiveType.EMAIL -> maskEmail(value)
                SensitiveType.GENERIC -> maskGeneric(value)
            }
            sanitized = sanitized.replace(value, masked)
        }

        return sanitized
    }

    /**
     * 이메일 마스킹
     */
    private fun maskEmail(email: String): String {
        if (!email.contains("@")) return "[잘못된 이메일]"

        val parts = email.split("@")
        val local = parts[0]
        val domain = parts[1]

        val maskedLocal = when {
            local.length <= 2 -> "**"
            local.length <= 4 -> "${local.first()}**"
            else -> "${local.take(2)}***${local.takeLast(1)}"
        }

        val maskedDomain = when {
            domain.length <= 4 -> "**.com"
            else -> "${domain.take(2)}***${domain.takeLast(4)}"
        }

        return "$maskedLocal@$maskedDomain"
    }

    /**
     * 일반 민감정보 마스킹
     */
    private fun maskGeneric(value: String): String {
        return when {
            value.isEmpty() -> "[빈 값]"
            value.length <= 3 -> "***"
            value.length <= 8 -> "${value.first()}***${value.last()}"
            else -> "${value.take(3)}***${value.takeLast(3)}"
        }
    }

    /**
     * 로그 레벨 확인
     */
    private fun shouldLog(level: LogLevel): Boolean {
        return level.ordinal >= currentLogLevel.ordinal
    }

    /**
     * 개발용 상세 로그 (운영에서 자동 비활성화)
     */
    fun verbose(tag: String, message: String) {
        if (BuildConfig.DEBUG && shouldLog(LogLevel.VERBOSE)) {
            Log.v(tag, message)
        }
    }

    /**
     * 로그 레벨 정보 반환
     */
    fun getLogInfo(): LogInfo {
        return LogInfo(
            currentLevel = currentLogLevel,
            isDebugMode = BuildConfig.DEBUG,
            securityEnabled = true
        )
    }

    data class LogInfo(
        val currentLevel: LogLevel,
        val isDebugMode: Boolean,
        val securityEnabled: Boolean
    )
}