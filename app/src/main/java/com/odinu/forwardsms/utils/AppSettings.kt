package com.odinu.forwardsms.utils

import android.content.Context

class AppSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isNotificationMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_MONITORING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_MONITORING_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_NOTIFICATION_MONITORING_ENABLED = "notification_monitoring_enabled"

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context).also { INSTANCE = it }
            }
        }
    }
}
