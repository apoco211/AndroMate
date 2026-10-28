package com.sudataxi.bridgemate

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * [A25 전용 수다택시 브릿지 에이전트]
 * 역할:
 * 1. 차량 백그라운드에서 상시 대기하며 승객 문자 및 카카오T 알림 감지
 * 2. 아이폰 PWA에서 명령을 받아 유튜브 실행 및 문자(감사문자/답장) 발송
 * 3. XML 레이아웃 ID 충돌을 방지하기 위해 순수 코드로 대시보드 UI를 구성
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var logContainer: LinearLayout
    private lateinit var btnSmsPerm: Button
    private lateinit var btnNotiPerm: Button
    private lateinit var btnBatteryPerm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeManager.activeActivity = this

        // A25 브릿지 관리 대시보드 화면 생성
        setContentView(createDashboardView())

        checkAllPermissions()
        addLog("✅ 수다택시 A25 통신 에이전트 준비 완료")
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BridgeManager.activeActivity == this) {
            BridgeManager.activeActivity = null
        }
    }

    // 1. 대시보드 뷰 프로그래밍 방식 생성 (XML 에러 방지)
    private fun createDashboardView(): View {
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        // 헤더
        val title = TextView(this).apply {
            text = "수다택시 A25 브릿지 에이전트"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        mainLayout.addView(title)

        val subTitle = TextView(this).apply {
            text = "아이폰 PWA 리모콘 연동 백그라운드 데몬"
            textSize = 13f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 8, 0, 32)
        }
        mainLayout.addView(subTitle)

        // 상태 카드
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(40, 40, 40, 40)
            elevation = 8f
        }

        statusText = TextView(this).apply {
            text = "● 백그라운드 브릿지 활성화됨"
            textSize = 15f
            setTextColor(Color.parseColor("#4CAF50"))
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(statusText)

        val desc = TextView(this).apply {
            text = "A25는 운전 중 조작하지 마세요. 손안의 아이폰에서 모든 기능을 원격 조종합니다."
            textSize = 12f
            setTextColor(Color.parseColor("#B0B0B0"))
            setPadding(0, 16, 0, 0)
        }
        card.addView(desc)
        mainLayout.addView(card)

        // 권한 설정 섹션 헤더
        val permTitle = TextView(this).apply {
            text = "필수 권한 설정 상태"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 48, 0, 16)
        }
        mainLayout.addView(permTitle)

        // 1) 문자 권한 버튼
        btnSmsPerm = Button(this).apply {
            text = "1. 문자 수발신 권한 허용하기"
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setTextColor(Color.WHITE)
            setOnClickListener { requestSmsPermissions() }
        }
        mainLayout.addView(btnSmsPerm)

        // 2) 알림 읽기 권한 버튼
        btnNotiPerm = Button(this).apply {
            text = "2. 카카오T 알림 감지 권한 허용하기"
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
            layoutParams = lp
            setOnClickListener { openNotificationAccessSettings() }
        }
        mainLayout.addView(btnNotiPerm)

        // 3) 배터리 최적화 제외 버튼
        btnBatteryPerm = Button(this).apply {
            text = "3. 백그라운드 절전 제외 (상시 유지)"
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
            layoutParams = lp
            setOnClickListener { requestBatteryOptimizationExemption() }
        }
        mainLayout.addView(btnBatteryPerm)

        // 빠른 테스트 도구 (유튜브 실행 & 테스트 문자)
        val testTitle = TextView(this).apply {
            text = "동작 테스트 (아이폰 명령 수신 시뮬레이션)"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 48, 0, 16)
        }
        mainLayout.addView(testTitle)

        val btnTestYoutube = Button(this).apply {
            text = "▶ 유튜브 백그라운드 실행 테스트"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                BridgeManager.playYoutube(this@MainActivity, "오늘의 뉴스 핵심 요약")
            }
        }
        mainLayout.addView(btnTestYoutube)

        // 실시간 수신 이벤트 로그
        val logTitle = TextView(this).apply {
            text = "실시간 수신 이벤트 로그"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 48, 0, 16)
        }
        mainLayout.addView(logTitle)

        logContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(24, 24, 24, 24)
        }
        mainLayout.addView(logContainer)

        root.addView(mainLayout)
        return root
    }

    // 2. 권한 검사 및 업데이트
    private fun checkAllPermissions() {
        val requiredPerms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requiredPerms.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requiredPerms.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requiredPerms.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (requiredPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, requiredPerms.toTypedArray(), 1001)
        }
    }

    private fun updatePermissionStatus() {
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                     ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        btnSmsPerm.text = if (hasSms) "✔ 1. 문자 수발신 권한 [허용됨]" else "1. 문자 수발신 권한 허용하기"
        btnSmsPerm.setBackgroundColor(if (hasSms) Color.parseColor("#1B5E20") else Color.parseColor("#2C2C2C"))

        val hasNoti = isNotificationServiceEnabled()
        btnNotiPerm.text = if (hasNoti) "✔ 2. 카카오T 알림 감지 권한 [허용됨]" else "2. 카카오T 알림 감지 권한 허용하기"
        btnNotiPerm.setBackgroundColor(if (hasNoti) Color.parseColor("#1B5E20") else Color.parseColor("#2C2C2C"))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        btnBatteryPerm.text = if (isIgnoringBattery) "✔ 3. 배터리 절전 제외 [완료됨]" else "3. 백그라운드 절전 제외 (상시 유지)"
        btnBatteryPerm.setBackgroundColor(if (isIgnoringBattery) Color.parseColor("#1B5E20") else Color.parseColor("#2C2C2C"))
    }

    private fun requestSmsPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            1001
        )
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "'수다택시 A25 브릿지'를 찾아서 권한을 켜주세요", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "설정 > 배터리 최적화에서 수다택시를 제외해주세요", Toast.LENGTH_LONG).show()
        }
    }

    fun addLog(message: String) {
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
            val logItem = TextView(this).apply {
                text = "[$time] $message"
                textSize = 12f
                setTextColor(Color.parseColor("#ECEFF1"))
                setPadding(0, 6, 0, 6)
            }
            logContainer.addView(logItem, 0)
        }
    }
}

/**
 * [BridgeManager]
 * 아이폰 PWA와 A25 단말기 사이의 통신 및 제어를 중앙 처리하는 객체
 */
object BridgeManager {
    var activeActivity: MainActivity? = null

    // 1. 유튜브 원격 재생 실행 (A25 유튜브 앱 백그라운드 구동)
    fun playYoutube(context: Context, query: String) {
        try {
            activeActivity?.addLog("📱 [아이폰 명령] YouTube 재생: $query")
            val uri = Uri.parse("vnd.youtube://results?search_query=" + Uri.encode(query))
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    // 2. A25 유심을 통한 승객 감사문자/답장 즉시 발송
    fun sendSms(context: Context, phone: String, message: String) {
        try {
            activeActivity?.addLog("📤 [SMS 발송] To: $phone, 내용: $message")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, message, null, null)
            Toast.makeText(context, "A25에서 승객($phone)에게 문자 발송 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            activeActivity?.addLog("❌ SMS 발송 실패: ${e.message}")
        }
    }

    // 3. 승객 수신 메시지를 아이폰 PWA로 포워딩
    fun onSmsReceived(sender: String, message: String) {
        activeActivity?.addLog("📩 [승객 문자 수신] $sender: $message")
        // TODO: 향후 Firebase Realtime DB / 서버로 전송하여 아이폰 PWA 화면에 즉시 표시
    }

    // 4. 카카오T 알림을 아이폰 PWA로 포워딩
    fun onKakaoNotificationReceived(title: String, text: String) {
        activeActivity?.addLog("🟡 [카카오T 감지] $title: $text")
        // TODO: 향후 Firebase Realtime DB / 서버로 전송하여 아이폰 PWA 화면에 즉시 표시
    }
}

/**
 * [SmsReceiver]
 * A25 단말기에 승객의 문자(안심번호 0504 등)가 도착했을 때 가로채는 리시버
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "알 수 없음"
                val body = sms.messageBody ?: ""
                BridgeManager.onSmsReceived(sender, body)
            }
        }
    }
}

/**
 * [KakaoNotificationService]
 * 카카오T 기사앱, 카카오톡 알림을 백그라운드에서 실시간 추출하는 리스너 서비스
 */
class KakaoNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val pkg = sbn.packageName
        // 카카오톡 및 카카오T 기사앱 패키지 감지
        if (pkg == "com.kakao.talk" || pkg.contains("taxi") || pkg.contains("driver")) {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            if (title.isNotEmpty() || text.isNotEmpty()) {
                BridgeManager.onKakaoNotificationReceived(title, text)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        BridgeManager.activeActivity?.addLog("✔ 카카오T 알림 리스너 서비스 연결됨")
    }
}