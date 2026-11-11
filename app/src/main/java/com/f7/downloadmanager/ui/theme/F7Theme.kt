package com.f7.downloadmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.f7.downloadmanager.R

@Immutable
data class ThemePreset(
    val id: String,
    val labelRes: Int,
    val primary: Color,
    val accent: Color,
    val bg: Color,
    val bgAlt: Color,
    val swatch: Color,
)

val Presets = listOf(
    ThemePreset("JADE", R.string.theme_jade, Color(0xFF0C6B5A), Color(0xFFC9F26B), Color(0xFFF7F4ED), Color(0xFFEFEADF), Color(0xFF0C6B5A)),
    ThemePreset("COBALT", R.string.theme_cobalt, Color(0xFF2446D0), Color(0xFFFFD166), Color(0xFFF4F5FC), Color(0xFFEAECF8), Color(0xFF2446D0)),
    ThemePreset("TERRA", R.string.theme_coral, Color(0xFFB4502F), Color(0xFFF2C879), Color(0xFFFBF3ED), Color(0xFFF4E8DE), Color(0xFFB4502F)),
    ThemePreset("PLUM", R.string.theme_plum, Color(0xFF6B3F8C), Color(0xFFEBC77A), Color(0xFFF8F4FA), Color(0xFFF0E9F4), Color(0xFF6B3F8C)),
    ThemePreset("SAND", R.string.theme_sand, Color(0xFF7A5A2E), Color(0xFFE0A94F), Color(0xFFF8F4EA), Color(0xFFF1EAD9), Color(0xFF7A5A2E)),
    ThemePreset("GRAPHITE", R.string.theme_graphite, Color(0xFF23282C), Color(0xFF9BE3C2), Color(0xFFF5F5F2), Color(0xFFECECE7), Color(0xFF23282C)),
)

@Immutable
data class F7Skin(
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val cardAlt: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val track: Color,
    val success: Color,
    val danger: Color,
    val isDark: Boolean,
)

val LocalSkin = staticCompositionLocalOf {
    F7Skin(
        primary = Color(0xFF0C6B5A),
        onPrimary = Color.White,
        accent = Color(0xFFC9F26B),
        onAccent = Color(0xFF14180F),
        bgTop = Color(0xFFF7F4ED),
        bgBottom = Color(0xFFEFEADF),
        card = Color.White,
        cardAlt = Color(0xFFEFEADF),
        ink = Color(0xFF15181A),
        muted = Color(0xFF6E6B62),
        hairline = Color(0xFFE7E1D4),
        track = Color(0xFFEDE7DB),
        success = Color(0xFF1E8E6A),
        danger = Color(0xFFC24432),
        isDark = false,
    )
}

fun skinFor(preset: ThemePreset, dark: Boolean): F7Skin {
    if (!dark) {
        return F7Skin(
            primary = preset.primary,
            onPrimary = Color.White,
            accent = preset.accent,
            onAccent = Color(0xFF14180F),
            bgTop = preset.bg,
            bgBottom = preset.bgAlt,
            card = Color.White,
            cardAlt = preset.bgAlt,
            ink = Color(0xFF15181A),
            muted = Color(0xFF6E6B62),
            hairline = Color(0xFFE7E1D4),
            track = Color(0xFFEDE7DB),
            success = Color(0xFF1E8E6A),
            danger = Color(0xFFC24432),
            isDark = false,
        )
    }
    // Lift the accent only enough to read on a dark surface while keeping its hue identity.
    val lifted = lerp(preset.primary, Color.White, 0.32f)
    return F7Skin(
        primary = lifted,
        onPrimary = Color(0xFF06231E),
        accent = preset.accent,
        onAccent = Color(0xFF14180F),
        bgTop = Color(0xFF0F1312),
        bgBottom = Color(0xFF151A18),
        card = Color(0xFF1A201E),
        cardAlt = Color(0xFF202725),
        ink = Color(0xFFF1F4F0),
        muted = Color(0xFF9AA39D),
        hairline = Color(0xFF2A3230),
        track = Color(0xFF2A3230),
        success = Color(0xFF5BD6A8),
        danger = Color(0xFFF08272),
        isDark = true,
    )
}

/** Numerals in a fixed width so live speeds do not jitter while they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

object F7Motion {
    const val FAST = 120
    const val BASE = 200
    const val SLOW = 320

    val press = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)
    val standard = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
    val soft = spring<Float>(dampingRatio = 0.90f, stiffness = 220f)
    val fade = tween<Float>(durationMillis = BASE, easing = FastOutSlowInEasing)
    val progress = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
}

val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

val F7Typography = Typography(
    displayLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1.0).sp),
    headlineLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
)

@Composable
fun F7Theme(themeId: String, mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val preset = Presets.firstOrNull { it.id == themeId } ?: Presets.first()
    val skin = skinFor(preset, dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = skin.primary,
            onPrimary = skin.onPrimary,
            secondary = skin.accent,
            onSecondary = skin.onAccent,
            background = skin.bgTop,
            onBackground = skin.ink,
            surface = skin.card,
            onSurface = skin.ink,
            surfaceVariant = skin.cardAlt,
            onSurfaceVariant = skin.muted,
            outline = skin.hairline,
            error = skin.danger,
        )
    } else {
        lightColorScheme(
            primary = skin.primary,
            onPrimary = skin.onPrimary,
            secondary = skin.accent,
            onSecondary = skin.onAccent,
            background = skin.bgTop,
            onBackground = skin.ink,
            surface = skin.card,
            onSurface = skin.ink,
            surfaceVariant = skin.cardAlt,
            onSurfaceVariant = skin.muted,
            outline = skin.hairline,
            error = skin.danger,
        )
    }
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(colorScheme = scheme, typography = F7Typography, content = content)
    }
}
