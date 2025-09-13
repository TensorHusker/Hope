// Offline-first storage system for puzzle proofs
// Manages local proof queue and synchronization with blockchain

use crate::proof::{PuzzleProof, ProofGenerator};
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_QUEUE_SIZE: usize = 1000;
const STORAGE_FILE: &str = "hope_proofs.dat";
const BACKUP_FILE: &str = "hope_proofs.bak";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoredProof {
    pub proof: PuzzleProof,
    pub submission_attempts: u32,
    pub last_attempt_time: Option<u64>,
    pub status: ProofStatus,
    pub blockchain_tx: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ProofStatus {
    Pending,
    Submitting,
    Confirmed,
    Failed(String),
    Expired,
}

pub struct OfflineStorage {
    queue: Arc<Mutex<VecDeque<StoredProof>>>,
    storage_path: PathBuf,
    auto_save: bool,
}

impl OfflineStorage {
    pub fn new(storage_dir: Option<&Path>) -> Result<Self, String> {
        let storage_path = if let Some(dir) = storage_dir {
            dir.join(STORAGE_FILE)
        } else {
            PathBuf::from(STORAGE_FILE)
        };
        
        let queue = Arc::new(Mutex::new(VecDeque::new()));
        
        let mut storage = Self {
            queue,
            storage_path,
            auto_save: true,
        };
        
        // Load existing proofs from disk
        storage.load_from_disk()?;
        
        Ok(storage)
    }
    
    pub fn add_proof(&self, proof: PuzzleProof) -> Result<(), String> {
        let mut queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        // Check queue size limit
        if queue.len() >= MAX_QUEUE_SIZE {
            // Remove oldest confirmed proofs to make space
            queue.retain(|p| p.status != ProofStatus::Confirmed);
            
            if queue.len() >= MAX_QUEUE_SIZE {
                return Err("Proof queue is full".to_string());
            }
        }
        
        let stored_proof = StoredProof {
            proof,
            submission_attempts: 0,
            last_attempt_time: None,
            status: ProofStatus::Pending,
            blockchain_tx: None,
        };
        
        queue.push_back(stored_proof);
        
        drop(queue);
        
        if self.auto_save {
            self.save_to_disk()?;
        }
        
        Ok(())
    }
    
    pub fn get_pending_proofs(&self, limit: usize) -> Result<Vec<StoredProof>, String> {
        let queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        let pending: Vec<StoredProof> = queue
            .iter()
            .filter(|p| p.status == ProofStatus::Pending)
            .take(limit)
            .cloned()
            .collect();
        
        Ok(pending)
    }
    
    pub fn mark_submitted(&self, proof_id: &[u8; 32], tx_hash: Option<String>) -> Result<(), String> {
        let mut queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        for stored_proof in queue.iter_mut() {
            if stored_proof.proof.puzzle_id == *proof_id {
                stored_proof.status = ProofStatus::Submitting;
                stored_proof.submission_attempts += 1;
                stored_proof.last_attempt_time = Some(
                    SystemTime::now()
                        .duration_since(UNIX_EPOCH)
                        .unwrap()
                        .as_secs()
                );
                
                if let Some(tx) = tx_hash {
                    stored_proof.blockchain_tx = Some(tx);
                }
                
                break;
            }
        }
        
        drop(queue);
        
        if self.auto_save {
            self.save_to_disk()?;
        }
        
        Ok(())
    }
    
    pub fn mark_confirmed(&self, proof_id: &[u8; 32], tx_hash: String) -> Result<(), String> {
        let mut queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        for stored_proof in queue.iter_mut() {
            if stored_proof.proof.puzzle_id == *proof_id {
                stored_proof.status = ProofStatus::Confirmed;
                stored_proof.blockchain_tx = Some(tx_hash);
                break;
            }
        }
        
        drop(queue);
        
        if self.auto_save {
            self.save_to_disk()?;
        }
        
        Ok(())
    }
    
    pub fn mark_failed(&self, proof_id: &[u8; 32], reason: String) -> Result<(), String> {
        let mut queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        for stored_proof in queue.iter_mut() {
            if stored_proof.proof.puzzle_id == *proof_id {
                // Retry logic: only mark as permanently failed after 3 attempts
                if stored_proof.submission_attempts >= 3 {
                    stored_proof.status = ProofStatus::Failed(reason.clone());
                } else {
                    // Reset to pending for retry
                    stored_proof.status = ProofStatus::Pending;
                }
                break;
            }
        }
        
        drop(queue);
        
        if self.auto_save {
            self.save_to_disk()?;
        }
        
        Ok(())
    }
    
    pub fn cleanup_old_proofs(&self, max_age_secs: u64) -> Result<usize, String> {
        let mut queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();
        
        let initial_len = queue.len();
        
        queue.retain(|stored_proof| {
            // Keep if not confirmed or if recently created
            if stored_proof.status != ProofStatus::Confirmed {
                return true;
            }
            
            current_time - stored_proof.proof.timestamp < max_age_secs
        });
        
        let removed = initial_len - queue.len();
        
        drop(queue);
        
        if removed > 0 && self.auto_save {
            self.save_to_disk()?;
        }
        
        Ok(removed)
    }
    
    pub fn save_to_disk(&self) -> Result<(), String> {
        let queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        // Create backup of existing file
        if self.storage_path.exists() {
            let backup_path = self.storage_path.with_file_name(BACKUP_FILE);
            fs::copy(&self.storage_path, backup_path)
                .map_err(|e| format!("Backup failed: {}", e))?;
        }
        
        // Serialize queue to JSON
        let json = serde_json::to_string_pretty(&*queue)
            .map_err(|e| format!("Serialization error: {}", e))?;
        
        // Write to file
        fs::write(&self.storage_path, json)
            .map_err(|e| format!("Write error: {}", e))?;
        
        Ok(())
    }
    
    pub fn load_from_disk(&mut self) -> Result<(), String> {
        if !self.storage_path.exists() {
            return Ok(()); // No existing data
        }
        
        let json = fs::read_to_string(&self.storage_path)
            .map_err(|e| format!("Read error: {}", e))?;
        
        let loaded: VecDeque<StoredProof> = serde_json::from_str(&json)
            .map_err(|e| format!("Deserialization error: {}", e))?;
        
        let mut queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        *queue = loaded;
        
        Ok(())
    }
    
    pub fn get_statistics(&self) -> Result<StorageStats, String> {
        let queue = self.queue.lock()
            .map_err(|e| format!("Lock error: {}", e))?;
        
        let mut stats = StorageStats::default();
        
        for stored_proof in queue.iter() {
            stats.total_proofs += 1;
            
            match &stored_proof.status {
                ProofStatus::Pending => stats.pending += 1,
                ProofStatus::Submitting => stats.submitting += 1,
                ProofStatus::Confirmed => {
                    stats.confirmed += 1;
                    stats.total_credits_earned += crate::proof::calculate_credits(&stored_proof.proof);
                }
                ProofStatus::Failed(_) => stats.failed += 1,
                ProofStatus::Expired => stats.expired += 1,
            }
        }
        
        Ok(stats)
    }
}

#[derive(Debug, Default)]
pub struct StorageStats {
    pub total_proofs: usize,
    pub pending: usize,
    pub submitting: usize,
    pub confirmed: usize,
    pub failed: usize,
    pub expired: usize,
    pub total_credits_earned: u64,
}

// Synchronization manager for blockchain submission
pub struct SyncManager {
    storage: Arc<OfflineStorage>,
    is_online: Arc<Mutex<bool>>,
    sync_interval_secs: u64,
}

impl SyncManager {
    pub fn new(storage: Arc<OfflineStorage>) -> Self {
        Self {
            storage,
            is_online: Arc::new(Mutex::new(false)),
            sync_interval_secs: 60, // Sync every minute
        }
    }
    
    pub fn set_online_status(&self, online: bool) {
        if let Ok(mut status) = self.is_online.lock() {
            *status = online;
        }
    }
    
    pub fn should_sync(&self) -> bool {
        self.is_online.lock()
            .map(|status| *status)
            .unwrap_or(false)
    }
    
    pub async fn sync_proofs(&self, bridge_client: &BridgeClient) -> Result<SyncResult, String> {
        if !self.should_sync() {
            return Ok(SyncResult {
                submitted: 0,
                confirmed: 0,
                failed: 0,
            });
        }
        
        let pending = self.storage.get_pending_proofs(10)?;
        
        let mut result = SyncResult::default();
        
        for stored_proof in pending {
            match bridge_client.submit_proof(&stored_proof.proof).await {
                Ok(tx_hash) => {
                    self.storage.mark_submitted(&stored_proof.proof.puzzle_id, Some(tx_hash.clone()))?;
                    result.submitted += 1;
                    
                    // Check confirmation (in real implementation, this would be async)
                    if bridge_client.check_confirmation(&tx_hash).await? {
                        self.storage.mark_confirmed(&stored_proof.proof.puzzle_id, tx_hash)?;
                        result.confirmed += 1;
                    }
                }
                Err(e) => {
                    self.storage.mark_failed(&stored_proof.proof.puzzle_id, e)?;
                    result.failed += 1;
                }
            }
        }
        
        // Cleanup old confirmed proofs
        self.storage.cleanup_old_proofs(7 * 24 * 3600)?; // 7 days
        
        Ok(result)
    }
}

#[derive(Debug, Default)]
pub struct SyncResult {
    pub submitted: usize,
    pub confirmed: usize,
    pub failed: usize,
}

// Mock bridge client interface (will be implemented separately)
pub struct BridgeClient;

impl BridgeClient {
    pub async fn submit_proof(&self, _proof: &PuzzleProof) -> Result<String, String> {
        // Mock implementation
        Ok("0x123abc".to_string())
    }
    
    pub async fn check_confirmation(&self, _tx_hash: &str) -> Result<bool, String> {
        // Mock implementation
        Ok(true)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_offline_storage() {
        let storage = OfflineStorage::new(None).unwrap();
        
        // Create a test proof
        let proof = PuzzleProof {
            puzzle_id: [1u8; 32],
            difficulty: 10,
            pattern_hash: [2u8; 32],
            solution_hash: [3u8; 32],
            move_sequence: vec![],
            solve_time_ms: 1000,
            player_address: "test".to_string(),
            session_id: [4u8; 16],
            timestamp: 1234567890,
            nonce: 42,
            signature: vec![],
        };
        
        storage.add_proof(proof.clone()).unwrap();
        
        let pending = storage.get_pending_proofs(10).unwrap();
        assert_eq!(pending.len(), 1);
        assert_eq!(pending[0].proof.puzzle_id, proof.puzzle_id);
        
        let stats = storage.get_statistics().unwrap();
        assert_eq!(stats.total_proofs, 1);
        assert_eq!(stats.pending, 1);
    }
}