package com.example.snspushdemo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.snspushdemo.MainActivity
import com.example.snspushdemo.PushMessageManager
import com.example.snspushdemo.R
import com.example.snspushdemo.model.PushMessage
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMessaging"
        private const val CHANNEL_ID = "sns_push_channel"
        private const val CHANNEL_NAME = "SNS推送通知"
        private const val CHANNEL_DESCRIPTION = "接收來自AWS SNS的推送通知"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            Log.d(TAG, "收到訊息: ${remoteMessage.from}")

            val data = remoteMessage.data

            // 檢查訊息是否包含通知負載，並提取標題和內容
            val (title, body) = remoteMessage.notification?.let { notification ->
                val t = notification.title ?: "新訊息"
                val b = notification.body ?: ""
                Log.d(TAG, "訊息通知負載: $t - $b")
                Pair(t, b)
            } ?: run {
                // 如果沒有通知負載，嘗試從資料中提取
                val t = data["title"] ?: data["notification.title"] ?: "AWS SNS推送"
                val b = data["body"] ?: data["notification.body"] ?: data["message"] ?: "收到一條新訊息"
                Pair(t, b)
            }

            // 檢查訊息是否包含資料負載
            if (data.isNotEmpty()) {
                Log.d(TAG, "訊息資料負載: $data")
                handleDataMessage(data)
            }

            // 保存訊息到本地儲存
            val pushMessage = PushMessage(
                title = title,
                body = body,
                data = data,
                from = remoteMessage.from
            )
            try {
                val messageManager = PushMessageManager(this)
                messageManager.saveMessage(pushMessage)
                Log.d(TAG, "訊息已保存: ${pushMessage.getFullInfo()}")
            } catch (e: Exception) {
                Log.e(TAG, "保存訊息失敗", e)
            }

            // 發送通知
            try {
                sendNotification(title, body, data)
            } catch (e: Exception) {
                Log.e(TAG, "發送通知失敗", e)
            }

            // 發送廣播通知MainActivity更新訊息列表
            try {
                sendBroadcastToUpdateUI(pushMessage)
            } catch (e: Exception) {
                Log.e(TAG, "發送廣播失敗", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "處理訊息時發生錯誤", e)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "刷新FCM註冊令牌: $token")
        
        // 將新令牌保存到本地儲存
        val prefs = getSharedPreferences("FCM_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        
        // 這裡應該將新令牌發送到您的伺服器
        // 以便在AWS SNS中更新設備令牌
        sendRegistrationToServer(token)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        // 處理自訂資料訊息
        data.forEach { (key, value) ->
            Log.d(TAG, "資料鍵值對: $key = $value")
        }
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
        data: Map<String, String>
    ) {
        try {
            // 確保通知渠道已創建
            createNotificationChannel()
            
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // 將資料傳遞給Activity
                data.forEach { (key, value) ->
                    putExtra(key, value)
                }
                if (data.isNotEmpty()) {
                    putExtra("notification_data", data.toString())
                }
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, flags
            )

            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            // 只在有權限時設定聲音
            try {
                notificationBuilder.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            } catch (e: Exception) {
                Log.w(TAG, "設定通知聲音失敗", e)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                // 使用時間戳的雜湊值作為通知ID，避免溢位
                val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                notificationManager.notify(notificationId, notificationBuilder.build())
            } else {
                Log.e(TAG, "無法獲取NotificationManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "發送通知時發生錯誤", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                }

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "創建通知渠道失敗", e)
            }
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: 實作將令牌發送到您的後端伺服器
        // 後端伺服器應該使用此令牌在AWS SNS中註冊或更新設備
        Log.d(TAG, "應該將令牌發送到伺服器: $token")
    }

    /**
     * 發送廣播通知MainActivity更新UI
     */
    private fun sendBroadcastToUpdateUI(message: PushMessage) {
        try {
            val intent = Intent("com.example.snspushdemo.NEW_MESSAGE_RECEIVED").apply {
                setPackage(packageName) // 在 Android 8.0+ 需要設定包名
                putExtra("message_id", message.id)
                putExtra("message_title", message.title)
                putExtra("message_body", message.body)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "發送廣播失敗", e)
        }
    }
}

