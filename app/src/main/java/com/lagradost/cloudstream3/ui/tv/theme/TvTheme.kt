package com.lagradost.cloudstream3.ui.tv.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

@Immutable
data class TvColors(
    val background: Color = Color(0xFF090B10),
    val surface: Color = Color(0xFF141923),
    val surfaceRaised: Color = Color(0xFF202838),
    val accent: Color = Color(0xFF8BD5FF),
    val accentStrong: Color = Color(0xFF45B7F4),
    val textPrimary: Color = Color(0xFFF4F7FB),
    val textSecondary: Color = Color(0xFFAAB5C5),
    val textDisabled: Color = Color(0xFF657082),
    val error: Color = Color(0xFFFF8D8D),
)

@Immutable
data class TvMotion(
    val reducedMotion: Boolean = false,
    val focusScale: Float = 1.04f,
)

object TvSpacing {
    val screen = 48.dp
    val section = 32.dp
    val card = 12.dp
    val compact = 8.dp
    val sidebar = 88.dp
    val sidebarExpanded = 224.dp
}

fun isReducedMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)

object TvShapes {
    val card = 14.dp
    val button = 10.dp
    val hero = 24.dp
}

val LocalTvColors: ProvidableCompositionLocal<TvColors> = compositionLocalOf { TvColors() }
val LocalTvMotion: ProvidableCompositionLocal<TvMotion> = compositionLocalOf { TvMotion() }

@Composable
fun TvTheme(
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTvColors provides TvColors(),
        LocalTvMotion provides TvMotion(reducedMotion = reducedMotion),
    ) {
        MaterialTheme {
            content()
        }
    }
}

val Dp.tvCardRadius: Dp
    get() = TvShapes.card
