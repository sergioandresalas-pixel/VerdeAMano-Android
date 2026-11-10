package com.verdeamano.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BotanicalColors {
    val Forest = Color(0xFF112A22)
    val Botanical = Color(0xFF2E8066)
    val Leaf = Color(0xFF3C9979)
    val Sage = Color(0xFFDCEBCB)
    val Ivory = Color(0xFFF7F8F2)
    val Muted = Color(0xFF4C6258)
}

private val VerdeColors = lightColorScheme(
    primary = BotanicalColors.Forest,
    onPrimary = Color.White,
    primaryContainer = BotanicalColors.Sage,
    onPrimaryContainer = BotanicalColors.Forest,
    secondary = Color(0xFF206B52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F1E8),
    onSecondaryContainer = BotanicalColors.Forest,
    tertiary = Color(0xFF725A3D),
    onTertiary = Color.White,
    background = BotanicalColors.Ivory,
    onBackground = BotanicalColors.Forest,
    surface = Color.White,
    onSurface = BotanicalColors.Forest,
    surfaceVariant = Color(0xFFEAF0E7),
    onSurfaceVariant = BotanicalColors.Muted,
    surfaceContainer = Color(0xFFEAF0E7),
    surfaceContainerLow = Color.White,
    outline = Color(0xFF60766A),
    outlineVariant = Color(0xFFCCD9CE),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
)

private val BaseTypography = Typography()
private val VerdeTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 29.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 32.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(lineHeight = 25.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(lineHeight = 21.sp),
)

@Composable
fun VerdeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VerdeColors,
        typography = VerdeTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
