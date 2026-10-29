package com.sudataxi.bridgemate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private var logView: TextView? = null
    private lateinit var db: FirebaseDatabase
    private lateinit var youtubeHelper: YouTubeHelper
    private lateinit var smsHelper: SmsHelper

    companion object {
        const val DB_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com"
        const val VERSION = "v1.3.14"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("activity_main", "layout", packageName))

        logView = findTextViewByNames("logView", "tvLog", "log", "tv_log", "log_text", "tvLogView")
        val versionView = findTextViewByNames("versionText", "subtitleText", "tvVersion", "version", "subtitle", "tvSubtitle", "version_text")
        versionView?.let {
            if (it.text.toString().contains("v1.") || it.text.toString().contains("데몬")) {
                it.text = "아이폰 Firebase 실시간 원격 제어 데몬 ($VERSION) - 백그라운드 서비스"
            }
        }

        youtubeHelper = YouTubeHelper(this)
        youtubeHelper.logCallback = { msg -> runOnUiThread { addLog(msg) } }
        smsHelper = SmsHelper()

        initFirebase()
        startBridgeService()

        addLog("🔥 Firebase Realtime DB 연결 성공!")
        addLog("✅ 수다택시 A25 통신 에이전트 준비 완료 ($VERSION)")
        addLog("📡 유튜브 토스/시크/다음이전 지원")
        addLog("📱 SMS 읽기/동기화 지원")
        addLog("🛡️ 포그라운드 서비스 시작 - 앱을 꺼도 작동함")
    }

    private fun startBridgeService() {
        try {
            val svcIntent = Intent(this, MyBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
            addLog("🚀 MyBridgeService 시작 요청 완료 ($VERSION)")
        } catch (e: Exception) {
            addLog("서비스 시작 실패: ${e.message}")
        }
    }

    private fun findTextViewByNames(vararg names: String): TextView? {
        for (name in names) {
            try {
                val id = resources.getIdentifier(name, "id", packageName)
                if (id != 0) {
                    val v = findViewById<TextView>(id)
                    if (v != null) return v
                }
            } catch (e: Exception) {}
        }
        return null
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
            db.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val connected = s.getValue(Boolean::class.java) ?: false
                    if (connected) addLog("🟢 Firebase 클라우드 브릿지 활성화됨 ($VERSION)")
                }
                override fun onCancelled(e: DatabaseError) {}
            })

            // ✅ v1.3.14: 유튜브 명령 6중 경로
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
                        if (System.currentTimeMillis() - ts > 60000) return
                        val action = (map["action"] as? String ?: "").lowercase()
                        val actionAlt = (map["actionAlt"] as? String ?: "").lowercase()
                        if (action.contains("youtube") || action.contains("play") || action.contains("pause") || action.contains("resume") ||
                            action.contains("seek") || action.contains("next") || action.contains("prev") || action.contains("volume") ||
                            actionAlt.contains("seek") || actionAlt.contains("next") || actionAlt.contains("prev")) {
                            val vid = map["videoId"] as? String ?: map["url"] as? String ?: action
                            addLog("📱 [아이폰 원격 명령] $path: $action -> $vid")
                            youtubeHelper.handleCommand(map)
                        }
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
            }

            // ✅ v1.3.14: SMS 동기화 명령
            val smsPaths = listOf(
                "sudataxi/commands/system",
                "sudataxi/commands/sms_sync",
                "sudataxi/commands/sync",
                "sudataxi/commands/inbox"
            )
            smsPaths.forEach { path ->
                db.getReference(path).addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        val map = snap.value as? Map<*, *> ?: return
                        val ts = (map["timestamp"] as? Long) ?: 0L
                        if (System.currentTimeMillis() - ts > 60000) return
                        val action = (map["action"] as? String ?: "").lowercase()
                        if (action.contains("sms") || action.contains("sync") || action.contains("read_sms")) {
                            addLog("📱 SMS 동기화 요청 수신[$path]: $action")
                            smsHelper.readAndPushAllSms(this@MainActivity, db) { msg -> runOnUiThread { addLog(msg) } }
                        }
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
            }

        } catch (e: Exception) {
            addLog("Firebase 초기화 실패: " + e.message)
        }
    }

    private fun addLog(msg: String) {
        try {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val line = "[" + sdf.format(java.util.Date()) + "] " + msg + "\n"
            logView?.append(line)
        } catch (e: Exception) {}
    }
}
