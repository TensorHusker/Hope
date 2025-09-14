// SecurityManager.swift
// Runtime security, anti-tampering, and jailbreak detection

import Foundation
import UIKit
import LocalAuthentication
import CryptoKit

/// Comprehensive security manager for runtime protection
final class SecurityManager {
    
    // MARK: - Singleton
    
    static let shared = SecurityManager()
    
    // MARK: - Properties
    
    private var integrityCheckTimer: Timer?
    private let expectedBundleID = "com.hope.game"
    private var sessionKey: SymmetricKey?
    
    private init() {
        // Initialize session key
        sessionKey = SymmetricKey(size: .bits256)
        
        // Start integrity monitoring
        startIntegrityMonitoring()
    }
    
    // MARK: - Jailbreak Detection
    
    /// Check if device is jailbroken
    func isJailbroken() -> Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        
        // Check 1: Suspicious files
        let suspiciousFiles = [
            "/Applications/Cydia.app",
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/bin/bash",
            "/usr/sbin/sshd",
            "/etc/apt",
            "/private/var/lib/apt/",
            "/private/var/lib/cydia",
            "/private/var/stash",
            "/usr/libexec/sftp-server",
            "/usr/bin/cycript",
            "/usr/local/bin/cycript",
            "/usr/lib/libcycript.dylib",
            "/System/Library/LaunchDaemons/com.saurik.Cydia.Startup.plist",
            "/var/cache/apt",
            "/var/lib/cydia",
            "/var/log/syslog",
            "/bin/sh",
            "/usr/libexec/ssh-keysign"
        ]
        
        for file in suspiciousFiles {
            if FileManager.default.fileExists(atPath: file) {
                return true
            }
        }
        
        // Check 2: Can write to system directories
        let testString = "jailbreak_test"
        do {
            try testString.write(toFile: "/private/jailbreak_test.txt", 
                                atomically: true, 
                                encoding: .utf8)
            try FileManager.default.removeItem(atPath: "/private/jailbreak_test.txt")
            return true
        } catch {
            // Expected behavior on non-jailbroken device
        }
        
        // Check 3: Symbolic links
        if let cydiaURL = URL(string: "cydia://package/com.example.package"),
           UIApplication.shared.canOpenURL(cydiaURL) {
            return true
        }
        
        // Check 4: Dynamic library injection
        let suspiciousLibraries = [
            "SubstrateLoader.dylib",
            "SSLKillSwitch2.dylib",
            "SSLKillSwitch.dylib",
            "MobileSubstrate.dylib",
            "TweakInject.dylib",
            "CydiaSubstrate",
            "cynject",
            "CustomWidgetIcons",
            "PreferenceLoader",
            "RocketBootstrap",
            "WeeLoader",
            "libhooker.dylib"
        ]
        
        for library in suspiciousLibraries {
            if dlopen(library, RTLD_NOW) != nil {
                return true
            }
        }
        
        // Check 5: Fork detection
        let pid = fork()
        if pid >= 0 {
            kill(pid, SIGTERM)
            return true
        }
        
        return false
        #endif
    }
    
    // MARK: - Debugger Detection
    
    /// Check if debugger is attached
    func isDebuggerAttached() -> Bool {
        var info = kinfo_proc()
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        var size = MemoryLayout<kinfo_proc>.stride
        
        let result = sysctl(&mib, u_int(mib.count), &info, &size, nil, 0)
        
        if result != 0 {
            return false
        }
        
        return (info.kp_proc.p_flag & P_TRACED) != 0
    }
    
    // MARK: - Binary Integrity
    
    /// Verify binary integrity
    func verifyBinaryIntegrity() -> Bool {
        // Check bundle identifier
        guard Bundle.main.bundleIdentifier == expectedBundleID else {
            return false
        }
        
        // Check code signature
        guard let executablePath = Bundle.main.executablePath else {
            return false
        }
        
        // Verify executable hash
        do {
            let executableData = try Data(contentsOf: URL(fileURLWithPath: executablePath))
            let hash = SHA256.hash(data: executableData)
            
            // In production, compare with known good hash
            // let expectedHash = "..." 
            // return hash == expectedHash
            
            // For now, just ensure we can compute hash
            return !hash.isEmpty
        } catch {
            return false
        }
    }
    
    // MARK: - Anti-Hooking
    
    /// Check for runtime manipulation
    func checkForHooks() -> Bool {
        // Check for method swizzling on critical methods
        let criticalSelectors = [
            #selector(URLSession.dataTask(with:completionHandler:)),
            #selector(SKPaymentQueue.add(_:)),
            #selector(LAContext.evaluatePolicy(_:localizedReason:reply:))
        ]
        
        for selector in criticalSelectors {
            if isMethodSwizzled(selector) {
                return true
            }
        }
        
        return false
    }
    
    private func isMethodSwizzled(_ selector: Selector) -> Bool {
        // Get the implementation
        guard let originalClass = NSClassFromString("NSURLSession"),
              let method = class_getInstanceMethod(originalClass, selector) else {
            return false
        }
        
        let imp = method_getImplementation(method)
        
        // Check if implementation points to suspicious location
        var info = Dl_info()
        dladdr(unsafeBitCast(imp, to: UnsafeRawPointer.self), &info)
        
        if let name = info.dli_fname {
            let path = String(cString: name)
            
            // Check for suspicious paths
            if path.contains("MobileSubstrate") || 
               path.contains("Cydia") ||
               path.contains("cycript") ||
               path.contains("libhooker") {
                return true
            }
        }
        
        return false
    }
    
    // MARK: - Screen Recording Detection
    
    /// Check if screen is being recorded
    func isScreenBeingRecorded() -> Bool {
        return UIScreen.main.isCaptured
    }
    
    // MARK: - Environment Validation
    
    /// Validate runtime environment
    func validateEnvironment() -> Bool {
        // Check for proxy
        if let proxySettings = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any] {
            if let httpProxy = proxySettings[kCFNetworkProxiesHTTPProxy as String] as? String,
               !httpProxy.isEmpty {
                return false // Proxy detected
            }
        }
        
        // Check for VPN
        if let settings = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any],
           let scopes = settings["__SCOPED__"] as? [String: Any] {
            for (key, _) in scopes {
                if key.contains("tap") || key.contains("tun") || key.contains("ppp") {
                    return false // VPN detected
                }
            }
        }
        
        // Check system version
        let systemVersion = UIDevice.current.systemVersion
        if let majorVersion = systemVersion.components(separatedBy: ".").first,
           let version = Int(majorVersion),
           version < 13 {
            return false // iOS too old
        }
        
        return true
    }
    
    // MARK: - Secure Storage
    
    /// Encrypt data for storage
    func encryptData(_ data: Data) -> Data? {
        guard let key = sessionKey else { return nil }
        
        do {
            let sealedBox = try AES.GCM.seal(data, using: key)
            return sealedBox.combined
        } catch {
            print("Encryption failed: \(error)")
            return nil
        }
    }
    
    /// Decrypt stored data
    func decryptData(_ encryptedData: Data) -> Data? {
        guard let key = sessionKey else { return nil }
        
        do {
            let sealedBox = try AES.GCM.SealedBox(combined: encryptedData)
            return try AES.GCM.open(sealedBox, using: key)
        } catch {
            print("Decryption failed: \(error)")
            return nil
        }
    }
    
    // MARK: - Integrity Monitoring
    
    private func startIntegrityMonitoring() {
        integrityCheckTimer = Timer.scheduledTimer(withTimeInterval: 30.0, repeats: true) { _ in
            self.performIntegrityCheck()
        }
    }
    
    private func performIntegrityCheck() {
        DispatchQueue.global(qos: .background).async {
            // Check for jailbreak
            if self.isJailbroken() {
                self.handleSecurityViolation(reason: "Jailbreak detected")
            }
            
            // Check for debugger
            if self.isDebuggerAttached() {
                self.handleSecurityViolation(reason: "Debugger detected")
            }
            
            // Check for hooks
            if self.checkForHooks() {
                self.handleSecurityViolation(reason: "Runtime manipulation detected")
            }
            
            // Verify binary integrity
            if !self.verifyBinaryIntegrity() {
                self.handleSecurityViolation(reason: "Binary integrity check failed")
            }
        }
    }
    
    private func handleSecurityViolation(reason: String) {
        // Log the violation
        print("SECURITY VIOLATION: \(reason)")
        
        // Clear sensitive data
        KeychainHelper.shared.deleteAll()
        
        // Notify the app
        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: .securityViolationDetected,
                object: nil,
                userInfo: ["reason": reason]
            )
            
            // Optionally terminate the app
            // fatalError("Security violation detected")
        }
    }
    
    // MARK: - Secure Random
    
    /// Generate cryptographically secure random data
    func generateSecureRandom(length: Int) -> Data? {
        var data = Data(count: length)
        let result = data.withUnsafeMutableBytes { bytes in
            SecRandomCopyBytes(kSecRandomDefault, length, bytes.baseAddress!)
        }
        
        return result == errSecSuccess ? data : nil
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let securityViolationDetected = Notification.Name("SecurityViolationDetected")
}

// MARK: - C Imports for sysctl

import Darwin

private let CTL_KERN = 1
private let KERN_PROC = 14
private let KERN_PROC_PID = 1
private let P_TRACED = 0x00000800

// MARK: - Obfuscation Helpers

extension SecurityManager {
    
    /// Obfuscate sensitive strings
    private func obfuscate(_ string: String) -> String {
        let data = Data(string.utf8)
        return data.base64EncodedString()
    }
    
    /// Deobfuscate strings
    private func deobfuscate(_ base64String: String) -> String? {
        guard let data = Data(base64Encoded: base64String) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}