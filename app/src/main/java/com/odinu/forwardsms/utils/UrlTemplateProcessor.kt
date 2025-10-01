package com.odinu.forwardsms.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlTemplateProcessor {

    /**
     * URL 템플릿의 플레이스홀더를 실제 값으로 치환합니다.
     *
     * 지원하는 플레이스홀더:
     * - ${message} 또는 ${msg}: SMS 메시지 본문
     * - ${sender}: 발신자 번호
     * - ${timestamp}: Unix timestamp
     *
     * 예시:
     * "http://example.com/webhook?msg=${message}&from=${sender}"
     * → "http://example.com/webhook?msg=인증번호%20123456&from=%2B821012345678"
     */
    fun processTemplate(
        urlTemplate: String,
        message: String,
        sender: String?,
        timestamp: Long
    ): String {
        var processedUrl = urlTemplate

        // URL 인코딩된 값들
        val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
        val encodedSender = URLEncoder.encode(sender ?: "unknown", StandardCharsets.UTF_8.toString())
        val timestampStr = timestamp.toString()

        // 플레이스홀더 치환
        processedUrl = processedUrl.replace("\${message}", encodedMessage, ignoreCase = true)
        processedUrl = processedUrl.replace("\${msg}", encodedMessage, ignoreCase = true)
        processedUrl = processedUrl.replace("\${sender}", encodedSender, ignoreCase = true)
        processedUrl = processedUrl.replace("\${from}", encodedSender, ignoreCase = true)
        processedUrl = processedUrl.replace("\${timestamp}", timestampStr, ignoreCase = true)
        processedUrl = processedUrl.replace("\${time}", timestampStr, ignoreCase = true)

        return processedUrl
    }

    /**
     * URL 템플릿이 유효한지 검사합니다.
     */
    fun isValidTemplate(urlTemplate: String): Boolean {
        return try {
            // 기본 URL 유효성 검사
            if (!urlTemplate.startsWith("http://") && !urlTemplate.startsWith("https://")) {
                return false
            }

            // 플레이스홀더 형식 검사 (선택사항)
            val placeholderPattern = Regex("\\$\\{[a-zA-Z_][a-zA-Z0-9_]*\\}")
            val placeholders = placeholderPattern.findAll(urlTemplate).map { it.value }.toList()

            // 지원하지 않는 플레이스홀더가 있는지 확인
            val supportedPlaceholders = listOf(
                "\${message}", "\${msg}", "\${sender}", "\${from}",
                "\${timestamp}", "\${time}"
            )

            for (placeholder in placeholders) {
                if (!supportedPlaceholders.contains(placeholder.lowercase())) {
                    // 경고는 하지만 실패로 처리하지는 않음
                    android.util.Log.w("UrlTemplateProcessor",
                        "Unsupported placeholder: $placeholder")
                }
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * GET 요청에서 기존 쿼리 파라미터가 있는지 확인하고 적절히 연결합니다.
     */
    fun buildGetUrl(urlTemplate: String, message: String, sender: String?, timestamp: Long): String {
        // 사용자가 템플릿을 제공한 경우 그것을 사용
        if (urlTemplate.contains("\${")) {
            return processTemplate(urlTemplate, message, sender, timestamp)
        }

        // 기존 방식 (하위 호환성)
        val baseUrl = urlTemplate.trimEnd('?', '&')
        val separator = if (urlTemplate.contains('?')) "&" else "?"
        val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
        val encodedSender = URLEncoder.encode(sender ?: "unknown", StandardCharsets.UTF_8.toString())

        return "$baseUrl${separator}message=$encodedMessage&sender=$encodedSender&timestamp=$timestamp"
    }
}