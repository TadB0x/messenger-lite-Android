package com.messengerlite

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class UserProfile(
    val id: String,
    val serverUrl: String,
    val username: String,
    val authToken: String
)

object ProfileManager {

    private lateinit var prefs: SharedPreferences

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
    }

    fun getAllProfiles(): List<UserProfile> {
        val raw = prefs.getString("profiles", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).toProfile() }
        } catch (_: Exception) { emptyList() }
    }

    fun getActiveProfileId(): String? = prefs.getString("active_profile_id", null)

    fun getActiveProfile(): UserProfile? {
        val activeId = getActiveProfileId() ?: return null
        return getAllProfiles().find { it.id == activeId }
    }

    fun saveProfile(serverUrl: String, username: String, authToken: String): UserProfile {
        val id = makeId(serverUrl, username)
        val profile = UserProfile(id, serverUrl.trimEnd('/'), username, authToken)
        val profiles = getAllProfiles().toMutableList()
        val idx = profiles.indexOfFirst { it.id == id }
        if (idx >= 0) profiles[idx] = profile else profiles.add(profile)
        persistProfiles(profiles)
        return profile
    }

    fun setActiveProfile(profile: UserProfile) {
        prefs.edit()
            .putString("active_profile_id", profile.id)
            .putString("server_url", profile.serverUrl)
            .putString("username", profile.username)
            .putString("auth_token", profile.authToken)
            .apply()
    }

    fun deleteProfile(id: String) {
        val profiles = getAllProfiles().filter { it.id != id }
        persistProfiles(profiles)
        if (prefs.getString("active_profile_id", null) == id) {
            prefs.edit()
                .remove("active_profile_id")
                .remove("server_url")
                .remove("username")
                .remove("auth_token")
                .apply()
        }
    }

    fun makeId(serverUrl: String, username: String) = "${serverUrl.trimEnd('/')}::$username"

    private fun persistProfiles(list: List<UserProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    private fun JSONObject.toProfile() = UserProfile(
        id = getString("id"),
        serverUrl = getString("serverUrl"),
        username = getString("username"),
        authToken = optString("authToken", "")
    )

    private fun UserProfile.toJson() = JSONObject().apply {
        put("id", id)
        put("serverUrl", serverUrl)
        put("username", username)
        put("authToken", authToken)
    }
}
