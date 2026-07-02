package com.odinu.forwardsms.performance

import com.odinu.forwardsms.data.Filter
import java.util.concurrent.ConcurrentHashMap

class FilterMatcher {

    // 키워드별로 인덱싱된 필터 맵 (키워드 → 필터 리스트)
    private val keywordIndex = ConcurrentHashMap<String, MutableList<Filter>>()

    // 전화번호별로 인덱싱된 필터 맵 (전화번호 → 필터 리스트)
    private val phoneNumberIndex = ConcurrentHashMap<String, MutableList<Filter>>()

    // 전체 키워드 리스트 (정렬된 상태로 유지)
    private val sortedKeywords = mutableListOf<String>()

    // 전체 전화번호 리스트
    private val sortedPhoneNumbers = mutableListOf<String>()

    /**
     * 필터 리스트를 최적화된 인덱스로 빌드
     */
    fun buildIndex(filters: List<Filter>) {
        keywordIndex.clear()
        phoneNumberIndex.clear()
        sortedKeywords.clear()
        sortedPhoneNumbers.clear()

        // 활성화된 필터만 처리
        val enabledFilters = filters.filter { it.enabled }

        // 필터 타입별로 인덱싱
        for (filter in enabledFilters) {
            when (filter.filterType.uppercase()) {
                "KEYWORD" -> {
                    // 키워드 필터만
                    if (filter.keyword.isNotBlank()) {
                        val normalizedKeyword = filter.keyword.lowercase().trim()
                        keywordIndex.getOrPut(normalizedKeyword) {
                            mutableListOf()
                        }.add(filter)
                        if (normalizedKeyword !in sortedKeywords) {
                            sortedKeywords.add(normalizedKeyword)
                        }
                    }
                }
                "PHONE_NUMBER" -> {
                    // 전화번호 필터만
                    if (filter.phoneNumber.isNotBlank()) {
                        val normalizedPhone = normalizePhoneNumber(filter.phoneNumber)
                        phoneNumberIndex.getOrPut(normalizedPhone) {
                            mutableListOf()
                        }.add(filter)
                        if (normalizedPhone !in sortedPhoneNumbers) {
                            sortedPhoneNumbers.add(normalizedPhone)
                        }
                    }
                }
                "BOTH" -> {
                    // 키워드 + 전화번호 조합 필터
                    if (filter.keyword.isNotBlank()) {
                        val normalizedKeyword = filter.keyword.lowercase().trim()
                        keywordIndex.getOrPut(normalizedKeyword) {
                            mutableListOf()
                        }.add(filter)
                        if (normalizedKeyword !in sortedKeywords) {
                            sortedKeywords.add(normalizedKeyword)
                        }
                    }
                }
            }
        }

        // 키워드를 길이 순으로 정렬 (긴 키워드부터 매칭 - 더 구체적인 매칭 우선)
        sortedKeywords.sortByDescending { it.length }
    }

    /**
     * 메시지에서 매칭되는 필터들을 빠르게 찾기
     * O(n) → O(k*m) where k=키워드 수, m=평균 키워드 길이
     */
    fun findMatchingFilters(messageBody: String, sender: String? = null): List<Filter> {
        if (keywordIndex.isEmpty() && phoneNumberIndex.isEmpty()) return emptyList()

        val normalizedMessage = messageBody.lowercase()
        val normalizedSender = sender?.let { normalizePhoneNumber(it) }
        val matchedFilters = mutableListOf<Filter>()
        val usedFilters = mutableSetOf<Int>() // 중복 방지

        // 1. 키워드 기반 필터 매칭
        for (keyword in sortedKeywords) {
            if (normalizedMessage.contains(keyword)) {
                keywordIndex[keyword]?.forEach { filter ->
                    if (filter.id !in usedFilters) {
                        // BOTH 타입인 경우 전화번호도 확인
                        if (filter.filterType.uppercase() == "BOTH") {
                            if (normalizedSender != null && filter.phoneNumber.isNotBlank()) {
                                val normalizedFilterPhone = normalizePhoneNumber(filter.phoneNumber)
                                if (normalizedSender.contains(normalizedFilterPhone) ||
                                    normalizedFilterPhone.contains(normalizedSender)) {
                                    matchedFilters.add(filter)
                                    usedFilters.add(filter.id)
                                }
                            }
                        } else {
                            // KEYWORD 타입
                            matchedFilters.add(filter)
                            usedFilters.add(filter.id)
                        }
                    }
                }
            }
        }

        // 2. 전화번호 기반 필터 매칭
        if (normalizedSender != null) {
            for (phoneNumber in sortedPhoneNumbers) {
                if (normalizedSender.contains(phoneNumber) || phoneNumber.contains(normalizedSender)) {
                    phoneNumberIndex[phoneNumber]?.forEach { filter ->
                        if (filter.id !in usedFilters) {
                            matchedFilters.add(filter)
                            usedFilters.add(filter.id)
                        }
                    }
                }
            }
        }

        return matchedFilters
    }

    /**
     * 단일 키워드 매칭 (더 빠른 검사)
     */
    fun hasAnyMatch(messageBody: String, sender: String? = null): Boolean {
        if (keywordIndex.isEmpty() && phoneNumberIndex.isEmpty()) return false

        val normalizedMessage = messageBody.lowercase()
        val hasKeywordMatch = sortedKeywords.any { normalizedMessage.contains(it) }

        if (hasKeywordMatch) return true

        // 전화번호 매칭 확인
        if (sender != null && phoneNumberIndex.isNotEmpty()) {
            val normalizedSender = normalizePhoneNumber(sender)
            return sortedPhoneNumbers.any {
                normalizedSender.contains(it) || it.contains(normalizedSender)
            }
        }

        return false
    }

    /**
     * 전화번호 정규화 (하이픈, 공백 제거)
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[\\s-]"), "").trim()
    }

    /**
     * 인덱스 상태 정보
     */
    fun getIndexInfo(): IndexInfo {
        return IndexInfo(
            totalKeywords = sortedKeywords.size,
            totalPhoneNumbers = sortedPhoneNumbers.size,
            totalFilters = keywordIndex.values.sumOf { it.size } + phoneNumberIndex.values.sumOf { it.size },
            averageFiltersPerKeyword = if (sortedKeywords.isNotEmpty()) {
                keywordIndex.values.map { it.size }.average()
            } else 0.0
        )
    }

    data class IndexInfo(
        val totalKeywords: Int,
        val totalPhoneNumbers: Int,
        val totalFilters: Int,
        val averageFiltersPerKeyword: Double
    )
}