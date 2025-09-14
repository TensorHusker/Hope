// GameCenterManager.swift
// Game Center integration for leaderboards and achievements

import GameKit
import UIKit

class GameCenterManager: NSObject {
    
    // MARK: - Singleton
    
    static let shared = GameCenterManager()
    
    // MARK: - Properties
    
    private var isAuthenticated = false
    private var localPlayer: GKLocalPlayer?
    private weak var presentingViewController: UIViewController?
    
    // Leaderboard IDs
    private let mainLeaderboardID = "com.hope.leaderboard.main"
    private let speedRunLeaderboardID = "com.hope.leaderboard.speedrun"
    
    // Achievement IDs
    private let achievements = [
        "com.hope.achievement.first_win",
        "com.hope.achievement.speed_demon",
        "com.hope.achievement.perfectionist",
        "com.hope.achievement.explorer"
    ]
    
    private override init() {
        super.init()
        setupNotifications()
    }
    
    // MARK: - Authentication
    
    func authenticateLocalPlayer(from viewController: UIViewController) {
        presentingViewController = viewController
        
        let player = GKLocalPlayer.local
        localPlayer = player
        
        player.authenticateHandler = { [weak self] viewController, error in
            if let viewController = viewController {
                // Present the authentication view controller
                self?.presentingViewController?.present(viewController, animated: true)
            } else if player.isAuthenticated {
                // Player is authenticated
                self?.isAuthenticated = true
                self?.loadAchievements()
                NotificationCenter.default.post(name: .gameCenterAuthenticationChanged, object: true)
            } else {
                // Authentication failed
                self?.isAuthenticated = false
                if let error = error {
                    print("Game Center authentication error: \(error.localizedDescription)")
                }
                NotificationCenter.default.post(name: .gameCenterAuthenticationChanged, object: false)
            }
        }
    }
    
    // MARK: - Leaderboards
    
    func submitScore(_ score: Int, to leaderboardID: String? = nil) {
        guard isAuthenticated else { return }
        
        let leaderboard = leaderboardID ?? mainLeaderboardID
        
        GKLeaderboard.submitScore(
            score,
            context: 0,
            player: GKLocalPlayer.local,
            leaderboardIDs: [leaderboard]
        ) { error in
            if let error = error {
                print("Failed to submit score: \(error.localizedDescription)")
            } else {
                print("Score submitted successfully")
            }
        }
    }
    
    func showLeaderboard(from viewController: UIViewController) {
        guard isAuthenticated else {
            authenticateLocalPlayer(from: viewController)
            return
        }
        
        let leaderboardVC = GKGameCenterViewController(leaderboardID: mainLeaderboardID, playerScope: .global, timeScope: .allTime)
        leaderboardVC.gameCenterDelegate = self
        viewController.present(leaderboardVC, animated: true)
    }
    
    func loadLeaderboardScores(completion: @escaping ([GKLeaderboard.Entry]?) -> Void) {
        guard isAuthenticated else {
            completion(nil)
            return
        }
        
        GKLeaderboard.loadLeaderboards(IDs: [mainLeaderboardID]) { leaderboards, error in
            guard let leaderboard = leaderboards?.first else {
                completion(nil)
                return
            }
            
            leaderboard.loadEntries(for: .global, timeScope: .allTime, range: NSRange(location: 1, length: 100)) { localEntry, entries, totalCount, error in
                completion(entries)
            }
        }
    }
    
    // MARK: - Achievements
    
    func reportAchievement(identifier: String, percentComplete: Double = 100.0) {
        guard isAuthenticated else { return }
        
        let achievement = GKAchievement(identifier: identifier)
        achievement.percentComplete = percentComplete
        achievement.showsCompletionBanner = true
        
        GKAchievement.report([achievement]) { error in
            if let error = error {
                print("Failed to report achievement: \(error.localizedDescription)")
            }
        }
    }
    
    func loadAchievements() {
        guard isAuthenticated else { return }
        
        GKAchievement.loadAchievements { achievements, error in
            if let achievements = achievements {
                // Cache achievements for offline access
                self.cacheAchievements(achievements)
            }
        }
    }
    
    func showAchievements(from viewController: UIViewController) {
        guard isAuthenticated else {
            authenticateLocalPlayer(from: viewController)
            return
        }
        
        let achievementsVC = GKGameCenterViewController(state: .achievements)
        achievementsVC.gameCenterDelegate = self
        viewController.present(achievementsVC, animated: true)
    }
    
    func resetAchievements() {
        guard isAuthenticated else { return }
        
        GKAchievement.resetAchievements { error in
            if let error = error {
                print("Failed to reset achievements: \(error.localizedDescription)")
            }
        }
    }
    
    // MARK: - Challenges
    
    func challengeFriends(with score: Int, message: String) {
        guard isAuthenticated else { return }
        
        GKLeaderboard.loadLeaderboards(IDs: [mainLeaderboardID]) { leaderboards, error in
            guard let leaderboard = leaderboards?.first else { return }
            
            let challenge = GKChallenge()
            // Configure challenge
        }
    }
    
    // MARK: - Private Methods
    
    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAuthenticationRequest),
            name: .requestGameCenterAuth,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleScoreSubmission),
            name: .submitScore,
            object: nil
        )
    }
    
    @objc private func handleAuthenticationRequest() {
        guard let viewController = presentingViewController else { return }
        authenticateLocalPlayer(from: viewController)
    }
    
    @objc private func handleScoreSubmission(_ notification: Notification) {
        guard let data = notification.userInfo?["data"] as? String,
              let scoreData = data.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: scoreData) as? [String: Any],
              let score = json["score"] as? Int else {
            return
        }
        
        submitScore(score)
    }
    
    private func cacheAchievements(_ achievements: [GKAchievement]) {
        // Cache achievements for offline access
        let achievementData = achievements.compactMap { achievement -> [String: Any]? in
            return [
                "identifier": achievement.identifier,
                "percentComplete": achievement.percentComplete,
                "isCompleted": achievement.isCompleted
            ]
        }
        
        UserDefaults.standard.set(achievementData, forKey: "CachedAchievements")
    }
}

// MARK: - GKGameCenterControllerDelegate

extension GameCenterManager: GKGameCenterControllerDelegate {
    func gameCenterViewControllerDidFinish(_ gameCenterViewController: GKGameCenterViewController) {
        gameCenterViewController.dismiss(animated: true)
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let gameCenterAuthenticationChanged = Notification.Name("GameCenterAuthenticationChanged")
}