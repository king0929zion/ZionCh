package com.zionchat.app.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary

private fun supportsLiquidGlassBlur(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (Build.VERSION.SDK_INT >= 36) return false

    val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
    val brand = Build.BRAND.orEmpty().lowercase()
    if (manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo")) return false
    return true
}

fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    overlayColor: Color,
    fallbackColor: Color = overlayColor.copy(alpha = 1f),
    blurRadius: Dp = 22.dp,
    refractionHeight: Dp = 5.dp,
    refractionAmount: Dp = 10.dp,
    highlightAlpha: Float = 0.35f,
    shadowAlpha: Float = 0.10f
): Modifier {
    val supportsBlur = supportsLiquidGlassBlur()
    if (!supportsBlur) {
        return this
            .background(fallbackColor, shape)
            .clip(shape)
    }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(blurRadius.toPx())
            lens(
                refractionHeight = refractionHeight.toPx(),
                refractionAmount = refractionAmount.toPx(),
                chromaticAberration = true
            )
        },
        highlight = { Highlight.Ambient.copy(alpha = highlightAlpha) },
        shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = shadowAlpha)) },
        onDrawSurface = { drawRect(overlayColor) }
    )
}

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackShape = RoundedCornerShape(14.dp)
    val trackBackdrop = rememberLayerBackdrop()
    val progress = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "liquid_switch_progress"
    ).value
    val trackColor = animateColorAsState(
        targetValue = if (checked) TextPrimary else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "liquid_switch_track"
    ).value
    val borderColor = animateColorAsState(
        targetValue = if (checked) TextPrimary else TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "liquid_switch_border"
    ).value
    val knobOverlay = lerp(TextSecondary.copy(alpha = 0.32f), Surface.copy(alpha = 0.92f), progress)
    val knobFallback = lerp(TextSecondary, Surface, progress)
    val knobOffset = 22.dp * progress

    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .semantics { role = Role.Switch }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCheckedChange
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop)
                .clip(trackShape)
                .background(trackColor, trackShape)
                .border(
                    width = 1.5.dp,
                    color = borderColor,
                    shape = trackShape
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .offset(x = knobOffset)
                .size(22.dp)
                .liquidGlass(
                    backdrop = trackBackdrop,
                    shape = CircleShape,
                    overlayColor = knobOverlay,
                    fallbackColor = knobFallback,
                    blurRadius = 12.dp,
                    refractionHeight = 4.dp,
                    refractionAmount = 8.dp,
                    highlightAlpha = 0.22f,
                    shadowAlpha = 0.06f
                )
        )
    }
}
