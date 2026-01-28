package com.example.aiwithlove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aiwithlove.ui.screen.ChatScreen
import com.example.aiwithlove.ui.theme.AIWithLoveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIWithLoveTheme {
                ChatScreen()
            }
        }
    }
}
