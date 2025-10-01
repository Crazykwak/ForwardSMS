package com.odinu.forwardsms.service

import android.content.Context
import android.util.Log
import com.odinu.forwardsms.FilterRepository
import com.odinu.forwardsms.data.FilterHistory
import com.odinu.forwardsms.network.RetrofitClient
import com.odinu.forwardsms.utils.SystemStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OfflineQueueService private constructor(private val context: Context) {

    private val repository = FilterRepository.getInstance(context)
    private val apiService = RetrofitClient.apiService
    private val systemStateManager = SystemStateManager(context)

    // 오프라인 큐
    private val offlineQueue = ConcurrentLinkedQueue<QueuedRequest>()
    private val isProcessingQueue = AtomicBoolean(false)
    private val retryCount = AtomicInteger(0)

    // 상태 관리
    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    // 처리 스코프
    private val processingScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() +
        CoroutineName("OfflineQueueService")
    )

    init {
        startNetworkMonitoring()
    }

    /**
     * 네트워크 상태 모니터링 시작
     */
    private fun startNetworkMonitoring() {
        processingScope.launch {
            while (isActive) {
                try {
                    val isNetworkAvailable = systemStateManager.isNetworkAvailable()

                    updateQueueState { currentState ->
                        currentState.copy(isNetworkAvailable = isNetworkAvailable)
                    }

                    if (isNetworkAvailable && offlineQueue.isNotEmpty()) {
                        processOfflineQueue()
                    }

                    delay(10000) // 10초마다 네트워크 상태 확인
                } catch (e: Exception) {
                    Log.e("OfflineQueueService", "Error in network monitoring", e)
                    delay(30000) // 오류 시 30초 후 재시도
                }
            }
        }
    }

    /**
     * 네트워크 요청 실행 또는 큐에 추가
     */
    suspend fun executeOrQueue(request: QueuedRequest): Boolean {
        return if (systemStateManager.isNetworkAvailable()) {
            try {
                executeRequest(request)
                true
            } catch (e: Exception) {
                Log.w("OfflineQueueService", "Request failed, adding to queue: ${e.message}")
                addToQueue(request)
                false
            }
        } else {
            addToQueue(request)
            false
        }
    }

    /**
     * 오프라인 큐에 요청 추가
     */
    private fun addToQueue(request: QueuedRequest) {
        // 큐 크기 제한 (최대 1000개)
        if (offlineQueue.size >= 1000) {
            offlineQueue.poll() // 가장 오래된 요청 제거
            Log.w("OfflineQueueService", "Queue is full, removing oldest request")
        }

        offlineQueue.offer(request)

        updateQueueState { currentState ->
            currentState.copy(
                queueSize = offlineQueue.size,
                lastQueuedTime = System.currentTimeMillis()
            )
        }

        Log.d("OfflineQueueService", "Added request to offline queue. Queue size: ${offlineQueue.size}")
    }

    /**
     * 오프라인 큐 처리
     */
    private suspend fun processOfflineQueue() {
        if (!isProcessingQueue.compareAndSet(false, true)) {
            return // 이미 처리 중
        }

        try {
            Log.d("OfflineQueueService", "Processing offline queue with ${offlineQueue.size} items")

            val processedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)

            // 동시에 최대 3개씩 처리
            val semaphore = kotlinx.coroutines.sync.Semaphore(3)

            val jobs = mutableListOf<Deferred<Unit>>()

            // 큐에서 요청을 하나씩 꺼내서 처리
            while (offlineQueue.isNotEmpty() && jobs.size < 50) { // 한 번에 최대 50개
                val request = offlineQueue.poll() ?: break

                val job = processingScope.async {
                    semaphore.withPermit {
                        try {
                            executeRequest(request)
                            processedCount.incrementAndGet()
                            Log.d("OfflineQueueService", "Successfully processed queued request")
                        } catch (e: Exception) {
                            failedCount.incrementAndGet()
                            Log.e("OfflineQueueService", "Failed to process queued request: ${e.message}")

                            // 재시도 횟수가 남아있으면 다시 큐에 추가
                            if (request.retryCount < 3) {
                                offlineQueue.offer(request.copy(retryCount = request.retryCount + 1))
                            } else {
                                Log.w("OfflineQueueService", "Max retry count reached for request, discarding")
                            }
                        }
                    }
                    Unit // 명시적으로 Unit 반환
                }
                jobs.add(job)
            }

            // 모든 작업 완료 대기
            jobs.awaitAll()

            updateQueueState { currentState ->
                currentState.copy(
                    queueSize = offlineQueue.size,
                    lastProcessedTime = System.currentTimeMillis(),
                    totalProcessed = currentState.totalProcessed + processedCount.get(),
                    totalFailed = currentState.totalFailed + failedCount.get()
                )
            }

            Log.d("OfflineQueueService", "Queue processing completed. Processed: ${processedCount.get()}, Failed: ${failedCount.get()}, Remaining: ${offlineQueue.size}")

        } finally {
            isProcessingQueue.set(false)
        }
    }

    /**
     * 실제 네트워크 요청 실행
     */
    private suspend fun executeRequest(request: QueuedRequest) {
        val response = when (request.method.uppercase()) {
            "POST" -> {
                apiService.sendMessagePost(
                    url = request.url,
                    message = request.messageBody,
                    sender = request.sender,
                    timestamp = request.timestamp
                )
            }
            "GET" -> {
                apiService.sendGetRequest(request.url)
            }
            else -> {
                throw IllegalArgumentException("Unsupported method: ${request.method}")
            }
        }

        val isSuccess = response.isSuccessful
        val responseCode = response.code()

        // 히스토리 저장
        val history = FilterHistory(
            filterId = request.filterId,
            filterKeyword = request.filterKeyword,
            smsMessage = request.messageBody,
            sender = request.sender ?: "Unknown",
            webhookUrl = request.url,
            httpMethod = request.method.uppercase(),
            timestamp = request.timestamp,
            success = isSuccess,
            responseCode = responseCode,
            errorMessage = if (!isSuccess) "HTTP $responseCode (Queued)" else null
        )
        repository.insertHistory(history)

        if (!isSuccess) {
            throw Exception("HTTP $responseCode")
        }
    }

    /**
     * 큐 상태 업데이트
     */
    private fun updateQueueState(update: (QueueState) -> QueueState) {
        _queueState.value = update(_queueState.value)
    }

    /**
     * 강제 큐 처리
     */
    suspend fun forceProcessQueue(): ProcessResult {
        val beforeSize = offlineQueue.size
        processOfflineQueue()
        val afterSize = offlineQueue.size

        return ProcessResult(
            beforeCount = beforeSize,
            afterCount = afterSize,
            processedCount = beforeSize - afterSize
        )
    }

    /**
     * 큐 초기화
     */
    fun clearQueue() {
        offlineQueue.clear()
        updateQueueState { currentState ->
            currentState.copy(queueSize = 0)
        }
        Log.d("OfflineQueueService", "Queue cleared")
    }

    /**
     * 서비스 종료
     */
    fun shutdown() {
        processingScope.cancel()
    }

    data class QueuedRequest(
        val filterId: Int,
        val filterKeyword: String,
        val url: String,
        val method: String,
        val messageBody: String,
        val sender: String?,
        val timestamp: Long,
        val retryCount: Int = 0
    )

    data class QueueState(
        val queueSize: Int = 0,
        val isNetworkAvailable: Boolean = false,
        val lastQueuedTime: Long = 0,
        val lastProcessedTime: Long = 0,
        val totalProcessed: Int = 0,
        val totalFailed: Int = 0
    ) {
        val hasQueuedItems: Boolean get() = queueSize > 0
        val isProcessingAvailable: Boolean get() = isNetworkAvailable && hasQueuedItems
    }

    data class ProcessResult(
        val beforeCount: Int,
        val afterCount: Int,
        val processedCount: Int
    )

    companion object {
        @Volatile
        private var INSTANCE: OfflineQueueService? = null

        fun getInstance(context: Context): OfflineQueueService {
            return INSTANCE ?: synchronized(this) {
                val instance = OfflineQueueService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}