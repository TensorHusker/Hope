package com.hope.game.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Play Billing v5 for in-app purchases and subscriptions
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener, BillingClientStateListener {
    
    companion object {
        // Product IDs (must match Play Console configuration)
        const val SKU_REMOVE_ADS = "remove_ads"
        const val SKU_DOUBLE_COINS = "double_coins"
        const val SKU_UNLOCK_ALL_LEVELS = "unlock_all_levels"
        const val SKU_PREMIUM_SKIN_PACK = "premium_skin_pack"
        const val SKU_STARTER_PACK = "starter_pack"
        const val SKU_MEGA_BUNDLE = "mega_bundle"
        
        // Consumable products
        const val SKU_100_COINS = "coins_100"
        const val SKU_500_COINS = "coins_500"
        const val SKU_1000_COINS = "coins_1000"
        const val SKU_EXTRA_LIFE = "extra_life"
        const val SKU_POWER_BOOST = "power_boost"
        
        // Subscription products
        const val SKU_PREMIUM_MONTHLY = "premium_monthly"
        const val SKU_PREMIUM_YEARLY = "premium_yearly"
        
        private const val RECONNECT_TIMER_START_MILLISECONDS = 1000L
        private const val RECONNECT_TIMER_MAX_TIME_MILLISECONDS = 1000L * 60L * 15L
    }
    
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()
    
    private val _billingConnectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val billingConnectionState: StateFlow<BillingConnectionState> = _billingConnectionState
    
    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases
    
    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails
    
    private val _purchaseResult = MutableSharedFlow<PurchaseResult>()
    val purchaseResult: SharedFlow<PurchaseResult> = _purchaseResult
    
    private var reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val analytics = Firebase.analytics
    
    /**
     * Initialize billing client and connect
     */
    fun initialize() {
        startConnection()
    }
    
    /**
     * Start billing client connection
     */
    private fun startConnection() {
        if (billingClient.isReady) {
            _billingConnectionState.value = BillingConnectionState.CONNECTED
            return
        }
        
        _billingConnectionState.value = BillingConnectionState.CONNECTING
        billingClient.startConnection(this)
    }
    
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _billingConnectionState.value = BillingConnectionState.CONNECTED
            reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS
            
            // Query purchases and product details
            coroutineScope.launch {
                queryPurchases()
                queryProductDetails()
            }
            
            analytics.logEvent("billing_connected", null)
        } else {
            _billingConnectionState.value = BillingConnectionState.ERROR
            retryConnection()
            
            analytics.logEvent("billing_connection_failed", Bundle().apply {
                putInt("response_code", billingResult.responseCode)
                putString("debug_message", billingResult.debugMessage)
            })
        }
    }
    
    override fun onBillingServiceDisconnected() {
        _billingConnectionState.value = BillingConnectionState.DISCONNECTED
        retryConnection()
    }
    
    /**
     * Retry connection with exponential backoff
     */
    private fun retryConnection() {
        coroutineScope.launch {
            delay(reconnectMilliseconds)
            reconnectMilliseconds = (reconnectMilliseconds * 2)
                .coerceAtMost(RECONNECT_TIMER_MAX_TIME_MILLISECONDS)
            startConnection()
        }
    }
    
    /**
     * Query existing purchases
     */
    suspend fun queryPurchases() {
        if (!billingClient.isReady) return
        
        val purchasesList = mutableListOf<Purchase>()
        
        // Query in-app purchases
        val inAppResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        
        purchasesList.addAll(inAppResult.purchasesList)
        
        // Query subscriptions
        val subsResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        
        purchasesList.addAll(subsResult.purchasesList)
        
        _purchases.value = purchasesList
        
        // Process pending purchases
        processPurchases(purchasesList)
    }
    
    /**
     * Query product details
     */
    private suspend fun queryProductDetails() {
        if (!billingClient.isReady) return
        
        val productList = mutableListOf<QueryProductDetailsParams.Product>()
        
        // Add in-app products
        val inAppProducts = listOf(
            SKU_REMOVE_ADS, SKU_DOUBLE_COINS, SKU_UNLOCK_ALL_LEVELS,
            SKU_PREMIUM_SKIN_PACK, SKU_STARTER_PACK, SKU_MEGA_BUNDLE,
            SKU_100_COINS, SKU_500_COINS, SKU_1000_COINS,
            SKU_EXTRA_LIFE, SKU_POWER_BOOST
        )
        
        inAppProducts.forEach { sku ->
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(sku)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        }
        
        // Add subscription products
        val subProducts = listOf(SKU_PREMIUM_MONTHLY, SKU_PREMIUM_YEARLY)
        
        subProducts.forEach { sku ->
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(sku)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = productDetailsList.associateBy { it.productId }
            }
        }
    }
    
    /**
     * Launch billing flow for purchase
     */
    suspend fun launchBillingFlow(activity: Activity, productId: String): Boolean {
        if (!billingClient.isReady) {
            _purchaseResult.emit(PurchaseResult.Error("Billing service not connected"))
            return false
        }
        
        val productDetails = _productDetails.value[productId]
        if (productDetails == null) {
            _purchaseResult.emit(PurchaseResult.Error("Product not found"))
            return false
        }
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        
        analytics.logEvent("purchase_initiated", Bundle().apply {
            putString("product_id", productId)
        })
        
        return billingResult.responseCode == BillingClient.BillingResponseCode.OK
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let {
                    _purchases.value = it
                    processPurchases(it)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                coroutineScope.launch {
                    _purchaseResult.emit(PurchaseResult.Cancelled)
                }
                
                analytics.logEvent("purchase_cancelled", null)
            }
            else -> {
                coroutineScope.launch {
                    _purchaseResult.emit(
                        PurchaseResult.Error(
                            "Purchase failed: ${billingResult.debugMessage}"
                        )
                    )
                }
                
                analytics.logEvent("purchase_failed", Bundle().apply {
                    putInt("response_code", billingResult.responseCode)
                    putString("debug_message", billingResult.debugMessage)
                })
            }
        }
    }
    
    /**
     * Process purchases (acknowledge and consume as needed)
     */
    private fun processPurchases(purchases: List<Purchase>) {
        coroutineScope.launch {
            purchases.forEach { purchase ->
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                        
                        // Consume consumable products
                        if (isConsumable(purchase)) {
                            consumePurchase(purchase)
                        }
                        
                        // Grant entitlements
                        grantEntitlements(purchase)
                        
                        _purchaseResult.emit(
                            PurchaseResult.Success(purchase.products.first())
                        )
                        
                        analytics.logEvent("purchase_completed", Bundle().apply {
                            putString("products", purchase.products.joinToString())
                            putString("order_id", purchase.orderId ?: "")
                        })
                    }
                    Purchase.PurchaseState.PENDING -> {
                        _purchaseResult.emit(PurchaseResult.Pending)
                        
                        analytics.logEvent("purchase_pending", Bundle().apply {
                            putString("products", purchase.products.joinToString())
                        })
                    }
                }
            }
        }
    }
    
    /**
     * Acknowledge purchase
     */
    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                analytics.logEvent("purchase_acknowledged", Bundle().apply {
                    putString("products", purchase.products.joinToString())
                })
            }
        }
    }
    
    /**
     * Consume purchase (for consumable products)
     */
    private suspend fun consumePurchase(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        val result = billingClient.consumeAsync(params) { billingResult, purchaseToken ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                analytics.logEvent("purchase_consumed", Bundle().apply {
                    putString("products", purchase.products.joinToString())
                })
            }
        }
    }
    
    /**
     * Check if product is consumable
     */
    private fun isConsumable(purchase: Purchase): Boolean {
        val consumableProducts = listOf(
            SKU_100_COINS, SKU_500_COINS, SKU_1000_COINS,
            SKU_EXTRA_LIFE, SKU_POWER_BOOST
        )
        
        return purchase.products.any { it in consumableProducts }
    }
    
    /**
     * Grant entitlements based on purchase
     */
    private fun grantEntitlements(purchase: Purchase) {
        purchase.products.forEach { productId ->
            when (productId) {
                SKU_REMOVE_ADS -> {
                    // Remove ads
                    savePreference("ads_removed", true)
                }
                SKU_DOUBLE_COINS -> {
                    // Enable double coins
                    savePreference("double_coins", true)
                }
                SKU_UNLOCK_ALL_LEVELS -> {
                    // Unlock all levels
                    savePreference("all_levels_unlocked", true)
                }
                SKU_100_COINS -> addCoins(100)
                SKU_500_COINS -> addCoins(500)
                SKU_1000_COINS -> addCoins(1000)
                SKU_EXTRA_LIFE -> addLives(1)
                SKU_POWER_BOOST -> enablePowerBoost()
                SKU_PREMIUM_MONTHLY, SKU_PREMIUM_YEARLY -> {
                    // Enable premium features
                    savePreference("premium_active", true)
                }
            }
        }
    }
    
    /**
     * Check if user has specific purchase
     */
    fun hasPurchase(productId: String): Boolean {
        return _purchases.value.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            productId in purchase.products
        }
    }
    
    /**
     * Get price for product
     */
    fun getPrice(productId: String): String? {
        return _productDetails.value[productId]?.oneTimePurchaseOfferDetails?.formattedPrice
    }
    
    /**
     * Release billing client
     */
    fun release() {
        billingClient.endConnection()
        coroutineScope.cancel()
    }
    
    // Helper methods for game integration
    private fun savePreference(key: String, value: Boolean) {
        context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
    
    private fun addCoins(amount: Int) {
        // Implement coin addition logic
    }
    
    private fun addLives(amount: Int) {
        // Implement life addition logic
    }
    
    private fun enablePowerBoost() {
        // Implement power boost logic
    }
    
    /**
     * Billing connection states
     */
    enum class BillingConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    /**
     * Purchase result sealed class
     */
    sealed class PurchaseResult {
        data class Success(val productId: String) : PurchaseResult()
        object Cancelled : PurchaseResult()
        object Pending : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }
}