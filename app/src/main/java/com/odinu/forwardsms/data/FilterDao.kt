package com.odinu.forwardsms.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {
    @Query("SELECT * FROM filters")
    fun getAllFilters(): Flow<List<Filter>>

    @Query("SELECT * FROM filters WHERE enabled = 1")
    fun getEnabledFilters(): Flow<List<Filter>>

    @Query("SELECT * FROM filters WHERE id = :id")
    suspend fun getFilterById(id: Int): Filter?

    @Insert
    suspend fun insertFilter(filter: Filter)

    @Update
    suspend fun updateFilter(filter: Filter)

    @Delete
    suspend fun deleteFilter(filter: Filter)

    @Query("DELETE FROM filters WHERE id = :id")
    suspend fun deleteFilterById(id: Int)

    // History methods
    @Insert
    suspend fun insertHistory(history: FilterHistory)

    @Query("SELECT * FROM filter_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<FilterHistory>>

    @Query("SELECT * FROM filter_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryPaged(limit: Int, offset: Int): List<FilterHistory>

    @Query("SELECT * FROM filter_history WHERE filterId = :filterId ORDER BY timestamp DESC")
    fun getHistoryByFilterId(filterId: Int): Flow<List<FilterHistory>>

    @Query("SELECT * FROM filter_history WHERE filterId = :filterId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryByFilterIdPaged(filterId: Int, limit: Int, offset: Int): List<FilterHistory>

    @Query("DELETE FROM filter_history WHERE timestamp < :timestamp")
    suspend fun deleteOldHistory(timestamp: Long)

    @Query("SELECT COUNT(*) FROM filter_history")
    suspend fun getHistoryCount(): Int

    @Query("SELECT COUNT(*) FROM filter_history WHERE filterId = :filterId")
    suspend fun getHistoryCountByFilter(filterId: Int): Int

    @Query("DELETE FROM filter_history WHERE id IN (SELECT id FROM filter_history ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestHistory(count: Int)

    @Query("SELECT COUNT(*) FROM filter_history WHERE success = 1")
    suspend fun getSuccessfulHistoryCount(): Int

    @Query("SELECT COUNT(*) FROM filter_history WHERE success = 0")
    suspend fun getFailedHistoryCount(): Int

    @Query("DELETE FROM filter_history WHERE filterId = :filterId")
    suspend fun deleteHistoryByFilterId(filterId: Int)
}