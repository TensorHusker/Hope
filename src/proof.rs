// Cryptographic proof generation for Pattern Echo puzzles
// Generates verifiable proofs of puzzle solutions for blockchain verification

use serde::{Deserialize, Serialize};
use sha3::{Digest, Sha3_256};
use std::time::{SystemTime, UNIX_EPOCH};

// Proof structure that can be verified on-chain
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PuzzleProof {
    // Puzzle identification
    pub puzzle_id: [u8; 32],           // Unique puzzle hash
    pub difficulty: u32,                // Puzzle difficulty level
    pub pattern_hash: [u8; 32],        // Hash of the original pattern
    
    // Solution data
    pub solution_hash: [u8; 32],       // Hash of player's solution
    pub move_sequence: Vec<Move>,      // Sequence of moves made
    pub solve_time_ms: u64,             // Time taken to solve
    
    // Player identification
    pub player_address: String,         // Blockchain address
    pub session_id: [u8; 16],          // Unique session identifier
    
    // Proof metadata
    pub timestamp: u64,                 // Unix timestamp
    pub nonce: u64,                     // For proof-of-work
    pub signature: Vec<u8>,            // Player's signature
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Move {
    pub cell_x: usize,
    pub cell_y: usize,
    pub action: MoveAction,
    pub timestamp_ms: u64,  // Relative to puzzle start
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MoveAction {
    Select,
    Deselect,
    Submit,
}

// Proof generation context
pub struct ProofGenerator {
    session_id: [u8; 16],
    player_keypair: Option<Keypair>,
    pending_proofs: Vec<PuzzleProof>,
}

// Simple keypair for signing (in production, use proper cryptography)
pub struct Keypair {
    pub public_key: [u8; 32],
    secret_key: [u8; 32],
}

impl ProofGenerator {
    pub fn new() -> Self {
        let mut session_id = [0u8; 16];
        // Generate random session ID
        for byte in &mut session_id {
            *byte = rand::random();
        }
        
        Self {
            session_id,
            player_keypair: None,
            pending_proofs: Vec::new(),
        }
    }
    
    pub fn set_player_keypair(&mut self, public_key: [u8; 32], secret_key: [u8; 32]) {
        self.player_keypair = Some(Keypair {
            public_key,
            secret_key,
        });
    }
    
    pub fn generate_proof(
        &mut self,
        pattern: &[[bool; 3]; 3],
        solution: &[[bool; 3]; 3],
        moves: Vec<Move>,
        solve_time_ms: u64,
        difficulty: u32,
    ) -> Result<PuzzleProof, String> {
        // Generate puzzle ID from pattern
        let puzzle_id = self.hash_pattern(pattern);
        let pattern_hash = puzzle_id; // For simplicity, reuse the same hash
        let solution_hash = self.hash_pattern(solution);
        
        // Get current timestamp
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|e| format!("Time error: {}", e))?
            .as_secs();
        
        // Calculate proof-of-work nonce
        let nonce = self.calculate_pow_nonce(&puzzle_id, &solution_hash, difficulty)?;
        
        // Get player address
        let player_address = if let Some(ref keypair) = self.player_keypair {
            self.derive_address(&keypair.public_key)
        } else {
            return Err("No player keypair set".to_string());
        };
        
        // Create proof
        let mut proof = PuzzleProof {
            puzzle_id,
            difficulty,
            pattern_hash,
            solution_hash,
            move_sequence: moves,
            solve_time_ms,
            player_address,
            session_id: self.session_id,
            timestamp,
            nonce,
            signature: Vec::new(),
        };
        
        // Sign the proof
        proof.signature = self.sign_proof(&proof)?;
        
        // Store for later submission
        self.pending_proofs.push(proof.clone());
        
        Ok(proof)
    }
    
    fn hash_pattern(&self, pattern: &[[bool; 3]; 3]) -> [u8; 32] {
        let mut hasher = Sha3_256::new();
        for row in pattern {
            for &cell in row {
                hasher.update(&[cell as u8]);
            }
        }
        hasher.finalize().into()
    }
    
    fn calculate_pow_nonce(
        &self,
        puzzle_id: &[u8; 32],
        solution_hash: &[u8; 32],
        difficulty: u32,
    ) -> Result<u64, String> {
        // Simple proof-of-work: find nonce where hash has leading zeros
        let target_zeros = (difficulty / 10).min(4) as usize;
        
        for nonce in 0..1_000_000u64 {
            let mut hasher = Sha3_256::new();
            hasher.update(puzzle_id);
            hasher.update(solution_hash);
            hasher.update(&nonce.to_le_bytes());
            
            let hash = hasher.finalize();
            let leading_zeros = hash.iter().take_while(|&&b| b == 0).count();
            
            if leading_zeros >= target_zeros {
                return Ok(nonce);
            }
        }
        
        Err("Could not find valid nonce".to_string())
    }
    
    fn derive_address(&self, public_key: &[u8; 32]) -> String {
        // Derive blockchain address from public key
        let mut hasher = Sha3_256::new();
        hasher.update(b"hope_player:");
        hasher.update(public_key);
        let hash = hasher.finalize();
        
        // Convert to bech32-like address
        format!("hope1{}", hex::encode(&hash[..20]))
    }
    
    fn sign_proof(&self, proof: &PuzzleProof) -> Result<Vec<u8>, String> {
        let keypair = self.player_keypair.as_ref()
            .ok_or("No keypair available")?;
        
        // Create message to sign
        let mut hasher = Sha3_256::new();
        hasher.update(&proof.puzzle_id);
        hasher.update(&proof.solution_hash);
        hasher.update(&proof.timestamp.to_le_bytes());
        hasher.update(&proof.nonce.to_le_bytes());
        
        let message_hash = hasher.finalize();
        
        // Simple signature (in production, use proper Ed25519 or similar)
        let mut sig_hasher = Sha3_256::new();
        sig_hasher.update(&keypair.secret_key);
        sig_hasher.update(&message_hash);
        
        Ok(sig_hasher.finalize().to_vec())
    }
    
    pub fn get_pending_proofs(&self) -> &[PuzzleProof] {
        &self.pending_proofs
    }
    
    pub fn clear_submitted_proofs(&mut self, count: usize) {
        self.pending_proofs.drain(0..count.min(self.pending_proofs.len()));
    }
}

// Verification functions (also used by smart contract)
pub fn verify_proof(proof: &PuzzleProof) -> Result<bool, String> {
    // Verify proof-of-work
    let mut hasher = Sha3_256::new();
    hasher.update(&proof.puzzle_id);
    hasher.update(&proof.solution_hash);
    hasher.update(&proof.nonce.to_le_bytes());
    
    let hash = hasher.finalize();
    let target_zeros = (proof.difficulty / 10).min(4) as usize;
    let leading_zeros = hash.iter().take_while(|&&b| b == 0).count();
    
    if leading_zeros < target_zeros {
        return Ok(false);
    }
    
    // Verify timestamp is reasonable (not too old, not in future)
    let current_time = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| format!("Time error: {}", e))?
        .as_secs();
    
    if proof.timestamp > current_time + 60 {
        return Ok(false); // Timestamp in future
    }
    
    if proof.timestamp < current_time - 86400 {
        return Ok(false); // More than 24 hours old
    }
    
    // Verify move sequence is valid
    if proof.move_sequence.is_empty() {
        return Ok(false);
    }
    
    // Check that moves are in chronological order
    let mut last_timestamp = 0;
    for move_item in &proof.move_sequence {
        if move_item.timestamp_ms < last_timestamp {
            return Ok(false);
        }
        last_timestamp = move_item.timestamp_ms;
    }
    
    Ok(true)
}

// Credit calculation based on puzzle difficulty and performance
pub fn calculate_credits(proof: &PuzzleProof) -> u64 {
    let base_credits = 10u64;
    let difficulty_multiplier = (proof.difficulty as u64).saturating_add(1);
    
    // Time bonus: faster solutions get more credits
    let time_bonus = match proof.solve_time_ms {
        0..=5000 => 5,      // Under 5 seconds
        5001..=10000 => 3,  // 5-10 seconds
        10001..=20000 => 1, // 10-20 seconds
        _ => 0,             // Over 20 seconds
    };
    
    // Efficiency bonus: fewer moves = more credits
    let move_efficiency = if proof.move_sequence.len() <= proof.difficulty as usize * 2 {
        2
    } else {
        0
    };
    
    base_credits
        .saturating_mul(difficulty_multiplier)
        .saturating_add(time_bonus)
        .saturating_add(move_efficiency)
}

// Module for hex encoding (simple implementation)
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
    
    #[test]
    fn test_proof_generation() {
        let mut generator = ProofGenerator::new();
        generator.set_player_keypair([1u8; 32], [2u8; 32]);
        
        let pattern = [[true, false, true],
                       [false, true, false],
                       [true, false, true]];
        
        let solution = pattern.clone();
        
        let moves = vec![
            Move {
                cell_x: 0,
                cell_y: 0,
                action: MoveAction::Select,
                timestamp_ms: 100,
            },
            Move {
                cell_x: 2,
                cell_y: 2,
                action: MoveAction::Submit,
                timestamp_ms: 500,
            },
        ];
        
        let proof = generator.generate_proof(
            &pattern,
            &solution,
            moves,
            1500,
            10,
        ).unwrap();
        
        assert!(verify_proof(&proof).unwrap());
        assert_eq!(calculate_credits(&proof), 75); // 10 * (10+1) + 5 + 2
    }
}