package com.example.snspushdemo.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 推送訊息資料模型
 */
data class PushMessage(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val from: String? = null
) {
    /**
     * 格式化時間戳為可讀字串
     */
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 獲取訊息的完整資訊
     */
    fun getFullInfo(): String {
        val info = StringBuilder()
        info.append("標題: $title\n")
        info.append("內容: $body\n")
        info.append("時間: ${getFormattedTime()}\n")
        if (from != null) {
            info.append("來源: $from\n")
        }
        if (data.isNotEmpty()) {
            info.append("資料:\n")
            data.forEach { (key, value) ->
                info.append("  $key: $value\n")
            }
        }
        return info.toString()
    }
}

