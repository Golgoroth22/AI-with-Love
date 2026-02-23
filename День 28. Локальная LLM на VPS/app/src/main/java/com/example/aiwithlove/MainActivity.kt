package com.example.aiwithlove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aiwithlove.di.appModule
import com.example.aiwithlove.ui.ChatScreen
import com.example.aiwithlove.ui.theme.AIWithLoveTheme
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startKoin {
            androidContext(this@MainActivity)
            modules(appModule)
        }

        enableEdgeToEdge()
        setContent {
            AIWithLoveTheme {
                ChatScreen()
            }
        }
    }
}