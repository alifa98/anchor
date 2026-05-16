package info.faraji.anchor.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.faraji.anchor.data.UserPrefs
import info.faraji.anchor.ui.theme.ListenGreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

private data class Page(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val accentColor: androidx.compose.ui.graphics.Color? = null,
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val mic = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    var index by remember { mutableStateOf(0) }

    val pages = remember {
        listOf(
            Page(
                icon = Icons.Outlined.Hearing,
                title = "An anchor for what's real",
                body = "Anchor listens to the room around you and tells you, " +
                    "objectively, what is actually there. It runs on your device — " +
                    "no audio leaves the phone.",
            ),
            Page(
                icon = Icons.Outlined.Mic,
                title = "How it works",
                body = "Once you grant microphone access, Anchor keeps a rolling " +
                    "60 seconds of audio in memory. When you tap Verify, it asks " +
                    "the on-device AI what it actually hears in those 60 seconds.",
            ),
            Page(
                icon = Icons.Outlined.Shield,
                title = "The green-screen rule",
                body = "Anchor will only ever speak to you while you are " +
                    "pressing and holding the Listen button — and the entire " +
                    "screen turns bright green at the same time.\n\n" +
                    "If you hear a voice and the screen is NOT green, it is not " +
                    "Anchor. This rule never changes.",
                accentColor = ListenGreen,
            ),
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DotIndicator(count = pages.size, current = index, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(40.dp))

            AnimatedContent(
                targetState = index,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it / 2 } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 2 } + fadeOut()
                    } else {
                        slideInHorizontally { -it / 2 } + fadeIn() togetherWith
                            slideOutHorizontally { it / 2 } + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
                label = "onboarding-page",
            ) { i ->
                PageContent(pages[i])
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    OutlinedButton(onClick = { index-- }) { Text("Back") }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                Button(
                    onClick = {
                        when {
                            index < pages.lastIndex -> index++
                            !mic.status.isGranted -> mic.launchPermissionRequest()
                            else -> {
                                scope.launch {
                                    UserPrefs.setOnboarded(ctx, true)
                                    onFinished()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                ) {
                    val label = when {
                        index < pages.lastIndex -> "Next"
                        !mic.status.isGranted -> "Enable microphone"
                        else -> "Begin"
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

}

@Composable
private fun PageContent(page: Page) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    color = (page.accentColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.18f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = page.accentColor ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(36.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DotIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                modifier = Modifier
                    .size(width = if (active) 24.dp else 6.dp, height = 6.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
