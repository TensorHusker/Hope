// HopeFFI.swift
// Safe Swift wrapper around Rust FFI functions

import Foundation
import UIKit

/// Touch event types matching Rust enum
public enum TouchEventType: Int32 {
    case began = 0
    case moved = 1
    case ended = 2
    case cancelled = 3
}

/// Thread-safe FFI bridge to Rust/Bevy engine
public final class HopeFFI {
    
    // MARK: - Singleton
    
    public static let shared = HopeFFI()
    
    private let engineQueue = DispatchQueue(label: "com.hope.engine", qos: .userInteractive)
    private var isInitialized = false
    private var platformCallbackHandler: ((String, String) -> Void)?
    
    private init() {
        setupPlatformCallback()
    }
    
    // MARK: - Engine Lifecycle
    
    /// Initialize the Bevy engine
    public func initialize() {
        engineQueue.sync {
            guard !isInitialized else { return }
            bevy_init()
            isInitialized = true
        }
    }
    
    /// Update the game state
    /// - Parameter deltaTime: Time since last update in seconds
    public func update(deltaTime: Float) {
        engineQueue.async {
            guard self.isInitialized else { return }
            bevy_update(deltaTime)
        }
    }
    
    /// Render the current frame
    public func render() {
        engineQueue.sync {
            guard self.isInitialized else { return }
            bevy_render()
        }
    }
    
    /// Pause the game
    public func pause() {
        engineQueue.async {
            guard self.isInitialized else { return }
            bevy_pause()
        }
    }
    
    /// Resume the game
    public func resume() {
        engineQueue.async {
            guard self.isInitialized else { return }
            bevy_resume()
        }
    }
    
    /// Check if engine is running
    public func isRunning() -> Bool {
        return engineQueue.sync {
            guard isInitialized else { return false }
            return bevy_is_running()
        }
    }
    
    /// Shutdown the engine and clean up resources
    public func shutdown() {
        engineQueue.sync {
            guard isInitialized else { return }
            bevy_shutdown()
            isInitialized = false
        }
    }
    
    // MARK: - Input Handling
    
    /// Send touch event to the engine
    /// - Parameters:
    ///   - point: Touch location in view coordinates
    ///   - eventType: Type of touch event
    public func sendTouchEvent(at point: CGPoint, eventType: TouchEventType) {
        engineQueue.async {
            guard self.isInitialized else { return }
            bevy_touch_event(Float(point.x), Float(point.y), eventType.rawValue)
        }
    }
    
    /// Update accelerometer data
    /// - Parameters:
    ///   - x: X-axis acceleration
    ///   - y: Y-axis acceleration
    ///   - z: Z-axis acceleration
    public func updateAccelerometer(x: Float, y: Float, z: Float) {
        engineQueue.async {
            guard self.isInitialized else { return }
            bevy_accelerometer_update(x, y, z)
        }
    }
    
    // MARK: - Audio Management
    
    /// Set the master audio volume
    /// - Parameter volume: Volume level from 0.0 to 1.0
    public func setAudioVolume(_ volume: Float) {
        let clampedVolume = max(0.0, min(1.0, volume))
        engineQueue.async {
            guard self.isInitialized else { return }
            bevy_set_audio_volume(clampedVolume)
        }
    }
    
    /// Mute or unmute audio
    /// - Parameter muted: Whether audio should be muted
    public func setAudioMuted(_ muted: Bool) {
        engineQueue.async {
            guard self.isInitialized else { return }
            bevy_mute_audio(muted)
        }
    }
    
    // MARK: - Game State
    
    /// Get the current score
    public func getScore() -> Int32 {
        return engineQueue.sync {
            guard isInitialized else { return 0 }
            return bevy_get_score()
        }
    }
    
    /// Get the current level
    public func getLevel() -> Int32 {
        return engineQueue.sync {
            guard isInitialized else { return 0 }
            return bevy_get_level()
        }
    }
    
    /// Get save data as JSON string
    public func getSaveData() -> String? {
        return engineQueue.sync {
            guard isInitialized else { return nil }
            guard let cString = bevy_get_save_data() else { return nil }
            let saveData = String(cString: cString)
            bevy_free_string(UnsafeMutablePointer(mutating: cString))
            return saveData
        }
    }
    
    /// Load save data from JSON string
    /// - Parameter data: Save data as JSON string
    public func loadSaveData(_ data: String) {
        engineQueue.async {
            guard self.isInitialized else { return }
            data.withCString { cString in
                bevy_load_save_data(cString)
            }
        }
    }
    
    // MARK: - Platform Callbacks
    
    /// Set handler for platform callbacks from Rust
    /// - Parameter handler: Callback handler receiving event name and data
    public func setPlatformCallbackHandler(_ handler: @escaping (String, String) -> Void) {
        platformCallbackHandler = handler
    }
    
    private func setupPlatformCallback() {
        let callback: platform_callback_t = { eventNamePtr, dataPtr in
            guard let eventNamePtr = eventNamePtr,
                  let dataPtr = dataPtr else { return }
            
            let eventName = String(cString: eventNamePtr)
            let data = String(cString: dataPtr)
            
            DispatchQueue.main.async {
                HopeFFI.shared.platformCallbackHandler?(eventName, data)
            }
        }
        
        bevy_register_platform_callback(callback)
    }
}

// MARK: - Memory Safety Extensions

extension HopeFFI {
    
    /// Safely execute a block with automatic resource cleanup
    /// - Parameter block: Block to execute
    /// - Returns: Result of the block
    public func withEngine<T>(_ block: () throws -> T) rethrows -> T {
        return try engineQueue.sync {
            guard isInitialized else {
                throw FFIError.engineNotInitialized
            }
            return try block()
        }
    }
}

/// FFI-related errors
public enum FFIError: LocalizedError {
    case engineNotInitialized
    case invalidData
    
    public var errorDescription: String? {
        switch self {
        case .engineNotInitialized:
            return "Engine must be initialized before use"
        case .invalidData:
            return "Invalid data format"
        }
    }
}