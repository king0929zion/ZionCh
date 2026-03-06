package com.zionchat.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zionchat.app.ui.components.headerActionButtonShadow
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary

internal val ZiCodePageBackground = Color(0xFFFFFFFF)
internal val ZiCodePanelGray = Color(0xFFF1F1F1)
internal val ZiCodePanelPressedGray = Color(0xFFE5E5E5)
internal val ZiCodeHairline = Color(0xFFFFFFFF)
internal val ZiCodeSecondaryText = Color(0xFF6B6B6B)
internal val ZiCodeTertiaryText = Color(0xFF8A8A8A)
internal val ZiCodePanelRadius = 26.dp
internal val ZiCodeInnerRadius = 20.dp

@Composable
internal fun ZiCodeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(start = 12.dp),
        color = ZiCodeSecondaryText,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = SourceSans3
    )
}

@Composable
internal fun ZiCodePanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ZiCodePanelRadius),
        colors = CardDefaults.cardColors(containerColor = ZiCodePageBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZiCodePageBackground)
                .padding(2.dp),
            content = content
        )
    }
}

@Composable
internal fun ZiCodeCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .headerActionButtonShadow(CircleShape)
            .background(Surface, CircleShape)
            .pressableScale(pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
internal fun ZiCodeChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val background = if (selected) ZiCodePanelPressedGray else ZiCodePanelGray
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .background(background, RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) {
                    Modifier.pressableScale(pressedScale = 0.97f, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontFamily = SourceSans3
        )
    }
}

@Composable
internal fun ZiCodeMetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = ZiCodeSecondaryText,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFamily = SourceSans3
    )
}

@Composable
internal fun ZiCodeMiniStatusBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ZiCodePanelGray, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = ZiCodeSecondaryText,
            fontSize = 12.sp,
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
            targetValue = 640f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "zicode_shimmer_translate"
        )

    Box(
        modifier = modifier.background(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    ZiCodePanelGray.copy(alpha = 0f),
                    Color.White.copy(alpha = 0.55f),
                    ZiCodePanelGray.copy(alpha = 0f)
                ),
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
