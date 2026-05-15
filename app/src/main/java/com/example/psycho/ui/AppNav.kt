package com.example.psycho.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.psycho.data.UserPrefs
import com.example.psycho.model.ModelAssets
import com.example.psycho.ui.screens.DownloadScreen
import com.example.psycho.ui.screens.MainScreen
import com.example.psycho.ui.screens.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DOWNLOAD = "download"
    const val MAIN = "main"
}

@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    val ctx = LocalContext.current
    val onboarded by UserPrefs.onboardedFlow(ctx)
        .collectAsStateWithLifecycle(initialValue = null)

    val startDestination = when {
        onboarded == null -> null
        onboarded == false -> Routes.ONBOARDING
        ModelAssets.isModelInstalled(ctx) -> Routes.MAIN
        else -> Routes.DOWNLOAD
    } ?: return // wait for prefs to load

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(
                        if (ModelAssets.isModelInstalled(ctx)) Routes.MAIN else Routes.DOWNLOAD
                    ) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DOWNLOAD) {
            DownloadScreen(
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.DOWNLOAD) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainScreen()
        }
    }
}
