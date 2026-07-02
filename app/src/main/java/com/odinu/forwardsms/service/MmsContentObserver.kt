package com.odinu.forwardsms.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Telephony
import com.odinu.forwardsms.FilterRepository
import com.odinu.forwardsms.utils.LogCollector
import com.odinu.forwardsms.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WAP_PUSH_RECEIVED 인텐트는 MMS 알림(전송 위치 URL)만 담고 있어 실제 본문이 없다.
 * 실제 본문은 시스템/기본 메시지 앱이 다운로드를 완료한 뒤 content://mms 에 기록하므로,
 * 해당 Provider 변경을 감지해 본문을 읽어와 필터를 실행한다.
 *
 * 하나의 MMS 삽입에도 여러 번 onChange가 연달아 호출될 수 있어, 동시 처리 시
 * 같은 메시지가 중복 전달될 수 있다. mutex로 처리를 직렬화해 이를 막는다.
 */
class MmsContentObserver(
    private val context: Context,
    handler: Handler,
    private val scope: CoroutineScope
) : ContentObserver(handler) {

    private val forwardedIds = LinkedHashSet<Long>()
    private val mutex = Mutex()

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    processLatestMms()
                } catch (e: Exception) {
                    LogCollector.e("MmsObserver", "MMS 처리 중 오류: ${e.message}")
                }
            }
        }
    }

    private suspend fun processLatestMms() {
        val candidates = mutableListOf<Pair<Long, Long>>()

        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
            "${Telephony.Mms.MESSAGE_BOX} = ?",
            arrayOf(Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
            "${Telephony.Mms.DATE} DESC LIMIT 5"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
            while (cursor.moveToNext()) {
                candidates.add(cursor.getLong(idCol) to cursor.getLong(dateCol))
            }
        }

        for ((id, dateSec) in candidates) {
            if (isForwarded(id)) continue

            val body = getMmsText(id)
            if (body.isBlank()) continue // 아직 다운로드 중 - 다음 onChange에서 재시도

            val sender = getMmsSender(id) ?: "Unknown"
            markForwarded(id)

            LogCollector.i("MmsObserver", "MMS 수신 감지 (id=$id)")
            SecureLogger.logSmsReceived("MmsObserver", sender, body)

            val repository = FilterRepository.getInstance(context)
            repository.checkAndTriggerFilters(body, sender, dateSec * 1000)
        }
    }

    private fun getMmsSender(mmsId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val addressCol = cursor.getColumnIndex("address")
            val typeCol = cursor.getColumnIndex("type")
            while (cursor.moveToNext()) {
                val type = if (typeCol >= 0) cursor.getInt(typeCol) else -1
                if (type == PDU_FROM_TYPE && addressCol >= 0) {
                    return cursor.getString(addressCol)
                }
            }
        }
        return null
    }

    private fun getMmsText(mmsId: Long): String {
        val body = StringBuilder()
        context.contentResolver.query(
            Uri.parse("content://mms/part"),
            null,
            "mid=?",
            arrayOf(mmsId.toString()),
            null
        )?.use { cursor ->
            val ctCol = cursor.getColumnIndex("ct")
            val idCol = cursor.getColumnIndex("_id")
            val textCol = cursor.getColumnIndex("text")
            while (cursor.moveToNext()) {
                if (ctCol < 0 || cursor.getString(ctCol) != "text/plain") continue

                var text = if (textCol >= 0) cursor.getString(textCol) else null
                if (text.isNullOrEmpty() && idCol >= 0) {
                    text = readMmsPart(cursor.getLong(idCol))
                }
                if (!text.isNullOrEmpty()) {
                    body.append(text)
                }
            }
        }
        return body.toString()
    }

    private fun readMmsPart(partId: Long): String? {
        return try {
            context.contentResolver.openInputStream(Uri.parse("content://mms/part/$partId"))
                ?.use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun isForwarded(id: Long): Boolean = synchronized(forwardedIds) {
        forwardedIds.contains(id)
    }

    private fun markForwarded(id: Long) = synchronized(forwardedIds) {
        forwardedIds.add(id)
        while (forwardedIds.size > MAX_TRACKED_IDS) {
            forwardedIds.remove(forwardedIds.first())
        }
    }

    companion object {
        private const val PDU_FROM_TYPE = 137 // PduHeaders.FROM
        private const val MAX_TRACKED_IDS = 500
    }
}
