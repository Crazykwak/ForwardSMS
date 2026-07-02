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
            if (pdus != null && pdus.isNotEmpty()) {
                val format = bundle.getString("format")
                val messages = pdus.map { pdu -> SmsMessage.createFromPdu(pdu as ByteArray, format) }
                // 긴 메시지(LMS)는 여러 PDU로 분할되어 도착하므로 전체 내용을 합쳐야 함
                val messageBody = messages.joinToString(separator = "") { it.messageBody }
                val sender = messages.first().originatingAddress
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

    private fun handleMmsReceived(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                "android.provider.Telephony.SMS_CB_RECEIVED" -> {
                    // 긴급/재난 문자(Cell Broadcast) 본문은 시스템 전용(hidden) API로만 노출되어
                    // 일반 앱은 공개 SDK로 내용을 읽을 수 없다.
                    Log.d("SmsReceiver", "Cell Broadcast 수신 - 공개 API로 본문 접근 불가, 처리 생략")
                }
                else -> {
                    // WAP_PUSH_RECEIVED(MMS)에는 다운로드 위치(URL)만 담겨 있고 실제 본문은 없다.
                    // 본문은 다운로드 완료 후 content://mms 에 저장되므로, 그 변화를
                    // MmsContentObserver(MessageProcessingService에 등록됨)가 감지해 처리한다.
                    Log.d("SmsReceiver", "MMS 알림 수신(${intent.action}) - MmsContentObserver가 본문 처리")
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error handling MMS message: ${e.message}", e)
        }
    }

}