package com.messengerlite.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.messengerlite.App
import com.messengerlite.R
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tvUser = findViewById<TextView>(R.id.tv_settings_user)
        val tvServer = findViewById<TextView>(R.id.tv_settings_server)
        val etBio = findViewById<EditText>(R.id.et_bio)
        val btnSaveBio = findViewById<Button>(R.id.btn_save_bio)

        tvUser.text = "Logged in as: ${App.currentUser}"
        tvServer.text = "Server: ${App.getServerUrl()}"

        // Load profile
        thread {
            try {
                val profile = App.api.getProfile(App.currentUser)
                if (profile.optString("status") == "success") {
                    val bio = profile.optString("bio", "")
                    runOnUiThread { etBio.setText(bio) }
                }
            } catch (_: Exception) {}
        }

        btnSaveBio.setOnClickListener {
            val bio = etBio.text.toString().trim()
            thread {
                try {
                    val result = App.api.updateProfile(bio = bio)
                    runOnUiThread {
                        if (result.optString("status") == "success") {
                            Toast.makeText(this, "Bio updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, result.optString("error", "Failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
