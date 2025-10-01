package com.odinu.forwardsms.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.odinu.forwardsms.utils.UrlValidator

@Entity(tableName = "filters")
data class Filter(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val keyword: String,
    val url: String,
    val method: String, // GET / POST
    val enabled: Boolean = true
) {
    fun isValid(): Boolean {
        return keyword.isNotBlank() &&
               UrlValidator.isValidUrl(url) &&
               (method.uppercase() == "GET" || method.uppercase() == "POST")
    }

    fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()

        if (keyword.isBlank()) {
            errors.add("키워드를 입력해주세요")
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