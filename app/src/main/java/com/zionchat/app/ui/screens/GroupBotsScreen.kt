package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.BotConfig
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.GrayLighter
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary

@Composable
fun GroupBotsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val bots by repository.botsFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())

    val modelNameById = remember(models) { models.associateBy({ it.id }, { it.displayName }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = stringResource(R.string.group_bots_title),
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(
                            pressedScale = 0.95f,
                            onClick = { navController.navigate("add_bot") }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Plus,
                        contentDescription = stringResource(R.string.group_bot_add_title),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bots.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.group_bots_empty_hint),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 40.dp)
                    )
                }
            } else {
                bots.forEach { bot ->
                    BotCard(
                        bot = bot,
                        modelDisplay = resolveBotModelName(bot, models, modelNameById),
                        onClick = {
                            navController.navigate("edit_bot/${bot.id}")
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private fun resolveBotModelName(
    bot: BotConfig,
    models: List<ModelConfig>,
    modelNameById: Map<String, String>
): String? {
    val key = bot.defaultModelId?.trim().orEmpty()
    if (key.isBlank()) return null
    return modelNameById[key]
        ?: models.firstOrNull { extractRemoteModelId(it.id) == key }?.displayName
        ?: key
}

@Composable
private fun BotCard(
    bot: BotConfig,
    modelDisplay: String?,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(GrayLighter),
            contentAlignment = Alignment.Center
        ) {
            when {
                bot.avatarUri?.isNotBlank() == true -> {
                    AsyncImage(
                        model = bot.avatarUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                bot.avatarAssetName?.isNotBlank() == true -> {
                    AsyncImage(
                        model = "file:///android_asset/avatars/${bot.avatarAssetName}",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                else -> {
                    Icon(
                        imageVector = AppIcons.Bot,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = bot.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!modelDisplay.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.group_bot_model_value, modelDisplay),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}
