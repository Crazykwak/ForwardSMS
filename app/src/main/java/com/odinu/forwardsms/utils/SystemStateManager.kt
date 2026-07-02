package com.odinu.forwardsms.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

class SystemStateManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * 배터리 최적화 상태 확인
     */
    fun isBatteryOptimizationIgnored(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Android 6.0 미만에서는 배터리 최적화 없음
        }
    }

    /**
     * 백그라운드 앱 새로고침 상태 확인 (제한적)
     */
    fun isBackgroundAppRefreshEnabled(): Boolean {
        return try {
            // Android의 백그라운드 앱 새로고침은 직접 확인하기 어려움
            // 대신 앱이 백그라운드에서 실행 중인지 확인
            !isAppInBackground()
        } catch (e: Exception) {
            true // 확인할 수 없는 경우 true로 가정
        }
    }

    /**
     * 앱이 백그라운드에 있는지 확인
     */
    fun isAppInBackground(): Boolean {
        val runningAppProcesses = activityManager.runningAppProcesses ?: return true

        for (processInfo in runningAppProcesses) {
            if (processInfo.processName == context.packageName) {
                return processInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
        return true
    }

    /**
     * 네트워크 연결 상태 확인
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                // NET_CAPABILITY_VALIDATED는 시스템의 자체 연결성 점검 URL 접근 성공 여부이며,
                // 방화벽/사내망/캡티브 포털 등에서 그 점검만 실패해도 웹훅 호출 자체는 정상 동작할 수 있으므로 제외
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * WiFi 연결 상태 확인
     */
    fun isWifiConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 모바일 데이터 연결 상태 확인
     */
    fun isMobileDataConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 자동 시작 관리자로 이동 (제조사별 대응)
     */
    fun openAutoStartSettings(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            manufacturer.contains("xiaomi") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            }
            manufacturer.contains("oppo") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            manufacturer.contains("vivo") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            }
            else -> null
        }
    }

    /**
     * 배터리 최적화 설정으로 이동
     */
    fun openBatteryOptimizationSettings(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * 시스템 상태 종합 진단
     */
    fun getSystemDiagnosis(): SystemDiagnosis {
        return SystemDiagnosis(
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            isNetworkAvailable = isNetworkAvailable(),
            isWifiConnected = isWifiConnected(),
            isMobileDataConnected = isMobileDataConnected(),
            isAppInBackground = isAppInBackground(),
            deviceManufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.SDK_INT,
            hasAutoStartSupport = openAutoStartSettings() != null
        )
    }

    data class SystemDiagnosis(
        val isBatteryOptimizationIgnored: Boolean,
        val isNetworkAvailable: Boolean,
        val isWifiConnected: Boolean,
        val isMobileDataConnected: Boolean,
        val isAppInBackground: Boolean,
        val deviceManufacturer: String,
        val androidVersion: Int,
        val hasAutoStartSupport: Boolean
    ) {
        val hasBackgroundIssues: Boolean
            get() = !isBatteryOptimizationIgnored

        val hasNetworkIssues: Boolean
            get() = !isNetworkAvailable

        val networkType: String
            get() = when {
                isWifiConnected -> "WiFi"
                isMobileDataConnected -> "모바일 데이터"
                else -> "연결 없음"
            }

        val overallStatus: String
            get() = when {
                hasBackgroundIssues && hasNetworkIssues -> "심각한 문제"
                hasBackgroundIssues || hasNetworkIssues -> "일부 문제"
                else -> "정상"
            }
    }
}