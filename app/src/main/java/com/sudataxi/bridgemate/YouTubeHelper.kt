package com.sudataxi.bridgemate

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

class YouTubeHelper(private val context: Context) {

    var logCallback: ((String) -> Unit)? = null
    private fun log(msg: String) { logCallback?.invoke(msg) }

    fun handleCommand(map: Map<*, *>) {
        val timestamp = (map["timestamp"] as? Long) ?: 0L
        if (System.currentTimeMillis() - timestamp > 30000) return
        val action = (map["action"] as? String ?: "").lowercase()
        val videoId = map["videoId"] as? String ?: ""
        val url = map["url"] as? String ?: ""
        val query = map["query"] as? String ?: ""

        if (action == "search") {
            log("검색 명령 무시 (아이폰에서만 검색) query=" + query)
            return
        }

        when {
            action.contains("play") || action.contains("youtube") -> {
                var finalId = videoId
                if (finalId.isEmpty() && url.isNotEmpty()) {
                    finalId = extractVideoId(url)
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
            action == "volume" -> {
                val vol = (map["value"] as? Long)?.toInt() ?: 50
                setVolume(vol)
            }
        }
    }

    fun playYoutube(videoId: String) {
        if (videoId.length < 6) {
            log("잘못된 videoId 무시 (가짜 차단): " + videoId)
            return
        }
        val fakeIds = listOf("b7Lz3aF4V8U", "sIP_uU0X-w0", "_x1bK6n1v30", "jNQXAC9IVRw", "fJ9rUzIMcZQ", "9bZkp7q19f0", "kJQP7kiw5Fk")
        if (fakeIds.contains(videoId)) {
            log("가짜 더미 ID 차단: " + videoId)
            return
        }
        log("▶ 유튜브 재생 요청: " + videoId)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + videoId))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            log("✓ vnd.youtube:" + videoId + " 실행")
        } catch (e: Exception) {
            log("vnd 실패, https fallback: " + e.message)
            playYoutubeUrl("https://www.youtube.com/watch?v=" + videoId)
        }
    }

    fun playYoutubeUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            log("✓ " + url + " 실행")
        } catch (e: Exception) {
            log("URL 실행 실패: " + e.message)
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
            log("미디어키 실패: " + e.message)
        }
    }

    private fun setVolume(percent: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val vol = (max * percent / 100f).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
            log("🔊 볼륨 " + percent + "% 설정")
        } catch (e: Exception) {}
    }

    private fun extractVideoId(url: String): String {
        return try {
            val regex = "(?:v=|/)([0-9A-Za-z_-]{11})".toRegex()
            regex.find(url)?.groupValues?.get(1) ?: ""
        } catch (e: Exception) { "" }
    }
}