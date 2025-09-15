package com.hope.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hope.game.data.Difficulty
import com.hope.game.data.GameState

/**
 * Settings dialog for game preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    gameState: GameState,
    modifier: Modifier = Modifier
) {
    var soundEnabled by remember { mutableStateOf(gameState.soundEnabled) }
    var musicEnabled by remember { mutableStateOf(gameState.musicEnabled) }
    var vibrationEnabled by remember { mutableStateOf(gameState.vibrationEnabled) }
    var difficulty by remember { mutableStateOf(gameState.difficulty) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SETTINGS",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Audio Settings
                SettingsSection(title = "Audio") {
                    SettingsSwitch(
                        label = "Sound Effects",
                        icon = Icons.Default.VolumeUp,
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it }
                    )
                    
                    SettingsSwitch(
                        label = "Music",
                        icon = Icons.Default.MusicNote,
                        checked = musicEnabled,
                        onCheckedChange = { musicEnabled = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Gameplay Settings
                SettingsSection(title = "Gameplay") {
                    SettingsSwitch(
                        label = "Vibration",
                        icon = Icons.Default.Vibration,
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Difficulty selector
                    Column {
                        Text(
                            text = "Difficulty",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DifficultyChip(
                                text = "Easy",
                                selected = difficulty == Difficulty.EASY,
                                color = Color(0xFF4CAF50),
                                onClick = { difficulty = Difficulty.EASY }
                            )
                            
                            DifficultyChip(
                                text = "Normal",
                                selected = difficulty == Difficulty.NORMAL,
                                color = Color(0xFF2196F3),
                                onClick = { difficulty = Difficulty.NORMAL }
                            )
                            
                            DifficultyChip(
                                text = "Hard",
                                selected = difficulty == Difficulty.HARD,
                                color = Color(0xFFFF9800),
                                onClick = { difficulty = Difficulty.HARD }
                            )
                            
                            DifficultyChip(
                                text = "Extreme",
                                selected = difficulty == Difficulty.EXTREME,
                                color = Color(0xFFF44336),
                                onClick = { difficulty = Difficulty.EXTREME }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Controls Settings
                SettingsSection(title = "Controls") {
                    // Sensitivity slider
                    Column {
                        Text(
                            text = "Touch Sensitivity",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        
                        var sensitivity by remember { mutableStateOf(0.5f) }
                        
                        Slider(
                            value = sensitivity,
                            onValueChange = { sensitivity = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2196F3),
                                activeTrackColor = Color(0xFF2196F3),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Graphics Settings
                SettingsSection(title = "Graphics") {
                    var graphicsQuality by remember { mutableStateOf(1) } // 0: Low, 1: Medium, 2: High
                    
                    Column {
                        Text(
                            text = "Quality",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QualityChip(
                                text = "Low",
                                selected = graphicsQuality == 0,
                                onClick = { graphicsQuality = 0 }
                            )
                            
                            QualityChip(
                                text = "Medium",
                                selected = graphicsQuality == 1,
                                onClick = { graphicsQuality = 1 }
                            )
                            
                            QualityChip(
                                text = "High",
                                selected = graphicsQuality == 2,
                                onClick = { graphicsQuality = 2 }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    var fpsLimit by remember { mutableStateOf(60) }
                    
                    Column {
                        Text(
                            text = "FPS Limit: $fpsLimit",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Slider(
                            value = fpsLimit.toFloat(),
                            onValueChange = { fpsLimit = it.toInt() },
                            valueRange = 30f..120f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4CAF50),
                                activeTrackColor = Color(0xFF4CAF50),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            // Save settings
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2C2C3E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color.White
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultyChip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                fontSize = 14.sp
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            containerColor = Color.Gray.copy(alpha = 0.3f),
            labelColor = Color.White.copy(alpha = 0.7f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                fontSize = 14.sp
            )
        },
        modifier = Modifier.weight(1f),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF4CAF50),
            selectedLabelColor = Color.White,
            containerColor = Color.Gray.copy(alpha = 0.3f),
            labelColor = Color.White.copy(alpha = 0.7f)
        )
    )
}