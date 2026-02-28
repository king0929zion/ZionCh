package com.zionchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary

fun Modifier.headerActionButtonShadow(
    shape: Shape = CircleShape
): Modifier = this.shadow(
    elevation = 14.dp,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.2f),
    spotColor = Color.Black.copy(alpha = 0.14f)
)

@Composable
fun Modifier.settingsBottomInsets(): Modifier =
    this.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))

/**
 * iOS-style gradient fade top bar: solid background at top, fading to transparent at bottom.
 * Content can scroll underneath the transparent area for a modern blur-like effect.
 */
@Composable
fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Surface,
    gradientFade: Boolean = false,
    fadeHeight: Dp = 20.dp,
    trailing: (@Composable () -> Unit)? = null
) {
    if (gradientFade) {
        // iOS-style: solid bar + gradient fade-out zone below
        Column(modifier = modifier.fillMaxWidth()) {
            // Solid background area with status bar insets + content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(containerColor)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .headerActionButtonShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = onBack)
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SourceSans3,
                    color = TextPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )

                if (trailing != null) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        trailing()
                    }
                }
            }
            // Gradient fade-out zone: containerColor -> transparent
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(containerColor, containerColor.copy(alpha = 0f))
                        )
                    )
            )
        }
    } else {
        // Legacy liquid glass style
        val topBarBackdrop = rememberLayerBackdrop()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .layerBackdrop(topBarBackdrop)
                .liquidGlass(
                    backdrop = topBarBackdrop,
                    shape = RoundedCornerShape(0.dp),
                    overlayColor = containerColor.copy(alpha = 0.6f),
                    fallbackColor = Surface.copy(alpha = 0.68f),
                    blurRadius = 24.dp,
                    refractionHeight = 4.dp,
                    refractionAmount = 8.dp,
                    highlightAlpha = 0.18f,
                    shadowAlpha = 0f
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .headerActionButtonShadow(CircleShape)
                    .clip(CircleShape)
                    .background(Surface, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onBack)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SourceSans3,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )

            if (trailing != null) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    trailing()
                }
            }
        }
    }
}
