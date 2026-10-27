package com.sudataxi.bridgemate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sudataxi.bridgemate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initWebView()
        initListeners()
    }

    private fun initWebView() {
        binding.webView.apply {
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            // PWA / 웹 페이지 주소
            loadUrl("https://apoco211.github.io/AndroMate/")
        }
    }

    private fun initListeners() {
        // 기존에 `setOnClickListener = { ... }`로 작성되어 발생한 에러를 수정했습니다.
        binding.btnRefresh?.setOnClickListener {
            binding.webView.reload()
            Toast.makeText(this, "새로고침", Toast.LENGTH_SHORT).show()
        }

        binding.btnHome?.setOnClickListener {
            binding.webView.loadUrl("https://apoco211.github.io/AndroMate/")
        }

        binding.btnYoutube?.setOnClickListener {
            launchYouTube()
        }
    }

    private fun launchYouTube() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:"))
            startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            startActivity(webIntent)
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
