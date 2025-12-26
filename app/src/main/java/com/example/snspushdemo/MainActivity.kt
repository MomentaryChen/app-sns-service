package com.example.snspushdemo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.snspushdemo.databinding.ActivityMainBinding
import com.example.snspushdemo.model.PushMessage
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fcmTokenManager: FCMTokenManager
    private lateinit var pushMessageManager: PushMessageManager
    private lateinit var messageAdapter: PushMessageAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "通知權限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要通知權限才能接收推送訊息", Toast.LENGTH_LONG).show()
        }
    }

    // 广播接收器，用于接收新消息通知
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.snspushdemo.NEW_MESSAGE_RECEIVED") {
                refreshMessageList()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 在onCreate中初始化管理器，此时Context已经可用
        fcmTokenManager = FCMTokenManager(this)
        pushMessageManager = PushMessageManager(this)

        setupUI()
        setupMessageList()
        requestNotificationPermission()
        getFCMToken()
        checkForNotificationData()
        refreshMessageList()
    }

    override fun onResume() {
        super.onResume()
        try {
            // 注册广播接收器
            val filter = IntentFilter("com.example.snspushdemo.NEW_MESSAGE_RECEIVED")
            registerReceiver(messageReceiver, filter)
            // 刷新消息列表
            refreshMessageList()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "onResume時發生錯誤", e)
        }
    }

    override fun onPause() {
        super.onPause()
        // 注销广播接收器
        try {
            unregisterReceiver(messageReceiver)
        } catch (e: Exception) {
            // 忽略未注册的异常
        }
    }

    private fun setupUI() {
        binding.btnGetToken.setOnClickListener {
            getFCMToken()
        }

        binding.btnCopyToken.setOnClickListener {
            fcmTokenManager.copyTokenToClipboard()
            Toast.makeText(this, "Token已複製到剪貼簿", Toast.LENGTH_SHORT).show()
        }

        binding.btnRegisterDevice.setOnClickListener {
            registerDeviceToSNS()
        }

        binding.btnClearMessages.setOnClickListener {
            clearMessages()
        }
    }

    private fun setupMessageList() {
        messageAdapter = PushMessageAdapter { message ->
            // 点击消息项时显示详细信息
            showMessageDetails(message)
        }
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = messageAdapter
    }

    private fun refreshMessageList() {
        try {
            if (::pushMessageManager.isInitialized && ::messageAdapter.isInitialized) {
                val messages = pushMessageManager.getMessages()
                messageAdapter.submitList(messages)
                binding.tvMessageCount.text = "共收到 ${messages.size} 條訊息"
                
                if (messages.isEmpty()) {
                    binding.tvEmptyMessages.visibility = android.view.View.VISIBLE
                    binding.recyclerViewMessages.visibility = android.view.View.GONE
                } else {
                    binding.tvEmptyMessages.visibility = android.view.View.GONE
                    binding.recyclerViewMessages.visibility = android.view.View.VISIBLE
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "刷新訊息列表時發生錯誤", e)
        }
    }

    private fun clearMessages() {
        pushMessageManager.clearMessages()
        refreshMessageList()
        Toast.makeText(this, "訊息已清空", Toast.LENGTH_SHORT).show()
    }

    private fun showMessageDetails(message: PushMessage) {
        val details = message.getFullInfo()
        android.app.AlertDialog.Builder(this)
            .setTitle("訊息詳情")
            .setMessage(details)
            .setPositiveButton("確定", null)
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 權限已授予
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun getFCMToken() {
        try {
            binding.tvTokenStatus.text = "正在獲取Token..."
            
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                try {
                    if (!task.isSuccessful) {
                        val errorMsg = task.exception?.message ?: "未知錯誤"
                        binding.tvTokenStatus.text = "獲取Token失敗: $errorMsg"
                        android.util.Log.e("MainActivity", "獲取FCM Token失敗", task.exception)
                        return@OnCompleteListener
                    }

                    // 獲取新的FCM註冊令牌
                    val token = task.result
                    if (token != null && token.isNotEmpty()) {
                        fcmTokenManager.saveToken(token)
                        
                        binding.tvTokenStatus.text = "Token獲取成功"
                        binding.tvTokenValue.text = token
                        binding.tvTokenValue.visibility = android.view.View.VISIBLE
                        
                        // 顯示Token資訊
                        showTokenInfo(token)
                    } else {
                        binding.tvTokenStatus.text = "Token為空"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "處理Token結果時發生錯誤", e)
                    binding.tvTokenStatus.text = "處理Token時發生錯誤: ${e.message}"
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "獲取FCM Token時發生錯誤", e)
            binding.tvTokenStatus.text = "獲取Token失敗: ${e.message}"
        }
    }

    private fun showTokenInfo(token: String) {
        val tokenInfo = """
            FCM Token:
            $token
            
            請將此Token發送到您的後端伺服器，
            以便在AWS SNS中註冊設備。
        """.trimIndent()
        
        binding.tvTokenInfo.text = tokenInfo
    }

    private fun registerDeviceToSNS() {
        val token = fcmTokenManager.getToken()
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "請先獲取FCM Token", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRegisterDevice.isEnabled = false
        binding.tvRegisterStatus.text = "正在註冊設備..."

        lifecycleScope.launch {
            try {
                // 這裡應該呼叫您的後端API來註冊設備到AWS SNS
                // 範例：SNSManager.registerDevice(token)
                Toast.makeText(
                    this@MainActivity,
                    "請實作後端API來註冊設備到AWS SNS",
                    Toast.LENGTH_LONG
                ).show()
                binding.tvRegisterStatus.text = "註冊功能需要後端支援"
            } catch (e: Exception) {
                binding.tvRegisterStatus.text = "註冊失敗: ${e.message}"
            } finally {
                binding.btnRegisterDevice.isEnabled = true
            }
        }
    }

    private fun checkForNotificationData() {
        // 檢查是否有從通知點擊進入的資料
        val notificationData = intent.extras
        if (notificationData != null && notificationData.containsKey("notification_data")) {
            val data = notificationData.getString("notification_data")
            binding.tvNotificationData.text = "接收到的通知資料:\n$data"
            binding.tvNotificationData.visibility = android.view.View.VISIBLE
        }
    }
}

