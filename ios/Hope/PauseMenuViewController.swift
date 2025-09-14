// PauseMenuViewController.swift
// Pause menu overlay

import UIKit

protocol PauseMenuDelegate: AnyObject {
    func pauseMenuDidResume()
    func pauseMenuDidQuit()
}

class PauseMenuViewController: UIViewController {
    
    // MARK: - Properties
    
    weak var delegate: PauseMenuDelegate?
    
    private let containerView = UIView()
    private let titleLabel = UILabel()
    private let resumeButton = UIButton(type: .system)
    private let settingsButton = UIButton(type: .system)
    private let quitButton = UIButton(type: .system)
    
    private let blurEffect = UIBlurEffect(style: .dark)
    private lazy var blurView = UIVisualEffectView(effect: blurEffect)
    
    // MARK: - Lifecycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        animateIn()
    }
    
    // MARK: - Setup
    
    private func setupUI() {
        view.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        
        // Setup blur background
        blurView.frame = view.bounds
        blurView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(blurView)
        
        // Setup container
        containerView.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.95)
        containerView.layer.cornerRadius = 20
        containerView.layer.shadowColor = UIColor.black.cgColor
        containerView.layer.shadowOffset = CGSize(width: 0, height: 10)
        containerView.layer.shadowOpacity = 0.3
        containerView.layer.shadowRadius = 20
        view.addSubview(containerView)
        
        // Setup title
        titleLabel.text = "PAUSED"
        titleLabel.font = UIFont.systemFont(ofSize: 36, weight: .heavy)
        titleLabel.textAlignment = .center
        titleLabel.textColor = .label
        containerView.addSubview(titleLabel)
        
        // Setup buttons
        setupButton(resumeButton, title: "Resume", color: .systemGreen, action: #selector(resumeTapped))
        setupButton(settingsButton, title: "Settings", color: .systemBlue, action: #selector(settingsTapped))
        setupButton(quitButton, title: "Quit", color: .systemRed, action: #selector(quitTapped))
        
        containerView.addSubview(resumeButton)
        containerView.addSubview(settingsButton)
        containerView.addSubview(quitButton)
        
        setupConstraints()
    }
    
    private func setupButton(_ button: UIButton, title: String, color: UIColor, action: Selector) {
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 20, weight: .semibold)
        button.backgroundColor = color
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 12
        button.addTarget(self, action: action, for: .touchUpInside)
        
        // Add haptic feedback
        button.addTarget(self, action: #selector(buttonTouchDown), for: .touchDown)
    }
    
    private func setupConstraints() {
        containerView.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        resumeButton.translatesAutoresizingMaskIntoConstraints = false
        settingsButton.translatesAutoresizingMaskIntoConstraints = false
        quitButton.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            // Container
            containerView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            containerView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            containerView.widthAnchor.constraint(equalToConstant: 300),
            containerView.heightAnchor.constraint(equalToConstant: 350),
            
            // Title
            titleLabel.topAnchor.constraint(equalTo: containerView.topAnchor, constant: 30),
            titleLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -20),
            
            // Resume button
            resumeButton.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 40),
            resumeButton.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 40),
            resumeButton.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -40),
            resumeButton.heightAnchor.constraint(equalToConstant: 50),
            
            // Settings button
            settingsButton.topAnchor.constraint(equalTo: resumeButton.bottomAnchor, constant: 20),
            settingsButton.leadingAnchor.constraint(equalTo: resumeButton.leadingAnchor),
            settingsButton.trailingAnchor.constraint(equalTo: resumeButton.trailingAnchor),
            settingsButton.heightAnchor.constraint(equalToConstant: 50),
            
            // Quit button
            quitButton.topAnchor.constraint(equalTo: settingsButton.bottomAnchor, constant: 20),
            quitButton.leadingAnchor.constraint(equalTo: resumeButton.leadingAnchor),
            quitButton.trailingAnchor.constraint(equalTo: resumeButton.trailingAnchor),
            quitButton.heightAnchor.constraint(equalToConstant: 50)
        ])
    }
    
    // MARK: - Actions
    
    @objc private func resumeTapped() {
        animateOut {
            self.delegate?.pauseMenuDidResume()
        }
    }
    
    @objc private func settingsTapped() {
        let settingsVC = SettingsViewController()
        settingsVC.modalPresentationStyle = .overCurrentContext
        present(settingsVC, animated: true)
    }
    
    @objc private func quitTapped() {
        let alert = UIAlertController(
            title: "Quit Game",
            message: "Are you sure you want to quit? Your progress will be saved.",
            preferredStyle: .alert
        )
        
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Quit", style: .destructive) { _ in
            self.delegate?.pauseMenuDidQuit()
        })
        
        present(alert, animated: true)
    }
    
    @objc private func buttonTouchDown() {
        // Haptic feedback
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
    }
    
    // MARK: - Animations
    
    private func animateIn() {
        containerView.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
        containerView.alpha = 0
        
        UIView.animate(withDuration: 0.3, delay: 0, usingSpringWithDamping: 0.7, initialSpringVelocity: 0.5, options: .curveEaseOut) {
            self.containerView.transform = .identity
            self.containerView.alpha = 1
        }
    }
    
    private func animateOut(completion: @escaping () -> Void) {
        UIView.animate(withDuration: 0.2, animations: {
            self.containerView.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
            self.containerView.alpha = 0
            self.view.alpha = 0
        }) { _ in
            self.dismiss(animated: false, completion: completion)
        }
    }
}