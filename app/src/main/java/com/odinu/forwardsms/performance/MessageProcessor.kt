package com.odinu.forwardsms.performance

import android.content.Context
import android.util.Log
import com.odinu.forwardsms.utils.SecureLogger
import com.odinu.forwardsms.FilterRepository
import com.odinu.forwardsms.data.Filter
import com.odinu.forwardsms.data.FilterHistory
import com.odinu.forwardsms.network.RetrofitClient
import com.odinu.forwardsms.utils.UrlTemplateProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MessageProcessor private constructor(private val context: Context) {

    private val repository = FilterRepository.getInstance(context)
    private val apiService = RetrofitClient.apiService
    private val filterMatcher = FilterMatcher()

    // 메시지 처리 큐
    private val messageQueue = Channel<MessageTask>(capacity = 1000)
    private val isProcessing = AtomicBoolean(false)
    private val processedCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)

    // 중복 메시지 방지를 위한 캐시 (메시지 해시 → 타임스탬프)
    private val recentMessageHashes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val DUPLICATE_WINDOW_MS = 5000L // 5초 내 중복 메시지 무시
    private val MAX_CACHE_SIZE = 1000 // 최대 캐시 크기

    // 처리 스코프
    private val processingScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() +
        CoroutineName("MessageProcessor")
    )

    init {
        startProcessing()
        startFilterIndexWatcher()
    }

    /**
     * 메시지 처리 요청 (비동기)
     */
    suspend fun processMessage(messageBody: String, sender: String?, timestamp: Long) {
        Log.d("MessageProcessor", "processMessage 호출: message='$messageBody', sender='$sender'")

        // 메시지 중복 확인 (발신자 포함 해시, 확인과 기록을 원자적으로 수행)
        val messageHash = generateMessageHash(messageBody, sender)
        val currentTime = System.currentTimeMillis()

        if (isDuplicateAndRecord(messageHash, currentTime)) {
            Log.d("MessageProcessor", "중복 메시지 감지됨, 무시함: hash=$messageHash")
            return
        }

        cleanupOldHashes(currentTime)

        val task = MessageTask(messageBody, sender, timestamp)

        // 큐가 가득 찬 경우 오래된 메시지 버리기
        if (!messageQueue.trySend(task).isSuccess) {
            Log.w("MessageProcessor", "Message queue is full, dropping oldest message")
            messageQueue.tryReceive() // 오래된 메시지 제거
            messageQueue.trySend(task)
        }
        Log.d("MessageProcessor", "메시지가 큐에 추가됨")
    }

    /**
     * 빠른 매칭 확인 (UI 블로킹 방지)
     */
    fun hasAnyFilterMatch(messageBody: String, sender: String? = null): Boolean {
        return filterMatcher.hasAnyMatch(messageBody, sender)
    }

    /**
     * 백그라운드 메시지 처리 시작
     */
    private fun startProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            processingScope.launch {
                while (isActive) {
                    try {
                        val task = messageQueue.receive()
                        processMessageTask(task)
                    } catch (e: Exception) {
                        Log.e("MessageProcessor", "Error in message processing loop", e)
                        failedCount.incrementAndGet()
                    }
                }
            }
        }
    }

    /**
     * 실제 메시지 처리 (백그라운드)
     */
    private suspend fun processMessageTask(task: MessageTask) {
        try {
            Log.d("MessageProcessor", "processMessageTask 시작: ${task.messageBody}")
            val matchedFilters = filterMatcher.findMatchingFilters(task.messageBody, task.sender)
            Log.d("MessageProcessor", "매칭된 필터 수: ${matchedFilters.size}")

            if (matchedFilters.isEmpty()) {
                Log.d("MessageProcessor", "매칭된 필터가 없음")
                return
            }

            Log.d("MessageProcessor", "Found ${matchedFilters.size} matching filters")

            // 병렬로 웹훅 호출 (최대 5개 동시 처리)
            val semaphore = kotlinx.coroutines.sync.Semaphore(5)

            coroutineScope {
                matchedFilters.map { filter ->
                    async {
                        semaphore.withPermit {
                            processFilterMatch(filter, task)
                        }
                    }
                }.awaitAll()
            }

            processedCount.incrementAndGet()

        } catch (e: Exception) {
            Log.e("MessageProcessor", "Error processing message task", e)
            failedCount.incrementAndGet()
        }
    }

    /**
     * 개별 필터 매칭 처리
     */
    private suspend fun processFilterMatch(filter: Filter, task: MessageTask) {
        try {
            val finalUrl = if (filter.method.uppercase() == "GET") {
                UrlTemplateProcessor.buildGetUrl(filter.url, task.messageBody, task.sender, task.timestamp)
            } else {
                filter.url
            }

            val response = when (filter.method.uppercase()) {
                "POST" -> {
                    apiService.sendMessagePost(
                        url = filter.url,
                        message = task.messageBody,
                        sender = task.sender,
                        timestamp = task.timestamp
                    )
                }
                "GET" -> {
                    apiService.sendGetRequest(finalUrl)
                }
                else -> {
                    Log.w("MessageProcessor", "Unknown method: ${filter.method}")
                    return
                }
            }

            val isSuccess = response.isSuccessful
            val responseCode = response.code()

            // 히스토리 저장
            val history = FilterHistory(
                filterId = filter.id,
                filterKeyword = filter.keyword,
                smsMessage = task.messageBody,
                sender = task.sender ?: "Unknown",
                webhookUrl = finalUrl,
                httpMethod = filter.method.uppercase(),
                timestamp = task.timestamp,
                success = isSuccess,
                responseCode = responseCode,
                errorMessage = if (!isSuccess) "HTTP $responseCode" else null
            )
            repository.insertHistory(history)

        } catch (e: Exception) {
            val errorMsg = e.message ?: "알 수 없는 오류"
            Log.e("MessageProcessor", "Error calling webhook for filter ${filter.id}: $errorMsg")

            // 오류 히스토리 저장
            val history = FilterHistory(
                filterId = filter.id,
                filterKeyword = filter.keyword,
                smsMessage = task.messageBody,
                sender = task.sender ?: "Unknown",
                webhookUrl = filter.url,
                httpMethod = filter.method.uppercase(),
                timestamp = task.timestamp,
                success = false,
                responseCode = null,
                errorMessage = errorMsg
            )
            repository.insertHistory(history)
        }
    }

    /**
     * 필터 변경사항을 실시간으로 감지하고 자동으로 인덱스 갱신
     */
    private fun startFilterIndexWatcher() {
        processingScope.launch {
            try {
                Log.d("MessageProcessor", "필터 인덱스 감시 시작")
                repository.getAllFilters().collect { filters ->
                    Log.d("MessageProcessor", "필터 변경 감지됨 - 필터 수: ${filters.size}")
                    filters.forEach { filter ->
                        Log.d("MessageProcessor", "필터: keyword='${filter.keyword}', enabled=${filter.enabled}, url='${filter.url}'")
                    }
                    filterMatcher.buildIndex(filters)
                    Log.d("MessageProcessor", "필터 인덱스 자동 갱신 완료: ${filterMatcher.getIndexInfo()}")
                }
            } catch (e: Exception) {
                Log.e("MessageProcessor", "필터 인덱스 감시 중 오류", e)
                // 오류 발생 시 기본 인덱스 업데이트 시도
                updateFilterIndexFallback()
            }
        }
    }

    /**
     * 필터 인덱스 업데이트 (수동 호출용)
     */
    fun updateFilterIndex() {
        processingScope.launch {
            updateFilterIndexFallback()
        }
    }

    /**
     * 필터 인덱스 업데이트 (백업 방식)
     */
    private suspend fun updateFilterIndexFallback() {
        try {
            Log.d("MessageProcessor", "수동 필터 인덱스 업데이트 시작")
            val filters = repository.getAllFilters().first()
            Log.d("MessageProcessor", "데이터베이스에서 읽어온 필터 수: ${filters.size}")
            filters.forEach { filter ->
                Log.d("MessageProcessor", "필터: keyword='${filter.keyword}', enabled=${filter.enabled}, url='${filter.url}'")
            }
            filterMatcher.buildIndex(filters)
            Log.d("MessageProcessor", "수동 필터 인덱스 업데이트 완료: ${filterMatcher.getIndexInfo()}")
        } catch (e: Exception) {
            Log.e("MessageProcessor", "수동 필터 인덱스 업데이트 실패", e)
        }
    }

    /**
     * 처리 통계
     */
    fun getProcessingStats(): ProcessingStats {
        return ProcessingStats(
            processedCount = processedCount.get(),
            failedCount = failedCount.get(),
            queueSize = messageQueue.tryReceive().let {
                if (it.isSuccess) {
                    messageQueue.trySend(it.getOrNull()!!)
                    1
                } else 0
            },
            indexInfo = filterMatcher.getIndexInfo()
        )
    }

    /**
     * 메시지 해시 생성 (메시지 본문 기반)
     */
    private fun generateMessageHash(messageBody: String, sender: String?): String {
        // 메시지 내용 정규화 (공백, 줄바꿈 제거) + 발신자를 포함해 동일 내용이라도
        // 발신자가 다르면 별개 메시지로 취급한다
        val normalizedMessage = messageBody.replace(Regex("\\s+"), " ").trim()
        val normalizedSender = sender?.trim().orEmpty()
        return "$normalizedSender:$normalizedMessage".hashCode().toString()
    }

    /**
     * 중복 메시지 확인과 기록을 하나의 원자적 연산으로 수행한다.
     * ConcurrentHashMap.compute는 동일 키에 대해 락을 걸어 처리하므로,
     * 동시에 같은 메시지가 들어와도 확인-기록 사이에 다른 스레드가 끼어들 수 없다.
     */
    private fun isDuplicateAndRecord(messageHash: String, currentTime: Long): Boolean {
        var duplicate = false
        recentMessageHashes.compute(messageHash) { _, lastSeenTime ->
            if (lastSeenTime != null && (currentTime - lastSeenTime) < DUPLICATE_WINDOW_MS) {
                duplicate = true
                lastSeenTime
            } else {
                currentTime
            }
        }
        return duplicate
    }

    /**
     * 오래된 해시 정리 (메모리 절약)
     */
    private fun cleanupOldHashes(currentTime: Long) {
        // 1. 시간 기반 정리
        val iterator = recentMessageHashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((currentTime - entry.value) > DUPLICATE_WINDOW_MS * 2) {
                iterator.remove()
            }
        }

        // 2. 크기 기반 정리 (최대 크기 초과 시)
        if (recentMessageHashes.size > MAX_CACHE_SIZE) {
            Log.w("MessageProcessor", "캐시 크기 초과 (${recentMessageHashes.size}), 오래된 항목 정리")

            // 가장 오래된 항목들 제거
            val sortedEntries = recentMessageHashes.entries.sortedBy { it.value }
            val removeCount = recentMessageHashes.size - (MAX_CACHE_SIZE * 0.8).toInt() // 80%까지 줄임

            repeat(removeCount) {
                if (sortedEntries.isNotEmpty()) {
                    recentMessageHashes.remove(sortedEntries[it].key)
                }
            }

            Log.d("MessageProcessor", "캐시 정리 완료: ${removeCount}개 제거, 현재 크기: ${recentMessageHashes.size}")
        }
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        isProcessing.set(false)
        processingScope.cancel()
        messageQueue.close()
        recentMessageHashes.clear()
    }

    data class MessageTask(
        val messageBody: String,
        val sender: String?,
        val timestamp: Long
    )

    data class ProcessingStats(
        val processedCount: Int,
        val failedCount: Int,
        val queueSize: Int,
        val indexInfo: FilterMatcher.IndexInfo
    )

    companion object {
        @Volatile
        private var INSTANCE: MessageProcessor? = null

        fun getInstance(context: Context): MessageProcessor {
            return INSTANCE ?: synchronized(this) {
                val instance = MessageProcessor(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}