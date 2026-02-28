package com.zionchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary

/**
 * Top-bar content area padding: status bar + 16dp vertical padding + 40dp icon row = ~72dp,
 * used by content to leave space for the floating header.
 */
val PageTopBarContentTopPadding: Dp = 72.dp

fun Modifier.headerActionButtonShadow(
    shape: Shape = CircleShape
): Modifier = this.shadow(
    elevation = 20.dp,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.25f),
    spotColor = Color.Black.copy(alpha = 0.18f)
)

@Composable
fun Modifier.settingsBottomInsets(): Modifier =
    this.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))

/**
 * iOS-style translucent top bar with a smooth full-height gradient.
 * The background transitions gradually from opaque at the very top
 * to fully transparent at the bottom — no hard edge.
 */
@Composable
fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFFFFFFFF),
    containerAlpha: Float = 0.92f,
    fadeHeight: Dp = 32.dp,
    trailing: (@Composable () -> Unit)? = null
) {
    val topColor = containerColor.copy(alpha = containerAlpha)
    val midColor = containerColor.copy(alpha = containerAlpha * 0.55f)

    Box(modifier = modifier.fillMaxWidth()) {
        // Gradient background that covers the entire header height
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to topColor,
                        0.55f to topColor,
                        0.78f to midColor,
                        1.0f to Color.Transparent
                    )
                )
        )

        // Foreground content
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
            // Extra space for gradient tail to breathe
            Spacer(modifier = Modifier.height(fadeHeight))
        }
    }
}

/**
 * Reusable page layout with iOS-style translucent floating header.
 * Content is rendered full-screen behind the header; the header overlays on top.
 *
 * @param title       Header title text
 * @param onBack      Back button action
 * @param trailing    Optional trailing composable for the header (e.g. action buttons)
 * @param content     Page content, rendered inside a Box that fills the screen.
 *                    Content should add `windowInsetsPadding(WindowInsets.statusBars)`
 *                    and `padding(top = PageTopBarContentTopPadding)` to leave space
 *                    for the floating header.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
    ) {
        // Content layer (behind header)
        content()

        // Floating translucent header (on top)
        PageTopBar(
            title = title,
            onBack = onBack,
            trailing = trailing
        )
    }
}
