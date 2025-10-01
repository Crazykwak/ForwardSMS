package com.odinu.forwardsms.performance

import android.content.Context
import android.util.Log
import com.odinu.forwardsms.FilterRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class MemoryManager private constructor(private val context: Context) {

    private val repository = FilterRepository.getInstance(context)
    private val cleanupScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() +
        CoroutineName("MemoryManager")
    )

    // 설정 가능한 제한값들
    private val maxHistoryEntries = 10000 // 최대 히스토리 개수
    private val maxHistoryAge = 30 * 24 * 60 * 60 * 1000L // 30일
    private val cleanupInterval = 6 * 60 * 60 * 1000L // 6시간마다 정리

    private val lastCleanupTime = AtomicLong(0)

    init {
        startPeriodicCleanup()
    }

    /**
     * 주기적 메모리 정리 시작
     */
    private fun startPeriodicCleanup() {
        cleanupScope.launch {
            while (isActive) {
                try {
                    delay(cleanupInterval)
                    performCleanup()
                } catch (e: Exception) {
                    Log.e("MemoryManager", "Error in periodic cleanup", e)
                }
            }
        }
    }

    /**
     * 메모리 정리 수행
     */
    suspend fun performCleanup() {
        val startTime = System.currentTimeMillis()
        lastCleanupTime.set(startTime)

        try {
            // 1. 오래된 히스토리 삭제 (30일 이상)
            val oldHistoryThreshold = startTime - maxHistoryAge
            repository.cleanOldHistory()
            Log.d("MemoryManager", "Cleaned old history older than 30 days")

            // 2. 히스토리 개수 제한 (최대 10,000개)
            val totalHistoryCount = repository.getHistoryCount()
            if (totalHistoryCount > maxHistoryEntries) {
                val excessCount = totalHistoryCount - maxHistoryEntries
                repository.deleteOldestHistory(excessCount)
                Log.d("MemoryManager", "Deleted $excessCount oldest history entries")
            }

            // 3. 통계 로깅
            val stats = getMemoryStats()
            Log.i("MemoryManager", "Cleanup completed: $stats")

        } catch (e: Exception) {
            Log.e("MemoryManager", "Error during cleanup", e)
        }
    }

    /**
     * 특정 필터의 히스토리 정리
     */
    suspend fun cleanupFilterHistory(filterId: Int, maxEntries: Int = 1000) {
        try {
            val filterHistoryCount = repository.getHistoryCountByFilter(filterId)
            if (filterHistoryCount > maxEntries) {
                // 필터별 히스토리도 제한 (현재 DAO에는 없음, 추후 구현 가능)
                Log.d("MemoryManager", "Filter $filterId has $filterHistoryCount entries")
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error cleaning filter history", e)
        }
    }

    /**
     * 강제 메모리 정리 (사용자 요청)
     */
    suspend fun forceCleanup(): CleanupResult {
        val beforeStats = getMemoryStats()
        performCleanup()
        val afterStats = getMemoryStats()

        return CleanupResult(
            beforeCount = beforeStats.totalHistoryCount,
            afterCount = afterStats.totalHistoryCount,
            deletedCount = beforeStats.totalHistoryCount - afterStats.totalHistoryCount,
            success = true
        )
    }

    /**
     * 메모리 사용 통계
     */
    suspend fun getMemoryStats(): MemoryStats {
        return try {
            MemoryStats(
                totalHistoryCount = repository.getHistoryCount(),
                successfulHistoryCount = repository.getSuccessfulHistoryCount(),
                failedHistoryCount = repository.getFailedHistoryCount(),
                lastCleanupTime = lastCleanupTime.get(),
                maxHistoryEntries = maxHistoryEntries,
                maxHistoryAge = maxHistoryAge
            )
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error getting memory stats", e)
            MemoryStats(0, 0, 0, 0, maxHistoryEntries, maxHistoryAge)
        }
    }

    /**
     * 자동 정리 필요 여부 확인
     */
    suspend fun needsCleanup(): Boolean {
        val stats = getMemoryStats()
        val timeSinceLastCleanup = System.currentTimeMillis() - stats.lastCleanupTime

        return stats.totalHistoryCount > maxHistoryEntries ||
               timeSinceLastCleanup > cleanupInterval
    }

    /**
     * 리소스 정리
     */
    fun shutdown() {
        cleanupScope.cancel()
    }

    data class MemoryStats(
        val totalHistoryCount: Int,
        val successfulHistoryCount: Int,
        val failedHistoryCount: Int,
        val lastCleanupTime: Long,
        val maxHistoryEntries: Int,
        val maxHistoryAge: Long
    ) {
        val memoryUsagePercentage: Float
            get() = if (maxHistoryEntries > 0) {
                (totalHistoryCount.toFloat() / maxHistoryEntries) * 100f
            } else 0f

        val isNearLimit: Boolean
            get() = memoryUsagePercentage > 80f
    }

    data class CleanupResult(
        val beforeCount: Int,
        val afterCount: Int,
        val deletedCount: Int,
        val success: Boolean
    )

    companion object {
        @Volatile
        private var INSTANCE: MemoryManager? = null

        fun getInstance(context: Context): MemoryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = MemoryManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}