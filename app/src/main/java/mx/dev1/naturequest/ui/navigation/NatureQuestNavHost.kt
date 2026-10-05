package mx.dev1.naturequest.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.ui.hunt.HuntScreen
import mx.dev1.naturequest.ui.hunt.HuntViewModel
import mx.dev1.naturequest.ui.setup.SetupScreen
import mx.dev1.naturequest.ui.verify.VerifyScreen
import mx.dev1.naturequest.ui.verify.VerifyViewModel

@Serializable
data object Setup

@Serializable
data class Hunt(
    val place: PlaceType,
    val length: HuntLength,
    val ageRange: AgeRange,
)

/** Take and check a photo for one hunt item (its id in the current hunt). */
@Serializable
data class Verify(val itemId: Int)

@Composable
fun NatureQuestNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Setup) {
        composable<Setup> {
            SetupScreen(
                onStart = { settings ->
                    navController.navigate(Hunt(settings.place, settings.length, settings.ageRange))
                },
            )
        }
        composable<Hunt> { entry ->
            val route = entry.toRoute<Hunt>()
            val settings = HuntSettings(route.place, route.length, route.ageRange)
            val viewModel = hiltViewModel<HuntViewModel, HuntViewModel.Factory>(
                creationCallback = { factory -> factory.create(settings) },
            )
            HuntScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFoundSomething = { itemId -> navController.navigate(Verify(itemId)) },
            )
        }
        composable<Verify> { entry ->
            val route = entry.toRoute<Verify>()
            val viewModel = hiltViewModel<VerifyViewModel, VerifyViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.itemId) },
            )
            VerifyScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
