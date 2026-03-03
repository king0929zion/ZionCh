package com.zionchat.app.ui.components.liquid

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs

@Composable
fun BackdropLiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 52.dp,
    trackHeight: Dp = 30.dp,
    knobSize: Dp = 26.dp
) {
    val isLightTheme = !isSystemInDarkTheme()
    val activeTrack = if (isLightTheme) Color(0xFF1A1A1E) else Color(0xFF2C2C31)
    val inactiveTrack = if (isLightTheme) Color(0xFFD1D1D8) else Color(0xFF6A6A70).copy(alpha = 0.45f)
    val trackBorder = if (isLightTheme) Color(0xFFB9BAC3) else Color(0xFF4C4C52)
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "liquid_toggle_progress"
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) (trackWidth - knobSize - 2.dp).coerceAtLeast(2.dp) else 2.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "liquid_toggle_knob_offset"
    )

    val trackShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(trackShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(inactiveTrack, lerpColor(inactiveTrack, activeTrack, progress))
                ),
                trackShape
            )
            .border(1.dp, trackBorder.copy(alpha = 0.9f), trackShape)
            .semantics { role = Role.Switch }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val padding = 2.dp.toPx()
                    val radius = (knobSize.toPx() / 2f).coerceAtMost(size.height / 2f - padding)
                    val minCenter = padding + radius
                    val maxCenter = size.width - padding - radius
                    val centerX = lerp(minCenter, maxCenter, progress)
                    val centerBias = 1f - abs(progress - 0.5f) * 2f
                    val bridgeWidth = radius * (0.8f + 0.9f * centerBias)
                    val bridgeHeight = size.height * (0.42f + 0.18f * centerBias)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f + 0.16f * centerBias),
                        topLeft = Offset(centerX - bridgeWidth / 2f, (size.height - bridgeHeight) / 2f),
                        size = Size(bridgeWidth, bridgeHeight),
                        cornerRadius = CornerRadius(bridgeHeight / 2f, bridgeHeight / 2f)
                    )
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (checked) 0.14f else 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            Modifier
                .offset(x = knobOffset)
                .size(knobSize)
                .shadow(
                    elevation = 7.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.98f),
                            Color(0xFFF4F4F8),
                            Color(0xFFE7E8EE)
                        )
                    ),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.82f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 5.dp, y = 4.dp)
                    .size(width = 10.dp, height = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.56f))
            )
        }
    }
}
