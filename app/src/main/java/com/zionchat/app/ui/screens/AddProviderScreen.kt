package com.zionchat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.zionchat.app.R
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.DEFAULT_PROVIDER_PRESETS
import com.zionchat.app.data.findProviderPreset
import com.zionchat.app.data.resolveProviderIconAsset
import com.zionchat.app.ui.components.AssetIcon
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.headerActionButtonShadow
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
@Composable
fun AddProviderScreen(
    navController: NavController,
    preset: String? = null,
    providerId: String? = null
) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()

    var providerName by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiUrl by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("openai") }
    var showAvatarModal by remember { mutableStateOf(false) }
    var selectedIconAsset by remember { mutableStateOf("") }

    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val presetData = remember(preset) { findProviderPreset(preset) }
    val normalizedPresetId = remember(presetData?.id) { presetData?.id?.trim()?.lowercase()?.takeIf { it.isNotBlank() } }
    val activeProviderId = remember(providerId) { providerId?.takeIf { it.isNotBlank() } }
    val existingProvider = remember(providers, activeProviderId) {
        activeProviderId?.let { id -> providers.firstOrNull { it.id == id } }
    }
    val matchedPresetProvider = remember(providers, activeProviderId, normalizedPresetId) {
        if (activeProviderId != null || normalizedPresetId.isNullOrBlank()) {
            null
        } else {
            providers.firstOrNull { provider ->
                provider.presetId?.trim()?.lowercase() == normalizedPresetId
            }
        }
    }
    val editingProvider = existingProvider ?: matchedPresetProvider
    val editingProviderId = remember(activeProviderId, matchedPresetProvider?.id) {
        activeProviderId ?: matchedPresetProvider?.id ?: UUID.randomUUID().toString()
    }
    val normalizedType = selectedType.trim().lowercase()
    val normalizedEditingPresetId = remember(editingProvider?.presetId) {
        editingProvider?.presetId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    }
    val isGrok2ApiProvider = remember(normalizedType, normalizedPresetId, normalizedEditingPresetId) {
        normalizedType == "grok2api" ||
            normalizedType == "grok" ||
            normalizedPresetId == "grok2api" ||
            normalizedPresetId == "grok" ||
            normalizedEditingPresetId == "grok2api" ||
            normalizedEditingPresetId == "grok"
    }
    val credentialLabel = if (isGrok2ApiProvider) "Token" else "API Key"
    val grokDefaultApiUrl = "https://grok.com"
    val settingsGroupColor = Color(0xFFF1F1F1)

    LaunchedEffect(editingProvider?.id) {
        editingProvider?.let {
            providerName = it.name
            apiKey = it.apiKey
            val isEditingGrokProvider =
                it.type.equals("grok2api", ignoreCase = true) ||
                    it.type.equals("grok", ignoreCase = true) ||
                    it.presetId.equals("grok2api", ignoreCase = true) ||
                    it.presetId.equals("grok", ignoreCase = true)
            apiUrl =
                if (
                    isEditingGrokProvider &&
                    (
                        it.apiUrl.contains("api.x.ai", ignoreCase = true) ||
                            it.apiUrl.contains("localhost", ignoreCase = true) ||
                            it.apiUrl.contains("127.0.0.1", ignoreCase = true) ||
                            it.apiUrl.contains("10.0.2.2", ignoreCase = true) ||
                            it.apiUrl.contains("host.docker.internal", ignoreCase = true)
                    )
                ) {
                    grokDefaultApiUrl
                } else {
                    it.apiUrl
                }
            selectedType = it.type
            selectedIconAsset = resolveProviderIconAsset(it).orEmpty()
        }
    }

    LaunchedEffect(presetData?.id, editingProvider?.id) {
        if (editingProvider != null) return@LaunchedEffect
        presetData?.let {
            providerName = it.name
            apiUrl = it.apiUrl
            selectedType = it.type
            selectedIconAsset = it.iconAsset.orEmpty()
        }
    }

    LaunchedEffect(isGrok2ApiProvider) {
        if (isGrok2ApiProvider &&
            !selectedType.equals("grok2api", ignoreCase = true) &&
            !selectedType.equals("grok", ignoreCase = true)
        ) {
            selectedType = "grok2api"
        }
        if (isGrok2ApiProvider && apiUrl.trim().isBlank()) {
            apiUrl = grokDefaultApiUrl
        }
    }

    // Auto-save only when editing existing provider, not when creating new
    LaunchedEffect(editingProviderId, editingProvider?.presetId, presetData?.id) {
        if (editingProvider == null) return@LaunchedEffect // Don't auto-save for new providers

        snapshotFlow {
            listOf(
                providerName,
                apiKey,
                apiUrl,
                selectedType,
                selectedIconAsset
            )
        }
            .debounce(500)
            .distinctUntilChanged()
            .collect {
                val shouldSave = providerName.isNotBlank() || apiUrl.isNotBlank() || apiKey.isNotBlank()
                if (!shouldSave) return@collect

                repository.upsertProvider(
                    ProviderConfig(
                        id = editingProviderId,
                        presetId = editingProvider.presetId ?: presetData?.id,
                        iconAsset = selectedIconAsset.ifBlank { editingProvider.iconAsset ?: presetData?.iconAsset },
                        name = providerName,
                        type = selectedType,
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        headers = editingProvider.headers
                    )
                )
            }
    }

    SettingsPage(
        title = "Add provider",
        onBack = { navController.navigateUp() },
        trailing = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .headerActionButtonShadow(CircleShape)
                    .clip(CircleShape)
                    .background(Surface, CircleShape)
                    .pressableScale(pressedScale = 0.95f) {
                        if (providerName.isBlank() || apiUrl.isBlank()) return@pressableScale
                        scope.launch {
                            val normalizedIconAsset = selectedIconAsset.trim().takeIf { it.isNotBlank() }
                            repository.upsertProvider(
                                editingProvider?.let { existing ->
                                    existing.copy(
                                        name = providerName.trim(),
                                    type = selectedType.trim(),
                                    apiUrl = apiUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    iconAsset = normalizedIconAsset ?: existing.iconAsset,
                                    headers = existing.headers
                                )
                            } ?: ProviderConfig(
                                    id = editingProviderId,
                                    presetId = presetData?.id ?: preset?.trim()?.takeIf { it.isNotBlank() },
                                    iconAsset = normalizedIconAsset,
                                    name = providerName.trim(),
                                    type = selectedType.trim(),
                                    apiUrl = apiUrl.trim(),
                                    apiKey = apiKey.trim()
                                )
                            )
                            navController.navigateUp()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = "Save",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = settingsGroupColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.width(72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
                                    .pressableScale(pressedScale = 0.95f) { showAvatarModal = true },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedIconAsset.isNotEmpty()) {
                                    AssetIcon(
                                        assetFileName = selectedIconAsset,
                                        contentDescription = providerName.ifBlank { "Provider" },
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = {
                                            Icon(
                                                painter = rememberResourceDrawablePainter(R.drawable.ic_provider_custom_default),
                                                contentDescription = "Select Avatar",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    )
                                } else {
                                    Icon(
                                        painter = rememberResourceDrawablePainter(R.drawable.ic_provider_custom_default),
                                        contentDescription = "Select Avatar",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            FormField(
                                label = "Name",
                                value = providerName,
                                onValueChange = { providerName = it },
                                placeholder = "Name",
                                containerColor = settingsGroupColor
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = settingsGroupColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!isGrok2ApiProvider) {
                            Column {
                                Text(
                                    text = "Provider Type",
                                    fontSize = 13.sp,
                                    fontFamily = SourceSans3,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                AnimatedProviderTypeSelector(
                                    modifier = Modifier.fillMaxWidth(),
                                    selectedType = selectedType,
                                    onTypeSelected = { selectedType = it }
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = credentialLabel,
                            transitionSpec = {
                                (fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 5 })) togetherWith
                                    (fadeOut(tween(120)) + slideOutVertically(targetOffsetY = { -it / 5 }))
                            },
                            label = "credential_field_transition"
                        ) { credentialFieldLabel ->
                            FormField(
                                label = credentialFieldLabel,
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                placeholder = credentialFieldLabel,
                                containerColor = settingsGroupColor
                            )
                        }

                        AnimatedVisibility(
                            visible = !isGrok2ApiProvider,
                            enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 3 }),
                            exit = fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { it / 3 })
                        ) {
                            FormField(
                                label = "Base URL",
                                value = apiUrl,
                                onValueChange = { apiUrl = it },
                                placeholder = "Base URL",
                                containerColor = settingsGroupColor
                            )
                        }
                    }
                }

                // Models Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF1F1F1), RoundedCornerShape(20.dp))
                        .pressableScale(pressedScale = 0.98f) {
                            scope.launch {
                                repository.upsertProvider(
                                    ProviderConfig(
                                        id = editingProviderId,
                                        presetId = editingProvider?.presetId ?: presetData?.id,
                                        iconAsset = selectedIconAsset.ifBlank { editingProvider?.iconAsset ?: presetData?.iconAsset },
                                        name = providerName,
                                        type = selectedType,
                                        apiUrl = apiUrl,
                                        apiKey = apiKey,
                                        headers = editingProvider?.headers.orEmpty()
                                    )
                                )
                                navController.navigate("models?providerId=$editingProviderId")
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Models",
                            fontSize = 13.sp,
                            fontFamily = SourceSans3,
                            color = TextSecondary
                        )
                        Text(
                            text = "Configure models",
                            fontSize = 17.sp,
                            fontFamily = SourceSans3,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = "Navigate",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        AvatarSelectionModal(
            visible = showAvatarModal,
            selectedAvatarAsset = selectedIconAsset,
            onDismiss = { showAvatarModal = false },
            onSelectAvatar = { avatar ->
                selectedIconAsset = avatar
                showAvatarModal = false
            }
        )
    }
}

@Composable
fun AvatarSelectionModal(
    visible: Boolean,
    selectedAvatarAsset: String,
    onDismiss: () -> Unit,
    onSelectAvatar: (String) -> Unit
) {
    if (!visible) return

    val avatarItems = remember { DEFAULT_PROVIDER_PRESETS.filter { !it.iconAsset.isNullOrBlank() } }
    val avatarScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    ),
                color = Surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(GrayLight, RoundedCornerShape(2.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Select Avatar",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(avatarScrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        avatarItems.chunked(5).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                repeat(5) { index ->
                                    val presetItem = rowItems.getOrNull(index)
                                    if (presetItem == null) {
                                        Spacer(
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        val asset = presetItem.iconAsset.orEmpty()
                                        val selected = selectedAvatarAsset == asset
                                        Box(
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(
                                                        if (selected) Surface else GrayLighter,
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .border(
                                                        width = if (selected) 1.5.dp else 0.dp,
                                                        color = if (selected) TextPrimary else Color.Transparent,
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                    .pressableScale(pressedScale = 0.95f) {
                                                        onSelectAvatar(asset)
                                                    },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AssetIcon(
                                                assetFileName = asset,
                                                contentDescription = presetItem.name,
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(8.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GrayLighter
                        )
                    ) {
                        Icon(
                            imageVector = AppIcons.Image,
                            contentDescription = "Import",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import from Gallery",
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 16.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    containerColor: Color = Color(0xFFF1F1F1)
) {
    val shape = RoundedCornerShape(24.dp)
    val hasValue = value.trim().isNotEmpty()
    val fieldBorderColor = Color(0xFF5F616A)
    val fieldHintColor = Color(0xFF74747D)
    val fieldTransition = updateTransition(targetState = hasValue, label = "provider_field_transition")
    val labelAlpha by fieldTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 210, easing = FastOutSlowInEasing) },
        label = "provider_field_label_alpha"
    ) { filled ->
        if (filled) 1f else 0f
    }
    val labelScale by fieldTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 210, easing = FastOutSlowInEasing) },
        label = "provider_field_label_scale"
    ) { filled ->
        if (filled) 0.88f else 1f
    }
    val labelOffsetY by fieldTransition.animateDp(
        transitionSpec = { tween(durationMillis = 210, easing = FastOutSlowInEasing) },
        label = "provider_field_label_offset"
    ) { filled ->
        if (filled) (-9).dp else 18.dp
    }
    val placeholderAlpha by fieldTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 180, easing = FastOutSlowInEasing) },
        label = "provider_field_placeholder_alpha"
    ) { filled ->
        if (filled) 0f else 1f
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .border(width = 0.9.dp, color = fieldBorderColor, shape = shape),
            color = containerColor,
            shape = shape
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                placeholder = {
                    Text(
                        text = placeholder,
                        fontSize = 17.sp,
                        fontFamily = SourceSans3,
                        color = fieldHintColor,
                        modifier = Modifier.alpha(placeholderAlpha)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = TextPrimary
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 17.sp,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                ),
                singleLine = true
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 14.dp, y = labelOffsetY)
                .alpha(labelAlpha)
                .scale(labelScale)
                .background(containerColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = SourceSans3,
                color = fieldHintColor
            )
        }
    }
}

@Composable
private fun AnimatedProviderTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = remember { listOf("openai" to "OpenAI", "anthropic" to "Anthropic", "google" to "Google") }
    val selectedIndex = options.indexOfFirst { it.first == selectedType }.coerceAtLeast(0)
    val trackShape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = modifier
            .height(38.dp)
            .clip(trackShape)
            .background(Color.White, trackShape)
            .border(width = 1.5.dp, color = Color(0xFFA7A7AE), shape = trackShape)
    ) {
        val optionWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = optionWidth * selectedIndex,
            animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
            label = "provider_type_indicator_offset"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .fillMaxHeight()
                .width(optionWidth)
                .padding(2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(width = 1.4.dp, color = Color(0xFF666872), shape = RoundedCornerShape(14.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            options.forEach { (type, label) ->
                val selected = type == selectedType
                val textColor by animateColorAsState(
                    targetValue = if (selected) TextPrimary else TextSecondary,
                    animationSpec = tween(durationMillis = 180),
                    label = "provider_type_text_color"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressableScale(pressedScale = 0.96f) { onTypeSelected(type) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SourceSans3,
                        color = textColor
                    )
                }
            }
        }
    }
}
