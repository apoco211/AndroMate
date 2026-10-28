package com.sudataxi.bridgemate
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.BroadcastReceiver
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
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class MainActivity : AppCompatActivity() {
private lateinit var statusText: TextView
private lateinit var logContainer: LinearLayout
private lateinit var btnSmsPerm: Button
private lateinit var btnNotiPerm: Button
private lateinit var btnBatteryPerm: Button
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
BridgeManager.activeActivity = this
setContentView(createDashboardView())
// 1. Firebase 실시간 엔진 초기화 (FirebaseApp 명시적 등록)
BridgeManager.initFirebase(this)
// 2. 권한 검사 및 상태 UI 업데이트
checkAllPermissions()
updatePermissionStatus()
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
private fun createDashboardView(): View {
val root = ScrollView(this).apply {
setBackgroundColor(Color.parseColor("#121212"))
isFillViewport = true
}
val mainLayout = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setPadding(48, 64, 48, 64)
}
val title = TextView(this).apply {
text = "수다택시 A25 브릿지 에이전트"
textSize = 22f
setTextColor(Color.WHITE)
typeface = Typeface.DEFAULT_BOLD
}
mainLayout.addView(title)
val subTitle = TextView(this).apply {
text = "아이폰 Firebase 실시간 원격 제어 데몬 (v1.2.1)"
textSize = 13f
setTextColor(Color.parseColor("#9E9E9E"))
setPadding(0, 8, 0, 32)
}
mainLayout.addView(subTitle)
val card = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setBackgroundColor(Color.parseColor("#1E1E1E"))
setPadding(40, 40, 40, 40)
}
statusText = TextView(this).apply {
text = "● Firebase 클라우드 브릿지 활성화됨"
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
val permTitle = TextView(this).apply {
text = "필수 권한 설정 상태"
textSize = 16f
setTextColor(Color.WHITE)
typeface = Typeface.DEFAULT_BOLD
setPadding(0, 48, 0, 16)
}
mainLayout.addView(permTitle)
btnSmsPerm = Button(this).apply {
text = "1. 문자 수발신 권한 확인 중..."
setBackgroundColor(Color.parseColor("#2C2C2C"))
setTextColor(Color.WHITE)
setOnClickListener { openAppSettings() }
}
mainLayout.addView(btnSmsPerm)
btnNotiPerm = Button(this).apply {
text = "2. 카카오T 알림 감지 권한"
setBackgroundColor(Color.parseColor("#2C2C2C"))
setTextColor(Color.WHITE)
val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
topMargin = 16
}
layoutParams = lp
setOnClickListener { openNotificationAccessSettings() }
}
mainLayout.addView(btnNotiPerm)
btnBatteryPerm = Button(this).apply {
text = "3. 백그라운드 절전 제외"
setBackgroundColor(Color.parseColor("#2C2C2C"))
setTextColor(Color.WHITE)
val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
topMargin = 16
}
layoutParams = lp
setOnClickListener { requestBatteryOptimizationExemption() }
}
mainLayout.addView(btnBatteryPerm)
val logTitle = TextView(this).apply {
text = "실시간 Firebase 통신 이벤트 로그"
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
private fun checkAllPermissions() {
val requiredPerms = mutableListOf<String>()
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
requiredPerms.add(Manifest.permission.RECEIVE_SMS)
}
if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
requiredPerms.add(Manifest.permission.SEND_SMS)
}
if (requiredPerms.isNotEmpty()) {
ActivityCompat.requestPermissions(this, requiredPerms.toTypedArray(), 1001)
}
}
fun updatePermissionStatus() {
// SMS 수신이나 발신 둘 중 하나라도 허용되어 있으면 활성화로 판정 (A25 실제 권한 상태와 일치시킴)
val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED ||
ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
btnSmsPerm.text = if (hasSms) "✔ 1. 문자 수발신 권한 [허용됨]" else "1. 문자 권한 설정 열기"
btnSmsPerm.setBackgroundColor(if (hasSms) Color.parseColor("#1B5E20") else Color.parseColor("#2C2C2C"))
val hasNoti = isNotificationServiceEnabled()
btnNotiPerm.text = if (hasNoti) "✔ 2. 카카오T 알림 감지 권한 [허용됨]" else "2. 카카오T 알림 감지 권한 허용하기"
btnNotiPerm.setBackgroundColor(if (hasNoti) Color.parseColor("#1B5E20") else Color.parseColor("#2C2C2C"))
val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
btnBatteryPerm.text = if (isIgnoringBattery) "✔ 3. 배터리 절전 제외 [완료됨]" else "3. 백그라운드 절전 제외 (상시 유지)"
btnBatteryPerm.setBackgroundColor(if (isIgnoringBattery) Color.parseColor("#1B5E20") else Color.parseColor("#2C2C2C"))
}
private fun openAppSettings() {
val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
data = Uri.parse("package:$packageName")
}
startActivity(intent)
}
private fun isNotificationServiceEnabled(): Boolean {
val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
return flat?.contains(packageName) == true
}
private fun openNotificationAccessSettings() {
val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
startActivity(intent)
}
@SuppressLint("BatteryLife")
private fun requestBatteryOptimizationExemption() {
try {
val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
data = Uri.parse("package:$packageName")
}
startActivity(intent)
} catch (_: Exception) {}
}
fun addLog(message: String) {
runOnUiThread {
val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
val logItem = TextView(this).apply {
text = "[$time]$message"
textSize = 12f
setTextColor(Color.parseColor("#ECEFF1"))
setPadding(0, 6, 0, 6)
}
logContainer.addView(logItem, 0)
}
}
}
object BridgeManager {
var activeActivity: MainActivity? = null
private const val FIREBASE_URL = "https://sudataxi-bridge-default-rtdb.firebaseio.com/"
private var isFirebaseInitialized = false
fun initFirebase(context: Context) {
if (isFirebaseInitialized) return
try {
// FirebaseApp이 초기화되어 있지 않다면 코드에서 직접 수동 초기화
if (FirebaseApp.getApps(context).isEmpty()) {
val options = FirebaseOptions.Builder()
.setApplicationId("com.sudataxi.bridgemate")
.setApiKey("AIzaSyDummyKeyForRestConnectionOnly")
.setDatabaseUrl(FIREBASE_URL)
.build()
FirebaseApp.initializeApp(context, options)
}
val db = FirebaseDatabase.getInstance(FIREBASE_URL)
isFirebaseInitialized = true
activeActivity?.addLog("🔥 Firebase Realtime DB 연결 성공!")
// 1. 유튜브 원격 명령 실시간 구독
db.getReference("sudataxi/commands/youtube").addValueEventListener(object : ValueEventListener {
private var lastTimestamp = 0L
override fun onDataChange(snapshot: DataSnapshot) {
val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: return
if (ts <= lastTimestamp) return
lastTimestamp = ts
val action = snapshot.child("action").getValue(String::class.java) ?: ""
when (action) {
"play" -> {
val query = snapshot.child("query").getValue(String::class.java) ?: ""
playYoutube(context, query)
}
"playlist" -> {
openYoutubePlaylist(context, "LL")
}
}
}
override fun onCancelled(error: DatabaseError) {}
})
// 2. 문자 발송 원격 명령 실시간 구독
db.getReference("sudataxi/commands/sms").addChildEventListener(object : ChildEventListener {
override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
val to = snapshot.child("to").getValue(String::class.java) ?: return
val message = snapshot.child("message").getValue(String::class.java) ?: return
val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
// 1분 이내의 새로운 명령만 처리
if (System.currentTimeMillis() - ts < 60000) {
sendSms(context, to, message)
snapshot.ref.removeValue() // 발송 후 명령 노드 정리
}
}
override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
override fun onChildRemoved(snapshot: DataSnapshot) {}
override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
override fun onCancelled(error: DatabaseError) {}
})
} catch (e: Exception) {
activeActivity?.addLog("❌ Firebase 초기화 실패: ${e.message}")
}
}
fun playYoutube(context: Context, query: String) {
try {
activeActivity?.addLog("📱 [아이폰 원격 명령] YouTube 실행: $query")
val uri = Uri.parse("vnd.youtube://results?search_query=" + Uri.encode(query))
val intent = Intent(Intent.ACTION_VIEW, uri).apply {
addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
} catch (_: Exception) {
val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).apply {
addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(webIntent)
}
}
private fun openYoutubePlaylist(context: Context, listId: String) {
try {
activeActivity?.addLog("📱 [아이폰 원격 명령] YouTube 좋아요 보관함 실행")
val uri = Uri.parse("vnd.youtube://playlist?list=$listId")
val intent = Intent(Intent.ACTION_VIEW, uri).apply {
addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
} catch (_: Exception) {}
}
fun sendSms(context: Context, phone: String, message: String) {
try {
activeActivity?.addLog("📤 [SMS 자동 발송] $phone:$message")
val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
context.getSystemService(SmsManager::class.java)
} else {
@Suppress("DEPRECATION")
SmsManager.getDefault()
}
smsManager.sendTextMessage(phone, null, message, null, null)
val db = FirebaseDatabase.getInstance(FIREBASE_URL)
val time = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
db.getReference("sudataxi/inbox/sms").push().setValue(
mapOf(
"sender" to phone,
"message" to message,
"type" to "sent",
"time" to time
)
)
Toast.makeText(context, "승객($phone)에게 문자 발송 완료", Toast.LENGTH_SHORT).show()
} catch (e: Exception) {
activeActivity?.addLog("❌ SMS 발송 실패: ${e.message}")
}
}
fun onSmsReceived(sender: String, message: String) {
activeActivity?.addLog("📩 [승객 문자 감지] $sender:$message")
try {
val db = FirebaseDatabase.getInstance(FIREBASE_URL)
val time = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
db.getReference("sudataxi/inbox/sms").push().setValue(
mapOf(
"sender" to sender,
"message" to message,
"type" to "received",
"time" to time
)
)
} catch (e: Exception) {
activeActivity?.addLog("❌ Firebase 전송 실패: ${e.message}")
}
}
fun onKakaoNotificationReceived(title: String, text: String) {
activeActivity?.addLog("🟡 [카카오T 감지] $title:$text")
try {
val db = FirebaseDatabase.getInstance(FIREBASE_URL)
val time = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
db.getReference("sudataxi/inbox/kakao").push().setValue(
mapOf(
"title" to title,
"text" to text,
"time" to time
)
)
} catch (e: Exception) {
activeActivity?.addLog("❌ Firebase 카카오 전송 실패: ${e.message}")
}
}
}
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
class KakaoNotificationService : NotificationListenerService() {
override fun onNotificationPosted(sbn: StatusBarNotification?) {
super.onNotificationPosted(sbn)
sbn ?: return
val pkg = sbn.packageName
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