package com.ifitwereyou.character.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 墨香与炉火：深夜书房、旧皮革书封、烛火暖光、陈旧羊皮纸。
val Ink = Color(0xFF0C0B0A)
val InkRaised = Color(0xFF141210)
val Card = Color(0xFF1C1915)
val Secondary = Color(0xFF252118)
val Amber = Color(0xFFC4955A)
val AmberLight = Color(0xFFD4A574)
val Parchment = Color(0xFFE8DCC8)
val Muted = Color(0xFF9A8E7A)
val Border = Color(0xFF2A2520)
val Crimson = Color(0xFF9B3A3A)
val Forest = Color(0xFF4A7C59)
val Sage = Color(0xFF7C9082)

private val ColourScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = Secondary,
    onPrimaryContainer = Parchment,
    secondary = Sage,
    onSecondary = Ink,
    tertiary = AmberLight,
    onTertiary = Ink,
    background = Ink,
    onBackground = Parchment,
    surface = Card,
    onSurface = Parchment,
    surfaceVariant = Secondary,
    onSurfaceVariant = Muted,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = Card,
    outline = Border,
    outlineVariant = Border,
    error = Crimson,
    onError = Parchment,
    scrim = Ink,
)

private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 54.sp),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 11.sp, lineHeight = 16.sp),
)

val GameTextStyle = TextStyle(
    fontFamily = Mono,
    fontSize = 15.sp,
    lineHeight = 27.sp,
    letterSpacing = 0.3.sp,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun CharacterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColourScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
