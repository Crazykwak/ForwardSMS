package com.odinu.forwardsms.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.odinu.forwardsms.utils.UrlValidator

@Entity(tableName = "filters")
data class Filter(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val keyword: String,
    val phoneNumber: String = "", // 전화번호 필터
    val filterType: String = "KEYWORD", // KEYWORD, PHONE_NUMBER, BOTH
    val url: String,
    val method: String, // GET / POST
    val enabled: Boolean = true
) {
    fun isValid(): Boolean {
        return when (filterType.uppercase()) {
            "KEYWORD" -> keyword.isNotBlank()
            "PHONE_NUMBER" -> phoneNumber.isNotBlank()
            "BOTH" -> keyword.isNotBlank() && phoneNumber.isNotBlank()
            else -> false
        } && UrlValidator.isValidUrl(url) &&
           (method.uppercase() == "GET" || method.uppercase() == "POST")
    }

    fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()

        when (filterType.uppercase()) {
            "KEYWORD" -> {
                if (keyword.isBlank()) {
                    errors.add("키워드를 입력해주세요")
                }
            }
            "PHONE_NUMBER" -> {
                if (phoneNumber.isBlank()) {
                    errors.add("전화번호를 입력해주세요")
                }
            }
            "BOTH" -> {
                if (keyword.isBlank()) {
                    errors.add("키워드를 입력해주세요")
                }
                if (phoneNumber.isBlank()) {
                    errors.add("전화번호를 입력해주세요")
                }
            }
            else -> {
                errors.add("필터 타입이 올바르지 않습니다")
            }
        }

        val urlValidation = UrlValidator.validateUrl(url)
        if (!urlValidation.isValid) {
            errors.add(urlValidation.message)
        }

        if (method.uppercase() != "GET" && method.uppercase() != "POST") {
            errors.add("HTTP 메소드는 GET 또는 POST만 가능합니다")
        }

        return errors
    }

    fun getNormalizedUrl(): String {
        return UrlValidator.getNormalizedUrl(url)
    }
}