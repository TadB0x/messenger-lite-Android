package com.messengerlite.api

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private var baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .cookieJar(InMemoryCookieJar())
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private var csrfToken: String? = null
    private var authToken: String? = null

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun getBaseUrl(): String = baseUrl

    fun setAuthToken(token: String?) {
        authToken = token
    }

    fun getAuthToken(): String? = authToken

    private fun fetchCsrfToken(): String {
        val request = Request.Builder()
            .url("$baseUrl/index.php?action=ping")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val cookies = response.headers("Set-Cookie")
        for (cookie in cookies) {
            if (cookie.startsWith("csrf_token=")) {
                csrfToken = cookie.substringAfter("csrf_token=").substringBefore(";")
                return csrfToken!!
            }
        }
        // Try to parse from response
        val body = response.body?.string() ?: ""
        try {
            val json = JSONObject(body)
            if (json.has("csrf")) {
                csrfToken = json.getString("csrf")
                return csrfToken!!
            }
        } catch (_: Exception) {}
        return csrfToken ?: ""
    }

    private fun ensureCsrf(): String {
        return csrfToken ?: fetchCsrfToken()
    }

    fun get(action: String, params: Map<String, String> = emptyMap()): JSONObject {
        val urlBuilder = StringBuilder("$baseUrl/index.php?action=$action")
        params.forEach { (k, v) -> urlBuilder.append("&$k=$v") }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        return JSONObject(body)
    }

    fun post(action: String, data: JSONObject): JSONObject {
        val csrf = ensureCsrf()
        val request = Request.Builder()
            .url("$baseUrl/index.php?action=$action")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-CSRF-Token", csrf)
            .post(data.toString().toRequestBody(JSON_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        return JSONObject(body)
    }

    fun ping(): JSONObject = get("ping")

    fun register(username: String, password: String): JSONObject {
        return post("register", JSONObject().apply {
            put("username", username)
            put("password", password)
        })
    }

    fun login(username: String, password: String): JSONObject {
        val result = post("login", JSONObject().apply {
            put("username", username)
            put("password", password)
        })
        if (result.optString("status") == "success") {
            authToken = result.optString("token", null)
        }
        return result
    }

    fun restoreSession(token: String): JSONObject {
        return post("restore_session", JSONObject().apply {
            put("token", token)
        })
    }

    fun poll(ackDms: List<Int> = emptyList(), groupCursors: Map<String, Int> = emptyMap()): JSONObject {
        val data = JSONObject()
        if (ackDms.isNotEmpty()) {
            data.put("ack_dms", org.json.JSONArray(ackDms))
        }
        if (groupCursors.isNotEmpty()) {
            val cursors = JSONObject()
            groupCursors.forEach { (k, v) -> cursors.put(k, v) }
            data.put("group_cursors", cursors)
        }
        return post("poll", data)
    }

    fun sendMessage(message: String, toUser: String? = null, groupId: Int? = null, replyTo: Int? = null): JSONObject {
        val data = JSONObject().apply {
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            toUser?.let { put("to_user", it) }
            groupId?.let { put("group_id", it) }
            replyTo?.let { put("reply_to", it) }
        }
        return post("send", data)
    }

    fun sendTyping(to: String): JSONObject {
        return post("typing", JSONObject().apply { put("to", to) })
    }

    fun getProfile(username: String): JSONObject {
        return get("get_profile", mapOf("u" to username))
    }

    fun updateProfile(avatar: String? = null, bio: String? = null, newPassword: String? = null): JSONObject {
        val data = JSONObject()
        avatar?.let { data.put("avatar", it) }
        bio?.let { data.put("bio", it) }
        newPassword?.let { data.put("new_password", it) }
        return post("update_profile", data)
    }

    fun createGroup(name: String, type: String = "public", category: String = "group"): JSONObject {
        return post("create_group", JSONObject().apply {
            put("name", name)
            put("type", type)
            put("category", category)
        })
    }

    fun joinGroup(code: String, password: String? = null): JSONObject {
        val data = JSONObject().apply { put("code", code) }
        password?.let { data.put("password", it) }
        return post("join_group", data)
    }

    fun leaveGroup(groupId: Int): JSONObject {
        return post("leave_group", JSONObject().apply { put("group_id", groupId) })
    }

    fun getDiscoverableGroups(): JSONObject {
        return get("get_discoverable_groups")
    }

    fun getGroupDetails(groupId: Int): JSONObject {
        return get("get_group_details", mapOf("id" to groupId.toString()))
    }

    fun logout(): JSONObject {
        authToken = null
        csrfToken = null
        return get("logout")
    }
}

class InMemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.removeAll { existing -> cookies.any { it.name == existing.name } }
        store.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        store.removeAll { it.expiresAt < System.currentTimeMillis() }
        return store.filter { it.matches(url) }
    }
}
