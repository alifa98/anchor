package info.faraji.anchor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import info.faraji.anchor.ui.AppNav
import info.faraji.anchor.ui.theme.AnchorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AnchorTheme {
                AppNav()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Releasing the model when the user backgrounds the app keeps memory
        // pressure low. Capture continues in the foreground service.
        (application as AnchorApp).engineHolder.scheduleUnload()
    }
}
