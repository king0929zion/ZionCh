package com.zionchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.Switch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Suppress("UNUSED_PARAMETER")
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
    return this
        .background(overlayColor, shape)
        .clip(shape)
}

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = { onCheckedChange() },
        modifier = modifier
    )
}
