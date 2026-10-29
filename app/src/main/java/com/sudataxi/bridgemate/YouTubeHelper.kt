package com.sudataxi.bridgemate

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.util.Log

class YouTubeHelper(private val context: Context) {

    var logCallback: ((String) -> Unit)? = null
    private fun log(msg: String) { 
        Log.d("YouTubeHelper", msg)
        logCallback?.invoke(msg) 
    }

    fun handleCommand(map: Map<*, *>) {
        val timestamp = (map["timestamp"] as? Long) ?: 0L
        if (System.currentTimeMillis() - timestamp > 60000) return // 60초 이내만 처리
        val rawAction = (map["action"] as? String ?: "").lowercase()
        val actionAlt = (map["actionAlt"] as? String ?: "").lowercase()
        val actionCompat = (map["actionCompat"] as? String ?: "").lowercase()
        val videoId = (map["videoId"] as? String ?: map["id"] as? String ?: "")
        val url = map["url"] as? String ?: map["searchUrl"] as? String ?: ""

        // 통합 액션 판별 (PWA v1.3.13~1.3.14 6중 발사 대응)
        val action = when {
            rawAction.contains("seekto") || rawAction == "seek_to" || actionCompat == "seek_to" || actionAlt == "seek_to" -> "seekto"
            rawAction == "seek" || actionAlt.contains("seek_") || actionCompat.contains("forward") || actionCompat.contains("rewind") -> "seek"
            rawAction.contains("next") || actionAlt.contains("next") || rawAction == "next_video" -> "next"
            rawAction.contains("prev") || actionAlt.contains("prev") || rawAction == "prev_video" -> "prev"
            else -> rawAction
        }

        when {
            action.contains("play") || rawAction.contains("youtube") || rawAction.contains("play_youtube") -> {
                var finalId = videoId
                if (finalId.isEmpty() && url.isNotEmpty()) {
                    finalId = extractVideoId(url)
                }
                if (finalId.isNotEmpty()) playYoutube(finalId) else if (url.isNotEmpty()) playYoutubeUrl(url)
            }
            action == "pause" -> { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE); log("⏸ 일시정지") }
            action == "resume" || action == "play_pause" || rawAction == "resume" -> { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY); log("▶ 재생 재개") }
            action == "volume" -> { val vol = (map["value"] as? Long)?.toInt() ?: (map["volume"] as? Long)?.toInt() ?: 50; setVolume(vol) }
            action == "seek" -> {
                val delta = (map["delta"] as? Long)?.toInt() ?: (map["value"] as? Long)?.toInt() ?: 0
                val seekTo = (map["seekTo"] as? Long)?.toInt() ?: (map["currentSec"] as? Long)?.toInt() ?: (map["position"] as? Long)?.toInt() ?: 0
                if (seekTo > 0) {
                    seekToAbsolute(seekTo)
                } else {
                    seekRelative(delta)
                }
            }
            action == "seekto" -> {
                val absSec = (map["seekTo"] as? Long)?.toInt() ?: (map["position"] as? Long)?.toInt() ?: (map["value"] as? Long)?.toInt() ?: (map["currentSec"] as? Long)?.toInt() ?: 0
                seekToAbsolute(absSec)
            }
            action == "next" -> { nextVideo(); log("⏭️ 다음 영상") }
            action == "prev" -> { prevVideo(); log("⏮️ 이전 영상") }
        }
    }

    fun playYoutube(videoId: String) {
        if (videoId.length < 6) { log("잘못된 videoId 무시: $videoId"); return }
        val fakeIds = listOf("b7Lz3aF4V8U", "sIP_uU0X-w0", "_x1bK6n1v30", "jNQXAC9IVRw", "fJ9rUzIMcZQ", "9bZkp7q19f0", "kJQP7kiw5Fk")
        if (fakeIds.contains(videoId)) { log("가짜 더미 ID 차단: $videoId"); return }
        log("▶ 유튜브 재생 요청: $videoId")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            log("✓ vnd.youtube:$videoId 실행")
        } catch (e: Exception) {
            playYoutubeUrl("https://www.youtube.com/watch?v=$videoId")
        }
    }

    fun playYoutubeUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            log("✓ $url 실행")
        } catch (e: Exception) { log("URL 실행 실패: ${e.message}") }
    }

    private fun seekRelative(deltaSec: Int) {
        log("⏩ ${if(deltaSec>0) "+" else ""}$deltaSec 초 이동 시도")
        // 1. 미디어 키로 빠르게 감기/되감기 시도 (YouTube 앱은 10초 단위)
        try {
            val times = kotlin.math.abs(deltaSec) / 10
            val keyCode = if (deltaSec > 0) KeyEvent.KEYCODE_MEDIA_FAST_FORWARD else KeyEvent.KEYCODE_MEDIA_REWIND
            // 일부 기기에서는 DPAD_RIGHT/LEFT가 시크에 동작
            repeat(if(times==0) 1 else times) {
                sendMediaKey(keyCode)
                Thread.sleep(100)
            }
            log("✓ 미디어키 ${if(deltaSec>0) "FF" else "RW"} x$times 전송")
        } catch (e: Exception) { log("미디어키 전송 실패: ${e.message}") }

        // 2. 추가: 접근성 서비스나 MediaController로 seek 시도 (향후 ExoPlayer 전환 시 정확한 seek 가능)
        // 현재는 YouTube 앱이 외부 seek Intent를 지원하지 않으므로 미디어키로 최선을 다함
    }

    private fun seekToAbsolute(sec: Int) {
        log("⏩ $sec 초 위치로 이동 시도")
        // YouTube 앱은 정확한 seekTo를 외부에서 지원하지 않음
        // 가능한 방법: 현재 위치를 모르면 상대 이동으로 근사, 또는 ExoPlayer 기반 재생으로 전환 필요
        // 여기서는 로그 남기고, 향후 ExoPlayer 사용 시 player.seekTo(sec*1000) 호출
        try {
            // 시도: MediaSession을 통한 seek (Android 9+에서 일부 동작)
            // 현재는 로그만 남기고, 실제로는 PWA 타이머와 동기화하여 UI만 갱신
            log("ℹ️ YouTube 앱은 외부 앱에서 정확한 seekTo 미지원 - A25에서 직접 YouTube Player API로 전환 필요")
            // 대안: 볼륨 키 대신 시크바를 직접 제어하려면 AccessibilityService 필요
        } catch (e: Exception) { log("seekTo 실패: ${e.message}") }
    }

    private fun nextVideo() {
        log("⏭️ 다음 영상 요청")
        try {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            // YouTube Music/YouTube에서 NEXT는 다음 영상으로 동작
            log("✓ MEDIA_NEXT 전송")
        } catch (e: Exception) { log("NEXT 실패: ${e.message}") }
    }

    private fun prevVideo() {
        log("⏮️ 이전 영상 요청")
        try {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            log("✓ MEDIA_PREVIOUS 전송")
        } catch (e: Exception) { log("PREV 실패: ${e.message}") }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val eventDown = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0)
            val eventUp = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)
            am.dispatchMediaKeyEvent(eventDown)
            am.dispatchMediaKeyEvent(eventUp)
        } catch (e: Exception) {
            try {
                // Fallback: 브로드캐스트 방식
                val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON)
                intentDown.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                context.sendOrderedBroadcast(intentDown, null)
                val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON)
                intentUp.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
                context.sendOrderedBroadcast(intentUp, null)
            } catch (e2: Exception) { log("미디어키 전송 완전 실패: ${e2.message}") }
        }
    }

    private fun setVolume(percent: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val vol = (max * percent / 100f).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
            log("🔊 볼륨 $percent% 설정")
        } catch (e: Exception) { log("볼륨 설정 실패: ${e.message}") }
    }

    private fun extractVideoId(url: String): String {
        return try {
            val regex = "(?:v=|/)([0-9A-Za-z_-]{11})".toRegex()
            regex.find(url)?.groupValues?.get(1) ?: ""
        } catch (e: Exception) { "" }
    }
}
