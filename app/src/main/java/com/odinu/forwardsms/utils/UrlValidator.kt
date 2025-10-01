package com.odinu.forwardsms.utils

import android.util.Patterns
import java.net.MalformedURLException
import java.net.URL

object UrlValidator {

    fun isValidUrl(url: String): Boolean {
        return try {
            if (url.isBlank()) {
                false
            } else {
                val normalizedUrl = normalizeUrl(url)
                isValidUrlFormat(normalizedUrl) && isAllowedScheme(normalizedUrl)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun validateUrl(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult(false, "URL을 입력해주세요")
        }

        val normalizedUrl = normalizeUrl(url)

        if (!isValidUrlFormat(normalizedUrl)) {
            return ValidationResult(false, "유효하지 않은 URL 형식입니다")
        }

        if (!isAllowedScheme(normalizedUrl)) {
            return ValidationResult(false, "HTTP 또는 HTTPS URL만 허용됩니다")
        }

        if (containsSuspiciousPatterns(normalizedUrl)) {
            return ValidationResult(false, "안전하지 않은 URL입니다")
        }

        return ValidationResult(true, "유효한 URL입니다")
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()

        // Add protocol if missing
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }

        return normalized
    }

    private fun isValidUrlFormat(url: String): Boolean {
        return try {
            // 템플릿 변수가 포함된 URL인 경우 템플릿 처리 후 검증
            val testUrl = if (url.contains("\${")) {
                // 템플릿 변수를 임시 값으로 치환하여 검증
                url.replace("\${message}", "test_message")
                   .replace("\${msg}", "test_message")
                   .replace("\${sender}", "test_sender")
                   .replace("\${from}", "test_sender")
                   .replace("\${timestamp}", "1234567890")
                   .replace("\${time}", "1234567890")
            } else {
                url
            }

            URL(testUrl)
            Patterns.WEB_URL.matcher(testUrl).matches()
        } catch (e: MalformedURLException) {
            false
        }
    }

    private fun isAllowedScheme(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun containsSuspiciousPatterns(url: String): Boolean {
        val suspiciousPatterns = listOf(
            "javascript:",
            "data:",
            "file:",
            "ftp:",
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "10.",
            "192.168.",
            "172.16.",
            "172.17.",
            "172.18.",
            "172.19.",
            "172.20.",
            "172.21.",
            "172.22.",
            "172.23.",
            "172.24.",
            "172.25.",
            "172.26.",
            "172.27.",
            "172.28.",
            "172.29.",
            "172.30.",
            "172.31."
        )

        val lowerUrl = url.lowercase()
        return suspiciousPatterns.any { pattern ->
            lowerUrl.contains(pattern)
        }
    }

    fun getNormalizedUrl(url: String): String {
        return if (isValidUrl(url)) {
            normalizeUrl(url)
        } else {
            url
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}