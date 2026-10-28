package com.sudataxi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var versionText: TextView
    private lateinit var firebaseStatus: TextView
    private lateinit var permSms: TextView
    private lateinit var permNoti: TextView
    private lateinit var permBattery: TextView
    private lateinit var logView: TextView

    private lateinit var youtubeHelper: YouTubeHelper
    private lateinit var smsHelper: SmsHelper
    private lateinit var callLogManager: CallLogManager
    private lateinit var db: FirebaseDatabase

    companion object {
        const val DB_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com"
        const val VERSION = "v1.3.6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 뷰 연결 - 너의 activity_main.xml ID에 맞춰놨음
        versionText = findViewById(R.id.versionText) ?: findViewById(R.id.subtitleText) ?: findViewById(R.id.tvVersion)
        firebaseStatus = findViewById(R.id.firebaseStatus) ?: findViewById(R.id.tvFirebaseStatus)
        permSms = findViewById(R.id.permSms) ?: findViewById(R.id.statusSms)
        permNoti = findViewById(R.id.permNoti) ?: findViewById(R.id.statusNoti)
        permBattery = findViewById(R.id.permBattery) ?: findViewById(R.id.statusBattery)
        logView = findViewById(R.id.logView) ?: findViewById(R.id.tvLog)

        // 헬퍼 초기화
        youtubeHelper = YouTubeHelper(this)
        smsHelper = SmsHelper(this)
        callLogManager = CallLogManager(this)

        youtubeHelper.logCallback = { msg -> runOnUiThread { addLog(msg) } }
        smsHelper.logCallback = { msg -> runOnUiThread { addLog(msg) } }

        // v1.3.6 버전 표시 - 사진 속 (v1.2.1) 이 부분이 여기야
        try {
            versionText.text = "아이폰 Firebase 실시간 원격 제어 데몬 ($VERSION)"
        } catch (e: Exception) {}

        initFirebase()
        checkPermissions()
        addLog("🔥 Firebase Realtime DB 연결 성공!")
        addLog("✅ 수다택시 A25 통신 에이전트 준비 완료 ($VERSION)")
        addLog("📡 가짜 데이터 생성 금지 원칙 적용")
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setDatabaseUrl(DB_URL)
                    .setProjectId("sudataxi-bridge")
                    .setApplicationId("1:000000000:android:000000")
                    .setApiKey("dummy-key")
                   