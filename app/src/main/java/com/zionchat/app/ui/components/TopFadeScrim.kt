package com.zionchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TopFadeScrim(
    color: Color,
    height: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to color.copy(alpha = 0.98f),
                            0.28f to color.copy(alpha = 0.72f),
                            0.64f to color.copy(alpha = 0.24f),
                            1f to color.copy(alpha = 0f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to color.copy(alpha = 0.54f),
                            0.42f to color.copy(alpha = 0.18f),
                            1f to color.copy(alpha = 0f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.46f)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to color.copy(alpha = 0.36f),
                            0.58f to color.copy(alpha = 0.08f),
                            1f to color.copy(alpha = 0f)
                        )
                    )
                )
        )
    }
}
