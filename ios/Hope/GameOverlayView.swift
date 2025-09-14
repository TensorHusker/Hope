// GameOverlayView.swift
// Native UI overlay for game HUD

import UIKit

class GameOverlayView: UIView {
    
    // MARK: - UI Elements
    
    private let scoreLabel = UILabel()
    private let levelLabel = UILabel()
    private let coinsLabel = UILabel()
    private let comboLabel = UILabel()
    private let fpsLabel = UILabel()
    
    private var scoreValue: Int32 = 0 {
        didSet {
            scoreLabel.text = "Score: \(scoreValue)"
            animateScoreChange()
        }
    }
    
    private var levelValue: Int32 = 1 {
        didSet {
            levelLabel.text = "Level \(levelValue)"
            animateLevelChange()
        }
    }
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupUI()
    }
    
    // MARK: - Setup
    
    private func setupUI() {
        backgroundColor = UIColor.black.withAlphaComponent(0.3)
        
        // Configure score label
        scoreLabel.font = UIFont.systemFont(ofSize: 24, weight: .bold)
        scoreLabel.textColor = .white
        scoreLabel.textAlignment = .left
        scoreLabel.text = "Score: 0"
        scoreLabel.layer.shadowColor = UIColor.black.cgColor
        scoreLabel.layer.shadowOffset = CGSize(width: 2, height: 2)
        scoreLabel.layer.shadowOpacity = 0.8
        scoreLabel.layer.shadowRadius = 2
        
        // Configure level label
        levelLabel.font = UIFont.systemFont(ofSize: 20, weight: .medium)
        levelLabel.textColor = .white
        levelLabel.textAlignment = .center
        levelLabel.text = "Level 1"
        levelLabel.layer.shadowColor = UIColor.black.cgColor
        levelLabel.layer.shadowOffset = CGSize(width: 2, height: 2)
        levelLabel.layer.shadowOpacity = 0.8
        levelLabel.layer.shadowRadius = 2
        
        // Configure coins label
        coinsLabel.font = UIFont.systemFont(ofSize: 18, weight: .medium)
        coinsLabel.textColor = .systemYellow
        coinsLabel.textAlignment = .right
        coinsLabel.text = "💰 0"
        coinsLabel.layer.shadowColor = UIColor.black.cgColor
        coinsLabel.layer.shadowOffset = CGSize(width: 1, height: 1)
        coinsLabel.layer.shadowOpacity = 0.8
        coinsLabel.layer.shadowRadius = 1
        
        // Configure combo label
        comboLabel.font = UIFont.systemFont(ofSize: 28, weight: .heavy)
        comboLabel.textColor = .systemOrange
        comboLabel.textAlignment = .center
        comboLabel.text = ""
        comboLabel.alpha = 0
        comboLabel.layer.shadowColor = UIColor.black.cgColor
        comboLabel.layer.shadowOffset = CGSize(width: 2, height: 2)
        comboLabel.layer.shadowOpacity = 0.9
        comboLabel.layer.shadowRadius = 3
        
        // Configure FPS label (debug)
        #if DEBUG
        fpsLabel.font = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)
        fpsLabel.textColor = .green
        fpsLabel.textAlignment = .right
        fpsLabel.text = "60 FPS"
        fpsLabel.alpha = 0.7
        #endif
        
        // Add subviews
        addSubview(scoreLabel)
        addSubview(levelLabel)
        addSubview(coinsLabel)
        addSubview(comboLabel)
        #if DEBUG
        addSubview(fpsLabel)
        #endif
        
        setupConstraints()
    }
    
    private func setupConstraints() {
        scoreLabel.translatesAutoresizingMaskIntoConstraints = false
        levelLabel.translatesAutoresizingMaskIntoConstraints = false
        coinsLabel.translatesAutoresizingMaskIntoConstraints = false
        comboLabel.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            // Score label - top left
            scoreLabel.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor, constant: 20),
            scoreLabel.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor, constant: 10),
            
            // Level label - top center
            levelLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            levelLabel.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor, constant: 10),
            
            // Coins label - top right
            coinsLabel.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor, constant: -20),
            coinsLabel.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor, constant: 10),
            
            // Combo label - center
            comboLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            comboLabel.centerYAnchor.constraint(equalTo: centerYAnchor, constant: -100)
        ])
        
        #if DEBUG
        fpsLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            fpsLabel.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor, constant: -20),
            fpsLabel.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -10)
        ])
        #endif
    }
    
    // MARK: - Public Methods
    
    func updateScore(_ score: Int32) {
        scoreValue = score
    }
    
    func updateLevel(_ level: Int32) {
        levelValue = level
    }
    
    func updateCoins(_ coins: Int) {
        coinsLabel.text = "💰 \(coins)"
        animateCoinChange()
    }
    
    func showCombo(_ multiplier: Int) {
        comboLabel.text = "COMBO x\(multiplier)!"
        comboLabel.transform = CGAffineTransform(scaleX: 0.5, y: 0.5)
        
        UIView.animate(withDuration: 0.3, delay: 0, usingSpringWithDamping: 0.5, initialSpringVelocity: 10, options: .curveEaseOut) {
            self.comboLabel.alpha = 1
            self.comboLabel.transform = .identity
        } completion: { _ in
            UIView.animate(withDuration: 0.5, delay: 1.0, options: .curveEaseIn) {
                self.comboLabel.alpha = 0
            }
        }
    }
    
    func updateFPS(_ fps: Int) {
        #if DEBUG
        fpsLabel.text = "\(fps) FPS"
        fpsLabel.textColor = fps >= 55 ? .green : (fps >= 30 ? .yellow : .red)
        #endif
    }
    
    func showMessage(_ message: String, color: UIColor = .white, duration: TimeInterval = 2.0) {
        let messageLabel = UILabel()
        messageLabel.font = UIFont.systemFont(ofSize: 32, weight: .bold)
        messageLabel.textColor = color
        messageLabel.textAlignment = .center
        messageLabel.text = message
        messageLabel.alpha = 0
        messageLabel.layer.shadowColor = UIColor.black.cgColor
        messageLabel.layer.shadowOffset = CGSize(width: 2, height: 2)
        messageLabel.layer.shadowOpacity = 0.9
        messageLabel.layer.shadowRadius = 3
        
        addSubview(messageLabel)
        messageLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            messageLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            messageLabel.centerYAnchor.constraint(equalTo: centerYAnchor)
        ])
        
        UIView.animate(withDuration: 0.3) {
            messageLabel.alpha = 1
            messageLabel.transform = CGAffineTransform(scaleX: 1.2, y: 1.2)
        } completion: { _ in
            UIView.animate(withDuration: 0.3, delay: duration, options: .curveEaseOut) {
                messageLabel.alpha = 0
                messageLabel.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
            } completion: { _ in
                messageLabel.removeFromSuperview()
            }
        }
    }
    
    // MARK: - Animations
    
    private func animateScoreChange() {
        UIView.animate(withDuration: 0.2) {
            self.scoreLabel.transform = CGAffineTransform(scaleX: 1.2, y: 1.2)
        } completion: { _ in
            UIView.animate(withDuration: 0.1) {
                self.scoreLabel.transform = .identity
            }
        }
    }
    
    private func animateLevelChange() {
        UIView.animate(withDuration: 0.3, delay: 0, usingSpringWithDamping: 0.5, initialSpringVelocity: 10, options: .curveEaseOut) {
            self.levelLabel.transform = CGAffineTransform(scaleX: 1.5, y: 1.5)
        } completion: { _ in
            UIView.animate(withDuration: 0.2) {
                self.levelLabel.transform = .identity
            }
        }
        
        showMessage("LEVEL \(levelValue)", color: .systemYellow, duration: 1.5)
    }
    
    private func animateCoinChange() {
        UIView.animate(withDuration: 0.1) {
            self.coinsLabel.transform = CGAffineTransform(rotationAngle: .pi / 8)
        } completion: { _ in
            UIView.animate(withDuration: 0.1) {
                self.coinsLabel.transform = CGAffineTransform(rotationAngle: -.pi / 8)
            } completion: { _ in
                UIView.animate(withDuration: 0.1) {
                    self.coinsLabel.transform = .identity
                }
            }
        }
    }
}