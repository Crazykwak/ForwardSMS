package com.odinu.forwardsms.network

import android.os.Build
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private fun createUserAgent(): String {
        val appName = "ForwardSMS"
        val appVersion = "1.0.0"
        val androidVersion = Build.VERSION.RELEASE
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val sdkVersion = Build.VERSION.SDK_INT

        return "$appName/$appVersion (Android $androidVersion; $deviceModel; API $sdkVersion) okhttp/4.12.0"
    }

    private val userAgentInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestWithUserAgent = originalRequest.newBuilder()
            .header("User-Agent", createUserAgent())
            .build()
        chain.proceed(requestWithUserAgent)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 임시로 BODY 레벨 사용
    }

    private val retryInterceptor = RetryInterceptor(
        maxRetryCount = 3,
        retryDelayMs = 1000L
    )

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(retryInterceptor)  // 재시도 로직 추가
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)  // 더 짧은 타임아웃으로 빠른 재시도
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)     // 전체 요청 타임아웃 (재시도 포함)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}