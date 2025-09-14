// StoreManager.swift
// In-App Purchase management with StoreKit

import StoreKit

class StoreManager: NSObject {
    
    // MARK: - Singleton
    
    static let shared = StoreManager()
    
    // MARK: - Properties
    
    private var products: [SKProduct] = []
    private var purchaseCompletionHandlers: [String: (Bool, Error?) -> Void] = [:]
    
    // Product identifiers
    private let productIdentifiers = Set([
        "com.hope.removeads",
        "com.hope.premiumthemes",
        "com.hope.doublecoins",
        "com.hope.coin.pack.small",
        "com.hope.coin.pack.medium",
        "com.hope.coin.pack.large"
    ])
    
    private override init() {
        super.init()
        SKPaymentQueue.default().add(self)
        setupNotifications()
    }
    
    deinit {
        SKPaymentQueue.default().remove(self)
    }
    
    // MARK: - Product Management
    
    func loadProducts() {
        let request = SKProductsRequest(productIdentifiers: productIdentifiers)
        request.delegate = self
        request.start()
    }
    
    func product(for identifier: String) -> SKProduct? {
        return products.first { $0.productIdentifier == identifier }
    }
    
    func canMakePayments() -> Bool {
        return SKPaymentQueue.canMakePayments()
    }
    
    // MARK: - Purchasing
    
    func purchase(productIdentifier: String, completion: @escaping (Bool, Error?) -> Void) {
        guard canMakePayments() else {
            completion(false, StoreError.paymentsDisabled)
            return
        }
        
        guard let product = product(for: productIdentifier) else {
            completion(false, StoreError.productNotFound)
            return
        }
        
        purchaseCompletionHandlers[productIdentifier] = completion
        
        let payment = SKPayment(product: product)
        SKPaymentQueue.default().add(payment)
    }
    
    func restorePurchases(completion: @escaping (Bool, Error?) -> Void) {
        guard canMakePayments() else {
            completion(false, StoreError.paymentsDisabled)
            return
        }
        
        purchaseCompletionHandlers["restore"] = completion
        SKPaymentQueue.default().restoreCompletedTransactions()
    }
    
    // MARK: - Price Formatting
    
    func priceString(for product: SKProduct) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = product.priceLocale
        return formatter.string(from: product.price) ?? ""
    }
    
    // MARK: - Receipt Validation
    
    func validateReceipt(completion: @escaping (Bool) -> Void) {
        guard let receiptURL = Bundle.main.appStoreReceiptURL,
              FileManager.default.fileExists(atPath: receiptURL.path) else {
            completion(false)
            return
        }
        
        do {
            let receiptData = try Data(contentsOf: receiptURL)
            let receiptString = receiptData.base64EncodedString()
            
            // Send to your server for validation
            validateReceiptOnServer(receiptString) { isValid in
                completion(isValid)
            }
        } catch {
            completion(false)
        }
    }
    
    private func validateReceiptOnServer(_ receipt: String, completion: @escaping (Bool) -> Void) {
        // SECURITY FIX: Implement proper server-side receipt validation
        guard let url = URL(string: "https://your-secure-server.com/api/validate-receipt") else {
            completion(false)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        // Add security headers
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Request-ID")
        request.setValue(Bundle.main.bundleIdentifier ?? "", forHTTPHeaderField: "X-Bundle-ID")
        
        let payload: [String: Any] = [
            "receipt": receipt,
            "bundle_id": Bundle.main.bundleIdentifier ?? "",
            "version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        ]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            completion(false)
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            guard error == nil,
                  let data = data,
                  let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                completion(false)
                return
            }
            
            // Verify response signature
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let isValid = json["valid"] as? Bool,
               let signature = json["signature"] as? String {
                // TODO: Verify server signature with public key
                completion(isValid)
            } else {
                completion(false)
            }
        }.resume()
    }
    
    // MARK: - Notifications
    
    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePurchaseRequest),
            name: .purchaseItem,
            object: nil
        )
    }
    
    @objc private func handlePurchaseRequest(_ notification: Notification) {
        guard let data = notification.userInfo?["data"] as? String,
              let purchaseData = data.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: purchaseData) as? [String: Any],
              let productId = json["product_id"] as? String else {
            return
        }
        
        purchase(productIdentifier: productId) { success, error in
            // Send result back to Rust
            HopeFFI.shared.setPlatformCallbackHandler { _, _ in
                // Handle purchase result
            }
        }
    }
    
    // MARK: - Private Methods
    
    private func handleTransaction(_ transaction: SKPaymentTransaction) {
        switch transaction.transactionState {
        case .purchased:
            completeTransaction(transaction)
        case .failed:
            failTransaction(transaction)
        case .restored:
            restoreTransaction(transaction)
        case .deferred:
            deferTransaction(transaction)
        case .purchasing:
            break
        @unknown default:
            break
        }
    }
    
    private func completeTransaction(_ transaction: SKPaymentTransaction) {
        let productIdentifier = transaction.payment.productIdentifier
        
        // Deliver content
        deliverPurchase(productIdentifier: productIdentifier)
        
        // Finish transaction
        SKPaymentQueue.default().finishTransaction(transaction)
        
        // Call completion handler
        if let handler = purchaseCompletionHandlers[productIdentifier] {
            handler(true, nil)
            purchaseCompletionHandlers.removeValue(forKey: productIdentifier)
        }
    }
    
    private func failTransaction(_ transaction: SKPaymentTransaction) {
        let productIdentifier = transaction.payment.productIdentifier
        
        // Finish transaction
        SKPaymentQueue.default().finishTransaction(transaction)
        
        // Call completion handler
        if let handler = purchaseCompletionHandlers[productIdentifier] {
            handler(false, transaction.error)
            purchaseCompletionHandlers.removeValue(forKey: productIdentifier)
        }
    }
    
    private func restoreTransaction(_ transaction: SKPaymentTransaction) {
        let productIdentifier = transaction.payment.productIdentifier
        
        // Deliver content
        deliverPurchase(productIdentifier: productIdentifier)
        
        // Finish transaction
        SKPaymentQueue.default().finishTransaction(transaction)
    }
    
    private func deferTransaction(_ transaction: SKPaymentTransaction) {
        // Handle deferred transaction (parental approval required)
    }
    
    private func deliverPurchase(productIdentifier: String) {
        // SECURITY FIX: Save purchase to Keychain instead of UserDefaults
        let transactionId = UUID().uuidString // In production, use actual transaction ID
        KeychainHelper.shared.savePurchase(
            productId: productIdentifier,
            transactionId: transactionId,
            purchaseDate: Date()
        )
        
        // Notify game engine
        NotificationCenter.default.post(
            name: .purchaseCompleted,
            object: nil,
            userInfo: ["product_id": productIdentifier]
        )
    }
    
    /// Check if a product has been purchased
    func isPurchased(productIdentifier: String) -> Bool {
        return KeychainHelper.shared.verifyPurchase(productId: productIdentifier)
    }
}

// MARK: - SKProductsRequestDelegate

extension StoreManager: SKProductsRequestDelegate {
    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        products = response.products
        
        // Log invalid product identifiers
        for invalidIdentifier in response.invalidProductIdentifiers {
            print("Invalid product identifier: \(invalidIdentifier)")
        }
        
        NotificationCenter.default.post(name: .productsLoaded, object: nil)
    }
    
    func request(_ request: SKRequest, didFailWithError error: Error) {
        print("Failed to load products: \(error.localizedDescription)")
    }
}

// MARK: - SKPaymentTransactionObserver

extension StoreManager: SKPaymentTransactionObserver {
    func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction in transactions {
            handleTransaction(transaction)
        }
    }
    
    func paymentQueueRestoreCompletedTransactionsFinished(_ queue: SKPaymentQueue) {
        if let handler = purchaseCompletionHandlers["restore"] {
            handler(true, nil)
            purchaseCompletionHandlers.removeValue(forKey: "restore")
        }
    }
    
    func paymentQueue(_ queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: Error) {
        if let handler = purchaseCompletionHandlers["restore"] {
            handler(false, error)
            purchaseCompletionHandlers.removeValue(forKey: "restore")
        }
    }
}

// MARK: - Store Errors

enum StoreError: LocalizedError {
    case paymentsDisabled
    case productNotFound
    case purchaseFailed
    
    var errorDescription: String? {
        switch self {
        case .paymentsDisabled:
            return "In-App Purchases are disabled"
        case .productNotFound:
            return "Product not found"
        case .purchaseFailed:
            return "Purchase failed"
        }
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let productsLoaded = Notification.Name("StoreProductsLoaded")
    static let purchaseCompleted = Notification.Name("StorePurchaseCompleted")
}