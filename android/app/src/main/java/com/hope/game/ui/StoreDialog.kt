package com.hope.game.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * In-game store dialog for purchases
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreDialog(
    onDismiss: () -> Unit,
    onPurchase: (String) -> Unit,
    coins: Int,
    purchasedItems: Set<String>,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E2E)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                StoreHeader(
                    coins = coins,
                    onClose = onDismiss
                )
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Items") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Coins") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Premium") }
                    )
                }
                
                // Content
                when (selectedTab) {
                    0 -> ItemsTab(onPurchase, purchasedItems)
                    1 -> CoinsTab(onPurchase)
                    2 -> PremiumTab(onPurchase, purchasedItems)
                }
            }
        }
    }
}

@Composable
fun StoreHeader(
    coins: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "STORE",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Coin balance
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFD700).copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Close button
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ItemsTab(
    onPurchase: (String) -> Unit,
    purchasedItems: Set<String>
) {
    val items = listOf(
        StoreItem("remove_ads", "Remove Ads", "$4.99", Icons.Default.Block, Color(0xFF4CAF50)),
        StoreItem("double_coins", "Double Coins", "$2.99", Icons.Default.DoubleArrow, Color(0xFFFFD700)),
        StoreItem("unlock_all_levels", "Unlock All Levels", "$9.99", Icons.Default.Lock, Color(0xFF2196F3)),
        StoreItem("premium_skin_pack", "Premium Skins", "$4.99", Icons.Default.Palette, Color(0xFF9C27B0)),
        StoreItem("starter_pack", "Starter Pack", "$1.99", Icons.Default.CardGiftcard, Color(0xFFFF5722)),
        StoreItem("mega_bundle", "Mega Bundle", "$14.99", Icons.Default.Stars, Color(0xFFE91E63))
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { item ->
            StoreItemCard(
                item = item,
                isPurchased = item.id in purchasedItems,
                onPurchase = { onPurchase(item.id) }
            )
        }
    }
}

@Composable
fun CoinsTab(onPurchase: (String) -> Unit) {
    val coinPacks = listOf(
        CoinPack("coins_100", "100 Coins", "$0.99", 100),
        CoinPack("coins_500", "500 Coins", "$3.99", 500, "BEST VALUE"),
        CoinPack("coins_1000", "1000 Coins", "$6.99", 1000),
        CoinPack("coins_2500", "2500 Coins", "$14.99", 2500, "20% BONUS"),
        CoinPack("coins_5000", "5000 Coins", "$24.99", 5000),
        CoinPack("coins_10000", "10000 Coins", "$39.99", 10000, "MEGA PACK")
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(coinPacks) { pack ->
            CoinPackCard(
                pack = pack,
                onPurchase = { onPurchase(pack.id) }
            )
        }
    }
}

@Composable
fun PremiumTab(
    onPurchase: (String) -> Unit,
    purchasedItems: Set<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Premium banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6200EA)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = "Premium",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "GO PREMIUM",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = "Unlock all features and content",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Benefits
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BenefitRow("Remove all ads")
                    BenefitRow("Unlimited lives")
                    BenefitRow("Double XP gain")
                    BenefitRow("Exclusive skins")
                    BenefitRow("Daily rewards")
                    BenefitRow("Priority support")
                }
            }
        }
        
        // Subscription options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Monthly
            Button(
                onClick = { onPurchase("premium_monthly") },
                modifier = Modifier.weight(1f),
                enabled = "premium_monthly" !in purchasedItems,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Monthly", fontWeight = FontWeight.Bold)
                    Text("$4.99/mo", fontSize = 12.sp)
                }
            }
            
            // Yearly
            Button(
                onClick = { onPurchase("premium_yearly") },
                modifier = Modifier.weight(1f),
                enabled = "premium_yearly" !in purchasedItems,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Yearly", fontWeight = FontWeight.Bold)
                    Text("$39.99/yr", fontSize = 12.sp)
                    Text("Save 33%", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun StoreItemCard(
    item: StoreItem,
    isPurchased: Boolean,
    onPurchase: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPurchased) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.8f)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(enabled = !isPurchased) { onPurchase() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPurchased) Color.Gray.copy(alpha = 0.3f) else Color(0xFF2C2C3E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                item.icon,
                contentDescription = item.name,
                tint = if (isPurchased) Color.Gray else item.color,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPurchased) Color.Gray else Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (isPurchased) {
                Text(
                    text = "OWNED",
                    fontSize = 12.sp,
                    color = Color.Green,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = item.price,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun CoinPackCard(
    pack: CoinPack,
    onPurchase: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPurchase() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C3E)
        ),
        border = if (pack.badge != null) {
            BorderStroke(2.dp, Color(0xFFFFD700))
        } else null
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Coin icon with amount
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MonetizationOn,
                        contentDescription = "Coins",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pack.amount.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = pack.price,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            
            // Badge
            pack.badge?.let { badge ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFF5722)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BenefitRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}

data class StoreItem(
    val id: String,
    val name: String,
    val price: String,
    val icon: ImageVector,
    val color: Color
)

data class CoinPack(
    val id: String,
    val name: String,
    val price: String,
    val amount: Int,
    val badge: String? = null
)