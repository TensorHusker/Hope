package com.hope.game.rendering

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.*
import androidx.core.view.GestureDetectorCompat
import com.hope.game.game.GameViewModel
import com.hope.game.jni.NativeLib
import kotlin.math.abs

/**
 * Custom SurfaceView for Vulkan/OpenGL rendering with advanced touch handling
 */
class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr),
    SurfaceHolder.Callback,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener,
    ScaleGestureDetector.OnScaleGestureListener {
    
    private var gameViewModel: GameViewModel? = null
    private val gestureDetector = GestureDetectorCompat(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    
    // Multi-touch tracking
    private val activePointers = mutableMapOf<Int, PointerInfo>()
    private var isScaling = false
    private var scaleFactor = 1.0f
    
    // Performance optimization
    private var lastTouchTime = 0L
    private val touchThrottle = 16L // ~60Hz touch sampling
    
    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.RGBA_8888)
        
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Set up gesture detection
        gestureDetector.setOnDoubleTapListener(this)
        
        // Keep screen on during gameplay
        keepScreenOn = true
        
        // Enable touch feedback
        isFocusable = true
        isFocusableInTouchMode = true
    }
    
    fun setGameViewModel(viewModel: GameViewModel) {
        gameViewModel = viewModel
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        // Surface is ready for rendering
        gameViewModel?.let { vm ->
            vm.viewModelScope.launch {
                vm.initializeGame(holder.surface, context.assets)
            }
        }
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        gameViewModel?.onSurfaceChanged(width, height)
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Clean up rendering resources
        gameViewModel?.pauseGame()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Throttle touch events for performance
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTouchTime < touchThrottle && 
            event.action != MotionEvent.ACTION_DOWN && 
            event.action != MotionEvent.ACTION_UP) {
            return true
        }
        lastTouchTime = currentTime
        
        // Handle scale gestures
        scaleGestureDetector.onTouchEvent(event)
        if (isScaling) {
            return true
        }
        
        // Handle standard gestures
        gestureDetector.onTouchEvent(event)
        
        // Handle multi-touch
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handlePointerDown(event, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                handlePointerDown(event, pointerIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    handlePointerMove(event, i)
                }
            }
            MotionEvent.ACTION_UP -> {
                handlePointerUp(event, 0)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                handlePointerUp(event, pointerIndex)
            }
            MotionEvent.ACTION_CANCEL -> {
                handleTouchCancel()
            }
        }
        
        return true
    }
    
    private fun handlePointerDown(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        
        activePointers[pointerId] = PointerInfo(x, y, System.currentTimeMillis())
        
        gameViewModel?.onTouch(x, y, NativeLib.TOUCH_DOWN, pointerId)
        
        // Haptic feedback
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    private fun handlePointerMove(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        
        activePointers[pointerId]?.let { pointer ->
            pointer.x = x
            pointer.y = y
            
            gameViewModel?.onTouch(x, y, NativeLib.TOUCH_MOVE, pointerId)
        }
    }
    
    private fun handlePointerUp(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        
        activePointers.remove(pointerId)
        
        gameViewModel?.onTouch(x, y, NativeLib.TOUCH_UP, pointerId)
    }
    
    private fun handleTouchCancel() {
        activePointers.forEach { (pointerId, pointer) ->
            gameViewModel?.onTouch(pointer.x, pointer.y, NativeLib.TOUCH_CANCEL, pointerId)
        }
        activePointers.clear()
    }
    
    // GestureDetector.OnGestureListener
    override fun onDown(e: MotionEvent): Boolean = true
    
    override fun onShowPress(e: MotionEvent) {
        // Visual feedback for press
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // Handle single tap
        return true
    }
    
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        // Handle scroll/pan gesture
        return true
    }
    
    override fun onLongPress(e: MotionEvent) {
        // Handle long press
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        // Handle fling gesture
        val minVelocity = 1000f
        val minDistance = 100f
        
        e1?.let { start ->
            val deltaX = e2.x - start.x
            val deltaY = e2.y - start.y
            
            when {
                abs(velocityX) > minVelocity && abs(deltaX) > minDistance -> {
                    // Horizontal fling
                    if (deltaX > 0) {
                        // Fling right
                    } else {
                        // Fling left
                    }
                }
                abs(velocityY) > minVelocity && abs(deltaY) > minDistance -> {
                    // Vertical fling
                    if (deltaY > 0) {
                        // Fling down
                    } else {
                        // Fling up
                    }
                }
            }
        }
        
        return true
    }
    
    // GestureDetector.OnDoubleTapListener
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        // Confirmed single tap (not part of double tap)
        return true
    }
    
    override fun onDoubleTap(e: MotionEvent): Boolean {
        // Handle double tap
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        return true
    }
    
    override fun onDoubleTapEvent(e: MotionEvent): Boolean = true
    
    // ScaleGestureDetector.OnScaleGestureListener
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        isScaling = true
        return true
    }
    
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)
        
        // Send scale event to game
        // gameViewModel?.onScale(scaleFactor, detector.focusX, detector.focusY)
        
        return true
    }
    
    override fun onScaleEnd(detector: ScaleGestureDetector) {
        isScaling = false
    }
    
    /**
     * Data class for tracking pointer information
     */
    private data class PointerInfo(
        var x: Float,
        var y: Float,
        val timestamp: Long
    )
}