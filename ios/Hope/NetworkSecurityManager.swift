// NetworkSecurityManager.swift
// Network security with certificate pinning and request signing

import Foundation
import CommonCrypto

/// Manages secure network communications with certificate pinning
final class NetworkSecurityManager: NSObject {
    
    // MARK: - Singleton
    
    static let shared = NetworkSecurityManager()
    
    // MARK: - Properties
    
    private let session: URLSession
    private let pinnedCertificates: [String: SecCertificate] = [:]
    private let apiKey = KeychainHelper.shared.getString(for: "api_key") ?? ""
    
    // Certificate fingerprints (SHA256)
    private let trustedFingerprints = [
        "libertalia.testnet.api": "SHA256:1234567890abcdef...", // Replace with actual fingerprint
        "your-secure-server.com": "SHA256:fedcba0987654321..."  // Replace with actual fingerprint
    ]
    
    private override init() {
        // Configure secure session
        let config = URLSessionConfiguration.default
        config.tlsMinimumSupportedProtocolVersion = .TLSv12
        config.tlsMaximumSupportedProtocolVersion = .TLSv13
        config.httpShouldUsePipelining = false
        config.httpCookieAcceptPolicy = .never
        config.httpShouldSetCookies = false
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        
        // Add security headers
        config.httpAdditionalHeaders = [
            "X-Security-Version": "1.0",
            "X-Platform": "iOS",
            "X-App-Version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        ]
        
        session = URLSession(configuration: config, delegate: nil, delegateQueue: nil)
        
        super.init()
        
        // Set self as delegate after initialization
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }
    
    // MARK: - Request Building
    
    /// Create a secure request with signing
    func createSecureRequest(url: URL, method: String = "POST", body: Data? = nil) -> URLRequest? {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 30
        
        // Add security headers
        let timestamp = String(Int(Date().timeIntervalSince1970))
        let nonce = UUID().uuidString
        
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(timestamp, forHTTPHeaderField: "X-Timestamp")
        request.setValue(nonce, forHTTPHeaderField: "X-Nonce")
        
        // Sign the request
        if let body = body {
            request.httpBody = body
            
            // Create signature
            let signatureData = "\(method):\(url.path):\(timestamp):\(nonce):\(body.base64EncodedString())"
            if let signature = hmacSHA256(data: signatureData, key: apiKey) {
                request.setValue(signature, forHTTPHeaderField: "X-Signature")
            }
        }
        
        // Add request ID for tracking
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Request-ID")
        
        return request
    }
    
    /// Perform secure data task with certificate pinning
    func secureDataTask(with request: URLRequest, completion: @escaping (Data?, URLResponse?, Error?) -> Void) -> URLSessionDataTask {
        return session.dataTask(with: request, completionHandler: completion)
    }
    
    // MARK: - HMAC Signing
    
    private func hmacSHA256(data: String, key: String) -> String? {
        guard let dataData = data.data(using: .utf8),
              let keyData = key.data(using: .utf8) else {
            return nil
        }
        
        var hmac = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        
        keyData.withUnsafeBytes { keyBytes in
            dataData.withUnsafeBytes { dataBytes in
                CCHmac(CCHmacAlgorithm(kCCHmacAlgSHA256),
                       keyBytes.baseAddress, keyData.count,
                       dataBytes.baseAddress, dataData.count,
                       &hmac)
            }
        }
        
        return Data(hmac).base64EncodedString()
    }
    
    // MARK: - Certificate Validation
    
    private func validateCertificate(for trust: SecTrust, host: String) -> Bool {
        // Get the certificate chain
        guard let certificateChain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              !certificateChain.isEmpty else {
            return false
        }
        
        // Check if we have a pinned fingerprint for this host
        guard let expectedFingerprint = trustedFingerprints[host] else {
            print("No pinned certificate for host: \(host)")
            return false
        }
        
        // Validate the leaf certificate
        let leafCertificate = certificateChain[0]
        guard let actualFingerprint = getCertificateFingerprint(leafCertificate) else {
            return false
        }
        
        // Compare fingerprints
        let isValid = actualFingerprint == expectedFingerprint
        
        if !isValid {
            print("Certificate pinning failed for \(host)")
            print("Expected: \(expectedFingerprint)")
            print("Actual: \(actualFingerprint)")
        }
        
        return isValid
    }
    
    private func getCertificateFingerprint(_ certificate: SecCertificate) -> String? {
        let data = SecCertificateCopyData(certificate) as Data
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        
        data.withUnsafeBytes { bytes in
            CC_SHA256(bytes.baseAddress, CC_LONG(data.count), &hash)
        }
        
        return "SHA256:" + hash.map { String(format: "%02x", $0) }.joined()
    }
    
    // MARK: - Response Validation
    
    /// Validate response signature from server
    func validateResponse(data: Data, response: URLResponse?, signature: String?) -> Bool {
        guard let httpResponse = response as? HTTPURLResponse,
              let signature = signature ?? httpResponse.allHeaderFields["X-Response-Signature"] as? String else {
            return false
        }
        
        // Verify HMAC signature
        let responseData = data.base64EncodedString()
        guard let expectedSignature = hmacSHA256(data: responseData, key: apiKey) else {
            return false
        }
        
        return signature == expectedSignature
    }
    
    // MARK: - Blockchain Communication
    
    /// Secure communication with Libertalia blockchain
    func submitToBlockchain(proof: Data, completion: @escaping (Result<Data, Error>) -> Void) {
        guard let url = URL(string: "https://libertalia.testnet.api/submit") else {
            completion(.failure(NetworkError.invalidURL))
            return
        }
        
        guard let request = createSecureRequest(url: url, method: "POST", body: proof) else {
            completion(.failure(NetworkError.requestCreationFailed))
            return
        }
        
        secureDataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let data = data else {
                completion(.failure(NetworkError.noData))
                return
            }
            
            // Validate response
            if !self.validateResponse(data: data, response: response, signature: nil) {
                completion(.failure(NetworkError.invalidSignature))
                return
            }
            
            completion(.success(data))
        }.resume()
    }
}

// MARK: - URLSessionDelegate for Certificate Pinning

extension NetworkSecurityManager: URLSessionDelegate {
    
    func urlSession(_ session: URLSession, 
                    didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        
        let host = challenge.protectionSpace.host
        
        // Perform certificate pinning
        if validateCertificate(for: serverTrust, host: host) {
            let credential = URLCredential(trust: serverTrust)
            completionHandler(.useCredential, credential)
        } else {
            // Certificate validation failed
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }
}

// MARK: - Network Errors

enum NetworkError: LocalizedError {
    case invalidURL
    case requestCreationFailed
    case noData
    case invalidSignature
    case certificatePinningFailed
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .requestCreationFailed:
            return "Failed to create secure request"
        case .noData:
            return "No data received"
        case .invalidSignature:
            return "Response signature validation failed"
        case .certificatePinningFailed:
            return "Certificate pinning validation failed"
        }
    }
}

// MARK: - Request Encryption Extension

extension NetworkSecurityManager {
    
    /// Encrypt sensitive request data
    func encryptRequestData(_ data: Data, publicKey: SecKey) -> Data? {
        var error: Unmanaged<CFError>?
        
        guard let encryptedData = SecKeyCreateEncryptedData(
            publicKey,
            .rsaEncryptionPKCS1,
            data as CFData,
            &error
        ) else {
            if let error = error {
                print("Encryption error: \(error.takeRetainedValue())")
            }
            return nil
        }
        
        return encryptedData as Data
    }
    
    /// Decrypt response data
    func decryptResponseData(_ data: Data, privateKey: SecKey) -> Data? {
        var error: Unmanaged<CFError>?
        
        guard let decryptedData = SecKeyCreateDecryptedData(
            privateKey,
            .rsaEncryptionPKCS1,
            data as CFData,
            &error
        ) else {
            if let error = error {
                print("Decryption error: \(error.takeRetainedValue())")
            }
            return nil
        }
        
        return decryptedData as Data
    }
}