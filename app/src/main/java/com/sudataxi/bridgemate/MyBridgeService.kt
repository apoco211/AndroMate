package com.sudataxi.bridgemate

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*

class MyBridgeService : Service() {

    private lateinit var db: FirebaseDatabase
    private lateinit var youtubeHelper: YouTubeHelper
    private val channelId = "bridge_foreground_v137"

    companion object {
        const val DB_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification("아이폰 원격 제어 대기 중 (v1.3.7)")
        startForeground(1, notification)

        youtubeHelper = YouTubeHelper(this)
        youtubeHelper.logCallback = { msg -> updateNotification(msg) }
        initFirebase()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "수다택시 브릿지 서비스", NotificationManager.IMPORTANCE_LOW)
            channel.description = "앱을 꺼도 아이폰 원격 명령을 받기 위한 서비스"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("수다택시 A25 브릿지 실행 중 (v1.3.7)")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(msg: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(1, buildNotification(msg))
        } catch (e: Exception) {}
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setDatabaseUrl(DB_URL)
                    .setProjectId("sudataxi-bridge")
                    .setApplicationId("1:000000000:android:000000")
                    .setApiKey("dummy-key")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
            db = FirebaseDatabase.getInstance()

            val ytPaths = listOf(
                "sudataxi/commands/youtube",
                "sudataxi/commands/youtube_compat",
                "sudataxi/commands/system",
                "sudataxi/commands/youtube_play"
            )
            ytPaths.forEach { path ->
                db.getReference(path).addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        val map = snap.value as? Map<*, *> ?: return
                        val ts = (map["timestamp"] as? Long) ?: 0L
                        if (System.currentTimeMillis() - ts > 30000) return
                        val action = (map["action"] as? String ?: "").lowercase()
                        if (action.contains("youtube") || action.contains("play") || action == "search" || action == "pause" || action == "resume") {
                            updateNotification("명령 수신: $action")
                            youtubeHelper.handleCommand(map)
                        }
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
            }
            updateNotification("Firebase 연결됨 - 명령 대기 중")
        } catch (e: Exception) {
            updateNotification("Firebase 실패: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
