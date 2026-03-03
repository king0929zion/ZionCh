package com.zionchat.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.zionchat.app.R
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalChatApiClient
import com.zionchat.app.LocalOAuthClient
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.OAuthClient
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.buildModelStorageId
import com.zionchat.app.data.findProviderPreset
import com.zionchat.app.ui.components.AssetIcon
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.headerActionButtonShadow
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.components.pressableScale
import kotlinx.coroutines.launch

// 页面状态
private enum class OAuthStep {
    STEP_1_CONNECT,
    STEP_2_CALLBACK,
    STEP_3_COMPLETED
}

private val SupplierCardGray = Color(0xFFF1F1F1)
private val SupplierFieldBorder = Color(0xFF44464D)
private val SupplierHintColor = Color(0xFF74747D)

@Composable
fun AddOAuthProviderScreen(
    navController: NavController,
    initialProvider: String? = null,
    providerId: String? = null
) {
    val repository = LocalAppRepository.current
    val chatApiClient = LocalChatApiClient.current
    val oauthClient = LocalOAuthClient.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lockedProviderId = remember(initialProvider) {
        initialProvider?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    }
    val existingProviderId = remember(providerId) { providerId?.trim()?.takeIf { it.isNotBlank() } }
    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val matchedBuiltInProvider = remember(providers, existingProviderId, lockedProviderId) {
        if (!existingProviderId.isNullOrBlank() || lockedProviderId.isNullOrBlank()) {
            null
        } else {
            providers.firstOrNull { provider ->
                provider.presetId?.trim()?.equals(lockedProviderId, ignoreCase = true) == true ||
                    provider.oauthProvider?.trim()?.equals(lockedProviderId, ignoreCase = true) == true
            }
        }
    }
    val existingProvider = remember(providers, existingProviderId, matchedBuiltInProvider?.id) {
        existingProviderId?.let { id -> providers.firstOrNull { it.id == id } } ?: matchedBuiltInProvider
    }

    var providerName by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(OAuthStep.STEP_1_CONNECT) }
    var callbackUrl by remember { mutableStateOf("") }
    var showAvatarModal by remember { mutableStateOf(false) }
    var selectedAvatar by remember(lockedProviderId) {
        mutableStateOf(
            when (lockedProviderId?.trim()?.lowercase()) {
                "qwen_code", "qwen-code" -> "qwen"
                null, "" -> "codex"
                else -> lockedProviderId
            }
        )
    }
    var isWorking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var oauthStart by remember { mutableStateOf<OAuthClient.OAuthStartResult?>(null) }
    var connectedProvider by remember { mutableStateOf<ProviderConfig?>(null) }

    LaunchedEffect(existingProvider?.id) {
        val providerConfig = existingProvider ?: return@LaunchedEffect
        if (connectedProvider?.id == providerConfig.id && currentStep == OAuthStep.STEP_3_COMPLETED) return@LaunchedEffect

        connectedProvider = providerConfig
        providerName = providerConfig.name
        providerConfig.oauthProvider?.trim()?.takeIf { it.isNotBlank() }?.let { selectedAvatar = it }
        oauthStart = null
        callbackUrl = ""
        currentStep = OAuthStep.STEP_3_COMPLETED
        errorText = null
    }

    fun resolvedOauthProvider(): OAuthClient.OAuthProvider? {
        return when (selectedAvatar.lowercase()) {
            "codex" -> OAuthClient.OAuthProvider.Codex
            "iflow" -> OAuthClient.OAuthProvider.IFlow
            "qwen", "qwen_code", "qwen-code" -> OAuthClient.OAuthProvider.QwenCode
            else -> null
        }
    }

    LaunchedEffect(selectedAvatar) {
        if (providerName.isNotBlank()) return@LaunchedEffect
        providerName =
            when (resolvedOauthProvider()) {
                OAuthClient.OAuthProvider.Codex -> "Codex"
                OAuthClient.OAuthProvider.IFlow -> "iFlow"
                OAuthClient.OAuthProvider.QwenCode -> "Qwen Code"
                else -> ""
            }
    }
    val selectedAvatarAsset = remember(selectedAvatar) { findProviderPreset(selectedAvatar)?.iconAsset.orEmpty() }

    fun handleSave() {
        if (isWorking) return
        errorText = null
        val provider = connectedProvider
        if (providerName.isBlank()) {
            errorText = context.getString(R.string.error_provider_name_required)
            return
        }
        if (currentStep != OAuthStep.STEP_3_COMPLETED || provider == null) {
            errorText = context.getString(R.string.error_oauth_complete_first)
            return
        }
        scope.launch {
            val trimmedName = providerName.trim()
            if (trimmedName.isNotBlank() && provider.name != trimmedName) {
                val updated = provider.copy(name = trimmedName)
                repository.upsertProvider(updated)
                connectedProvider = updated
            }
            navController.navigateUp()
        }
    }

    SettingsPage(
        title = stringResource(R.string.add_provider),
        onBack = { navController.navigateUp() },
        trailing = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .headerActionButtonShadow(CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pressableScale(pressedScale = 0.95f, onClick = ::handleSave),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = "Save",
                    tint = Color(0xFF1C1C1E),
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
                .padding(top = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SupplierCardGray,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
                                .pressableScale(
                                    pressedScale = if (lockedProviderId == null) 0.95f else 1f,
                                    onClick = { if (lockedProviderId == null) showAvatarModal = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedAvatarAsset.isNotBlank()) {
                                AssetIcon(
                                    assetFileName = selectedAvatarAsset,
                                    contentDescription = providerName.ifBlank { selectedAvatar },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.ChatGPTLogo,
                                    contentDescription = null,
                                    tint = Color(0xFF1C1C1E),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            ProviderNameInput(
                                value = providerName,
                                onValueChange = { providerName = it },
                                embedded = true,
                                inRow = true
                            )
                        }
                    }
                }

                // Provider Name 输入框
                // OAuth 认证区域
                OAuthSection(
                    currentStep = currentStep,
                    callbackUrl = callbackUrl,
                    showCallbackInput = oauthStart?.provider != OAuthClient.OAuthProvider.QwenCode,
                    onCallbackUrlChange = { callbackUrl = it },
                    onStartOAuth = {
                        if (isWorking) return@OAuthSection
                        errorText = null
                        val provider = resolvedOauthProvider()
                        if (provider == null) {
                            errorText = context.getString(R.string.error_select_oauth_provider)
                            return@OAuthSection
                        }
                        isWorking = true
                        scope.launch {
                            val startResult: Result<OAuthClient.OAuthStartResult> =
                                when (provider) {
                                    OAuthClient.OAuthProvider.Codex -> Result.success(oauthClient.startCodexOAuth())
                                    OAuthClient.OAuthProvider.IFlow -> Result.success(oauthClient.startIFlowOAuth())
                                    OAuthClient.OAuthProvider.QwenCode -> oauthClient.startQwenOAuth()
                                }
                            startResult.onSuccess { start ->
                                oauthStart = start
                                callbackUrl = ""
                                currentStep = OAuthStep.STEP_2_CALLBACK
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(start.authUrl)))
                                }.onFailure { throwable ->
                                    errorText = context.getString(
                                        R.string.error_open_browser_with_reason,
                                        throwable.message ?: throwable.toString()
                                    )
                                }
                            }.onFailure { throwable ->
                                errorText = throwable.message ?: throwable.toString()
                            }
                            isWorking = false
                        }
                    },
                    onSubmitCallback = {
                        if (isWorking) return@OAuthSection
                        val start = oauthStart
                        if (start == null) {
                            errorText = context.getString(R.string.error_start_oauth_first)
                            return@OAuthSection
                        }
                        val code =
                            if (start.provider == OAuthClient.OAuthProvider.QwenCode) {
                                ""
                            } else {
                                val parsed = oauthClient.parseCallback(callbackUrl)
                                if (parsed == null) {
                                    errorText = context.getString(R.string.error_invalid_callback_url)
                                    return@OAuthSection
                                }
                                if (!parsed.error.isNullOrBlank()) {
                                    errorText = parsed.errorDescription?.takeIf { it.isNotBlank() } ?: parsed.error
                                    return@OAuthSection
                                }
                                val parsedCode = parsed.code?.trim().orEmpty()
                                if (parsedCode.isBlank()) {
                                    errorText = context.getString(R.string.error_missing_authorization_code)
                                    return@OAuthSection
                                }
                                if (parsed.state?.trim().orEmpty() != start.state.trim()) {
                                    errorText = context.getString(R.string.error_oauth_state_mismatch)
                                    return@OAuthSection
                                }
                                parsedCode
                            }

                        isWorking = true
                        errorText = null
                        scope.launch {
                            val reusableProvider =
                                connectedProvider
                                    ?: existingProvider
                                    ?: when (start.provider) {
                                        OAuthClient.OAuthProvider.Codex -> {
                                            providers.firstOrNull { provider ->
                                                provider.presetId?.trim()?.equals("codex", ignoreCase = true) == true ||
                                                    provider.oauthProvider?.trim()?.equals("codex", ignoreCase = true) == true
                                            }
                                        }
                                        OAuthClient.OAuthProvider.IFlow -> {
                                            providers.firstOrNull { provider ->
                                                provider.presetId?.trim()?.equals("iflow", ignoreCase = true) == true ||
                                                    provider.oauthProvider?.trim()?.equals("iflow", ignoreCase = true) == true
                                            }
                                        }
                                        OAuthClient.OAuthProvider.QwenCode -> {
                                            providers.firstOrNull { provider ->
                                                provider.presetId?.trim()?.equals("qwen_code", ignoreCase = true) == true ||
                                                    provider.presetId?.trim()?.equals("qwen", ignoreCase = true) == true ||
                                                    provider.oauthProvider?.trim()?.equals("qwen", ignoreCase = true) == true
                                            }
                                        }
                                    }
                            val reusableProviderId =
                                reusableProvider?.id?.trim()?.takeIf { it.isNotBlank() }
                                    ?: java.util.UUID.randomUUID().toString()

                            val providerResult: Result<ProviderConfig> =
                                when (start.provider) {
                                    OAuthClient.OAuthProvider.Codex -> {
                                        oauthClient.exchangeCodex(
                                            code = code,
                                            redirectUri = start.redirectUri,
                                            pkceCodeVerifier = start.pkceCodeVerifier.orEmpty()
                                        ).map { token ->
                                            ProviderConfig(
                                                id = reusableProviderId,
                                                presetId = "codex",
                                                iconAsset = "codex.svg",
                                                name = providerName.trim().ifBlank { "Codex" },
                                                type = "codex",
                                                apiUrl = "https://chatgpt.com/backend-api/codex",
                                                apiKey = token.accessToken,
                                                oauthProvider = "codex",
                                                oauthAccessToken = token.accessToken,
                                                oauthRefreshToken = token.refreshToken,
                                                oauthIdToken = token.idToken,
                                                oauthAccountId = token.accountId,
                                                oauthEmail = token.email,
                                                oauthExpiresAtMs = token.expiresAtMs,
                                                headers = reusableProvider?.headers.orEmpty()
                                            )
                                        }
                                    }
                                    OAuthClient.OAuthProvider.IFlow -> {
                                        oauthClient.exchangeIFlow(code = code, redirectUri = start.redirectUri).map { token ->
                                            ProviderConfig(
                                                id = reusableProviderId,
                                                presetId = "iflow",
                                                iconAsset = "iflow.svg",
                                                name = providerName.trim().ifBlank { "iFlow" },
                                                type = "openai",
                                                apiUrl = "https://apis.iflow.cn/v1",
                                                apiKey = token.apiKey,
                                                oauthProvider = "iflow",
                                                oauthAccessToken = token.accessToken,
                                                oauthRefreshToken = token.refreshToken,
                                                oauthEmail = token.email,
                                                oauthExpiresAtMs = token.expiresAtMs,
                                                headers = reusableProvider?.headers.orEmpty()
                                            )
                                        }
                                    }
                                    OAuthClient.OAuthProvider.QwenCode -> {
                                        oauthClient.exchangeQwenDeviceCode(
                                            deviceCode = start.deviceCode.orEmpty(),
                                            pkceCodeVerifier = start.pkceCodeVerifier.orEmpty(),
                                            pollIntervalSeconds = start.pollIntervalSeconds ?: 5
                                        ).map { token ->
                                            ProviderConfig(
                                                id = reusableProviderId,
                                                presetId = "qwen_code",
                                                iconAsset = "qwen-color.svg",
                                                name = providerName.trim().ifBlank { "Qwen Code" },
                                                type = "openai",
                                                apiUrl = token.apiBaseUrl,
                                                apiKey = token.accessToken,
                                                oauthProvider = "qwen",
                                                oauthAccessToken = token.accessToken,
                                                oauthRefreshToken = token.refreshToken,
                                                oauthExpiresAtMs = token.expiresAtMs,
                                                headers = reusableProvider?.headers.orEmpty()
                                            )
                                        }
                                    }
                                }

                            providerResult.onSuccess { providerConfig ->
                                connectedProvider = providerConfig
                                repository.upsertProvider(providerConfig)

                                val modelIds =
                                    chatApiClient.listModels(providerConfig).getOrElse { throwable ->
                                        errorText = context.getString(
                                            R.string.error_load_models_with_reason,
                                            throwable.message ?: throwable.toString()
                                        )
                                        emptyList()
                                    }
                                val enabledSet = suggestEnabledModels(start.provider, modelIds).toSet()

                                if (modelIds.isNotEmpty()) {
                                    repository.upsertModels(
                                        modelIds.map { idValue ->
                                            ModelConfig(
                                                id = buildModelStorageId(providerConfig.id, idValue),
                                                displayName = idValue,
                                                enabled = enabledSet.contains(idValue),
                                                providerId = providerConfig.id
                                            )
                                        }
                                    )
                                }

                                currentStep = OAuthStep.STEP_3_COMPLETED
                            }.onFailure { throwable ->
                                errorText = throwable.message ?: throwable.toString()
                            }

                            isWorking = false
                        }
                    },
                    onCancel = { currentStep = OAuthStep.STEP_1_CONNECT },
                    onReset = {
                        val providerId = connectedProvider?.id
                        if (!providerId.isNullOrBlank()) {
                            scope.launch { repository.deleteProviderAndModels(providerId) }
                        }
                        currentStep = OAuthStep.STEP_1_CONNECT
                        callbackUrl = ""
                        oauthStart = null
                        connectedProvider = null
                    }
                )

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText.orEmpty(),
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        color = Color(0xFFFF3B30),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                AnimatedVisibility(
                    visible = currentStep == OAuthStep.STEP_3_COMPLETED && connectedProvider != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { 20 })
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SupplierCardGray, RoundedCornerShape(20.dp))
                            .pressableScale(pressedScale = 0.98f) {
                                val providerId = connectedProvider?.id?.trim().orEmpty()
                                if (providerId.isBlank()) return@pressableScale
                                scope.launch {
                                    val trimmedName = providerName.trim()
                                    val provider = connectedProvider
                                    if (provider != null && trimmedName.isNotBlank() && provider.name != trimmedName) {
                                        val updated = provider.copy(name = trimmedName)
                                        repository.upsertProvider(updated)
                                        connectedProvider = updated
                                    }
                                    navController.navigate("models?providerId=$providerId")
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.models),
                                fontSize = 13.sp,
                                fontFamily = SourceSans3,
                                color = Color(0xFF8E8E93)
                            )
                            Text(
                                text = stringResource(R.string.model_services_configure_models),
                                fontSize = 17.sp,
                                fontFamily = SourceSans3,
                                color = Color(0xFF1C1C1E),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = "Navigate",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 底部空间
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 头像选择弹窗
    if (showAvatarModal) {
        AvatarSelectionModal(
            selectedAvatar = selectedAvatar,
            onAvatarSelected = { selectedAvatar = it },
            onDismiss = { showAvatarModal = false },
            onImportFromGallery = { /* 打开相册 */ }
        )
    }
}

private fun suggestEnabledModels(provider: OAuthClient.OAuthProvider, modelIds: List<String>): List<String> {
    if (modelIds.isEmpty()) return emptyList()
    val byLower = modelIds.associateBy { it.lowercase() }
    return when (provider) {
        OAuthClient.OAuthProvider.Codex ->
            listOfNotNull(byLower["gpt-5.1-codex"], byLower["gpt-5-codex"], modelIds.firstOrNull()).distinct()
        OAuthClient.OAuthProvider.IFlow -> listOfNotNull(modelIds.firstOrNull()).distinct()
        OAuthClient.OAuthProvider.QwenCode ->
            listOfNotNull(byLower["qwen3-coder-plus"], byLower["qwen3-coder-flash"], modelIds.firstOrNull()).distinct()
    }
}

@Composable
private fun AvatarSection(
    selectedAvatar: String,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconAsset = remember(selectedAvatar) { findProviderPreset(selectedAvatar)?.iconAsset }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 大头像
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF1F1F1))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAvatarClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!iconAsset.isNullOrBlank()) {
                AssetIcon(
                    assetFileName = iconAsset,
                    contentDescription = selectedAvatar,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF2F2F7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ChatGPTLogo,
                        contentDescription = null,
                        tint = Color(0xFF1C1C1E),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // 编辑图标覆盖层 (hover效果在移动端显示为常显或点击时显示)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Edit,
                    contentDescription = "Edit",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.add_provider_tap_change_avatar),
            fontSize = 13.sp,
            color = Color(0xFF8E8E93),
            fontFamily = SourceSans3
        )
    }
}

@Composable
private fun AvatarSelectionModal(
    selectedAvatar: String,
    onAvatarSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onImportFromGallery: () -> Unit
) {
    val builtinAvatars = listOf(
        "codex",
        "iflow",
        "qwen"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    ),
                color = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // 拖动条
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(Color(0xFFD1D1D6), RoundedCornerShape(2.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 标题
                    Text(
                        text = stringResource(R.string.add_provider_select_avatar),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1C1E),
                        fontFamily = SourceSans3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 头像网格
                    val rows = builtinAvatars.chunked(5)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { avatar ->
                                    val isSelected = avatar == selectedAvatar
                                    val avatarAsset = findProviderPreset(avatar)?.iconAsset.orEmpty()
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSelected) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                                            )
                                            .clickable {
                                                onAvatarSelected(avatar)
                                                onDismiss()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarAsset.isNotBlank()) {
                                            AssetIcon(
                                                assetFileName = avatarAsset,
                                                contentDescription = avatar,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Icon(
                                                imageVector = AppIcons.ChatGPTLogo,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else Color(0xFF1C1C1E),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 从相册导入按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFF5F5F7))
                            .clickable(onClick = onImportFromGallery),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Image,
                                contentDescription = null,
                                tint = Color(0xFF1C1C1E),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.add_provider_import_from_gallery),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1C1C1E),
                                fontFamily = SourceSans3
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 取消按钮
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            fontSize = 16.sp,
                            color = Color(0xFF8E8E93),
                            fontFamily = SourceSans3
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderNameInput(
    value: String,
    onValueChange: (String) -> Unit,
    embedded: Boolean = false,
    inRow: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (embedded) 0.dp else 16.dp, vertical = if (embedded) 0.dp else 14.dp)
        ) {
            if (!inRow) {
                Text(
                    text = stringResource(R.string.add_oauth_provider_name),
                    fontSize = 13.sp,
                    color = SupplierHintColor,
                    fontFamily = SourceSans3
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.9.dp, color = SupplierFieldBorder, shape = RoundedCornerShape(24.dp)),
                color = SupplierCardGray,
                shape = RoundedCornerShape(24.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = if (inRow) 12.dp else 13.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 17.sp,
                        color = Color(0xFF1C1C1E),
                        fontFamily = SourceSans3
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.add_oauth_provider_name_placeholder),
                                    fontSize = 17.sp,
                                    color = SupplierHintColor,
                                    fontFamily = SourceSans3
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
    if (embedded) {
        content()
    } else {
        Surface(
            color = SupplierCardGray,
            shape = RoundedCornerShape(20.dp)
        ) { content() }
    }
}

@Composable
private fun OAuthSection(
    currentStep: OAuthStep,
    callbackUrl: String,
    showCallbackInput: Boolean,
    onCallbackUrlChange: (String) -> Unit,
    onStartOAuth: () -> Unit,
    onSubmitCallback: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        color = SupplierCardGray,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_oauth_authentication),
                    fontSize = 13.sp,
                    color = Color(0xFF8E8E93),
                    fontFamily = SourceSans3
                )

                // OAuth 2.0 标签
                Surface(
                    color = Color(0xFFE8F4FD),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.oauth_badge),
                        fontSize = 12.sp,
                        color = Color(0xFF1C1C1E),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontFamily = SourceSans3
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 步骤指示器
            StepIndicator(currentStep = currentStep)

            Spacer(modifier = Modifier.height(16.dp))

            // 步骤内容
            when (currentStep) {
                OAuthStep.STEP_1_CONNECT -> Step1Content(onStartOAuth = onStartOAuth)
                OAuthStep.STEP_2_CALLBACK -> Step2Content(
                    callbackUrl = callbackUrl,
                    showCallbackInput = showCallbackInput,
                    onCallbackUrlChange = onCallbackUrlChange,
                    onSubmit = onSubmitCallback,
                    onCancel = onCancel
                )
                OAuthStep.STEP_3_COMPLETED -> Step3Content(onReset = onReset)
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: OAuthStep) {
    val stepNumber = when (currentStep) {
        OAuthStep.STEP_1_CONNECT -> 1
        OAuthStep.STEP_2_CALLBACK -> 2
        OAuthStep.STEP_3_COMPLETED -> 3
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step 1
        StepCircle(
            number = 1,
            isActive = stepNumber == 1,
            isCompleted = stepNumber > 1
        )

        // Line 1
        StepLine(isCompleted = stepNumber > 1, modifier = Modifier.weight(1f))

        // Step 2
        StepCircle(
            number = 2,
            isActive = stepNumber == 2,
            isCompleted = stepNumber > 2
        )

        // Line 2
        StepLine(isCompleted = stepNumber > 2, modifier = Modifier.weight(1f))

        // Step 3
        StepCircle(
            number = 3,
            isActive = stepNumber == 3,
            isCompleted = false
        )
    }
}

@Composable
private fun StepCircle(
    number: Int,
    isActive: Boolean,
    isCompleted: Boolean
) {
    val backgroundColor = when {
        isCompleted -> Color(0xFF1C1C1E)
        isActive -> Color(0xFF007AFF)
        else -> Color(0xFFF2F2F7)
    }

    val contentColor = when {
        isCompleted || isActive -> Color.White
        else -> Color(0xFF8E8E93)
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                text = number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun StepLine(isCompleted: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(2.dp)
            .padding(horizontal = 8.dp)
            .background(
                if (isCompleted) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            )
    )
}

@Composable
private fun Step1Content(onStartOAuth: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.add_oauth_step1_tip),
            fontSize = 14.sp,
            color = Color(0xFF6B6B6B),
            fontFamily = SourceSans3,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1E))
                .clickable(onClick = onStartOAuth),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.OAuth,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.add_oauth_connect),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontFamily = SourceSans3
                )
            }
        }
    }
}

@Composable
private fun Step2Content(
    callbackUrl: String,
    showCallbackInput: Boolean,
    onCallbackUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(if (showCallbackInput) R.string.add_oauth_step2_tip else R.string.add_oauth_step2_device_tip),
            fontSize = 14.sp,
            color = Color(0xFF6B6B6B),
            fontFamily = SourceSans3,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (showCallbackInput) {
            // 输入框
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.2.dp, color = SupplierFieldBorder, shape = RoundedCornerShape(14.dp)),
                color = SupplierCardGray,
                shape = RoundedCornerShape(14.dp)
            ) {
                BasicTextField(
                    value = callbackUrl,
                    onValueChange = onCallbackUrlChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        color = Color(0xFF1C1C1E),
                        fontFamily = SourceSans3
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (callbackUrl.isEmpty()) {
                                Text(
                                    text = "https://your-app.com/oauth/callback",
                                    fontSize = 16.sp,
                                    color = SupplierHintColor,
                                    fontFamily = SourceSans3
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.2.dp, color = SupplierFieldBorder, shape = RoundedCornerShape(14.dp)),
                color = SupplierCardGray,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_oauth_step2_device_hint),
                    fontSize = 14.sp,
                    color = SupplierHintColor,
                    fontFamily = SourceSans3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cancel 按钮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF5F5F7))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1C1C1E),
                    fontFamily = SourceSans3
                )
            }

            // Continue 按钮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable(onClick = onSubmit),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.common_continue),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontFamily = SourceSans3
                )
            }
        }
    }
}

@Composable
private fun Step3Content(onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 成功图标
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.add_oauth_connected),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1C1C1E),
            fontFamily = SourceSans3
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.add_oauth_connected_tip),
            fontSize = 13.sp,
            color = Color(0xFF8E8E93),
            fontFamily = SourceSans3
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onReset) {
            Text(
                text = stringResource(R.string.add_oauth_disconnect),
                fontSize = 14.sp,
                color = Color(0xFF1C1C1E).copy(alpha = 0.6f),
                fontFamily = SourceSans3
            )
        }
    }
}
