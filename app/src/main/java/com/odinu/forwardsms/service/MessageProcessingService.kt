package com.odinu.forwardsms.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.odinu.forwardsms.MainActivity
import com.odinu.forwardsms.R
import com.odinu.forwardsms.performance.MessageProcessor
import com.odinu.forwardsms.performance.MemoryManager
import com.odinu.forwardsms.utils.SystemStateManager
import kotlinx.coroutines.*

class MessageProcessingService : Service() {

    companion object {
        const val CHANNEL_ID = "message_processing_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_PROCESSING = "start_processing"
        const val ACTION_STOP_PROCESSING = "stop_processing"
        const val ACTION_FORCE_CLEANUP = "force_cleanup"

        fun start(context: Context) {
            val intent = Intent(context, MessageProcessingService::class.java).apply {
                action = ACTION_START_PROCESSING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MessageProcessingService::class.java).apply {
                action = ACTION_STOP_PROCESSING
            }
            context.startService(intent)
        }
    }

    private lateinit var messageProcessor: MessageProcessor
    private lateinit var memoryManager: MemoryManager
    private lateinit var systemStateManager: SystemStateManager
    private lateinit var offlineQueueService: OfflineQueueService
    private var mmsContentObserver: MmsContentObserver? = null

    private val serviceScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() +
        CoroutineName("MessageProcessingService")
    )

    private var isServiceRunning = false
    private var statsUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        messageProcessor = MessageProcessor.getInstance(this)
        memoryManager = MemoryManager.getInstance(this)
        systemStateManager = SystemStateManager(this)
        offlineQueueService = OfflineQueueService.getInstance(this)

        createNotificationChannel()

        // WAP_PUSH_RECEIVED에는 MMS 본문이 없으므로, 다운로드 완료 후 저장되는
        // content://mms 를 감시해 실제 본문을 읽어온다
        mmsContentObserver = MmsContentObserver(this, Handler(Looper.getMainLooper()), serviceScope).also {
            contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, it)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROCESSING -> {
                startProcessing()
            }
            ACTION_STOP_PROCESSING -> {
                stopProcessing()
            }
            ACTION_FORCE_CLEANUP -> {
                forceCleanup()
            }
        }

        return START_STICKY // 시스템에 의해 종료되어도 재시작
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProcessing() {
        if (isServiceRunning) return

        isServiceRunning = true

        // Foreground 서비스로 시작
        val notification = createNotification("메시지 처리 서비스 시작 중...")
        startForeground(NOTIFICATION_ID, notification)

        // 주기적 통계 업데이트 시작
        startStatsUpdate()
    }

    private fun stopProcessing() {
        isServiceRunning = false
        statsUpdateJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun forceCleanup() {
        serviceScope.launch {
            try {
                updateNotification("시스템 정리 중...")

                // 메모리 정리 수행
                val cleanupResult = memoryManager.forceCleanup()

                // 오프라인 큐 처리
                val queueResult = offlineQueueService.forceProcessQueue()

                updateNotification(
                    "정리 완료 - 삭제: ${cleanupResult.deletedCount}개, 큐 처리: ${queueResult.processedCount}개"
                )

                // 5초 후 원래 상태로 복원
                delay(5000)
                updateStatsNotification()

            } catch (e: Exception) {
                updateNotification("정리 중 오류 발생: ${e.message}")
            }
        }
    }

    private fun startStatsUpdate() {
        statsUpdateJob = serviceScope.launch {
            while (isActive && isServiceRunning) {
                try {
                    updateStatsNotification()
                    delay(30000) // 30초마다 업데이트
                } catch (e: Exception) {
                    // 에러 무시하고 계속 진행
                    delay(60000) // 오류 시 1분 후 재시도
                }
            }
        }
    }

    private suspend fun updateStatsNotification() {
        try {
            val processingStats = messageProcessor.getProcessingStats()
            val memoryStats = memoryManager.getMemoryStats()
            val queueState = offlineQueueService.queueState.value
            val systemDiagnosis = systemStateManager.getSystemDiagnosis()

            val statusText = buildString {
                append("처리완료: ${processingStats.processedCount}개")

                if (processingStats.failedCount > 0) {
                    append(" | 실패: ${processingStats.failedCount}개")
                }

                if (queueState.hasQueuedItems) {
                    append(" | 대기: ${queueState.queueSize}개")
                }

                if (!systemDiagnosis.isNetworkAvailable) {
                    append(" | 오프라인")
                }

                if (systemDiagnosis.hasBackgroundIssues) {
                    append(" | 백그라운드 제한")
                }
            }

            updateNotification(statusText)

        } catch (e: Exception) {
            updateNotification("상태 업데이트 실패")
        }
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 정리 액션
        val cleanupIntent = Intent(this, MessageProcessingService::class.java).apply {
            action = ACTION_FORCE_CLEANUP
        }
        val cleanupPendingIntent = PendingIntent.getService(
            this, 1, cleanupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 중지 액션
        val stopIntent = Intent(this, MessageProcessingService::class.java).apply {
            action = ACTION_STOP_PROCESSING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ForwardSMS 백그라운드 서비스")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 적절한 아이콘으로 변경
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "정리",
                cleanupPendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "중지",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "메시지 처리 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS 메시지 처리 및 웹훅 호출을 백그라운드에서 수행합니다"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        statsUpdateJob?.cancel()
        serviceScope.cancel()

        mmsContentObserver?.let { contentResolver.unregisterContentObserver(it) }

        // 서비스들 정리
        messageProcessor.cleanup()
        memoryManager.shutdown()
        offlineQueueService.shutdown()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 앱이 최근 앱에서 제거되어도 서비스는 계속 실행
        // 필요시 서비스 재시작
        if (isServiceRunning) {
            val restartServiceIntent = Intent(applicationContext, MessageProcessingService::class.java).apply {
                action = ACTION_START_PROCESSING
            }
            applicationContext.startService(restartServiceIntent)
        }
    }
}