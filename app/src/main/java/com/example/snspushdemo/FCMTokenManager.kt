package com.example.snspushdemo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class FCMTokenManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("FCM_PREFS", Context.MODE_PRIVATE)
    private val TOKEN_KEY = "fcm_token"

    fun saveToken(token: String) {
        prefs.edit().putString(TOKEN_KEY, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }

    fun copyTokenToClipboard() {
        val token = getToken()
        if (token != null) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("FCM Token", token)
            clipboard.setPrimaryClip(clip)
        }
    }
}

