package com.hope.game.distributed

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Byzantine Fault Tolerant Consensus Protocol Implementation
 * Implements a variant of PBFT (Practical Byzantine Fault Tolerance) optimized for game state
 */
@Singleton
class ConsensusProtocol @Inject constructor(
    private val vectorClock: VectorClock,
    private val crdtManager: CRDTManager
) {
    
    private val nodeId = generateNodeId()
    private val keyPair = generateKeyPair()
    
    // Consensus state
    private val currentView = AtomicLong(0)
    private val sequenceNumber = AtomicLong(0)
    private val consensusLog = ConcurrentHashMap<Long, ConsensusEntry>()
    
    // Message pools for different phases
    private val prepareMessages = ConcurrentHashMap<ConsensusKey, MutableSet<PrepareMessage>>()
    private val commitMessages = ConcurrentHashMap<ConsensusKey, MutableSet<CommitMessage>>()
    
    // State machine
    private val stateMachine = GameStateMachine()
    
    // Consensus events
    private val consensusEvents = MutableSharedFlow<ConsensusEvent>(
        replay = 10,
        extraBufferCapacity = 100
    )
    
    companion object {
        const val BYZANTINE_FAULT_TOLERANCE = 3 // f nodes, requires 3f+1 total
        const val MIN_NODES = 4 // 3f+1
        const val CONSENSUS_TIMEOUT_MS = 5000L
        const val VIEW_CHANGE_TIMEOUT_MS = 10000L
        const val CHECKPOINT_INTERVAL = 100
    }
    
    /**
     * Propose a new value for consensus
     */
    suspend fun propose(operation: GameOperation): ConsensusResult = coroutineScope {
        val view = currentView.get()
        val seq = sequenceNumber.incrementAndGet()
        
        // Create pre-prepare message
        val prePrepare = PrePrepareMessage(
            view = view,
            sequenceNumber = seq,
            digest = operation.hash(),
            operation = operation,
            timestamp = vectorClock.increment(nodeId),
            signature = sign(operation.toByteArray())
        )
        
        // If we're the primary, broadcast pre-prepare
        if (isPrimary()) {
            broadcastPrePrepare(prePrepare)
        }
        
        // Wait for consensus
        withTimeoutOrNull(CONSENSUS_TIMEOUT_MS) {
            waitForConsensus(seq)
        } ?: ConsensusResult.Timeout
    }
    
    /**
     * Handle incoming pre-prepare message
     */
    suspend fun handlePrePrepare(message: PrePrepareMessage) {
        // Verify message integrity
        if (!verifySignature(message)) {
            return
        }
        
        // Check view and sequence number
        if (message.view != currentView.get()) {
            initiateViewChange()
            return
        }
        
        // Verify we haven't seen this sequence number
        val key = ConsensusKey(message.view, message.sequenceNumber)
        if (consensusLog.containsKey(message.sequenceNumber)) {
            return
        }
        
        // Move to prepare phase
        val prepare = PrepareMessage(
            view = message.view,
            sequenceNumber = message.sequenceNumber,
            digest = message.digest,
            nodeId = nodeId,
            signature = sign(message.digest)
        )
        
        prepareMessages.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(prepare)
        broadcastPrepare(prepare)
        
        // Check if we have enough prepares
        checkPreparePhase(key)
    }
    
    /**
     * Handle incoming prepare message
     */
    suspend fun handlePrepare(message: PrepareMessage) {
        if (!verifySignature(message)) {
            return
        }
        
        val key = ConsensusKey(message.view, message.sequenceNumber)
        prepareMessages.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(message)
        
        checkPreparePhase(key)
    }
    
    /**
     * Check if we have enough prepare messages to move to commit phase
     */
    private suspend fun checkPreparePhase(key: ConsensusKey) {
        val prepares = prepareMessages[key] ?: return
        
        if (prepares.size >= 2 * BYZANTINE_FAULT_TOLERANCE) {
            // Move to commit phase
            val commit = CommitMessage(
                view = key.view,
                sequenceNumber = key.sequenceNumber,
                digest = prepares.first().digest,
                nodeId = nodeId,
                signature = sign(prepares.first().digest)
            )
            
            commitMessages.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(commit)
            broadcastCommit(commit)
            
            checkCommitPhase(key)
        }
    }
    
    /**
     * Handle incoming commit message
     */
    suspend fun handleCommit(message: CommitMessage) {
        if (!verifySignature(message)) {
            return
        }
        
        val key = ConsensusKey(message.view, message.sequenceNumber)
        commitMessages.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(message)
        
        checkCommitPhase(key)
    }
    
    /**
     * Check if we have enough commit messages to execute operation
     */
    private suspend fun checkCommitPhase(key: ConsensusKey) {
        val commits = commitMessages[key] ?: return
        
        if (commits.size >= 2 * BYZANTINE_FAULT_TOLERANCE + 1) {
            // Execute operation
            val entry = ConsensusEntry(
                sequenceNumber = key.sequenceNumber,
                view = key.view,
                digest = commits.first().digest,
                committed = true,
                timestamp = System.currentTimeMillis()
            )
            
            consensusLog[key.sequenceNumber] = entry
            
            // Apply to state machine
            stateMachine.apply(entry)
            
            // Emit consensus event
            consensusEvents.emit(ConsensusEvent.Committed(key.sequenceNumber))
            
            // Checkpoint if needed
            if (key.sequenceNumber % CHECKPOINT_INTERVAL == 0L) {
                createCheckpoint(key.sequenceNumber)
            }
        }
    }
    
    /**
     * Initiate view change when primary fails
     */
    private suspend fun initiateViewChange() {
        val newView = currentView.incrementAndGet()
        
        val viewChange = ViewChangeMessage(
            newView = newView,
            lastStableCheckpoint = getLastStableCheckpoint(),
            preparedMessages = getPreparedMessages(),
            nodeId = nodeId,
            signature = sign(newView.toString().toByteArray())
        )
        
        broadcastViewChange(viewChange)
    }
    
    /**
     * Wait for consensus on a sequence number
     */
    private suspend fun waitForConsensus(sequenceNumber: Long): ConsensusResult {
        return consensusEvents
            .filter { it is ConsensusEvent.Committed && it.sequenceNumber == sequenceNumber }
            .map { ConsensusResult.Success }
            .first()
    }
    
    /**
     * Generate node ID
     */
    private fun generateNodeId(): String {
        return "node_${System.currentTimeMillis()}_${(0..1000).random()}"
    }
    
    /**
     * Generate ECDSA key pair for signatures
     */
    private fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        return keyGen.generateKeyPair()
    }
    
    /**
     * Sign data with private key
     */
    private fun sign(data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(data)
        return signature.sign()
    }
    
    /**
     * Verify signature
     */
    private fun verifySignature(message: Any): Boolean {
        // Implementation depends on message type
        // Would verify using public key of sender
        return true // Simplified for example
    }
    
    /**
     * Check if this node is the primary
     */
    private fun isPrimary(): Boolean {
        // Primary is determined by view number
        // In real implementation, would use node list
        return currentView.get() % MIN_NODES == 0L
    }
    
    // Broadcast methods (would use NetworkArchitecture)
    private suspend fun broadcastPrePrepare(message: PrePrepareMessage) {
        // Broadcast to all nodes
    }
    
    private suspend fun broadcastPrepare(message: PrepareMessage) {
        // Broadcast to all nodes
    }
    
    private suspend fun broadcastCommit(message: CommitMessage) {
        // Broadcast to all nodes
    }
    
    private suspend fun broadcastViewChange(message: ViewChangeMessage) {
        // Broadcast to all nodes
    }
    
    private fun getLastStableCheckpoint(): Long {
        return consensusLog.values
            .filter { it.sequenceNumber % CHECKPOINT_INTERVAL == 0L }
            .maxByOrNull { it.sequenceNumber }
            ?.sequenceNumber ?: 0
    }
    
    private fun getPreparedMessages(): List<PrepareMessage> {
        return prepareMessages.values.flatten()
    }
    
    private suspend fun createCheckpoint(sequenceNumber: Long) {
        // Create state checkpoint
        val checkpoint = stateMachine.createCheckpoint()
        // Store checkpoint
    }
}

/**
 * Vector Clock implementation for event ordering
 */
@Singleton
class VectorClock @Inject constructor() {
    private val clocks = ConcurrentHashMap<String, AtomicLong>()
    
    fun increment(nodeId: String): Map<String, Long> {
        clocks.computeIfAbsent(nodeId) { AtomicLong(0) }.incrementAndGet()
        return snapshot()
    }
    
    fun update(nodeId: String, remoteClock: Map<String, Long>) {
        remoteClock.forEach { (node, time) ->
            clocks.compute(node) { _, current ->
                AtomicLong(max(current?.get() ?: 0, time))
            }
        }
        increment(nodeId)
    }
    
    fun snapshot(): Map<String, Long> {
        return clocks.mapValues { it.value.get() }
    }
    
    fun compare(clock1: Map<String, Long>, clock2: Map<String, Long>): ClockComparison {
        var less = false
        var greater = false
        
        val allNodes = clock1.keys + clock2.keys
        
        for (node in allNodes) {
            val time1 = clock1[node] ?: 0
            val time2 = clock2[node] ?: 0
            
            when {
                time1 < time2 -> less = true
                time1 > time2 -> greater = true
            }
        }
        
        return when {
            less && !greater -> ClockComparison.BEFORE
            !less && greater -> ClockComparison.AFTER
            !less && !greater -> ClockComparison.EQUAL
            else -> ClockComparison.CONCURRENT
        }
    }
    
    enum class ClockComparison {
        BEFORE, AFTER, EQUAL, CONCURRENT
    }
}

/**
 * CRDT Manager for conflict-free replicated data types
 */
@Singleton
class CRDTManager @Inject constructor() {
    
    /**
     * G-Counter (Grow-only counter) CRDT
     */
    class GCounter {
        private val counts = ConcurrentHashMap<String, AtomicLong>()
        
        fun increment(nodeId: String, amount: Long = 1) {
            counts.computeIfAbsent(nodeId) { AtomicLong(0) }.addAndGet(amount)
        }
        
        fun value(): Long {
            return counts.values.sumOf { it.get() }
        }
        
        fun merge(other: GCounter) {
            other.counts.forEach { (node, count) ->
                counts.compute(node) { _, current ->
                    AtomicLong(max(current?.get() ?: 0, count.get()))
                }
            }
        }
    }
    
    /**
     * LWW-Element-Set (Last-Write-Wins Element Set) CRDT
     */
    class LWWElementSet<T> {
        private val addSet = ConcurrentHashMap<T, Long>()
        private val removeSet = ConcurrentHashMap<T, Long>()
        
        fun add(element: T, timestamp: Long = System.currentTimeMillis()) {
            addSet[element] = timestamp
        }
        
        fun remove(element: T, timestamp: Long = System.currentTimeMillis()) {
            removeSet[element] = timestamp
        }
        
        fun contains(element: T): Boolean {
            val addTime = addSet[element] ?: return false
            val removeTime = removeSet[element] ?: return true
            return addTime > removeTime
        }
        
        fun elements(): Set<T> {
            return addSet.keys.filter { contains(it) }.toSet()
        }
        
        fun merge(other: LWWElementSet<T>) {
            other.addSet.forEach { (element, timestamp) ->
                addSet.merge(element, timestamp) { old, new -> max(old, new) }
            }
            other.removeSet.forEach { (element, timestamp) ->
                removeSet.merge(element, timestamp) { old, new -> max(old, new) }
            }
        }
    }
    
    /**
     * OR-Set (Observed-Remove Set) CRDT
     */
    class ORSet<T> {
        data class Element<T>(val value: T, val uid: String)
        
        private val elements = ConcurrentHashMap.newKeySet<Element<T>>()
        
        fun add(value: T): String {
            val uid = "${System.currentTimeMillis()}_${(0..Int.MAX_VALUE).random()}"
            elements.add(Element(value, uid))
            return uid
        }
        
        fun remove(value: T) {
            elements.removeAll { it.value == value }
        }
        
        fun contains(value: T): Boolean {
            return elements.any { it.value == value }
        }
        
        fun values(): Set<T> {
            return elements.map { it.value }.toSet()
        }
        
        fun merge(other: ORSet<T>) {
            elements.addAll(other.elements)
        }
    }
    
    // Game-specific CRDTs
    val playerScores = GCounter()
    val activeRooms = LWWElementSet<String>()
    val playerInventory = ORSet<String>()
}

/**
 * Game State Machine for applying consensus operations
 */
class GameStateMachine {
    private var state = GameState()
    private val stateHistory = mutableListOf<GameState>()
    
    fun apply(entry: ConsensusEntry) {
        // Apply operation to state
        // Store in history
        stateHistory.add(state.copy())
    }
    
    fun createCheckpoint(): ByteArray {
        // Serialize current state
        return state.toByteArray()
    }
    
    fun restoreCheckpoint(checkpoint: ByteArray) {
        // Restore from serialized state
    }
}

// Message classes
data class PrePrepareMessage(
    val view: Long,
    val sequenceNumber: Long,
    val digest: ByteArray,
    val operation: GameOperation,
    val timestamp: Map<String, Long>,
    val signature: ByteArray
)

data class PrepareMessage(
    val view: Long,
    val sequenceNumber: Long,
    val digest: ByteArray,
    val nodeId: String,
    val signature: ByteArray
)

data class CommitMessage(
    val view: Long,
    val sequenceNumber: Long,
    val digest: ByteArray,
    val nodeId: String,
    val signature: ByteArray
)

data class ViewChangeMessage(
    val newView: Long,
    val lastStableCheckpoint: Long,
    val preparedMessages: List<PrepareMessage>,
    val nodeId: String,
    val signature: ByteArray
)

data class ConsensusKey(val view: Long, val sequenceNumber: Long)

data class ConsensusEntry(
    val sequenceNumber: Long,
    val view: Long,
    val digest: ByteArray,
    val committed: Boolean,
    val timestamp: Long
)

sealed class ConsensusEvent {
    data class Committed(val sequenceNumber: Long) : ConsensusEvent()
    data class ViewChanged(val newView: Long) : ConsensusEvent()
}

sealed class ConsensusResult {
    object Success : ConsensusResult()
    object Timeout : ConsensusResult()
    data class Error(val message: String) : ConsensusResult()
}

data class GameOperation(
    val type: String,
    val data: ByteArray
) {
    fun hash(): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(toByteArray())
    }
    
    fun toByteArray(): ByteArray {
        return "$type:${data.contentToString()}".toByteArray()
    }
}

data class GameState(
    val players: Map<String, PlayerState> = emptyMap(),
    val rooms: Map<String, RoomState> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toByteArray(): ByteArray {
        // Serialize to bytes
        return toString().toByteArray()
    }
}

data class PlayerState(
    val id: String,
    val score: Long,
    val position: Position
)

data class RoomState(
    val id: String,
    val players: Set<String>,
    val state: String
)

data class Position(val x: Float, val y: Float)