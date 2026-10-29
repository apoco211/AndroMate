package com.sudataxi.bridgemate

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class MyBridgeService : Service() {

    companion object {
        private const val TAG = "SudaBridge"
        private const val FIREBASE_DB_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "suda_bridge_channel"

        fun startService(context: Context) {
            val intent = Intent(context, MyBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun syncSmsInbox(context: Context) {
            MyBridgeService().syncAllSms(context)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("A25 브릿지 실행 중"))
        listenForCommands()
        syncAllSms(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncAllSms(this)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "수다택시 브릿지", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("수다택시 브릿지 v1.3.29").setContentText(content).setSmallIcon(android.R.drawable.stat_notify_sync).build()
    }

    private fun listenForCommands() {
        try {
            val db = FirebaseDatabase.getInstance(FIREBASE_DB_URL).reference
            db.child("sudataxi/commands/system").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val data = snapshot.value as? Map<*, *> ?: return
                    val action = data["action"] as? String ?: return
                    if (action == "sync_sms_inbox") syncAllSms(this@MyBridgeService)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        } catch (e: Exception) {}
    }

    private fun getContactName(context: Context, phoneNumber: String): String {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use { if (it.moveToFirst()) it.getString(0) ?: "" else "" } ?: ""
        } catch (e: Exception) { "" }
    }

    fun syncAllSms(context: Context) {
        try {
            val db = FirebaseDatabase.getInstance(FIREBASE_DB_URL).reference.child("sudataxi/inbox/sms")
            val cr: ContentResolver = context.contentResolver
            val inboxCursor: Cursor? = cr.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "${Telephony.Sms.DATE} DESC LIMIT 100")
            val sentCursor: Cursor? = cr.query(Telephony.Sms.Sent.CONTENT_URI, null, null, null, "${Telephony.Sms.DATE} DESC LIMIT 100")
            var count = 0
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

            fun processCursor(cursor: Cursor?, type: String) {
                cursor?.use {
                    val idxAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val idxBody = it.getColumnIndex(Telephony.Sms.BODY)
                    val idxDate = it.getColumnIndex(Telephony.Sms.DATE)
                    while (it.moveToNext()) {
                        val address = if (idxAddress >= 0) it.getString(idxAddress) ?: "" else ""
                        val body = if (idxBody >= 0) it.getString(idxBody) ?: "" else ""
                        val date = if (idxDate >= 0) it.getLong(idxDate) else System.currentTimeMillis()
                        if (address.isBlank() || body.isBlank()) continue
                        val contactName = getContactName(context, address)
                        val data = hashMapOf(
                            "sender" to address, "address" to address, "phone" to address,
                            "message" to body, "body" to body, "text" to body,
                            "timestamp" to date, "date" to date, "time" to sdf.format(Date(date)),
                            "type" to type, "contactName" to contactName, "displayName" to contactName, "name" to contactName
                        )
                        db.child("${date}_${count}_${address.hashCode()}").setValue(data)
                        count++
                    }
                }
            }
            processCursor(inboxCursor, "received")
            processCursor(sentCursor, "sent")
            FirebaseDatabase.getInstance(FIREBASE_DB_URL).reference.child("sudataxi/ping").setValue(true)
        } catch (e: Exception) { Log.e("SudaBridge", "sync error", e) }
    }
}