package com.zionchat.app.ui.components.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CornerRadius
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
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

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { (trackWidth - knobSize - 4.dp).toPx().coerceAtLeast(1f) }
    val knobSizePx = with(density) { knobSize.toPx() }
    val animationScope = rememberCoroutineScope()

    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val dampedDragAnimation =
        remember(animationScope, dragWidth) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = fraction,
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.28f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        fraction = if (targetValue >= 0.5f) 1f else 0f
                        didDrag = false
                        val next = fraction == 1f
                        if (next != checked) onCheckedChange(next)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = dragAmount.x / dragWidth
                    fraction =
                        if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                        else (fraction - delta).fastCoerceIn(0f, 1f)
                }
            )
        }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { latest ->
            dampedDragAnimation.updateValue(latest)
        }
    }

    LaunchedEffect(checked) {
        snapshotFlow { checked }.collectLatest { isChecked ->
            val target = if (isChecked) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(trackShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(inactiveTrack, lerpColor(inactiveTrack, activeTrack, dampedDragAnimation.value))
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
                    val p = dampedDragAnimation.value
                    val padding = 2.dp.toPx()
                    val r = (knobSizePx / 2f).coerceAtMost(size.height / 2f - padding)
                    val minCenter = padding + r
                    val maxCenter = size.width - padding - r
                    val cx = lerp(minCenter, maxCenter, p)
                    val centerBias = 1f - abs(p - 0.5f) * 2f
                    val bridgeW = r * (0.8f + 0.9f * centerBias)
                    val bridgeH = size.height * (0.42f + 0.18f * centerBias)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f + 0.12f * centerBias),
                        topLeft = Offset(cx - bridgeW / 2f, (size.height - bridgeH) / 2f),
                        size = Size(bridgeW, bridgeH),
                        cornerRadius = CornerRadius(bridgeH / 2f, bridgeH / 2f)
                    )
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (checked) 0.16f else 0.24f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .graphicsLayer {
                    val p = dampedDragAnimation.value
                    translationX = if (isLtr) lerp(0f, dragWidth, p) else lerp(0f, -dragWidth, p)
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val v = (dampedDragAnimation.velocity / 40f).fastCoerceIn(-0.12f, 0.12f)
                    scaleX *= (1f + v)
                    scaleY *= (1f - v * 0.8f)
                }
                .then(dampedDragAnimation.modifier)
                .size(knobSize)
                .shadow(
                    elevation = 8.dp,
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
                    .padding(start = 5.dp, top = 4.dp)
                    .size(width = 10.dp, height = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.56f))
            )
        }
    }
}
