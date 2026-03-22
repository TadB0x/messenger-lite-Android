package com.messengerlite.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.messengerlite.App
import com.messengerlite.R
import kotlin.concurrent.thread

class ServerSetupActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var btnConnect: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_setup)

        App.init(this)

        etServer = findViewById(R.id.et_server)
        btnConnect = findViewById(R.id.btn_connect)
        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tv_status)

        // If server already saved, go straight to login (or auto-login)
        val savedUrl = App.getServerUrl()
        if (savedUrl.isNotEmpty()) {
            if (App.isLoggedIn()) {
                tryAutoLogin(savedUrl)
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            return
        }

        btnConnect.setOnClickListener { connect() }
    }

    private fun connect() {
        val input = etServer.text.toString().trim()
        if (input.isEmpty()) {
            tvStatus.text = "Please enter a server URL"
            return
        }
        val url = if (input.startsWith("http")) input else "https://$input"

        setLoading(true)
        tvStatus.text = "Connecting..."

        thread {
            try {
                App.api.setBaseUrl(url)
                val result = App.api.ping()
                if (result.optString("status") == "pong") {
                    // Save server URL
                    App.prefs.edit().putString("server_url", url).apply()
                    runOnUiThread {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    throw Exception("Server did not respond correctly")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    tvStatus.text = "Could not connect: ${e.message}"
                }
            }
        }
    }

    private fun tryAutoLogin(url: String) {
        setLoading(true)
        tvStatus.text = "Restoring session..."
        thread {
            try {
                App.api.setBaseUrl(url)
                val token = App.api.getAuthToken() ?: throw Exception("No token")
                val result = App.api.restoreSession(token)
                if (result.optString("status") == "success") {
                    runOnUiThread {
                        startActivity(Intent(this, ChatListActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnConnect.isEnabled = !loading
    }
}
