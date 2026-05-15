package com.example.psycho.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.psycho.model.DownloadState
import com.example.psycho.model.ModelDownloader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(onComplete: () -> Unit) {
    val ctx = LocalContext.current
    val downloader = remember { ModelDownloader(ctx) }
    val state = remember { MutableStateFlow<DownloadState>(DownloadState.Idle) }
    val scope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    fun startDownload() {
        job?.cancel()
        job = scope.launch {
            downloader.download().collectLatest { state.value = it }
        }
    }

    LaunchedEffect(Unit) { startDownload() }

    val current by state.collectAsState()
    LaunchedEffect(current) {
        if (current is DownloadState.Done) onComplete()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier.size(108.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (current is DownloadState.Failed) Icons.Outlined.ErrorOutline
                                  else Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                if (current is DownloadState.Idle ||
                    (current is DownloadState.Progress && (current as DownloadState.Progress).totalBytes <= 0)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(108.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Setting up Anchor",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Downloading the on-device AI model. This is a one-time " +
                    "step (~2.5 GB). Future launches are instant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            when (val s = current) {
                is DownloadState.Progress -> ProgressBlock(s)
                is DownloadState.Failed -> FailureBlock(s) { startDownload() }
                is DownloadState.Done -> Text(
                    text = "Done. Opening Anchor…",
                    style = MaterialTheme.typography.bodyLarge,
                )
                DownloadState.Idle -> Text(
                    text = "Connecting…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProgressBlock(state: DownloadState.Progress) {
    val animatedFraction by animateFloatAsState(state.fraction, label = "dl-frac")
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "%,.0f / %,.0f MB".format(state.downloadedMb, state.totalMb),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (state.mbPerSec > 0) "%.1f MB/s".format(state.mbPerSec) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "%d %%".format((state.fraction * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailureBlock(state: DownloadState.Failed, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Download failed",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
        ) {
            Text("Retry", fontSize = 16.sp)
        }
    }
}

