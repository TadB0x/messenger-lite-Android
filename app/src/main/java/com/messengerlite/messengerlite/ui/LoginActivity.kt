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

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvServer: TextView
    private var addProfileMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        addProfileMode = intent.getStringExtra("mode") == "add_profile"

        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)
        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tv_status)
        tvServer = findViewById(R.id.tv_server)

        tvServer.text = App.getServerUrl()
        tvServer.setOnClickListener {
            App.prefs.edit().remove("server_url").apply()
            val intent = Intent(this, ServerSetupActivity::class.java)
            if (addProfileMode) intent.putExtra("mode", "add_profile")
            startActivity(intent)
            finish()
        }

        if (addProfileMode) {
            title = "Add Profile"
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        btnLogin.setOnClickListener { doAuth(register = false) }
        btnRegister.setOnClickListener { doAuth(register = true) }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun doAuth(register: Boolean) {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            tvStatus.text = "Username and password are required"
            return
        }

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
                    App.saveLogin(username, token, App.getServerUrl())
                    runOnUiThread {
                        // Always go to ChatListActivity, clear back stack so switching feels clean
                        val intent = Intent(this, ChatListActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
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
}
