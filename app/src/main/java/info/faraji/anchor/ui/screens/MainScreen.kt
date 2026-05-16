package info.faraji.anchor.ui.screens

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.faraji.anchor.ui.MainViewModel
import info.faraji.anchor.ui.theme.ListenGreen
import info.faraji.anchor.ui.theme.ListenGreenDeep
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val mic = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val isCapturing by viewModel.isCapturing.collectAsState()
    val rms by viewModel.rmsLevel.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val isVerifying by viewModel.isVerifying.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val listening by viewModel.listenButtonHeld.collectAsState()

    LaunchedEffect(mic.status.isGranted) {
        if (mic.status.isGranted && !isCapturing) viewModel.startCapture()
        if (!mic.status.isGranted) mic.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 22.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(8.dp))
                Header(ttsEnabled = ttsEnabled, onToggleTts = viewModel::setTtsEnabled)

                Spacer(Modifier.height(28.dp))
                MicVisualizer(isCapturing = isCapturing, rms = rms)

                Spacer(Modifier.height(28.dp))
                CaptureToggle(
                    isCapturing = isCapturing,
                    onToggle = {
                        if (isCapturing) viewModel.stopCapture()
                        else viewModel.startCapture()
                    },
                )

                Spacer(Modifier.height(20.dp))
                VerifyButton(
                    enabled = isCapturing && !isVerifying,
                    isVerifying = isVerifying,
                    onClick = { viewModel.verifyNow() },
                )

                Spacer(Modifier.height(24.dp))
                TranscriptCard(transcript = transcript, engineState = engineState)

                Spacer(Modifier.height(24.dp))

                if (ttsEnabled) {
                    HoldToListenButton(
                        held = listening,
                        onPress = { viewModel.setListenButtonHeld(true) },
                        onRelease = { viewModel.setListenButtonHeld(false) },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Press and hold to let Anchor speak. The screen will turn green.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }

        // SAFETY: Full-screen green overlay while user is holding Listen.
        // This is the visual half of the anti-hallucination contract.
        AnimatedVisibility(
            visible = listening,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize(),
        ) {
            ListenOverlay()
        }
    }
}

@Composable
private fun Header(ttsEnabled: Boolean, onToggleTts: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Anchor", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Listening to the room",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconToggleButton(
            checked = ttsEnabled,
            onCheckedChange = onToggleTts,
        ) {
            Icon(
                imageVector = Icons.Outlined.RecordVoiceOver,
                contentDescription = if (ttsEnabled) "Voice on" else "Voice off",
            )
        }
    }
}

@Composable
private fun MicVisualizer(isCapturing: Boolean, rms: Float) {
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val scale = if (isCapturing) pulse + (rms * 0.18f) else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(scale)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(132.dp)
                .scale(scale * 0.95f)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(94.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isCapturing) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun CaptureToggle(isCapturing: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCapturing) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.primary,
            contentColor = if (isCapturing) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(
            imageVector = if (isCapturing) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = if (isCapturing) "Stop listening" else "Start listening",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun VerifyButton(enabled: Boolean, isVerifying: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        if (isVerifying) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSecondary,
            )
            Spacer(Modifier.size(12.dp))
            Text("Listening to the last 60 seconds…", style = MaterialTheme.typography.labelLarge)
        } else {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("Verify what I just heard", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TranscriptCard(transcript: String, engineState: info.faraji.anchor.model.EngineState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "What's actually there",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            val body = when {
                transcript.isNotBlank() -> transcript
                engineState is info.faraji.anchor.model.EngineState.Loading ->
                    "Loading the on-device model…"
                engineState is info.faraji.anchor.model.EngineState.Error ->
                    "Model error: ${engineState.message}"
                else -> "Tap Verify when you want a check on the last 60 seconds."
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HoldToListenButton(held: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (held) 1.06f else 1f,
        label = "hold-scale",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .scale(scale)
            .background(
                color = if (held) ListenGreenDeep else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onRelease()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.RecordVoiceOver,
                contentDescription = null,
                tint = if (held) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = if (held) "LISTENING — RELEASE TO MUTE" else "Hold to let Anchor speak",
                style = MaterialTheme.typography.labelLarge,
                color = if (held) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ListenOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ListenGreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "ANCHOR",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = Color.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "is speaking now",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
            )
            Spacer(Modifier.height(48.dp))
            Text(
                text = "Release the button to mute.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black,
            )
        }
    }
}

