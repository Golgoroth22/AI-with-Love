package com.example.aiwithlove

import android.app.Application
import com.example.aiwithlove.di.KoinInitializer
import com.example.aiwithlove.scheduler.NotificationHelper

class AIWithLoveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KoinInitializer.init(this)
        NotificationHelper(this).createNotificationChannels()
    }
}
