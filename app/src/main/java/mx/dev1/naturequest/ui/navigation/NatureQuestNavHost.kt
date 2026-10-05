package mx.dev1.naturequest.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import mx.dev1.naturequest.ui.hunt.HuntScreen
import mx.dev1.naturequest.ui.setup.SetupScreen

@Serializable
data object Setup

@Serializable
data object Hunt

@Composable
fun NatureQuestNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Setup) {
        composable<Setup> {
            SetupScreen(onStart = { navController.navigate(Hunt) })
        }
        composable<Hunt> {
            HuntScreen(onBack = { navController.popBackStack() })
        }
    }
}
