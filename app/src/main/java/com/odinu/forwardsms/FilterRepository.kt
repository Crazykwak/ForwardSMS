package com.odinu.forwardsms

import android.content.Context
import android.util.Log
import com.odinu.forwardsms.utils.SecureLogger
import com.odinu.forwardsms.data.Filter
import com.odinu.forwardsms.data.FilterDatabase
import com.odinu.forwardsms.data.FilterHistory
import com.odinu.forwardsms.network.RetrofitClient
import com.odinu.forwardsms.utils.UrlTemplateProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FilterRepository private constructor(private val context: Context) {
    private val filterDao = FilterDatabase.getDatabase(context).filterDao()
    private val apiService = RetrofitClient.apiService

    fun getAllFilters(): Flow<List<Filter>> = filterDao.getAllFilters()

    suspend fun insertFilter(filter: Filter) {
        filterDao.insertFilter(filter)
        notifyFilterChanged("insertFilter")
    }

    suspend fun updateFilter(filter: Filter) {
        filterDao.updateFilter(filter)
        notifyFilterChanged("updateFilter")
    }

    suspend fun deleteFilter(filter: Filter) {
        filterDao.deleteFilter(filter)
        notifyFilterChanged("deleteFilter")
    }

    suspend fun deleteFilterById(id: Int) {
        filterDao.deleteFilterById(id)
        notifyFilterChanged("deleteFilterById")
    }

    /**
     * 필터 변경 시 MessageProcessor에 알림
     */
    private fun notifyFilterChanged(operation: String) {
        try {
            Log.d("FilterRepository", "필터 변경 알림: $operation")
            // MessageProcessor는 Flow를 구독하고 있으므로 자동으로 감지됨
            // 별도 알림이 필요한 경우 여기에 추가 로직 구현 가능
        } catch (e: Exception) {
            Log.e("FilterRepository", "필터 변경 알림 실패: $operation", e)
        }
    }

    suspend fun getFilterById(id: Int): Filter? {
        return filterDao.getFilterById(id)
    }

    // History methods
    fun getAllHistory(): Flow<List<FilterHistory>> = filterDao.getAllHistory()

    fun getHistoryByFilterId(filterId: Int): Flow<List<FilterHistory>> =
        filterDao.getHistoryByFilterId(filterId)

    suspend fun insertHistory(history: FilterHistory) {
        filterDao.insertHistory(history)
    }

    suspend fun cleanOldHistory() {
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        filterDao.deleteOldHistory(oneWeekAgo)
    }

    // 새로운 히스토리 관리 메서드들
    suspend fun getHistoryPaged(limit: Int, offset: Int): List<FilterHistory> =
        filterDao.getHistoryPaged(limit, offset)

    suspend fun getHistoryByFilterIdPaged(filterId: Int, limit: Int, offset: Int): List<FilterHistory> =
        filterDao.getHistoryByFilterIdPaged(filterId, limit, offset)

    suspend fun getHistoryCount(): Int = filterDao.getHistoryCount()

    suspend fun getHistoryCountByFilter(filterId: Int): Int =
        filterDao.getHistoryCountByFilter(filterId)

    suspend fun deleteOldestHistory(count: Int) = filterDao.deleteOldestHistory(count)

    suspend fun getSuccessfulHistoryCount(): Int = filterDao.getSuccessfulHistoryCount()

    suspend fun getFailedHistoryCount(): Int = filterDao.getFailedHistoryCount()

    suspend fun deleteHistoryByFilterId(filterId: Int) = filterDao.deleteHistoryByFilterId(filterId)

    suspend fun checkAndTriggerFilters(messageBody: String, sender: String?, timestamp: Long) {
        // 최적화된 메시지 프로세서 사용
        val messageProcessor = com.odinu.forwardsms.performance.MessageProcessor.getInstance(context)
        messageProcessor.processMessage(messageBody, sender, timestamp)
    }

    // 레거시 동기 방식 (호환성을 위해 유지)
    suspend fun checkAndTriggerFiltersSync(messageBody: String, sender: String?, timestamp: Long) {
        try {
            val enabledFilters = filterDao.getEnabledFilters().first()

            for (filter in enabledFilters) {
                if (messageBody.contains(filter.keyword, ignoreCase = true)) {
                    SecureLogger.logFilterMatch("FilterRepository", filter.keyword, messageBody)

                    val finalUrl = if (filter.method.uppercase() == "GET") {
                        UrlTemplateProcessor.buildGetUrl(filter.url, messageBody, sender, timestamp)
                    } else {
                        filter.url
                    }

                    try {
                        val response = when (filter.method.uppercase()) {
                            "POST" -> {
                                apiService.sendMessagePost(
                                    url = filter.url,
                                    message = messageBody,
                                    sender = sender,
                                    timestamp = timestamp
                                )
                            }
                            "GET" -> {
                                SecureLogger.d("FilterRepository", "GET 요청 실행",
                                    mapOf(finalUrl to SecureLogger.SensitiveType.URL))
                                apiService.sendGetRequest(finalUrl)
                            }
                            else -> {
                                Log.w("FilterRepository", "Unknown method: ${filter.method}")
                                continue
                            }
                        }

                        val isSuccess = response.isSuccessful
                        val responseCode = response.code()

                        // Save history
                        val history = FilterHistory(
                            filterId = filter.id,
                            filterKeyword = filter.keyword,
                            smsMessage = messageBody,
                            sender = sender ?: "Unknown",
                            webhookUrl = finalUrl,
                            httpMethod = filter.method.uppercase(),
                            timestamp = timestamp,
                            success = isSuccess,
                            responseCode = responseCode,
                            errorMessage = if (!isSuccess) "HTTP $responseCode" else null
                        )
                        insertHistory(history)

                    } catch (e: Exception) {
                        val errorMsg = e.message ?: "알 수 없는 오류"
                        Log.e("FilterRepository", "Error calling URL ${filter.url}: $errorMsg")

                        // Save error history
                        val history = FilterHistory(
                            filterId = filter.id,
                            filterKeyword = filter.keyword,
                            smsMessage = messageBody,
                            sender = sender ?: "Unknown",
                            webhookUrl = finalUrl,
                            httpMethod = filter.method.uppercase(),
                            timestamp = timestamp,
                            success = false,
                            responseCode = null,
                            errorMessage = errorMsg
                        )
                        insertHistory(history)

                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FilterRepository", "Error checking filters: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: FilterRepository? = null

        fun getInstance(context: Context): FilterRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = FilterRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}