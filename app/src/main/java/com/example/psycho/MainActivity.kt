package com.example.psycho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.psycho.ui.AppNav
import com.example.psycho.ui.theme.PsychoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PsychoTheme {
                AppNav()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Releasing the model when the user backgrounds the app keeps memory
        // pressure low. Capture continues in the foreground service.
        (application as PsychoApp).engineHolder.scheduleUnload()
    }
}
