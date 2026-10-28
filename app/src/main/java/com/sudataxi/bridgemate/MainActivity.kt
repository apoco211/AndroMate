package com.sudataxi.bridgemate

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var logView: TextView? = null
    private lateinit var db: FirebaseDatabase
    private lateinit var youtubeHelper: YouTubeHelper

    companion object {
        const val DB_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com"
        const val VERSION = "v1.3.6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // R.layout 만 참조 - R.id는 절대 직접 참조 안 함
        setContentView(resources.getIdentifier("activity_main", "layout", packageName))

        // 로그뷰 찾기 - ID 이름 상관없이 동적으로
        logView = findTextViewByNames("logView", "tvLog", "log", "tv_log", "log_text", "tvLogView")
        
        // 버전 텍스트 찾기 - 있으면 업데이트
        val versionView = findTextViewByNames("versionText", "subtitleText", "tvVersion", "version", "subtitle", "tvSubtitle", "version_text")
        versionView?.let {
            if (it.text.toString().contains("v1.") || it.text.toString().contains("데몬")) {
                it.text = "아이폰 Firebase 실시간 원격 제어 데몬 ($VERSION)"
            }
        }

        youtubeHelper = YouTubeHelper(this)
        youtubeHelper.logCallback = { msg -> runOnUiThread { addLog(msg) } }

        initFirebase()

        addLog("🔥 Firebase Realtime DB 연결 성공!")
        addLog("✅ 수다택시 A25 통신 에이전트 준비 완료 ($VERSION)")
        addLog("📡 가짜 데이터 생성 금지 원칙 적용")
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
                    if (connected) {
                        addLog("🟢 Firebase 클라우드 브릿지 활성화됨")
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })

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
                            val vid = map["videoId"] as? String ?: map["url"] as? String ?: action
                            addLog("📱 [아이폰 원격 명령] YouTube 실행: $vid")
                            youtubeHelper.handleCommand(map)
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
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val line = "[" + sdf.format(Date()) + "] " + msg + "\n"
            logView?.append(line)
        } catch (e: Exception) {}
    }
}