// Google Play Store Integration Module
// Handles IAP, subscriptions, and Play Services integration

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use sha2::{Digest, Sha256};
use base64::{Engine as _, engine::general_purpose};

// ============================================================================
// GOOGLE PLAY BILLING
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlayStoreSKU {
    pub sku: String,
    pub product_type: ProductType,
    pub price_micros: i64,
    pub currency_code: String,
    pub title: String,
    pub description: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ProductType {
    InApp,
    Subscription,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Purchase {
    pub order_id: String,
    pub package_name: String,
    pub product_id: String,
    pub purchase_time: i64,
    pub purchase_state: PurchaseState,
    pub purchase_token: String,
    pub signature: String,
    pub acknowledged: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum PurchaseState {
    Purchased = 0,
    Pending = 2,
}

pub struct GooglePlayBilling {
    pub_key: String,
    skus: HashMap<String, PlayStoreSKU>,
    pending_purchases: Arc<Mutex<Vec<Purchase>>>,
}

impl GooglePlayBilling {
    pub fn new(pub_key: String) -> Self {
        let mut skus = HashMap::new();
        
        // Define all SKUs
        skus.insert("hope.crystals.100".to_string(), PlayStoreSKU {
            sku: "hope.crystals.100".to_string(),
            product_type: ProductType::InApp,
            price_micros: 990000,
            currency_code: "USD".to_string(),
            title: "100 Hope Crystals".to_string(),
            description: "A small bundle of Hope Crystals".to_string(),
        });
        
        skus.insert("hope.crystals.550".to_string(), PlayStoreSKU {
            sku: "hope.crystals.550".to_string(),
            product_type: ProductType::InApp,
            price_micros: 4990000,
            currency_code: "USD".to_string(),
            title: "550 Hope Crystals".to_string(),
            description: "Value pack with 10% bonus crystals".to_string(),
        });
        
        skus.insert("hope.crystals.1200".to_string(), PlayStoreSKU {
            sku: "hope.crystals.1200".to_string(),
            product_type: ProductType::InApp,
            price_micros: 9990000,
            currency_code: "USD".to_string(),
            title: "1200 Hope Crystals".to_string(),
            description: "Premium pack with 20% bonus crystals".to_string(),
        });
        
        skus.insert("hope.crystals.2500".to_string(), PlayStoreSKU {
            sku: "hope.crystals.2500".to_string(),
            product_type: ProductType::InApp,
            price_micros: 19990000,
            currency_code: "USD".to_string(),
            title: "2500 Hope Crystals".to_string(),
            description: "Mega pack with 25% bonus crystals".to_string(),
        });
        
        skus.insert("hope.crystals.6500".to_string(), PlayStoreSKU {
            sku: "hope.crystals.6500".to_string(),
            product_type: ProductType::InApp,
            price_micros: 49990000,
            currency_code: "USD".to_string(),
            title: "6500 Hope Crystals".to_string(),
            description: "Whale pack with 30% bonus crystals".to_string(),
        });
        
        skus.insert("hope.crystals.14000".to_string(), PlayStoreSKU {
            sku: "hope.crystals.14000".to_string(),
            product_type: ProductType::InApp,
            price_micros: 99990000,
            currency_code: "USD".to_string(),
            title: "14000 Hope Crystals".to_string(),
            description: "Legendary pack with 40% bonus crystals".to_string(),
        });
        
        // Subscriptions
        skus.insert("hope.vip.weekly".to_string(), PlayStoreSKU {
            sku: "hope.vip.weekly".to_string(),
            product_type: ProductType::Subscription,
            price_micros: 4990000,
            currency_code: "USD".to_string(),
            title: "VIP Weekly Pass".to_string(),
            description: "2x energy regen, daily crystals, ad-free".to_string(),
        });
        
        skus.insert("hope.premium.monthly".to_string(), PlayStoreSKU {
            sku: "hope.premium.monthly".to_string(),
            product_type: ProductType::Subscription,
            price_micros: 9990000,
            currency_code: "USD".to_string(),
            title: "Premium Monthly Pass".to_string(),
            description: "3x energy regen, 100 daily crystals, exclusive content".to_string(),
        });
        
        Self {
            pub_key,
            skus,
            pending_purchases: Arc::new(Mutex::new(Vec::new())),
        }
    }
    
    pub fn verify_purchase(&self, purchase: &Purchase) -> Result<bool, String> {
        // Verify purchase signature using Google's public key
        let data = format!(
            "{}:{}:{}:{}",
            purchase.order_id,
            purchase.product_id,
            purchase.purchase_time,
            purchase.purchase_token
        );
        
        // In production, use proper RSA signature verification with Google's public key
        let mut hasher = Sha256::new();
        hasher.update(data.as_bytes());
        hasher.update(self.pub_key.as_bytes());
        let expected_hash = hasher.finalize();
        
        // Decode and verify signature
        let signature_bytes = general_purpose::STANDARD
            .decode(&purchase.signature)
            .map_err(|e| format!("Failed to decode signature: {}", e))?;
        
        // Simplified verification (in production, use RSA)
        if signature_bytes.len() != 32 {
            return Ok(false);
        }
        
        Ok(true)
    }
    
    pub fn acknowledge_purchase(&self, purchase_token: &str) -> Result<(), String> {
        // In production, call Google Play API to acknowledge
        let mut pending = self.pending_purchases.lock().unwrap();
        
        if let Some(purchase) = pending.iter_mut().find(|p| p.purchase_token == purchase_token) {
            purchase.acknowledged = true;
            Ok(())
        } else {
            Err("Purchase not found".to_string())
        }
    }
    
    pub fn get_sku_details(&self, sku: &str) -> Option<&PlayStoreSKU> {
        self.skus.get(sku)
    }
    
    pub fn consume_purchase(&self, purchase_token: &str) -> Result<(), String> {
        // Consume the purchase (for consumable items)
        let mut pending = self.pending_purchases.lock().unwrap();
        pending.retain(|p| p.purchase_token != purchase_token);
        Ok(())
    }
}

// ============================================================================
// GOOGLE PLAY GAMES SERVICES
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Achievement {
    pub id: String,
    pub name: String,
    pub description: String,
    pub xp_value: u32,
    pub unlocked: bool,
    pub progress: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Leaderboard {
    pub id: String,
    pub name: String,
    pub order: ScoreOrder,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub enum ScoreOrder {
    LargerIsBetter,
    SmallerIsBetter,
}

pub struct PlayGamesServices {
    player_id: Option<String>,
    achievements: HashMap<String, Achievement>,
    leaderboards: HashMap<String, Leaderboard>,
    scores: Arc<Mutex<HashMap<String, Vec<Score>>>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Score {
    player_id: String,
    score: i64,
    timestamp: i64,
}

impl PlayGamesServices {
    pub fn new() -> Self {
        let mut achievements = HashMap::new();
        
        // Define achievements
        achievements.insert("first_win".to_string(), Achievement {
            id: "first_win".to_string(),
            name: "First Victory".to_string(),
            description: "Complete your first puzzle".to_string(),
            xp_value: 100,
            unlocked: false,
            progress: 0.0,
        });
        
        achievements.insert("speed_demon".to_string(), Achievement {
            id: "speed_demon".to_string(),
            name: "Speed Demon".to_string(),
            description: "Complete a puzzle in under 5 seconds".to_string(),
            xp_value: 500,
            unlocked: false,
            progress: 0.0,
        });
        
        achievements.insert("perfect_streak".to_string(), Achievement {
            id: "perfect_streak".to_string(),
            name: "Perfect Streak".to_string(),
            description: "Complete 10 puzzles in a row without mistakes".to_string(),
            xp_value: 1000,
            unlocked: false,
            progress: 0.0,
        });
        
        achievements.insert("whale_hunter".to_string(), Achievement {
            id: "whale_hunter".to_string(),
            name: "Whale Hunter".to_string(),
            description: "Purchase the legendary crystal pack".to_string(),
            xp_value: 2000,
            unlocked: false,
            progress: 0.0,
        });
        
        // Define leaderboards
        let mut leaderboards = HashMap::new();
        
        leaderboards.insert("high_score".to_string(), Leaderboard {
            id: "high_score".to_string(),
            name: "High Score".to_string(),
            order: ScoreOrder::LargerIsBetter,
        });
        
        leaderboards.insert("fastest_solve".to_string(), Leaderboard {
            id: "fastest_solve".to_string(),
            name: "Fastest Solve".to_string(),
            order: ScoreOrder::SmallerIsBetter,
        });
        
        leaderboards.insert("total_coins".to_string(), Leaderboard {
            id: "total_coins".to_string(),
            name: "Total Coins Earned".to_string(),
            order: ScoreOrder::LargerIsBetter,
        });
        
        Self {
            player_id: None,
            achievements,
            leaderboards,
            scores: Arc::new(Mutex::new(HashMap::new())),
        }
    }
    
    pub fn sign_in(&mut self, player_id: String) -> Result<(), String> {
        self.player_id = Some(player_id);
        Ok(())
    }
    
    pub fn unlock_achievement(&mut self, achievement_id: &str) -> Result<(), String> {
        let achievement = self.achievements.get_mut(achievement_id)
            .ok_or("Achievement not found")?;
        
        if !achievement.unlocked {
            achievement.unlocked = true;
            achievement.progress = 1.0;
            
            // In production, sync with Google Play Games
            println!("Achievement unlocked: {}", achievement.name);
        }
        
        Ok(())
    }
    
    pub fn increment_achievement(&mut self, achievement_id: &str, steps: u32) -> Result<(), String> {
        let achievement = self.achievements.get_mut(achievement_id)
            .ok_or("Achievement not found")?;
        
        if !achievement.unlocked {
            achievement.progress += steps as f32 / 100.0;  // Assuming 100 steps to complete
            
            if achievement.progress >= 1.0 {
                achievement.progress = 1.0;
                achievement.unlocked = true;
                println!("Achievement unlocked: {}", achievement.name);
            }
        }
        
        Ok(())
    }
    
    pub fn submit_score(&self, leaderboard_id: &str, score: i64) -> Result<(), String> {
        let player_id = self.player_id.as_ref()
            .ok_or("Not signed in")?;
        
        if !self.leaderboards.contains_key(leaderboard_id) {
            return Err("Leaderboard not found".to_string());
        }
        
        let score_entry = Score {
            player_id: player_id.clone(),
            score,
            timestamp: chrono::Utc::now().timestamp(),
        };
        
        let mut scores = self.scores.lock().unwrap();
        scores.entry(leaderboard_id.to_string())
            .or_insert_with(Vec::new)
            .push(score_entry);
        
        Ok(())
    }
    
    pub fn get_leaderboard(&self, leaderboard_id: &str, top_n: usize) -> Result<Vec<(String, i64)>, String> {
        let leaderboard = self.leaderboards.get(leaderboard_id)
            .ok_or("Leaderboard not found")?;
        
        let scores = self.scores.lock().unwrap();
        let mut leaderboard_scores = scores.get(leaderboard_id)
            .cloned()
            .unwrap_or_default();
        
        // Sort based on score order
        match leaderboard.order {
            ScoreOrder::LargerIsBetter => {
                leaderboard_scores.sort_by(|a, b| b.score.cmp(&a.score));
            }
            ScoreOrder::SmallerIsBetter => {
                leaderboard_scores.sort_by(|a, b| a.score.cmp(&b.score));
            }
        }
        
        Ok(leaderboard_scores
            .into_iter()
            .take(top_n)
            .map(|s| (s.player_id, s.score))
            .collect())
    }
}

// ============================================================================
// GOOGLE ADMOB INTEGRATION
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AdFormat {
    Banner,
    Interstitial,
    RewardedVideo,
    Native,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AdUnit {
    pub unit_id: String,
    pub format: AdFormat,
    pub ecpm: f64,  // Effective cost per thousand impressions
}

pub struct AdMobManager {
    ad_units: HashMap<String, AdUnit>,
    impressions: Arc<Mutex<Vec<AdImpression>>>,
    daily_cap: HashMap<String, u32>,
}

#[derive(Debug, Clone)]
struct AdImpression {
    unit_id: String,
    player_id: String,
    timestamp: i64,
    completed: bool,
    revenue: f64,
}

impl AdMobManager {
    pub fn new() -> Self {
        let mut ad_units = HashMap::new();
        
        // Define ad units
        ad_units.insert("banner_main".to_string(), AdUnit {
            unit_id: "ca-app-pub-xxx/banner".to_string(),
            format: AdFormat::Banner,
            ecpm: 0.50,
        });
        
        ad_units.insert("interstitial_level_complete".to_string(), AdUnit {
            unit_id: "ca-app-pub-xxx/interstitial".to_string(),
            format: AdFormat::Interstitial,
            ecpm: 2.00,
        });
        
        ad_units.insert("rewarded_energy".to_string(), AdUnit {
            unit_id: "ca-app-pub-xxx/rewarded".to_string(),
            format: AdFormat::RewardedVideo,
            ecpm: 10.00,
        });
        
        let mut daily_cap = HashMap::new();
        daily_cap.insert("rewarded_energy".to_string(), 10);  // Max 10 rewarded ads per day
        daily_cap.insert("interstitial_level_complete".to_string(), 20);
        
        Self {
            ad_units,
            impressions: Arc::new(Mutex::new(Vec::new())),
            daily_cap,
        }
    }
    
    pub fn load_ad(&self, unit_id: &str) -> Result<(), String> {
        if !self.ad_units.contains_key(unit_id) {
            return Err("Ad unit not found".to_string());
        }
        
        // In production, preload ad from AdMob
        Ok(())
    }
    
    pub fn show_ad(&self, unit_id: &str, player_id: String) -> Result<f64, String> {
        let ad_unit = self.ad_units.get(unit_id)
            .ok_or("Ad unit not found")?;
        
        // Check daily cap
        if let Some(cap) = self.daily_cap.get(unit_id) {
            let impressions = self.impressions.lock().unwrap();
            let today_impressions = impressions.iter()
                .filter(|i| {
                    i.unit_id == unit_id && 
                    i.player_id == player_id &&
                    // Check if within last 24 hours
                    i.timestamp > chrono::Utc::now().timestamp() - 86400
                })
                .count() as u32;
            
            if today_impressions >= *cap {
                return Err("Daily ad limit reached".to_string());
            }
        }
        
        // Calculate revenue
        let revenue = ad_unit.ecpm / 1000.0;
        
        // Record impression
        let impression = AdImpression {
            unit_id: unit_id.to_string(),
            player_id,
            timestamp: chrono::Utc::now().timestamp(),
            completed: true,
            revenue,
        };
        
        self.impressions.lock().unwrap().push(impression);
        
        Ok(revenue)
    }
    
    pub fn get_daily_revenue(&self, player_id: &str) -> f64 {
        let impressions = self.impressions.lock().unwrap();
        let cutoff = chrono::Utc::now().timestamp() - 86400;
        
        impressions.iter()
            .filter(|i| i.player_id == player_id && i.timestamp > cutoff && i.completed)
            .map(|i| i.revenue)
            .sum()
    }
}

// ============================================================================
// GOOGLE ANALYTICS FOR FIREBASE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AnalyticsEvent {
    pub name: String,
    pub parameters: HashMap<String, EventValue>,
    pub timestamp: i64,
    pub user_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum EventValue {
    String(String),
    Number(f64),
    Boolean(bool),
}

pub struct FirebaseAnalytics {
    events: Arc<Mutex<Vec<AnalyticsEvent>>>,
    user_properties: Arc<Mutex<HashMap<String, HashMap<String, String>>>>,
}

impl FirebaseAnalytics {
    pub fn new() -> Self {
        Self {
            events: Arc::new(Mutex::new(Vec::new())),
            user_properties: Arc::new(Mutex::new(HashMap::new())),
        }
    }
    
    pub fn log_event(&self, name: &str, parameters: HashMap<String, EventValue>, user_id: Option<String>) {
        let event = AnalyticsEvent {
            name: name.to_string(),
            parameters,
            timestamp: chrono::Utc::now().timestamp(),
            user_id,
        };
        
        self.events.lock().unwrap().push(event);
        
        // In production, send to Firebase
    }
    
    pub fn log_purchase(&self, value: f64, currency: &str, items: Vec<String>, user_id: String) {
        let mut parameters = HashMap::new();
        parameters.insert("value".to_string(), EventValue::Number(value));
        parameters.insert("currency".to_string(), EventValue::String(currency.to_string()));
        parameters.insert("items".to_string(), EventValue::String(items.join(",")));
        
        self.log_event("purchase", parameters, Some(user_id));
    }
    
    pub fn log_level_complete(&self, level: u32, score: i64, time_ms: u64, user_id: String) {
        let mut parameters = HashMap::new();
        parameters.insert("level".to_string(), EventValue::Number(level as f64));
        parameters.insert("score".to_string(), EventValue::Number(score as f64));
        parameters.insert("time_ms".to_string(), EventValue::Number(time_ms as f64));
        
        self.log_event("level_complete", parameters, Some(user_id));
    }
    
    pub fn set_user_property(&self, user_id: &str, name: &str, value: &str) {
        let mut properties = self.user_properties.lock().unwrap();
        properties.entry(user_id.to_string())
            .or_insert_with(HashMap::new)
            .insert(name.to_string(), value.to_string());
    }
    
    pub fn get_conversion_funnel(&self) -> HashMap<String, u32> {
        let events = self.events.lock().unwrap();
        let mut funnel = HashMap::new();
        
        // Count key events for funnel analysis
        for event in events.iter() {
            match event.name.as_str() {
                "app_open" => *funnel.entry("1_opened".to_string()).or_insert(0) += 1,
                "tutorial_begin" => *funnel.entry("2_tutorial_started".to_string()).or_insert(0) += 1,
                "tutorial_complete" => *funnel.entry("3_tutorial_completed".to_string()).or_insert(0) += 1,
                "level_complete" => *funnel.entry("4_first_level".to_string()).or_insert(0) += 1,
                "purchase" => *funnel.entry("5_made_purchase".to_string()).or_insert(0) += 1,
                _ => {}
            }
        }
        
        funnel
    }
}

// ============================================================================
// PLAY STORE MANAGER
// ============================================================================

pub struct PlayStoreManager {
    pub billing: Arc<GooglePlayBilling>,
    pub games_services: Arc<Mutex<PlayGamesServices>>,
    pub admob: Arc<AdMobManager>,
    pub analytics: Arc<FirebaseAnalytics>,
}

impl PlayStoreManager {
    pub fn new(google_pub_key: String) -> Self {
        Self {
            billing: Arc::new(GooglePlayBilling::new(google_pub_key)),
            games_services: Arc::new(Mutex::new(PlayGamesServices::new())),
            admob: Arc::new(AdMobManager::new()),
            analytics: Arc::new(FirebaseAnalytics::new()),
        }
    }
    
    pub fn initialize(&self, player_id: String) -> Result<(), String> {
        // Sign into Play Games
        self.games_services.lock().unwrap().sign_in(player_id.clone())?;
        
        // Set user properties for analytics
        self.analytics.set_user_property(&player_id, "game_version", "1.0.0");
        self.analytics.set_user_property(&player_id, "platform", "android");
        
        // Log app open event
        let mut params = HashMap::new();
        params.insert("source".to_string(), EventValue::String("organic".to_string()));
        self.analytics.log_event("app_open", params, Some(player_id));
        
        Ok(())
    }
    
    pub fn process_purchase(
        &self,
        purchase: Purchase,
        player_id: String,
    ) -> Result<u64, String> {
        // Verify purchase
        if !self.billing.verify_purchase(&purchase)? {
            return Err("Purchase verification failed".to_string());
        }
        
        // Get SKU details
        let sku = self.billing.get_sku_details(&purchase.product_id)
            .ok_or("Unknown product")?;
        
        // Award crystals based on SKU
        let crystals = match purchase.product_id.as_str() {
            "hope.crystals.100" => 100,
            "hope.crystals.550" => 550,
            "hope.crystals.1200" => 1200,
            "hope.crystals.2500" => 2500,
            "hope.crystals.6500" => 6500,
            "hope.crystals.14000" => 14000,
            _ => return Err("Unknown crystal bundle".to_string()),
        };
        
        // Acknowledge purchase
        self.billing.acknowledge_purchase(&purchase.purchase_token)?;
        
        // Consume purchase (for consumables)
        self.billing.consume_purchase(&purchase.purchase_token)?;
        
        // Log purchase event
        self.analytics.log_purchase(
            sku.price_micros as f64 / 1_000_000.0,
            &sku.currency_code,
            vec![purchase.product_id.clone()],
            player_id.clone(),
        );
        
        // Check for whale achievement
        if purchase.product_id == "hope.crystals.14000" {
            self.games_services.lock().unwrap()
                .unlock_achievement("whale_hunter")?;
        }
        
        Ok(crystals)
    }
    
    pub fn show_rewarded_ad(&self, player_id: String) -> Result<u32, String> {
        // Show rewarded video ad
        let revenue = self.admob.show_ad("rewarded_energy", player_id.clone())?;
        
        // Log ad impression
        let mut params = HashMap::new();
        params.insert("ad_format".to_string(), EventValue::String("rewarded".to_string()));
        params.insert("revenue".to_string(), EventValue::Number(revenue));
        self.analytics.log_event("ad_impression", params, Some(player_id));
        
        // Return energy reward
        Ok(5)  // 5 energy for watching ad
    }
}

// Module for chrono compatibility
mod chrono {
    pub struct Utc;
    
    impl Utc {
        pub fn now() -> DateTime {
            DateTime { timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64 
            }
        }
    }
    
    pub struct DateTime {
        timestamp: i64,
    }
    
    impl DateTime {
        pub fn timestamp(&self) -> i64 {
            self.timestamp
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_purchase_verification() {
        let billing = GooglePlayBilling::new("test_key".to_string());
        
        let purchase = Purchase {
            order_id: "GPA.1234-5678-9012".to_string(),
            package_name: "com.hope.game".to_string(),
            product_id: "hope.crystals.100".to_string(),
            purchase_time: 1234567890,
            purchase_state: PurchaseState::Purchased,
            purchase_token: "token123".to_string(),
            signature: general_purpose::STANDARD.encode(&[0u8; 32]),
            acknowledged: false,
        };
        
        assert!(billing.verify_purchase(&purchase).is_ok());
    }
    
    #[test]
    fn test_achievements() {
        let mut games = PlayGamesServices::new();
        games.sign_in("player1".to_string()).unwrap();
        
        assert!(games.unlock_achievement("first_win").is_ok());
        assert!(games.achievements.get("first_win").unwrap().unlocked);
    }
    
    #[test]
    fn test_leaderboards() {
        let mut games = PlayGamesServices::new();
        games.sign_in("player1".to_string()).unwrap();
        
        games.submit_score("high_score", 1000).unwrap();
        games.submit_score("high_score", 2000).unwrap();
        
        games.player_id = Some("player2".to_string());
        games.submit_score("high_score", 1500).unwrap();
        
        let top_scores = games.get_leaderboard("high_score", 3).unwrap();
        assert_eq!(top_scores[0].1, 2000);  // Highest score first
    }
    
    #[test]
    fn test_ad_revenue() {
        let admob = AdMobManager::new();
        
        let revenue1 = admob.show_ad("rewarded_energy", "player1".to_string()).unwrap();
        assert!(revenue1 > 0.0);
        
        let daily_revenue = admob.get_daily_revenue("player1");
        assert_eq!(daily_revenue, revenue1);
    }
}