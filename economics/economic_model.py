#!/usr/bin/env python3
"""
Hope Economic Model - Game Theory and Token Economics
Mathematical modeling and simulation of the Hope game economy
"""

import numpy as np
from scipy.optimize import minimize, differential_evolution
from scipy.stats import beta, gamma, pareto
# import matplotlib.pyplot as plt  # Optional for visualization
from dataclasses import dataclass
from typing import Tuple, Dict, List, Optional
import json
import hashlib
from enum import Enum

# ============================================================================
# CORE ECONOMIC PARAMETERS
# ============================================================================

class Currency(Enum):
    """Three-token economy for Hope"""
    HOPE_CRYSTALS = "hope_crystals"  # Premium currency (purchased)
    ENERGY = "energy"                 # Gameplay currency (regenerates)
    COINS = "coins"                   # Soft currency (earned)

class PlayerType(Enum):
    """Player segmentation for economic modeling"""
    WHALE = "whale"           # High spenders ($100+/month)
    DOLPHIN = "dolphin"       # Medium spenders ($20-100/month)
    MINNOW = "minnow"        # Low spenders ($1-20/month)
    F2P = "f2p"              # Free-to-play (no spending)

@dataclass
class EconomicParameters:
    """Core economic parameters for the game"""
    # Currency generation rates
    energy_regen_rate: float = 1.0 / 300  # 1 energy per 5 minutes
    energy_cap: int = 50                   # Maximum energy storage
    coins_per_puzzle: float = 10.0         # Base coin reward
    
    # Exchange rates (in USD)
    crystal_price_usd: float = 0.01        # $0.01 per crystal
    crystal_bundle_sizes: Dict = None      # Bundle pricing
    
    # Sink rates
    energy_per_play: int = 1               # Energy cost per puzzle
    coin_sink_rate: float = 0.3            # 30% of coins are sunk
    
    # Player distribution
    player_distribution: Dict = None       # Player type distribution
    
    # Monetization parameters
    ad_revenue_per_view: float = 0.003     # $0.003 per ad
    battle_pass_price: float = 9.99        # Monthly pass
    vip_subscription_price: float = 4.99   # Weekly VIP
    
    def __post_init__(self):
        if self.crystal_bundle_sizes is None:
            self.crystal_bundle_sizes = {
                100: 0.99,    # $0.99 for 100 crystals
                550: 4.99,    # $4.99 for 550 crystals (10% bonus)
                1200: 9.99,   # $9.99 for 1200 crystals (20% bonus)
                2500: 19.99,  # $19.99 for 2500 crystals (25% bonus)
                6500: 49.99,  # $49.99 for 6500 crystals (30% bonus)
                14000: 99.99  # $99.99 for 14000 crystals (40% bonus)
            }
        
        if self.player_distribution is None:
            self.player_distribution = {
                PlayerType.WHALE: 0.002,    # 0.2% whales
                PlayerType.DOLPHIN: 0.018,  # 1.8% dolphins
                PlayerType.MINNOW: 0.08,    # 8% minnows
                PlayerType.F2P: 0.90        # 90% F2P
            }

# ============================================================================
# BONDING CURVE MECHANICS
# ============================================================================

class BondingCurve:
    """
    Implements various bonding curve models for dynamic pricing
    Used for limited-time offers and special items
    """
    
    @staticmethod
    def linear(supply: float, k: float = 1.0, b: float = 0) -> float:
        """Linear bonding curve: price = k * supply + b"""
        return k * supply + b
    
    @staticmethod
    def quadratic(supply: float, a: float = 0.01, b: float = 1.0, c: float = 0) -> float:
        """Quadratic bonding curve: price = a * supply^2 + b * supply + c"""
        return a * supply**2 + b * supply + c
    
    @staticmethod
    def exponential(supply: float, a: float = 1.0, k: float = 0.001) -> float:
        """Exponential bonding curve: price = a * e^(k * supply)"""
        return a * np.exp(k * supply)
    
    @staticmethod
    def sigmoid(supply: float, L: float = 100, k: float = 0.1, x0: float = 50) -> float:
        """Sigmoid bonding curve: price = L / (1 + e^(-k*(supply-x0)))"""
        return L / (1 + np.exp(-k * (supply - x0)))
    
    @staticmethod
    def optimal_curve_parameters(
        target_revenue: float,
        max_supply: float,
        curve_type: str = "quadratic"
    ) -> Dict:
        """
        Calculate optimal bonding curve parameters for target revenue
        """
        def objective(params):
            if curve_type == "quadratic":
                a, b, c = params
                revenues = [BondingCurve.quadratic(s, a, b, c) * s 
                           for s in np.linspace(0, max_supply, 100)]
            elif curve_type == "exponential":
                a, k = params
                revenues = [BondingCurve.exponential(s, a, k) * s 
                           for s in np.linspace(0, max_supply, 100)]
            else:
                raise ValueError(f"Unknown curve type: {curve_type}")
            
            total_revenue = np.sum(revenues) / 100 * max_supply
            return abs(total_revenue - target_revenue)
        
        if curve_type == "quadratic":
            bounds = [(0, 1), (0, 10), (0, 10)]
            x0 = [0.01, 1.0, 0]
        else:
            bounds = [(0, 10), (0, 0.1)]
            x0 = [1.0, 0.001]
        
        result = minimize(objective, x0, bounds=bounds, method='L-BFGS-B')
        return dict(zip(['a', 'b', 'c'][:len(result.x)], result.x))

# ============================================================================
# GAME THEORY MODELS
# ============================================================================

class GameTheoryAnalyzer:
    """
    Analyzes game-theoretic aspects of the economy
    """
    
    @staticmethod
    def nash_equilibrium_pvp(
        reward_pool: float,
        entry_cost: float,
        skill_distribution: np.ndarray
    ) -> Tuple[float, float]:
        """
        Calculate Nash equilibrium for PvP participation
        Returns: (participation_rate, expected_value)
        """
        n_players = len(skill_distribution)
        
        def expected_utility(p_participate: float) -> float:
            """Expected utility for a player given participation rate"""
            n_participating = p_participate * n_players
            if n_participating < 2:
                return -entry_cost
            
            # Probability of winning based on skill
            win_prob = skill_distribution / np.sum(skill_distribution)
            expected_reward = reward_pool / n_participating
            
            return np.mean(win_prob * expected_reward - entry_cost)
        
        # Find equilibrium participation rate
        p_star = 0.5  # Initial guess
        for _ in range(100):
            eu = expected_utility(p_star)
            if eu > 0:
                p_star = min(1.0, p_star * 1.1)
            elif eu < 0:
                p_star = max(0.0, p_star * 0.9)
            else:
                break
        
        return p_star, expected_utility(p_star)
    
    @staticmethod
    def prisoners_dilemma_guild_rewards(
        cooperation_bonus: float = 1.5,
        defection_penalty: float = 0.5
    ) -> Dict:
        """
        Model guild cooperation as iterated prisoner's dilemma
        """
        # Payoff matrix
        payoff_matrix = {
            ('cooperate', 'cooperate'): (cooperation_bonus, cooperation_bonus),
            ('cooperate', 'defect'): (defection_penalty, cooperation_bonus * 1.2),
            ('defect', 'cooperate'): (cooperation_bonus * 1.2, defection_penalty),
            ('defect', 'defect'): (1.0, 1.0)
        }
        
        # Tit-for-tat strategy dominance
        strategies = ['always_cooperate', 'always_defect', 'tit_for_tat', 'random']
        results = {}
        
        for strat1 in strategies:
            for strat2 in strategies:
                score1, score2 = GameTheoryAnalyzer._simulate_iterated_game(
                    strat1, strat2, payoff_matrix, rounds=100
                )
                results[f"{strat1}_vs_{strat2}"] = (score1, score2)
        
        return results
    
    @staticmethod
    def _simulate_iterated_game(
        strategy1: str,
        strategy2: str,
        payoff_matrix: Dict,
        rounds: int = 100
    ) -> Tuple[float, float]:
        """Simulate iterated prisoner's dilemma"""
        score1, score2 = 0, 0
        history1, history2 = [], []
        
        for _ in range(rounds):
            # Determine moves
            if strategy1 == 'always_cooperate':
                move1 = 'cooperate'
            elif strategy1 == 'always_defect':
                move1 = 'defect'
            elif strategy1 == 'tit_for_tat':
                move1 = 'cooperate' if not history2 or history2[-1] == 'cooperate' else 'defect'
            else:  # random
                move1 = np.random.choice(['cooperate', 'defect'])
            
            if strategy2 == 'always_cooperate':
                move2 = 'cooperate'
            elif strategy2 == 'always_defect':
                move2 = 'defect'
            elif strategy2 == 'tit_for_tat':
                move2 = 'cooperate' if not history1 or history1[-1] == 'cooperate' else 'defect'
            else:  # random
                move2 = np.random.choice(['cooperate', 'defect'])
            
            # Update scores
            pay1, pay2 = payoff_matrix[(move1, move2)]
            score1 += pay1
            score2 += pay2
            
            # Update history
            history1.append(move1)
            history2.append(move2)
        
        return score1 / rounds, score2 / rounds

# ============================================================================
# ECONOMIC SIMULATION ENGINE
# ============================================================================

class EconomicSimulator:
    """
    Simulates the game economy over time with player behaviors
    """
    
    def __init__(self, params: EconomicParameters, n_players: int = 10000):
        self.params = params
        self.n_players = n_players
        self.players = self._initialize_players()
        self.time = 0
        self.history = {
            'revenue': [],
            'dau': [],
            'currency_supply': [],
            'currency_sinks': [],
            'player_satisfaction': []
        }
    
    def _initialize_players(self) -> List[Dict]:
        """Initialize player population with types and behaviors"""
        players = []
        
        for player_type, proportion in self.params.player_distribution.items():
            n = int(self.n_players * proportion)
            
            for _ in range(n):
                if player_type == PlayerType.WHALE:
                    spending_rate = gamma.rvs(2, scale=50)  # $100+ per month
                    skill = beta.rvs(5, 2)  # High skill
                    sessions_per_day = np.random.poisson(5) + 3
                elif player_type == PlayerType.DOLPHIN:
                    spending_rate = gamma.rvs(2, scale=10)  # $20-100 per month
                    skill = beta.rvs(3, 3)  # Medium skill
                    sessions_per_day = np.random.poisson(3) + 2
                elif player_type == PlayerType.MINNOW:
                    spending_rate = gamma.rvs(1.5, scale=3)  # $1-20 per month
                    skill = beta.rvs(2, 4)  # Lower skill
                    sessions_per_day = np.random.poisson(2) + 1
                else:  # F2P
                    spending_rate = 0
                    skill = beta.rvs(2, 5)  # Lowest skill
                    sessions_per_day = np.random.poisson(1) + 1
                
                players.append({
                    'type': player_type,
                    'spending_rate': spending_rate,
                    'skill': skill,
                    'sessions_per_day': sessions_per_day,
                    'crystals': 0,
                    'energy': self.params.energy_cap,
                    'coins': 0,
                    'satisfaction': 0.5,
                    'retention_probability': 0.8,
                    'days_played': 0
                })
        
        return players
    
    def simulate_day(self) -> Dict:
        """Simulate one day of economic activity"""
        daily_revenue = 0
        daily_active_users = 0
        currency_generated = {'crystals': 0, 'energy': 0, 'coins': 0}
        currency_sunk = {'crystals': 0, 'energy': 0, 'coins': 0}
        
        for player in self.players:
            # Check retention
            if np.random.random() > player['retention_probability']:
                continue  # Player churned
            
            daily_active_users += 1
            player['days_played'] += 1
            
            # Process sessions
            for _ in range(player['sessions_per_day']):
                # Energy regeneration
                player['energy'] = min(
                    self.params.energy_cap,
                    player['energy'] + self.params.energy_regen_rate * 300  # 5 min per session
                )
                currency_generated['energy'] += self.params.energy_regen_rate * 300
                
                # Play puzzles
                puzzles_played = min(int(player['energy']), np.random.poisson(3))
                if puzzles_played > 0:
                    player['energy'] -= puzzles_played * self.params.energy_per_play
                    currency_sunk['energy'] += puzzles_played * self.params.energy_per_play
                    
                    # Earn coins
                    coins_earned = puzzles_played * self.params.coins_per_puzzle * (1 + player['skill'])
                    player['coins'] += coins_earned
                    currency_generated['coins'] += coins_earned
                
                # Spending behavior
                if player['type'] != PlayerType.F2P:
                    if np.random.random() < 0.1:  # 10% chance to spend per session
                        # Purchase crystals
                        spend_amount = np.random.exponential(player['spending_rate'] / 30)
                        best_bundle = self._find_best_bundle(spend_amount)
                        if best_bundle:
                            crystals, price = best_bundle
                            player['crystals'] += crystals
                            currency_generated['crystals'] += crystals
                            daily_revenue += price
                
                # Use crystals (energy refill, cosmetics, etc.)
                if player['crystals'] > 0 and np.random.random() < 0.2:
                    crystals_used = min(player['crystals'], np.random.randint(10, 50))
                    player['crystals'] -= crystals_used
                    currency_sunk['crystals'] += crystals_used
                
                # Watch ads (F2P and Minnows)
                if player['type'] in [PlayerType.F2P, PlayerType.MINNOW]:
                    if np.random.random() < 0.3:  # 30% chance to watch ad
                        daily_revenue += self.params.ad_revenue_per_view
                        player['energy'] += 1  # Bonus energy from ad
            
            # Update satisfaction and retention
            self._update_player_satisfaction(player)
        
        # Record metrics
        self.history['revenue'].append(daily_revenue)
        self.history['dau'].append(daily_active_users)
        self.history['currency_supply'].append(currency_generated)
        self.history['currency_sinks'].append(currency_sunk)
        self.history['player_satisfaction'].append(
            np.mean([p['satisfaction'] for p in self.players if p['days_played'] > 0])
        )
        
        self.time += 1
        
        return {
            'day': self.time,
            'revenue': daily_revenue,
            'dau': daily_active_users,
            'arpdau': daily_revenue / max(daily_active_users, 1),
            'currency_inflation': (
                currency_generated['coins'] - currency_sunk['coins']
            ) / max(currency_generated['coins'], 1)
        }
    
    def _find_best_bundle(self, budget: float) -> Optional[Tuple[int, float]]:
        """Find the best crystal bundle within budget"""
        affordable = [(c, p) for c, p in self.params.crystal_bundle_sizes.items() if p <= budget]
        if affordable:
            return max(affordable, key=lambda x: x[0])  # Most crystals for budget
        return None
    
    def _update_player_satisfaction(self, player: Dict):
        """Update player satisfaction based on progress and spending"""
        # Satisfaction factors
        progress_factor = min(1.0, player['coins'] / 1000)  # Progress feeling
        value_factor = 1.0 if player['type'] == PlayerType.F2P else (
            player['crystals'] / max(player['spending_rate'] * 100, 1)
        )
        skill_factor = player['skill']
        
        # Update satisfaction (weighted average)
        new_satisfaction = 0.3 * progress_factor + 0.4 * value_factor + 0.3 * skill_factor
        player['satisfaction'] = 0.9 * player['satisfaction'] + 0.1 * new_satisfaction
        
        # Update retention probability based on satisfaction
        player['retention_probability'] = 0.5 + 0.5 * player['satisfaction']
    
    def run_simulation(self, days: int = 30):
        """Run economic simulation for specified days"""
        results = []
        
        for _ in range(days):
            day_results = self.simulate_day()
            results.append(day_results)
        
        return results  # Return as list of dicts instead of DataFrame

# ============================================================================
# ANTI-FRAUD MECHANISMS
# ============================================================================

class AntiFraudSystem:
    """
    Implements economic exploit detection and prevention
    """
    
    def __init__(self):
        self.suspicious_patterns = []
        self.banned_accounts = set()
        self.rate_limits = {
            'puzzles_per_hour': 30,
            'purchases_per_day': 10,
            'currency_transfers_per_day': 5
        }
    
    def detect_exploit(self, player_action: Dict) -> Tuple[bool, str]:
        """
        Detect potential economic exploits
        Returns: (is_exploit, reason)
        """
        
        # Check for impossible puzzle solve times
        if player_action.get('action') == 'puzzle_complete':
            solve_time = player_action.get('solve_time_ms', float('inf'))
            difficulty = player_action.get('difficulty', 1)
            
            min_possible_time = 500 * difficulty  # 500ms per difficulty level minimum
            if solve_time < min_possible_time:
                return True, f"Impossible solve time: {solve_time}ms for difficulty {difficulty}"
        
        # Check for currency generation exploits
        if player_action.get('action') == 'currency_earned':
            amount = player_action.get('amount', 0)
            expected_max = player_action.get('expected_max', 100)
            
            if amount > expected_max * 1.5:  # 50% tolerance
                return True, f"Excessive currency generation: {amount} (max: {expected_max})"
        
        # Check for rapid-fire actions (rate limiting)
        if player_action.get('action') in ['puzzle_complete', 'purchase', 'transfer']:
            timestamp = player_action.get('timestamp', 0)
            player_id = player_action.get('player_id')
            
            if self._check_rate_limit(player_id, player_action['action'], timestamp):
                return True, f"Rate limit exceeded for {player_action['action']}"
        
        # Check for pattern-based exploits
        if self._detect_pattern_exploit(player_action):
            return True, "Suspicious pattern detected"
        
        return False, ""
    
    def _check_rate_limit(self, player_id: str, action: str, timestamp: float) -> bool:
        """Check if action exceeds rate limits"""
        # Implementation would track action timestamps per player
        # This is a simplified version
        action_key = f"{player_id}_{action}"
        
        # In production, maintain a sliding window of timestamps
        # For now, return False (no violation)
        return False
    
    def _detect_pattern_exploit(self, player_action: Dict) -> bool:
        """Detect exploit patterns using statistical analysis"""
        # Check for automated behavior patterns
        if player_action.get('action') == 'puzzle_complete':
            moves = player_action.get('moves', [])
            
            # Check for inhuman consistency
            if len(moves) > 5:
                timestamps = [m.get('timestamp_ms', 0) for m in moves]
                intervals = np.diff(timestamps)
                
                # If intervals are too consistent (low variance), likely a bot
                if len(intervals) > 0 and np.std(intervals) < 10:  # Less than 10ms variance
                    return True
        
        return False
    
    def calculate_risk_score(self, player_profile: Dict) -> float:
        """
        Calculate economic risk score for a player
        Returns: risk score from 0 (safe) to 1 (high risk)
        """
        risk_factors = []
        
        # New account risk
        account_age = player_profile.get('account_age_days', 0)
        if account_age < 1:
            risk_factors.append(0.3)
        elif account_age < 7:
            risk_factors.append(0.1)
        
        # Unusual spending patterns
        spending_history = player_profile.get('spending_history', [])
        if spending_history:
            mean_spending = np.mean(spending_history)
            recent_spending = spending_history[-1] if spending_history else 0
            
            if recent_spending > mean_spending * 5:  # 5x normal spending
                risk_factors.append(0.4)
        
        # Rapid progression
        progression_rate = player_profile.get('progression_rate', 0)
        expected_rate = player_profile.get('expected_progression_rate', 1)
        
        if progression_rate > expected_rate * 2:
            risk_factors.append(0.3)
        
        # Geographic anomalies
        if player_profile.get('vpn_detected', False):
            risk_factors.append(0.2)
        
        if player_profile.get('country_changed', False):
            risk_factors.append(0.3)
        
        # Calculate weighted risk score
        if risk_factors:
            return min(1.0, sum(risk_factors))
        return 0.0

# ============================================================================
# REVENUE OPTIMIZATION
# ============================================================================

class RevenueOptimizer:
    """
    Optimizes pricing and monetization strategies
    """
    
    @staticmethod
    def optimize_iap_pricing(
        price_elasticity: float = -1.5,
        base_demand: float = 1000,
        marginal_cost: float = 0.3
    ) -> Dict:
        """
        Optimize IAP pricing using price elasticity of demand
        """
        # Price elasticity: % change in quantity / % change in price
        # Optimal price = marginal_cost * (elasticity / (elasticity + 1))
        
        optimal_prices = {}
        
        for bundle_size, current_price in EconomicParameters().crystal_bundle_sizes.items():
            # Calculate optimal price
            optimal_price = marginal_cost * abs(price_elasticity / (price_elasticity + 1))
            
            # Adjust for bundle size (bulk discount)
            size_factor = np.log10(bundle_size) / 4  # Logarithmic scaling
            adjusted_price = optimal_price * (1 - size_factor * 0.1)  # Up to 10% bulk discount
            
            # Calculate expected revenue
            quantity = base_demand * (adjusted_price / current_price) ** price_elasticity
            revenue = quantity * adjusted_price
            
            optimal_prices[bundle_size] = {
                'current_price': current_price,
                'optimal_price': round(adjusted_price, 2),
                'expected_quantity': int(quantity),
                'expected_revenue': round(revenue, 2)
            }
        
        return optimal_prices
    
    @staticmethod
    def calculate_ltv(
        arpu: float,
        retention_curve: List[float],
        discount_rate: float = 0.1
    ) -> float:
        """
        Calculate player Lifetime Value (LTV)
        """
        ltv = 0
        for day, retention in enumerate(retention_curve):
            # Discounted cash flow
            daily_value = arpu * retention / (1 + discount_rate/365) ** day
            ltv += daily_value
        
        return ltv
    
    @staticmethod
    def optimize_ad_frequency(
        base_retention: float = 0.8,
        ad_annoyance_factor: float = 0.05,
        revenue_per_ad: float = 0.003
    ) -> Dict:
        """
        Find optimal ad frequency balancing revenue and retention
        """
        def objective(ads_per_session):
            # Retention decreases with more ads
            retention = base_retention * np.exp(-ad_annoyance_factor * ads_per_session)
            
            # Revenue increases with more ads (but diminishing returns)
            ad_completion_rate = 1 / (1 + 0.2 * ads_per_session)  # Decreases with fatigue
            revenue = ads_per_session * revenue_per_ad * ad_completion_rate
            
            # LTV = revenue * expected_sessions * retention
            expected_sessions = 30 * retention  # 30 days
            ltv = revenue * expected_sessions
            
            return -ltv  # Negative for minimization
        
        # Optimize
        result = minimize(objective, x0=[3], bounds=[(0, 10)], method='L-BFGS-B')
        optimal_ads = result.x[0]
        
        # Calculate metrics at optimum
        retention_at_optimum = base_retention * np.exp(-ad_annoyance_factor * optimal_ads)
        revenue_at_optimum = optimal_ads * revenue_per_ad / (1 + 0.2 * optimal_ads)
        ltv_at_optimum = -result.fun
        
        return {
            'optimal_ads_per_session': round(optimal_ads, 1),
            'retention_rate': round(retention_at_optimum, 3),
            'revenue_per_session': round(revenue_at_optimum, 4),
            'ltv': round(ltv_at_optimum, 2)
        }

# ============================================================================
# MARKET ANALYSIS
# ============================================================================

class MarketAnalyzer:
    """
    Analyzes market conditions and competitor strategies
    """
    
    @staticmethod
    def competitor_pricing_analysis() -> Dict:
        """
        Analyze competitor pricing strategies for similar games
        """
        # Simplified competitor data (in production, would be from real market data)
        competitors = {
            'Puzzle Game A': {
                'iap_prices': [0.99, 4.99, 9.99, 19.99, 49.99, 99.99],
                'dau': 500000,
                'arpdau': 0.35,
                'retention_d7': 0.25
            },
            'Puzzle Game B': {
                'iap_prices': [1.99, 4.99, 14.99, 29.99, 59.99, 99.99],
                'dau': 300000,
                'arpdau': 0.42,
                'retention_d7': 0.30
            },
            'Puzzle Game C': {
                'iap_prices': [0.99, 2.99, 6.99, 14.99, 39.99, 79.99],
                'dau': 800000,
                'arpdau': 0.28,
                'retention_d7': 0.22
            }
        }
        
        # Calculate market statistics
        all_prices = []
        for game_data in competitors.values():
            all_prices.extend(game_data['iap_prices'])
        
        price_percentiles = {
            'p25': np.percentile(all_prices, 25),
            'p50': np.percentile(all_prices, 50),
            'p75': np.percentile(all_prices, 75)
        }
        
        avg_arpdau = np.mean([d['arpdau'] for d in competitors.values()])
        avg_retention = np.mean([d['retention_d7'] for d in competitors.values()])
        
        return {
            'price_percentiles': price_percentiles,
            'average_arpdau': round(avg_arpdau, 3),
            'average_d7_retention': round(avg_retention, 3),
            'recommended_pricing': MarketAnalyzer._recommend_pricing(price_percentiles, avg_arpdau)
        }
    
    @staticmethod
    def _recommend_pricing(percentiles: Dict, target_arpdau: float) -> Dict:
        """Generate pricing recommendations based on market analysis"""
        return {
            'starter_pack': round(percentiles['p25'], 2),      # Below market for acquisition
            'value_pack': round(percentiles['p50'] * 0.9, 2),  # Slightly below median
            'premium_pack': round(percentiles['p75'], 2),       # At 75th percentile
            'whale_pack': 99.99                                  # Standard whale pricing
        }
    
    @staticmethod
    def regional_pricing_optimization() -> Dict:
        """
        Optimize pricing for different regions based on purchasing power
        """
        # Purchasing Power Parity indices (relative to US = 1.0)
        ppp_indices = {
            'US': 1.0,
            'UK': 0.95,
            'Germany': 0.90,
            'Japan': 0.85,
            'South Korea': 0.80,
            'Brazil': 0.40,
            'India': 0.25,
            'Russia': 0.35,
            'Mexico': 0.45,
            'Indonesia': 0.30
        }
        
        base_price = 9.99  # US price
        regional_prices = {}
        
        for region, ppp in ppp_indices.items():
            # Adjust price based on PPP
            adjusted_price = base_price * ppp
            
            # Round to local pricing conventions
            if region in ['Japan', 'South Korea']:
                adjusted_price = round(adjusted_price * 100) / 100  # Yen/Won friendly
            else:
                adjusted_price = round(adjusted_price - 0.01, 2)  # .99 pricing
            
            regional_prices[region] = {
                'local_price': adjusted_price,
                'ppp_index': ppp,
                'discount_from_us': f"{(1 - ppp) * 100:.1f}%"
            }
        
        return regional_prices

# ============================================================================
# MAIN SIMULATION AND REPORTING
# ============================================================================

def run_comprehensive_simulation():
    """
    Run a comprehensive economic simulation and generate reports
    """
    
    print("=" * 80)
    print("HOPE GAME ECONOMIC SIMULATION")
    print("=" * 80)
    
    # Initialize parameters
    params = EconomicParameters()
    
    # 1. Run economic simulation
    print("\n1. RUNNING 30-DAY ECONOMIC SIMULATION...")
    simulator = EconomicSimulator(params, n_players=10000)
    results = simulator.run_simulation(days=30)
    
    total_revenue = sum(r['revenue'] for r in results)
    avg_dau = sum(r['dau'] for r in results) / len(results)
    avg_arpdau = sum(r['arpdau'] for r in results) / len(results)
    
    print(f"   Total Revenue: ${total_revenue:.2f}")
    print(f"   Average DAU: {avg_dau:.0f}")
    print(f"   ARPDAU: ${avg_arpdau:.4f}")
    
    # 2. Optimize pricing
    print("\n2. IAP PRICING OPTIMIZATION...")
    optimizer = RevenueOptimizer()
    optimal_prices = optimizer.optimize_iap_pricing()
    
    for bundle, pricing in list(optimal_prices.items())[:3]:
        print(f"   {bundle} crystals: ${pricing['current_price']:.2f} → ${pricing['optimal_price']:.2f}")
        print(f"      Expected Revenue: ${pricing['expected_revenue']:.2f}")
    
    # 3. Calculate optimal ad frequency
    print("\n3. AD MONETIZATION OPTIMIZATION...")
    ad_optimization = optimizer.optimize_ad_frequency()
    print(f"   Optimal Ads/Session: {ad_optimization['optimal_ads_per_session']}")
    print(f"   Retention Rate: {ad_optimization['retention_rate']*100:.1f}%")
    print(f"   LTV per Player: ${ad_optimization['ltv']:.2f}")
    
    # 4. Game theory analysis
    print("\n4. GAME THEORY ANALYSIS...")
    analyzer = GameTheoryAnalyzer()
    
    # PvP Nash equilibrium
    skill_dist = np.random.beta(2, 2, 100)
    participation, expected_value = analyzer.nash_equilibrium_pvp(
        reward_pool=1000, entry_cost=10, skill_distribution=skill_dist
    )
    print(f"   PvP Participation Rate: {participation*100:.1f}%")
    print(f"   Expected Value: {expected_value:.2f}")
    
    # 5. Anti-fraud system
    print("\n5. ANTI-FRAUD SYSTEM TEST...")
    anti_fraud = AntiFraudSystem()
    
    # Test various player actions
    test_actions = [
        {'action': 'puzzle_complete', 'solve_time_ms': 100, 'difficulty': 5},
        {'action': 'currency_earned', 'amount': 1000, 'expected_max': 100},
        {'action': 'puzzle_complete', 'solve_time_ms': 5000, 'difficulty': 5}
    ]
    
    for action in test_actions:
        is_exploit, reason = anti_fraud.detect_exploit(action)
        if is_exploit:
            print(f"   ⚠️ EXPLOIT DETECTED: {reason}")
        else:
            print(f"   ✓ Action legitimate: {action['action']}")
    
    # 6. Market analysis
    print("\n6. MARKET ANALYSIS...")
    market = MarketAnalyzer()
    competitor_analysis = market.competitor_pricing_analysis()
    
    print(f"   Market ARPDAU: ${competitor_analysis['average_arpdau']:.3f}")
    print(f"   Market D7 Retention: {competitor_analysis['average_d7_retention']*100:.1f}%")
    print(f"   Recommended Starter Pack: ${competitor_analysis['recommended_pricing']['starter_pack']:.2f}")
    
    # 7. Regional pricing
    print("\n7. REGIONAL PRICING OPTIMIZATION...")
    regional_prices = market.regional_pricing_optimization()
    
    for region in ['US', 'India', 'Brazil']:
        rp = regional_prices[region]
        print(f"   {region}: ${rp['local_price']:.2f} ({rp['discount_from_us']} discount)")
    
    # 8. Generate revenue projections
    print("\n8. REVENUE PROJECTIONS (12 MONTHS)...")
    monthly_projections = []
    
    for month in range(1, 13):
        # Assume growth curve
        growth_factor = 1 + np.log(month) / 10
        dau = 10000 * growth_factor
        arpdau = 0.35 * (1 + month * 0.02)  # Improving monetization
        
        monthly_revenue = dau * arpdau * 30
        monthly_projections.append({
            'month': month,
            'dau': int(dau),
            'arpdau': arpdau,
            'revenue': monthly_revenue
        })
    
    total_projected = sum(p['revenue'] for p in monthly_projections)
    
    print(f"   Month 1: ${monthly_projections[0]['revenue']:,.2f}")
    print(f"   Month 6: ${monthly_projections[5]['revenue']:,.2f}")
    print(f"   Month 12: ${monthly_projections[11]['revenue']:,.2f}")
    print(f"   Total Year 1: ${total_projected:,.2f}")
    
    print("\n" + "=" * 80)
    print("SIMULATION COMPLETE")
    print("=" * 80)
    
    return {
        'simulation_results': results,
        'optimal_pricing': optimal_prices,
        'ad_optimization': ad_optimization,
        'market_analysis': competitor_analysis,
        'regional_pricing': regional_prices,
        'revenue_projections': monthly_projections
    }

if __name__ == "__main__":
    # Run the comprehensive simulation
    results = run_comprehensive_simulation()
    
    # Optional: Export results to JSON
    with open('economic_analysis.json', 'w') as f:
        # Convert results to JSON-serializable format
        sim_results = results['simulation_results']
        export_data = {
            'summary': {
                'total_revenue_30d': float(sum(r['revenue'] for r in sim_results)),
                'avg_dau': float(sum(r['dau'] for r in sim_results) / len(sim_results)),
                'projected_annual_revenue': float(sum(p['revenue'] for p in results['revenue_projections']))
            },
            'optimal_pricing': results['optimal_pricing'],
            'ad_optimization': results['ad_optimization'],
            'market_analysis': results['market_analysis'],
            'regional_pricing': results['regional_pricing']
        }
        json.dump(export_data, f, indent=2)
        print("\n📊 Results exported to economic_analysis.json")