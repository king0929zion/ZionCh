package com.zionchat.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zionchat.app.ui.components.headerActionButtonShadow
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary

internal val ZiCodePageBackground = Color.White
internal val ZiCodePanelGray = Color(0xFFF1F1F1)
internal val ZiCodePanelPressedGray = Color(0xFFE8E8E8)
internal val ZiCodeDividerColor = Color(0xFFE2E2E2)
internal val ZiCodeSecondaryText = Color(0xFF6B6B6B)
internal val ZiCodeTertiaryText = Color(0xFF9A9A9A)
internal val ZiCodePanelRadius = 32.dp
internal val ZiCodeRowRadius = 24.dp

@Composable
internal fun ZiCodeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(start = 4.dp),
        color = ZiCodeSecondaryText,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = SourceSans3
    )
}

@Composable
internal fun ZiCodePanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ZiCodePanelRadius))
                .background(ZiCodePanelGray)
                .then(
                    if (onClick != null) {
                        Modifier.pressableScale(pressedScale = 0.985f, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        content = content
    )
}

@Composable
internal fun ZiCodeCircleButton(
    onClick: () -> Unit,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .headerActionButtonShadow(CircleShape)
            .background(Surface, CircleShape)
            .pressableScale(pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
internal fun ZiCodeMetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = ZiCodeSecondaryText,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFamily = SourceSans3
    )
}

@Composable
internal fun ZiCodeMiniStatusBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(TextPrimary)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SourceSans3
        )
    }
}

@Composable
internal fun ZiCodeRunningShimmer(
    modifier: Modifier = Modifier,
    visible: Boolean
) {
    if (!visible) return
    val transition = rememberInfiniteTransition(label = "zicode_shimmer")
    val translate =
        transition.animateFloat(
            initialValue = -320f,
            targetValue = 700f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
            label = "zicode_shimmer_translate"
        )

    Box(
        modifier = modifier.background(
            brush = Brush.horizontalGradient(
                colors = listOf(ZiCodePanelGray.copy(alpha = 0f), Color.White.copy(alpha = 0.55f), ZiCodePanelGray.copy(alpha = 0f)),
                startX = translate.value - 220f,
                endX = translate.value
            )
        )
    )
}

@Composable
internal fun ZiCodeBackIcon() {
    Icon(
        imageVector = AppIcons.Back,
        contentDescription = null,
        tint = TextPrimary,
        modifier = Modifier.size(20.dp)
    )
}
