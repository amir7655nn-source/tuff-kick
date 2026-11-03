package com.tuff.kick

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Ink = Color(0xFF0E0E10)
    val Panel = Color(0xFF18181B)
    val Raised = Color(0xFF26262C)
    val Line = Color(0xFF2F2F35)
    val Text = Color(0xFFEFEFF1)
    val Muted = Color(0xFFADADB8)
    val Accent = Color(0xFF53FC18)
    val AccentInk = Color(0xFF0E0E10)
    val Live = Color(0xFFEB0400)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.AccentInk,
    background = Palette.Ink,
    onBackground = Palette.Text,
    surface = Palette.Panel,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Raised,
    onSurfaceVariant = Palette.Muted,
)

@Composable
fun TuffTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
