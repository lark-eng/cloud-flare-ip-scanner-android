package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// Premium Cybernetic Dark Blue-Black Theme Palette
var DarkBg by mutableStateOf(Color(0xFF070A0F))
var CardBg by mutableStateOf(Color(0xFF0F1622))
var SurfaceVariantBg by mutableStateOf(Color(0xFF1B2535))

// Accent blues and cyans instead of orange/amber
var AccentBlue by mutableStateOf(Color(0xFF2E8BFF))
var AccentCyan by mutableStateOf(Color(0xFF00E5FF))
var AccentTeal by mutableStateOf(Color(0xFF00FFCC))
var AccentGreen by mutableStateOf(Color(0xFF10B981))
var AccentYellow by mutableStateOf(Color(0xFFF59E0B))

var TextPrimary by mutableStateOf(Color(0xFFF1F5F9))
var TextSecondary by mutableStateOf(Color(0xFF8A99AD))
var GridDivider by mutableStateOf(Color(0xFF1E293B))

fun Color.isLight(): Boolean {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return luminance > 0.5f
}
