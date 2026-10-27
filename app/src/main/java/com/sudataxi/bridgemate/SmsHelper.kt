
package com.sudataxi.bridgemate

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

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
            Log.d("SmsHelper", "Sent to " + phoneNumber)
            true
        } catch (e: Exception) {
            Log.e("SmsHelper", "Send failed", e)
            false
        }
    }
}
