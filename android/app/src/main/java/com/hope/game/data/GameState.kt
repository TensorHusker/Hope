package com.hope.game.data

import kotlinx.serialization.Serializable

/**
 * Game state data model
 */
@Serializable
data class GameState(
    val score: Long = 0L,
    val highScore: Long = 0L,
    val level: Int = 1,
    val lives: Int = 3,
    val coins: Int = 0,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val purchasedItems: Set<String> = emptySet(),
    val unlockedLevels: Set<Int> = setOf(1),
    val achievements: Set<String> = emptySet(),
    val statistics: GameStatistics = GameStatistics(),
    val error: String? = null,
    val adsEnabled: Boolean = true,
    val coinMultiplier: Float = 1.0f,
    val allLevelsUnlocked: Boolean = false
)

/**
 * Game difficulty levels
 */
enum class Difficulty {
    EASY,
    NORMAL,
    HARD,
    EXTREME
}

/**
 * Game statistics for analytics
 */
@Serializable
data class GameStatistics(
    val totalPlayTime: Long = 0L,
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val totalScore: Long = 0L,
    val totalCoins: Int = 0,
    val enemiesDefeated: Int = 0,
    val powerUpsUsed: Int = 0,
    val perfectGames: Int = 0,
    val longestStreak: Int = 0,
    val fastestTime: Long = Long.MAX_VALUE
)