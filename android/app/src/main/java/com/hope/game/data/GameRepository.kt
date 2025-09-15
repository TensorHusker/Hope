package com.hope.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "game_preferences")

/**
 * Repository for game data persistence
 */
@Singleton
class GameRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Preference keys
    private object PreferenceKeys {
        val GAME_STATE = stringPreferencesKey("game_state")
        val HIGH_SCORE = longPreferencesKey("high_score")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val DIFFICULTY = stringPreferencesKey("difficulty")
        val PLAYER_NAME = stringPreferencesKey("player_name")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
        val STATISTICS = stringPreferencesKey("statistics")
        val LAST_SAVE_TIME = longPreferencesKey("last_save_time")
    }
    
    /**
     * Save game state
     */
    suspend fun saveGameState(state: ByteArray) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferenceKeys.GAME_STATE] = state.decodeToString()
                preferences[PreferenceKeys.LAST_SAVE_TIME] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load game state
     */
    suspend fun loadGameState(): ByteArray? {
        return try {
            context.dataStore.data.first()[PreferenceKeys.GAME_STATE]?.encodeToByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Save high score
     */
    suspend fun saveHighScore(score: Long) {
        context.dataStore.edit { preferences ->
            val currentHighScore = preferences[PreferenceKeys.HIGH_SCORE] ?: 0L
            if (score > currentHighScore) {
                preferences[PreferenceKeys.HIGH_SCORE] = score
            }
        }
    }
    
    /**
     * Get high score
     */
    fun getHighScore(): Flow<Long> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferenceKeys.HIGH_SCORE] ?: 0L
            }
    }
    
    /**
     * Save sound settings
     */
    suspend fun saveSoundSettings(soundEnabled: Boolean, musicEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SOUND_ENABLED] = soundEnabled
            preferences[PreferenceKeys.MUSIC_ENABLED] = musicEnabled
        }
    }
    
    /**
     * Get sound settings
     */
    fun getSoundSettings(): Flow<Pair<Boolean, Boolean>> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val sound = preferences[PreferenceKeys.SOUND_ENABLED] ?: true
                val music = preferences[PreferenceKeys.MUSIC_ENABLED] ?: true
                Pair(sound, music)
            }
    }
    
    /**
     * Save vibration setting
     */
    suspend fun saveVibrationSetting(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.VIBRATION_ENABLED] = enabled
        }
    }
    
    /**
     * Get vibration setting
     */
    fun getVibrationSetting(): Flow<Boolean> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferenceKeys.VIBRATION_ENABLED] ?: true
            }
    }
    
    /**
     * Save difficulty setting
     */
    suspend fun saveDifficulty(difficulty: Difficulty) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DIFFICULTY] = difficulty.name
        }
    }
    
    /**
     * Get difficulty setting
     */
    fun getDifficulty(): Flow<Difficulty> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val difficultyName = preferences[PreferenceKeys.DIFFICULTY] ?: Difficulty.NORMAL.name
                try {
                    Difficulty.valueOf(difficultyName)
                } catch (e: IllegalArgumentException) {
                    Difficulty.NORMAL
                }
            }
    }
    
    /**
     * Save player name
     */
    suspend fun savePlayerName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PLAYER_NAME] = name
        }
    }
    
    /**
     * Get player name
     */
    fun getPlayerName(): Flow<String?> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferenceKeys.PLAYER_NAME]
            }
    }
    
    /**
     * Check if first launch
     */
    suspend fun isFirstLaunch(): Boolean {
        val isFirst = context.dataStore.data.first()[PreferenceKeys.FIRST_LAUNCH] ?: true
        if (isFirst) {
            context.dataStore.edit { preferences ->
                preferences[PreferenceKeys.FIRST_LAUNCH] = false
            }
        }
        return isFirst
    }
    
    /**
     * Mark tutorial as completed
     */
    suspend fun completeTutorial() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.TUTORIAL_COMPLETED] = true
        }
    }
    
    /**
     * Check if tutorial is completed
     */
    fun isTutorialCompleted(): Flow<Boolean> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferenceKeys.TUTORIAL_COMPLETED] ?: false
            }
    }
    
    /**
     * Save game statistics
     */
    suspend fun saveStatistics(statistics: GameStatistics) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.STATISTICS] = json.encodeToString(statistics)
        }
    }
    
    /**
     * Get game statistics
     */
    fun getStatistics(): Flow<GameStatistics> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferenceKeys.STATISTICS]?.let {
                    try {
                        json.decodeFromString<GameStatistics>(it)
                    } catch (e: Exception) {
                        GameStatistics()
                    }
                } ?: GameStatistics()
            }
    }
    
    /**
     * Clear all game data
     */
    suspend fun clearAllData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    /**
     * Export game data for backup
     */
    suspend fun exportGameData(): String {
        val preferences = context.dataStore.data.first()
        val exportData = mutableMapOf<String, Any>()
        
        preferences.asMap().forEach { (key, value) ->
            exportData[key.name] = value
        }
        
        return json.encodeToString(exportData)
    }
    
    /**
     * Import game data from backup
     */
    suspend fun importGameData(jsonData: String) {
        try {
            val importData = json.decodeFromString<Map<String, Any>>(jsonData)
            
            context.dataStore.edit { preferences ->
                importData.forEach { (key, value) ->
                    when (key) {
                        PreferenceKeys.GAME_STATE.name -> 
                            preferences[PreferenceKeys.GAME_STATE] = value as String
                        PreferenceKeys.HIGH_SCORE.name -> 
                            preferences[PreferenceKeys.HIGH_SCORE] = (value as Number).toLong()
                        PreferenceKeys.SOUND_ENABLED.name -> 
                            preferences[PreferenceKeys.SOUND_ENABLED] = value as Boolean
                        PreferenceKeys.MUSIC_ENABLED.name -> 
                            preferences[PreferenceKeys.MUSIC_ENABLED] = value as Boolean
                        PreferenceKeys.VIBRATION_ENABLED.name -> 
                            preferences[PreferenceKeys.VIBRATION_ENABLED] = value as Boolean
                        PreferenceKeys.DIFFICULTY.name -> 
                            preferences[PreferenceKeys.DIFFICULTY] = value as String
                        PreferenceKeys.PLAYER_NAME.name -> 
                            preferences[PreferenceKeys.PLAYER_NAME] = value as String
                        PreferenceKeys.STATISTICS.name -> 
                            preferences[PreferenceKeys.STATISTICS] = value as String
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}