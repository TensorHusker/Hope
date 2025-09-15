package com.hope.game.playservices

import android.app.Activity
import android.content.Intent
import com.google.android.gms.games.*
import com.google.android.gms.games.leaderboard.LeaderboardsClient
import com.google.android.gms.games.achievement.AchievementsClient
import com.google.android.gms.games.snapshot.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages Google Play Games Services integration
 */
@Singleton
class PlayGamesManager @Inject constructor() {
    
    companion object {
        // Request codes
        const val RC_SIGN_IN = 9001
        const val RC_ACHIEVEMENT_UI = 9002
        const val RC_LEADERBOARD_UI = 9003
        
        // Leaderboard IDs (replace with your actual IDs from Play Console)
        const val LEADERBOARD_HIGH_SCORES = "CgkI_YOUR_LEADERBOARD_ID"
        const val LEADERBOARD_FASTEST_TIME = "CgkI_YOUR_LEADERBOARD_ID_2"
        
        // Achievement IDs (replace with your actual IDs from Play Console)
        const val ACHIEVEMENT_FIRST_GAME = "CgkI_YOUR_ACHIEVEMENT_ID_1"
        const val ACHIEVEMENT_SCORE_10K = "CgkI_YOUR_ACHIEVEMENT_ID_2"
        const val ACHIEVEMENT_SCORE_50K = "CgkI_YOUR_ACHIEVEMENT_ID_3"
        const val ACHIEVEMENT_SCORE_100K = "CgkI_YOUR_ACHIEVEMENT_ID_4"
        const val ACHIEVEMENT_LEVEL_5 = "CgkI_YOUR_ACHIEVEMENT_ID_5"
        const val ACHIEVEMENT_LEVEL_10 = "CgkI_YOUR_ACHIEVEMENT_ID_6"
        const val ACHIEVEMENT_LEVEL_20 = "CgkI_YOUR_ACHIEVEMENT_ID_7"
        const val ACHIEVEMENT_SPEED_RUN = "CgkI_YOUR_ACHIEVEMENT_ID_8"
        const val ACHIEVEMENT_PERFECT_GAME = "CgkI_YOUR_ACHIEVEMENT_ID_9"
        const val ACHIEVEMENT_COLLECTOR = "CgkI_YOUR_ACHIEVEMENT_ID_10"
        
        // Save game constants
        const val SAVE_GAME_NAME = "HopeGameSave"
        const val SAVE_GAME_DESCRIPTION = "Hope Game Progress"
        const val MAX_SNAPSHOT_RESOLVE_RETRIES = 3
    }
    
    private var playersClient: PlayersClient? = null
    private var leaderboardsClient: LeaderboardsClient? = null
    private var achievementsClient: AchievementsClient? = null
    private var snapshotsClient: SnapshotsClient? = null
    private var eventsClient: EventsClient? = null
    
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn
    
    private val _currentPlayer = MutableStateFlow<Player?>(null)
    val currentPlayer: StateFlow<Player?> = _currentPlayer
    
    /**
     * Initialize Play Games clients after sign-in
     */
    private fun initializeClients(activity: Activity) {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        if (account != null) {
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            
            playersClient = PlayGames.getPlayersClient(activity)
            leaderboardsClient = PlayGames.getLeaderboardsClient(activity)
            achievementsClient = PlayGames.getAchievementsClient(activity)
            snapshotsClient = PlayGames.getSnapshotsClient(activity)
            eventsClient = PlayGames.getEventsClient(activity)
            
            _isSignedIn.value = true
            
            // Load current player info
            loadCurrentPlayer()
        }
    }
    
    /**
     * Sign in to Google Play Games
     */
    suspend fun signIn(activity: Activity): Boolean {
        return try {
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            val result = gamesSignInClient.signIn().await()
            
            if (result.isAuthenticated) {
                initializeClients(activity)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Silent sign-in (no UI)
     */
    suspend fun signInSilently(activity: Activity): Boolean {
        return try {
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            gamesSignInClient.isAuthenticated().await().let { authResult ->
                if (authResult.isAuthenticated) {
                    initializeClients(activity)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Sign out from Google Play Games
     */
    suspend fun signOut(activity: Activity) {
        try {
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            gamesSignInClient.signOut().await()
            
            _isSignedIn.value = false
            _currentPlayer.value = null
            
            // Clear client references
            playersClient = null
            leaderboardsClient = null
            achievementsClient = null
            snapshotsClient = null
            eventsClient = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load current player information
     */
    private fun loadCurrentPlayer() {
        playersClient?.currentPlayer?.addOnSuccessListener { player ->
            _currentPlayer.value = player
        }
    }
    
    /**
     * Submit score to leaderboard
     */
    suspend fun submitScore(leaderboardId: String, score: Long): Boolean {
        return try {
            leaderboardsClient?.submitScoreImmediate(leaderboardId, score)?.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Show leaderboard UI
     */
    suspend fun showLeaderboard(activity: Activity, leaderboardId: String): Boolean {
        return try {
            leaderboardsClient?.getLeaderboardIntent(leaderboardId)?.await()?.let { intent ->
                activity.startActivityForResult(intent, RC_LEADERBOARD_UI)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Show all leaderboards UI
     */
    suspend fun showAllLeaderboards(activity: Activity): Boolean {
        return try {
            leaderboardsClient?.allLeaderboardsIntent?.await()?.let { intent ->
                activity.startActivityForResult(intent, RC_LEADERBOARD_UI)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Unlock achievement
     */
    suspend fun unlockAchievement(achievementId: String): Boolean {
        return try {
            achievementsClient?.unlockImmediate(achievementId)?.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Increment achievement progress
     */
    suspend fun incrementAchievement(achievementId: String, increment: Int): Boolean {
        return try {
            achievementsClient?.incrementImmediate(achievementId, increment)?.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Show achievements UI
     */
    suspend fun showAchievements(activity: Activity): Boolean {
        return try {
            achievementsClient?.achievementsIntent?.await()?.let { intent ->
                activity.startActivityForResult(intent, RC_ACHIEVEMENT_UI)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Save game data to cloud
     */
    suspend fun saveGameData(data: ByteArray): Boolean {
        val client = snapshotsClient ?: return false
        
        return try {
            val conflictResolution = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
            
            client.open(SAVE_GAME_NAME, true, conflictResolution).await().let { result ->
                val snapshot = result.data
                
                snapshot?.let {
                    // Write save data
                    it.snapshotContents.writeBytes(data)
                    
                    // Create metadata
                    val metadata = SnapshotMetadataChange.Builder()
                        .setDescription(SAVE_GAME_DESCRIPTION)
                        .setPlayedTimeMillis(System.currentTimeMillis())
                        .build()
                    
                    // Commit the save
                    client.commitAndClose(it, metadata).await()
                    true
                } ?: false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Load game data from cloud
     */
    suspend fun loadGameData(): ByteArray? {
        val client = snapshotsClient ?: return null
        
        return try {
            val conflictResolution = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
            
            client.open(SAVE_GAME_NAME, false, conflictResolution).await().let { result ->
                val snapshot = result.data
                
                snapshot?.let {
                    val contents = it.snapshotContents
                    val data = contents.readFully()
                    
                    // Close the snapshot
                    client.discardAndClose(it)
                    
                    data
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Delete cloud save
     */
    suspend fun deleteGameSave(): Boolean {
        val client = snapshotsClient ?: return false
        
        return try {
            val conflictResolution = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
            
            client.open(SAVE_GAME_NAME, false, conflictResolution).await().let { result ->
                val snapshot = result.data
                
                snapshot?.let {
                    val metadata = it.metadata
                    client.delete(metadata).await()
                    true
                } ?: false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Submit event for analytics
     */
    fun submitEvent(eventId: String, incrementBy: Int = 1) {
        eventsClient?.increment(eventId, incrementBy)
    }
    
    /**
     * Load events data
     */
    suspend fun loadEvents(): List<Event>? {
        return try {
            eventsClient?.load(true)?.await()?.let { buffer ->
                val events = mutableListOf<Event>()
                buffer.forEach { events.add(it) }
                buffer.release()
                events
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if player has achievement
     */
    suspend fun hasAchievement(achievementId: String): Boolean {
        return try {
            achievementsClient?.load(false)?.await()?.let { buffer ->
                var hasAchievement = false
                buffer.forEach { achievement ->
                    if (achievement.achievementId == achievementId && 
                        achievement.state == Achievement.STATE_UNLOCKED) {
                        hasAchievement = true
                    }
                }
                buffer.release()
                hasAchievement
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get player's best score for a leaderboard
     */
    suspend fun getBestScore(leaderboardId: String): Long? {
        return try {
            leaderboardsClient?.loadCurrentPlayerLeaderboardScore(
                leaderboardId,
                LeaderboardVariant.TIME_SPAN_ALL_TIME,
                LeaderboardVariant.COLLECTION_PUBLIC
            )?.await()?.get()?.rawScore
        } catch (e: Exception) {
            null
        }
    }
}