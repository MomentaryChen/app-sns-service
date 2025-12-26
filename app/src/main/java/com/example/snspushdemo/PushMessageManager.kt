package com.example.snspushdemo

import android.content.Context
import android.content.SharedPreferences
import com.example.snspushdemo.model.PushMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 推送訊息管理器
 * 負責保存和讀取推送訊息歷史
 */
class PushMessageManager(private val context: Context) {

    private val prefs: SharedPreferences = 
        context.getSharedPreferences("PUSH_MESSAGES_PREFS", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val MESSAGES_KEY = "push_messages"
    private val MAX_MESSAGES = 100 // 最多保存100條訊息

    /**
     * 保存推送訊息
     */
    fun saveMessage(message: PushMessage) {
        val messages = getMessages().toMutableList()
        messages.add(0, message) // 新訊息新增到列表開頭
        
        // 限制訊息數量
        if (messages.size > MAX_MESSAGES) {
            messages.removeAt(messages.size - 1)
        }
        
        val json = gson.toJson(messages)
        prefs.edit().putString(MESSAGES_KEY, json).apply()
    }

    /**
     * 獲取所有推送訊息
     */
    fun getMessages(): List<PushMessage> {
        val json = prefs.getString(MESSAGES_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<PushMessage>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清除所有訊息
     */
    fun clearMessages() {
        prefs.edit().remove(MESSAGES_KEY).apply()
    }

    /**
     * 獲取訊息數量
     */
    fun getMessageCount(): Int {
        return getMessages().size
    }
}

