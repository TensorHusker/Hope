package com.hope.game

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.games.PlayGamesSdk
import com.hope.game.billing.BillingManager
import com.hope.game.game.GameViewModel
import com.hope.game.playservices.PlayGamesManager
import com.hope.game.ui.GameScreen
import com.hope.game.ui.theme.HopeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Activity for Hope Game
 * Manages lifecycle, Play Games Services, and game rendering
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var playGamesManager: PlayGamesManager
    
    @Inject
    lateinit var billingManager: BillingManager
    
    private val gameViewModel: GameViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Play Games SDK
        PlayGamesSdk.initialize(this)
        
        // Set fullscreen immersive mode
        setupFullscreenMode()
        
        // Keep screen on during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Initialize billing
        billingManager.initialize()
        
        // Set up UI
        setContent {
            HopeTheme {
                val gameState by gameViewModel.gameState.collectAsState()
                val isSignedIn by playGamesManager.isSignedIn.collectAsState()
                
                GameScreen(
                    gameState = gameState,
                    isSignedIn = isSignedIn,
                    onStartGame = { gameViewModel.startGame() },
                    onPauseGame = { gameViewModel.pauseGame() },
                    onResumeGame = { gameViewModel.resumeGame() },
                    onSignIn = { signInToPlayGames() },
                    onShowLeaderboard = { showLeaderboard() },
                    onShowAchievements = { showAchievements() },
                    onPurchase = { sku -> purchaseItem(sku) }
                )
                
                // Handle game lifecycle
                LaunchedEffect(Unit) {
                    lifecycleScope.launch {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            gameViewModel.initializeGame(
                                surface = null, // Will be set by GameSurfaceView
                                assetManager = assets
                            )
                        }
                    }
                }
            }
        }
        
        // Auto sign-in to Play Games
        lifecycleScope.launch {
            playGamesManager.signInSilently()
        }
    }
    
    override fun onResume() {
        super.onResume()
        gameViewModel.resumeGame()
        billingManager.queryPurchases()
    }
    
    override fun onPause() {
        super.onPause()
        gameViewModel.pauseGame()
        gameViewModel.saveGameState()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        gameViewModel.destroyGame()
        billingManager.release()
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreenMode()
        }
    }
    
    private fun setupFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun signInToPlayGames() {
        lifecycleScope.launch {
            val success = playGamesManager.signIn(this@MainActivity)
            if (success) {
                gameViewModel.onPlayGamesSignedIn()
            }
        }
    }
    
    private fun showLeaderboard() {
        lifecycleScope.launch {
            playGamesManager.showLeaderboard(
                this@MainActivity,
                PlayGamesManager.LEADERBOARD_HIGH_SCORES
            )
        }
    }
    
    private fun showAchievements() {
        lifecycleScope.launch {
            playGamesManager.showAchievements(this@MainActivity)
        }
    }
    
    private fun purchaseItem(sku: String) {
        lifecycleScope.launch {
            val success = billingManager.launchBillingFlow(this@MainActivity, sku)
            if (success) {
                gameViewModel.onPurchaseCompleted(sku)
            }
        }
    }
}