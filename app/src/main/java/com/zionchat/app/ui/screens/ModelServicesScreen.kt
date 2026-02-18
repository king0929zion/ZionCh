package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.R
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.DEFAULT_PROVIDER_PRESETS
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.ProviderPreset
import com.zionchat.app.data.isGrok2ApiProvider
import com.zionchat.app.data.resolveProviderIconAsset
import com.zionchat.app.ui.components.AssetIcon
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ModelServicesScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()

    val configuredProviders by repository.providersFlow.collectAsState(initial = emptyList())
    val oauthPresetIds = remember { setOf("codex", "iflow", "qwen_code") }
    val builtInPresetIdSet = remember {
        DEFAULT_PROVIDER_PRESETS.map { it.id.trim().lowercase() }.toSet()
    }
    val configuredProvidersForDisplay = remember(configuredProviders, builtInPresetIdSet) {
        val seenBuiltInPresetIds = mutableSetOf<String>()
        configuredProviders.filter { provider ->
            val presetId = provider.presetId?.trim()?.lowercase().orEmpty()
            if (presetId.isBlank() || !builtInPresetIdSet.contains(presetId)) {
                true
            } else {
                seenBuiltInPresetIds.add(presetId)
            }
        }
    }
    val configuredBuiltInPresetIds = remember(configuredProvidersForDisplay, builtInPresetIdSet) {
        configuredProvidersForDisplay
            .mapNotNull { provider ->
                provider.presetId?.trim()?.lowercase()?.takeIf { builtInPresetIdSet.contains(it) }
            }
            .toSet()
    }
    val availablePresetProviders = remember(configuredBuiltInPresetIds) {
        DEFAULT_PROVIDER_PRESETS.filterNot { preset ->
            configuredBuiltInPresetIds.contains(preset.id.trim().lowercase())
        }
    }
    var openedProviderId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = stringResource(R.string.model_services),
            onBack = { navController.navigateUp() },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f) {
                            navController.navigate("add_provider?preset=&providerId=")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Plus,
                        contentDescription = stringResource(R.string.add_provider),
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            configuredProvidersForDisplay.forEach { provider ->
                SwipeableConfiguredProviderItem(
                    provider = provider,
                    iconAsset = resolveProviderIconAsset(provider),
                    tokenBadge = provider.isGrok2ApiProvider(),
                    isOpened = openedProviderId == provider.id,
                    onOpenChanged = { isOpen ->
                        openedProviderId =
                            if (isOpen) {
                                provider.id
                            } else if (openedProviderId == provider.id) {
                                null
                            } else {
                                openedProviderId
                            }
                    },
                    onClick = {
                        val oauthProvider = provider.oauthProvider?.trim()?.lowercase().orEmpty()
                        if (oauthProvider.isNotBlank()) {
                            navController.navigate("add_oauth_provider?provider=$oauthProvider&providerId=${provider.id}")
                        } else {
                            navController.navigate("add_provider?preset=&providerId=${provider.id}")
                        }
                    },
                    onDelete = {
                        openedProviderId = null
                        scope.launch { repository.deleteProviderAndModels(provider.id) }
                    }
                )
            }

            if (configuredProvidersForDisplay.isNotEmpty() && availablePresetProviders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = GrayLight, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
            }

            availablePresetProviders.forEach { provider ->
                ProviderItem(
                    provider = provider,
                    oauthBadge = oauthPresetIds.contains(provider.id),
                    tokenBadge = provider.id.trim().equals("grok2api", ignoreCase = true) || provider.id.trim().equals("grok", ignoreCase = true),
                    onClick = {
                        if (oauthPresetIds.contains(provider.id)) {
                            navController.navigate("add_oauth_provider?provider=${provider.id}&providerId=")
                        } else {
                            navController.navigate("add_provider?preset=${provider.id}&providerId=")
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderItem(
    provider: ProviderPreset,
    oauthBadge: Boolean = false,
    tokenBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Surface, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProviderIcon(
            iconAsset = provider.iconAsset,
            contentDescription = provider.name,
            modifier = Modifier.size(28.dp)
        )

        Text(
            text = provider.name,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = SourceSans3,
            color = TextPrimary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tokenBadge) TokenBadge()
            if (oauthBadge) OAuthBadge()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun SwipeableConfiguredProviderItem(
    provider: ProviderConfig,
    iconAsset: String?,
    tokenBadge: Boolean,
    isOpened: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val itemScope = rememberCoroutineScope()
    val actionWidth = 72.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val swipeableState = rememberSwipeableState(
        initialValue = if (isOpened) 1 else 0,
        confirmStateChange = { targetValue ->
            onOpenChanged(targetValue != 0)
            true
        }
    )
    val anchors = remember(actionWidthPx) { mapOf(0f to 0, -actionWidthPx to 1) }

    LaunchedEffect(isOpened) {
        val target = if (isOpened) 1 else 0
        if (swipeableState.currentValue != target) {
            swipeableState.animateTo(target)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(actionWidth)
                .align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onOpenChanged(false)
                        onDelete()
                        itemScope.launch { swipeableState.animateTo(0) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Trash,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
                .background(Surface, RoundedCornerShape(14.dp))
                .swipeable(
                    state = swipeableState,
                    anchors = anchors,
                    thresholds = { _, _ -> FractionalThreshold(0.3f) },
                    orientation = Orientation.Horizontal
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (swipeableState.currentValue != 0) {
                        itemScope.launch { swipeableState.animateTo(0) }
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProviderIcon(
                iconAsset = iconAsset,
                contentDescription = provider.name,
                modifier = Modifier.size(28.dp)
            )

            Text(
                text = provider.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SourceSans3,
                color = TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tokenBadge) {
                    TokenBadge()
                }
                if (!provider.oauthProvider.isNullOrBlank()) {
                    OAuthBadge()
                }
            }
        }
    }
}

@Composable
private fun OAuthBadge() {
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(TextPrimary, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.oauth_badge),
            fontSize = 12.sp,
            fontFamily = SourceSans3,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun TokenBadge() {
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(Color(0xFFF3F4F6), RoundedCornerShape(11.dp))
            .border(width = 1.dp, color = Color(0xFF111111), shape = RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Token",
            fontSize = 12.sp,
            fontFamily = SourceSans3,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF111111),
            maxLines = 1
        )
    }
}

@Composable
private fun ProviderIcon(
    iconAsset: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (!iconAsset.isNullOrBlank()) {
        AssetIcon(
            assetFileName = iconAsset,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ChatGPTLogo,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
        return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.ChatGPTLogo,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
