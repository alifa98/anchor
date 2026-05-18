# Anchor Technical Architecture

This document describes the high-level architecture and technical decisions behind Anchor.

## System Overview

Anchor is designed to be a "Zero-Trust" audio observer. The core flow involves continuous background audio buffering, user-triggered inference, and accessibility-first output.

```mermaid
graph TD
    A[Microphone] --> B[AudioCaptureService]
    B --> C[Rolling PCM Buffer (60s)]
    D[MainScreen] -- Trigger Verify --> E[MainViewModel]
    E --> F[GemmaEngineHolder]
    C -- Consume WAV --> F
    F --> G[LiteRT-LM Engine]
    G --> H[Multimodal Gemma Model]
    H --> I[Objective Transcript]
    I --> J[HoldToSpeakTts]
    J --> K[Speaker/Headphones]
```

## Key Components

### 1. `AudioCaptureService`
A Foreground Service that manages the device microphone. It maintains a `RollingPcmBuffer` of the last 60 seconds.
- **Why a Service?** To ensure audio capture isn't killed when the app is backgrounded, allowing the user to switch back and verify a sound they heard a few seconds ago.
- **Privacy**: The buffer resides in volatile memory and is never written to disk or sent over the network.

### 2. `GemmaEngineHolder`
Manages the lifecycle of the LiteRT-LM `Engine`.
- **On-Demand Loading**: Multimodal models are memory-intensive. `GemmaEngineHolder` loads the model only when needed and unloads it after a period of inactivity (default 90s).
- **GPU Acceleration**: It attempts to initialize the model on the GPU first, falling back to CPU if initialization fails.
- **Multi-Token Prediction**: (Planned) To increase inference speed.

### 3. `HoldToSpeakTts`
A custom Text-to-Speech wrapper that implements a "Release to Mute" contract.
- **Visual Feedback**: When Anchor speaks, the entire screen turns green. This is a critical safety feature to ensure the user knows the sound is coming from the app.
- **Interaction**: The user must hold a button to permit the app to speak. Releasing the button immediately silences the output.

## Anti-Hallucination Prompt Engineering

The system prompt in `GemmaEngineHolder` is the "brain" of the objective observer. It is engineered with several key constraints:
- **Strict Objectivity**: The model is forbidden from using emotional language or offering reassurance.
- **Direct Contradiction**: If the user asks about a sound that isn't in the audio, the model is instructed to say "I do not hear that."
- **Formatting**: Output is limited to 1-3 factual bullet points to reduce cognitive load.

## Tech Stack Decisions

- **Kotlin Coroutines & Flow**: Used for non-blocking UI and efficient streaming of audio levels and transcripts.
- **Jetpack Compose**: Enables the complex "Green Overlay" visual states and reactive UI.
- **LiteRT-LM**: Chosen for its efficient execution of Gemma models on Android hardware.

## Safety Considerations

- **Visual Sync**: The "speaking" state in the UI is tightly coupled with the TTS engine.
- **Model Unloading**: Aggressive memory management to prevent the app from slowing down the device.
