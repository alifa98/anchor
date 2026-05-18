# Anchor: Your Objective Ear in a Subjective World

**Anchor** is an Android application designed to support individuals who experience auditory hallucinations. It acts as an "audio observer," using on-device multimodal AI to provide an objective reality check on environmental sounds.

## 🌟 The Problem & Our Mission

Auditory hallucinations can be distressing and confusing. Is that voice real? Did someone actually knock? Anchor's mission is to provide immediate, objective verification without compromising privacy. By listening to the last 60 seconds of audio and describing it through a specialized AI model, Anchor helps users "anchor" themselves in reality.

## ✨ Key Features

- **Real-time Audio Verification**: Tap "Verify" to have the AI analyze the last 60 seconds of environment audio.
- **On-Device Multimodal AI**: Uses [LiteRT-LM](https://ai.google.dev/edge/litert) with the **Gemma** model to process audio locally—no audio ever leaves your device.
- **Safety-First UI**: A high-contrast "Listen" mode with a full-screen green overlay provides clear visual feedback when the app is speaking, preventing the app's own voice from being confused with the environment.
- **Background Capture**: A foreground service ensures you can trigger verification even if the app was in the background.
- **Privacy by Design**: Zero data collection. All processing happens on the NPU/GPU/CPU of your phone.

## 💪 Advantages

- **Privacy**: Processing audio on-device is the only way to ensure the absolute privacy of sensitive personal environments.
- **Objective Prompting**: Our system instructions are strictly tuned to be factual and non-emotional. It doesn't speculate; it only labels what is actually there.
- **Low Friction**: Designed for high-stress moments with large, accessible buttons and clear visual states.
- **Resource Efficient**: Automatically loads and unloads the heavy AI model to preserve battery and memory.


## 📲 Installation

- See the [GitHub Release](https://github.com/alifa98/anchor/releases) of the project to download the lastest version.


## 🛠️ Architecture Overview

Anchor is built with a modern Android stack:
- **UI**: Jetpack Compose for a reactive, accessible interface.
- **AI**: Google LiteRT-LM (Multimodal Gemma) for on-device inference.
- **Audio**: Low-level PCM buffering in a Kotlin Foreground Service.
- **State**: Kotlin Coroutines and Flow for seamless data streaming.

For more details, see [ARCHITECTURE.md](ARCHITECTURE.md).


## 🚀 Getting Started as Developer

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/anchor.git
   ```
2. Open the project in **Android Studio (Ladybug or newer)**.
3. Place your Gemma model file in the specified assets directory (or follow the in-app download instructions if implemented).
4. Build and run the `:app` module.

## 🤝 Contributing

We welcome contributions! Whether it's improving the prompt engineering, optimizing audio capture, or enhancing the UI accessibility.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## ⚖️ License

Distributed under the MIT License. See `LICENSE` for more information.

---
*Disclaimer: Anchor is an assistive tool and not a medical device. It is intended to complement, not replace, professional medical advice and treatment.*
