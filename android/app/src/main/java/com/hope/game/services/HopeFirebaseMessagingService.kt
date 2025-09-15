package com.hope.game.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hope.game.MainActivity
import com.hope.game.R
import com.hope.game.data.GameRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service for push notifications
 */
@AndroidEntryPoint
class HopeFirebaseMessagingService : FirebaseMessagingService() {
    
    @Inject
    lateinit var gameRepository: GameRepository
    
    companion object {
        private const val CHANNEL_ID = "hope_game_notifications"
        private const val CHANNEL_NAME = "Hope Game"
        private const val CHANNEL_DESCRIPTION = "Notifications for Hope Game"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to server for targeted notifications
        sendTokenToServer(token)
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        }
        
        // Handle notification payload
        remoteMessage.notification?.let {
            showNotification(it.title ?: "", it.body ?: "", remoteMessage.data)
        }
    }
    
    private fun handleDataMessage(data: Map<String, String>) {
        when (data["type"]) {
            "daily_reward" -> handleDailyReward(data)
            "event_start" -> handleEventStart(data)
            "achievement_unlocked" -> handleAchievementUnlocked(data)
            "friend_challenge" -> handleFriendChallenge(data)
            "update_available" -> handleUpdateAvailable(data)
            else -> handleGenericMessage(data)
        }
    }
    
    private fun handleDailyReward(data: Map<String, String>) {
        val rewardAmount = data["reward_amount"]?.toIntOrNull() ?: 0
        val rewardType = data["reward_type"] ?: "coins"
        
        // Save reward to be claimed when app opens
        CoroutineScope(Dispatchers.IO).launch {
            // Store pending reward in preferences
        }
        
        showNotification(
            "Daily Reward Available!",
            "Claim your $rewardAmount $rewardType now!",
            data
        )
    }
    
    private fun handleEventStart(data: Map<String, String>) {
        val eventName = data["event_name"] ?: "Special Event"
        val eventDuration = data["duration"] ?: "Limited Time"
        
        showNotification(
            "$eventName Started!",
            "Join now for $eventDuration",
            data
        )
    }
    
    private fun handleAchievementUnlocked(data: Map<String, String>) {
        val achievementName = data["achievement_name"] ?: "Achievement"
        val reward = data["reward"] ?: ""
        
        showNotification(
            "Achievement Unlocked!",
            "$achievementName - Reward: $reward",
            data
        )
    }
    
    private fun handleFriendChallenge(data: Map<String, String>) {
        val friendName = data["friend_name"] ?: "A friend"
        val challengeType = data["challenge_type"] ?: "score"
        
        showNotification(
            "Challenge from $friendName",
            "Can you beat their $challengeType?",
            data
        )
    }
    
    private fun handleUpdateAvailable(data: Map<String, String>) {
        val version = data["version"] ?: ""
        val features = data["features"] ?: "Bug fixes and improvements"
        
        showNotification(
            "Update Available",
            "Version $version: $features",
            data
        )
    }
    
    private fun handleGenericMessage(data: Map<String, String>) {
        val title = data["title"] ?: "Hope Game"
        val message = data["message"] ?: ""
        
        if (message.isNotEmpty()) {
            showNotification(title, message, data)
        }
    }
    
    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with actual icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        
        // Add action buttons based on notification type
        when (data["type"]) {
            "daily_reward" -> {
                val claimIntent = createActionIntent("CLAIM_REWARD", data)
                notificationBuilder.addAction(
                    android.R.drawable.ic_menu_add,
                    "Claim Now",
                    claimIntent
                )
            }
            "friend_challenge" -> {
                val acceptIntent = createActionIntent("ACCEPT_CHALLENGE", data)
                notificationBuilder.addAction(
                    android.R.drawable.ic_menu_add,
                    "Accept",
                    acceptIntent
                )
            }
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
    
    private fun createActionIntent(action: String, data: Map<String, String>): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }
        
        return PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun sendTokenToServer(token: String) {
        // Send FCM token to your backend server
        CoroutineScope(Dispatchers.IO).launch {
            // Implement API call to register token
        }
    }
}