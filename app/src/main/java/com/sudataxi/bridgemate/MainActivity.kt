
package com.sudataxi.bridgemate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sudataxi.bridgemate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val callLogManager = CallLogManager()
    private val smsHelper = SmsHelper()
    private val youTubeHelper = YouTubeHelper()

    companion object {
        const val PERM_REQ = 1001
        val PERMS = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPerms()
        setupUI()
        loadLastCall()
    }

    private fun checkPerms() {
        val need = PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), PERM_REQ)
        }
    }

    private fun setupUI() {
        binding.btnRefresh.setOnClickListener { loadLastCall() }
        binding.btnSendThankYou.setOnClickListener {
            val number = binding.tvNumber.text.toString()
            val msg = binding.etTemplate1.text.toString()
            if (number.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, "번호와 메시지를 확인하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val success = smsHelper.sendImmediate(this, number, msg)
            if (success) {
                Toast.makeText(this, "발송 완료: " + number, Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "발송 완료"
            } else {
                Toast.makeText(this, "발송 실패 - 권한 확인", Toast.LENGTH_LONG).show()
            }
        }
        binding.btnYouTube.setOnClickListener { youTubeHelper.launchYouTube(this) }
        binding.btnYouTubeMusic.setOnClickListener { youTubeHelper.launchYouTubeMusic(this) }
        binding.btnYouTubeSearch.setOnClickListener {
            val query = binding.etYouTubeQuery.text.toString()
            if (query.isNotBlank()) youTubeHelper.searchYouTube(this, query)
        }
        binding.chipTrot.setOnClickListener { youTubeHelper.searchYouTube(this, "트로트") }
        binding.chipLatest.setOnClickListener { youTubeHelper.searchYouTube(this, "최신가요") }
        binding.chipCho.setOnClickListener { youTubeHelper.searchYouTube(this, "조용필") }
    }

    private fun loadLastCall() {
        val last = callLogManager.getLastCall(this)
        if (last != null) {
            binding.tvNumber.text = last.number
            binding.tvDuration.text = last.durationSec.toString() + "초 통화 - " + last.timeStr
            val isAnsim = last.number.startsWith("0504") || last.number.contains("0504")
            binding.tvBadge.text = if (isAnsim) "안심번호 - 10분 내 발송 권장" else "일반번호"
        } else {
            binding.tvNumber.text = "통화 기록 없음"
            binding.tvDuration.text = "권한을 확인하세요"
        }
    }
}
