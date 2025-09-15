package com.hope.game.jni

import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI Bridge for Hope Rust/Bevy game engine
 * Provides safe Kotlin bindings for native Rust functions
 */
@Singleton
class NativeLib @Inject constructor() {
    
    companion object {
        init {
            System.loadLibrary("hope_game")
        }
        
        // Touch action constants matching Android MotionEvent
        const val TOUCH_DOWN = 0
        const val TOUCH_UP = 1
        const val TOUCH_MOVE = 2
        const val TOUCH_CANCEL = 3
    }
    
    // Native method declarations
    private external fun nativeInit(
        surface: Surface,
        width: Int,
        height: Int,
        assetManager: Any
    ): Long
    
    private external fun nativeUpdate(handle: Long, deltaTime: Float): Boolean
    private external fun nativeRender(handle: Long): Boolean
    private external fun nativeOnTouch(
        handle: Long,
        x: Float,
        y: Float,
        action: Int,
        pointerId: Int
    ): Boolean
    
    private external fun nativePause(handle: Long)
    private external fun nativeResume(handle: Long)
    private external fun nativeDestroy(handle: Long)
    private external fun nativeResize(handle: Long, width: Int, height: Int)
    
    // Game state methods
    private external fun nativeGetGameState(handle: Long): ByteArray
    private external fun nativeSetGameState(handle: Long, state: ByteArray): Boolean
    private external fun nativeGetScore(handle: Long): Long
    private external fun nativeGetLevel(handle: Long): Int
    
    // Audio methods
    private external fun nativeSetMasterVolume(handle: Long, volume: Float)
    private external fun nativePlaySound(handle: Long, soundId: Int)
    private external fun nativeStopAllSounds(handle: Long)
    
    // Analytics/telemetry
    private external fun nativeLogEvent(handle: Long, event: String, params: String)
    private external fun nativeGetPerformanceMetrics(handle: Long): String
    
    private var nativeHandle: Long = 0L
    private val handleLock = Any()
    
    /**
     * Initialize the native game engine
     * @param surface Android Surface for rendering
     * @param width Surface width in pixels
     * @param height Surface height in pixels
     * @param assetManager Android AssetManager for loading resources
     * @return true if initialization successful
     */
    suspend fun init(
        surface: Surface,
        width: Int,
        height: Int,
        assetManager: Any
    ): Boolean = withContext(Dispatchers.IO) {
        synchronized(handleLock) {
            if (nativeHandle != 0L) {
                destroy()
            }
            
            try {
                nativeHandle = nativeInit(surface, width, height, assetManager)
                nativeHandle != 0L
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Update game logic
     * @param deltaTime Time since last update in seconds
     * @return true if update successful
     */
    suspend fun update(deltaTime: Float): Boolean = withContext(Dispatchers.Default) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return@withContext false
            
            try {
                nativeUpdate(nativeHandle, deltaTime)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Render current frame
     * @return true if render successful
     */
    suspend fun render(): Boolean = withContext(Dispatchers.Default) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return@withContext false
            
            try {
                nativeRender(nativeHandle)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Handle touch input
     * @param x Touch X coordinate in screen space
     * @param y Touch Y coordinate in screen space
     * @param action Touch action (DOWN, UP, MOVE, CANCEL)
     * @param pointerId Touch pointer ID for multi-touch
     * @return true if touch handled
     */
    fun onTouch(x: Float, y: Float, action: Int, pointerId: Int = 0): Boolean {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return false
            
            return try {
                nativeOnTouch(nativeHandle, x, y, action, pointerId)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Pause game execution
     */
    fun pause() {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return
            
            try {
                nativePause(nativeHandle)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Resume game execution
     */
    fun resume() {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return
            
            try {
                nativeResume(nativeHandle)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Resize rendering surface
     * @param width New width in pixels
     * @param height New height in pixels
     */
    fun resize(width: Int, height: Int) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return
            
            try {
                nativeResize(nativeHandle, width, height)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Clean up native resources
     */
    fun destroy() {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return
            
            try {
                nativeDestroy(nativeHandle)
            } finally {
                nativeHandle = 0L
            }
        }
    }
    
    /**
     * Get current game state for saving
     * @return Serialized game state or null if error
     */
    suspend fun getGameState(): ByteArray? = withContext(Dispatchers.IO) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return@withContext null
            
            try {
                nativeGetGameState(nativeHandle)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Restore game state from save
     * @param state Previously saved game state
     * @return true if state restored successfully
     */
    suspend fun setGameState(state: ByteArray): Boolean = withContext(Dispatchers.IO) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return@withContext false
            
            try {
                nativeSetGameState(nativeHandle, state)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Get current score
     * @return Current game score
     */
    fun getScore(): Long {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return 0L
            
            return try {
                nativeGetScore(nativeHandle)
            } catch (e: Exception) {
                0L
            }
        }
    }
    
    /**
     * Get current level
     * @return Current game level
     */
    fun getLevel(): Int {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return 0
            
            return try {
                nativeGetLevel(nativeHandle)
            } catch (e: Exception) {
                0
            }
        }
    }
    
    /**
     * Set master volume
     * @param volume Volume level (0.0 to 1.0)
     */
    fun setMasterVolume(volume: Float) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return
            
            try {
                nativeSetMasterVolume(nativeHandle, volume.coerceIn(0f, 1f))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Play sound effect
     * @param soundId Sound resource ID
     */
    fun playSound(soundId: Int) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return
            
            try {
                nativePlaySound(nativeHandle, soundId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Log analytics event
     * @param event Event name
     * @param params Event parameters as JSON string
     */
    fun logEvent(event: String, params: String = "{}") {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return
            
            try {
                nativeLogEvent(nativeHandle, event, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Get performance metrics
     * @return JSON string with performance data
     */
    suspend fun getPerformanceMetrics(): String = withContext(Dispatchers.IO) {
        synchronized(handleLock) {
            if (nativeHandle == 0L) return@withContext "{}"
            
            try {
                nativeGetPerformanceMetrics(nativeHandle)
            } catch (e: Exception) {
                "{}"
            }
        }
    }
}