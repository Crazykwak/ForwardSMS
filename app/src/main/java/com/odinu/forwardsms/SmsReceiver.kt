package com.odinu.forwardsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.odinu.forwardsms.utils.LogCollector
import com.odinu.forwardsms.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        LogCollector.i("SmsReceiver", "=== 메시지 수신 ===")
        LogCollector.i("SmsReceiver", "Action: ${intent.action}")
        LogCollector.d("SmsReceiver", "Data: ${intent.data}")
        LogCollector.d("SmsReceiver", "Extras: ${intent.extras?.keySet()}")

        Log.d("SmsReceiver", "Received intent with action: ${intent.action}")
        Log.d("SmsReceiver", "Intent data: ${intent.data}")
        Log.d("SmsReceiver", "Intent extras: ${intent.extras?.keySet()}")

        // 모든 extra 데이터 로깅
        intent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                LogCollector.d("SmsReceiver", "Extra [$key]: $value")
                Log.d("SmsReceiver", "Extra [$key]: $value")
            }
        }

        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                Log.d("SmsReceiver", "Processing SMS message")
                handleSmsReceived(context, intent)
            }
            "android.provider.Telephony.SMS_CB_RECEIVED",
            "android.provider.Telephony.WAP_PUSH_RECEIVED",
            "android.provider.Telephony.MMS_RECEIVED" -> {
                Log.d("SmsReceiver", "Processing MMS/CB/WAP message: ${intent.action}")
                handleMmsReceived(context, intent)
            }
            else -> {
                Log.d("SmsReceiver", "Unknown action: ${intent.action}")
            }
        }
    }

    private fun handleSmsReceived(context: Context, intent: Intent) {
        val bundle = intent.extras
        Log.i("INFO", "here we go")
        if (bundle != null) {
            val pdus = bundle.get("pdus") as Array<*>?
            if (pdus != null) {
                for (pdu in pdus) {
                    val format = bundle.getString("format")
                    val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                    val messageBody = sms.messageBody
                    val sender = sms.originatingAddress
                    val timestamp = System.currentTimeMillis()

                    LogCollector.i("SMS", "SMS 수신 감지")
                    SecureLogger.logSmsReceived("SmsReceiver", sender, messageBody)

                    CoroutineScope(Dispatchers.IO).launch {
                        Log.d("SmsReceiver", "필터 체크 시작: message='$messageBody', sender='$sender'")
                        try {
                            val repository = FilterRepository.getInstance(context)
                            Log.d("SmsReceiver", "Repository 인스턴스 생성 완료")
                            repository.checkAndTriggerFilters(messageBody, sender, timestamp)
                            Log.d("SmsReceiver", "필터 체크 완료")
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "필터 체크 중 오류 발생", e)
                        }
                    }
                }
            }
        }
    }

    private fun handleMmsReceived(context: Context, intent: Intent) {
        try {
            Log.d("SmsReceiver", "Handling MMS/CB/WAP message")

            val extras = intent.extras
            extras?.let { bundle ->
                Log.d("SmsReceiver", "Available extras keys: ${bundle.keySet().joinToString()}")

                val messageFromExtras = bundle.getString("message")
                    ?: bundle.getString("text")
                    ?: bundle.getString("body")

                val senderFromExtras = bundle.getString("sender")
                    ?: bundle.getString("from")
                    ?: bundle.getString("address")

                if (!messageFromExtras.isNullOrEmpty()) {
                    LogCollector.i("MMS", "MMS 수신 감지")
                    SecureLogger.logSmsReceived("SmsReceiver", senderFromExtras, messageFromExtras)

                    CoroutineScope(Dispatchers.IO).launch {
                        val repository = FilterRepository.getInstance(context)
                        repository.checkAndTriggerFilters(
                            messageFromExtras,
                            senderFromExtras ?: "Unknown",
                            System.currentTimeMillis()
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error handling MMS message: ${e.message}", e)
        }
    }

}