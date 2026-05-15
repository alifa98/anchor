package com.example.psycho.ui.theme

import androidx.compose.ui.graphics.Color

// Calm slate / sand / muted teal palette.
// Avoid hot reds, bright yellows, harsh contrasts — those can be agitating.

val SlateInk = Color(0xFF1B2230)        // background dark
val SlateMist = Color(0xFFEFF1F4)       // background light
val SlateSurface = Color(0xFF242C3B)
val SlateSurfaceLight = Color(0xFFFFFFFF)

val MutedTeal = Color(0xFF6FA8A1)       // primary accent (calm)
val MutedTealDark = Color(0xFF3F7A74)
val WarmSand = Color(0xFFD9C7A7)        // secondary
val SoftClay = Color(0xFFB58A78)        // tertiary

val InkOn = Color(0xFFE8ECF1)
val InkOnLight = Color(0xFF1B2230)
val MutedOn = Color(0xFF9CA7B8)

// SAFETY-CRITICAL color: only used for the full-screen overlay while
// the user holds the Listen button. Pure, unmistakable green so it can
// never be confused with normal UI accents or with a hallucinated voice.
val ListenGreen = Color(0xFF00E676)
val ListenGreenDeep = Color(0xFF00C853)
