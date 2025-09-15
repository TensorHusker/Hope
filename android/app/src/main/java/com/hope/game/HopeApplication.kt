package com.hope.game

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Hope Game
 */
@HiltAndroidApp
class HopeApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Enable Crashlytics collection
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
        
        // Set up custom crash keys
        Firebase.crashlytics.setCustomKeys {
            key("app_version", BuildConfig.VERSION_NAME)
            key("app_version_code", BuildConfig.VERSION_CODE)
            key("device_model", android.os.Build.MODEL)
            key("android_version", android.os.Build.VERSION.SDK_INT)
        }
        
        // Initialize analytics
        Firebase.analytics.setAnalyticsCollectionEnabled(true)
        
        // Set default event parameters
        Firebase.analytics.setDefaultEventParameters(
            bundleOf(
                "app_version" to BuildConfig.VERSION_NAME,
                "platform" to "android"
            )
        )
        
        // Set user properties
        Firebase.analytics.setUserProperty("app_version", BuildConfig.VERSION_NAME)
        
        // Initialize any other SDK components
        initializeSDKs()
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR
            )
            .build()
    }
    
    private fun initializeSDKs() {
        // Initialize any third-party SDKs here
        // For example: AdMob, Facebook SDK, etc.
    }
}