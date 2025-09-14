// GameViewController.swift
// Main game view controller with Metal rendering and input handling

import UIKit
import MetalKit
import CoreMotion
import GameController

class GameViewController: UIViewController {
    
    // MARK: - Properties
    
    private var metalView: MTKView!
    private var renderer: MetalRenderer!
    private let ffi = HopeFFI.shared
    
    private let motionManager = CMMotionManager()
    private var displayLink: CADisplayLink?
    private var lastUpdateTime: TimeInterval = 0
    
    private var gameOverlay: GameOverlayView!
    private var pauseButton: UIButton!
    private var isPaused = false
    
    // MARK: - Lifecycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        setupMetalView()
        setupRenderer()
        setupOverlayUI()
        setupGestures()
        setupMotionUpdates()
        setupGameControllers()
        setupNotifications()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        startGameLoop()
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopGameLoop()
    }
    
    // MARK: - Metal Setup
    
    private func setupMetalView() {
        metalView = MTKView(frame: view.bounds)
        metalView.device = MTLCreateSystemDefaultDevice()
        metalView.colorPixelFormat = .bgra8Unorm
        metalView.depthStencilPixelFormat = .depth32Float
        metalView.clearColor = MTLClearColor(red: 0.1, green: 0.1, blue: 0.15, alpha: 1.0)
        metalView.isPaused = false
        metalView.enableSetNeedsDisplay = false
        metalView.preferredFramesPerSecond = 60
        metalView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        
        view.addSubview(metalView)
    }
    
    private func setupRenderer() {
        guard let device = metalView.device else {
            fatalError("Metal device not available")
        }
        
        renderer = MetalRenderer(device: device, metalView: metalView)
        metalView.delegate = renderer
    }
    
    // MARK: - UI Setup
    
    private func setupOverlayUI() {
        // Game overlay for score, level, etc.
        gameOverlay = GameOverlayView(frame: CGRect(x: 0, y: 0, width: view.bounds.width, height: 100))
        gameOverlay.autoresizingMask = [.flexibleWidth, .flexibleBottomMargin]
        view.addSubview(gameOverlay)
        
        // Pause button
        pauseButton = UIButton(type: .system)
        pauseButton.frame = CGRect(x: view.bounds.width - 60, y: 40, width: 44, height: 44)
        pauseButton.setImage(UIImage(systemName: "pause.circle.fill"), for: .normal)
        pauseButton.tintColor = .white
        pauseButton.autoresizingMask = [.flexibleLeftMargin, .flexibleBottomMargin]
        pauseButton.addTarget(self, action: #selector(pauseButtonTapped), for: .touchUpInside)
        view.addSubview(pauseButton)
    }
    
    // MARK: - Input Handling
    
    private func setupGestures() {
        // Tap gesture
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        view.addGestureRecognizer(tapGesture)
        
        // Pan gesture for drag
        let panGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        view.addGestureRecognizer(panGesture)
        
        // Pinch gesture for zoom
        let pinchGesture = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        view.addGestureRecognizer(pinchGesture)
        
        // Long press gesture
        let longPressGesture = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        longPressGesture.minimumPressDuration = 0.5
        view.addGestureRecognizer(longPressGesture)
    }
    
    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        let location = gesture.location(in: metalView)
        let normalizedLocation = normalizePoint(location)
        ffi.sendTouchEvent(at: normalizedLocation, eventType: .began)
        ffi.sendTouchEvent(at: normalizedLocation, eventType: .ended)
    }
    
    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        let location = gesture.location(in: metalView)
        let normalizedLocation = normalizePoint(location)
        
        switch gesture.state {
        case .began:
            ffi.sendTouchEvent(at: normalizedLocation, eventType: .began)
        case .changed:
            ffi.sendTouchEvent(at: normalizedLocation, eventType: .moved)
        case .ended:
            ffi.sendTouchEvent(at: normalizedLocation, eventType: .ended)
        case .cancelled:
            ffi.sendTouchEvent(at: normalizedLocation, eventType: .cancelled)
        default:
            break
        }
    }
    
    @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
        // Handle pinch for zoom if needed
        // Send scale factor to Rust
    }
    
    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        let location = gesture.location(in: metalView)
        let normalizedLocation = normalizePoint(location)
        // Send special long press event to Rust
    }
    
    private func normalizePoint(_ point: CGPoint) -> CGPoint {
        // Normalize to Metal's coordinate system
        return CGPoint(
            x: point.x / metalView.bounds.width,
            y: 1.0 - (point.y / metalView.bounds.height)
        )
    }
    
    // MARK: - Touch Handling
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        handleTouches(touches, eventType: .began)
    }
    
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesMoved(touches, with: event)
        handleTouches(touches, eventType: .moved)
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        handleTouches(touches, eventType: .ended)
    }
    
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesCancelled(touches, with: event)
        handleTouches(touches, eventType: .cancelled)
    }
    
    private func handleTouches(_ touches: Set<UITouch>, eventType: TouchEventType) {
        for touch in touches {
            let location = touch.location(in: metalView)
            let normalizedLocation = normalizePoint(location)
            ffi.sendTouchEvent(at: normalizedLocation, eventType: eventType)
        }
    }
    
    // MARK: - Motion Updates
    
    private func setupMotionUpdates() {
        guard motionManager.isAccelerometerAvailable else { return }
        
        motionManager.accelerometerUpdateInterval = 1.0 / 60.0
        motionManager.startAccelerometerUpdates(to: .main) { [weak self] data, error in
            guard let data = data else { return }
            self?.ffi.updateAccelerometer(
                x: Float(data.acceleration.x),
                y: Float(data.acceleration.y),
                z: Float(data.acceleration.z)
            )
        }
    }
    
    // MARK: - Game Controllers
    
    private func setupGameControllers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(controllerDidConnect),
            name: .GCControllerDidConnect,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(controllerDidDisconnect),
            name: .GCControllerDidDisconnect,
            object: nil
        )
        
        GCController.startWirelessControllerDiscovery()
    }
    
    @objc private func controllerDidConnect(_ notification: Notification) {
        guard let controller = notification.object as? GCController else { return }
        setupController(controller)
    }
    
    @objc private func controllerDidDisconnect(_ notification: Notification) {
        // Handle controller disconnection
    }
    
    private func setupController(_ controller: GCController) {
        controller.extendedGamepad?.valueChangedHandler = { [weak self] gamepad, element in
            // Forward controller input to Rust
            if gamepad.buttonA.isPressed {
                self?.ffi.sendTouchEvent(at: CGPoint(x: 0.5, y: 0.5), eventType: .began)
            }
        }
    }
    
    // MARK: - Game Loop
    
    private func startGameLoop() {
        displayLink = CADisplayLink(target: self, selector: #selector(gameLoop))
        displayLink?.preferredFramesPerSecond = 60
        displayLink?.add(to: .current, forMode: .common)
    }
    
    private func stopGameLoop() {
        displayLink?.invalidate()
        displayLink = nil
        motionManager.stopAccelerometerUpdates()
    }
    
    @objc private func gameLoop() {
        let currentTime = CACurrentMediaTime()
        let deltaTime = lastUpdateTime > 0 ? currentTime - lastUpdateTime : 1.0 / 60.0
        lastUpdateTime = currentTime
        
        if !isPaused {
            ffi.update(deltaTime: Float(deltaTime))
            updateUI()
        }
    }
    
    private func updateUI() {
        let score = ffi.getScore()
        let level = ffi.getLevel()
        gameOverlay.updateScore(score)
        gameOverlay.updateLevel(level)
    }
    
    // MARK: - Actions
    
    @objc private func pauseButtonTapped() {
        isPaused.toggle()
        
        if isPaused {
            ffi.pause()
            pauseButton.setImage(UIImage(systemName: "play.circle.fill"), for: .normal)
            showPauseMenu()
        } else {
            ffi.resume()
            pauseButton.setImage(UIImage(systemName: "pause.circle.fill"), for: .normal)
            hidePauseMenu()
        }
    }
    
    private func showPauseMenu() {
        let pauseMenu = PauseMenuViewController()
        pauseMenu.delegate = self
        pauseMenu.modalPresentationStyle = .overCurrentContext
        pauseMenu.modalTransitionStyle = .crossDissolve
        present(pauseMenu, animated: true)
    }
    
    private func hidePauseMenu() {
        dismiss(animated: true)
    }
    
    // MARK: - Notifications
    
    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleMemoryWarning),
            name: .memoryWarning,
            object: nil
        )
    }
    
    @objc private func handleMemoryWarning() {
        // Release non-essential resources
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
        stopGameLoop()
    }
}

// MARK: - PauseMenuDelegate

extension GameViewController: PauseMenuDelegate {
    func pauseMenuDidResume() {
        pauseButtonTapped()
    }
    
    func pauseMenuDidQuit() {
        ffi.shutdown()
        exit(0)
    }
}