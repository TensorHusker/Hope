package com.hope.game.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hope.game.data.GameState
import com.hope.game.rendering.GameSurfaceView
import kotlinx.coroutines.delay

/**
 * Main game screen with Compose UI overlays
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    gameState: GameState,
    isSignedIn: Boolean,
    onStartGame: () -> Unit,
    onPauseGame: () -> Unit,
    onResumeGame: () -> Unit,
    onSignIn: () -> Unit,
    onShowLeaderboard: () -> Unit,
    onShowAchievements: () -> Unit,
    onPurchase: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showStore by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Game Surface View
        AndroidView(
            factory = { context ->
                GameSurfaceView(context).apply {
                    // Configure surface view
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Game HUD Overlay
        if (!gameState.isPaused && !gameState.isGameOver) {
            GameHUD(
                score = gameState.score,
                level = gameState.level,
                lives = gameState.lives,
                coins = gameState.coins,
                onPauseClick = {
                    onPauseGame()
                    showMenu = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Pause Menu
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            PauseMenu(
                onResume = {
                    showMenu = false
                    onResumeGame()
                },
                onSettings = {
                    showSettings = true
                },
                onStore = {
                    showStore = true
                },
                onLeaderboard = onShowLeaderboard,
                onAchievements = onShowAchievements,
                onExit = {
                    // Handle exit
                },
                isSignedIn = isSignedIn,
                onSignIn = onSignIn
            )
        }
        
        // Game Over Screen
        AnimatedVisibility(
            visible = gameState.isGameOver,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            GameOverScreen(
                score = gameState.score,
                highScore = gameState.highScore,
                level = gameState.level,
                onRestart = onStartGame,
                onShare = {
                    // Share score
                },
                onLeaderboard = onShowLeaderboard
            )
        }
        
        // Store Dialog
        if (showStore) {
            StoreDialog(
                onDismiss = { showStore = false },
                onPurchase = onPurchase,
                coins = gameState.coins,
                purchasedItems = gameState.purchasedItems
            )
        }
        
        // Settings Dialog
        if (showSettings) {
            SettingsDialog(
                onDismiss = { showSettings = false },
                gameState = gameState
            )
        }
    }
}

@Composable
fun GameHUD(
    score: Long,
    level: Int,
    lives: Int,
    coins: Int,
    onPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Score
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Score",
                        tint = Color.Yellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = score.toString(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Level
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Level $level",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Pause button
            IconButton(
                onClick = onPauseClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = "Pause",
                    tint = Color.White
                )
            }
        }
        
        // Bottom bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Lives
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(lives) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Life",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Coins
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MonetizationOn,
                        contentDescription = "Coins",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = coins.toString(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PauseMenu(
    onResume: () -> Unit,
    onSettings: () -> Unit,
    onStore: () -> Unit,
    onLeaderboard: () -> Unit,
    onAchievements: () -> Unit,
    onExit: () -> Unit,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E2E)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PAUSED",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                MenuButton(
                    text = "Resume",
                    icon = Icons.Default.PlayArrow,
                    onClick = onResume,
                    isPrimary = true
                )
                
                MenuButton(
                    text = "Store",
                    icon = Icons.Default.ShoppingCart,
                    onClick = onStore
                )
                
                MenuButton(
                    text = "Leaderboard",
                    icon = Icons.Default.Leaderboard,
                    onClick = onLeaderboard
                )
                
                MenuButton(
                    text = "Achievements",
                    icon = Icons.Default.EmojiEvents,
                    onClick = onAchievements
                )
                
                MenuButton(
                    text = "Settings",
                    icon = Icons.Default.Settings,
                    onClick = onSettings
                )
                
                if (!isSignedIn) {
                    MenuButton(
                        text = "Sign In",
                        icon = Icons.Default.AccountCircle,
                        onClick = onSignIn
                    )
                }
                
                MenuButton(
                    text = "Exit",
                    icon = Icons.Default.ExitToApp,
                    onClick = onExit
                )
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isPrimary) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF00BCD4), Color(0xFF2196F3))
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF424242), Color(0xFF616161))
        )
    }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun GameOverScreen(
    score: Long,
    highScore: Long,
    level: Int,
    onRestart: () -> Unit,
    onShare: () -> Unit,
    onLeaderboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateIntAsState(
        targetValue = score.toInt(),
        animationSpec = tween(1500, easing = FastOutSlowInEasing)
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E2E)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "GAME OVER",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                
                Divider(color = Color.Gray)
                
                // Score display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Score",
                        fontSize = 18.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = animatedScore.toString(),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // High score
                if (score >= highScore) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFD700).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "NEW HIGH SCORE!",
                            color = Color(0xFFFFD700),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Best: $highScore",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
                
                Text(
                    text = "Level Reached: $level",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onRestart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                    
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                }
                
                Button(
                    onClick = onLeaderboard,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    )
                ) {
                    Icon(Icons.Default.Leaderboard, contentDescription = "Leaderboard")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leaderboard")
                }
            }
        }
    }
}