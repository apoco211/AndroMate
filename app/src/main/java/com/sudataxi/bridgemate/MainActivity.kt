package com.sudataxi.bridgemate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

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
            statusText.text = "📡 SMS 동기화 요청 중... 200건 읽기"
        }
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 33) {
                statusText.text = "⚠️ 권한 필요\n\n설정 > 앱 > 수다택시 브릿지 > ⋮ > 제한된 설정 허용\n후 권한 허용"
            }
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            statusText.text = "✅ 모든 권한 허용됨"
            MyBridgeService.startService(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                statusText.text = "✅ 권한 허용 완료"
                MyBridgeService.startService(this)
            } else {
                statusText.text = "❌ 권한 거부됨 - Play 프로텍트 끄고 제한된 설정 허용"
            }
        }
    }
}