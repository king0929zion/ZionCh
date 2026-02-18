package com.zionchat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.DEFAULT_PROVIDER_PRESETS
import com.zionchat.app.data.findProviderPreset
import com.zionchat.app.data.resolveProviderIconAsset
import com.zionchat.app.ui.components.AssetIcon
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.liquidGlass
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

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
    val credentialPlaceholder = if (isGrok2ApiProvider) "Enter Grok token" else "Enter API key"
    val grokGatewayUrl = "http://10.0.2.2:8000/v1"
    val apiUrlPlaceholder = if (isGrok2ApiProvider) grokGatewayUrl else "https://api.example.com/v1"

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
                            it.apiUrl.contains("127.0.0.1", ignoreCase = true)
                    )
                ) {
                    grokGatewayUrl
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
        if (isGrok2ApiProvider) {
            val normalized = apiUrl.trim()
            val shouldNormalizeGateway =
                normalized.isBlank() ||
                    normalized.contains("api.x.ai", ignoreCase = true) ||
                    normalized.contains("localhost", ignoreCase = true) ||
                    normalized.contains("127.0.0.1", ignoreCase = true)
            if (shouldNormalizeGateway) {
                apiUrl = grokGatewayUrl
            }
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
                        apiKey = apiKey
                    )
                )
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = "Add provider",
            onBack = { navController.navigateUp() },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                                            headers = emptyList()
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
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar Selection
            Column(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GrayLighter, RoundedCornerShape(16.dp))
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
                                    imageVector = AppIcons.ChatGPTLogo,
                                    contentDescription = "Select Avatar",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.ChatGPTLogo,
                            contentDescription = "Select Avatar",
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tap to change avatar",
                    fontSize = 13.sp,
                    fontFamily = SourceSans3,
                    color = TextSecondary
                )
            }

            // Form Fields
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Provider Name
                FormField(
                    label = "Provider Name",
                    value = providerName,
                    onValueChange = { providerName = it },
                    placeholder = "Enter provider name"
                )

                if (!isGrok2ApiProvider) {
                    // Provider Type
                    Column {
                        Text(
                            text = "Provider Type",
                            fontSize = 13.sp,
                            fontFamily = SourceSans3,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val typeBackdrop = rememberLayerBackdrop()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .layerBackdrop(typeBackdrop)
                                    .background(Color.White, RoundedCornerShape(20.dp))
                                    .border(width = 1.dp, color = Color(0xFFE8E8ED), shape = RoundedCornerShape(20.dp))
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TypeOption(
                                        text = "OpenAI",
                                        selected = selectedType == "openai",
                                        onClick = { selectedType = "openai" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TypeOption(
                                        text = "Anthropic",
                                        selected = selectedType == "anthropic",
                                        onClick = { selectedType = "anthropic" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TypeOption(
                                        text = "Google",
                                        selected = selectedType == "google",
                                        onClick = { selectedType = "google" },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // API Key
                FormField(
                    label = credentialLabel,
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = credentialPlaceholder
                )

                AnimatedVisibility(
                    visible = !isGrok2ApiProvider,
                    enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 3 }),
                    exit = fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { it / 3 })
                ) {
                    FormField(
                        label = "API URL",
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        placeholder = apiUrlPlaceholder
                    )
                }

                // Models Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface, RoundedCornerShape(20.dp))
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
                                        apiKey = apiKey
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
            onDismiss = { showAvatarModal = false },
            onSelectAvatar = { avatar ->
                selectedIconAsset = avatar
                showAvatarModal = false
            }
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AvatarSelectionModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectAvatar: (String) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pressableScale(pressedScale = 1f, onClick = onDismiss)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        )
                        .pressableScale(pressedScale = 1f, onClick = { }),
                    color = Surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                    // Drag handle
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

                    // Avatar Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DEFAULT_PROVIDER_PRESETS
                            .filter { !it.iconAsset.isNullOrBlank() }
                            .chunked(5)
                            .forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowItems.forEach { presetItem ->
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(GrayLighter, RoundedCornerShape(14.dp))
                                                .pressableScale(pressedScale = 0.95f) {
                                                    onSelectAvatar(presetItem.iconAsset.orEmpty())
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AssetIcon(
                                                assetFileName = presetItem.iconAsset.orEmpty(),
                                                contentDescription = presetItem.name,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                            }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Import from Gallery
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
                            imageVector = AppIcons.ChatGPTLogo,
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

}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = SourceSans3,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                placeholder = {
                    Text(
                        text = placeholder,
                        fontSize = 17.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 17.sp,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                ),
                singleLine = true
            )
        }
    }
}

@Composable
fun TypeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) TextPrimary else Color(0xFFF5F5F7),
        animationSpec = tween(durationMillis = 180),
        label = "type_option_bg"
    )
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) Surface else TextPrimary,
        animationSpec = tween(durationMillis = 180),
        label = "type_option_text"
    )
    val pressedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1f else 0.998f,
        animationSpec = tween(durationMillis = 160),
        label = "type_option_scale"
    )
    Box(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .clip(shape)
            .then(
                when {
                    !selected -> Modifier.background(bgColor, shape)
                    backdrop != null -> Modifier.liquidGlass(
                        backdrop = backdrop,
                        shape = shape,
                        overlayColor = TextPrimary.copy(alpha = 0.88f),
                        fallbackColor = TextPrimary,
                        blurRadius = 16.dp,
                        refractionHeight = 4.dp,
                        refractionAmount = 8.dp,
                        highlightAlpha = 0.18f,
                        shadowAlpha = 0.06f
                    )
                    else -> Modifier.background(bgColor, shape)
                }
            )
            .pressableScale(pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SourceSans3,
            color = textColor
        )
    }
}
