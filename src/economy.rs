// Hope Game Economy Module
// Implements token economics, monetization, and anti-fraud systems

use serde::{Deserialize, Serialize};
use std::collections::{HashMap, VecDeque};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use sha3::{Digest, Sha3_256};

// ============================================================================
// CURRENCY SYSTEM
// ============================================================================

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Currency {
    HopeCrystals,  // Premium currency (purchased with real money)
    Energy,        // Gameplay currency (regenerates over time)
    Coins,         // Soft currency (earned through gameplay)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Wallet {
    pub hope_crystals: u64,
    pub energy: u32,
    pub coins: u64,
    pub energy_cap: u32,
    pub last_energy_update: Instant,
}

impl Wallet {
    pub fn new() -> Self {
        Self {
            hope_crystals: 0,
            energy: 50,
            coins: 0,
            energy_cap: 50,
            last_energy_update: Instant::now(),
        }
    }
    
    pub fn regenerate_energy(&mut self, regen_rate: f32) {
        let elapsed = self.last_energy_update.elapsed().as_secs_f32();
        let energy_gained = (elapsed * regen_rate) as u32;
        
        if energy_gained > 0 {
            self.energy = (self.energy + energy_gained).min(self.energy_cap);
            self.last_energy_update = Instant::now();
        }
    }
    
    pub fn spend(&mut self, currency: Currency, amount: u64) -> Result<(), String> {
        match currency {
            Currency::HopeCrystals => {
                if self.hope_crystals >= amount {
                    self.hope_crystals -= amount;
                    Ok(())
                } else {
                    Err("Insufficient Hope Crystals".to_string())
                }
            }
            Currency::Energy => {
                if self.energy >= amount as u32 {
                    self.energy -= amount as u32;
                    Ok(())
                } else {
                    Err("Insufficient Energy".to_string())
                }
            }
            Currency::Coins => {
                if self.coins >= amount {
                    self.coins -= amount;
                    Ok(())
                } else {
                    Err("Insufficient Coins".to_string())
                }
            }
        }
    }
    
    pub fn add(&mut self, currency: Currency, amount: u64) {
        match currency {
            Currency::HopeCrystals => {
                self.hope_crystals = self.hope_crystals.saturating_add(amount);
            }
            Currency::Energy => {
                self.energy = (self.energy + amount as u32).min(self.energy_cap);
            }
            Currency::Coins => {
                self.coins = self.coins.saturating_add(amount);
            }
        }
    }
}

// ============================================================================
// IAP STORE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IAPBundle {
    pub id: String,
    pub crystals: u64,
    pub price_usd: f64,
    pub bonus_percentage: f32,
    pub limited_time: bool,
    pub max_purchases: Option<u32>,
}

pub struct IAPStore {
    bundles: Vec<IAPBundle>,
    purchase_history: HashMap<String, Vec<Purchase>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Purchase {
    bundle_id: String,
    timestamp: SystemTime,
    transaction_id: String,
    validated: bool,
}

impl IAPStore {
    pub fn new() -> Self {
        let bundles = vec![
            IAPBundle {
                id: "starter_pack".to_string(),
                crystals: 100,
                price_usd: 0.99,
                bonus_percentage: 0.0,
                limited_time: false,
                max_purchases: None,
            },
            IAPBundle {
                id: "value_pack".to_string(),
                crystals: 550,
                price_usd: 4.99,
                bonus_percentage: 10.0,
                limited_time: false,
                max_purchases: None,
            },
            IAPBundle {
                id: "premium_pack".to_string(),
                crystals: 1200,
                price_usd: 9.99,
                bonus_percentage: 20.0,
                limited_time: false,
                max_purchases: None,
            },
            IAPBundle {
                id: "mega_pack".to_string(),
                crystals: 2500,
                price_usd: 19.99,
                bonus_percentage: 25.0,
                limited_time: false,
                max_purchases: None,
            },
            IAPBundle {
                id: "whale_pack".to_string(),
                crystals: 6500,
                price_usd: 49.99,
                bonus_percentage: 30.0,
                limited_time: false,
                max_purchases: None,
            },
            IAPBundle {
                id: "legendary_pack".to_string(),
                crystals: 14000,
                price_usd: 99.99,
                bonus_percentage: 40.0,
                limited_time: false,
                max_purchases: None,
            },
            // Limited time offers
            IAPBundle {
                id: "flash_sale".to_string(),
                crystals: 300,
                price_usd: 1.99,
                bonus_percentage: 50.0,
                limited_time: true,
                max_purchases: Some(1),
            },
        ];
        
        Self {
            bundles,
            purchase_history: HashMap::new(),
        }
    }
    
    pub fn get_bundles(&self) -> &[IAPBundle] {
        &self.bundles
    }
    
    pub fn process_purchase(
        &mut self,
        player_id: String,
        bundle_id: String,
        transaction_id: String,
    ) -> Result<u64, String> {
        // Find bundle
        let bundle = self.bundles.iter()
            .find(|b| b.id == bundle_id)
            .ok_or("Invalid bundle ID")?;
        
        // Check purchase limits
        if let Some(max) = bundle.max_purchases {
            let purchase_count = self.purchase_history
                .get(&player_id)
                .map(|history| {
                    history.iter()
                        .filter(|p| p.bundle_id == bundle_id)
                        .count() as u32
                })
                .unwrap_or(0);
            
            if purchase_count >= max {
                return Err("Purchase limit reached".to_string());
            }
        }
        
        // Validate with payment provider (simulated)
        let validated = self.validate_transaction(&transaction_id)?;
        
        // Record purchase
        let purchase = Purchase {
            bundle_id: bundle_id.clone(),
            timestamp: SystemTime::now(),
            transaction_id,
            validated,
        };
        
        self.purchase_history
            .entry(player_id)
            .or_insert_with(Vec::new)
            .push(purchase);
        
        Ok(bundle.crystals)
    }
    
    fn validate_transaction(&self, transaction_id: &str) -> Result<bool, String> {
        // In production, this would validate with Google Play
        // For now, check transaction ID format
        if transaction_id.len() < 10 {
            return Err("Invalid transaction ID".to_string());
        }
        Ok(true)
    }
}

// ============================================================================
// BONDING CURVES
// ============================================================================

pub struct BondingCurve;

impl BondingCurve {
    /// Linear bonding curve: price = k * supply + b
    pub fn linear(supply: f64, k: f64, b: f64) -> f64 {
        k * supply + b
    }
    
    /// Quadratic bonding curve: price = a * supply^2 + b * supply + c
    pub fn quadratic(supply: f64, a: f64, b: f64, c: f64) -> f64 {
        a * supply * supply + b * supply + c
    }
    
    /// Exponential bonding curve: price = a * e^(k * supply)
    pub fn exponential(supply: f64, a: f64, k: f64) -> f64 {
        a * (k * supply).exp()
    }
    
    /// Sigmoid bonding curve for smooth price transitions
    pub fn sigmoid(supply: f64, max_price: f64, steepness: f64, midpoint: f64) -> f64 {
        max_price / (1.0 + (-steepness * (supply - midpoint)).exp())
    }
    
    /// Calculate optimal price for limited-time offers
    pub fn dynamic_pricing(
        base_price: f64,
        current_demand: f64,
        time_remaining: f64,
        total_time: f64,
    ) -> f64 {
        // Price increases as time runs out and demand is high
        let time_factor = 1.0 + (1.0 - time_remaining / total_time) * 0.5;
        let demand_factor = 1.0 + (current_demand / 100.0).min(1.0) * 0.3;
        
        base_price * time_factor * demand_factor
    }
}

// ============================================================================
// ANTI-FRAUD SYSTEM
// ============================================================================

#[derive(Debug, Clone)]
pub struct PlayerAction {
    pub player_id: String,
    pub action_type: String,
    pub timestamp: Instant,
    pub metadata: HashMap<String, String>,
}

pub struct AntiFraudSystem {
    rate_limits: HashMap<String, (u32, Duration)>,
    action_history: Arc<Mutex<HashMap<String, VecDeque<PlayerAction>>>>,
    suspicious_players: Arc<Mutex<Vec<String>>>,
}

impl AntiFraudSystem {
    pub fn new() -> Self {
        let mut rate_limits = HashMap::new();
        
        // Define rate limits for different actions
        rate_limits.insert("puzzle_complete".to_string(), (30, Duration::from_secs(3600))); // 30 per hour
        rate_limits.insert("purchase".to_string(), (10, Duration::from_secs(86400))); // 10 per day
        rate_limits.insert("energy_refill".to_string(), (5, Duration::from_secs(3600))); // 5 per hour
        rate_limits.insert("reward_claim".to_string(), (50, Duration::from_secs(3600))); // 50 per hour
        
        Self {
            rate_limits,
            action_history: Arc::new(Mutex::new(HashMap::new())),
            suspicious_players: Arc::new(Mutex::new(Vec::new())),
        }
    }
    
    pub fn check_action(&self, action: PlayerAction) -> Result<(), String> {
        // Check rate limits
        if let Some((limit, window)) = self.rate_limits.get(&action.action_type) {
            let mut history = self.action_history.lock().unwrap();
            let player_history = history.entry(action.player_id.clone())
                .or_insert_with(VecDeque::new);
            
            // Remove old actions outside the window
            let cutoff = Instant::now() - *window;
            while let Some(front) = player_history.front() {
                if front.timestamp < cutoff {
                    player_history.pop_front();
                } else {
                    break;
                }
            }
            
            // Count actions of this type in the window
            let count = player_history.iter()
                .filter(|a| a.action_type == action.action_type)
                .count() as u32;
            
            if count >= *limit {
                self.flag_suspicious_player(&action.player_id);
                return Err(format!("Rate limit exceeded: {} {} in {:?}", 
                    limit, action.action_type, window));
            }
            
            player_history.push_back(action.clone());
        }
        
        // Check for specific exploits
        self.check_exploits(&action)?;
        
        Ok(())
    }
    
    fn check_exploits(&self, action: &PlayerAction) -> Result<(), String> {
        match action.action_type.as_str() {
            "puzzle_complete" => {
                // Check for impossible solve times
                if let Some(solve_time) = action.metadata.get("solve_time_ms") {
                    if let Ok(time_ms) = solve_time.parse::<u64>() {
                        if let Some(difficulty) = action.metadata.get("difficulty") {
                            if let Ok(diff) = difficulty.parse::<u32>() {
                                let min_time = 500 * diff; // 500ms per difficulty level minimum
                                if time_ms < min_time as u64 {
                                    self.flag_suspicious_player(&action.player_id);
                                    return Err(format!("Impossible solve time: {}ms for difficulty {}", 
                                        time_ms, diff));
                                }
                            }
                        }
                    }
                }
            }
            "currency_earned" => {
                // Check for excessive currency generation
                if let Some(amount) = action.metadata.get("amount") {
                    if let Ok(amt) = amount.parse::<u64>() {
                        if amt > 10000 {  // Suspicious amount
                            self.flag_suspicious_player(&action.player_id);
                            return Err(format!("Excessive currency generation: {}", amt));
                        }
                    }
                }
            }
            _ => {}
        }
        
        Ok(())
    }
    
    fn flag_suspicious_player(&self, player_id: &str) {
        let mut suspicious = self.suspicious_players.lock().unwrap();
        if !suspicious.contains(&player_id.to_string()) {
            suspicious.push(player_id.to_string());
        }
    }
    
    pub fn get_suspicious_players(&self) -> Vec<String> {
        self.suspicious_players.lock().unwrap().clone()
    }
    
    pub fn calculate_risk_score(&self, player_id: &str) -> f32 {
        let history = self.action_history.lock().unwrap();
        
        let mut risk_score = 0.0;
        
        // Check if player is flagged as suspicious
        if self.get_suspicious_players().contains(&player_id.to_string()) {
            risk_score += 0.5;
        }
        
        // Check action patterns
        if let Some(player_history) = history.get(player_id) {
            // Check for automated behavior (consistent timing)
            if player_history.len() > 10 {
                let mut intervals = Vec::new();
                for i in 1..player_history.len() {
                    let interval = player_history[i].timestamp.duration_since(player_history[i-1].timestamp);
                    intervals.push(interval.as_millis() as f64);
                }
                
                // Calculate variance in intervals
                let mean = intervals.iter().sum::<f64>() / intervals.len() as f64;
                let variance = intervals.iter()
                    .map(|x| (x - mean).powi(2))
                    .sum::<f64>() / intervals.len() as f64;
                
                // Low variance suggests automation
                if variance < 100.0 {  // Less than 100ms variance
                    risk_score += 0.3;
                }
            }
            
            // Check for rapid actions
            let recent_actions = player_history.iter()
                .filter(|a| a.timestamp.elapsed() < Duration::from_secs(60))
                .count();
            
            if recent_actions > 20 {
                risk_score += 0.2;
            }
        }
        
        risk_score.min(1.0)
    }
}

// ============================================================================
// GAME THEORY MODELS
// ============================================================================

pub struct GameTheory;

impl GameTheory {
    /// Calculate Nash equilibrium for competitive events
    pub fn nash_equilibrium_competition(
        entry_cost: f64,
        prize_pool: f64,
        expected_players: f64,
    ) -> (f64, f64) {
        // Participation rate where expected value = 0
        let break_even_players = prize_pool / entry_cost;
        let participation_rate = (break_even_players / expected_players).min(1.0);
        
        // Expected value per player
        let expected_value = if participation_rate > 0.0 {
            (prize_pool / (expected_players * participation_rate)) - entry_cost
        } else {
            -entry_cost
        };
        
        (participation_rate, expected_value)
    }
    
    /// Optimal pricing for battle pass using price elasticity
    pub fn optimal_battle_pass_price(
        base_demand: f64,
        price_elasticity: f64,  // Typically negative
        marginal_cost: f64,
    ) -> f64 {
        // Monopoly pricing formula: P = MC * (e / (e + 1))
        // where e is price elasticity (negative)
        marginal_cost * (price_elasticity / (price_elasticity + 1.0)).abs()
    }
    
    /// Calculate optimal reward distribution for retention
    pub fn optimal_reward_schedule(
        total_rewards: f64,
        days: u32,
        retention_curve: &[f64],
    ) -> Vec<f64> {
        let mut rewards = vec![0.0; days as usize];
        
        // Allocate more rewards to critical retention points
        for (day, &retention) in retention_curve.iter().enumerate() {
            if day >= days as usize {
                break;
            }
            
            // Weight rewards by inverse of retention (help struggling points)
            let weight = 1.0 - retention;
            rewards[day] = weight;
        }
        
        // Normalize to total rewards
        let sum: f64 = rewards.iter().sum();
        if sum > 0.0 {
            for reward in &mut rewards {
                *reward = (*reward / sum) * total_rewards;
            }
        }
        
        rewards
    }
}

// ============================================================================
// ECONOMY MANAGER
// ============================================================================

pub struct EconomyManager {
    pub wallet: Arc<Mutex<Wallet>>,
    pub iap_store: Arc<Mutex<IAPStore>>,
    pub anti_fraud: Arc<AntiFraudSystem>,
    pub daily_limits: Arc<Mutex<HashMap<String, u32>>>,
    pub metrics: Arc<Mutex<EconomyMetrics>>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct EconomyMetrics {
    pub total_revenue: f64,
    pub total_purchases: u32,
    pub currency_generated: HashMap<Currency, u64>,
    pub currency_sunk: HashMap<Currency, u64>,
    pub average_session_value: f64,
    pub conversion_rate: f32,
}

impl EconomyManager {
    pub fn new() -> Self {
        Self {
            wallet: Arc::new(Mutex::new(Wallet::new())),
            iap_store: Arc::new(Mutex::new(IAPStore::new())),
            anti_fraud: Arc::new(AntiFraudSystem::new()),
            daily_limits: Arc::new(Mutex::new(HashMap::new())),
            metrics: Arc::new(Mutex::new(EconomyMetrics::default())),
        }
    }
    
    pub fn process_puzzle_completion(
        &self,
        player_id: String,
        difficulty: u32,
        solve_time_ms: u64,
    ) -> Result<u64, String> {
        // Anti-fraud check
        let action = PlayerAction {
            player_id: player_id.clone(),
            action_type: "puzzle_complete".to_string(),
            timestamp: Instant::now(),
            metadata: {
                let mut m = HashMap::new();
                m.insert("difficulty".to_string(), difficulty.to_string());
                m.insert("solve_time_ms".to_string(), solve_time_ms.to_string());
                m
            },
        };
        
        self.anti_fraud.check_action(action)?;
        
        // Calculate rewards
        let base_reward = 10u64;
        let difficulty_bonus = difficulty as u64 * 5;
        let time_bonus = if solve_time_ms < 5000 { 5 } else if solve_time_ms < 10000 { 3 } else { 0 };
        
        let total_reward = base_reward + difficulty_bonus + time_bonus;
        
        // Update wallet
        let mut wallet = self.wallet.lock().unwrap();
        wallet.add(Currency::Coins, total_reward);
        
        // Update metrics
        let mut metrics = self.metrics.lock().unwrap();
        *metrics.currency_generated.entry(Currency::Coins).or_insert(0) += total_reward;
        
        Ok(total_reward)
    }
    
    pub fn purchase_iap(
        &self,
        player_id: String,
        bundle_id: String,
        transaction_id: String,
    ) -> Result<u64, String> {
        // Anti-fraud check
        let action = PlayerAction {
            player_id: player_id.clone(),
            action_type: "purchase".to_string(),
            timestamp: Instant::now(),
            metadata: {
                let mut m = HashMap::new();
                m.insert("bundle_id".to_string(), bundle_id.clone());
                m.insert("transaction_id".to_string(), transaction_id.clone());
                m
            },
        };
        
        self.anti_fraud.check_action(action)?;
        
        // Process purchase
        let mut store = self.iap_store.lock().unwrap();
        let crystals = store.process_purchase(player_id, bundle_id.clone(), transaction_id)?;
        
        // Update wallet
        let mut wallet = self.wallet.lock().unwrap();
        wallet.add(Currency::HopeCrystals, crystals);
        
        // Update metrics
        let mut metrics = self.metrics.lock().unwrap();
        metrics.total_purchases += 1;
        *metrics.currency_generated.entry(Currency::HopeCrystals).or_insert(0) += crystals;
        
        // Find bundle price for revenue tracking
        if let Some(bundle) = store.get_bundles().iter().find(|b| b.id == bundle_id) {
            metrics.total_revenue += bundle.price_usd;
        }
        
        Ok(crystals)
    }
    
    pub fn use_energy(&self, amount: u32) -> Result<(), String> {
        let mut wallet = self.wallet.lock().unwrap();
        wallet.spend(Currency::Energy, amount as u64)?;
        
        // Update metrics
        let mut metrics = self.metrics.lock().unwrap();
        *metrics.currency_sunk.entry(Currency::Energy).or_insert(0) += amount as u64;
        
        Ok(())
    }
    
    pub fn exchange_crystals_for_energy(&self, crystals: u64) -> Result<u32, String> {
        // Exchange rate: 10 crystals = 10 energy
        let energy_amount = (crystals / 10) as u32 * 10;
        
        if energy_amount == 0 {
            return Err("Insufficient crystals for exchange".to_string());
        }
        
        let mut wallet = self.wallet.lock().unwrap();
        wallet.spend(Currency::HopeCrystals, crystals)?;
        wallet.add(Currency::Energy, energy_amount as u64);
        
        // Update metrics
        let mut metrics = self.metrics.lock().unwrap();
        *metrics.currency_sunk.entry(Currency::HopeCrystals).or_insert(0) += crystals;
        *metrics.currency_generated.entry(Currency::Energy).or_insert(0) += energy_amount as u64;
        
        Ok(energy_amount)
    }
    
    pub fn get_economy_report(&self) -> EconomyMetrics {
        self.metrics.lock().unwrap().clone()
    }
    
    pub fn get_player_risk_score(&self, player_id: &str) -> f32 {
        self.anti_fraud.calculate_risk_score(player_id)
    }
}

// ============================================================================
// SUBSCRIPTION SYSTEM
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Subscription {
    pub id: String,
    pub name: String,
    pub price_usd: f64,
    pub duration_days: u32,
    pub benefits: SubscriptionBenefits,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubscriptionBenefits {
    pub energy_cap_bonus: u32,
    pub energy_regen_multiplier: f32,
    pub coin_multiplier: f32,
    pub daily_crystals: u64,
    pub ad_free: bool,
}

pub struct SubscriptionManager {
    subscriptions: Vec<Subscription>,
    active_subscriptions: Arc<Mutex<HashMap<String, ActiveSubscription>>>,
}

#[derive(Debug, Clone)]
struct ActiveSubscription {
    subscription_id: String,
    start_time: SystemTime,
    end_time: SystemTime,
    last_daily_claim: Option<SystemTime>,
}

impl SubscriptionManager {
    pub fn new() -> Self {
        let subscriptions = vec![
            Subscription {
                id: "vip_weekly".to_string(),
                name: "VIP Weekly Pass".to_string(),
                price_usd: 4.99,
                duration_days: 7,
                benefits: SubscriptionBenefits {
                    energy_cap_bonus: 25,
                    energy_regen_multiplier: 2.0,
                    coin_multiplier: 1.5,
                    daily_crystals: 50,
                    ad_free: true,
                },
            },
            Subscription {
                id: "premium_monthly".to_string(),
                name: "Premium Monthly Pass".to_string(),
                price_usd: 9.99,
                duration_days: 30,
                benefits: SubscriptionBenefits {
                    energy_cap_bonus: 50,
                    energy_regen_multiplier: 3.0,
                    coin_multiplier: 2.0,
                    daily_crystals: 100,
                    ad_free: true,
                },
            },
        ];
        
        Self {
            subscriptions,
            active_subscriptions: Arc::new(Mutex::new(HashMap::new())),
        }
    }
    
    pub fn activate_subscription(
        &self,
        player_id: String,
        subscription_id: String,
    ) -> Result<(), String> {
        let subscription = self.subscriptions.iter()
            .find(|s| s.id == subscription_id)
            .ok_or("Invalid subscription ID")?;
        
        let now = SystemTime::now();
        let duration = Duration::from_secs(subscription.duration_days as u64 * 86400);
        
        let active = ActiveSubscription {
            subscription_id: subscription_id.clone(),
            start_time: now,
            end_time: now + duration,
            last_daily_claim: None,
        };
        
        self.active_subscriptions.lock().unwrap()
            .insert(player_id, active);
        
        Ok(())
    }
    
    pub fn claim_daily_rewards(
        &self,
        player_id: &str,
    ) -> Result<u64, String> {
        let mut subs = self.active_subscriptions.lock().unwrap();
        
        let active = subs.get_mut(player_id)
            .ok_or("No active subscription")?;
        
        let now = SystemTime::now();
        
        // Check if subscription is still valid
        if now > active.end_time {
            subs.remove(player_id);
            return Err("Subscription expired".to_string());
        }
        
        // Check if daily reward was already claimed
        if let Some(last_claim) = active.last_daily_claim {
            let elapsed = now.duration_since(last_claim)
                .map_err(|_| "Time error")?;
            
            if elapsed < Duration::from_secs(86400) {
                return Err("Daily reward already claimed".to_string());
            }
        }
        
        // Find subscription benefits
        let subscription = self.subscriptions.iter()
            .find(|s| s.id == active.subscription_id)
            .ok_or("Subscription not found")?;
        
        active.last_daily_claim = Some(now);
        
        Ok(subscription.benefits.daily_crystals)
    }
    
    pub fn get_active_benefits(&self, player_id: &str) -> Option<SubscriptionBenefits> {
        let subs = self.active_subscriptions.lock().unwrap();
        
        if let Some(active) = subs.get(player_id) {
            let now = SystemTime::now();
            
            if now <= active.end_time {
                return self.subscriptions.iter()
                    .find(|s| s.id == active.subscription_id)
                    .map(|s| s.benefits.clone());
            }
        }
        
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_wallet_operations() {
        let mut wallet = Wallet::new();
        
        // Test adding currency
        wallet.add(Currency::Coins, 100);
        assert_eq!(wallet.coins, 100);
        
        // Test spending currency
        assert!(wallet.spend(Currency::Coins, 50).is_ok());
        assert_eq!(wallet.coins, 50);
        
        // Test insufficient funds
        assert!(wallet.spend(Currency::Coins, 100).is_err());
        
        // Test energy cap
        wallet.add(Currency::Energy, 100);
        assert_eq!(wallet.energy, wallet.energy_cap);
    }
    
    #[test]
    fn test_bonding_curves() {
        let linear = BondingCurve::linear(10.0, 1.0, 5.0);
        assert_eq!(linear, 15.0);
        
        let quadratic = BondingCurve::quadratic(10.0, 0.1, 1.0, 5.0);
        assert_eq!(quadratic, 25.0);
        
        let sigmoid = BondingCurve::sigmoid(50.0, 100.0, 0.1, 50.0);
        assert!((sigmoid - 50.0).abs() < 1.0);
    }
    
    #[test]
    fn test_anti_fraud() {
        let anti_fraud = AntiFraudSystem::new();
        
        // Test normal action
        let action = PlayerAction {
            player_id: "player1".to_string(),
            action_type: "puzzle_complete".to_string(),
            timestamp: Instant::now(),
            metadata: {
                let mut m = HashMap::new();
                m.insert("solve_time_ms".to_string(), "5000".to_string());
                m.insert("difficulty".to_string(), "3".to_string());
                m
            },
        };
        
        assert!(anti_fraud.check_action(action).is_ok());
        
        // Test exploit detection
        let exploit_action = PlayerAction {
            player_id: "player2".to_string(),
            action_type: "puzzle_complete".to_string(),
            timestamp: Instant::now(),
            metadata: {
                let mut m = HashMap::new();
                m.insert("solve_time_ms".to_string(), "100".to_string());
                m.insert("difficulty".to_string(), "5".to_string());
                m
            },
        };
        
        assert!(anti_fraud.check_action(exploit_action).is_err());
    }
    
    #[test]
    fn test_game_theory() {
        let (participation, value) = GameTheory::nash_equilibrium_competition(
            10.0, 1000.0, 200.0
        );
        
        assert!(participation > 0.0 && participation <= 1.0);
        
        let optimal_price = GameTheory::optimal_battle_pass_price(
            1000.0, -1.5, 0.3
        );
        
        assert!(optimal_price > 0.0);
    }
}