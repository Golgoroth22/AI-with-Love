package com.example.aiwithlove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aiwithlove.ui.screen.ChatScreen
import com.example.aiwithlove.ui.screen.LaunchScreen
import com.example.aiwithlove.ui.screen.OllamaScreen
import com.example.aiwithlove.ui.screen.TeamScreen
import com.example.aiwithlove.ui.theme.AIWithLoveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIWithLoveTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "launch"
    ) {
        composable("launch") {
            LaunchScreen(
                onNavigateToAiAgent = {
                    navController.navigate("chat")
                },
                onNavigateToDocIndexing = {
                    navController.navigate("ollama")
                },
                onNavigateToSupport = {
                    navController.navigate("team")
                }
            )
        }

        composable("chat") {
            ChatScreen()
        }

        composable("ollama") {
            OllamaScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("team") {
            TeamScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
