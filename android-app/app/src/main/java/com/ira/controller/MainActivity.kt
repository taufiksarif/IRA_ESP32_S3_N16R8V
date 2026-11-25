package com.ira.controller

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val DEFAULT_CONTROLLER_ADDRESS = "10.10.10.10"
private const val PREFS_NAME = "ira-app"
private const val PREF_ROUTER_ADDRESS = "controller-router-address"
private const val PROBE_TIMEOUT_MS = 2500
private const val PROBE_RETRY_MS = 3000L
private val BACKGROUND_COLOR = android.graphics.Color.rgb(4, 27, 19)

class MainActivity : ComponentActivity() {
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var controllerWifiNetwork: Network? = null
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var dashboard: WebView
    @Volatile private var controllerAddress = DEFAULT_CONTROLLER_ADDRESS
    @Volatile private var showingDemo = true
    private var probeInFlight = false
    private var clearHistoryOnNextPage = false
    // Retries only while the app is visible; onResume probes again when the user comes back.
    private val retryProbe = Runnable {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controllerWifiNetwork?.let(::loadLiveDashboardIfReachable)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BACKGROUND_COLOR
        window.navigationBarColor = BACKGROUND_COLOR

        dashboard = WebView(this).apply {
            setBackgroundColor(BACKGROUND_COLOR)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webChromeClient = WebChromeClient()
            addJavascriptInterface(AnalyzerDownloadBridge(applicationContext), "IraAndroid")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !request.url.toString().startsWith("http://$controllerAddress/")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (clearHistoryOnNextPage) {
                        clearHistoryOnNextPage = false
                        view.clearHistory()
                    }
                    installAnalyzerDownloads(view)
                    installMobileStopButtonLayout(view)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !showingDemo) {
                        showDemoDashboard()
                        scheduleProbeRetry()
                    }
                }
            }
        }
        val appRoot = FrameLayout(this).apply {
            setBackgroundColor(BACKGROUND_COLOR)
            addView(
                dashboard,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
        // Edge-to-edge is enforced on Android 15+, so the keyboard must be handled explicitly.
        ViewCompat.setOnApplyWindowInsetsListener(appRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        setContentView(appRoot)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (dashboard.canGoBack()) {
                    dashboard.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        showDemoDashboard()
        registerControllerNetwork()
    }

    override fun onResume() {
        super.onResume()
        dashboard.onResume()
        // Try again right away when the user returns (for example from Wi-Fi settings).
        if (showingDemo) controllerWifiNetwork?.let(::loadLiveDashboardIfReachable)
    }

    override fun onPause() {
        dashboard.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(retryProbe)
        wifiNetworkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        wifiNetworkCallback = null
        connectivityManager.bindProcessToNetwork(null)
        controllerWifiNetwork = null
        dashboard.removeJavascriptInterface("IraAndroid")
        dashboard.destroy()
        super.onDestroy()
    }

    private fun registerControllerNetwork() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    controllerWifiNetwork = network
                    loadLiveDashboardIfReachable(network)
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    if (controllerWifiNetwork == network) {
                        mainHandler.removeCallbacks(retryProbe)
                        connectivityManager.bindProcessToNetwork(null)
                        controllerWifiNetwork = null
                        showDemoDashboard()
                    }
                }
            }
        }
        try {
            connectivityManager.requestNetwork(request, callback)
            wifiNetworkCallback = callback
        } catch (_: SecurityException) {
            controllerWifiNetwork = null
        }
    }

    private fun scheduleProbeRetry() {
        mainHandler.removeCallbacks(retryProbe)
        mainHandler.postDelayed(retryProbe, PROBE_RETRY_MS)
    }

    /** Must be called on the main thread. Probes the AP address and the last known router address. */
    private fun loadLiveDashboardIfReachable(network: Network) {
        if (probeInFlight || !showingDemo) return
        probeInFlight = true
        mainHandler.removeCallbacks(retryProbe)
        val candidates = listOfNotNull(
            DEFAULT_CONTROLLER_ADDRESS,
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_ROUTER_ADDRESS, null),
        ).map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        Thread {
            var reachableAddress: String? = null
            var routerAddress: String? = null
            for (address in candidates) {
                val status = probeStatus(network, address) ?: continue
                reachableAddress = address
                routerAddress = status.optString("staIp", "").takeIf {
                    status.optBoolean("staConnected") && it.isNotBlank() && it != "0.0.0.0"
                }
                break
            }
            runOnUiThread {
                probeInFlight = false
                if (controllerWifiNetwork != network || !showingDemo) return@runOnUiThread
                if (reachableAddress == null) {
                    scheduleProbeRetry()
                    return@runOnUiThread
                }
                if (routerAddress != null) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(PREF_ROUTER_ADDRESS, routerAddress).apply()
                }
                connectivityManager.bindProcessToNetwork(network)
                controllerAddress = reachableAddress
                showingDemo = false
                dashboard.tag = "$reachableAddress|live"
                clearHistoryOnNextPage = true
                dashboard.loadUrl("http://$reachableAddress/")
            }
        }.start()
    }

    private fun probeStatus(network: Network, address: String): JSONObject? = runCatching {
        (network.openConnection(URL("http://$address/api/status?compact=1")) as HttpURLConnection).run {
            connectTimeout = PROBE_TIMEOUT_MS
            readTimeout = PROBE_TIMEOUT_MS
            useCaches = false
            try {
                if (responseCode != 200) null
                else JSONObject(inputStream.bufferedReader().use { it.readText() })
            } finally {
                disconnect()
            }
        }
    }.getOrNull()

    private fun showDemoDashboard() {
        if (!::dashboard.isInitialized) return
        showingDemo = true
        controllerAddress = DEFAULT_CONTROLLER_ADDRESS
        val renderKey = "$DEFAULT_CONTROLLER_ADDRESS|demo"
        if (dashboard.tag == renderKey) return
        dashboard.tag = renderKey
        dashboard.post {
            if (!showingDemo || dashboard.tag != renderKey) return@post
            clearHistoryOnNextPage = true
            dashboard.loadDataWithBaseURL(
                "http://$DEFAULT_CONTROLLER_ADDRESS/",
                createDemoPageHtml(),
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    private fun createDemoPageHtml(): String = runCatching {
        val html = assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val mock = assets.open("demo-mode.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val charsetTag = "<meta charset=\"utf-8\">"
        check(html.contains(charsetTag)) { "Dashboard HTML is missing its UTF-8 declaration" }
        html.replace(charsetTag, "$charsetTag<script>$mock</script>")
    }.getOrElse { error ->
        "<!doctype html><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><body style=\"margin:0;padding:24px;background:#041b13;color:#eafff1;font:16px system-ui\"><h1>IRA demo unavailable</h1><p>${error.message ?: "Could not load the packaged dashboard."}</p></body>"
    }
}

private class AnalyzerDownloadBridge(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun saveFile(fileName: String, base64Data: String, mimeType: String) {
        val result = runCatching {
            val safeName = File(fileName).name.ifBlank { "ira-analyzer-export.txt" }
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "text/plain" })
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create the download")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Could not write the download")
                context.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
                "Analyzer export saved to Downloads: $safeName"
            } else {
                // Android 9 and older: public Downloads needs a storage permission, so use the app's own folder.
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: error("Storage is unavailable")
                val file = File(directory, safeName)
                file.writeBytes(bytes)
                "Analyzer export saved to ${file.absolutePath}"
            }
        }
        val message = result.getOrElse { it.message ?: "Could not save analyzer export" }
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}

private fun installAnalyzerDownloads(webView: WebView) {
    val script = """
        (function() {
          if (window.__iraAndroidDownloads) return;
          window.__iraAndroidDownloads = true;
          document.addEventListener('click', function(event) {
            var link = event.target && event.target.closest ? event.target.closest('a[download]') : null;
            if (!link || !link.href || !link.href.startsWith('blob:')) return;
            event.preventDefault();
            fetch(link.href).then(function(response) { return response.blob(); }).then(function(blob) {
              var reader = new FileReader();
              reader.onloadend = function() {
                var base64 = String(reader.result).split(',')[1] || '';
                window.IraAndroid.saveFile(link.download || 'ira-analyzer-export.txt', base64, blob.type || 'text/plain');
              };
              reader.readAsDataURL(blob);
            });
          }, true);
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}

private fun installMobileStopButtonLayout(webView: WebView) {
    val script = """
        (function() {
          var styleId = 'ira-android-mobile-stop-layout';
          var css = '@media (max-width:620px) {' +
            'main { padding-bottom:clamp(14px,3vw,28px) !important; }' +
            '#stopSeq { position:static !important; left:auto !important; right:auto !important; bottom:auto !important; z-index:auto !important; width:100% !important; min-height:48px; grid-column:1 / -1 !important; border-radius:16px !important; }' +
            '} @media (max-width:420px) { main { padding-bottom:12px !important; } }';
          var style = document.getElementById(styleId);
          if (!style) {
            style = document.createElement('style');
            style.id = styleId;
            (document.head || document.documentElement).appendChild(style);
          }
          style.textContent = css;
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}
