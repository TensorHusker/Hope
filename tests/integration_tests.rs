// Integration tests for Hope-Papilio-Libertalia ecosystem

#[cfg(test)]
mod integration_tests {
    use hope::proof::{ProofGenerator, PuzzleProof, Move, MoveAction, verify_proof, calculate_credits};
    use hope::storage::{OfflineStorage, SyncManager, StoredProof, ProofStatus};
    use hope::bridge::{LibertaliaBridge, anticheat::CheatDetector};
    use std::sync::Arc;
    use std::time::Duration;
    
    #[test]
    fn test_end_to_end_proof_generation_and_verification() {
        // Setup
        let mut generator = ProofGenerator::new();
        generator.set_player_keypair([1u8; 32], [2u8; 32]);
        
        // Create a pattern
        let pattern = [
            [true, false, true],
            [false, true, false],
            [true, false, true],
        ];
        
        // Player solves it correctly
        let solution = pattern.clone();
        
        // Simulate player moves
        let moves = vec![
            Move {
                cell_x: 0,
                cell_y: 0,
                action: MoveAction::Select,
                timestamp_ms: 100,
            },
            Move {
                cell_x: 1,
                cell_y: 1,
                action: MoveAction::Select,
                timestamp_ms: 300,
            },
            Move {
                cell_x: 2,
                cell_y: 0,
                action: MoveAction::Select,
                timestamp_ms: 500,
            },
            Move {
                cell_x: 0,
                cell_y: 2,
                action: MoveAction::Select,
                timestamp_ms: 700,
            },
            Move {
                cell_x: 2,
                cell_y: 2,
                action: MoveAction::Select,
                timestamp_ms: 900,
            },
            Move {
                cell_x: 0,
                cell_y: 0,
                action: MoveAction::Submit,
                timestamp_ms: 1200,
            },
        ];
        
        // Generate proof
        let proof = generator.generate_proof(
            &pattern,
            &solution,
            moves,
            1200,
            15, // difficulty
        ).expect("Failed to generate proof");
        
        // Verify proof
        assert!(verify_proof(&proof).expect("Verification failed"));
        
        // Calculate credits
        let credits = calculate_credits(&proof);
        assert!(credits > 0);
        assert!(credits <= 1000); // Should be within bounds
    }
    
    #[test]
    fn test_offline_storage_and_sync() {
        // Create storage
        let storage = Arc::new(
            OfflineStorage::new(None).expect("Failed to create storage")
        );
        
        // Generate some proofs
        let mut generator = ProofGenerator::new();
        generator.set_player_keypair([1u8; 32], [2u8; 32]);
        
        let pattern = [[true; 3]; 3];
        let solution = pattern.clone();
        
        // Generate multiple proofs
        for i in 0..5 {
            let proof = generator.generate_proof(
                &pattern,
                &solution,
                vec![Move {
                    cell_x: 0,
                    cell_y: 0,
                    action: MoveAction::Submit,
                    timestamp_ms: 100 * (i + 1) as u64,
                }],
                1000 + i * 100,
                10 + i,
            ).expect("Failed to generate proof");
            
            storage.add_proof(proof).expect("Failed to store proof");
        }
        
        // Check pending proofs
        let pending = storage.get_pending_proofs(10).expect("Failed to get pending");
        assert_eq!(pending.len(), 5);
        
        // Simulate submission
        storage.mark_submitted(&pending[0].proof.puzzle_id, Some("0x123".to_string()))
            .expect("Failed to mark submitted");
        
        // Verify status change
        let updated = storage.get_pending_proofs(10).expect("Failed to get pending");
        assert_eq!(updated.len(), 4); // One less pending
        
        // Get statistics
        let stats = storage.get_statistics().expect("Failed to get stats");
        assert_eq!(stats.total_proofs, 5);
        assert_eq!(stats.submitting, 1);
        assert_eq!(stats.pending, 4);
    }
    
    #[test]
    fn test_anti_cheat_detection() {
        let mut detector = CheatDetector::new();
        
        // Test 1: Valid proof
        let valid_proof = PuzzleProof {
            puzzle_id: [1u8; 32],
            difficulty: 10,
            pattern_hash: [2u8; 32],
            solution_hash: [3u8; 32],
            move_sequence: vec![
                Move {
                    cell_x: 0,
                    cell_y: 0,
                    action: MoveAction::Select,
                    timestamp_ms: 200,
                },
                Move {
                    cell_x: 1,
                    cell_y: 1,
                    action: MoveAction::Select,
                    timestamp_ms: 500,
                },
                Move {
                    cell_x: 0,
                    cell_y: 0,
                    action: MoveAction::Submit,
                    timestamp_ms: 1000,
                },
            ],
            solve_time_ms: 2000,
            player_address: "player1".to_string(),
            session_id: [4u8; 16],
            timestamp: 1234567890,
            nonce: 42,
            signature: vec![],
        };
        
        assert!(detector.verify_proof(&valid_proof).expect("Verification failed"));
        
        // Test 2: Too fast (cheating)
        let cheat_proof = PuzzleProof {
            solve_time_ms: 50, // Too fast
            ..valid_proof.clone()
        };
        
        assert!(!detector.verify_proof(&cheat_proof).expect("Verification failed"));
        
        // Test 3: Repeated pattern (farming)
        for _ in 0..15 {
            let _ = detector.verify_proof(&valid_proof);
        }
        
        // Should now fail due to too many repeats
        assert!(!detector.verify_proof(&valid_proof).expect("Verification failed"));
    }
    
    #[tokio::test]
    async fn test_bridge_connection_and_submission() {
        let bridge = LibertaliaBridge::new(None);
        
        // Connect to blockchain
        bridge.connect().await.expect("Failed to connect");
        
        // Create a valid proof
        let proof = PuzzleProof {
            puzzle_id: [1u8; 32],
            difficulty: 10,
            pattern_hash: [2u8; 32],
            solution_hash: [3u8; 32],
            move_sequence: vec![
                Move {
                    cell_x: 0,
                    cell_y: 0,
                    action: MoveAction::Submit,
                    timestamp_ms: 1000,
                },
            ],
            solve_time_ms: 1000,
            player_address: "hope1abc123".to_string(),
            session_id: [4u8; 16],
            timestamp: 1234567890,
            nonce: 42,
            signature: vec![0u8; 32],
        };
        
        // Submit proof
        let result = bridge.submit_proof(&proof).await.expect("Failed to submit");
        
        assert!(!result.tx_hash.is_empty());
        assert_eq!(result.credits_minted, calculate_credits(&proof));
        assert!(result.gas_used > 0);
    }
    
    #[tokio::test]
    async fn test_batch_submission() {
        let bridge = LibertaliaBridge::new(None);
        bridge.connect().await.expect("Failed to connect");
        
        // Create multiple proofs
        let mut proofs = Vec::new();
        for i in 0..3 {
            proofs.push(PuzzleProof {
                puzzle_id: [i as u8; 32],
                difficulty: 10 + i,
                pattern_hash: [2u8; 32],
                solution_hash: [3u8; 32],
                move_sequence: vec![
                    Move {
                        cell_x: 0,
                        cell_y: 0,
                        action: MoveAction::Submit,
                        timestamp_ms: 1000,
                    },
                ],
                solve_time_ms: 1000 + i as u64 * 100,
                player_address: "hope1abc123".to_string(),
                session_id: [4u8; 16],
                timestamp: 1234567890 + i as u64,
                nonce: 42 + i as u64,
                signature: vec![0u8; 32],
            });
        }
        
        // Submit batch
        let results = bridge.submit_batch(proofs.clone()).await
            .expect("Failed to submit batch");
        
        assert_eq!(results.len(), 3);
        
        // Verify gas discount for batch
        for result in &results {
            assert!(result.gas_used < 21000); // Should have discount
        }
    }
    
    #[tokio::test]
    async fn test_player_balance_and_exchange() {
        let bridge = LibertaliaBridge::new(None);
        bridge.connect().await.expect("Failed to connect");
        
        let player = "hope1testplayer";
        
        // Get initial balance
        let balance = bridge.get_player_balance(player).await
            .expect("Failed to get balance");
        
        assert_eq!(balance.address, player);
        assert!(balance.papilio_balance > 0);
        
        // Test exchange
        let tx_hash = bridge.exchange_credits(
            hope::bridge::TokenType::Papilio,
            hope::bridge::TokenType::Pollen,
            100,
            player,
        ).await.expect("Failed to exchange");
        
        assert!(!tx_hash.is_empty());
        
        // Verify balance updated
        let new_balance = bridge.get_player_balance(player).await
            .expect("Failed to get balance");
        
        assert!(new_balance.papilio_balance < balance.papilio_balance);
        assert!(new_balance.pollen_balance > balance.pollen_balance);
    }
    
    #[test]
    fn test_credit_calculation_scaling() {
        // Test that credits scale appropriately with difficulty
        let base_proof = PuzzleProof {
            puzzle_id: [1u8; 32],
            difficulty: 1,
            pattern_hash: [2u8; 32],
            solution_hash: [3u8; 32],
            move_sequence: vec![
                Move {
                    cell_x: 0,
                    cell_y: 0,
                    action: MoveAction::Submit,
                    timestamp_ms: 3000,
                },
            ],
            solve_time_ms: 3000,
            player_address: "test".to_string(),
            session_id: [4u8; 16],
            timestamp: 1234567890,
            nonce: 42,
            signature: vec![],
        };
        
        let credits_low = calculate_credits(&base_proof);
        
        let hard_proof = PuzzleProof {
            difficulty: 50,
            solve_time_ms: 4000,
            move_sequence: vec![
                Move {
                    cell_x: 0,
                    cell_y: 0,
                    action: MoveAction::Submit,
                    timestamp_ms: 4000,
                },
            ],
            ..base_proof.clone()
        };
        
        let credits_high = calculate_credits(&hard_proof);
        
        // Higher difficulty should give more credits
        assert!(credits_high > credits_low);
        
        // Fast solve should give bonus
        let fast_proof = PuzzleProof {
            solve_time_ms: 3000,
            ..hard_proof.clone()
        };
        
        let credits_fast = calculate_credits(&fast_proof);
        assert!(credits_fast > credits_high);
    }
    
    #[test]
    fn test_proof_expiration() {
        let storage = OfflineStorage::new(None).expect("Failed to create storage");
        
        // Add an old proof
        let old_proof = PuzzleProof {
            puzzle_id: [1u8; 32],
            difficulty: 10,
            pattern_hash: [2u8; 32],
            solution_hash: [3u8; 32],
            move_sequence: vec![],
            solve_time_ms: 1000,
            player_address: "test".to_string(),
            session_id: [4u8; 16],
            timestamp: 1000, // Very old timestamp
            nonce: 42,
            signature: vec![],
        };
        
        storage.add_proof(old_proof.clone()).expect("Failed to add proof");
        
        // Mark as confirmed
        storage.mark_confirmed(&old_proof.puzzle_id, "0x123".to_string())
            .expect("Failed to confirm");
        
        // Cleanup old proofs
        let removed = storage.cleanup_old_proofs(86400) // 1 day
            .expect("Failed to cleanup");
        
        assert_eq!(removed, 1);
        
        // Verify it's gone
        let stats = storage.get_statistics().expect("Failed to get stats");
        assert_eq!(stats.total_proofs, 0);
    }
    
    #[tokio::test]
    async fn test_sync_manager() {
        let storage = Arc::new(OfflineStorage::new(None).expect("Failed to create storage"));
        let sync_manager = SyncManager::new(storage.clone());
        
        // Initially offline
        assert!(!sync_manager.should_sync());
        
        // Set online
        sync_manager.set_online_status(true);
        assert!(sync_manager.should_sync());
        
        // Add some proofs to storage
        let proof = PuzzleProof {
            puzzle_id: [1u8; 32],
            difficulty: 10,
            pattern_hash: [2u8; 32],
            solution_hash: [3u8; 32],
            move_sequence: vec![],
            solve_time_ms: 1000,
            player_address: "test".to_string(),
            session_id: [4u8; 16],
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs(),
            nonce: 42,
            signature: vec![],
        };
        
        storage.add_proof(proof).expect("Failed to add proof");
        
        // Sync proofs
        let result = sync_manager.sync_proofs(&hope::storage::BridgeClient).await
            .expect("Failed to sync");
        
        assert_eq!(result.submitted, 1);
        assert_eq!(result.confirmed, 1);
        assert_eq!(result.failed, 0);
    }
}