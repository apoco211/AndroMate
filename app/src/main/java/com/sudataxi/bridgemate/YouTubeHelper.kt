
package com.sudataxi.bridgemate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class YouTubeHelper {
    fun launchYouTube(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (intent != null) context.startActivity(intent)
            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")))
        } catch (e: Exception) {
            Toast.makeText(context, "유튜브 실행 실패", Toast.LENGTH_SHORT).show()
        }
    }
    fun launchYouTubeMusic(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
            if (intent != null) context.startActivity(intent)
            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/")))
        } catch (e: Exception) {
            Toast.makeText(context, "뮤직 실행 실패", Toast.LENGTH_SHORT).show()
        }
    }
    fun searchYouTube(context: Context, query: String) {
        val encoded = Uri.encode(query)
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + encoded)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) }
        catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(webIntent)
        }
    }
    fun playVideo(context: Context, videoId: String) {
        val uri = Uri.parse("vnd.youtube:" + videoId)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { context.startActivity(intent) }
        catch (e: Exception) {
            val webUri = Uri.parse("https://www.youtube.com/watch?v=" + videoId)
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
