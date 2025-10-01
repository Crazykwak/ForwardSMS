package com.odinu.forwardsms.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RetryInterceptor(
    private val maxRetryCount: Int = 3,
    private val retryDelayMs: Long = 1000L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var exception: IOException? = null

        repeat(maxRetryCount + 1) { attempt ->
            try {
                exception = null
                response?.close() // Close previous response if any
                response = chain.proceed(request)

                // Success case - return response
                if (response!!.isSuccessful) {
                    Log.d("RetryInterceptor", "Request successful on attempt ${attempt + 1}")
                    return response!!
                }

                // HTTP error case - retry for server errors (5xx)
                if (response!!.code >= 500 && attempt < maxRetryCount) {
                    Log.w("RetryInterceptor", "Server error ${response!!.code} on attempt ${attempt + 1}, retrying...")
                    Thread.sleep(retryDelayMs * (attempt + 1)) // Exponential backoff
                }

                // Client error (4xx) - don't retry
                if (response!!.code >= 400) {
                    Log.w("RetryInterceptor", "Client error ${response!!.code}, not retrying")
                    return response!!
                }

            } catch (e: IOException) {
                exception = e
                Log.w("RetryInterceptor", "Network error on attempt ${attempt + 1}: ${e.message}")

                // Don't retry on the last attempt
                if (attempt < maxRetryCount) {
                    // Only retry on specific network errors
                    if (shouldRetry(e)) {
                        Log.i("RetryInterceptor", "Retrying after ${retryDelayMs * (attempt + 1)}ms...")
                        Thread.sleep(retryDelayMs * (attempt + 1)) // Exponential backoff
                    } else {
                        Log.w("RetryInterceptor", "Error not retryable: ${e.javaClass.simpleName}")
                        return response!!
                    }
                }
            }
        }

        // If we get here, all retries failed
        response?.let {
            Log.e("RetryInterceptor", "All retries failed, returning last response with code ${it.code}")
            return it
        }

        exception?.let {
            Log.e("RetryInterceptor", "All retries failed with exception: ${it.message}")
            throw it
        }

        throw IOException("Unknown error occurred during retry attempts")
    }

    private fun shouldRetry(exception: IOException): Boolean {
        return when (exception) {
            is SocketTimeoutException -> true    // Timeout - retry
            is UnknownHostException -> false     // DNS/Host error - don't retry
            is IOException -> {
                // Generic IO error - check message for specific cases
                val message = exception.message?.lowercase() ?: ""
                when {
                    message.contains("connection reset") -> true
                    message.contains("connection refused") -> false
                    message.contains("network unreachable") -> false
                    else -> true // Default to retry for unknown IO errors
                }
            }
            else -> false
        }
    }
}