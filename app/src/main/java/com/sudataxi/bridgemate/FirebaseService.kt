
package com.sudataxi.bridgemate

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val action = message.data["action"]
        val number = message.data["number"]
        val text = message.data["text"]
        val query = message.data["query"]
        val videoId = message.data["videoId"]
        when (action) {
            "send_sms" -> if (!number.isNullOrBlank() && !text.isNullOrBlank()) SmsHelper().sendImmediate(this, number, text)
            "youtube_search" -> if (!query.isNullOrBlank()) YouTubeHelper().searchYouTube(this, query)
            "youtube_play" -> {
                if (!videoId.isNullOrBlank()) YouTubeHelper().playVideo(this, videoId)
                else if (!query.isNullOrBlank()) YouTubeHelper().searchYouTube(this, query)
            }
            "youtube_launch" -> YouTubeHelper().launchYouTube(this)
        }
    }
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FirebaseService", "New token: " + token)
    }
}
