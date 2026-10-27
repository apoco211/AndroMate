
package com.sudataxi.bridgemate

import android.content.Context
import android.provider.CallLog
import java.text.SimpleDateFormat
import java.util.*

data class LastCall(val number: String, val durationSec: Long, val timeStr: String, val timestamp: Long)

class CallLogManager {
    fun getLastCall(context: Context): LastCall? {
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.DATE)
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC"
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                val number = it.getString(0) ?: ""
                if (number.isBlank()) return null
                val duration = it.getLong(1)
                val date = it.getLong(2)
                val sdf = SimpleDateFormat("오늘 HH:mm", Locale.KOREA)
                return LastCall(number, duration, sdf.format(Date(date)), date)
            }
        }
        return null
    }
}
