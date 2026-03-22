package com.messengerlite

import android.content.Context
import android.content.SharedPreferences
import com.messengerlite.api.ApiClient

object App {
    lateinit var api: ApiClient
        private set
    lateinit var prefs: SharedPreferences
        private set
    var currentUser: String = ""

    fun init(context: Context) {
        prefs = context.getSharedPreferences("messenger_lite", Context.MODE_PRIVATE)
        ProfileManager.init(prefs)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        api = ApiClient(serverUrl)
        currentUser = prefs.getString("username", "") ?: ""
        api.setAuthToken(prefs.getString("auth_token", null))
    }

    fun saveLogin(username: String, token: String?, serverUrl: String) {
        currentUser = username
        val profile = ProfileManager.saveProfile(serverUrl, username, token ?: "")
        ProfileManager.setActiveProfile(profile)
        api.setAuthToken(token)
        api.setBaseUrl(serverUrl)
    }

    fun clearLogin() {
        currentUser = ""
        prefs.edit()
            .remove("username")
            .remove("auth_token")
            .remove("active_profile_id")
            .apply()
        api.setAuthToken(null)
    }

    fun isLoggedIn(): Boolean = currentUser.isNotEmpty() && api.getAuthToken() != null

    fun getServerUrl(): String = prefs.getString("server_url", "") ?: ""
}
