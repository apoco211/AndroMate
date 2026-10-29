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
    private val REQ = 1001
    private lateinit var tv: TextView
    private val PERMS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            tv = findViewById(R.id.statusText)
            val btnStart = findViewById<Button>(R.id.btnStartService)
            val btnSync = findViewById<Button>(R.id.btnSyncSms)
            btnStart.setOnClickListener { checkPerms() }
            btnSync.setOnClickListener {
                MyBridgeService.syncSmsInbox(this)
                tv.text = "📡 200건 동기화 요청 (백그라운드)\n수신100+발신100"
            }
            checkPerms()
        } catch (e: Exception) {
            val t = TextView(this)
            t.text = "오류: ${e.message}"
            setContentView(t)
        }
    }
    private fun checkPerms() {
        val missing = PERMS.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 33) {
                tv.text = "⚠️ 권한 필요\n설정 > 앱 > 수다택시 브릿지 > ⋮ > 제한된 설정 허용 후 다시 실행"
            }
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ)
        } else {
            tv.text = "✅ 권한 OK - 서비스 시작"
            MyBridgeService.startService(this)
            tv.text = "✅ 서비스 실행 중\n200건 자동 동기화 진행"
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            MyBridgeService.startService(this)
        }
    }
}