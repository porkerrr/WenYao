package com.peterpork.liuyao.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic spacing scale shared by every Compose surface. */
internal object WenYaoSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val PageHorizontal = 16.dp
    val PageHorizontalExpanded = 24.dp
    val ActionHeight = 52.dp
    val SecondaryActionHeight = 48.dp
}

internal object WenYaoLayout {
    val BrandMarkHeight = 96.dp
    val ImeBrandMarkHeight = 72.dp
    val ImeQuestionMinHeight = 108.dp
    val ContentMaxWidth = 720.dp
    val CastingStageHeight = 208.dp
    val LineLabelWidth = 48.dp
    val LineValueWidth = 80.dp
    val ProgressSize = 20.dp
    val ListItemMinHeight = 64.dp
    val MiniGlyphSize = 24.dp
    val EmptyStateSize = 64.dp
    val MenuMaxHeight = 256.dp
}

internal object WenYaoShapeTokens {
    val OutlineWidth = 1.dp
    val FlatElevation = 0.dp
    val DivinationLine = 3.dp
    val FocusOutlineWidth = 2.dp
    val ErrorOutlineWidth = 1.5.dp
    val ModalElevation = 6.dp
}

internal object WenYaoMotion {
    const val ImeLayoutMillis = 220
    const val CoinRevealMillis = 750
    const val TransitionMillis = 250
    const val AccentMillis = 300
}

/** Brand-only accents; semantic UI colors are supplied through [WenYaoColorScheme]. */
internal val Ink = Color(0xFF1C1B1A)
internal val InkSoft = Color(0xFF686561)
internal val InkQuiet = Color(0xFF9E9A93)
internal val Paper = Color(0xFFFAF8F3)
internal val PaperQuiet = Color(0xFFF3EFE6)
internal val LineMist = Color(0xFFE2DDD4)
internal val LineQuiet = Color(0xFFECE7DC)
internal val Cinnabar = Color(0xFFAD483C)
internal val TeaGold = Color(0xFF9A7A42)
internal val DisabledPaper = Color(0xFFE8E5DF)
private val ErrorContainer = Color(0xFFFDF2F2)
private val ErrorInk = Color(0xFF601410)

internal val WenYaoColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = PaperQuiet,
    onPrimaryContainer = Ink,
    secondary = InkSoft,
    onSecondary = Color.White,
    secondaryContainer = PaperQuiet,
    onSecondaryContainer = Ink,
    tertiary = Cinnabar,
    onTertiary = Color.White,
    tertiaryContainer = PaperQuiet,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperQuiet,
    onSurfaceVariant = InkSoft,
    surfaceTint = Ink,
    surfaceBright = Paper,
    surfaceDim = PaperQuiet,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = PaperQuiet,
    surfaceContainer = PaperQuiet,
    surfaceContainerHigh = PaperQuiet,
    surfaceContainerHighest = PaperQuiet,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = PaperQuiet,
    scrim = Color.Black,
    outline = LineMist,
    outlineVariant = LineQuiet,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorInk,
)

internal val WenYaoTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

internal val WenYaoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun WenYaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WenYaoColorScheme,
        typography = WenYaoTypography,
        shapes = WenYaoShapes,
        content = content,
    )
}
