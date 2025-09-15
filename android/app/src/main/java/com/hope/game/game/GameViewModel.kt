package com.hope.game.game

import android.content.res.AssetManager
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.hope.game.data.GameRepository
import com.hope.game.data.GameState
import com.hope.game.jni.NativeLib
import com.hope.game.playservices.PlayGamesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel managing game state and coordinating between native code and UI
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val nativeLib: NativeLib,
    private val gameRepository: GameRepository,
    private val playGamesManager: PlayGamesManager
) : ViewModel() {
    
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    
    private val _isGameRunning = MutableStateFlow(false)
    val isGameRunning: StateFlow<Boolean> = _isGameRunning.asStateFlow()
    
    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()
    
    private var gameLoopJob: Job? = null
    private var lastFrameTime = System.nanoTime()
    private val targetFrameTime = 16_666_667L // 60 FPS in nanoseconds
    
    private val analytics = Firebase.analytics
    
    /**
     * Initialize game engine with rendering surface
     */
    suspend fun initializeGame(surface: Surface?, assetManager: AssetManager) {
        if (surface == null) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val success = nativeLib.init(
                surface = surface,
                width = _gameState.value.screenWidth,
                height = _gameState.value.screenHeight,
                assetManager = assetManager
            )
            
            if (success) {
                loadSavedGameState()
                analytics.logEvent("game_initialized", null)
            } else {
                _gameState.update { it.copy(error = "Failed to initialize game engine") }
                analytics.logEvent("game_init_failed", null)
            }
        }
    }
    
    /**
     * Start the game loop
     */
    fun startGame() {
        if (_isGameRunning.value) return
        
        _isGameRunning.value = true
        _gameState.update { it.copy(isPaused = false) }
        
        analytics.logEvent("game_started", null)
        
        // Start game loop
        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            while (_isGameRunning.value) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime
                
                // Update game logic
                if (!_gameState.value.isPaused) {
                    updateGame(deltaTime)
                }
                
                // Render frame
                renderFrame()
                
                // Calculate FPS
                val actualFrameTime = System.nanoTime() - currentTime
                _fps.value = (1_000_000_000 / actualFrameTime.coerceAtLeast(1)).toInt()
                
                // Frame rate limiting
                val sleepTime = targetFrameTime - actualFrameTime
                if (sleepTime > 0) {
                    delay((sleepTime / 1_000_000).milliseconds)
                }
            }
        }
    }
    
    /**
     * Pause the game
     */
    fun pauseGame() {
        _gameState.update { it.copy(isPaused = true) }
        nativeLib.pause()
        
        analytics.logEvent("game_paused", null)
        
        // Save game state when pausing
        saveGameState()
    }
    
    /**
     * Resume the game
     */
    fun resumeGame() {
        _gameState.update { it.copy(isPaused = false) }
        nativeLib.resume()
        
        analytics.logEvent("game_resumed", null)
    }
    
    /**
     * Stop the game
     */
    fun stopGame() {
        _isGameRunning.value = false
        gameLoopJob?.cancel()
        gameLoopJob = null
        
        analytics.logEvent("game_stopped", null)
    }
    
    /**
     * Clean up game resources
     */
    fun destroyGame() {
        stopGame()
        nativeLib.destroy()
    }
    
    /**
     * Handle touch input
     */
    fun onTouch(x: Float, y: Float, action: Int, pointerId: Int = 0) {
        viewModelScope.launch(Dispatchers.Default) {
            val handled = nativeLib.onTouch(x, y, action, pointerId)
            if (handled) {
                // Update UI based on touch feedback
                updateGameStateFromNative()
            }
        }
    }
    
    /**
     * Handle surface size change
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        _gameState.update { 
            it.copy(screenWidth = width, screenHeight = height)
        }
        nativeLib.resize(width, height)
    }
    
    /**
     * Save current game state
     */
    fun saveGameState() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = nativeLib.getGameState()
            if (state != null) {
                gameRepository.saveGameState(state)
                
                // Save to Play Games cloud save
                if (playGamesManager.isSignedIn.value) {
                    playGamesManager.saveGameData(state)
                }
            }
        }
    }
    
    /**
     * Load saved game state
     */
    private suspend fun loadSavedGameState() {
        // Try loading from cloud save first
        if (playGamesManager.isSignedIn.value) {
            val cloudState = playGamesManager.loadGameData()
            if (cloudState != null) {
                nativeLib.setGameState(cloudState)
                updateGameStateFromNative()
                return
            }
        }
        
        // Fall back to local save
        val localState = gameRepository.loadGameState()
        if (localState != null) {
            nativeLib.setGameState(localState)
            updateGameStateFromNative()
        }
    }
    
    /**
     * Update game logic
     */
    private suspend fun updateGame(deltaTime: Float) {
        val success = nativeLib.update(deltaTime)
        if (success) {
            updateGameStateFromNative()
            checkAchievements()
        }
    }
    
    /**
     * Render frame
     */
    private suspend fun renderFrame() {
        nativeLib.render()
    }
    
    /**
     * Update UI state from native game state
     */
    private fun updateGameStateFromNative() {
        val score = nativeLib.getScore()
        val level = nativeLib.getLevel()
        
        _gameState.update { currentState ->
            currentState.copy(
                score = score,
                level = level,
                highScore = maxOf(currentState.highScore, score)
            )
        }
        
        // Submit score to leaderboard
        if (playGamesManager.isSignedIn.value && score > 0) {
            viewModelScope.launch {
                playGamesManager.submitScore(
                    PlayGamesManager.LEADERBOARD_HIGH_SCORES,
                    score
                )
            }
        }
    }
    
    /**
     * Check and unlock achievements
     */
    private fun checkAchievements() {
        val state = _gameState.value
        
        viewModelScope.launch {
            // First game achievement
            if (state.score > 0) {
                playGamesManager.unlockAchievement(PlayGamesManager.ACHIEVEMENT_FIRST_GAME)
            }
            
            // Score milestones
            when {
                state.score >= 10000 -> {
                    playGamesManager.unlockAchievement(PlayGamesManager.ACHIEVEMENT_SCORE_10K)
                }
                state.score >= 50000 -> {
                    playGamesManager.unlockAchievement(PlayGamesManager.ACHIEVEMENT_SCORE_50K)
                }
                state.score >= 100000 -> {
                    playGamesManager.unlockAchievement(PlayGamesManager.ACHIEVEMENT_SCORE_100K)
                }
            }
            
            // Level achievements
            when (state.level) {
                5 -> playGamesManager.unlockAchievement(PlayGamesManager.ACHIEVEMENT_LEVEL_5)
                10 -> playGamesManager.unlockAchievement(PlayGamesManager.ACHIEVEMENT_LEVEL_10)
                20 -> playGamesManager.unlockAchievement(PlayGamesManager.ACHIEVEMENT_LEVEL_20)
            }
        }
    }
    
    /**
     * Handle Play Games sign in
     */
    fun onPlayGamesSignedIn() {
        viewModelScope.launch {
            // Load cloud save
            loadSavedGameState()
            
            // Submit current score to leaderboard
            val score = _gameState.value.score
            if (score > 0) {
                playGamesManager.submitScore(
                    PlayGamesManager.LEADERBOARD_HIGH_SCORES,
                    score
                )
            }
        }
        
        analytics.logEvent("play_games_signed_in", null)
    }
    
    /**
     * Handle purchase completion
     */
    fun onPurchaseCompleted(sku: String) {
        _gameState.update { 
            it.copy(purchasedItems = it.purchasedItems + sku)
        }
        
        // Apply purchase benefits
        when (sku) {
            "remove_ads" -> {
                _gameState.update { it.copy(adsEnabled = false) }
            }
            "double_coins" -> {
                _gameState.update { it.copy(coinMultiplier = 2.0f) }
            }
            "unlock_all_levels" -> {
                _gameState.update { it.copy(allLevelsUnlocked = true) }
            }
        }
        
        analytics.logEvent("purchase_completed", Bundle().apply {
            putString("sku", sku)
        })
    }
    
    /**
     * Log performance metrics
     */
    fun logPerformanceMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            val metrics = nativeLib.getPerformanceMetrics()
            analytics.logEvent("performance_metrics", Bundle().apply {
                putString("metrics", metrics)
                putInt("fps", _fps.value)
            })
        }
    }
}