package com.messengerlite.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.messengerlite.App
import com.messengerlite.ProfileManager
import com.messengerlite.R
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    private lateinit var llProfiles: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tvUser = findViewById<TextView>(R.id.tv_settings_user)
        val tvServer = findViewById<TextView>(R.id.tv_settings_server)
        val etBio = findViewById<EditText>(R.id.et_bio)
        val btnSaveBio = findViewById<Button>(R.id.btn_save_bio)
        llProfiles = findViewById(R.id.ll_profiles)

        tvUser.text = "Logged in as: ${App.currentUser}"
        tvServer.text = App.getServerUrl()

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
                        Toast.makeText(this,
                            if (result.optString("status") == "success") "Bio updated"
                            else result.optString("error", "Failed"),
                            Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        refreshProfiles()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun refreshProfiles() {
        llProfiles.removeAllViews()
        val profiles = ProfileManager.getAllProfiles()
        val activeId = ProfileManager.getActiveProfileId()

        profiles.forEach { profile ->
            val isActive = profile.id == activeId

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(TextView(this).apply {
                text = profile.username
                textSize = 15f
                setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isActive) 0xFF7C4DFF.toInt() else 0xFFFFFFFF.toInt())
            })
            info.addView(TextView(this).apply {
                text = profile.serverUrl.removePrefix("https://").removePrefix("http://")
                textSize = 12f
                setTextColor(0x88FFFFFF.toInt())
            })
            if (isActive) {
                info.addView(TextView(this).apply {
                    text = "● Active"
                    textSize = 11f
                    setTextColor(0xFF7C4DFF.toInt())
                })
            }

            row.addView(info)

            if (!isActive) {
                row.addView(Button(this).apply {
                    text = "Remove"
                    textSize = 12f
                    setTextColor(0xFFFF5252.toInt())
                    setBackgroundColor(0x00000000.toInt())
                    setOnClickListener {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Remove Profile")
                            .setMessage("Remove ${profile.username} @ ${profile.serverUrl}?")
                            .setPositiveButton("Remove") { _, _ ->
                                ProfileManager.deleteProfile(profile.id)
                                refreshProfiles()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                })
            }

            card.addView(row)

            // Divider
            card.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.topMargin = 16 }
                setBackgroundColor(0x22FFFFFF.toInt())
            })

            llProfiles.addView(card)
        }

        // Add profile button
        llProfiles.addView(TextView(this).apply {
            text = "+ Add Another Profile"
            textSize = 14f
            setTextColor(0xFF7C4DFF.toInt())
            setPadding(0, 20, 0, 8)
            setOnClickListener {
                startActivity(
                    Intent(this@SettingsActivity, ServerSetupActivity::class.java)
                        .putExtra("mode", "add_profile")
                )
            }
        })
    }
}
