// AppDelegate.swift
// Main application delegate with lifecycle management

import UIKit
import AVFoundation
import UserNotifications

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    
    var window: UIWindow?
    private let ffi = HopeFFI.shared
    
    // MARK: - Application Lifecycle
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        // Setup audio session
        setupAudioSession()
        
        // Setup push notifications
        setupPushNotifications()
        
        // Initialize FFI bridge
        ffi.initialize()
        
        // Setup platform callback handler
        setupPlatformCallbacks()
        
        // Create and configure window
        window = UIWindow(frame: UIScreen.main.bounds)
        let gameViewController = GameViewController()
        window?.rootViewController = gameViewController
        window?.makeKeyAndVisible()
        
        return true
    }
    
    func applicationWillResignActive(_ application: UIApplication) {
        // Pause the game when app becomes inactive
        ffi.pause()
        
        // Save game state
        if let saveData = ffi.getSaveData() {
            UserDefaults.standard.set(saveData, forKey: "HopeGameSave")
        }
    }
    
    func applicationDidEnterBackground(_ application: UIApplication) {
        // Additional cleanup when entering background
        ffi.setAudioMuted(true)
    }
    
    func applicationWillEnterForeground(_ application: UIApplication) {
        // Prepare to resume
        ffi.setAudioMuted(false)
    }
    
    func applicationDidBecomeActive(_ application: UIApplication) {
        // Resume the game
        ffi.resume()
        
        // Load saved game state
        if let saveData = UserDefaults.standard.string(forKey: "HopeGameSave") {
            ffi.loadSaveData(saveData)
        }
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
        // Clean shutdown
        if let saveData = ffi.getSaveData() {
            UserDefaults.standard.set(saveData, forKey: "HopeGameSave")
        }
        ffi.shutdown()
    }
    
    // MARK: - Memory Management
    
    func applicationDidReceiveMemoryWarning(_ application: UIApplication) {
        // Handle memory pressure
        NotificationCenter.default.post(name: .memoryWarning, object: nil)
    }
    
    // MARK: - Audio Setup
    
    private func setupAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            try audioSession.setActive(true)
        } catch {
            print("Failed to setup audio session: \(error)")
        }
    }
    
    // MARK: - Push Notifications
    
    private func setupPushNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }
    
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        // Send token to backend
        ffi.setPlatformCallbackHandler { event, data in
            if event == "request_push_token" {
                // Handle push token request
            }
        }
    }
    
    // MARK: - Platform Callbacks
    
    private func setupPlatformCallbacks() {
        ffi.setPlatformCallbackHandler { [weak self] event, data in
            self?.handlePlatformCallback(event: event, data: data)
        }
    }
    
    private func handlePlatformCallback(event: String, data: String) {
        switch event {
        case "request_game_center_auth":
            NotificationCenter.default.post(name: .requestGameCenterAuth, object: nil)
        case "submit_score":
            NotificationCenter.default.post(name: .submitScore, object: nil, userInfo: ["data": data])
        case "purchase_item":
            NotificationCenter.default.post(name: .purchaseItem, object: nil, userInfo: ["data": data])
        default:
            break
        }
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let memoryWarning = Notification.Name("HopeMemoryWarning")
    static let requestGameCenterAuth = Notification.Name("HopeRequestGameCenterAuth")
    static let submitScore = Notification.Name("HopeSubmitScore")
    static let purchaseItem = Notification.Name("HopePurchaseItem")
}