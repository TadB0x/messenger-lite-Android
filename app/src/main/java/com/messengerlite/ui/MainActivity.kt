package com.messengerlite.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.messengerlite.R

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var setupScreen: LinearLayout
    private lateinit var serverUrlInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var setupError: TextView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var serverUrl: String = ""

    companion object {
        private const val PREFS = "messenger_prefs"
        private const val KEY_URL = "server_url"
        private const val FILE_PICK = 101
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        webView = findViewById(R.id.webview)
        progress = findViewById(R.id.progress)
        setupScreen = findViewById(R.id.setupScreen)
        serverUrlInput = findViewById(R.id.serverUrlInput)
        connectBtn = findViewById(R.id.connectBtn)
        setupError = findViewById(R.id.setupError)

        setupWebView()

        // Check if we already have a saved URL
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_URL, null)

        if (!savedUrl.isNullOrBlank()) {
            serverUrl = savedUrl
            showWebView()
            if (savedInstanceState != null) {
                webView.restoreState(savedInstanceState)
            } else {
                webView.loadUrl(serverUrl)
            }
        } else {
            showSetupScreen()
        }

        connectBtn.setOnClickListener { onConnect() }

        serverUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onConnect()
                true
            } else false
        }
    }

    private fun onConnect() {
        var url = serverUrlInput.text.toString().trim()
        if (url.isBlank()) {
            showError("Please enter a server URL")
            return
        }
        // Add https:// if no scheme
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        // Remove trailing slash
        url = url.trimEnd('/')

        serverUrl = url
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, serverUrl).apply()

        showWebView()
        webView.loadUrl(serverUrl)
    }

    private fun showSetupScreen() {
        setupScreen.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun showWebView() {
        setupScreen.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun showError(msg: String) {
        setupError.text = msg
        setupError.visibility = View.VISIBLE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("wv", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val reqUrl = request.url.toString()
                // Keep same-host navigation inside the app
                if (serverUrl.isNotBlank()) {
                    val host = Uri.parse(serverUrl).host ?: ""
                    if (reqUrl.contains(host)) return false
                }
                // Open external links in browser
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl)))
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(null, errorPage(), "text/html", "UTF-8", null)
                    progress.visibility = View.GONE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progress.visibility = View.VISIBLE
                    progress.progress = newProgress
                } else {
                    progress.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = callback
                startActivityForResult(
                    Intent.createChooser(params.createIntent(), "Select File"),
                    FILE_PICK
                )
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
                webView.goBack()
                return true
            }
            // If on webview but can't go back, go to setup screen
            if (webView.visibility == View.VISIBLE) {
                showSetupScreen()
                serverUrlInput.setText(serverUrl)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICK) {
            fileUploadCallback?.onReceiveValue(
                if (resultCode == Activity.RESULT_OK && data != null)
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                else null
            )
            fileUploadCallback = null
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    fun changeServer() {
        serverUrl = ""
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_URL).apply()
        webView.loadUrl("about:blank")
        showSetupScreen()
        serverUrlInput.setText("")
    }

    private fun errorPage() = """
        <html>
        <head><meta name='viewport' content='width=device-width,initial-scale=1'>
        <style>
          body { background:#09090b; color:#fafafa; font-family:-apple-system,system-ui,sans-serif;
                 display:flex; flex-direction:column; align-items:center;
                 justify-content:center; height:100vh; margin:0; text-align:center; padding:24px; }
          h2 { color:#7c3aed; margin-bottom:12px; }
          p  { color:#a1a1aa; font-size:14px; line-height:1.6; }
          .btns { display:flex; gap:12px; margin-top:24px; }
          button { background:#7c3aed; color:#fafafa; border:none;
                   padding:14px 28px; border-radius:10px; font-size:15px; cursor:pointer; }
          .secondary { background:#27272a; }
        </style></head>
        <body>
          <h2>No Connection</h2>
          <p>Could not reach the server.<br>Check your internet connection and try again.</p>
          <div class='btns'>
            <button onclick="location.href='$serverUrl'">Retry</button>
            <button class='secondary' onclick="Android.changeServer()">Change Server</button>
          </div>
        </body></html>
    """.trimIndent()
}
