package com.lagradost.cloudstream3.ui.tv.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvColors
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvMotion

/** Adds the visible focus affordance shared by all D-pad targets in the TV shell. */
fun Modifier.tvFocusRing(
    enabled: Boolean = true,
    focusedColor: Color? = null,
): Modifier = composed {
    val focused = remember { mutableStateOf(false) }
    val motion = LocalTvMotion.current
    val colors = LocalTvColors.current
    val scale by animateFloatAsState(
        targetValue = if (focused.value && enabled) motion.focusScale else 1f,
        animationSpec = tween(if (motion.reducedMotion) 0 else 140),
        label = "tv-focus-scale",
    )
    val ringColor = focusedColor ?: colors.accent

    this
        .onFocusChanged { focused.value = it.isFocused }
        .scale(scale)
        .border(
            width = if (focused.value && enabled) 2.dp else 0.dp,
            color = if (focused.value && enabled) ringColor else Color.Transparent,
            shape = RoundedCornerShape(10.dp),
        )
        .focusable(enabled = enabled)
}

/** Makes a composable both keyboard accessible and semantically identifiable as a button. */
fun Modifier.tvFocusable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    tvFocusRing(enabled)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        .semantics { role = Role.Button }
}
