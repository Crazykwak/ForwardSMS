package com.odinu.forwardsms.ui

import android.content.Context
import com.odinu.forwardsms.R
import com.odinu.forwardsms.utils.UrlTemplateProcessor
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorHandler {
    fun getErrorMessage(context: Context, throwable: Throwable): String {
        return when (throwable) {
            is ConnectException, is UnknownHostException -> {
                context.getString(R.string.error_network)
            }
            is SocketTimeoutException -> {
                "네트워크 연결 시간이 초과되었습니다"
            }
            is IOException -> {
                "네트워크 연결에 문제가 발생했습니다"
            }
            is IllegalArgumentException -> {
                "잘못된 입력값입니다: ${throwable.message}"
            }
            else -> {
                "알 수 없는 오류가 발생했습니다: ${throwable.message}"
            }
        }
    }

    fun validateFilter(context: Context, keyword: String, url: String, method: String): String? {
        if (keyword.isBlank() || url.isBlank() || method.isBlank()) {
            return context.getString(R.string.error_empty_fields)
        }

        if (!UrlTemplateProcessor.isValidTemplate(url)) {
            return context.getString(R.string.error_invalid_url)
        }

        if (method.uppercase() !in listOf("GET", "POST")) {
            return context.getString(R.string.error_invalid_method)
        }

        return null
    }

    private fun isValidUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false
        }

        return try {
            java.net.URL(url)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getAddFilterErrorMessage(context: Context, throwable: Throwable): String {
        val baseError = getErrorMessage(context, throwable)
        return context.getString(R.string.error_add_filter, baseError)
    }

    fun getUpdateFilterErrorMessage(context: Context, throwable: Throwable): String {
        val baseError = getErrorMessage(context, throwable)
        return context.getString(R.string.error_update_filter, baseError)
    }

    fun getDeleteFilterErrorMessage(context: Context, throwable: Throwable): String {
        val baseError = getErrorMessage(context, throwable)
        return context.getString(R.string.error_delete_filter, baseError)
    }
}