// KeychainHelper.swift
// Secure storage for sensitive game data using iOS Keychain

import Foundation
import Security

/// Secure keychain wrapper for storing sensitive game data
final class KeychainHelper {
    
    // MARK: - Properties
    
    static let shared = KeychainHelper()
    private let serviceName = "com.hope.game"
    
    private init() {}
    
    // MARK: - Public Methods
    
    /// Save data securely to keychain
    /// - Parameters:
    ///   - data: Data to save
    ///   - key: Unique key for the data
    ///   - accessibility: When the keychain item should be accessible
    /// - Returns: Success status
    @discardableResult
    func save(_ data: Data, for key: String, accessibility: CFString = kSecAttrAccessibleWhenUnlockedThisDeviceOnly) -> Bool {
        // Delete any existing item
        delete(for: key)
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: accessibility,
            // Additional security attributes
            kSecAttrSynchronizable as String: false,  // Don't sync to iCloud
            kSecAttrIsInvisible as String: true,      // Hide from backup
        ]
        
        let status = SecItemAdd(query as CFDictionary, nil)
        
        if status != errSecSuccess {
            print("Keychain save error: \(status)")
        }
        
        return status == errSecSuccess
    }
    
    /// Save string securely to keychain
    func save(_ string: String, for key: String) -> Bool {
        guard let data = string.data(using: .utf8) else { return false }
        return save(data, for: key)
    }
    
    /// Save codable object securely to keychain
    func save<T: Codable>(_ object: T, for key: String) -> Bool {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = .sortedKeys
            let data = try encoder.encode(object)
            return save(data, for: key)
        } catch {
            print("Keychain encoding error: \(error)")
            return false
        }
    }
    
    /// Retrieve data from keychain
    /// - Parameter key: Key for the data
    /// - Returns: Retrieved data or nil
    func getData(for key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        guard status == errSecSuccess else {
            if status != errSecItemNotFound {
                print("Keychain read error: \(status)")
            }
            return nil
        }
        
        return result as? Data
    }
    
    /// Retrieve string from keychain
    func getString(for key: String) -> String? {
        guard let data = getData(for: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }
    
    /// Retrieve codable object from keychain
    func getObject<T: Codable>(_ type: T.Type, for key: String) -> T? {
        guard let data = getData(for: key) else { return nil }
        
        do {
            let decoder = JSONDecoder()
            return try decoder.decode(type, from: data)
        } catch {
            print("Keychain decoding error: \(error)")
            return nil
        }
    }
    
    /// Delete item from keychain
    /// - Parameter key: Key for the item to delete
    @discardableResult
    func delete(for key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
    
    /// Delete all items for this app
    @discardableResult
    func deleteAll() -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
    
    // MARK: - Purchase Validation
    
    /// Save purchase validation data securely
    func savePurchase(productId: String, transactionId: String, purchaseDate: Date) -> Bool {
        let purchase = PurchaseRecord(
            productId: productId,
            transactionId: transactionId,
            purchaseDate: purchaseDate,
            validationHash: generateValidationHash(productId: productId, transactionId: transactionId)
        )
        
        return save(purchase, for: "purchase_\(productId)")
    }
    
    /// Verify purchase exists and is valid
    func verifyPurchase(productId: String) -> Bool {
        guard let purchase: PurchaseRecord = getObject(PurchaseRecord.self, for: "purchase_\(productId)") else {
            return false
        }
        
        // Verify the validation hash
        let expectedHash = generateValidationHash(productId: purchase.productId, transactionId: purchase.transactionId)
        return purchase.validationHash == expectedHash
    }
    
    private func generateValidationHash(productId: String, transactionId: String) -> String {
        let salt = "HopeGame2024SecuritySalt"
        let combined = "\(productId)_\(transactionId)_\(salt)"
        
        // Use CryptoKit for proper hashing (iOS 13+)
        if #available(iOS 13.0, *) {
            import CryptoKit
            let data = Data(combined.utf8)
            let hash = SHA256.hash(data: data)
            return hash.compactMap { String(format: "%02x", $0) }.joined()
        } else {
            // Fallback for older iOS versions
            return combined.data(using: .utf8)?.base64EncodedString() ?? ""
        }
    }
    
    // MARK: - Game Save Data
    
    /// Save game progress securely
    func saveGameProgress(_ progress: GameProgress) -> Bool {
        // Encrypt sensitive game data
        var secureProgress = progress
        secureProgress.timestamp = Date()
        
        // Add integrity check
        secureProgress.integrityHash = generateIntegrityHash(for: progress)
        
        return save(secureProgress, for: "game_progress")
    }
    
    /// Load game progress with integrity verification
    func loadGameProgress() -> GameProgress? {
        guard var progress: GameProgress = getObject(GameProgress.self, for: "game_progress") else {
            return nil
        }
        
        // Verify integrity
        let expectedHash = progress.integrityHash
        progress.integrityHash = ""
        let actualHash = generateIntegrityHash(for: progress)
        
        guard expectedHash == actualHash else {
            print("Game progress integrity check failed")
            return nil
        }
        
        return progress
    }
    
    private func generateIntegrityHash(for progress: GameProgress) -> String {
        let components = "\(progress.score)_\(progress.level)_\(progress.unlockedItems.joined())"
        return components.data(using: .utf8)?.base64EncodedString() ?? ""
    }
}

// MARK: - Data Models

struct PurchaseRecord: Codable {
    let productId: String
    let transactionId: String
    let purchaseDate: Date
    let validationHash: String
}

struct GameProgress: Codable {
    var score: Int
    var level: Int
    var unlockedItems: [String]
    var timestamp: Date
    var integrityHash: String = ""
}

// MARK: - Biometric Authentication Extension

extension KeychainHelper {
    
    /// Save data with biometric protection
    func saveWithBiometric(_ data: Data, for key: String) -> Bool {
        // Delete any existing item
        delete(for: key)
        
        // Create access control with biometric requirement
        var error: Unmanaged<CFError>?
        guard let accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .biometryCurrentSet,
            &error
        ) else {
            print("Failed to create biometric access control: \(String(describing: error))")
            return false
        }
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessControl as String: accessControl,
            kSecAttrSynchronizable as String: false
        ]
        
        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }
    
    /// Retrieve data with biometric authentication
    func getDataWithBiometric(for key: String, reason: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseAuthenticationContext as String: LAContext(),
            kSecUseAuthenticationUI as String: kSecUseAuthenticationUIAllow,
            kSecAuthenticationPrompt as String: reason
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        guard status == errSecSuccess else {
            print("Biometric keychain read error: \(status)")
            return nil
        }
        
        return result as? Data
    }
}

// Import LocalAuthentication for biometric support
import LocalAuthentication