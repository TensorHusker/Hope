// Libertalia Smart Contract for Papilio Credits
// Verifies Hope puzzle proofs and mints credits
// Written in CosmWasm-compatible Rust

use cosmwasm_std::{
    entry_point, to_binary, Binary, Deps, DepsMut, Env, MessageInfo, Response, StdResult,
    Uint128, Addr, CosmosMsg, WasmMsg, BankMsg, Coin, StdError,
};
use cw_storage_plus::{Item, Map};
use serde::{Deserialize, Serialize};
use sha3::{Digest, Sha3_256};

// Contract name and version
const CONTRACT_NAME: &str = "papilio-credits";
const CONTRACT_VERSION: &str = "1.0.0";

// Storage keys
const CONFIG: Item<Config> = Item::new("config");
const PLAYER_BALANCES: Map<&Addr, PlayerInfo> = Map::new("player_balances");
const VERIFIED_PROOFS: Map<&[u8], ProofRecord> = Map::new("verified_proofs");
const TOTAL_SUPPLY: Item<TokenSupply> = Item::new("total_supply");

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct Config {
    pub admin: Addr,
    pub bridge_address: Addr,
    pub min_difficulty: u32,
    pub max_credits_per_proof: u64,
    pub verification_enabled: bool,
    pub exchange_rates: ExchangeRates,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct ExchangeRates {
    pub papilio_to_pollen: u64,  // 10:1
    pub pollen_to_nectar: u64,   // 10:1
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct PlayerInfo {
    pub papilio_balance: Uint128,
    pub pollen_balance: Uint128,
    pub nectar_balance: Uint128,
    pub total_puzzles_solved: u64,
    pub reputation_score: u64, // Out of 1000
    pub last_submission: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ProofRecord {
    pub player: Addr,
    pub credits_minted: u64,
    pub timestamp: u64,
    pub block_height: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct TokenSupply {
    pub papilio: Uint128,
    pub pollen: Uint128,
    pub nectar: Uint128,
}

// Instantiate message
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct InstantiateMsg {
    pub bridge_address: String,
    pub min_difficulty: u32,
    pub max_credits_per_proof: u64,
}

// Execute messages
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum ExecuteMsg {
    SubmitProof {
        proof: PuzzleProof,
    },
    SubmitBatch {
        proofs: Vec<PuzzleProof>,
    },
    ExchangeTokens {
        from: TokenType,
        to: TokenType,
        amount: Uint128,
    },
    UpdateConfig {
        min_difficulty: Option<u32>,
        max_credits_per_proof: Option<u64>,
        verification_enabled: Option<bool>,
    },
    TransferCredits {
        to: String,
        token: TokenType,
        amount: Uint128,
    },
}

// Query messages
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum QueryMsg {
    Config {},
    PlayerBalance { address: String },
    ProofStatus { proof_id: Vec<u8> },
    TotalSupply {},
    Leaderboard { limit: Option<u32> },
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct PuzzleProof {
    pub puzzle_id: Vec<u8>,
    pub difficulty: u32,
    pub pattern_hash: Vec<u8>,
    pub solution_hash: Vec<u8>,
    pub move_sequence: Vec<Move>,
    pub solve_time_ms: u64,
    pub player_address: String,
    pub session_id: Vec<u8>,
    pub timestamp: u64,
    pub nonce: u64,
    pub signature: Vec<u8>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct Move {
    pub cell_x: u8,
    pub cell_y: u8,
    pub action: String,
    pub timestamp_ms: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub enum TokenType {
    Papilio,
    Pollen,
    Nectar,
}

// Contract entry points
#[entry_point]
pub fn instantiate(
    deps: DepsMut,
    _env: Env,
    info: MessageInfo,
    msg: InstantiateMsg,
) -> StdResult<Response> {
    let config = Config {
        admin: info.sender.clone(),
        bridge_address: deps.api.addr_validate(&msg.bridge_address)?,
        min_difficulty: msg.min_difficulty,
        max_credits_per_proof: msg.max_credits_per_proof,
        verification_enabled: true,
        exchange_rates: ExchangeRates {
            papilio_to_pollen: 10,
            pollen_to_nectar: 10,
        },
    };
    
    CONFIG.save(deps.storage, &config)?;
    TOTAL_SUPPLY.save(deps.storage, &TokenSupply::default())?;
    
    Ok(Response::new()
        .add_attribute("method", "instantiate")
        .add_attribute("admin", info.sender))
}

#[entry_point]
pub fn execute(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    msg: ExecuteMsg,
) -> StdResult<Response> {
    match msg {
        ExecuteMsg::SubmitProof { proof } => execute_submit_proof(deps, env, info, proof),
        ExecuteMsg::SubmitBatch { proofs } => execute_submit_batch(deps, env, info, proofs),
        ExecuteMsg::ExchangeTokens { from, to, amount } => {
            execute_exchange_tokens(deps, env, info, from, to, amount)
        }
        ExecuteMsg::UpdateConfig {
            min_difficulty,
            max_credits_per_proof,
            verification_enabled,
        } => execute_update_config(
            deps,
            info,
            min_difficulty,
            max_credits_per_proof,
            verification_enabled,
        ),
        ExecuteMsg::TransferCredits { to, token, amount } => {
            execute_transfer_credits(deps, info, to, token, amount)
        }
    }
}

fn execute_submit_proof(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    proof: PuzzleProof,
) -> StdResult<Response> {
    let config = CONFIG.load(deps.storage)?;
    
    // Only bridge can submit proofs
    if info.sender != config.bridge_address {
        return Err(StdError::generic_err("Unauthorized: only bridge can submit"));
    }
    
    // Verify proof hasn't been submitted before
    let proof_id = &proof.puzzle_id.as_slice();
    if VERIFIED_PROOFS.has(deps.storage, proof_id) {
        return Err(StdError::generic_err("Proof already submitted"));
    }
    
    // Verify proof if enabled
    if config.verification_enabled {
        verify_proof(&proof, config.min_difficulty)?;
    }
    
    // Calculate credits
    let credits = calculate_credits(&proof, config.max_credits_per_proof);
    
    // Get or create player info
    let player_addr = deps.api.addr_validate(&proof.player_address)?;
    let mut player_info = PLAYER_BALANCES
        .load(deps.storage, &player_addr)
        .unwrap_or_default();
    
    // Mint credits
    player_info.papilio_balance += Uint128::from(credits);
    player_info.total_puzzles_solved += 1;
    player_info.last_submission = env.block.time.seconds();
    
    // Update reputation
    player_info.reputation_score = calculate_reputation(
        player_info.total_puzzles_solved,
        proof.solve_time_ms,
        proof.difficulty,
    );
    
    // Save player info
    PLAYER_BALANCES.save(deps.storage, &player_addr, &player_info)?;
    
    // Record proof
    VERIFIED_PROOFS.save(
        deps.storage,
        proof_id,
        &ProofRecord {
            player: player_addr.clone(),
            credits_minted: credits,
            timestamp: env.block.time.seconds(),
            block_height: env.block.height,
        },
    )?;
    
    // Update total supply
    let mut supply = TOTAL_SUPPLY.load(deps.storage)?;
    supply.papilio += Uint128::from(credits);
    TOTAL_SUPPLY.save(deps.storage, &supply)?;
    
    Ok(Response::new()
        .add_attribute("method", "submit_proof")
        .add_attribute("player", player_addr)
        .add_attribute("credits_minted", credits.to_string()))
}

fn execute_submit_batch(
    deps: DepsMut,
    env: Env,
    info: MessageInfo,
    proofs: Vec<PuzzleProof>,
) -> StdResult<Response> {
    let config = CONFIG.load(deps.storage)?;
    
    if info.sender != config.bridge_address {
        return Err(StdError::generic_err("Unauthorized"));
    }
    
    let mut total_credits = 0u64;
    let mut response = Response::new().add_attribute("method", "submit_batch");
    
    for proof in proofs {
        // Process each proof
        match execute_submit_proof(deps.branch(), env.clone(), info.clone(), proof) {
            Ok(res) => {
                // Extract credits from attributes
                if let Some(attr) = res.attributes.iter().find(|a| a.key == "credits_minted") {
                    if let Ok(credits) = attr.value.parse::<u64>() {
                        total_credits += credits;
                    }
                }
            }
            Err(e) => {
                // Log error but continue processing
                response = response.add_attribute("error", e.to_string());
            }
        }
    }
    
    Ok(response
        .add_attribute("total_credits", total_credits.to_string()))
}

fn execute_exchange_tokens(
    deps: DepsMut,
    _env: Env,
    info: MessageInfo,
    from: TokenType,
    to: TokenType,
    amount: Uint128,
) -> StdResult<Response> {
    let config = CONFIG.load(deps.storage)?;
    let mut player_info = PLAYER_BALANCES
        .load(deps.storage, &info.sender)
        .map_err(|_| StdError::generic_err("No balance found"))?;
    
    // Calculate exchange amount
    let output_amount = calculate_exchange(from.clone(), to.clone(), amount, &config.exchange_rates)?;
    
    // Update balances
    match from {
        TokenType::Papilio => {
            if player_info.papilio_balance < amount {
                return Err(StdError::generic_err("Insufficient balance"));
            }
            player_info.papilio_balance -= amount;
        }
        TokenType::Pollen => {
            if player_info.pollen_balance < amount {
                return Err(StdError::generic_err("Insufficient balance"));
            }
            player_info.pollen_balance -= amount;
        }
        TokenType::Nectar => {
            if player_info.nectar_balance < amount {
                return Err(StdError::generic_err("Insufficient balance"));
            }
            player_info.nectar_balance -= amount;
        }
    }
    
    match to {
        TokenType::Papilio => player_info.papilio_balance += output_amount,
        TokenType::Pollen => player_info.pollen_balance += output_amount,
        TokenType::Nectar => player_info.nectar_balance += output_amount,
    }
    
    PLAYER_BALANCES.save(deps.storage, &info.sender, &player_info)?;
    
    Ok(Response::new()
        .add_attribute("method", "exchange_tokens")
        .add_attribute("from", format!("{:?}", from))
        .add_attribute("to", format!("{:?}", to))
        .add_attribute("amount_in", amount.to_string())
        .add_attribute("amount_out", output_amount.to_string()))
}

fn execute_update_config(
    deps: DepsMut,
    info: MessageInfo,
    min_difficulty: Option<u32>,
    max_credits_per_proof: Option<u64>,
    verification_enabled: Option<bool>,
) -> StdResult<Response> {
    let mut config = CONFIG.load(deps.storage)?;
    
    if info.sender != config.admin {
        return Err(StdError::generic_err("Unauthorized: only admin"));
    }
    
    if let Some(min) = min_difficulty {
        config.min_difficulty = min;
    }
    if let Some(max) = max_credits_per_proof {
        config.max_credits_per_proof = max;
    }
    if let Some(enabled) = verification_enabled {
        config.verification_enabled = enabled;
    }
    
    CONFIG.save(deps.storage, &config)?;
    
    Ok(Response::new().add_attribute("method", "update_config"))
}

fn execute_transfer_credits(
    deps: DepsMut,
    info: MessageInfo,
    to: String,
    token: TokenType,
    amount: Uint128,
) -> StdResult<Response> {
    let to_addr = deps.api.addr_validate(&to)?;
    
    let mut sender_info = PLAYER_BALANCES
        .load(deps.storage, &info.sender)
        .map_err(|_| StdError::generic_err("No balance found"))?;
    
    let mut receiver_info = PLAYER_BALANCES
        .load(deps.storage, &to_addr)
        .unwrap_or_default();
    
    // Transfer tokens
    match token {
        TokenType::Papilio => {
            if sender_info.papilio_balance < amount {
                return Err(StdError::generic_err("Insufficient balance"));
            }
            sender_info.papilio_balance -= amount;
            receiver_info.papilio_balance += amount;
        }
        TokenType::Pollen => {
            if sender_info.pollen_balance < amount {
                return Err(StdError::generic_err("Insufficient balance"));
            }
            sender_info.pollen_balance -= amount;
            receiver_info.pollen_balance += amount;
        }
        TokenType::Nectar => {
            if sender_info.nectar_balance < amount {
                return Err(StdError::generic_err("Insufficient balance"));
            }
            sender_info.nectar_balance -= amount;
            receiver_info.nectar_balance += amount;
        }
    }
    
    PLAYER_BALANCES.save(deps.storage, &info.sender, &sender_info)?;
    PLAYER_BALANCES.save(deps.storage, &to_addr, &receiver_info)?;
    
    Ok(Response::new()
        .add_attribute("method", "transfer_credits")
        .add_attribute("from", info.sender)
        .add_attribute("to", to_addr)
        .add_attribute("amount", amount.to_string()))
}

// Query entry point
#[entry_point]
pub fn query(deps: Deps, _env: Env, msg: QueryMsg) -> StdResult<Binary> {
    match msg {
        QueryMsg::Config {} => to_binary(&query_config(deps)?),
        QueryMsg::PlayerBalance { address } => to_binary(&query_player_balance(deps, address)?),
        QueryMsg::ProofStatus { proof_id } => to_binary(&query_proof_status(deps, proof_id)?),
        QueryMsg::TotalSupply {} => to_binary(&query_total_supply(deps)?),
        QueryMsg::Leaderboard { limit } => to_binary(&query_leaderboard(deps, limit)?),
    }
}

fn query_config(deps: Deps) -> StdResult<Config> {
    CONFIG.load(deps.storage)
}

fn query_player_balance(deps: Deps, address: String) -> StdResult<PlayerInfo> {
    let addr = deps.api.addr_validate(&address)?;
    PLAYER_BALANCES.load(deps.storage, &addr)
}

fn query_proof_status(deps: Deps, proof_id: Vec<u8>) -> StdResult<ProofRecord> {
    VERIFIED_PROOFS.load(deps.storage, &proof_id.as_slice())
}

fn query_total_supply(deps: Deps) -> StdResult<TokenSupply> {
    TOTAL_SUPPLY.load(deps.storage)
}

fn query_leaderboard(deps: Deps, limit: Option<u32>) -> StdResult<Vec<(Addr, PlayerInfo)>> {
    let limit = limit.unwrap_or(10).min(100) as usize;
    
    let mut players: Vec<(Addr, PlayerInfo)> = PLAYER_BALANCES
        .range(deps.storage, None, None, cosmwasm_std::Order::Ascending)
        .collect::<StdResult<Vec<_>>>()?;
    
    // Sort by total credits (papilio + pollen*10 + nectar*100)
    players.sort_by(|a, b| {
        let a_total = a.1.papilio_balance.u128() 
            + a.1.pollen_balance.u128() * 10 
            + a.1.nectar_balance.u128() * 100;
        let b_total = b.1.papilio_balance.u128() 
            + b.1.pollen_balance.u128() * 10 
            + b.1.nectar_balance.u128() * 100;
        b_total.cmp(&a_total)
    });
    
    players.truncate(limit);
    Ok(players)
}

// Helper functions
fn verify_proof(proof: &PuzzleProof, min_difficulty: u32) -> StdResult<()> {
    // Check difficulty
    if proof.difficulty < min_difficulty {
        return Err(StdError::generic_err("Difficulty too low"));
    }
    
    // Verify proof-of-work
    let mut hasher = Sha3_256::new();
    hasher.update(&proof.puzzle_id);
    hasher.update(&proof.solution_hash);
    hasher.update(&proof.nonce.to_le_bytes());
    
    let hash = hasher.finalize();
    let target_zeros = (proof.difficulty / 10).min(4) as usize;
    let leading_zeros = hash.iter().take_while(|&&b| b == 0).count();
    
    if leading_zeros < target_zeros {
        return Err(StdError::generic_err("Invalid proof-of-work"));
    }
    
    // Verify move sequence
    if proof.move_sequence.is_empty() {
        return Err(StdError::generic_err("Empty move sequence"));
    }
    
    // Check solve time is reasonable
    if proof.solve_time_ms < 100 {
        return Err(StdError::generic_err("Solve time too fast"));
    }
    
    Ok(())
}

fn calculate_credits(proof: &PuzzleProof, max_credits: u64) -> u64 {
    let base_credits = 10u64;
    let difficulty_multiplier = (proof.difficulty as u64).min(10);
    
    // Time bonus
    let time_bonus = match proof.solve_time_ms {
        0..=5000 => 5,
        5001..=10000 => 3,
        10001..=20000 => 1,
        _ => 0,
    };
    
    // Efficiency bonus
    let move_efficiency = if proof.move_sequence.len() <= proof.difficulty as usize * 2 {
        2
    } else {
        0
    };
    
    let total = base_credits
        .saturating_mul(difficulty_multiplier)
        .saturating_add(time_bonus)
        .saturating_add(move_efficiency);
    
    total.min(max_credits)
}

fn calculate_reputation(puzzles_solved: u64, solve_time: u64, difficulty: u32) -> u64 {
    let base_rep = puzzles_solved.min(100) * 5; // Max 500 from count
    let speed_bonus = if solve_time < 5000 { 200 } else if solve_time < 10000 { 100 } else { 0 };
    let difficulty_bonus = (difficulty as u64 * 10).min(300);
    
    (base_rep + speed_bonus + difficulty_bonus).min(1000)
}

fn calculate_exchange(
    from: TokenType,
    to: TokenType,
    amount: Uint128,
    rates: &ExchangeRates,
) -> StdResult<Uint128> {
    match (from, to) {
        (TokenType::Papilio, TokenType::Pollen) => {
            Ok(amount.checked_div(rates.papilio_to_pollen.into())
                .map_err(|_| StdError::generic_err("Division error"))?)
        }
        (TokenType::Papilio, TokenType::Nectar) => {
            let to_pollen = amount.checked_div(rates.papilio_to_pollen.into())
                .map_err(|_| StdError::generic_err("Division error"))?;
            Ok(to_pollen.checked_div(rates.pollen_to_nectar.into())
                .map_err(|_| StdError::generic_err("Division error"))?)
        }
        (TokenType::Pollen, TokenType::Papilio) => {
            Ok(amount.checked_mul(rates.papilio_to_pollen.into())
                .map_err(|_| StdError::generic_err("Multiplication overflow"))?)
        }
        (TokenType::Pollen, TokenType::Nectar) => {
            Ok(amount.checked_div(rates.pollen_to_nectar.into())
                .map_err(|_| StdError::generic_err("Division error"))?)
        }
        (TokenType::Nectar, TokenType::Pollen) => {
            Ok(amount.checked_mul(rates.pollen_to_nectar.into())
                .map_err(|_| StdError::generic_err("Multiplication overflow"))?)
        }
        (TokenType::Nectar, TokenType::Papilio) => {
            let to_pollen = amount.checked_mul(rates.pollen_to_nectar.into())
                .map_err(|_| StdError::generic_err("Multiplication overflow"))?;
            Ok(to_pollen.checked_mul(rates.papilio_to_pollen.into())
                .map_err(|_| StdError::generic_err("Multiplication overflow"))?)
        }
        _ => Ok(amount), // Same token
    }
}