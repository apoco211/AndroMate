package com.sudataxi.bridgemate

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val PERMISSION_REQUEST_CODE = 1001
    private lateinit var statusText: TextView

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val btnStart = findViewById<Button>(R.id.btnStartService)
        val btnSync = findViewById<Button>(R.id.btnSyncSms)

        btnStart.setOnClickListener { checkAndRequestPermissions() }
        btnSync.setOnClickListener {
            MyBridgeService.syncSmsInbox(this)
            statusText.text = "📡 SMS 200건 동기화 중..."
        }
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 33) {
                statusText.text = "⚠️ 권한 필요\n\n설정 > 앱 > 수다택시 브릿지 > ⋮ > 제한된 설정 허용\n후 허용 필요:\n${missing.joinToString("\n")}"
            }
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            statusText.text = "✅ 권한 OK - 서비스 시작"
            MyBridgeService.startService(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            statusText.text = if (allGranted) {
                MyBridgeService.startService(this)
                "✅ 권한 허용 완료 - 실행 중"
            } else {
                "❌ 권한 거부됨\nPlay 프로텍트 끄고\n제한된 설정 허용 후 재시도"
            }
        }
    }
}