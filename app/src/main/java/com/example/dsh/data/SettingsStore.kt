package com.example.dsh.data

import android.content.Context
import android.content.SharedPreferences

data class ConnectionConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)

/** Persists the reverse-proxy endpoint + Basic-auth credentials in SharedPreferences. */
class SettingsStore(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("dsh", Context.MODE_PRIVATE)

    fun load(): ConnectionConfig = ConnectionConfig(
        baseUrl = prefs.getString("baseUrl", "") ?: "",
        username = prefs.getString("username", "") ?: "",
        password = prefs.getString("password", "") ?: "",
    )

    fun save(baseUrl: String, username: String, password: String) {
        prefs.edit()
            .putString("baseUrl", baseUrl)
            .putString("username", username)
            .putString("password", password)
            .apply()
    }
}
