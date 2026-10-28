package com.sudataxi

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/**
 * 수다택시 A25 브릿지 - YouTubeHelper v1.3.5
 * 원칙: 검색 실패 시 가짜 데이터 생성 금지, 검색 명령은 무시하고 재생만 처리
 */
class YouTubeHelper(private val context: Context) {

    // 로그 콜백 - MainActivity에서 연결됨
    var logCallback: ((String) -> Unit)? = null

    private fun log(msg: String) {
        logCallback?.invoke(msg)
    }

    /**
     * Firebase에서 넘어온 유튜브 명령 처리
     * PWA v1.3.5에서 보내는 payload를 처리함
     */
    fun handleCommand(map: Map<*, *>) {
        val timestamp = (map["timestamp"] as? Long) ?: 0L
        // 30초 이상 오래된 명령 무시 - 재부팅 시 중복 재생 방지
        if (System.currentTimeMillis() - timestamp > 30000) return

        val action = (map["action"] as? String ?: "").lowercase()
        val videoId = map["videoId"] as? String ?: ""
        val url = map["url"] as? String ?: ""
        val query = map["query"] as? String ?: ""

        // v1.3.5 핵심: search는 A25에서 처리하지 않음
        // 아이폰에서만 검색하고 A25는 재생만 - 카카오T 가림 방지 + 가짜 데이터 방지
        if (action == "search") {
            log("검색 명령 무시 (아이폰에서만 검색) query=$query")
            return
        }

        when {
            action.contains("play") || action.contains("youtube") -> {
                val finalId = when {
                    videoId.isNotEmpty() -> videoId
                    url.isNotEmpty() -> extractVideoId(url)
                    else -> ""
                }
                if (finalId.isNotEmpty()) {
                    playYoutube(finalId)
                } else if (url.isNotEmpty()) {
                    playYoutubeUrl(url)
                } else {
                    log("재생 요청 무시 - videoId 없음")
                }
            }
            action == "pause" -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                log("⏸ 일시정지")
            }
            action == "resume" || action == "play_pause" -> {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                log("▶ 재생 재개")
            }
            action == "seek" -> {
                val sec = (map["value"] as? Long) ?: (map["currentSec"] as? Long) ?: 0L
                log("⏩ ${sec}초 이동 요청")
            }
            action == "volume" -> {
                val vol = (map["value"] as? Long)?.toInt() ?: 50
                setVolume(vol)
            }
        }
    }

    /**
     * 실제 유튜브 재생 - 가짜 ID는 차단
     */
    fun playYoutube(videoId: String) {
        // v1.3.5: 6자 미만은 가짜 ID로 간주하고 무시
        if (videoId.length < 6) {
            log("잘못된 videoId 무시 (가짜 차단): $videoId")
            return
        }
        // 이전 가짜 더미 ID들 하드 차단
        val fakeIds = listOf("b7Lz3aF4V8U", "sIP_uU0X-w0", "_x1bK6n1v30", "jNQXAC9IVRw", "fJ9rUzIMcZQ")
        if (fakeIds.contains(videoId)) {
            log("가짜 더미 ID 차단: $videoId")
            return
        }

        log("▶ 유튜브 재생 요청: $videoId")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            log("✓ vnd.youtube:$videoId 실행")
        } catch (e: Exception) {
            log("vnd 실패, https fallback: ${e.message}")
            playYoutubeUrl("https://www.youtube.com/watch?v=$videoId")
        }
    }

    fun playYoutubeUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            log("✓ $url 실행")
        } catch (e: Exception) {
            log("URL 실행 실패: ${e.message}")
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            am.dispatchMediaKeyEvent(downEvent)
            am.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            log("미디어키 실패: ${e.message}")
        }
    }

    private fun setVolume(percent: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val vol = (max * percent / 100f).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
            log("🔊 볼륨 $percent% 설정")
        } catch (e: Exception) {
            log("볼륨 설정 실패")
        }
    }

    private fun extractVideoId(url: String): String {
        return try {
            val regex = "(?:v=|/)([0-9A-Za-z_-]{11})".toRegex()
            regex.find(url)?.groupValues?.get(1) ?: ""
        } catch (e: Exception) { "" }
    }
}