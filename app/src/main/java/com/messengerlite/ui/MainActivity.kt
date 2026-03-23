package com.messengerlite.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.messengerlite.R

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val URL = "https://baharestan11.ir/nana"
        private const val FILE_PICK = 101
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        webView = findViewById(R.id.webview)
        progress = findViewById(R.id.progress)

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
                val url = request.url.toString()
                // Keep same-host navigation inside the app
                if (url.contains("baharestan11.ir")) {
                    return false
                }
                // Open external links in browser
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

        // Restore state or load URL
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
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

    private fun errorPage() = """
        <html>
        <head><meta name='viewport' content='width=device-width,initial-scale=1'>
        <style>
          body { background:#121212; color:#fff; font-family:sans-serif;
                 display:flex; flex-direction:column; align-items:center;
                 justify-content:center; height:100vh; margin:0; text-align:center; padding:24px; }
          h2 { color:#7C4DFF; margin-bottom:12px; }
          p  { color:#aaa; font-size:14px; line-height:1.6; }
          button { margin-top:24px; background:#7C4DFF; color:#fff; border:none;
                   padding:14px 32px; border-radius:8px; font-size:16px; cursor:pointer; }
        </style></head>
        <body>
          <h2>No Connection</h2>
          <p>Could not reach the server.<br>Check your internet connection and try again.</p>
          <button onclick="location.href='$URL'">Retry</button>
        </body></html>
    """.trimIndent()
}
