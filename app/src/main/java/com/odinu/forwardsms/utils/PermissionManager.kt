package com.odinu.forwardsms.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 1001
        const val REQUEST_CODE_NOTIFICATION_LISTENER = 1002
        const val REQUEST_CODE_BATTERY_OPTIMIZATION = 1003

        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.INTERNET
        ).let { basicPermissions ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                basicPermissions + Manifest.permission.POST_NOTIFICATIONS
            } else {
                basicPermissions
            }
        }
    }

    /**
     * 모든 필수 권한이 허용되었는지 확인
     */
    fun areAllPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 거부된 권한 목록 반환
     */
    fun getDeniedPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 권한 요청이 필요한지 확인
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return REQUIRED_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    /**
     * 알림 리스너 권한 확인
     */
    fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = context.packageName
        return enabledListeners?.contains(packageName) == true
    }

    /**
     * 권한 상태 정보 반환
     */
    fun getPermissionStatus(): PermissionStatus {
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()

        REQUIRED_PERMISSIONS.forEach { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                granted.add(permission)
            } else {
                denied.add(permission)
            }
        }

        return PermissionStatus(
            grantedPermissions = granted,
            deniedPermissions = denied,
            isNotificationListenerEnabled = isNotificationListenerEnabled(),
            allRequired = areAllPermissionsGranted() && isNotificationListenerEnabled()
        )
    }

    /**
     * 권한별 사용자 친화적 이름 반환
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECEIVE_SMS -> "SMS 수신"
            Manifest.permission.READ_SMS -> "SMS 읽기"
            Manifest.permission.RECEIVE_MMS -> "MMS 수신"
            Manifest.permission.INTERNET -> "인터넷 연결"
            Manifest.permission.POST_NOTIFICATIONS -> "알림 표시"
            else -> permission.substringAfterLast(".")
        }
    }

    /**
     * 권한별 설명 반환
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.RECEIVE_SMS -> "SMS 메시지 수신을 감지하기 위해 필요합니다"
            Manifest.permission.READ_SMS -> "SMS 메시지 내용을 읽기 위해 필요합니다"
            Manifest.permission.RECEIVE_MMS -> "MMS 메시지 수신을 감지하기 위해 필요합니다"
            Manifest.permission.INTERNET -> "웹훅 URL로 메시지를 전송하기 위해 필요합니다"
            Manifest.permission.POST_NOTIFICATIONS -> "처리 결과를 알림으로 표시하기 위해 필요합니다"
            else -> "앱 정상 동작을 위해 필요한 권한입니다"
        }
    }

    /**
     * 설정 화면으로 이동하는 인텐트 생성
     */
    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 알림 리스너 설정 화면으로 이동하는 인텐트 생성
     */
    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 배터리 최적화 설정 화면으로 이동하는 인텐트 생성
     */
    fun createBatteryOptimizationIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * 권한 문제 해결 단계 반환
     */
    fun getTroubleshootingSteps(): List<TroubleshootingStep> {
        val steps = mutableListOf<TroubleshootingStep>()
        val status = getPermissionStatus()

        // 1. 기본 권한 확인
        if (status.deniedPermissions.isNotEmpty()) {
            steps.add(
                TroubleshootingStep(
                    title = "앱 권한 허용",
                    description = "설정 > 앱 > ForwardSMS > 권한에서 다음 권한들을 허용해주세요:\n" +
                            status.deniedPermissions.joinToString("\n") { "• ${getPermissionDisplayName(it)}" },
                    action = TroubleshootingAction.OPEN_APP_SETTINGS,
                    priority = TroubleshootingPriority.HIGH
                )
            )
        }

        // 2. 알림 리스너 권한
        if (!status.isNotificationListenerEnabled) {
            steps.add(
                TroubleshootingStep(
                    title = "알림 액세스 허용",
                    description = "RCS 메시지 감지를 위해 알림 액세스 권한을 허용해주세요.",
                    action = TroubleshootingAction.OPEN_NOTIFICATION_LISTENER,
                    priority = TroubleshootingPriority.HIGH
                )
            )
        }

        // 3. 배터리 최적화
        steps.add(
            TroubleshootingStep(
                title = "배터리 최적화 해제",
                description = "백그라운드에서 정상 동작을 위해 배터리 최적화를 해제해주세요.",
                action = TroubleshootingAction.OPEN_BATTERY_OPTIMIZATION,
                priority = TroubleshootingPriority.MEDIUM
            )
        )

        // 4. 자동 시작 (제조사별)
        val systemState = SystemStateManager(context)
        if (systemState.openAutoStartSettings() != null) {
            steps.add(
                TroubleshootingStep(
                    title = "자동 시작 허용",
                    description = "제조사 설정에서 앱의 자동 시작을 허용해주세요.",
                    action = TroubleshootingAction.OPEN_AUTO_START,
                    priority = TroubleshootingPriority.MEDIUM
                )
            )
        }

        return steps
    }

    data class PermissionStatus(
        val grantedPermissions: List<String>,
        val deniedPermissions: List<String>,
        val isNotificationListenerEnabled: Boolean,
        val allRequired: Boolean
    ) {
        val grantedCount: Int get() = grantedPermissions.size
        val totalCount: Int get() = grantedPermissions.size + deniedPermissions.size
        val progressPercentage: Float get() = if (totalCount > 0) (grantedCount.toFloat() / totalCount) * 100f else 0f
    }

    data class TroubleshootingStep(
        val title: String,
        val description: String,
        val action: TroubleshootingAction,
        val priority: TroubleshootingPriority
    )

    enum class TroubleshootingAction {
        OPEN_APP_SETTINGS,
        OPEN_NOTIFICATION_LISTENER,
        OPEN_BATTERY_OPTIMIZATION,
        OPEN_AUTO_START,
        CONTACT_SUPPORT
    }

    enum class TroubleshootingPriority {
        HIGH, MEDIUM, LOW
    }
}