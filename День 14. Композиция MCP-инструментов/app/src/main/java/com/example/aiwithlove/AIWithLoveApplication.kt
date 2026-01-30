package com.example.aiwithlove

import android.app.Application
import com.example.aiwithlove.di.KoinInitializer

class AIWithLoveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KoinInitializer.init(this)
    }
}
