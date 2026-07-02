package com.odinu.forwardsms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.odinu.forwardsms.utils.AppSettings
import com.odinu.forwardsms.utils.LogCollector
import com.odinu.forwardsms.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessageNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        LogCollector.i("NotificationRCS", "MessageNotificationListener 서비스 시작됨")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        LogCollector.i("NotificationRCS", "NotificationListener 연결됨")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        LogCollector.w("NotificationRCS", "NotificationListener 연결 끊김")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.notification == null) {
            return
        }

        if (!AppSettings.getInstance(this).isNotificationMonitoringEnabled) {
            return
        }

        try {
            val notification = sbn
            val packageName = notification.packageName
            val extras = notification.notification?.extras

            // 모든 알림을 로깅해서 LGU+ RCS 패키지명 찾기
            val title = extras?.getString("android.title")
            // BigTextStyle의 android.bigText가 전체 본문이고, android.text는 축약된 미리보기이므로
            // 긴 메시지(LMS)가 잘리지 않도록 bigText를 우선 사용
            val text = extras?.getString("android.bigText")
                ?: extras?.getString("android.text")

            if (packageName.contains("com.odinu.forwardsms")) {
                return;
            }

            LogCollector.i("NotificationRCS", "메시지 관련 알림 감지: $packageName")

            val subText = extras?.getString("android.subText")
            val infoText = extras?.getString("android.infoText")

            LogCollector.i("NotificationRCS", "알림 상세:")
            LogCollector.i("NotificationRCS", "  - Title: $title")
            LogCollector.i("NotificationRCS", "  - Text: $text")
            LogCollector.i("NotificationRCS", "  - SubText: $subText")
            LogCollector.i("NotificationRCS", "  - InfoText: $infoText")

            // 더 관대한 조건으로 메시지 감지
            if (!text.isNullOrEmpty() && text.trim().isNotEmpty()) {
                val sender = title ?: subText ?: infoText ?: "Unknown"

                LogCollector.i("NotificationRCS", "RCS/메시지 감지됨")
                SecureLogger.logSmsReceived("NotificationRCS", sender, text)

                try {
                    CoroutineScope(Dispatchers.IO).launch {
                        val repository = FilterRepository.getInstance(this@MessageNotificationListener)
                        repository.checkAndTriggerFilters(
                            text,
                            sender,
                            System.currentTimeMillis()
                        )
                    }
                } catch (e: Exception) {
                    LogCollector.e("NotificationRCS", "필터 처리 오류: ${e.message}")
                }
            }
        } catch (e: Exception) {
            LogCollector.e("NotificationRCS", "알림 처리 오류: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 필요시 구현
    }
}