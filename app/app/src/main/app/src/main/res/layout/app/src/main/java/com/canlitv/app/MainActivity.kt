package com.canlitv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.URLDecoder

@UnstableApi
class MainActivity : AppCompatActivity() {

    // Cloudflare Worker Listesinin Adresi - LÜTFEN KENDİ LİNKİNİZİ YAZIN
    private val PANEL_URL = "https://tv-listesi.senin-adın.workers.dev" 

    private lateinit var webView: WebView
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var isPlaying = false 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        playerView = findViewById(R.id.player_view)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.setBackgroundColor(0xFF000000.toInt())

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("go://")) {
                    val uri = Uri.parse(url)
                    val jsonString = uri.getQueryParameter("data")
                    if (jsonString != null) parseAndPlay(jsonString)
                    return true
                }
                return false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPlaying) {
                    stopPlayer()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        handleIntent(intent)

        if (!isPlaying) {
            webView.loadUrl(PANEL_URL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && "go" == data.scheme) {
            val jsonDataString = data.getQueryParameter("data")
            if (jsonDataString != null) {
                parseAndPlay(jsonDataString)
            }
        }
    }

    private fun parseAndPlay(jsonString: String) {
        try {
            val decodedJson = URLDecoder.decode(jsonString, "UTF-8")
            val jsonObject = JSONObject(decodedJson)
            val videoUrl = jsonObject.getString("url")
            val headersMap = mutableMapOf<String, String>()

            if (jsonObject.has("headers")) {
                val headersJson = jsonObject.getJSONObject("headers")
                val keys = headersJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = headersJson.getString(key)
                    headersMap[key] = value
                }
            }

            runOnUiThread {
                webView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
            }
            isPlaying = true
            startExoPlayer(videoUrl, headersMap)
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startExoPlayer(url: String, headers: Map<String, String>) {
        if (player != null) player?.release()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(headers["User-Agent"] ?: "CanliTV-App") 

        if (headers.isNotEmpty()) {
            dataSourceFactory.setDefaultRequestProperties(headers)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        playerView.player = player
        val mediaItem = MediaItem.fromUri(url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun stopPlayer() {
        player?.stop()
        player?.release()
        player = null
        playerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        isPlaying = false
    }
}
