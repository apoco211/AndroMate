package com.sudataxi.bridgemate

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class SmsHelper {
    fun sendImmediate(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.d("SmsHelper", "Sent to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e("SmsHelper", "Send failed", e)
            false
        }
    }

    // ✅ 추가: 단말기 SMS 전체 읽어서 Firebase로 푸시 (PWA 동기화용)
    fun readAndPushAllSms(context: Context, db: FirebaseDatabase, logCallback: ((String)->Unit)? = null) {
        fun log(msg: String) { Log.d("SmsHelper", msg); logCallback?.invoke(msg) }
        try {
            log("📱 SMS 읽기 시작...")
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 100"
            )

            if (cursor == null) {
                log("❌ SMS cursor null - 권한 확인 필요")
                return
            }

            var count = 0
            var pushCount = 0
            val inboxRef = db.getReference("sudataxi/inbox/sms")
            // 기존 inbox 비우지 않고 push (중복 방지 위해 타임스탬프 체크는 PWA에서)

            cursor.use {
                while (it.moveToNext()) {
                    try {
                        val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: continue
                        if (address.isBlank()) continue
                        val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                        val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        val typeInt = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                        val type = if (typeInt == Telephony.Sms.MESSAGE_TYPE_INBOX) "received" else "sent"

                        // 필터: 빈 메시지 제외
                        if (body.isBlank()) continue

                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val timeStr = sdf.format(Date(date))

                        val data = mapOf(
                            "sender" to address,
                            "message" to body,
                            "time" to timeStr,
                            "timestamp" to date,
                            "type" to type
                        )

                        // Firebase로 push
                        inboxRef.push().setValue(data)
                            .addOnSuccessListener { pushCount++ }
                            .addOnFailureListener { e -> log("push 실패: ${e.message}") }

                        count++
                        if (count >= 100) break
                    } catch (e: Exception) {
                        log("SMS 1개 읽기 실패: ${e.message}")
                    }
                }
            }

            log("✅ SMS $count 개 읽음, Firebase로 푸시 시도")
            // 2초 후 결과 로그
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                log("📤 Firebase 푸시 완료 예상: $count 개")
            }, 2000)

        } catch (e: Exception) {
            log("❌ SMS 읽기 전체 실패: ${e.message} - 권한 READ_SMS 확인")
            Log.e("SmsHelper", "readAndPush failed", e)
        }
    }
}
