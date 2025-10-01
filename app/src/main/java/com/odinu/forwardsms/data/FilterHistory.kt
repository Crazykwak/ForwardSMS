package com.odinu.forwardsms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_history")
data class FilterHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val filterId: Int,
    val filterKeyword: String,
    val smsMessage: String,
    val sender: String,
    val webhookUrl: String,
    val httpMethod: String,
    val timestamp: Long,
    val success: Boolean,
    val responseCode: Int? = null,
    val errorMessage: String? = null
)