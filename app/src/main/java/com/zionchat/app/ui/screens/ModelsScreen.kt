package com.zionchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalChatApiClient
import com.zionchat.app.LocalProviderAuthManager
import com.zionchat.app.R
import com.zionchat.app.data.Message
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.buildModelStorageId
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.headerActionButtonShadow
import com.zionchat.app.ui.components.liquid.BackdropLiquidToggle
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material3.Surface as M3Surface

private val ModelModalCardGray = Color(0xFFF1F1F1)
private val ModelModalInputBorder = Color(0xFF44464D)
private val ModelModalHint = Color(0xFF74747D)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ModelsScreen(navController: NavController, providerId: String? = null) {
    val repository = LocalAppRepository.current
    val chatApiClient = LocalChatApiClient.current
    val providerAuthManager = LocalProviderAuthManager.current
    val scope = rememberCoroutineScope()

    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())

    val activeProvider = remember(providers, providerId) {
        val id = providerId?.trim().orEmpty()
        if (id.isNotBlank()) providers.firstOrNull { it.id == id } else providers.firstOrNull()
    }

    var showAddModal by remember { mutableStateOf(false) }
    var showTestModal by remember { mutableStateOf(false) }
    var isFetchingRemote by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var fetchedSignature by remember { mutableStateOf<String?>(null) }
    var isTestingConnections by remember { mutableStateOf(false) }
    var testingModelId by remember { mutableStateOf<String?>(null) }
    var selectedTestModelId by remember { mutableStateOf<String?>(null) }
    var testSummary by remember { mutableStateOf<String?>(null) }
    var testResults by remember { mutableStateOf<Map<String, ModelConnectionTestResult>>(emptyMap()) }

    val testPrompt = stringResource(R.string.models_test_prompt)
    val testSummaryNoProvider = stringResource(R.string.models_test_summary_no_provider)
    val testSummaryNoModels = stringResource(R.string.models_test_summary_no_models)
    val testSummaryNoSelection = stringResource(R.string.models_test_summary_no_selection)
    val testSummarySingleOk = stringResource(R.string.models_test_summary_single_ok)
    val testSummarySingleFailed = stringResource(R.string.models_test_summary_single_failed)
    val testSummaryAllTemplate = stringResource(R.string.models_test_summary_all_template)

    LaunchedEffect(activeProvider?.id, activeProvider?.apiUrl, activeProvider?.apiKey, fetchedSignature) {
        val provider = activeProvider ?: return@LaunchedEffect
        if (provider.apiUrl.isBlank() || provider.apiKey.isBlank()) return@LaunchedEffect

        val signature = "${provider.id}|${provider.apiUrl}|${provider.apiKey}"
        if (signature == fetchedSignature) return@LaunchedEffect

        isFetchingRemote = true
        remoteError = null
        val modelIds = runCatching {
            val resolvedProvider = providerAuthManager.ensureValidProvider(provider)
            chatApiClient.listModels(resolvedProvider).getOrThrow()
        }.getOrElse { throwable ->
            remoteError = "Failed to load models: ${throwable.message ?: throwable.toString()}"
            emptyList()
        }
        if (modelIds.isNotEmpty()) {
            val existingModels = repository.modelsFlow.first()
            val existingIds = existingModels.map { it.id }.toSet()
            val toInsert =
                modelIds
                    .map { idValue -> idValue.trim() }
                    .filter { it.isNotBlank() }
                    .map { idValue -> buildModelStorageId(provider.id, idValue) to idValue }
                    .filter { (storageId, _) -> storageId !in existingIds }
                    .map { (storageId, remoteId) ->
                        ModelConfig(
                            id = storageId,
                            displayName = remoteId,
                            enabled = false,
                            providerId = provider.id
                        )
                    }
            if (toInsert.isNotEmpty()) {
                repository.upsertModels(toInsert)
            }
        }

        fetchedSignature = signature
        isFetchingRemote = false
    }

    val providerModels by remember(models, activeProvider?.id) {
        derivedStateOf {
            val pid = activeProvider?.id?.trim().orEmpty()
            if (pid.isBlank()) emptyList()
            else models.filter { it.providerId == pid }
        }
    }

    val sortedModels by remember(providerModels) {
        derivedStateOf {
            providerModels.sortedWith(
                compareByDescending<ModelConfig> { it.enabled }.thenBy { it.displayName.lowercase() }
            )
        }
    }

    LaunchedEffect(showTestModal, sortedModels) {
        if (!showTestModal) return@LaunchedEffect
        if (sortedModels.isEmpty()) {
            selectedTestModelId = null
            return@LaunchedEffect
        }
        val current = selectedTestModelId?.trim().orEmpty()
        if (current.isBlank() || sortedModels.none { it.id == current }) {
            selectedTestModelId = sortedModels.first().id
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isFetchingRemote,
        onRefresh = { fetchedSignature = null }
    )

    SettingsPage(
        title = stringResource(R.string.models),
        onBack = { navController.popBackStack() },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .headerActionButtonShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f) { showTestModal = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = rememberResourceDrawablePainter(R.drawable.ic_model_test),
                        contentDescription = stringResource(R.string.models_test_entry),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .headerActionButtonShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f) { showAddModal = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Plus,
                        contentDescription = "Add Model",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = PageTopBarContentTopPadding)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    if (activeProvider == null) {
                        Text(
                            text = stringResource(R.string.models_test_summary_no_provider),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    } else {
                        if (isFetchingRemote || remoteError != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = when {
                                        isFetchingRemote -> "Loading models from provider..."
                                        remoteError != null -> remoteError.orEmpty()
                                        else -> ""
                                    },
                                    fontSize = 13.sp,
                                    color = if (remoteError != null) Color(0xFFFF3B30) else TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (remoteError != null) {
                                    Text(
                                        text = "Retry",
                                        fontSize = 13.sp,
                                        fontFamily = SourceSans3,
                                        color = TextPrimary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { fetchedSignature = null }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        sortedModels.forEach { model ->
                            ModelItem(
                                model = model,
                                onToggle = { nextEnabled ->
                                    scope.launch { repository.upsertModel(model.copy(enabled = nextEnabled)) }
                                },
                                onClick = {
                                    navController.navigate("model_config?id=${model.id}")
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                PullRefreshIndicator(
                    refreshing = isFetchingRemote,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = PageTopBarContentTopPadding)
                        .padding(top = 4.dp),
                    contentColor = TextPrimary,
                    backgroundColor = Surface
                )
            }

        AddModelModal(
            visible = showAddModal,
            onDismiss = { showAddModal = false },
            onAdd = { id, name ->
                scope.launch {
                    val provider = activeProvider ?: return@launch
                    repository.upsertModel(
                        ModelConfig(
                            id = buildModelStorageId(provider.id, id.trim()),
                            displayName = name.trim().ifBlank { id.trim() },
                            enabled = true,
                            providerId = provider.id
                        )
                    )
                    showAddModal = false
                }
            }
        )

        ModelConnectionTestModal(
            visible = showTestModal,
            models = sortedModels,
            selectedModelId = selectedTestModelId,
            isTesting = isTestingConnections,
            testingModelId = testingModelId,
            summary = testSummary,
            results = testResults,
            onDismiss = { showTestModal = false },
            onSelectModel = { selectedTestModelId = it },
            onRunSelected = {
                if (isTestingConnections) return@ModelConnectionTestModal
                scope.launch {
                    val provider = activeProvider
                    if (provider == null) {
                        testSummary = testSummaryNoProvider
                        return@launch
                    }
                    val selectedId = selectedTestModelId?.trim().orEmpty()
                    val selectedModel = sortedModels.firstOrNull { it.id == selectedId }
                    if (selectedModel == null) {
                        testSummary = testSummaryNoSelection
                        return@launch
                    }
                    isTestingConnections = true
                    testingModelId = selectedModel.id
                    val result =
                        runModelConnectionTest(
                            provider = provider,
                            model = selectedModel,
                            providerAuthManager = providerAuthManager,
                            chatApiClient = chatApiClient,
                            testPrompt = testPrompt
                        )
                    testResults = testResults + (selectedModel.id to result)
                    testSummary = if (result.success) testSummarySingleOk else testSummarySingleFailed
                    testingModelId = null
                    isTestingConnections = false
                }
            },
            onRunAll = {
                if (isTestingConnections) return@ModelConnectionTestModal
                scope.launch {
                    val provider = activeProvider
                    if (provider == null) {
                        testSummary = testSummaryNoProvider
                        return@launch
                    }
                    if (sortedModels.isEmpty()) {
                        testSummary = testSummaryNoModels
                        return@launch
                    }
                    isTestingConnections = true
                    testResults = emptyMap()
                    var successCount = 0
                    sortedModels.forEach { model ->
                        testingModelId = model.id
                        val result =
                            runModelConnectionTest(
                                provider = provider,
                                model = model,
                                providerAuthManager = providerAuthManager,
                                chatApiClient = chatApiClient,
                                testPrompt = testPrompt
                            )
                        if (result.success) successCount += 1
                        testResults = testResults + (model.id to result)
                    }
                    testingModelId = null
                    isTestingConnections = false
                    testSummary = String.format(testSummaryAllTemplate, successCount, sortedModels.size)
                }
            }
        )
    }
}

private data class ModelConnectionTestResult(
    val success: Boolean,
    val detail: String
)

private suspend fun runModelConnectionTest(
    provider: ProviderConfig,
    model: ModelConfig,
    providerAuthManager: com.zionchat.app.data.ProviderAuthManager,
    chatApiClient: com.zionchat.app.data.ChatApiClient,
    testPrompt: String
): ModelConnectionTestResult {
    return runCatching {
        val resolvedProvider = providerAuthManager.ensureValidProvider(provider)
        val remoteModelId = extractRemoteModelId(model.id).ifBlank { model.id.trim() }
        chatApiClient.chatCompletions(
            provider = resolvedProvider,
            modelId = remoteModelId,
            messages = listOf(Message(role = "user", content = testPrompt))
        ).getOrThrow()
        ModelConnectionTestResult(
            success = true,
            detail = ""
        )
    }.getOrElse { throwable ->
        val detail =
            throwable.message
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throwable::class.java.simpleName
        ModelConnectionTestResult(
            success = false,
            detail = detail
        )
    }
}

@Composable
private fun ModelItem(
    model: ModelConfig,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val neutralModelCard = Color(0xFFF2F2F2)
    val cardColor by animateColorAsState(
        targetValue = neutralModelCard,
        animationSpec = tween(durationMillis = 180),
        label = "model_item_bg"
    )
    val rowBackdrop = rememberLayerBackdrop()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 180))
            .layerBackdrop(rowBackdrop)
            .background(cardColor, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = model.displayName,
            fontSize = 17.sp,
            fontFamily = SourceSans3,
            color = TextPrimary,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )

        ModelToggleSwitch(
            enabled = model.enabled,
            onToggle = onToggle,
            backdrop = rowBackdrop
        )
    }
}

@Composable
private fun ModelToggleSwitch(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    BackdropLiquidToggle(
        checked = enabled,
        onCheckedChange = onToggle,
        backdrop = backdrop,
        modifier = modifier
    )
}

private fun deriveModelDisplayNameFromId(rawModelId: String): String {
    val normalized = rawModelId.trim()
    if (normalized.isBlank()) return ""
    val slashCleaned = normalized.substringAfterLast('/').trim()
    return if (slashCleaned.isNotBlank()) slashCleaned else normalized
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AddModelModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }

    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var displayNameManuallyEdited by remember { mutableStateOf(false) }
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sheetBottomInset = maxOf(imeBottomPadding, navBottomPadding)

    LaunchedEffect(visible) {
        if (visible) {
            modelId = ""
            modelName = ""
            displayNameManuallyEdited = false
            dragOffsetPx = 0f
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                M3Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = sheetBottomInset)
                        .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = { velocity ->
                                val shouldDismiss = dragOffsetPx > dismissThresholdPx || velocity > 2400f
                                if (shouldDismiss) {
                                    onDismiss()
                                    dragOffsetPx = 0f
                                } else {
                                    scope.launch {
                                        animate(
                                            initialValue = dragOffsetPx,
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 180, easing = LinearEasing)
                                        ) { value, _ ->
                                            dragOffsetPx = value
                                        }
                                    }
                                }
                            }
                        )
                        .animateEnterExit(
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        ),
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(GrayLight, RoundedCornerShape(2.dp))
                            )
                        }

                        Text(
                            text = "Add Model",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SourceSans3,
                            color = TextPrimary
                        )

                        FieldBlock(
                            label = "Model ID",
                            value = modelId,
                            onValueChange = { value ->
                                modelId = value
                                if (!displayNameManuallyEdited) {
                                    modelName = deriveModelDisplayNameFromId(value)
                                }
                            },
                            placeholder = "e.g. gpt-4o"
                        )

                        FieldBlock(
                            label = "Display Name",
                            value = modelName,
                            onValueChange = {
                                displayNameManuallyEdited = true
                                modelName = it
                            },
                            placeholder = "e.g. GPT-4o"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ModelModalCardGray)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontSize = 17.sp,
                                    fontFamily = SourceSans3,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = {
                                    val finalModelId = modelId.trim()
                                    val finalDisplayName = modelName.trim().ifBlank { deriveModelDisplayNameFromId(finalModelId) }
                                    if (finalModelId.isNotBlank() && finalDisplayName.isNotBlank()) {
                                        onAdd(finalModelId, finalDisplayName)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                            ) {
                                Text(
                                    text = "Add",
                                    fontSize = 17.sp,
                                    fontFamily = SourceSans3,
                                    color = Surface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldBlock(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val shape = RoundedCornerShape(16.dp)
    val hasValue = value.trim().isNotEmpty()
    Box(modifier = Modifier.fillMaxWidth()) {
        M3Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, shape)
                .border(width = 1.dp, color = ModelModalInputBorder, shape = shape),
            color = ModelModalCardGray,
            shape = shape
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                placeholder = {
                    if (!hasValue) {
                        Text(
                            text = placeholder,
                            fontSize = 17.sp,
                            fontFamily = SourceSans3,
                            color = ModelModalHint
                        )
                    }
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
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                ),
                singleLine = true
            )
        }

        AnimatedVisibility(
            visible = hasValue,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 14.dp, y = (-8).dp),
            enter = fadeIn(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Box(
                modifier = Modifier
                    .background(ModelModalCardGray, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontFamily = SourceSans3,
                    color = ModelModalHint,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ModelConnectionTestModal(
    visible: Boolean,
    models: List<ModelConfig>,
    selectedModelId: String?,
    isTesting: Boolean,
    testingModelId: String?,
    summary: String?,
    results: Map<String, ModelConnectionTestResult>,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit,
    onRunSelected: () -> Unit,
    onRunAll: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sheetBottomInset = maxOf(imeBottomPadding, navBottomPadding)

    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetPx = 0f
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                M3Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = sheetBottomInset)
                        .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = { velocity ->
                                val shouldDismiss = dragOffsetPx > dismissThresholdPx || velocity > 2400f
                                if (shouldDismiss) {
                                    onDismiss()
                                    dragOffsetPx = 0f
                                } else {
                                    scope.launch {
                                        animate(
                                            initialValue = dragOffsetPx,
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 180, easing = LinearEasing)
                                        ) { value, _ ->
                                            dragOffsetPx = value
                                        }
                                    }
                                }
                            }
                        )
                        .animateEnterExit(
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        ),
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(GrayLight, RoundedCornerShape(2.dp))
                            )
                        }
                        Text(
                            text = stringResource(R.string.models_test_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SourceSans3,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.models_test_subtitle),
                            fontSize = 13.sp,
                            fontFamily = SourceSans3,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRunSelected,
                                enabled = !isTesting && selectedModelId != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = TextPrimary,
                                        disabledContainerColor = GrayLight
                                    )
                            ) {
                                Text(
                                    text = stringResource(R.string.models_test_button_selected),
                                    color = if (isTesting || selectedModelId == null) TextSecondary else Surface,
                                    fontFamily = SourceSans3,
                                    fontSize = 14.sp
                                )
                            }
                            Button(
                                onClick = onRunAll,
                                enabled = !isTesting && models.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFDADAE0)),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        disabledContainerColor = GrayLight
                                    )
                            ) {
                                Text(
                                    text = stringResource(R.string.models_test_button_all),
                                    color = if (isTesting || models.isEmpty()) TextSecondary else TextPrimary,
                                    fontFamily = SourceSans3,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (summary?.isNotBlank() == true) {
                            Text(
                                text = summary,
                                fontSize = 12.sp,
                                fontFamily = SourceSans3,
                                color = TextSecondary
                            )
                        }

                        if (models.isEmpty()) {
                            Text(
                                text = stringResource(R.string.models_test_empty),
                                fontSize = 13.sp,
                                fontFamily = SourceSans3,
                                color = TextSecondary
                            )
                        } else {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 320.dp)
                                        .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                models.forEach { model ->
                                    val selected = selectedModelId == model.id
                                    val result = results[model.id]
                                    val isRunning = testingModelId == model.id
                                    val optionShape = RoundedCornerShape(12.dp)

                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .shadow(if (selected) 5.dp else 3.dp, optionShape)
                                                .clip(optionShape)
                                                .background(Color.White, optionShape)
                                                .border(
                                                    width = 1.dp,
                                                    color = Color(0xFFE0E0E5),
                                                    shape = optionShape
                                                )
                                                .pressableScale(pressedScale = 0.98f, onClick = { onSelectModel(model.id) })
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = model.displayName,
                                                fontSize = 14.sp,
                                                fontFamily = SourceSans3,
                                                color = TextPrimary,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            val statusColor =
                                                when {
                                                    isRunning -> Color(0xFF3B82F6)
                                                    result == null -> Color(0xFFD1D5DB)
                                                    result.success -> Color(0xFF0B8F5A)
                                                    else -> Color(0xFFD63B2F)
                                                }
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor)
                                            )
                                        }
                                        if (result?.detail?.isNotBlank() == true) {
                                            Text(
                                                text = result.detail,
                                                fontSize = 12.sp,
                                                fontFamily = SourceSans3,
                                                color = TextSecondary,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
