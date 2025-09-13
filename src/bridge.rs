// Bridge service for Hope to Libertalia blockchain communication
// Handles proof submission, credit minting, and synchronization

use crate::proof::{PuzzleProof, calculate_credits, verify_proof};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;

const BRIDGE_ENDPOINT: &str = "https://libertalia.testnet.api/";
const MAX_BATCH_SIZE: usize = 50;
const RETRY_DELAY_MS: u64 = 5000;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProofSubmission {
    pub proof: PuzzleProof,
    pub expected_credits: u64,
    pub batch_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubmissionResult {
    pub tx_hash: String,
    pub credits_minted: u64,
    pub block_height: u64,
    pub gas_used: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlayerBalance {
    pub address: String,
    pub papilio_balance: u64,
    pub pollen_balance: u64,
    pub nectar_balance: u64,
    pub total_puzzles_solved: u64,
    pub reputation_score: f64,
}

pub struct LibertaliaBridge {
    endpoint: String,
    player_cache: Arc<RwLock<HashMap<String, PlayerBalance>>>,
    pending_submissions: Arc<RwLock<Vec<ProofSubmission>>>,
    connection_status: Arc<RwLock<ConnectionStatus>>,
}

#[derive(Debug, Clone)]
pub enum ConnectionStatus {
    Connected,
    Disconnected,
    Syncing,
    Error(String),
}

impl LibertaliaBridge {
    pub fn new(custom_endpoint: Option<String>) -> Self {
        Self {
            endpoint: custom_endpoint.unwrap_or_else(|| BRIDGE_ENDPOINT.to_string()),
            player_cache: Arc::new(RwLock::new(HashMap::new())),
            pending_submissions: Arc::new(RwLock::new(Vec::new())),
            connection_status: Arc::new(RwLock::new(ConnectionStatus::Disconnected)),
        }
    }
    
    pub async fn connect(&self) -> Result<(), String> {
        *self.connection_status.write().await = ConnectionStatus::Syncing;
        
        // Simulate connection to blockchain
        // In production, this would establish WebSocket or HTTP connection
        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
        
        *self.connection_status.write().await = ConnectionStatus::Connected;
        Ok(())
    }
    
    pub async fn submit_proof(&self, proof: &PuzzleProof) -> Result<SubmissionResult, String> {
        // Verify proof locally first
        if !verify_proof(proof)? {
            return Err("Invalid proof".to_string());
        }
        
        // Calculate expected credits
        let credits = calculate_credits(proof);
        
        // Check connection status
        let status = self.connection_status.read().await.clone();
        match status {
            ConnectionStatus::Connected => {
                // Direct submission
                self.submit_to_chain(proof, credits).await
            }
            _ => {
                // Queue for later submission
                self.queue_submission(proof.clone(), credits).await?;
                Err("Offline: proof queued for later submission".to_string())
            }
        }
    }
    
    pub async fn submit_batch(&self, proofs: Vec<PuzzleProof>) -> Result<Vec<SubmissionResult>, String> {
        if proofs.is_empty() {
            return Ok(Vec::new());
        }
        
        if proofs.len() > MAX_BATCH_SIZE {
            return Err(format!("Batch size {} exceeds maximum {}", proofs.len(), MAX_BATCH_SIZE));
        }
        
        // Verify all proofs
        for proof in &proofs {
            if !verify_proof(proof)? {
                return Err(format!("Invalid proof in batch: {:?}", proof.puzzle_id));
            }
        }
        
        // Generate batch ID
        let batch_id = self.generate_batch_id(&proofs);
        
        // Submit each proof with batch ID
        let mut results = Vec::new();
        for proof in proofs {
            let credits = calculate_credits(&proof);
            match self.submit_to_chain_with_batch(&proof, credits, &batch_id).await {
                Ok(result) => results.push(result),
                Err(e) => {
                    // Continue with other proofs even if one fails
                    eprintln!("Failed to submit proof: {}", e);
                }
            }
        }
        
        Ok(results)
    }
    
    async fn submit_to_chain(&self, proof: &PuzzleProof, credits: u64) -> Result<SubmissionResult, String> {
        // Simulate blockchain submission
        // In production, this would call the smart contract
        
        // Mock transaction hash
        let tx_hash = format!("0x{}", hex::encode(&proof.puzzle_id[..8]));
        
        // Simulate gas calculation
        let gas_used = 21000 + (proof.move_sequence.len() as u64 * 100);
        
        Ok(SubmissionResult {
            tx_hash,
            credits_minted: credits,
            block_height: 1234567,
            gas_used,
        })
    }
    
    async fn submit_to_chain_with_batch(
        &self,
        proof: &PuzzleProof,
        credits: u64,
        batch_id: &str,
    ) -> Result<SubmissionResult, String> {
        // Similar to submit_to_chain but includes batch processing
        let mut result = self.submit_to_chain(proof, credits).await?;
        
        // Batch submissions get gas discount
        result.gas_used = (result.gas_used * 80) / 100; // 20% discount
        
        Ok(result)
    }
    
    async fn queue_submission(&self, proof: PuzzleProof, credits: u64) -> Result<(), String> {
        let mut pending = self.pending_submissions.write().await;
        
        pending.push(ProofSubmission {
            proof,
            expected_credits: credits,
            batch_id: None,
        });
        
        Ok(())
    }
    
    pub async fn sync_pending(&self) -> Result<usize, String> {
        let mut pending = self.pending_submissions.write().await;
        
        if pending.is_empty() {
            return Ok(0);
        }
        
        let to_submit = pending.clone();
        pending.clear();
        
        let mut synced = 0;
        for submission in to_submit {
            match self.submit_to_chain(&submission.proof, submission.expected_credits).await {
                Ok(_) => synced += 1,
                Err(e) => {
                    // Re-queue failed submissions
                    pending.push(submission);
                    eprintln!("Sync failed: {}", e);
                }
            }
        }
        
        Ok(synced)
    }
    
    pub async fn get_player_balance(&self, address: &str) -> Result<PlayerBalance, String> {
        // Check cache first
        let cache = self.player_cache.read().await;
        if let Some(balance) = cache.get(address) {
            return Ok(balance.clone());
        }
        drop(cache);
        
        // Fetch from blockchain
        let balance = self.fetch_balance_from_chain(address).await?;
        
        // Update cache
        let mut cache = self.player_cache.write().await;
        cache.insert(address.to_string(), balance.clone());
        
        Ok(balance)
    }
    
    async fn fetch_balance_from_chain(&self, address: &str) -> Result<PlayerBalance, String> {
        // Simulate blockchain query
        // In production, this would query the smart contract
        
        Ok(PlayerBalance {
            address: address.to_string(),
            papilio_balance: 1000,
            pollen_balance: 100,
            nectar_balance: 10,
            total_puzzles_solved: 42,
            reputation_score: 0.95,
        })
    }
    
    pub async fn exchange_credits(
        &self,
        from_token: TokenType,
        to_token: TokenType,
        amount: u64,
        player_address: &str,
    ) -> Result<String, String> {
        // Calculate exchange rate
        let rate = self.get_exchange_rate(from_token, to_token)?;
        let output_amount = (amount as f64 * rate) as u64;
        
        // Simulate exchange transaction
        let tx_hash = format!("0xexchange_{:x}", rand::random::<u64>());
        
        // Update cached balance
        let mut cache = self.player_cache.write().await;
        if let Some(balance) = cache.get_mut(player_address) {
            match from_token {
                TokenType::Papilio => balance.papilio_balance -= amount,
                TokenType::Pollen => balance.pollen_balance -= amount,
                TokenType::Nectar => balance.nectar_balance -= amount,
            }
            match to_token {
                TokenType::Papilio => balance.papilio_balance += output_amount,
                TokenType::Pollen => balance.pollen_balance += output_amount,
                TokenType::Nectar => balance.nectar_balance += output_amount,
            }
        }
        
        Ok(tx_hash)
    }
    
    fn get_exchange_rate(&self, from: TokenType, to: TokenType) -> Result<f64, String> {
        // Papilio -> Pollen: 10:1
        // Pollen -> Nectar: 10:1
        // Papilio -> Nectar: 100:1
        
        match (from, to) {
            (TokenType::Papilio, TokenType::Pollen) => Ok(0.1),
            (TokenType::Papilio, TokenType::Nectar) => Ok(0.01),
            (TokenType::Pollen, TokenType::Papilio) => Ok(10.0),
            (TokenType::Pollen, TokenType::Nectar) => Ok(0.1),
            (TokenType::Nectar, TokenType::Papilio) => Ok(100.0),
            (TokenType::Nectar, TokenType::Pollen) => Ok(10.0),
            _ => Ok(1.0), // Same token
        }
    }
    
    fn generate_batch_id(&self, proofs: &[PuzzleProof]) -> String {
        use sha3::{Digest, Sha3_256};
        
        let mut hasher = Sha3_256::new();
        for proof in proofs {
            hasher.update(&proof.puzzle_id);
        }
        
        format!("batch_{}", hex::encode(&hasher.finalize()[..8]))
    }
    
    pub async fn get_leaderboard(&self, limit: usize) -> Result<Vec<LeaderboardEntry>, String> {
        // Simulate fetching leaderboard from blockchain
        Ok(vec![
            LeaderboardEntry {
                rank: 1,
                address: "hope1abc123".to_string(),
                total_credits: 50000,
                puzzles_solved: 500,
                reputation: 0.99,
            },
            LeaderboardEntry {
                rank: 2,
                address: "hope1def456".to_string(),
                total_credits: 45000,
                puzzles_solved: 480,
                reputation: 0.97,
            },
        ])
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum TokenType {
    Papilio,
    Pollen,
    Nectar,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LeaderboardEntry {
    pub rank: u32,
    pub address: String,
    pub total_credits: u64,
    pub puzzles_solved: u64,
    pub reputation: f64,
}

// Anti-cheat verification module
pub mod anticheat {
    use super::*;
    use sha3::{Digest, Sha3_256};
    
    pub struct CheatDetector {
        known_patterns: HashMap<[u8; 32], u64>, // Pattern hash -> solve count
        solve_times: HashMap<String, Vec<u64>>,  // Player -> solve times
    }
    
    impl CheatDetector {
        pub fn new() -> Self {
            Self {
                known_patterns: HashMap::new(),
                solve_times: HashMap::new(),
            }
        }
        
        pub fn verify_proof(&mut self, proof: &PuzzleProof) -> Result<bool, String> {
            // Check for impossible solve times
            if proof.solve_time_ms < 100 {
                return Ok(false); // Too fast to be human
            }
            
            // Check for repeated patterns (farming detection)
            let pattern_count = self.known_patterns
                .entry(proof.pattern_hash)
                .or_insert(0);
            *pattern_count += 1;
            
            if *pattern_count > 10 {
                return Ok(false); // Same pattern solved too many times
            }
            
            // Check for consistent superhuman performance
            let times = self.solve_times
                .entry(proof.player_address.clone())
                .or_insert_with(Vec::new);
            times.push(proof.solve_time_ms);
            
            if times.len() >= 10 {
                let avg_time: u64 = times.iter().sum::<u64>() / times.len() as u64;
                if avg_time < 500 && proof.difficulty > 20 {
                    return Ok(false); // Consistently too fast for difficulty
                }
            }
            
            // Verify move sequence makes sense
            if !Self::verify_move_sequence(&proof.move_sequence) {
                return Ok(false);
            }
            
            Ok(true)
        }
        
        fn verify_move_sequence(moves: &[crate::proof::Move]) -> bool {
            if moves.is_empty() {
                return false;
            }
            
            // Check for reasonable timing between moves
            let mut last_time = 0;
            for m in moves {
                if m.timestamp_ms < last_time {
                    return false; // Time went backwards
                }
                
                let delta = m.timestamp_ms - last_time;
                if delta < 50 && last_time > 0 {
                    return false; // Moves too fast (< 50ms)
                }
                
                last_time = m.timestamp_ms;
            }
            
            true
        }
    }
}

// Simple hex encoding module
mod hex {
    pub fn encode(data: &[u8]) -> String {
        data.iter()
            .map(|b| format!("{:02x}", b))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_bridge_connection() {
        let bridge = LibertaliaBridge::new(None);
        bridge.connect().await.unwrap();
        
        let status = bridge.connection_status.read().await.clone();
        matches!(status, ConnectionStatus::Connected);
    }
    
    #[test]
    fn test_cheat_detection() {
        use crate::proof::Move;
        use super::anticheat::CheatDetector;
        
        let mut detector = CheatDetector::new();
        
        let good_proof = PuzzleProof {
            puzzle_id: [1u8; 32],
            difficulty: 10,
            pattern_hash: [2u8; 32],
            solution_hash: [3u8; 32],
            move_sequence: vec![
                Move {
                    cell_x: 0,
                    cell_y: 0,
                    action: crate::proof::MoveAction::Select,
                    timestamp_ms: 100,
                },
                Move {
                    cell_x: 1,
                    cell_y: 1,
                    action: crate::proof::MoveAction::Select,
                    timestamp_ms: 300,
                },
            ],
            solve_time_ms: 2000,
            player_address: "test".to_string(),
            session_id: [4u8; 16],
            timestamp: 1234567890,
            nonce: 42,
            signature: vec![],
        };
        
        assert!(detector.verify_proof(&good_proof).unwrap());
        
        let bad_proof = PuzzleProof {
            solve_time_ms: 50, // Too fast
            ..good_proof.clone()
        };
        
        assert!(!detector.verify_proof(&bad_proof).unwrap());
    }
}