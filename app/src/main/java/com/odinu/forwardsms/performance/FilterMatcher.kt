package com.odinu.forwardsms.performance

import com.odinu.forwardsms.data.Filter
import java.util.concurrent.ConcurrentHashMap

class FilterMatcher {

    // 키워드별로 인덱싱된 필터 맵 (키워드 → 필터 리스트)
    private val keywordIndex = ConcurrentHashMap<String, MutableList<Filter>>()

    // 전체 키워드 리스트 (정렬된 상태로 유지)
    private val sortedKeywords = mutableListOf<String>()

    /**
     * 필터 리스트를 최적화된 인덱스로 빌드
     */
    fun buildIndex(filters: List<Filter>) {
        keywordIndex.clear()
        sortedKeywords.clear()

        // 활성화된 필터만 처리
        val enabledFilters = filters.filter { it.enabled }

        // 키워드별로 인덱싱
        for (filter in enabledFilters) {
            val normalizedKeyword = filter.keyword.lowercase().trim()

            keywordIndex.getOrPut(normalizedKeyword) {
                mutableListOf()
            }.add(filter)

            if (normalizedKeyword !in sortedKeywords) {
                sortedKeywords.add(normalizedKeyword)
            }
        }

        // 키워드를 길이 순으로 정렬 (긴 키워드부터 매칭 - 더 구체적인 매칭 우선)
        sortedKeywords.sortByDescending { it.length }
    }

    /**
     * 메시지에서 매칭되는 필터들을 빠르게 찾기
     * O(n) → O(k*m) where k=키워드 수, m=평균 키워드 길이
     */
    fun findMatchingFilters(messageBody: String): List<Filter> {
        if (keywordIndex.isEmpty()) return emptyList()

        val normalizedMessage = messageBody.lowercase()
        val matchedFilters = mutableListOf<Filter>()
        val usedFilters = mutableSetOf<Int>() // 중복 방지

        // 정렬된 키워드 순서로 검사 (긴 키워드부터)
        for (keyword in sortedKeywords) {
            if (normalizedMessage.contains(keyword)) {
                keywordIndex[keyword]?.forEach { filter ->
                    if (filter.id !in usedFilters) {
                        matchedFilters.add(filter)
                        usedFilters.add(filter.id)
                    }
                }
            }
        }

        return matchedFilters
    }

    /**
     * 단일 키워드 매칭 (더 빠른 검사)
     */
    fun hasAnyMatch(messageBody: String): Boolean {
        if (keywordIndex.isEmpty()) return false

        val normalizedMessage = messageBody.lowercase()
        return sortedKeywords.any { normalizedMessage.contains(it) }
    }

    /**
     * 인덱스 상태 정보
     */
    fun getIndexInfo(): IndexInfo {
        return IndexInfo(
            totalKeywords = sortedKeywords.size,
            totalFilters = keywordIndex.values.sumOf { it.size },
            averageFiltersPerKeyword = if (sortedKeywords.isNotEmpty()) {
                keywordIndex.values.map { it.size }.average()
            } else 0.0
        )
    }

    data class IndexInfo(
        val totalKeywords: Int,
        val totalFilters: Int,
        val averageFiltersPerKeyword: Double
    )
}