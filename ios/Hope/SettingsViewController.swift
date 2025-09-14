// SettingsViewController.swift
// Game settings interface

import UIKit

class SettingsViewController: UIViewController {
    
    // MARK: - Properties
    
    private let containerView = UIView()
    private let titleLabel = UILabel()
    private let closeButton = UIButton(type: .system)
    
    private let soundVolumeSlider = UISlider()
    private let musicVolumeSlider = UISlider()
    private let hapticSwitch = UISwitch()
    private let notificationsSwitch = UISwitch()
    
    private let ffi = HopeFFI.shared
    
    // MARK: - Lifecycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        loadSettings()
    }
    
    // MARK: - Setup
    
    private func setupUI() {
        view.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        
        // Container setup
        containerView.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.95)
        containerView.layer.cornerRadius = 20
        containerView.layer.shadowColor = UIColor.black.cgColor
        containerView.layer.shadowOffset = CGSize(width: 0, height: 10)
        containerView.layer.shadowOpacity = 0.3
        containerView.layer.shadowRadius = 20
        view.addSubview(containerView)
        
        // Title setup
        titleLabel.text = "Settings"
        titleLabel.font = UIFont.systemFont(ofSize: 28, weight: .bold)
        titleLabel.textAlignment = .center
        containerView.addSubview(titleLabel)
        
        // Close button
        closeButton.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal)
        closeButton.tintColor = .systemGray
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        containerView.addSubview(closeButton)
        
        // Sound settings
        let soundLabel = createLabel("Sound Effects")
        containerView.addSubview(soundLabel)
        
        soundVolumeSlider.minimumValue = 0
        soundVolumeSlider.maximumValue = 1
        soundVolumeSlider.addTarget(self, action: #selector(soundVolumeChanged), for: .valueChanged)
        containerView.addSubview(soundVolumeSlider)
        
        // Music settings
        let musicLabel = createLabel("Music")
        containerView.addSubview(musicLabel)
        
        musicVolumeSlider.minimumValue = 0
        musicVolumeSlider.maximumValue = 1
        musicVolumeSlider.addTarget(self, action: #selector(musicVolumeChanged), for: .valueChanged)
        containerView.addSubview(musicVolumeSlider)
        
        // Haptic settings
        let hapticLabel = createLabel("Haptic Feedback")
        containerView.addSubview(hapticLabel)
        
        hapticSwitch.addTarget(self, action: #selector(hapticToggled), for: .valueChanged)
        containerView.addSubview(hapticSwitch)
        
        // Notifications settings
        let notificationsLabel = createLabel("Notifications")
        containerView.addSubview(notificationsLabel)
        
        notificationsSwitch.addTarget(self, action: #selector(notificationsToggled), for: .valueChanged)
        containerView.addSubview(notificationsSwitch)
        
        // Setup constraints
        setupConstraints(
            soundLabel: soundLabel,
            musicLabel: musicLabel,
            hapticLabel: hapticLabel,
            notificationsLabel: notificationsLabel
        )
    }
    
    private func createLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = UIFont.systemFont(ofSize: 16, weight: .medium)
        label.textColor = .label
        return label
    }
    
    private func setupConstraints(soundLabel: UILabel, musicLabel: UILabel, hapticLabel: UILabel, notificationsLabel: UILabel) {
        containerView.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        soundLabel.translatesAutoresizingMaskIntoConstraints = false
        soundVolumeSlider.translatesAutoresizingMaskIntoConstraints = false
        musicLabel.translatesAutoresizingMaskIntoConstraints = false
        musicVolumeSlider.translatesAutoresizingMaskIntoConstraints = false
        hapticLabel.translatesAutoresizingMaskIntoConstraints = false
        hapticSwitch.translatesAutoresizingMaskIntoConstraints = false
        notificationsLabel.translatesAutoresizingMaskIntoConstraints = false
        notificationsSwitch.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            // Container
            containerView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            containerView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            containerView.widthAnchor.constraint(equalToConstant: 320),
            containerView.heightAnchor.constraint(equalToConstant: 400),
            
            // Title
            titleLabel.topAnchor.constraint(equalTo: containerView.topAnchor, constant: 20),
            titleLabel.centerXAnchor.constraint(equalTo: containerView.centerXAnchor),
            
            // Close button
            closeButton.topAnchor.constraint(equalTo: containerView.topAnchor, constant: 20),
            closeButton.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -20),
            closeButton.widthAnchor.constraint(equalToConstant: 30),
            closeButton.heightAnchor.constraint(equalToConstant: 30),
            
            // Sound
            soundLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 40),
            soundLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 30),
            
            soundVolumeSlider.topAnchor.constraint(equalTo: soundLabel.bottomAnchor, constant: 10),
            soundVolumeSlider.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 30),
            soundVolumeSlider.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -30),
            
            // Music
            musicLabel.topAnchor.constraint(equalTo: soundVolumeSlider.bottomAnchor, constant: 30),
            musicLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 30),
            
            musicVolumeSlider.topAnchor.constraint(equalTo: musicLabel.bottomAnchor, constant: 10),
            musicVolumeSlider.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 30),
            musicVolumeSlider.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -30),
            
            // Haptic
            hapticLabel.topAnchor.constraint(equalTo: musicVolumeSlider.bottomAnchor, constant: 30),
            hapticLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 30),
            
            hapticSwitch.centerYAnchor.constraint(equalTo: hapticLabel.centerYAnchor),
            hapticSwitch.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -30),
            
            // Notifications
            notificationsLabel.topAnchor.constraint(equalTo: hapticLabel.bottomAnchor, constant: 30),
            notificationsLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 30),
            
            notificationsSwitch.centerYAnchor.constraint(equalTo: notificationsLabel.centerYAnchor),
            notificationsSwitch.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -30)
        ])
    }
    
    // MARK: - Settings Management
    
    private func loadSettings() {
        let defaults = UserDefaults.standard
        soundVolumeSlider.value = defaults.float(forKey: "soundVolume")
        musicVolumeSlider.value = defaults.float(forKey: "musicVolume")
        hapticSwitch.isOn = defaults.bool(forKey: "hapticEnabled")
        notificationsSwitch.isOn = defaults.bool(forKey: "notificationsEnabled")
    }
    
    private func saveSettings() {
        let defaults = UserDefaults.standard
        defaults.set(soundVolumeSlider.value, forKey: "soundVolume")
        defaults.set(musicVolumeSlider.value, forKey: "musicVolume")
        defaults.set(hapticSwitch.isOn, forKey: "hapticEnabled")
        defaults.set(notificationsSwitch.isOn, forKey: "notificationsEnabled")
    }
    
    // MARK: - Actions
    
    @objc private func closeTapped() {
        saveSettings()
        dismiss(animated: true)
    }
    
    @objc private func soundVolumeChanged() {
        ffi.setAudioVolume(soundVolumeSlider.value)
        provideHapticFeedback()
    }
    
    @objc private func musicVolumeChanged() {
        // Send music volume to Rust
        provideHapticFeedback()
    }
    
    @objc private func hapticToggled() {
        provideHapticFeedback()
    }
    
    @objc private func notificationsToggled() {
        if notificationsSwitch.isOn {
            requestNotificationPermission()
        }
        provideHapticFeedback()
    }
    
    private func provideHapticFeedback() {
        guard hapticSwitch.isOn else { return }
        let generator = UISelectionFeedbackGenerator()
        generator.selectionChanged()
    }
    
    private func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if !granted {
                DispatchQueue.main.async {
                    self.notificationsSwitch.isOn = false
                }
            }
        }
    }
}