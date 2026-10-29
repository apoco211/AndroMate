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
    private lateinit var smsHelper: SmsHelper
    private val channelId = "bridge_foreground_v1414"

    companion object {
        const val DB_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com"
        const val VERSION = "v1.3.14"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification("아이폰 원격 제어 대기 중 ($VERSION)")
        startForeground(1, notification)

        youtubeHelper = YouTubeHelper(this)
        youtubeHelper.logCallback = { msg -> updateNotification(msg) }
        smsHelper = SmsHelper()
        initFirebase()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "수다택시 브릿지 서비스", NotificationManager.IMPORTANCE_LOW)
            channel.description = "앱을 꺼도 아이폰 원격 명령을 받기 위한 서비스 ($VERSION)"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("수다택시 A25 브릿지 실행 중 ($VERSION)")
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

            // ✅ v1.3.14: 유튜브 명령 6중 경로 모두 수신 (PWA 6중 발사 대응)
            val ytPaths = listOf(
                "sudataxi/commands/youtube",
                "sudataxi/commands/youtube_compat",
                "sudataxi/commands/system",
                "sudataxi/commands/youtube_play",
                "sudataxi/commands/media",
                "sudataxi/commands/play"
            )
            ytPaths.forEach { path ->
                db.getReference(path).addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        val map = snap.value as? Map<*, *> ?: return
                        val ts = (map["timestamp"] as? Long) ?: 0L
                        if (System.currentTimeMillis() - ts > 60000) return // 60초 이내만
                        val action = (map["action"] as? String ?: "").lowercase()
                        val actionAlt = (map["actionAlt"] as? String ?: "").lowercase()
                        val actionCompat = (map["actionCompat"] as? String ?: "").lowercase()
                        // ✅ v1.3.14 필터 확장: seek, next, prev, forward, rewind 등 모두 포함
                        if (action.contains("youtube") || action.contains("play") || action.contains("pause") || action.contains("resume") ||
                            action.contains("seek") || action.contains("forward") || action.contains("rewind") ||
                            action.contains("next") || action.contains("prev") || action.contains("volume") ||
                            actionAlt.contains("seek") || actionAlt.contains("next") || actionAlt.contains("prev") ||
                            actionCompat.contains("forward") || actionCompat.contains("rewind") ||
                            action == "search" || action == "next_video" || action == "prev_video") {
                            updateNotification("명령 수신[$path]: $action")
                            youtubeHelper.handleCommand(map)
                        }
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
            }

            // ✅ v1.3.14: SMS 동기화 명령 수신 (5중 경로)
            val smsSyncPaths = listOf(
                "sudataxi/commands/system",
                "sudataxi/commands/sms_sync",
                "sudataxi/commands/sync",
                "sudataxi/commands/sms",
                "sudataxi/commands/inbox"
            )
            smsSyncPaths.forEach { path ->
                db.getReference(path).addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        val map = snap.value as? Map<*, *> ?: return
                        val ts = (map["timestamp"] as? Long) ?: 0L
                        if (System.currentTimeMillis() - ts > 60000) return
                        val action = (map["action"] as? String ?: "").lowercase()
                        if (action.contains("sms") || action.contains("sync") || action.contains("read_sms") || action == "sync_sms_inbox" || action == "request_sms_sync") {
                            updateNotification("📱 SMS 동기화 요청 수신: $action")
                            try {
                                smsHelper.readAndPushAllSms(this@MyBridgeService, db) { msg -> updateNotification(msg) }
                            } catch (e: Exception) {
                                updateNotification("SMS 동기화 실패: ${e.message}")
                            }
                        }
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
            }

            // SMS 발송 명령 수신
            db.getReference("sudataxi/commands/sms").addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                    val map = snap.value as? Map<*, *> ?: return
                    val to = map["to"] as? String ?: return
                    val msg = map["message"] as? String ?: return
                    val ts = (map["timestamp"] as? Long) ?: 0L
                    if (System.currentTimeMillis() - ts > 60000) return
                    // 발송 명령은 sync와 구분: to 필드가 있으면 발송
                    if (to.isNotEmpty() && msg.isNotEmpty() && map["action"] == null) {
                        updateNotification("📤 SMS 발송 요청: $to")
                        smsHelper.sendImmediate(this@MyBridgeService, to, msg)
                    }
                }
                override fun onChildChanged(s: DataSnapshot, p: String?) {}
                override fun onChildRemoved(s: DataSnapshot) {}
                override fun onChildMoved(s: DataSnapshot, p: String?) {}
                override fun onCancelled(e: DatabaseError) {}
            })

            updateNotification("Firebase 연결됨 - $VERSION 명령 대기 중")
        } catch (e: Exception) {
            updateNotification("Firebase 실패: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
