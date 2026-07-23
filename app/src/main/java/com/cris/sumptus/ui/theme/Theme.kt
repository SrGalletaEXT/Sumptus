package com.cris.sumptus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SumptusColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Clay,
    onPrimaryContainer = Color.White,
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5D5),
    onSecondaryContainer = Ink,
    background = Sand,
    onBackground = Ink,
    surface = Color.White.copy(alpha = 0.92f),
    onSurface = Ink,
    surfaceVariant = Mist,
    outline = Color(0xFFD9C8B9),
)

private val SumptusDarkColors = darkColorScheme(
    primary = Color(0xFF95C0C7),
    onPrimary = Color(0xFF12343D),
    primaryContainer = Color(0xFF315964),
    onPrimaryContainer = Color(0xFFE0F3F3),
    secondary = Color(0xFF87C7B6),
    onSecondary = Color(0xFF133D35),
    secondaryContainer = Color(0xFF28564C),
    onSecondaryContainer = Color(0xFFD2F2E7),
    background = Color(0xFF171A19),
    onBackground = Color(0xFFE8E5DF),
    surface = Color(0xFF202523),
    onSurface = Color(0xFFE8E5DF),
    surfaceVariant = Color(0xFF303834),
    outline = Color(0xFF9AA8A0),
)

private val SumptusTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
    ),
)

@Composable
fun SumptusTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SumptusDarkColors else SumptusColors,
        typography = SumptusTypography,
        content = content,
    )
}
