package com.zionchat.app.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.navigation.NavController
import com.zionchat.app.R
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalChatApiClient
import com.zionchat.app.LocalProviderAuthManager
import com.zionchat.app.LocalRuntimePackagingService
import com.zionchat.app.LocalWebHostingService
import com.zionchat.app.autosoul.runtime.AutoSoulAutomationManager
import com.zionchat.app.autosoul.runtime.AutoSoulScriptParser
import com.zionchat.app.data.AppRepository
import com.zionchat.app.data.AppAutomationTask
import com.zionchat.app.data.ChatApiClient
import com.zionchat.app.data.Conversation
import com.zionchat.app.data.HttpHeader
import com.zionchat.app.data.Message
import com.zionchat.app.data.MessageAttachment
import com.zionchat.app.data.MessageTag
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.McpClient
import com.zionchat.app.data.McpConfig
import com.zionchat.app.data.McpToolCall
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.RuntimeShellPlugin
import com.zionchat.app.data.SavedApp
import com.zionchat.app.data.WebSearchConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.data.isLikelyVisionModel
import com.zionchat.app.ui.components.TopFadeScrim
import com.zionchat.app.ui.components.AppSheetDragHandle
import com.zionchat.app.ui.components.AppHtmlWebView
import com.zionchat.app.ui.components.MarkdownText
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import coil3.compose.AsyncImage
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import java.io.ByteArrayOutputStream
import java.io.StringReader
import kotlin.math.roundToInt

// 颜色常量 - 完全匹配HTML原型
private data class PendingMessage(val conversationId: String, val message: Message)

// Keep streaming alive even if ChatScreen leaves composition (e.g. app switch/navigation).
private val chatStreamingExecutionScope by lazy {
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
internal data class PendingImageAttachment(val uri: Uri? = null, val bitmap: Bitmap? = null)
internal enum class ToolMenuPage { Tools, McpServers }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreenContent(navController: NavController) {
    val repository = LocalAppRepository.current
    val chatApiClient = LocalChatApiClient.current
    val providerAuthManager = LocalProviderAuthManager.current
    val runtimePackagingService = LocalRuntimePackagingService.current
    val webHostingService = LocalWebHostingService.current
    val mcpClient = remember { McpClient() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showToolMenu by remember { mutableStateOf(false) }
    var toolMenuPage by remember { mutableStateOf(ToolMenuPage.Tools) }
    var showChatModelPicker by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var selectedMcpServerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mcpToolPickerLoading by remember { mutableStateOf(false) }
    var mcpToolPickerError by remember { mutableStateOf<String?>(null) }
    val chatModelPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var messageText by remember { mutableStateOf("") }
    var imageAttachments by remember { mutableStateOf<List<PendingImageAttachment>>(emptyList()) }
    var inputFieldFocused by remember { mutableStateOf(false) }
    var lastKeyboardHideRequestAtMs by remember { mutableLongStateOf(0L) }
    var showThinkingSheet by remember { mutableStateOf(false) }
    var thinkingSheetText by remember { mutableStateOf<String?>(null) }
    val thinkingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tagSheetMessageId by remember { mutableStateOf<String?>(null) }
    var tagSheetTagId by remember { mutableStateOf<String?>(null) }
    val tagSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var appWorkspaceMessageId by remember { mutableStateOf<String?>(null) }
    var appWorkspaceTagId by remember { mutableStateOf<String?>(null) }
    var appDevDownloadBusyIds by remember { mutableStateOf(setOf<String>()) }
    var pendingAppBuilderConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = !appWorkspaceTagId.isNullOrBlank()) {
        appWorkspaceMessageId = null
        appWorkspaceTagId = null
    }
    BackHandler(enabled = showToolMenu) {
        if (toolMenuPage == ToolMenuPage.McpServers) {
            toolMenuPage = ToolMenuPage.Tools
        } else {
            showToolMenu = false
        }
    }
    BackHandler(enabled = showChatModelPicker) {
        showChatModelPicker = false
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                imageAttachments = imageAttachments + PendingImageAttachment(uri = uri)
            }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                imageAttachments = imageAttachments + PendingImageAttachment(bitmap = bitmap)
            }
        }

    val conversations by repository.conversationsFlow.collectAsState(initial = emptyList())
    val currentConversationId by repository.currentConversationIdFlow.collectAsState(initial = null)
    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val nickname by repository.nicknameFlow.collectAsState(initial = "")
    val avatarUri by repository.avatarUriFlow.collectAsState(initial = "")
    val customInstructions by repository.customInstructionsFlow.collectAsState(initial = "")
    val defaultChatModelId by repository.defaultChatModelIdFlow.collectAsState(initial = null)
    val chatThinkingEnabled by repository.chatThinkingEnabledFlow.collectAsState(initial = true)
    val defaultImageModelId by repository.defaultImageModelIdFlow.collectAsState(initial = null)
    val defaultAppBuilderModelId by repository.defaultAppBuilderModelIdFlow.collectAsState(initial = null)
    val webSearchConfig by repository.webSearchConfigFlow.collectAsState(initial = WebSearchConfig())
    val mcpList by repository.mcpListFlow.collectAsState(initial = emptyList())
    val pendingAppAutomationTask by repository.pendingAppAutomationTaskFlow.collectAsState(initial = null)
    val appAccentColor by repository.appAccentColorFlow.collectAsState(initial = "default")
    val accentPalette = remember(appAccentColor) { accentPaletteForKey(appAccentColor) }
    val enabledMcpServers = remember(mcpList) { mcpList.filter { it.enabled } }
    val mcpServerPickerItems = remember(enabledMcpServers) { buildMcpServerPickerItems(enabledMcpServers) }
    val chatModelGroups = remember(providers, models) { groupEnabledModelsByProvider(providers, models) }
    var lastAutoDispatchedTaskId by remember { mutableStateOf<String?>(null) }

    // Avoid IME restore loops on cold start/resume.
    LaunchedEffect(Unit) {
        delay(80)
        focusManager.clearFocus(force = true)
    }

    // 本地优先的会话选择：避免 DataStore 状态滞后导致“首条消息消失/会话跳回”
    var preferredConversationId by remember { mutableStateOf<String?>(null) }
    var preferredConversationSetAtMs by remember { mutableStateOf(0L) }

    LaunchedEffect(currentConversationId) {
        if (preferredConversationId.isNullOrBlank() && !currentConversationId.isNullOrBlank()) {
            preferredConversationId = currentConversationId
            preferredConversationSetAtMs = System.currentTimeMillis()
        }
    }

    val effectiveConversationId = remember(
        conversations,
        currentConversationId,
        preferredConversationId,
        preferredConversationSetAtMs
    ) {
        val preferred = preferredConversationId?.trim().takeIf { !it.isNullOrBlank() }
        val fromStore = currentConversationId?.trim().takeIf { !it.isNullOrBlank() }
        if (preferred == null) {
            fromStore ?: conversations.firstOrNull()?.id
        } else {
            val inList = conversations.any { it.id == preferred }
            val withinGrace = System.currentTimeMillis() - preferredConversationSetAtMs < 2500
            when {
                inList || withinGrace -> preferred
                !fromStore.isNullOrBlank() -> fromStore
                else -> conversations.firstOrNull()?.id
            }
        }
    }

    val currentConversation = remember(conversations, effectiveConversationId) {
        val cid = effectiveConversationId?.trim().orEmpty()
        if (cid.isBlank()) null else conversations.firstOrNull { it.id == cid }
    }

    LaunchedEffect(conversations, currentConversationId) {
        if (currentConversationId.isNullOrBlank() && conversations.isNotEmpty()) {
            repository.setCurrentConversationId(conversations.first().id)
        }
    }

    val messages = currentConversation?.messages.orEmpty()

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeightDp = with(density) { if (topBarHeightPx == 0) 66.dp else topBarHeightPx.toDp() }
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listTopPadding = statusBarTopPadding + topBarHeightDp + 8.dp
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisibilityThresholdPx = with(density) { 24.dp.roundToPx() }
    val imeVisible = inputFieldFocused && imeBottomPx > imeVisibilityThresholdPx
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSystemPadding =
        if (imeVisible) maxOf(imeBottomPadding, navBottomPadding) else navBottomPadding
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }
    val bottomContentPadding = maxOf(80.dp, bottomBarHeightDp + 12.dp + bottomSystemPadding)
    val inputBottomInset = bottomSystemPadding

    // Pending 消息：在 DataStore 落盘前立即显示，彻底修复“首条消息消失”
    var pendingMessages by remember { mutableStateOf<List<PendingMessage>>(emptyList()) }

    // 流式输出：用同一条 assistant 消息实时更新，避免结束时“闪一下重新渲染”
    var isStreaming by remember { mutableStateOf(false) }
    var sendInFlight by remember { mutableStateOf(false) }
    var streamingMessageId by remember { mutableStateOf<String?>(null) }
    var streamingConversationId by remember { mutableStateOf<String?>(null) }
    var streamingThinkingActive by remember { mutableStateOf(false) }
    var streamingJob by remember { mutableStateOf<Job?>(null) }
    var stopRequestedByUser by remember { mutableStateOf(false) }

    // 清理已落盘的 pending，避免列表不断增长
    LaunchedEffect(conversations, pendingMessages) {
        if (pendingMessages.isEmpty()) return@LaunchedEffect
        val updated = pendingMessages.filter { pending ->
            val convo = conversations.firstOrNull { it.id == pending.conversationId } ?: return@filter true
            convo.messages.none { it.id == pending.message.id }
        }
        if (updated.size != pendingMessages.size) {
            pendingMessages = updated
        }
    }

    val localMessages = remember(messages, pendingMessages, effectiveConversationId) {
        val convoId = effectiveConversationId?.trim().orEmpty()
        val dataStoreMessages = messages
        if (convoId.isBlank()) return@remember dataStoreMessages
        val pendingForConversation = pendingMessages
            .filter { it.conversationId == convoId }
            .map { it.message }
            .filterNot { pendingMsg -> dataStoreMessages.any { it.id == pendingMsg.id } }
        dataStoreMessages + pendingForConversation
    }

    val shouldAutoScroll by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= total - 2
        }
    }
    val latestLocalMessagesSize by rememberUpdatedState(localMessages.size)
    val latestShouldAutoScroll by rememberUpdatedState(shouldAutoScroll)
    val latestEffectiveConversationId by rememberUpdatedState(effectiveConversationId)
    val latestStreamingConversationId by rememberUpdatedState(streamingConversationId)

    var lastAutoScrolledConversationId by remember { mutableStateOf<String?>(null) }
    var scrollToBottomToken by remember { mutableIntStateOf(0) }

    // 进入新会话时定位到最新消息
    LaunchedEffect(effectiveConversationId) {
        val convoId = effectiveConversationId?.trim().orEmpty()
        if (convoId.isBlank() || localMessages.isEmpty()) return@LaunchedEffect

        if (lastAutoScrolledConversationId != convoId) {
            lastAutoScrolledConversationId = convoId
            if (localMessages.isNotEmpty()) {
                listState.scrollToItem(localMessages.size - 1, scrollOffset = 0)
            }
        }
    }

    // 发送消息时滚动到最新消息
    LaunchedEffect(scrollToBottomToken, localMessages.size) {
        if (scrollToBottomToken > 0 && localMessages.isNotEmpty()) {
            listState.scrollToItem(localMessages.size - 1, scrollOffset = 0)
        }
    }

    // 流式过程中保持显示最新内容
    LaunchedEffect(isStreaming, streamingMessageId, streamingConversationId) {
        if (!isStreaming) return@LaunchedEffect
        while (isStreaming) {
            val convoId = latestEffectiveConversationId?.trim().orEmpty()
            val targetConversationId = latestStreamingConversationId?.trim().orEmpty()
            if (convoId.isNotBlank() && convoId == targetConversationId && latestShouldAutoScroll) {
                val lastIndex = latestLocalMessagesSize - 1
                if (lastIndex >= 0) {
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                    val viewportBottom = layoutInfo.viewportEndOffset
                    val needsScroll =
                        when {
                            lastVisible == null -> true
                            lastVisible.index < lastIndex -> true
                            lastVisible.index > lastIndex -> false
                            else -> (lastVisible.offset + lastVisible.size) > (viewportBottom + 4)
                        }
                    if (needsScroll) {
                        listState.scrollToItem(lastIndex, scrollOffset = 0)
                    }
                }
            }
            delay(220)
        }
    }

    fun startNewChat() {
        scope.launch {
            val created = repository.createConversation()
            preferredConversationId = created.id
            preferredConversationSetAtMs = System.currentTimeMillis()
            selectedTool = null
            messageText = ""
            drawerState.close()
            scrollToBottomToken++
        }
    }

    fun stopStreaming() {
        if (!isStreaming) return
        stopRequestedByUser = true
        streamingJob?.cancel(CancellationException("Stopped by user."))
    }

    fun hideKeyboardIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force) {
            if (now - lastKeyboardHideRequestAtMs < 220L) return
            if (!imeVisible && !inputFieldFocused) return
        } else if (now - lastKeyboardHideRequestAtMs < 120L) {
            return
        }
        lastKeyboardHideRequestAtMs = now
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun openMcpToolPicker() {
        showToolMenu = true
        toolMenuPage = ToolMenuPage.McpServers
        mcpToolPickerError = null
        if (selectedTool == "mcp") {
            selectedTool = null
        }
        hideKeyboardIfNeeded(force = true)
        scope.launch {
            mcpToolPickerLoading = true
            runCatching {
                val enabledServersSnapshot = repository.mcpListFlow.first().filter { it.enabled }
                enabledServersSnapshot.forEach { server ->
                    if (server.tools.isNotEmpty()) return@forEach
                    val fetched = mcpClient.fetchTools(server).getOrNull().orEmpty()
                    if (fetched.isNotEmpty()) {
                        repository.updateMcpTools(server.id, fetched)
                    }
                }
            }.onFailure { error ->
                mcpToolPickerError =
                    error.message?.trim()?.takeIf { it.isNotBlank() }
                        ?: "Failed to sync MCP tools."
            }
            val refreshedEnabledServers = repository.mcpListFlow.first().filter { it.enabled }
            val refreshedIds = refreshedEnabledServers.map { it.id }.toSet()
            val normalizedCurrent = selectedMcpServerIds.filter { it in refreshedIds }.toSet()
            selectedMcpServerIds =
                when {
                    refreshedIds.isEmpty() -> emptySet()
                    normalizedCurrent.isNotEmpty() -> normalizedCurrent
                    selectedMcpServerIds.isNotEmpty() -> normalizedCurrent
                    else -> refreshedIds
                }
            mcpToolPickerLoading = false
        }
    }

    data class DeployOutcome(
        val app: SavedApp,
        val errorText: String?,
        val deployedNow: Boolean
    )

    suspend fun deploySavedAppIfEnabled(app: SavedApp): DeployOutcome {
        val hostingConfig = repository.getWebHostingConfig()
        if (!hostingConfig.autoDeploy) {
            return DeployOutcome(
                app = app,
                errorText = "Auto deploy is off. Enable it in Web hosting.",
                deployedNow = false
            )
        }
        if (hostingConfig.token.isBlank()) {
            return DeployOutcome(
                app = app,
                errorText = "Missing Vercel token in Web hosting settings.",
                deployedNow = false
            )
        }
        return webHostingService.deployApp(
            appId = app.id,
            html = app.html,
            config = hostingConfig
        ).fold(
            onSuccess = { deployUrl ->
                DeployOutcome(
                    app =
                        app.copy(
                            deployUrl = deployUrl.trim(),
                            runtimeBuildStatus = "",
                            runtimeBuildRequestId = null,
                            runtimeBuildRunId = null,
                            runtimeBuildRunUrl = null,
                            runtimeBuildArtifactName = null,
                            runtimeBuildArtifactUrl = null,
                            runtimeBuildError = null,
                            runtimeBuildUpdatedAt = System.currentTimeMillis()
                        ),
                    errorText = null,
                    deployedNow = true
                )
            },
            onFailure = { throwable ->
                val errorText =
                    throwable.message?.trim()?.takeIf { it.isNotBlank() }
                        ?: "Deploy failed"
                DeployOutcome(app = app, errorText = errorText, deployedNow = false)
            }
        )
    }

    suspend fun triggerRuntimePackagingIfNeeded(
        app: SavedApp,
        deployedNow: Boolean
    ): SavedApp {
        if (!deployedNow) return app
        if (!RuntimeShellPlugin.isInstalled(context)) {
            return app.copy(
                runtimeBuildStatus = "disabled",
                runtimeBuildError = "Runtime shell template is required. Open Apps and download it first.",
                runtimeBuildUpdatedAt = System.currentTimeMillis()
            )
        }
        val deployUrl = app.deployUrl?.trim().orEmpty()
        if (deployUrl.isBlank()) {
            return app.copy(
                runtimeBuildStatus = "skipped",
                runtimeBuildError = "Deploy URL is missing",
                runtimeBuildUpdatedAt = System.currentTimeMillis()
            )
        }

        val versionModel = repository.appModuleVersionModelFlow.first().coerceAtLeast(1)
        return runtimePackagingService
            .triggerRuntimePackaging(
                app = app,
                deployUrl = deployUrl,
                versionModel = versionModel
            )
            .getOrElse { throwable ->
                app.copy(
                    runtimeBuildStatus = "failed",
                    runtimeBuildError = throwable.message?.trim()?.takeIf { it.isNotBlank() } ?: "Runtime packaging failed",
                    runtimeBuildVersionModel = versionModel,
                    runtimeBuildUpdatedAt = System.currentTimeMillis()
                )
            }
    }

    fun runtimeBuildStatusText(status: String?, errorText: String?): String? {
        val value = status?.trim()?.lowercase().orEmpty()
        return when (value) {
            "queued" -> "APK packaging queued"
            "in_progress" -> "APK packaging in progress"
            "success" -> "APK is ready"
            "failed" -> errorText?.takeIf { it.isNotBlank() } ?: "APK packaging failed"
            "disabled" -> errorText?.takeIf { it.isNotBlank() } ?: "Runtime shell template is required"
            "skipped" -> errorText?.takeIf { it.isNotBlank() } ?: "APK packaging skipped"
            else -> null
        }
    }

    fun shouldTrackRuntimeBuild(status: String?): Boolean {
        return when (status?.trim()?.lowercase()) {
            "queued", "in_progress" -> true
            else -> false
        }
    }

    fun startRuntimeBuildTracking(
        appId: String,
        conversationId: String,
        messageId: String,
        tagId: String
    ) {
        scope.launch {
            repeat(30) {
                delay(4000)
                val currentApp =
                    repository.savedAppsFlow.first().firstOrNull { it.id == appId }
                        ?: return@launch
                if (!shouldTrackRuntimeBuild(currentApp.runtimeBuildStatus)) {
                    return@launch
                }
                val synced =
                    runtimePackagingService.syncRuntimePackaging(currentApp)
                        .getOrElse { return@launch }
                if (synced == currentApp) return@repeat

                val persisted = repository.upsertSavedApp(synced) ?: synced
                repository.updateMessageTag(
                    conversationId = conversationId,
                    messageId = messageId,
                    tagId = tagId
                ) { current ->
                    val existingPayload =
                        parseAppDevTagPayload(
                            content = current.content,
                            fallbackName = current.title.ifBlank { "App development" },
                            fallbackStatus = current.status
                        )
                    val runtimeText = runtimeBuildStatusText(persisted.runtimeBuildStatus, persisted.runtimeBuildError)
                    val updatedPayload =
                        existingPayload.copy(
                            deployUrl = persisted.deployUrl,
                            runtimeStatus = persisted.runtimeBuildStatus.takeIf { it.isNotBlank() },
                            runtimeMessage = runtimeText,
                            runtimeRunUrl = persisted.runtimeBuildRunUrl,
                            runtimeArtifactName = persisted.runtimeBuildArtifactName,
                            runtimeArtifactUrl = persisted.runtimeBuildArtifactUrl
                        )
                    current.copy(
                        content = encodeAppDevTagPayload(updatedPayload),
                        status = if (persisted.runtimeBuildStatus == "failed") "error" else current.status
                    )
                }
            }
        }
    }

    fun downloadApkToDevice(
        artifactUrl: String,
        suggestedName: String,
        fallbackAppName: String
    ): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
        val uri = runCatching { Uri.parse(artifactUrl.trim()) }.getOrNull() ?: return false
        val rawName =
            suggestedName.trim().takeIf { it.isNotBlank() }
                ?: buildString {
                    append(
                        fallbackAppName
                            .trim()
                            .ifBlank { "zionchat-app" }
                            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
                            .trim('_')
                            .ifBlank { "zionchat-app" }
                            .take(64)
                    )
                    append("-")
                    append(System.currentTimeMillis())
                }
        val fileName = if (rawName.endsWith(".apk", ignoreCase = true)) rawName else "$rawName.apk"
        return runCatching {
            val request =
                DownloadManager.Request(uri)
                    .setTitle(fileName)
                    .setDescription("ZionChat APK download")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            manager.enqueue(request)
            true
        }.getOrDefault(false)
    }

    fun handleAppDevTagDownload(conversationId: String?, messageId: String, tag: MessageTag) {
        val convoId = conversationId?.trim().orEmpty()
        if (convoId.isBlank()) {
            Toast.makeText(context, "Conversation is unavailable.", Toast.LENGTH_SHORT).show()
            return
        }
        val initialPayload =
            parseAppDevTagPayload(
                content = tag.content,
                fallbackName = tag.title.ifBlank { "App development" },
                fallbackStatus = tag.status
            )
        val appId = initialPayload.sourceAppId?.trim().orEmpty()
        if (appId.isBlank()) {
            Toast.makeText(context, "App is not ready for APK download yet.", Toast.LENGTH_SHORT).show()
            return
        }
        if (appDevDownloadBusyIds.contains(appId)) {
            Toast.makeText(context, "APK packaging is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            appDevDownloadBusyIds = appDevDownloadBusyIds + appId
            try {
                suspend fun updateTagRuntime(saved: SavedApp, statusOverride: String? = null, messageOverride: String? = null) {
                    val runtimeText = messageOverride ?: runtimeBuildStatusText(
                        statusOverride ?: saved.runtimeBuildStatus,
                        saved.runtimeBuildError
                    )
                    repository.updateMessageTag(
                        conversationId = convoId,
                        messageId = messageId,
                        tagId = tag.id
                    ) { current ->
                        val existingPayload =
                            parseAppDevTagPayload(
                                content = current.content,
                                fallbackName = current.title.ifBlank { initialPayload.name },
                                fallbackStatus = current.status
                            )
                        val updatedPayload =
                            existingPayload.copy(
                                sourceAppId = saved.id,
                                deployUrl = saved.deployUrl,
                                deployError = null,
                                runtimeStatus = (statusOverride ?: saved.runtimeBuildStatus).takeIf { it.isNotBlank() },
                                runtimeMessage = runtimeText,
                                runtimeRunUrl = saved.runtimeBuildRunUrl,
                                runtimeArtifactName = saved.runtimeBuildArtifactName,
                                runtimeArtifactUrl = saved.runtimeBuildArtifactUrl
                            )
                        current.copy(content = encodeAppDevTagPayload(updatedPayload), status = current.status)
                    }
                }

                var savedApp =
                    repository.savedAppsFlow.first().firstOrNull { it.id == appId }
                        ?: run {
                            Toast.makeText(context, "Saved app was not found.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                val existingArtifact = savedApp.runtimeBuildArtifactUrl?.trim().orEmpty()
                if (existingArtifact.isNotBlank()) {
                    val downloaded =
                        downloadApkToDevice(
                            artifactUrl = existingArtifact,
                            suggestedName = savedApp.runtimeBuildArtifactName.orEmpty(),
                            fallbackAppName = savedApp.name
                        )
                    if (downloaded) {
                        updateTagRuntime(savedApp, statusOverride = "success", messageOverride = "APK download started")
                        Toast.makeText(context, "APK download started.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to start APK download.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                updateTagRuntime(savedApp, statusOverride = "queued", messageOverride = "Preparing APK packaging")

                val deployUrlReady =
                    savedApp.deployUrl?.trim()?.takeIf { it.isNotBlank() }
                        ?: run {
                            val deployOutcome = deploySavedAppIfEnabled(savedApp)
                            savedApp = repository.upsertSavedApp(deployOutcome.app) ?: deployOutcome.app
                            if (!deployOutcome.errorText.isNullOrBlank()) {
                                updateTagRuntime(
                                    savedApp,
                                    statusOverride = "failed",
                                    messageOverride = deployOutcome.errorText
                                )
                                Toast.makeText(context, deployOutcome.errorText, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            savedApp.deployUrl?.trim()?.takeIf { it.isNotBlank() }
                        }
                if (deployUrlReady.isNullOrBlank()) {
                    updateTagRuntime(savedApp, statusOverride = "failed", messageOverride = "Deploy URL is missing.")
                    Toast.makeText(context, "Deploy URL is missing.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val versionModel = repository.appModuleVersionModelFlow.first().coerceAtLeast(1)
                val triggered =
                    runtimePackagingService
                        .triggerRuntimePackaging(
                            app = savedApp,
                            deployUrl = deployUrlReady,
                            versionModel = versionModel
                        )
                        .getOrElse { throwable ->
                            savedApp.copy(
                                runtimeBuildStatus = "failed",
                                runtimeBuildError =
                                    throwable.message?.trim()?.takeIf { it.isNotBlank() }
                                        ?: "Runtime packaging failed",
                                runtimeBuildVersionModel = versionModel,
                                runtimeBuildUpdatedAt = System.currentTimeMillis()
                            )
                        }
                savedApp = repository.upsertSavedApp(triggered) ?: triggered
                updateTagRuntime(savedApp)

                suspend fun tryDownloadCurrentArtifact(): Boolean {
                    val artifactUrl = savedApp.runtimeBuildArtifactUrl?.trim().orEmpty()
                    if (artifactUrl.isBlank()) return false
                    val downloaded =
                        downloadApkToDevice(
                            artifactUrl = artifactUrl,
                            suggestedName = savedApp.runtimeBuildArtifactName.orEmpty(),
                            fallbackAppName = savedApp.name
                        )
                    if (downloaded) {
                        updateTagRuntime(savedApp, statusOverride = "success", messageOverride = "APK download started")
                        Toast.makeText(context, "APK download started.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to start APK download.", Toast.LENGTH_SHORT).show()
                    }
                    return downloaded
                }

                if (savedApp.runtimeBuildStatus.equals("success", ignoreCase = true)) {
                    if (!tryDownloadCurrentArtifact()) {
                        Toast.makeText(context, "APK artifact URL is missing.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (savedApp.runtimeBuildStatus.equals("failed", ignoreCase = true)) {
                    Toast.makeText(
                        context,
                        savedApp.runtimeBuildError?.ifBlank { null } ?: "APK packaging failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                if (savedApp.runtimeBuildStatus.equals("queued", ignoreCase = true) ||
                    savedApp.runtimeBuildStatus.equals("in_progress", ignoreCase = true)
                ) {
                    Toast.makeText(context, "APK packaging started, checking status...", Toast.LENGTH_SHORT).show()
                    repeat(30) {
                        delay(4000)
                        val synced =
                            runtimePackagingService
                                .syncRuntimePackaging(savedApp)
                                .getOrElse { return@repeat }
                        if (synced != savedApp) {
                            savedApp = repository.upsertSavedApp(synced) ?: synced
                            updateTagRuntime(savedApp)
                        }
                        when (savedApp.runtimeBuildStatus.trim().lowercase()) {
                            "success" -> {
                                if (!tryDownloadCurrentArtifact()) {
                                    Toast.makeText(context, "APK artifact URL is missing.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            "failed", "disabled", "skipped" -> {
                                Toast.makeText(
                                    context,
                                    savedApp.runtimeBuildError?.ifBlank { null } ?: "APK packaging failed.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                        }
                    }
                    Toast.makeText(context, "APK packaging is still running. Please try download again soon.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                appDevDownloadBusyIds = appDevDownloadBusyIds - appId
            }
        }
    }

    fun sendMessage() {
        val trimmed = messageText.trim()
        val attachmentsSnapshot = imageAttachments
        if ((trimmed.isEmpty() && attachmentsSnapshot.isEmpty()) || isStreaming || sendInFlight) return
        val selectedToolSnapshot = selectedTool
        val webSearchConfigSnapshot = webSearchConfig
        val explicitWebSearchRequest = selectedToolSnapshot == "web"
        val explicitAppBuilderRequest = selectedToolSnapshot == "app_builder"
        val explicitAutoSoulRequest = selectedToolSnapshot == "autosoul"
        val confirmationMatched = pendingAppBuilderConfirmation && isAppBuilderConfirmationReply(trimmed)
        val weakAutoWebSearchIntent =
            selectedToolSnapshot == null &&
                !explicitAppBuilderRequest &&
                !explicitAutoSoulRequest &&
                !confirmationMatched &&
                attachmentsSnapshot.isEmpty() &&
                webSearchConfigSnapshot.autoSearchEnabled &&
                shouldAutoSearchForPrompt(trimmed)
        val weakAutoAppBuilderIntent =
            selectedToolSnapshot == null &&
                !explicitWebSearchRequest &&
                !weakAutoWebSearchIntent &&
                !explicitAppBuilderRequest &&
                !explicitAutoSoulRequest &&
                !confirmationMatched &&
                shouldEnableAppBuilderForPrompt(trimmed)

        stopRequestedByUser = false
        sendInFlight = true
        streamingJob = chatStreamingExecutionScope.launch {
            val nowMs = System.currentTimeMillis()
            val provisionalTitle = trimmed.lineSequence().firstOrNull().orEmpty().trim().take(24)
            val initialConversations = repository.conversationsFlow.first()

            var conversationId = effectiveConversationId?.trim().takeIf { !it.isNullOrBlank() }
                ?: repository.currentConversationIdFlow.first()?.trim().takeIf { !it.isNullOrBlank() }
            var conversation = conversationId?.let { cid -> initialConversations.firstOrNull { it.id == cid } }

            if (conversation == null) {
                val created = repository.createConversation()
                conversation = created
                conversationId = created.id
            }

            val safeConversationId = conversationId ?: return@launch
            preferredConversationId = safeConversationId
            preferredConversationSetAtMs = nowMs
            repository.setCurrentConversationId(safeConversationId)

            val encodedImages = mutableListOf<String>()
            for (attachment in attachmentsSnapshot) {
                val url = encodeImageAttachmentToDataUrl(context, attachment)
                if (url.isNullOrBlank()) {
                    repository.appendMessage(
                        safeConversationId,
                        Message(
                            role = "assistant",
                            content = "Failed to read the selected image. Please try again."
                        )
                    )
                    return@launch
                }
                encodedImages.add(url)
            }
            
            // Build attachments list instead of encoding into content
            val messageAttachments = encodedImages.map { url ->
                MessageAttachment(url = url)
            }
            
            // Only include text content, attachments will be displayed separately
            val userContent = trimmed.trim()
            val hasVisionInput = encodedImages.isNotEmpty()
            val userMessage = Message(
                role = "user",
                content = userContent,
                attachments = messageAttachments.takeIf { it.isNotEmpty() }
            )
            pendingMessages = pendingMessages + PendingMessage(safeConversationId, userMessage)
            messageText = ""
            imageAttachments = emptyList()
            scrollToBottomToken++
            repository.appendMessage(safeConversationId, userMessage)

            if (pendingAppBuilderConfirmation) {
                pendingAppBuilderConfirmation = false
            }
            if (weakAutoAppBuilderIntent) {
                pendingAppBuilderConfirmation = true
                repository.appendMessage(
                    safeConversationId,
                    Message(
                        role = "assistant",
                        content = buildAppBuilderConfirmationMessage(trimmed)
                    )
                )
                return@launch
            }
            if (confirmationMatched && selectedTool != "app_builder") {
                selectedTool = "app_builder"
            }

            // Update conversation title only using the latest persisted conversation to avoid wiping messages.
            val latestConversationForTitle =
                repository.conversationsFlow.first().firstOrNull { it.id == safeConversationId }
            if (
                latestConversationForTitle != null &&
                    (latestConversationForTitle.title.isBlank() || latestConversationForTitle.title == "New chat")
            ) {
                if (provisionalTitle.isNotBlank()) {
                    repository.updateConversationTitle(safeConversationId, provisionalTitle)
                }
            }

            if (explicitAutoSoulRequest) {
                val autoSoulResult =
                    handleAutoSoulInvocation(
                        repository = repository,
                        chatApiClient = chatApiClient,
                        context = context,
                        userMessage = userMessage
                    )
                repository.appendMessage(
                    safeConversationId,
                    Message(role = "assistant", content = autoSoulResult)
                )
                selectedTool = null
                return@launch
            }

            val latestDefaultChatModelId = repository.defaultChatModelIdFlow.first()
            if (latestDefaultChatModelId.isNullOrBlank()) {
                repository.appendMessage(
                    safeConversationId,
                    Message(
                        role = "assistant",
                        content = "Please configure Chat Model (required) in Settings → Default model before chatting."
                    )
                )
                return@launch
            }

            val effectiveModelId =
                if (hasVisionInput) {
                    repository.defaultVisionModelIdFlow.first()?.trim().takeIf { !it.isNullOrBlank() }
                        ?: latestDefaultChatModelId
                } else {
                    latestDefaultChatModelId
                }

            val allModels = repository.modelsFlow.first()
            val providerList = repository.providersFlow.first()
            var selectedModel =
                allModels.firstOrNull { it.id == effectiveModelId }
                    ?: allModels.firstOrNull { extractRemoteModelId(it.id) == effectiveModelId }
            if (selectedModel == null) {
                repository.appendMessage(
                    safeConversationId,
                    Message(
                        role = "assistant",
                        content = "Default model not found: $effectiveModelId. Enable or add it in Models, then re-select it in Settings → Default model."
                    )
                )
                return@launch
            }

            var provider = selectedModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } } ?: providerList.firstOrNull()
            if (hasVisionInput && provider != null && provider.isQwenCodeProvider()) {
                val qwenVisionModel =
                    allModels.firstOrNull { model ->
                        model.enabled &&
                            model.providerId == provider.id &&
                            isLikelyVisionModel(model)
                    }
                if (qwenVisionModel != null && qwenVisionModel.id != selectedModel.id) {
                    selectedModel = qwenVisionModel
                    provider = selectedModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } } ?: provider
                }
            }
            if (provider == null || provider.apiUrl.isBlank() || provider.apiKey.isBlank()) {
                repository.appendMessage(
                    safeConversationId,
                    Message(
                        role = "assistant",
                        content = "Please add a provider in Settings → Model services, and fill in API URL and API Key."
                    )
                )
                return@launch
            }

            val systemMessage = run {
                val latestNickname = repository.personalNicknameFlow.first().trim()
                val latestInstructions = repository.customInstructionsFlow.first().trim()
                val latestMemories =
                    repository.memoriesFlow.first()
                        .map { it.content.trim() }
                        .filter { it.isNotBlank() }
                        .take(12)
                val content = buildString {
                    if (latestNickname.isNotBlank()) {
                        append("Nickname: ")
                        append(latestNickname)
                    }
                    if (latestInstructions.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(latestInstructions)
                    }
                    if (latestMemories.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Memories:\n")
                        latestMemories.forEach { memory ->
                            append("- ")
                            append(memory)
                            append('\n')
                        }
                    }
                }.trim()
                if (content.isBlank()) null else Message(role = "system", content = content)
            }

            // 获取最新的消息列表
            val refreshedConversations = repository.conversationsFlow.first()
            val latestConversation = refreshedConversations.firstOrNull { it.id == safeConversationId }
            val currentMessages = latestConversation?.messages.orEmpty()

            // 检查是否为图片生成请求
            val isImageGeneration = selectedTool == "image"

            // 流式输出
            val assistantMessage = Message(role = "assistant", content = "")
            var assistantTags = emptyList<MessageTag>()
            val mcpTagAnchors = mutableListOf<McpTagAnchor>()
            var latestAssistantContent = ""
            var latestAssistantReasoning: String? = null
            var dropPendingAssistantOnExit = false
            pendingMessages = pendingMessages + PendingMessage(safeConversationId, assistantMessage)
            isStreaming = true
            streamingMessageId = assistantMessage.id
            streamingConversationId = safeConversationId
            streamingThinkingActive = false
            scrollToBottomToken++

            fun updateAssistantPending(update: (Message) -> Message) {
                pendingMessages =
                    pendingMessages.map { pending ->
                        if (pending.conversationId == safeConversationId && pending.message.id == assistantMessage.id) {
                            pending.copy(message = update(pending.message))
                        } else {
                            pending
                        }
                    }
            }

            fun updateAssistantContent(content: String, reasoning: String?) {
                val normalizedReasoning = reasoning?.trim()?.ifBlank { null }
                latestAssistantContent = content
                latestAssistantReasoning = normalizedReasoning
                val contentWithMarkers = insertMcpTagMarkers(content, mcpTagAnchors)
                updateAssistantPending { it.copy(content = contentWithMarkers, reasoning = normalizedReasoning) }
            }

            fun appendAssistantTag(tag: MessageTag) {
                assistantTags = assistantTags + tag
                if (tag.kind == "mcp" || tag.kind == "app_dev") {
                    mcpTagAnchors += McpTagAnchor(tag.id, latestAssistantContent.length)
                }
                val contentWithMarkers = insertMcpTagMarkers(latestAssistantContent, mcpTagAnchors)
                updateAssistantPending { it.copy(content = contentWithMarkers, tags = assistantTags) }
            }

            fun updateAssistantTag(tagId: String, update: (MessageTag) -> MessageTag) {
                assistantTags =
                    assistantTags.map { tag ->
                        if (tag.id == tagId) update(tag) else tag
                    }
                updateAssistantPending { it.copy(tags = assistantTags) }
            }

            try {
                val resolvedProvider = providerAuthManager.ensureValidProvider(provider)
                if (isImageGeneration) {
                    // 图片生成流程
                    handleImageGeneration(
                        repository = repository,
                        chatApiClient = chatApiClient,
                        safeConversationId = safeConversationId,
                        userPrompt = trimmed,
                        assistantMessage = assistantMessage,
                        updateAssistantContent = { updateAssistantContent(it, null) }
                    )
                } else {
                    // 普通聊天流程
                    val selectedMcpServerIdsSnapshot = selectedMcpServerIds
                    val mcpAutoEnabled = selectedMcpServerIdsSnapshot.isNotEmpty()
                    val explicitAppBuilder = selectedTool == "app_builder"
                    val useWebSearch = explicitWebSearchRequest || weakAutoWebSearchIntent
                    val canUseAppBuilder = explicitAppBuilder
                    val canUseAutoSoulTool =
                        repository.defaultAutoSoulModelIdFlow.first()
                            ?.trim()
                            ?.isNotBlank() == true
                    val configuredAppBuilderModel =
                        defaultAppBuilderModelId?.trim()?.takeIf { it.isNotBlank() }?.let { key ->
                            allModels.firstOrNull { it.id == key }
                                ?: allModels.firstOrNull { extractRemoteModelId(it.id) == key }
                        }
                    val configuredAppBuilderProvider =
                        configuredAppBuilderModel?.providerId?.let { pid ->
                            providerList.firstOrNull { it.id == pid }
                        }
                    if (explicitAppBuilder && configuredAppBuilderModel == null) {
                        val msg = "App Development model is not configured. Set it in Settings → Default model."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }
                    if (
                        explicitAppBuilder &&
                            (configuredAppBuilderProvider == null ||
                                configuredAppBuilderProvider.apiUrl.isBlank() ||
                                configuredAppBuilderProvider.apiKey.isBlank())
                    ) {
                        val msg = "Configured App Development model provider is not configured."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }
                    val webSearchResult =
                        if (useWebSearch) {
                            chatApiClient.webSearch(trimmed, webSearchConfigSnapshot)
                        } else {
                            null
                        }
                    if (explicitWebSearchRequest && webSearchResult?.isFailure == true) {
                        val reason =
                            webSearchResult.exceptionOrNull()?.message?.trim()?.takeIf { it.isNotBlank() }
                                ?: "Unknown error"
                        val msg = "Web search failed: $reason. Configure it in Settings → Search."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }
                    val webContextMessage =
                        webSearchResult
                            ?.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { content ->
                                Message(
                                    role = "system",
                                    content =
                                        "Use the following web search results as reference. Prefer these sources for factual/real-time questions.\n\n$content"
                                )
                            }

                    val enabledServers =
                        if (useWebSearch) {
                            emptyList<McpConfig>()
                        } else {
                            repository.mcpListFlow.first().filter { it.enabled }
                        }

                    if (mcpAutoEnabled && enabledServers.isEmpty()) {
                        val msg = "No MCP servers enabled. Configure one in Settings → MCP Tools."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }

                    val serversWithTools =
                        enabledServers.map { server ->
                            if (server.tools.isNotEmpty()) return@map server
                            val fetched = mcpClient.fetchTools(server).getOrNull().orEmpty()
                            if (fetched.isNotEmpty()) {
                                repository.updateMcpTools(server.id, fetched)
                            }
                            server.copy(tools = fetched)
                        }
                    val scopedMcpServers =
                        if (mcpAutoEnabled) {
                            serversWithTools.filter { server ->
                                selectedMcpServerIdsSnapshot.contains(server.id)
                            }
                        } else {
                            emptyList()
                        }
                    val availableMcpServers = scopedMcpServers.filter { it.tools.isNotEmpty() }
                    val canUseMcp = mcpAutoEnabled && availableMcpServers.isNotEmpty()
                    val canUseAnyTool = canUseMcp || canUseAppBuilder || canUseAutoSoulTool

                    if (mcpAutoEnabled && !canUseMcp) {
                        val msg =
                            if (selectedMcpServerIdsSnapshot.isNotEmpty()) {
                                "No selected MCP servers available. Re-select MCP providers."
                            } else {
                                "No MCP tools available. Sync tools in MCP Tools first."
                            }
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }

                    val prettyGson = GsonBuilder().setPrettyPrinting().create()
                    val serversById = availableMcpServers.associateBy { it.id }
                    val maxRounds =
                        when {
                            !canUseAnyTool -> 1
                            canUseMcp -> 6
                            canUseAppBuilder || canUseAutoSoulTool -> 4
                            else -> 4
                        }
                    val maxCallsPerRound =
                        when {
                            canUseMcp -> 4
                            canUseAppBuilder || canUseAutoSoulTool -> 2
                            else -> 3
                        }
                    val modelId = extractRemoteModelId(selectedModel.id)

                    val baseMessages = mutableListOf<Message>().apply {
                        if (systemMessage != null) add(systemMessage)
                        if (webContextMessage != null) add(webContextMessage)
                        addAll(
                            currentMessages.map { msg ->
                                if (msg.role == "assistant") {
                                    msg.copy(content = stripMcpTagMarkers(msg.content))
                                } else {
                                    msg
                                }
                            }
                        )
                        if (currentMessages.lastOrNull()?.id != userMessage.id) add(userMessage)
                    }

                    val visibleContent = StringBuilder()
                    val thinkingContent = StringBuilder()
                    var autoSoulInvokedInThisTask = false

                    fun updateAssistantFromCombined(roundVisible: String, roundThinking: String) {
                        val mergedVisible = mergeTextSections(visibleContent.toString(), roundVisible)
                        val mergedThinking = mergeTextSections(thinkingContent.toString(), roundThinking).trim()
                        updateAssistantContent(mergedVisible, mergedThinking.ifBlank { null })
                    }

                    fun appendRoundToCombined(roundVisible: String, roundThinking: String) {
                        appendTextSection(visibleContent, roundVisible)
                        appendTextSection(thinkingContent, roundThinking)
                    }

                    var roundIndex = 1
                    while (roundIndex <= maxRounds) {
                        val savedAppsSnapshot = repository.savedAppsFlow.first()
                        val mcpInstruction =
                            if (canUseMcp) {
                                buildMcpToolCallInstruction(
                                    servers = availableMcpServers,
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound
                                )
                            } else {
                                null
                            }
                        val appBuilderInstruction =
                            if (canUseAppBuilder) {
                                buildAppDeveloperToolInstruction(
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound,
                                    savedApps = savedAppsSnapshot
                                )
                            } else {
                                null
                            }
                        val autoSoulInstruction =
                            if (canUseAutoSoulTool) {
                                buildAutoSoulToolInstruction(
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound,
                                    alreadyInvoked = autoSoulInvokedInThisTask
                                )
                            } else {
                                null
                            }

                        val requestMessages = buildList {
                            if (!appBuilderInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = appBuilderInstruction))
                            }
                            if (!autoSoulInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = autoSoulInstruction))
                            }
                            if (!mcpInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = mcpInstruction))
                            }
                            addAll(baseMessages)
                        }

                        val roundVisibleRaw = StringBuilder()
                        val roundThinkingRaw = StringBuilder()
                        val streamedCallBlocks = mutableListOf<String>()
                        var streamHasReadyValidCall = false
                        val inlineCallRegex = Regex("(?is)<(?:mcp_call|tool_call)\\b[^>]*>(.*?)</(?:mcp_call|tool_call)>")
                        val inlineCallStartRegex = Regex("(?is)<(?:mcp_call|tool_call)\\b")
                        var inlineCallTail = ""
                        var thinkingInlineCallTail = ""
                        var stoppedForToolCall = false
                        var inThink = false
                        var remainder = ""
                        val keepTail = 12
                        var lastUiUpdateMs = 0L
                        var lastThinkingSignalAtMs = 0L

                        fun captureStreamedCallPayload(payload: String) {
                            val cleaned = payload.trim()
                            if (cleaned.isBlank()) return
                            streamedCallBlocks += cleaned
                            if (!streamHasReadyValidCall && parseMcpToolCallsPayload(cleaned).isNotEmpty()) {
                                streamHasReadyValidCall = true
                            }
                        }

                        fun appendVisibleWithInlineCallExtraction(text: String, flush: Boolean = false) {
                            if (text.isEmpty() && !flush) return
                            var source = inlineCallTail + text
                            inlineCallTail = ""
                            if (source.isEmpty()) return

                            var cursor = 0
                            inlineCallRegex.findAll(source).forEach { match ->
                                if (match.range.first > cursor) {
                                    roundVisibleRaw.append(source.substring(cursor, match.range.first))
                                }
                                val payload = match.groupValues.getOrNull(1)?.trim().orEmpty()
                                if (payload.isNotBlank()) {
                                    captureStreamedCallPayload(payload)
                                }
                                cursor = match.range.last + 1
                            }

                            if (cursor >= source.length) return
                            val remaining = source.substring(cursor)
                            val openIdx = inlineCallStartRegex.find(remaining)?.range?.first ?: -1
                            if (openIdx >= 0) {
                                if (openIdx > 0) {
                                    roundVisibleRaw.append(remaining.substring(0, openIdx))
                                }
                                val tailCandidate = remaining.substring(openIdx)
                                if (flush) {
                                    roundVisibleRaw.append(tailCandidate)
                                } else {
                                    inlineCallTail = tailCandidate
                                }
                            } else if (flush) {
                                roundVisibleRaw.append(remaining)
                            } else {
                                val safeTailLen = 24
                                if (remaining.length > safeTailLen) {
                                    roundVisibleRaw.append(remaining.dropLast(safeTailLen))
                                    inlineCallTail = remaining.takeLast(safeTailLen)
                                } else {
                                    inlineCallTail = remaining
                                }
                            }
                        }

                        fun appendThinkingWithInlineCallExtraction(text: String, flush: Boolean = false) {
                            if (text.isEmpty() && !flush) return
                            var source = thinkingInlineCallTail + text
                            thinkingInlineCallTail = ""
                            if (source.isEmpty()) return

                            var cursor = 0
                            inlineCallRegex.findAll(source).forEach { match ->
                                if (match.range.first > cursor) {
                                    roundThinkingRaw.append(source.substring(cursor, match.range.first))
                                }
                                val payload = match.groupValues.getOrNull(1)?.trim().orEmpty()
                                if (payload.isNotBlank()) {
                                    captureStreamedCallPayload(payload)
                                }
                                cursor = match.range.last + 1
                            }

                            if (cursor >= source.length) return
                            val remaining = source.substring(cursor)
                            val openIdx = inlineCallStartRegex.find(remaining)?.range?.first ?: -1
                            if (openIdx >= 0) {
                                if (openIdx > 0) {
                                    roundThinkingRaw.append(remaining.substring(0, openIdx))
                                }
                                val tailCandidate = remaining.substring(openIdx)
                                if (flush) {
                                    roundThinkingRaw.append(tailCandidate)
                                } else {
                                    thinkingInlineCallTail = tailCandidate
                                }
                            } else if (flush) {
                                roundThinkingRaw.append(remaining)
                            } else {
                                val safeTailLen = 24
                                if (remaining.length > safeTailLen) {
                                    roundThinkingRaw.append(remaining.dropLast(safeTailLen))
                                    thinkingInlineCallTail = remaining.takeLast(safeTailLen)
                                } else {
                                    thinkingInlineCallTail = remaining
                                }
                            }
                        }

                        fun appendWithThinkExtraction(input: String) {
                            if (input.isEmpty()) return
                            var source = remainder + input
                            remainder = ""
                            var cursor = 0

                            while (cursor < source.length) {
                                if (!inThink) {
                                    val idxThink = source.indexOf("<think>", cursor, ignoreCase = true)
                                    val idxThinking = source.indexOf("<thinking>", cursor, ignoreCase = true)
                                    val next =
                                        when {
                                            idxThink < 0 -> idxThinking
                                            idxThinking < 0 -> idxThink
                                            else -> minOf(idxThink, idxThinking)
                                        }

                                    if (next < 0) {
                                        val safeEnd = maxOf(cursor, source.length - keepTail)
                                        if (safeEnd > cursor) {
                                            appendVisibleWithInlineCallExtraction(source.substring(cursor, safeEnd))
                                        }
                                        remainder = source.substring(safeEnd)
                                        return
                                    }

                                    if (next > cursor) {
                                        appendVisibleWithInlineCallExtraction(source.substring(cursor, next))
                                    }

                                    inThink = true
                                    cursor =
                                        if (idxThinking == next) {
                                            next + "<thinking>".length
                                        } else {
                                            next + "<think>".length
                                        }
                                } else {
                                    val idxEndThink = source.indexOf("</think>", cursor, ignoreCase = true)
                                    val idxEndThinking = source.indexOf("</thinking>", cursor, ignoreCase = true)
                                    val next =
                                        when {
                                            idxEndThink < 0 -> idxEndThinking
                                            idxEndThinking < 0 -> idxEndThink
                                            else -> minOf(idxEndThink, idxEndThinking)
                                        }

                                    if (next < 0) {
                                        val safeEnd = maxOf(cursor, source.length - keepTail)
                                        if (safeEnd > cursor) {
                                            appendThinkingWithInlineCallExtraction(source.substring(cursor, safeEnd))
                                        }
                                        remainder = source.substring(safeEnd)
                                        return
                                    }

                                    if (next > cursor) {
                                        appendThinkingWithInlineCallExtraction(source.substring(cursor, next))
                                    }

                                    inThink = false
                                    cursor =
                                        if (idxEndThinking == next) {
                                            next + "</thinking>".length
                                        } else {
                                            next + "</think>".length
                                        }
                                }
                            }
                        }

                        chatApiClient.chatCompletionsStream(
                            provider = resolvedProvider,
                            modelId = modelId,
                            messages = requestMessages,
                            extraHeaders = selectedModel.headers,
                            reasoningEffort = if (chatThinkingEnabled) selectedModel.reasoningEffort else "none",
                            conversationId = safeConversationId
                        ).takeWhile { delta ->
                            val now = System.currentTimeMillis()
                            var sawThinkingSignal = false
                            delta.reasoning?.takeIf { it.isNotBlank() }?.let {
                                appendThinkingWithInlineCallExtraction(it)
                                sawThinkingSignal = true
                            }
                            delta.content?.let { appendWithThinkExtraction(it) }
                            if (inThink) {
                                sawThinkingSignal = true
                            }
                            if (sawThinkingSignal) {
                                lastThinkingSignalAtMs = now
                            }
                            val thinkingActiveNow = (inThink || (now - lastThinkingSignalAtMs) <= 650L)
                            if (streamingThinkingActive != thinkingActiveNow) {
                                streamingThinkingActive = thinkingActiveNow
                            }

                            val shouldUpdate =
                                now - lastUiUpdateMs >= 33L || (roundVisibleRaw.length + roundThinkingRaw.length) % 120 == 0
                            if (shouldUpdate) {
                                updateAssistantFromCombined(
                                    roundVisible = roundVisibleRaw.toString(),
                                    roundThinking = roundThinkingRaw.toString()
                                )
                                lastUiUpdateMs = now
                            }

                            val hasOpenInlineTagTail =
                                inlineCallStartRegex.containsMatchIn(inlineCallTail) ||
                                    inlineCallStartRegex.containsMatchIn(thinkingInlineCallTail)
                            val hasReadyValidCall =
                                canUseMcp &&
                                    streamHasReadyValidCall &&
                                    !hasOpenInlineTagTail
                            if (hasReadyValidCall) {
                                stoppedForToolCall = true
                            }
                            !hasReadyValidCall
                        }.collect()

                        if (remainder.isNotEmpty()) {
                            if (inThink) {
                                if (!stoppedForToolCall) {
                                    appendThinkingWithInlineCallExtraction(remainder, flush = true)
                                }
                            } else if (!stoppedForToolCall) {
                                appendVisibleWithInlineCallExtraction(remainder, flush = true)
                            }
                            remainder = ""
                        }
                        if (!stoppedForToolCall) {
                            appendVisibleWithInlineCallExtraction("", flush = true)
                            appendThinkingWithInlineCallExtraction("", flush = true)
                        }

                        val thinkingSplit = splitThinkingFromContent(roundVisibleRaw.toString())
                        var roundVisible = thinkingSplit.visible
                        var roundThinking = mergeTextSections(roundThinkingRaw.toString(), thinkingSplit.thinking)

                        val visibleCallBlocks = extractInlineMcpCallBlocks(roundVisible)
                        roundVisible = visibleCallBlocks.visibleText.trim()
                        val thinkingCallBlocks = extractInlineMcpCallBlocks(roundThinking)
                        roundThinking = thinkingCallBlocks.visibleText.trim()

                        val allRawCallBlocks = streamedCallBlocks + visibleCallBlocks.blocks + thinkingCallBlocks.blocks
                        val hadRawCallBlocks = allRawCallBlocks.any { it.isNotBlank() }
                        val parseFailedBlocks = mutableListOf<String>()
                        val roundSeenSignatures = linkedSetOf<String>()
                        val parsedCallsRaw =
                            allRawCallBlocks
                                .flatMap { block ->
                                    val parsed = parseMcpToolCallsPayload(block)
                                    if (parsed.isEmpty()) {
                                        parseFailedBlocks += block
                                    }
                                    parsed
                                }
                                .map { call ->
                                    call.copy(
                                        serverId = call.serverId.trim(),
                                        toolName = call.toolName.trim(),
                                        arguments =
                                            call.arguments.mapNotNull { (k, v) ->
                                                val key = k.trim()
                                                if (key.isBlank()) return@mapNotNull null
                                                key to v
                                            }.toMap()
                                    )
                                }
                                .filter { it.toolName.isNotBlank() }
                                .filter { call ->
                                    roundSeenSignatures.add(buildMcpCallSignature(call))
                                }
                                .take(maxCallsPerRound)
                        var droppedAutoSoulCallByPolicy = false
                        var keptAutoSoulInRound = false
                        val parsedCalls =
                            parsedCallsRaw.filter { call ->
                                if (!isBuiltInAutoSoulCall(call)) {
                                    return@filter true
                                }
                                if (autoSoulInvokedInThisTask) {
                                    droppedAutoSoulCallByPolicy = true
                                    return@filter false
                                }
                                if (keptAutoSoulInRound) {
                                    droppedAutoSoulCallByPolicy = true
                                    return@filter false
                                }
                                keptAutoSoulInRound = true
                                true
                            }

                        updateAssistantFromCombined(roundVisible = roundVisible, roundThinking = roundThinking)
                        appendRoundToCombined(roundVisible = roundVisible, roundThinking = roundThinking)

                        parseFailedBlocks.take(2).forEach { rawBlock ->
                            val rawPreview = rawBlock.trim().take(1800)
                            appendAssistantTag(
                                MessageTag(
                                    kind = "mcp",
                                    title = "Tool",
                                    content = buildMcpTagDetailContent(
                                        round = roundIndex,
                                        serverName = null,
                                        toolName = "unknown",
                                        argumentsJson = rawPreview,
                                        statusText = "Failed",
                                        attempts = 1,
                                        elapsedMs = null,
                                        resultText = null,
                                        errorText = "Invalid mcp_call payload."
                                    ),
                                    status = "error"
                                )
                            )
                        }

                        if (roundVisible.isNotBlank()) {
                            baseMessages.add(Message(role = "assistant", content = roundVisible))
                        }

                        if (!canUseAnyTool) {
                            break
                        }
                        if (parsedCalls.isEmpty()) {
                            if (droppedAutoSoulCallByPolicy && roundIndex < maxRounds) {
                                baseMessages.add(
                                    Message(
                                        role = "system",
                                        content = buildMcpRoundResultContext(
                                            roundIndex = roundIndex,
                                            summary =
                                                "- autosoul_agent was already invoked for this user task.\n" +
                                                    "- Do NOT call autosoul_agent again.\n" +
                                                    "- Continue with direct user-facing answer based on existing AutoSoul result."
                                        )
                                    )
                                )
                                roundIndex += 1
                                continue
                            }
                            if (hadRawCallBlocks && roundIndex < maxRounds) {
                                baseMessages.add(
                                    Message(
                                        role = "system",
                                        content = buildMcpRoundResultContext(
                                            roundIndex = roundIndex,
                                            summary =
                                                "- Failed to parse executable tool_call payload from the previous reply.\n" +
                                                    "- Keep the visible reply, then emit a valid JSON payload inside " +
                                                    "<tool_call>...</tool_call>."
                                        )
                                    )
                                )
                                roundIndex += 1
                                continue
                            }
                            break
                        }

                        val roundSummary = StringBuilder()
                        var processedCallCount = 0

                        parsedCalls.forEach { call ->
                            processedCallCount += 1
                            val args =
                                call.arguments
                                    .mapNotNull { (k, v) ->
                                        val key = k.trim()
                                        if (key.isBlank()) return@mapNotNull null
                                        val value = v ?: return@mapNotNull null
                                        key to value
                                    }
                                    .toMap()
                            val argsJson = prettyGson.toJson(args)

                            if (isBuiltInAutoSoulCall(call)) {
                                autoSoulInvokedInThisTask = true
                                val taskPrompt = resolveAutoSoulTaskPrompt(args, trimmed)
                                val pendingTag =
                                    MessageTag(
                                        kind = "mcp",
                                        title = "AutoSoul",
                                        content = buildMcpTagDetailContent(
                                            round = roundIndex,
                                            serverName = "AutoSoul",
                                            toolName = call.toolName.ifBlank { "autosoul_agent" },
                                            argumentsJson = argsJson,
                                            statusText = "Running...",
                                            attempts = 1,
                                            elapsedMs = null,
                                            resultText = null,
                                            errorText = null
                                        ),
                                        status = "running"
                                    )
                                appendAssistantTag(pendingTag)

                                val startedAt = System.currentTimeMillis()
                                val execution =
                                    executeAutoSoulTask(
                                        repository = repository,
                                        chatApiClient = chatApiClient,
                                        context = context,
                                        taskPrompt = taskPrompt,
                                        attachments = userMessage.attachments
                                    ).getOrElse { error ->
                                        val errorText =
                                            error.message?.trim().orEmpty().ifBlank { "AutoSoul execution failed." }
                                        val tagContent = buildMcpTagDetailContent(
                                            round = roundIndex,
                                            serverName = "AutoSoul",
                                            toolName = call.toolName.ifBlank { "autosoul_agent" },
                                            argumentsJson = argsJson,
                                            statusText = "Failed",
                                            attempts = 1,
                                            elapsedMs = System.currentTimeMillis() - startedAt,
                                            resultText = null,
                                            errorText = errorText
                                        )
                                        updateAssistantTag(pendingTag.id) {
                                            it.copy(content = tagContent, status = "error")
                                        }
                                        roundSummary.append("- autosoul_agent: failed - ")
                                        roundSummary.append(errorText.take(300))
                                        roundSummary.append('\n')
                                        return@forEach
                                    }

                                val elapsedMs = System.currentTimeMillis() - startedAt
                                val resultText = buildAutoSoulToolResultText(execution)
                                val tagContent = buildMcpTagDetailContent(
                                    round = roundIndex,
                                    serverName = "AutoSoul",
                                    toolName = call.toolName.ifBlank { "autosoul_agent" },
                                    argumentsJson = argsJson,
                                    statusText = if (execution.success) "Completed" else "Failed",
                                    attempts = 1,
                                    elapsedMs = elapsedMs,
                                    resultText = resultText,
                                    errorText = if (execution.success) null else execution.error
                                )
                                updateAssistantTag(pendingTag.id) {
                                    it.copy(content = tagContent, status = if (execution.success) "success" else "error")
                                }
                                roundSummary.append("- autosoul_agent: ")
                                roundSummary.append(if (execution.success) "success" else "failed")
                                roundSummary.append(" | ")
                                roundSummary.append(resultText.replace('\n', ' ').take(560))
                                roundSummary.append('\n')
                                return@forEach
                            }

                            if (isBuiltInAppDeveloperCall(call)) {
                                val parsedSpec = parseAppDevToolSpec(args)
                                val savedAppsSnapshot = repository.savedAppsFlow.first()
                                val targetSavedApp = parsedSpec?.let { resolveExistingSavedApp(it, savedAppsSnapshot) }
                                val rawName =
                                    normalizeAppDisplayName(
                                        (args["name"] as? String)?.trim().orEmpty()
                                            .ifBlank { parsedSpec?.name.orEmpty() }
                                            .ifBlank { targetSavedApp?.name.orEmpty() }
                                            .ifBlank { "App development" }
                                    )
                                val mode = parsedSpec?.mode ?: "create"
                                val pendingPayload =
                                    AppDevTagPayload(
                                        name = rawName,
                                        subtitle = if (mode == "edit") "Updating app" else "Developing app",
                                        description = parsedSpec?.description?.takeIf { it.isNotBlank() }
                                            ?: targetSavedApp?.description.orEmpty(),
                                        appIcon = parsedSpec?.appIcon ?: inferLucideIconFromSignal(rawName, parsedSpec?.description.orEmpty()),
                                        style = parsedSpec?.style.orEmpty(),
                                        features = parsedSpec?.features.orEmpty(),
                                        progress = 8,
                                        status = "running",
                                        html = if (mode == "edit") targetSavedApp?.html.orEmpty() else "",
                                        error = null,
                                        sourceAppId = targetSavedApp?.id,
                                        mode = mode
                                    )
                                val pendingTag =
                                    MessageTag(
                                        kind = "app_dev",
                                        title = rawName,
                                        content = encodeAppDevTagPayload(pendingPayload),
                                        status = "running"
                                    )
                                appendAssistantTag(pendingTag)

                                if (!canUseAppBuilder) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "App builder is disabled for this request."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: disabled\n")
                                    return@forEach
                                }

                                if (parsedSpec == null) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error =
                                                "Invalid arguments. app_developer requires: name + description + app_icon."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: invalid arguments\n")
                                    return@forEach
                                }
                                if (parsedSpec.mode == "edit" && targetSavedApp == null) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "Target saved app was not found. Provide targetAppId or exact targetAppName."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: target app missing\n")
                                    return@forEach
                                }

                                val preferredAppModelKey =
                                    defaultAppBuilderModelId?.trim()?.takeIf { it.isNotBlank() }
                                if (preferredAppModelKey.isNullOrBlank()) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "App Development model is not configured. Set it in Settings → Default model."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: model not configured\n")
                                    return@forEach
                                }

                                val appModel =
                                    allModels.firstOrNull { it.id == preferredAppModelKey }
                                        ?: allModels.firstOrNull { extractRemoteModelId(it.id) == preferredAppModelKey }
                                if (appModel == null) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "Configured App Development model was not found. Re-select it in Settings → Default model."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: model missing\n")
                                    return@forEach
                                }

                                val appProviderRaw =
                                    appModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } }

                                if (appProviderRaw == null || appProviderRaw.apiUrl.isBlank() || appProviderRaw.apiKey.isBlank()) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "Configured App Development model provider is not configured."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: provider unavailable\n")
                                    return@forEach
                                }

                                val appProvider = runCatching { providerAuthManager.ensureValidProvider(appProviderRaw) }.getOrElse { error ->
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = error.message?.trim().orEmpty().ifBlank { "Provider auth failed." }
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: auth failed\n")
                                    return@forEach
                                }

                                val startedAt = System.currentTimeMillis()
                                var streamedProgress = pendingPayload.progress
                                var lastProgressUpdateAtMs = 0L
                                var lastDraftUpdateAtMs = 0L
                                fun updateAppDevDraft(rawDraft: String) {
                                    val draft = normalizeGeneratedHtmlDraft(rawDraft)
                                    if (draft.length < 40) return
                                    val now = System.currentTimeMillis()
                                    if (now - lastDraftUpdateAtMs < 170L) return
                                    lastDraftUpdateAtMs = now
                                    updateAssistantTag(pendingTag.id) { current ->
                                        val existing =
                                            parseAppDevTagPayload(
                                                content = current.content,
                                                fallbackName = parsedSpec.name.ifBlank { rawName },
                                                fallbackStatus = "running"
                                            )
                                        val updated =
                                            existing.copy(
                                                html = draft.take(120_000),
                                                status = "running",
                                                error = null,
                                                mode = parsedSpec.mode,
                                                sourceAppId = targetSavedApp?.id ?: existing.sourceAppId
                                            )
                                        current.copy(content = encodeAppDevTagPayload(updated), status = "running")
                                    }
                                }
                                fun updateAppDevProgress(progressValue: Int) {
                                    val incoming = progressValue.coerceIn(6, 98)
                                    if (incoming <= streamedProgress) return
                                    val normalized = incoming
                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressUpdateAtMs < 90L && normalized < 97) return
                                    streamedProgress = normalized
                                    lastProgressUpdateAtMs = now
                                    updateAssistantTag(pendingTag.id) { current ->
                                        val existing =
                                            parseAppDevTagPayload(
                                                content = current.content,
                                                fallbackName = parsedSpec.name,
                                                fallbackStatus = "running"
                                            )
                                        val updated =
                                            existing.copy(
                                                progress = normalized,
                                                status = "running",
                                                error = null,
                                                mode = parsedSpec.mode,
                                                sourceAppId = targetSavedApp?.id ?: existing.sourceAppId
                                            )
                                        current.copy(
                                            content = encodeAppDevTagPayload(updated),
                                            status = "running"
                                        )
                                    }
                                }
                                val htmlResult =
                                    runCatching {
                                        if (parsedSpec.mode == "edit") {
                                            reviseHtmlAppFromPrompt(
                                                chatApiClient = chatApiClient,
                                                provider = appProvider,
                                                modelId = extractRemoteModelId(appModel.id),
                                                extraHeaders = appModel.headers,
                                                currentHtml = targetSavedApp?.html.orEmpty(),
                                                requestText = parsedSpec.editRequest.orEmpty(),
                                                spec = parsedSpec,
                                                sourceUserPrompt = trimmed,
                                                rawToolArgsJson = argsJson,
                                                onProgress = ::updateAppDevProgress,
                                                onDraftHtml = ::updateAppDevDraft
                                            )
                                        } else {
                                            generateHtmlAppFromSpec(
                                                chatApiClient = chatApiClient,
                                                provider = appProvider,
                                                modelId = extractRemoteModelId(appModel.id),
                                                extraHeaders = appModel.headers,
                                                spec = parsedSpec,
                                                sourceUserPrompt = trimmed,
                                                rawToolArgsJson = argsJson,
                                                onProgress = ::updateAppDevProgress,
                                                onDraftHtml = ::updateAppDevDraft
                                            )
                                        }
                                    }
                                val elapsedMs = System.currentTimeMillis() - startedAt
                                val html = htmlResult.getOrNull()
                                if (html != null) {
                                    val finalName =
                                        if (parsedSpec.mode == "edit") {
                                            normalizeAppDisplayName(
                                                parsedSpec.name.takeIf { it.isNotBlank() } ?: targetSavedApp?.name.orEmpty()
                                            )
                                        } else {
                                            parsedSpec.name
                                        }
                                    val finalSubtitle =
                                        if (parsedSpec.mode == "edit") {
                                            compactAppDescription(parsedSpec.editRequest.orEmpty(), "App updated")
                                        } else {
                                            compactAppDescription(parsedSpec.description, "HTML app ready")
                                        }
                                    val finalDescription =
                                        if (parsedSpec.mode == "edit") {
                                            compactAppDescription(
                                                parsedSpec.editRequest.orEmpty(),
                                                targetSavedApp?.description.orEmpty().ifBlank { "Updated HTML app" }
                                            )
                                        } else {
                                            compactAppDescription(parsedSpec.description, "HTML app")
                                        }
                                    val finalIcon =
                                        extractLucideIconName(parsedSpec.appIcon)
                                            ?: extractSavedAppIconNameFromRaw(parsedSpec.description, html)
                                            ?: inferLucideIconFromSignal(finalName, finalDescription)
                                    val finalHtml = ensureAppIconMetadataInHtml(html, finalIcon)
                                    val savedAppId = targetSavedApp?.id ?: java.util.UUID.randomUUID().toString()
                                    val baseSavedApp =
                                        SavedApp(
                                            id = savedAppId,
                                            sourceTagId = pendingTag.id,
                                            name = finalName,
                                            description = finalDescription,
                                            html = finalHtml,
                                            versionCode = targetSavedApp?.versionCode ?: 1,
                                            versionName = targetSavedApp?.versionName ?: "v1",
                                            createdAt = targetSavedApp?.createdAt ?: System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    val persistedApp =
                                        repository.upsertSavedApp(
                                            baseSavedApp,
                                            note = if (parsedSpec.mode == "edit") "AI edited app" else "AI generated app"
                                        ) ?: baseSavedApp
                                    val payload =
                                        pendingPayload.copy(
                                            name = finalName,
                                            subtitle = finalSubtitle,
                                            description = finalDescription,
                                            appIcon = finalIcon,
                                            style = parsedSpec.style.ifBlank { pendingPayload.style },
                                            features = if (parsedSpec.features.isNotEmpty()) parsedSpec.features else pendingPayload.features,
                                            progress = 100,
                                            status = "success",
                                            html = finalHtml,
                                            error = null,
                                            sourceAppId = persistedApp.id,
                                            mode = parsedSpec.mode,
                                            deployUrl = null,
                                            deployError = null,
                                            runtimeStatus = null,
                                            runtimeMessage = null,
                                            runtimeRunUrl = null,
                                            runtimeArtifactName = null,
                                            runtimeArtifactUrl = null
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(
                                            title = finalName,
                                            content = encodeAppDevTagPayload(payload),
                                            status = "success"
                                        )
                                    }

                                    roundSummary.append("- app_developer: ")
                                    roundSummary.append(if (parsedSpec.mode == "edit") "updated " else "generated ")
                                    roundSummary.append(finalName.take(80))
                                    roundSummary.append(" in ")
                                    roundSummary.append(formatElapsedDuration(elapsedMs))
                                    roundSummary.append('\n')
                                } else {
                                    val error = htmlResult.exceptionOrNull()
                                    updateAssistantTag(pendingTag.id) { current ->
                                        val existing =
                                            parseAppDevTagPayload(
                                                content = current.content,
                                                fallbackName = rawName,
                                                fallbackStatus = "running"
                                            )
                                        val payload =
                                            existing.copy(
                                                progress = 0,
                                                status = "error",
                                                error = error?.message?.trim().orEmpty().ifBlank { "App generation failed." },
                                                sourceAppId = targetSavedApp?.id ?: existing.sourceAppId,
                                                mode = parsedSpec.mode
                                            )
                                        current.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: failed\n")
                                }
                                return@forEach
                            }

                            fun resolveServer(): McpConfig? {
                                val serverId = call.serverId.trim()
                                if (serverId.isNotBlank()) {
                                    serversById[serverId]?.let { return it }
                                    availableMcpServers.firstOrNull {
                                        it.name.trim().equals(serverId, ignoreCase = true)
                                    }?.let { return it }
                                }
                                val candidates = availableMcpServers.filter { server ->
                                    server.tools.any { tool -> tool.name == call.toolName }
                                }
                                return if (candidates.size == 1) candidates.first() else null
                            }

                            val server = resolveServer()
                            val tagTitle = call.toolName.trim().ifBlank { "Tool" }
                            val pendingTag =
                                MessageTag(
                                    kind = "mcp",
                                    title = tagTitle,
                                    content = buildMcpTagDetailContent(
                                        round = roundIndex,
                                        serverName = server?.name,
                                        toolName = call.toolName,
                                        argumentsJson = argsJson,
                                        statusText = "Running...",
                                        attempts = 1,
                                        elapsedMs = null,
                                        resultText = null,
                                        errorText = null
                                    ),
                                    status = "running"
                                )
                            appendAssistantTag(pendingTag)

                            if (server == null) {
                                val tagContent = buildMcpTagDetailContent(
                                    round = roundIndex,
                                    serverName = null,
                                    toolName = call.toolName,
                                    argumentsJson = argsJson,
                                    statusText = "Failed",
                                    attempts = 1,
                                    elapsedMs = null,
                                    resultText = null,
                                    errorText = "MCP server not found for this tool call."
                                )
                                updateAssistantTag(pendingTag.id) {
                                    it.copy(content = tagContent, status = "error")
                                }
                                roundSummary.append("- ")
                                roundSummary.append(call.toolName)
                                roundSummary.append(": server unavailable\n")
                                return@forEach
                            }

                            val startedAt = System.currentTimeMillis()
                            val callResult =
                                mcpClient.callTool(
                                    config = server,
                                    toolCall = McpToolCall(toolName = call.toolName, arguments = args)
                                ).getOrElse(::toMcpFailureResult)
                            val elapsedMs = System.currentTimeMillis() - startedAt

                            val compactContent = callResult.content.trim().take(1800)
                            val finalError = (callResult.error ?: callResult.content).trim()
                            val apiError = if (callResult.success) null else extractExplicitApiError(finalError)
                            val hasExplicitApiError = !apiError.isNullOrBlank()
                            val tagContent = buildMcpTagDetailContent(
                                round = roundIndex,
                                serverName = server.name,
                                toolName = call.toolName,
                                argumentsJson = argsJson,
                                statusText = if (hasExplicitApiError) "Failed" else "Completed",
                                attempts = 1,
                                elapsedMs = elapsedMs,
                                resultText = if (callResult.success) compactContent.ifBlank { "{}" } else "{}",
                                errorText = apiError
                            )
                            updateAssistantTag(pendingTag.id) {
                                it.copy(
                                    title = call.toolName.trim().ifBlank { server.name },
                                    content = tagContent,
                                    status = if (hasExplicitApiError) "error" else "success"
                                )
                            }

                            roundSummary.append("- ")
                            roundSummary.append(server.name)
                            roundSummary.append("/")
                            roundSummary.append(call.toolName)
                            roundSummary.append(": ")
                            roundSummary.append(
                                when {
                                    callResult.success -> compactContent.ifBlank { "{}" }
                                    hasExplicitApiError -> "api error: $apiError"
                                    else -> "failed"
                                }.take(600)
                            )
                            roundSummary.append('\n')
                        }

                        if (processedCallCount == 0) {
                            break
                        }

                        baseMessages.add(
                            Message(
                                role = "system",
                                content = buildMcpRoundResultContext(roundIndex, roundSummary.toString())
                            )
                        )

                        roundIndex += 1
                    }

                    val finalContent = visibleContent.toString().trim()
                    val finalReasoning = thinkingContent.toString().trim().ifBlank { null }
                    val finalContentWithMarkers = insertMcpTagMarkers(finalContent, mcpTagAnchors)
                    updateAssistantContent(finalContent, finalReasoning)
                    if (finalContent.isNotBlank() || !finalReasoning.isNullOrBlank() || assistantTags.isNotEmpty()) {
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(
                                content = finalContentWithMarkers,
                                reasoning = finalReasoning,
                                tags = assistantTags.takeIf { it.isNotEmpty() }
                            )
                        )
                    }

                    if (finalContent.isNotBlank()) {
                        val chatModelId = extractRemoteModelId(selectedModel.id)
                        val chatExtraHeaders = selectedModel.headers
                        val userTextForMemory = trimmed
                        val assistantTextForMemory = finalContent
                        val assistantMessageIdForTags = assistantMessage.id

                        if (shouldAttemptMemoryExtraction(userTextForMemory)) {
                            scope.launch(Dispatchers.IO) {
                                val result =
                                    runCatching {
                                        extractMemoryCandidatesFromTurn(
                                            chatApiClient = chatApiClient,
                                            provider = resolvedProvider,
                                            modelId = chatModelId,
                                            userText = userTextForMemory,
                                            assistantText = assistantTextForMemory,
                                            extraHeaders = chatExtraHeaders
                                        )
                                    }

                                if (result.isFailure) return@launch

                                val candidates =
                                    filterMemoryCandidates(
                                        candidates = result.getOrDefault(emptyList()),
                                        userText = userTextForMemory
                                    )
                                if (candidates.isEmpty()) return@launch

                                val saved = mutableListOf<String>()
                                candidates.forEach { candidate ->
                                    repository.addMemory(candidate)?.let { added ->
                                        saved.add(added.content)
                                    }
                                }
                                if (saved.isEmpty()) return@launch

                                val detail =
                                    buildString {
                                        append("Saved memories:\n")
                                        saved.forEach { item ->
                                            append("- ")
                                            append(item)
                                            append('\n')
                                        }
                                    }.trim()

                                repository.appendMessageTag(
                                    conversationId = safeConversationId,
                                    messageId = assistantMessageIdForTags,
                                    tag = MessageTag(kind = "memory", title = "Memory", content = detail)
                                )
                            }
                        }

                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                val titleModelKey = repository.defaultTitleModelIdFlow.first()?.trim().orEmpty()
                                if (titleModelKey.isBlank()) return@runCatching

                                val modelsAll = repository.modelsFlow.first()
                                val titleModel =
                                    modelsAll.firstOrNull { it.id == titleModelKey }
                                        ?: modelsAll.firstOrNull { extractRemoteModelId(it.id) == titleModelKey }
                                        ?: return@runCatching

                                val providersAll = repository.providersFlow.first()
                                val titleProvider =
                                    titleModel.providerId?.let { pid -> providersAll.firstOrNull { it.id == pid } }
                                        ?: providersAll.firstOrNull()
                                        ?: return@runCatching

                                if (titleProvider.apiUrl.isBlank() || titleProvider.apiKey.isBlank()) return@runCatching

                                val validTitleProvider = providerAuthManager.ensureValidProvider(titleProvider)
                                val convo = repository.conversationsFlow.first().firstOrNull { it.id == safeConversationId }
                                    ?: return@runCatching

                                val currentTitle = convo.title.trim()
                                val shouldGenerateTitle =
                                    currentTitle.isBlank() ||
                                        currentTitle == "New chat" ||
                                        (provisionalTitle.isNotBlank() && currentTitle == provisionalTitle.trim())
                                if (!shouldGenerateTitle) return@runCatching

                                val transcript =
                                    buildConversationTranscript(
                                        messages = convo.messages,
                                        maxMessages = 12,
                                        maxCharsPerMessage = 420
                                    )
                                if (transcript.isBlank()) return@runCatching

                                val generatedTitle =
                                    generateConversationTitle(
                                        chatApiClient = chatApiClient,
                                        provider = validTitleProvider,
                                        modelId = extractRemoteModelId(titleModel.id),
                                        transcript = transcript,
                                        extraHeaders = titleModel.headers
                                    )?.trim().orEmpty()

                                val finalTitle = generatedTitle.trim().trim('"', '\'').take(48)
                                if (finalTitle.isBlank()) return@runCatching

                                repository.updateConversationTitle(safeConversationId, finalTitle)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (!stopRequestedByUser) throw e
                val partialContent = latestAssistantContent.trim()
                val partialReasoning = latestAssistantReasoning?.trim()?.ifBlank { null }
                val hasPartialOutput =
                    partialContent.isNotBlank() ||
                        !partialReasoning.isNullOrBlank() ||
                        assistantTags.isNotEmpty()
                val alreadyPersisted =
                    repository.conversationsFlow.first()
                        .firstOrNull { it.id == safeConversationId }
                        ?.messages
                        ?.any { it.id == assistantMessage.id } == true
                if (!alreadyPersisted && hasPartialOutput) {
                    val partialContentWithMarkers = insertMcpTagMarkers(partialContent, mcpTagAnchors)
                    repository.appendMessage(
                        safeConversationId,
                        assistantMessage.copy(
                            content = partialContentWithMarkers,
                            reasoning = partialReasoning,
                            tags = assistantTags.takeIf { it.isNotEmpty() }
                        )
                    )
                } else if (!alreadyPersisted) {
                    dropPendingAssistantOnExit = true
                }
            } catch (e: Exception) {
                val errorMsg = "Request failed: ${e.message ?: e.toString()}"
                updateAssistantContent(errorMsg, null)
                repository.appendMessage(safeConversationId, assistantMessage.copy(content = errorMsg, reasoning = null))
            } finally {
                if (dropPendingAssistantOnExit) {
                    pendingMessages =
                        pendingMessages.filterNot { pending ->
                            pending.conversationId == safeConversationId && pending.message.id == assistantMessage.id
                        }
                }
                sendInFlight = false
                isStreaming = false
                streamingMessageId = null
                streamingConversationId = null
                streamingThinkingActive = false
                streamingJob = null
                stopRequestedByUser = false
                selectedTool = null // 清除选中的工具
            }
        }
    }

    LaunchedEffect(pendingAppAutomationTask?.id, isStreaming) {
        val task = pendingAppAutomationTask ?: return@LaunchedEffect
        if (task.id == lastAutoDispatchedTaskId) return@LaunchedEffect
        if (isStreaming) return@LaunchedEffect
        val automationPrompt = buildPendingAppAutomationPrompt(task)
        lastAutoDispatchedTaskId = task.id
        repository.clearPendingAppAutomationTask()
        if (automationPrompt.isBlank()) return@LaunchedEffect
        showToolMenu = false
        selectedTool = "app_builder"
        messageText = automationPrompt
        sendMessage()
    }

    val displayName = nickname.takeIf { it.isNotBlank() } ?: "Kendall Williamson"
    val appWorkspaceActive = !appWorkspaceTagId.isNullOrBlank()

    LaunchedEffect(appWorkspaceActive) {
        if (appWorkspaceActive && drawerState.isOpen) {
            drawerState.close()
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible && showToolMenu) {
            showToolMenu = false
            toolMenuPage = ToolMenuPage.Tools
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !appWorkspaceActive,
        drawerContent = {
            SidebarContent(
                conversations = conversations,
                currentConversationId = effectiveConversationId,
                nickname = displayName,
                avatarUri = avatarUri,
                onClose = { scope.launch { drawerState.close() } },
                onNewChat = ::startNewChat,
                onConversationClick = { convo ->
                    scope.launch {
                        preferredConversationId = convo.id
                        preferredConversationSetAtMs = System.currentTimeMillis()
                        repository.setCurrentConversationId(convo.id)
                        drawerState.close()
                        scrollToBottomToken++
                    }
                },
                onDeleteConversation = { id ->
                    scope.launch {
                        repository.deleteConversation(id)
                        if (preferredConversationId == id) {
                            preferredConversationId = null
                        }
                    }
                },
                navController = navController
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.25f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatBackground)
        ) {
            // Chat content (behind the top bar), so messages can scroll into the fade region.
            if (localMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = listTopPadding, bottom = bottomContentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyChatState()
                }
            } else {
                val latestAssistantToolbarIds = remember(localMessages) {
                    localMessages
                        .asReversed()
                        .asSequence()
                        .filter { it.role == "assistant" }
                        .take(3)
                        .map { it.id }
                        .toSet()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = listTopPadding,
                        bottom = bottomContentPadding + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(localMessages, key = { _, item -> item.id }) { index, message ->
                        val showToolbar =
                            message.role == "assistant" && latestAssistantToolbarIds.contains(message.id)
                        MessageItem(
                            message = message,
                            conversationId = effectiveConversationId,
                            isStreaming = isStreaming
                                && streamingConversationId == effectiveConversationId
                                && message.id == streamingMessageId,
                            isThinkingActive = isStreaming
                                && streamingConversationId == effectiveConversationId
                                && message.id == streamingMessageId
                                && streamingThinkingActive,
                            showToolbar = showToolbar,
                            onShowReasoning = { reasoning ->
                                hideKeyboardIfNeeded(force = true)
                                showToolMenu = false
                                tagSheetMessageId = null
                                tagSheetTagId = null
                                thinkingSheetText = reasoning
                                showThinkingSheet = true
                            },
                            onShowTag = { messageId, tagId ->
                                hideKeyboardIfNeeded(force = true)
                                showToolMenu = false
                                showThinkingSheet = false
                                thinkingSheetText = null
                                val clickedTag =
                                    localMessages
                                        .firstOrNull { it.id == messageId }
                                        ?.tags
                                        .orEmpty()
                                        .firstOrNull { it.id == tagId }
                                if (clickedTag?.kind == "app_dev") {
                                    tagSheetMessageId = null
                                    tagSheetTagId = null
                                    appWorkspaceMessageId = messageId
                                    appWorkspaceTagId = tagId
                                } else {
                                    appWorkspaceMessageId = null
                                    appWorkspaceTagId = null
                                    tagSheetMessageId = messageId
                                    tagSheetTagId = tagId
                                }
                            },
                            onDownloadAppDevTag = { messageId, tag ->
                                hideKeyboardIfNeeded(force = true)
                                showToolMenu = false
                                handleAppDevTagDownload(
                                    conversationId = effectiveConversationId,
                                    messageId = messageId,
                                    tag = tag
                                )
                            },
                            userBubbleColor = accentPalette.bubbleColor,
                            userBubbleSecondaryColor = accentPalette.bubbleColorSecondary,
                            userBubbleTextColor = accentPalette.bubbleTextColor,
                            onEdit = { },
                            onDelete = { convoId, messageId ->
                                scope.launch { repository.deleteMessage(convoId, messageId) }
                            }
                        )
                    }
                }
            }

            // Top fade: start at the bottom of TopNavBar (blue line), fully hidden above (orange line).
            val topFadeHeight = 52.dp
            val topFadeTopPadding = maxOf(
                statusBarTopPadding,
                statusBarTopPadding + topBarHeightDp - topFadeHeight
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topFadeTopPadding)
                    .background(ChatBackground)
                    .zIndex(1f)
            )
            TopFadeScrim(
                color = ChatBackground,
                height = topFadeHeight,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topFadeTopPadding)
                    .zIndex(1f)
            )
            TopFadeScrim(
                color = ChatBackground.copy(alpha = 0.9f),
                height = topFadeHeight * 0.62f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topFadeTopPadding + 1.dp)
                    .zIndex(1.1f)
            )

            // Bottom fade mask: starts near the input top and fades to solid background at screen bottom.
            val bottomFadeHeightTarget = remember(imeVisible, bottomSystemPadding, bottomBarHeightDp) {
                if (imeVisible) {
                    bottomBarHeightDp + 10.dp
                } else {
                    bottomBarHeightDp + bottomSystemPadding + 20.dp
                }
            }
            val bottomFadeHeight by animateDpAsState(
                targetValue = bottomFadeHeightTarget,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                label = "bottom_fade_height"
            )
            if (bottomFadeHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomFadeHeight)
                        .zIndex(3f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ChatBackground.copy(alpha = 0f),
                                    ChatBackground.copy(alpha = 0.28f),
                                    ChatBackground.copy(alpha = 0.58f),
                                    ChatBackground
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .zIndex(2f)
            ) {
                Box(modifier = Modifier.onSizeChanged { topBarHeightPx = it.height }) {
                    TopNavBar(
                        onMenuClick = {
                            if (!appWorkspaceActive) {
                                scope.launch { drawerState.open() }
                            }
                        },
                        onChatModelClick = { showChatModelPicker = true },
                        onNewChatClick = ::startNewChat
                    )
                }
            }

            // 底部输入框区域 - 固定在底部（高度动态，支持多行/工具标签）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = inputBottomInset)
                    .zIndex(5f)
            ) {
                Box(
                    modifier = Modifier
                        .onSizeChanged { bottomBarHeightPx = it.height }
                ) {
                    BottomInputArea(
                        selectedTool = selectedTool,
                        mcpSelectedCount = selectedMcpServerIds.size,
                        webSearchEngine = webSearchConfig.engine,
                        attachments = imageAttachments,
                        onRemoveAttachment = { index ->
                            if (index < 0 || index >= imageAttachments.size) return@BottomInputArea
                            val updated = imageAttachments.toMutableList()
                            updated.removeAt(index)
                            imageAttachments = updated.toList()
                        },
                        onToolToggle = {
                            if (showToolMenu) {
                                showToolMenu = false
                                toolMenuPage = ToolMenuPage.Tools
                                return@BottomInputArea
                            }
                            if (imeVisible) {
                                hideKeyboardIfNeeded(force = true)
                                scope.launch {
                                    delay(180)
                                    showToolMenu = true
                                    toolMenuPage = ToolMenuPage.Tools
                                }
                            } else {
                                showToolMenu = true
                                toolMenuPage = ToolMenuPage.Tools
                            }
                        },
                        onClearTool = { selectedTool = null },
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        onSend = ::sendMessage,
                        onStopStreaming = ::stopStreaming,
                        sendAllowed = selectedTool == "autosoul" || !defaultChatModelId.isNullOrBlank(),
                        sendBusy = sendInFlight,
                        isStreaming = isStreaming,
                        imeVisible = imeVisible,
                        onInputFocusChanged = { focused ->
                            inputFieldFocused = focused
                            if (focused && showToolMenu) {
                                showToolMenu = false
                                toolMenuPage = ToolMenuPage.Tools
                            }
                        },
                        actionActiveColor = accentPalette.actionColor
                    )
                }
            }

            // 底部工具面板（覆盖在输入框上方）
            ToolMenuPanel(
                visible = showToolMenu,
                page = toolMenuPage,
                webSearchEngine = webSearchConfig.engine,
                mcpLoading = mcpToolPickerLoading,
                mcpErrorText = mcpToolPickerError,
                mcpServers = mcpServerPickerItems,
                selectedMcpServerIds = selectedMcpServerIds,
                onToggleMcpServer = { serverId, enabled ->
                    val updated =
                        if (enabled) {
                            selectedMcpServerIds + serverId
                        } else {
                            selectedMcpServerIds - serverId
                        }
                    selectedMcpServerIds = updated
                },
                onMcpPageChange = { page ->
                    toolMenuPage = page
                    if (page == ToolMenuPage.McpServers) {
                        openMcpToolPicker()
                    }
                },
                onOpenMcpSettings = {
                    showToolMenu = false
                    toolMenuPage = ToolMenuPage.Tools
                    navController.navigate("mcp")
                },
                modifier = Modifier.zIndex(20f),
                onDismiss = {
                    showToolMenu = false
                    toolMenuPage = ToolMenuPage.Tools
                },
                onToolSelect = { tool ->
                    when (tool) {
                        "camera" -> {
                            showToolMenu = false
                            toolMenuPage = ToolMenuPage.Tools
                            hideKeyboardIfNeeded(force = true)
                            cameraLauncher.launch(null)
                        }
                        "photos" -> {
                            showToolMenu = false
                            toolMenuPage = ToolMenuPage.Tools
                            hideKeyboardIfNeeded(force = true)
                            photoPickerLauncher.launch("image/*")
                        }
                        "mcp" -> {
                            openMcpToolPicker()
                        }
                        else -> {
                            selectedTool = tool
                            showToolMenu = false
                            toolMenuPage = ToolMenuPage.Tools
                        }
                    }
                }
            )

            if (showChatModelPicker) {
                ModalBottomSheet(
                    onDismissRequest = { showChatModelPicker = false },
                    sheetState = chatModelPickerState,
                    sheetGesturesEnabled = false,
                    containerColor = Surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = null
                ) {
                    ChatModelPickerSheetContent(
                        groupedModels = chatModelGroups,
                        selectedModelId = defaultChatModelId,
                        thinkingEnabled = chatThinkingEnabled,
                        onToggleThinking = { enabled ->
                            scope.launch { repository.setChatThinkingEnabled(enabled) }
                        },
                        onDismissRequest = { showChatModelPicker = false },
                        onSelectModel = { model ->
                            scope.launch {
                                repository.setDefaultChatModelId(model.id)
                            }
                            showChatModelPicker = false
                        }
                    )
                }
            }

            // 获取当前对话的实时thinking内容
            val currentMessageThinking = localMessages.lastOrNull { it.reasoning != null }?.reasoning
            val displayThinkingText = if (showThinkingSheet && currentMessageThinking != null) {
                currentMessageThinking
            } else thinkingSheetText

            if (showThinkingSheet && !displayThinkingText.isNullOrBlank()) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showThinkingSheet = false
                        thinkingSheetText = null
                    },
                    sheetState = thinkingSheetState,
                    containerColor = ThinkingBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { AppSheetDragHandle(backgroundColor = ThinkingBackground) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.67f)
                            .background(ThinkingBackground)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.thinking_label),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MarkdownText(
                                markdown = displayThinkingText.orEmpty(),
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            val activeTag = remember(localMessages, tagSheetMessageId, tagSheetTagId) {
                val messageId = tagSheetMessageId?.trim().orEmpty()
                val tagId = tagSheetTagId?.trim().orEmpty()
                if (messageId.isBlank() || tagId.isBlank()) {
                    null
                } else {
                    localMessages.firstOrNull { it.id == messageId }
                        ?.tags
                        .orEmpty()
                        .firstOrNull { it.id == tagId }
                }
            }

            if (!tagSheetTagId.isNullOrBlank() && activeTag == null) {
                LaunchedEffect(localMessages, tagSheetTagId, tagSheetMessageId) {
                    tagSheetMessageId = null
                    tagSheetTagId = null
                }
            }

            activeTag?.let { tag ->
                ModalBottomSheet(
                    onDismissRequest = {
                        tagSheetMessageId = null
                        tagSheetTagId = null
                    },
                    sheetState = tagSheetState,
                    containerColor = ThinkingBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { AppSheetDragHandle(backgroundColor = ThinkingBackground) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.67f)
                            .background(ThinkingBackground)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = if (tag.kind == "mcp") "Details" else tag.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (tag.kind == "mcp") {
                                McpTagDetailCard(tag = tag)
                            } else {
                                MarkdownText(
                                    markdown = tag.content,
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val activeAppWorkspaceTag = remember(localMessages, appWorkspaceMessageId, appWorkspaceTagId) {
                val messageId = appWorkspaceMessageId?.trim().orEmpty()
                val tagId = appWorkspaceTagId?.trim().orEmpty()
                if (messageId.isBlank() || tagId.isBlank()) {
                    null
                } else {
                    localMessages.firstOrNull { it.id == messageId }
                        ?.tags
                        .orEmpty()
                        .firstOrNull { it.id == tagId && it.kind == "app_dev" }
                }
            }
            if (!appWorkspaceTagId.isNullOrBlank() && activeAppWorkspaceTag == null) {
                LaunchedEffect(localMessages, appWorkspaceTagId, appWorkspaceMessageId) {
                    appWorkspaceMessageId = null
                    appWorkspaceTagId = null
                }
            }

            activeAppWorkspaceTag?.let { tag ->
                AppDevWorkspaceScreen(
                    tag = tag,
                    onDismiss = {
                        appWorkspaceMessageId = null
                        appWorkspaceTagId = null
                    }
                )
            }
        }
    }
}
