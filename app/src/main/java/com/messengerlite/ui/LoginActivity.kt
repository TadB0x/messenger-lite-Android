package com.messengerlite.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.messengerlite.App
import com.messengerlite.R
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        App.init(this)

        etServer = findViewById(R.id.et_server)
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)
        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tv_status)

        // Restore saved server URL
        val savedUrl = App.getServerUrl()
        if (savedUrl.isNotEmpty()) {
            etServer.setText(savedUrl)
        }

        // Try auto-login
        if (App.isLoggedIn()) {
            etServer.setText(App.getServerUrl())
            etUsername.setText(App.currentUser)
            autoLogin()
        }

        btnLogin.setOnClickListener { doAuth(register = false) }
        btnRegister.setOnClickListener { doAuth(register = true) }
    }

    private fun autoLogin() {
        setLoading(true)
        tvStatus.text = "Restoring session..."
        thread {
            try {
                val token = App.api.getAuthToken() ?: throw Exception("No token")
                val result = App.api.restoreSession(token)
                if (result.optString("status") == "success") {
                    runOnUiThread { goToChats() }
                } else {
                    runOnUiThread {
                        setLoading(false)
                        tvStatus.text = "Session expired, please login again"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    tvStatus.text = "Could not restore session"
                }
            }
        }
    }

    private fun doAuth(register: Boolean) {
        val server = etServer.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            tvStatus.text = "All fields are required"
            return
        }

        val url = if (server.startsWith("http")) server else "https://$server"
        App.api.setBaseUrl(url)

        setLoading(true)
        tvStatus.text = if (register) "Registering..." else "Logging in..."

        thread {
            try {
                if (register) {
                    val regResult = App.api.register(username, password)
                    if (regResult.optString("status") != "success") {
                        throw Exception(regResult.optString("error", "Registration failed"))
                    }
                }
                val loginResult = App.api.login(username, password)
                if (loginResult.optString("status") == "success") {
                    val token = loginResult.optString("token", "")
                    App.saveLogin(username, token, url)
                    runOnUiThread { goToChats() }
                } else {
                    throw Exception(loginResult.optString("error", "Login failed"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    tvStatus.text = e.message ?: "Error"
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnRegister.isEnabled = !loading
    }

    private fun goToChats() {
        startActivity(Intent(this, ChatListActivity::class.java))
        finish()
    }
}
