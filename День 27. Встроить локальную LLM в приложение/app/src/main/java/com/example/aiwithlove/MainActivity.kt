package com.example.aiwithlove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.aiwithlove.di.appModule
import com.example.aiwithlove.ui.ChatScreen
import com.example.aiwithlove.ui.GGUFModelScreen
import com.example.aiwithlove.ui.theme.AIWithLoveTheme
import com.example.aiwithlove.viewmodel.GGUFModelViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MainActivity)
                modules(appModule)
            }
        }

        enableEdgeToEdge()
        setContent {
            AIWithLoveTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: GGUFModelViewModel = koinViewModel()) {
    val isModelReady by viewModel.isModelReady.collectAsState()
    var showChat by remember { mutableStateOf(false) }

    if (isModelReady || showChat) {
        ChatScreen()
    } else {
        GGUFModelScreen(
            onModelReady = { showChat = true }
        )
    }
}