package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class LanguageOption(
    val code: String,
    val titleRes: Int
)

@Composable
fun LanguageScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val current by repository.appLanguageFlow.collectAsState(initial = "system")

    val options =
        remember {
            listOf(
                LanguageOption(code = "system", titleRes = R.string.language_option_system),
                LanguageOption(code = "en", titleRes = R.string.language_option_en),
                LanguageOption(code = "zh", titleRes = R.string.language_option_zh),
            )
        }
    val selectedCode =
        remember(current) {
            when (current.trim().lowercase()) {
                "en", "zh", "system" -> current.trim().lowercase()
                else -> "system"
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFFFFFFFF))
    ) {
        PageTopBar(
            title = stringResource(R.string.language_title),
            onBack = { navController.navigateUp() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 2.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsGroup(
                title = stringResource(R.string.settings_item_language),
                itemCount = options.size
            ) {
                options.forEachIndexed { index, option ->
                    LanguageOptionItem(
                        title = stringResource(option.titleRes),
                        selected = selectedCode == option.code,
                        showDivider = index != options.lastIndex,
                        onClick = {
                            scope.launch { repository.setAppLanguage(option.code) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionItem(
    title: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "language_item_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (isPressed) androidx.compose.ui.graphics.Color(0xFFE5E5EA) else androidx.compose.ui.graphics.Color(0xFFF1F1F1))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SourceSans3,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.alpha(if (selected) 1f else 0f)
            )
        }

        if (showDivider) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
