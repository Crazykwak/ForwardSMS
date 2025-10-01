package com.odinu.forwardsms.network

data class NetworkConfig(
    val maxRetryCount: Int = 3,
    val retryDelayMs: Long = 1000L,
    val connectTimeoutSeconds: Long = 15L,
    val readTimeoutSeconds: Long = 30L,
    val writeTimeoutSeconds: Long = 15L,
    val callTimeoutSeconds: Long = 60L
) {
    companion object {
        val DEFAULT = NetworkConfig()

        val FAST = NetworkConfig(
            maxRetryCount = 2,
            retryDelayMs = 500L,
            connectTimeoutSeconds = 10L,
            readTimeoutSeconds = 20L,
            writeTimeoutSeconds = 10L,
            callTimeoutSeconds = 40L
        )

        val ROBUST = NetworkConfig(
            maxRetryCount = 5,
            retryDelayMs = 2000L,
            connectTimeoutSeconds = 30L,
            readTimeoutSeconds = 60L,
            writeTimeoutSeconds = 30L,
            callTimeoutSeconds = 120L
        )
    }
}