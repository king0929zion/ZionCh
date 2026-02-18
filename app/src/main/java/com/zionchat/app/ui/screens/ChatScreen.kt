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
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.Job
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
import kotlin.math.roundToInt

// 颜色常量 - 完全匹配HTML原型
private data class PendingMessage(val conversationId: String, val message: Message)
private data class PendingImageAttachment(val uri: Uri? = null, val bitmap: Bitmap? = null)
private enum class ToolMenuPage { Tools, McpServers }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController) {
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
            if (convoId.isNotBlank() && convoId == streamingConversationId && latestShouldAutoScroll) {
                val lastIndex = latestLocalMessagesSize - 1
                if (lastIndex >= 0) {
                    listState.scrollToItem(lastIndex, scrollOffset = 0)
                }
            }
            delay(150)
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
        selectedTool = "mcp"
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
        if ((trimmed.isEmpty() && attachmentsSnapshot.isEmpty()) || isStreaming) return
        val selectedToolSnapshot = selectedTool
        val webSearchConfigSnapshot = webSearchConfig
        val explicitWebSearchRequest = selectedToolSnapshot == "web"
        val explicitAppBuilderRequest = selectedToolSnapshot == "app_builder"
        val confirmationMatched = pendingAppBuilderConfirmation && isAppBuilderConfirmationReply(trimmed)
        val weakAutoWebSearchIntent =
            selectedToolSnapshot == null &&
                !explicitAppBuilderRequest &&
                !confirmationMatched &&
                attachmentsSnapshot.isEmpty() &&
                webSearchConfigSnapshot.autoSearchEnabled &&
                shouldAutoSearchForPrompt(trimmed)
        val weakAutoAppBuilderIntent =
            selectedToolSnapshot == null &&
                !explicitWebSearchRequest &&
                !weakAutoWebSearchIntent &&
                !explicitAppBuilderRequest &&
                !confirmationMatched &&
                shouldEnableAppBuilderForPrompt(trimmed)

        stopRequestedByUser = false
        streamingJob = scope.launch {
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
                    repository.updateConversation(latestConversationForTitle.copy(title = provisionalTitle))
                }
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
            val selectedModel =
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

            val providerList = repository.providersFlow.first()
            val provider = selectedModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } } ?: providerList.firstOrNull()
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
                    val explicitMcp = selectedTool == "mcp"
                    val selectedMcpServerIdsSnapshot = selectedMcpServerIds
                    val explicitAppBuilder = selectedTool == "app_builder"
                    val useWebSearch = explicitWebSearchRequest || weakAutoWebSearchIntent
                    val canUseAppBuilder = explicitAppBuilder
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

                    if (explicitMcp && enabledServers.isEmpty()) {
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
                        if (explicitMcp && selectedMcpServerIdsSnapshot.isNotEmpty()) {
                            serversWithTools.filter { server ->
                                selectedMcpServerIdsSnapshot.contains(server.id)
                            }
                        } else {
                            serversWithTools
                        }
                    val availableMcpServers = scopedMcpServers.filter { it.tools.isNotEmpty() }
                    val canUseMcp = availableMcpServers.isNotEmpty()
                    val canUseAnyTool = canUseMcp || canUseAppBuilder

                    if (explicitMcp && !canUseMcp) {
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
                            explicitMcp -> 6
                            explicitAppBuilder -> 4
                            else -> 4
                        }
                    val maxCallsPerRound =
                        when {
                            explicitMcp -> 4
                            explicitAppBuilder -> 2
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

                        val requestMessages = buildList {
                            if (!appBuilderInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = appBuilderInstruction))
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
                            reasoningEffort = selectedModel.reasoningEffort,
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
                        val parsedCalls =
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
                                                "Invalid arguments. app_developer now accepts only: name + description."
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
                                    val normalized = progressValue.coerceIn(8, 95)
                                    val now = System.currentTimeMillis()
                                    if (normalized <= streamedProgress) return
                                    if (now - lastProgressUpdateAtMs < 90L) return
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
                                    val savedAppId = targetSavedApp?.id ?: java.util.UUID.randomUUID().toString()
                                    val baseSavedApp =
                                        SavedApp(
                                            id = savedAppId,
                                            sourceTagId = pendingTag.id,
                                            name = finalName,
                                            description = finalDescription,
                                            html = html,
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
                                            style = parsedSpec.style.ifBlank { pendingPayload.style },
                                            features = if (parsedSpec.features.isNotEmpty()) parsedSpec.features else pendingPayload.features,
                                            progress = 100,
                                            status = "success",
                                            html = html,
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

                                repository.updateConversation(convo.copy(title = finalTitle))
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
                        val showToolbar = !isStreaming || (streamingMessageId == null) ||
                            (index >= localMessages.size - 3)
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
                        sendAllowed = !defaultChatModelId.isNullOrBlank(),
                        isStreaming = isStreaming,
                        imeVisible = imeVisible,
                        onInputFocusChanged = { focused -> inputFieldFocused = focused },
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
                    selectedTool = if (updated.isNotEmpty()) "mcp" else null
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
                            hideKeyboardIfNeeded(force = true)
                            cameraLauncher.launch(null)
                        }
                        "photos" -> {
                            showToolMenu = false
                            hideKeyboardIfNeeded(force = true)
                            photoPickerLauncher.launch("image/*")
                        }
                        "mcp" -> {
                            selectedTool = "mcp"
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
                    containerColor = Surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { AppSheetDragHandle(backgroundColor = Surface) }
                ) {
                    ChatModelPickerSheetContent(
                        groupedModels = chatModelGroups,
                        selectedModelId = defaultChatModelId,
                        onSelectModel = { model ->
                            scope.launch {
                                repository.setDefaultChatModelId(model.id)
                            }
                            showChatModelPicker = false
                            Toast.makeText(
                                context,
                                "默认对话模型已切换为 ${model.displayName}",
                                Toast.LENGTH_SHORT
                            ).show()
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

/**
 * Dynamic grid layout for message attachments
 * - 1-3 images: single row
 * - 4 images: 2x2 grid
 * - 9 images: 3x3 grid
 * - Other counts: adaptive grid
 */
@Composable
private fun AttachmentGrid(
    attachments: List<MessageAttachment>,
    modifier: Modifier = Modifier
) {
    val count = attachments.size
    val columns = when {
        count <= 3 -> count
        count == 4 -> 2
        count <= 6 -> 3
        count <= 9 -> 3
        else -> 4
    }
    val rows = (count + columns - 1) / columns

    // Calculate image size based on count
    val imageSize = when {
        count == 1 -> 200.dp
        count <= 3 -> 120.dp
        count <= 4 -> 100.dp
        count <= 6 -> 90.dp
        else -> 80.dp
    }

    val spacing = 6.dp

    // Column wrap content - alignment controlled by parent
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        for (row in 0 until rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                val startIndex = row * columns
                val endIndex = minOf(startIndex + columns, count)
                for (index in startIndex until endIndex) {
                    val attachment = attachments[index]
                    Box(
                        modifier = Modifier
                            .size(imageSize)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GrayLighter)
                    ) {
                        AsyncImage(
                            model = attachment.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: Message,
    conversationId: String?,
    isStreaming: Boolean = false,
    isThinkingActive: Boolean = false,
    showToolbar: Boolean = true,
    onShowReasoning: (String) -> Unit,
    onShowTag: (messageId: String, tagId: String) -> Unit,
    onDownloadAppDevTag: (messageId: String, tag: MessageTag) -> Unit = { _, _ -> },
    userBubbleColor: Color = UserMessageBubble,
    userBubbleSecondaryColor: Color? = null,
    userBubbleTextColor: Color = TextPrimary,
    onEdit: () -> Unit,
    onDelete: (conversationId: String, messageId: String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val isUser = message.role == "user"
    if (isUser) {
        val bubbleShape = RoundedCornerShape(18.dp)
        val attachments = message.attachments.orEmpty()

        // User message (right aligned)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // Show attachments above the bubble
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AttachmentGrid(
                        attachments = attachments,
                        modifier = Modifier
                            .padding(start = 60.dp, bottom = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 60.dp)
                        .then(
                            if (userBubbleSecondaryColor != null) {
                                Modifier
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(userBubbleColor, userBubbleSecondaryColor)
                                        ),
                                        shape = bubbleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.08f),
                                        shape = bubbleShape
                                    )
                            } else {
                                Modifier.background(userBubbleColor, bubbleShape)
                            }
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { },
                            onLongClick = { showMenu = true }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    MarkdownText(
                        markdown = message.content,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = userBubbleTextColor
                        )
                    )
                }
            }
        }
    } else {
        // Assistant message (left aligned)
        val assistantContentModifier =
            if (isStreaming) {
                Modifier
            } else {
                Modifier.animateContentSize(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
            }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(assistantContentModifier)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { },
                    onLongClick = { showMenu = true }
                )
        ) {
            val reasoningText = message.reasoning?.trim().orEmpty()
            if (reasoningText.isNotBlank()) {
                val thinkingTint =
                    if (isThinkingActive) {
                        val thinkingTransition = rememberInfiniteTransition(label = "thinking_label_pulse")
                        thinkingTransition.animateColor(
                            initialValue = Color(0xFF1C1C1E),
                            targetValue = Color(0xFFCFCFD4),
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(720, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                            label = "thinking_label_color"
                        ).value
                    } else {
                        Color(0xFF1C1C1E)
                    }
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .pressableScale(
                            pressedScale = 0.98f,
                            onClick = { onShowReasoning(reasoningText) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.thinking_label),
                        fontSize = 15.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.SemiBold,
                        color = thinkingTint
                    )
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = thinkingTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            val contentSegments = remember(message.content) { splitContentByMcpTagMarkers(message.content) }
            val tagsById = remember(message.tags) { message.tags.orEmpty().associateBy { it.id } }
            val inlineTagIds = remember(contentSegments) {
                contentSegments.mapNotNull { it.tagId }.toSet()
            }

            if (contentSegments.isEmpty()) {
                MarkdownText(
                    markdown = stripMcpTagMarkers(message.content),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = TextPrimary
                    )
                )
            } else {
                contentSegments.forEach { segment ->
                    val text = segment.text
                    val tagId = segment.tagId
                    if (!text.isNullOrBlank()) {
                        MarkdownText(
                            markdown = text,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = TextPrimary
                            )
                        )
                    } else if (!tagId.isNullOrBlank()) {
                        val tag = tagsById[tagId]
                        if (tag != null) {
                            MessageTagRow(
                                tag = tag,
                                messageId = message.id,
                                onShowTag = onShowTag,
                                onDownloadAppDevTag = onDownloadAppDevTag
                            )
                        }
                    }
                }
            }

            val orderedTags = remember(message.tags, inlineTagIds) {
                message.tags.orEmpty()
                    .filterNot { tag -> inlineTagIds.contains(tag.id) }
                    .sortedBy { it.createdAt }
            }
            orderedTags.forEach { tag ->
                MessageTagRow(
                    tag = tag,
                    messageId = message.id,
                    onShowTag = onShowTag,
                    onDownloadAppDevTag = onDownloadAppDevTag
                )
            }

            // Message action buttons (assistant only) - use AnimatedVisibility for smooth transition
            androidx.compose.animation.AnimatedVisibility(
                visible = !isStreaming && showToolbar,
                enter = fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    ActionButton(
                        icon = AppIcons.Copy,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(stripMcpTagMarkers(message.content)))
                        }
                    )
                    ActionButton(icon = AppIcons.Edit, onClick = onEdit)
                    ActionButton(icon = AppIcons.Volume, onClick = { })
                    ActionButton(icon = AppIcons.Share, onClick = { })
                    ActionButton(
                        icon = AppIcons.Refresh,
                        onClick = { /* Regenerate */ }
                    )
                    ActionButton(icon = AppIcons.More, onClick = { showMenu = true })
                }
            }
        }
    }

    // Long-press menu
    if (showMenu) {
        MessageOptionsDialog(
            isUser = isUser,
            onCopy = {
                clipboardManager.setText(AnnotatedString(stripMcpTagMarkers(message.content)))
                showMenu = false
            },
            onEdit = {
                onEdit()
                showMenu = false
            },
            onDelete = {
                val convoId = conversationId
                if (!convoId.isNullOrBlank()) {
                    onDelete(convoId, message.id)
                }
                showMenu = false
            },
            onDismiss = { showMenu = false }
        )
    }
}

@Composable
private fun MessageTagRow(
    tag: MessageTag,
    messageId: String,
    onShowTag: (messageId: String, tagId: String) -> Unit,
    onDownloadAppDevTag: (messageId: String, tag: MessageTag) -> Unit
) {
    if (tag.kind == "app_dev") {
        AppDevToolTagCard(
            tag = tag,
            onClick = { onShowTag(messageId, tag.id) },
            onDownloadClick = { onDownloadAppDevTag(messageId, tag) }
        )
        return
    }

    val tagRunning = isTagRunning(tag)
    val toolName = remember(tag.title) {
        tag.title.trim().takeIf { it.isNotBlank() && !it.equals("tool", ignoreCase = true) }
    }
    val displayText =
        if (tag.kind == "mcp") {
            if (toolName == null) {
                stringResource(R.string.tool_label)
            } else {
                stringResource(R.string.tool_label_with_name, stringResource(R.string.tool_label), toolName)
            }
        } else {
            tag.title
        }
    Row(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
            .pressableScale(
                pressedScale = 0.98f,
                onClick = { onShowTag(messageId, tag.id) }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (tag.kind == "mcp") {
            Icon(
                imageVector = AppIcons.Tool,
                contentDescription = null,
                tint = ThinkingLabelColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = displayText,
            fontSize = 15.sp,
            fontFamily = SourceSans3,
            fontWeight = FontWeight.SemiBold,
            color = ThinkingLabelColor
        )
        if (tagRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.7.dp,
                color = ThinkingLabelColor
            )
        } else {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = ThinkingLabelColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AppDevToolTagCard(
    tag: MessageTag,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val payload = remember(tag.content, tag.title, tag.status) {
        parseAppDevTagPayload(
            content = tag.content,
            fallbackName = tag.title.ifBlank { "App development" },
            fallbackStatus = tag.status
        )
    }
    val statusLower = payload.status.trim().lowercase()
    val hasHtml = payload.html.trim().isNotBlank()
    val isCompleted = statusLower == "success" && hasHtml
    val isRunning = statusLower == "running"
    val showSkeleton = !isCompleted && statusLower != "error"
    val runtimeBusy = payload.runtimeStatus == "queued" || payload.runtimeStatus == "in_progress"
    val reportedProgress = payload.progress.coerceIn(0, 100).toFloat()
    val htmlLength = payload.html.trim().length
    val htmlSignalProgress =
        if (htmlLength <= 0) {
            0f
        } else {
            val normalized =
                kotlin.math.sqrt(htmlLength.toFloat().coerceAtMost(120_000f)) /
                    kotlin.math.sqrt(120_000f)
            (10f + normalized * 78f).coerceAtMost(92f)
        }
    val targetProgress =
        when {
            isCompleted -> 100f
            statusLower == "error" -> reportedProgress
            isRunning ->
                maxOf(
                    reportedProgress,
                    htmlSignalProgress,
                    8f,
                    if (runtimeBusy) 96f else 0f
                ).coerceAtMost(if (runtimeBusy) 99f else 96f)
            else -> reportedProgress.coerceAtMost(99f)
        }
    val initialProgress = if (isRunning) maxOf(8f, targetProgress) else targetProgress
    var monotonicProgress by remember(tag.id) { mutableFloatStateOf(initialProgress) }
    LaunchedEffect(tag.id, targetProgress, isCompleted, statusLower) {
        when {
            isCompleted -> monotonicProgress = 100f
            statusLower == "error" -> monotonicProgress = targetProgress
            targetProgress > monotonicProgress -> monotonicProgress = targetProgress
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = monotonicProgress.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 240, easing = LinearEasing),
        label = "app_dev_progress_value"
    )
    val progressValue = animatedProgress.roundToInt().coerceIn(0, 100)

    val shimmerTransition = rememberInfiniteTransition(label = "app_dev_skeleton_shimmer")
    val skeletonShift by shimmerTransition.animateFloat(
        initialValue = 220f,
        targetValue = -220f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2000, easing = LinearEasing)),
        label = "app_dev_skeleton_shift"
    )
    val skeletonBrush = remember(skeletonShift) {
        Brush.linearGradient(
            colors = listOf(Color(0xFFE5E7EB), Color(0xFFF3F4F6), Color(0xFFE5E7EB)),
            start = Offset(skeletonShift, 0f),
            end = Offset(skeletonShift + 220f, 0f)
        )
    }

    val contributionLevels =
        remember(tag.id, payload.name, payload.subtitle, payload.description) {
            buildGitHubContributionLevels(
                seedKey = "${tag.id}|${payload.name}|${payload.subtitle}|${payload.description}",
                totalCells = 48
            )
        }

    val titleText = payload.name.ifBlank { "CodeMaster" }
    val subtitleText =
        when {
            statusLower == "error" ->
                payload.error?.trim().orEmpty().ifBlank { "Generation failed. Tap to inspect details and retry." }
            else ->
                payload.subtitle.ifBlank { payload.description }
                    .ifBlank { "Full-stack project with 847 commits" }
        }
    val secondaryTint =
        when {
            isCompleted && runtimeBusy -> Color(0xFF9CA3AF)
            isCompleted -> Color(0xFF4B5563)
            else -> Color(0xFF9CA3AF)
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .pressableScale(pressedScale = 0.985f, onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(width = 1.dp, color = Color(0xFFF3F4F6), shape = RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.14f),
                            spotColor = Color.Black.copy(alpha = 0.18f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF111827), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.GitHub,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (showSkeleton) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.90f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(skeletonBrush)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.70f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(skeletonBrush)
                        )
                    } else {
                        Text(
                            text = titleText,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = subtitleText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF6B7280),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Development progress",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = "${progressValue}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF16A34A)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppDevContributionGrid(
                    levels = contributionLevels,
                    progressPercent = progressValue,
                    isRunning = isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSkeleton) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(skeletonBrush)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Preview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent, CircleShape)
                        .pressableScale(
                            enabled = isCompleted && !runtimeBusy,
                            pressedScale = 0.95f,
                            onClick = onDownloadClick
                        )
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        DownloadOutlineGlyph(
                            modifier = Modifier.fillMaxSize(),
                            tint = secondaryTint
                        )
                    } else {
                        EyeOutlineGlyph(
                            modifier = Modifier.fillMaxSize(),
                            tint = secondaryTint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDevContributionGrid(
    levels: List<Int>,
    progressPercent: Int,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val rows = 3
    val cols = 16
    val totalCells = rows * cols
    val targetFilledCells =
        ((progressPercent.coerceIn(0, 100) / 100f) * totalCells)
            .toInt()
            .coerceIn(0, totalCells)
    var animatedFilledCells by remember(levels, isRunning) { mutableIntStateOf(0) }

    LaunchedEffect(targetFilledCells, isRunning) {
        when {
            targetFilledCells < animatedFilledCells -> {
                animatedFilledCells = targetFilledCells
            }
            targetFilledCells == animatedFilledCells -> Unit
            else -> {
                val stepDelayMs =
                    when {
                        targetFilledCells - animatedFilledCells > 20 -> 6L
                        targetFilledCells - animatedFilledCells > 8 -> 10L
                        else -> 16L
                    }
                while (animatedFilledCells < targetFilledCells) {
                    animatedFilledCells += 1
                    delay(stepDelayMs)
                }
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (col in 0 until cols) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (row in 0 until rows) {
                    val index = (col * rows) + row
                    val level = levels.getOrElse(index) { 0 }.coerceIn(0, 4)
                    val filled = index < animatedFilledCells
                    val scale by animateFloatAsState(
                        targetValue = if (filled) 1f else 0f,
                        animationSpec =
                            if (filled) {
                                keyframes {
                                    durationMillis = 260
                                    0f at 0
                                    1.12f at 160
                                    1f at 260
                                }
                            } else {
                                tween(durationMillis = 120, easing = LinearEasing)
                            },
                        label = "app_dev_contrib_scale_$index"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (filled) 1f else 0f,
                        animationSpec = tween(durationMillis = 160, easing = LinearEasing),
                        label = "app_dev_contrib_alpha_$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFEBEDF0))
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer(
                                    alpha = alpha,
                                    scaleX = scale,
                                    scaleY = scale
                                )
                                .background(githubContributionColor(level))
                        )
                    }
                }
            }
        }
    }
}

private fun buildGitHubContributionLevels(seedKey: String, totalCells: Int): List<Int> {
    val safeTotal = totalCells.coerceAtLeast(1)
    val random = kotlin.random.Random(seedKey.hashCode())
    return List(safeTotal) { index ->
        val progress = index.toFloat() / safeTotal.toFloat()
        when {
            progress < 0.2f -> if (random.nextFloat() > 0.3f) random.nextInt(from = 1, until = 3) else 0
            progress < 0.4f -> if (random.nextFloat() > 0.2f) random.nextInt(from = 1, until = 4) else 0
            progress < 0.6f -> if (random.nextFloat() > 0.1f) random.nextInt(from = 1, until = 5) else 0
            progress < 0.8f -> if (random.nextFloat() > 0.05f) random.nextInt(from = 2, until = 5) else 1
            else -> if (random.nextFloat() > 0.1f) random.nextInt(from = 3, until = 5) else 2
        }
    }
}

private fun githubContributionColor(level: Int): Color {
    return when (level.coerceIn(0, 4)) {
        1 -> Color(0xFF9BE9A8)
        2 -> Color(0xFF40C463)
        3 -> Color(0xFF30A14E)
        4 -> Color(0xFF216E39)
        else -> Color(0xFFEBEDF0)
    }
}

@Composable
private fun EyeOutlineGlyph(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Canvas(modifier = modifier) {
        val stroke = (size.minDimension * 0.10f).coerceAtLeast(1.2f)
        drawOval(
            color = tint,
            topLeft = Offset(size.width * 0.08f, size.height * 0.24f),
            size = Size(size.width * 0.84f, size.height * 0.52f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.14f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = stroke * 0.86f)
        )
    }
}

@Composable
private fun DownloadOutlineGlyph(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Canvas(modifier = modifier) {
        val stroke = (size.minDimension * 0.10f).coerceAtLeast(1.2f)
        val centerX = size.width / 2f
        val startY = size.height * 0.18f
        val arrowY = size.height * 0.62f
        drawLine(
            color = tint,
            start = Offset(centerX, startY),
            end = Offset(centerX, arrowY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, arrowY),
            end = Offset(size.width * 0.35f, size.height * 0.47f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, arrowY),
            end = Offset(size.width * 0.65f, size.height * 0.47f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.24f, size.height * 0.80f),
            end = Offset(size.width * 0.76f, size.height * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun McpTagDetailCard(tag: MessageTag) {
    val detail = remember(tag.content) { parseMcpTagDetail(tag.content) }
    val isRunning = isTagRunning(tag)
    val detailsRaw =
        when {
            isRunning && detail.argumentsJson.isNotBlank() -> detail.argumentsJson
            isRunning -> "Running..."
            detail.error.isNotBlank() -> detail.error
            detail.result.isNotBlank() -> detail.result
            detail.argumentsJson.isNotBlank() -> detail.argumentsJson
            else -> "{}"
        }
    val detailsText = remember(detailsRaw) { formatToolDetailJson(detailsRaw) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolDetailCodeCard(
            title = "Details",
            content = detailsText
        )
        if (isRunning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = ThinkingLabelColor
                )
                Text(
                    text = "Running",
                    fontSize = 12.sp,
                    color = ThinkingLabelColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AppDevWorkspaceScreen(
    tag: MessageTag,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val payload =
        remember(tag.id, tag.content, tag.status) {
            parseAppDevTagPayload(
                content = tag.content,
                fallbackName = tag.title.ifBlank { "App development" },
                fallbackStatus = tag.status
            )
        }
    val html = remember(payload.html) { normalizeGeneratedHtmlDraft(payload.html) }.trim()
    val isRunning = payload.status.equals("running", ignoreCase = true)
    var showCode by remember(tag.id) { mutableStateOf(isRunning || html.isBlank()) }
    var userToggledTab by remember(tag.id) { mutableStateOf(false) }

    LaunchedEffect(isRunning, html, userToggledTab) {
        if (!userToggledTab) {
            // Default tab policy: coding while generating, preview after completed.
            showCode = isRunning || html.isBlank()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                .padding(top = 8.dp, bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = payload.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!showCode) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                            .pressableScale(
                                pressedScale = 0.97f,
                                onClick = {
                                    if (isRunning) return@pressableScale
                                    userToggledTab = true
                                    showCode = false
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Preview",
                            fontSize = 12.sp,
                            color =
                                when {
                                    isRunning -> TextSecondary.copy(alpha = 0.55f)
                                    !showCode -> TextPrimary
                                    else -> TextSecondary
                                }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (showCode) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                            .pressableScale(
                                pressedScale = 0.97f,
                                onClick = {
                                    userToggledTab = true
                                    showCode = true
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Code",
                            fontSize = 12.sp,
                            color = if (showCode) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (showCode || isRunning) {
                    AppDevCodeSurface(
                        html = html,
                        error = payload.error,
                        isRunning = isRunning
                    )
                } else {
                    if (html.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = payload.error?.ifBlank { null }
                                    ?: stringResource(R.string.app_dev_preview_unavailable),
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        val workspaceBaseUrl =
                            remember(payload.sourceAppId) {
                                val key = payload.sourceAppId?.trim().orEmpty().ifBlank { "draft" }
                                "https://workspace-app.zionchat.local/app/$key/"
                            }
                        val contentSignature =
                            remember(workspaceBaseUrl, html) { "$workspaceBaseUrl:${html.hashCode()}" }
                        AppHtmlWebView(
                            modifier = Modifier.fillMaxSize(),
                            contentSignature = contentSignature,
                            html = html,
                            baseUrl = workspaceBaseUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDevCodeSurface(
    html: String,
    error: String?,
    isRunning: Boolean
) {
    val codeText =
        remember(html, isRunning) {
            val normalized = normalizeGeneratedHtmlDraft(html)
            if (normalized.isBlank()) {
                ""
            } else if (isRunning) {
                // Stream phase prioritizes low-latency updates.
                normalized
            } else {
                prettyFormatHtmlForCodeView(normalized)
            }
        }
    val scrollState = rememberScrollState()
    val horizontalState = rememberScrollState()
    val lineCount = remember(codeText) { codeText.count { it == '\n' } + 1 }
    val lineNumberText = remember(lineCount) {
        buildString {
            for (i in 1..lineCount) {
                append(i)
                if (i != lineCount) append('\n')
            }
        }
    }

    LaunchedEffect(codeText, isRunning) {
        if (isRunning && codeText.isNotBlank()) {
            // Keep viewport near newest streamed chunks for a terminal-like coding flow.
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (codeText.isBlank()) {
            Text(
                text = error?.ifBlank { null }
                    ?: if (isRunning) "Generating code..." else "No code available.",
                fontSize = 14.sp,
                color = TextSecondary
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalState)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = lineNumberText,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary.copy(alpha = 0.72f),
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = codeText,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    softWrap = false
                )
            }
        }
    }
}

private fun prettyFormatHtmlForCodeView(raw: String): String {
    val clean = normalizeGeneratedHtmlDraft(raw).trim()
    if (clean.isBlank()) return ""

    // Script/style heavy pages are easy to corrupt with naive token splitting.
    // For those cases, keep original line structure to avoid broken indentation.
    if (Regex("(?is)<\\s*(script|style|pre|textarea)\\b").containsMatchIn(clean)) {
        return clean
    }

    val openTagNameRegex = Regex("^<\\s*([A-Za-z][A-Za-z0-9:_-]*)\\b")
    val voidTags =
        setOf(
            "area",
            "base",
            "br",
            "col",
            "embed",
            "hr",
            "img",
            "input",
            "link",
            "meta",
            "param",
            "source",
            "track",
            "wbr"
        )

    val tokens = clean
        .replace("><", ">\n<")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val sb = StringBuilder()
    var indent = 0
    tokens.forEach { token ->
        val trimmedToken = token.trim()
        val lowerToken = trimmedToken.lowercase()
        val closeTag = lowerToken.startsWith("</")

        if (closeTag) indent = (indent - 1).coerceAtLeast(0)
        repeat(indent) { sb.append("  ") }
        sb.append(trimmedToken)
        sb.append('\n')

        val openMatch = openTagNameRegex.find(trimmedToken)
        val tagName = openMatch?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
        val selfClosing = trimmedToken.endsWith("/>")
        val isDoctypeOrComment = lowerToken.startsWith("<!") || lowerToken.startsWith("<?")
        val isVoid = tagName in voidTags
        val remainder =
            if (openMatch != null && openMatch.range.last + 1 < trimmedToken.length) {
                trimmedToken.substring(openMatch.range.last + 1)
            } else {
                ""
            }
        val hasInlineCloseForSameTag =
            tagName.isNotBlank() &&
                Regex("</\\s*${Regex.escape(tagName)}\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(remainder)

        val openTag =
            openMatch != null &&
                !closeTag &&
                !isDoctypeOrComment &&
                !selfClosing &&
                !isVoid &&
                !hasInlineCloseForSameTag

        if (openTag) indent += 1
    }
    return sb.toString().trimEnd()
}

@Composable
private fun ToolDetailCodeCard(
    title: String,
    content: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = GrayLighter
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = "json",
                    fontSize = 11.sp,
                    color = TextSecondary.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content.ifBlank { "{}" },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun MessageOptionsDialog(
    isUser: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                DialogOption(text = "Copy", onClick = onCopy)
                if (isUser) {
                    DialogOption(text = "Edit", onClick = onEdit)
                }
                DialogOption(text = "Delete", onClick = onDelete, isDestructive = true)
                DialogOption(text = "Cancel", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun DialogOption(
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            color = if (isDestructive) Color(0xFFFF3B30) else TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyChatState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "What's on your mind today?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0D0D0D)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarContent(
    conversations: List<Conversation>,
    currentConversationId: String?,
    nickname: String,
    avatarUri: String,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onConversationClick: (Conversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    navController: NavController
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 顶部搜索区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 搜索框
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = "Search",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Search",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }

            // 新建对话按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onNewChat),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.NewChat,
                    contentDescription = "New Chat",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 菜单区域
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            SidebarMenuItem(
                icon = { Icon(AppIcons.NewChat, null, Modifier.size(22.dp), TextPrimary) },
                label = "New chat",
                onClick = onNewChat
            )
            SidebarMenuItem(
                icon = { Icon(AppIcons.ChatGPTLogo, null, Modifier.size(22.dp), TextPrimary) },
                label = "Images",
                onClick = { }
            )
            SidebarMenuItem(
                icon = {
                    Icon(
                        painter = rememberResourceDrawablePainter(R.drawable.ic_apps),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.Unspecified
                    )
                },
                label = "Apps",
                onClick = {
                    onClose()
                    navController.navigate("apps")
                }
            )
        }

        // 历史记录区域
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            conversations.forEach { conversation ->
                val isSelected = conversation.id == currentConversationId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) GrayLighter else Color.Transparent)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onConversationClick(conversation) },
                            onLongClick = { onDeleteConversation(conversation.id) }
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = conversation.title.ifBlank { "New chat" },
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        // 底部用户信息
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .pressableScale { navController.navigate("settings") }
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像显示
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GrayLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri.isNotBlank()) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.User,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nickname,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Personal",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SidebarMenuItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressableScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = label,
            fontSize = 17.sp,
            fontFamily = SourceSans3,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
fun TopNavBar(
    onMenuClick: () -> Unit,
    onChatModelClick: () -> Unit,
    onNewChatClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧组
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 汉堡菜单
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(CircleShape)
                    .background(Surface, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(TextPrimary, RoundedCornerShape(1.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(TextPrimary, RoundedCornerShape(1.dp))
                    )
                }
            }

            // ChatGPT 标签
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(21.dp), clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(RoundedCornerShape(21.dp))
                    .background(Surface, RoundedCornerShape(21.dp))
                    .pressableScale(pressedScale = 0.97f, onClick = onChatModelClick)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ChatGPT",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }

        // 右侧新建对话
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                .clip(CircleShape)
                .background(Surface, CircleShape)
                .pressableScale(pressedScale = 0.95f, onClick = onNewChatClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.NewChat,
                contentDescription = "New Chat",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .pressableScale(pressedScale = 0.95f, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ActionIcon,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ToolMenuPanel(
    visible: Boolean,
    page: ToolMenuPage,
    webSearchEngine: String,
    mcpLoading: Boolean,
    mcpErrorText: String?,
    mcpServers: List<McpServerPickerItem>,
    selectedMcpServerIds: Set<String>,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onMcpPageChange: (ToolMenuPage) -> Unit,
    onOpenMcpSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onToolSelect: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panelMaxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.6f)
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }
    val dragProgress = remember(dragOffsetPx, dismissThresholdPx) {
        (dragOffsetPx / dismissThresholdPx).coerceIn(0f, 1f)
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) (0.5f * (1f - dragProgress * 0.72f)) else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
    )
    LaunchedEffect(visible) {
        if (!visible) {
            dragOffsetPx = 0f
            onMcpPageChange(ToolMenuPage.Tools)
        }
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = { velocity ->
                                val shouldDismiss =
                                    dragOffsetPx > dismissThresholdPx || velocity > 2400f
                                if (shouldDismiss) {
                                    onDismiss()
                                    dragOffsetPx = 0f
                                } else {
                                    scope.launch {
                                        animate(
                                            initialValue = dragOffsetPx,
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = 220,
                                                easing = FastOutSlowInEasing
                                            )
                                        ) { value, _ ->
                                            dragOffsetPx = value
                                        }
                                    }
                                }
                            }
                        )
                        .animateEnterExit(
                            enter =
                                slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                ) +
                                    fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
                            exit =
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                ) +
                                    fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        )
                        .zIndex(10f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = panelMaxHeight)
                            .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
                            .background(Surface)
                            .padding(16.dp)
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

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                if (targetState == ToolMenuPage.McpServers) {
                                    slideInHorizontally(
                                        initialOffsetX = { it / 2 },
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                    ) togetherWith
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 2 },
                                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                        )
                                } else {
                                    slideInHorizontally(
                                        initialOffsetX = { -it / 2 },
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                    ) togetherWith
                                        slideOutHorizontally(
                                            targetOffsetX = { it / 2 },
                                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                        )
                                }
                            },
                            label = "tool_menu_page_switch"
                        ) { currentPage ->
                            if (currentPage == ToolMenuPage.Tools) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        QuickActionButton(
                                            icon = { Icon(AppIcons.Camera, null, Modifier.size(28.dp), TextPrimary) },
                                            label = "Camera",
                                            onClick = { onToolSelect("camera") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        QuickActionButton(
                                            icon = { Icon(AppIcons.ChatGPTLogo, null, Modifier.size(28.dp), TextPrimary) },
                                            label = "Photos",
                                            onClick = { onToolSelect("photos") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        QuickActionButton(
                                            icon = {
                                                Icon(
                                                    painter = rememberResourceDrawablePainter(R.drawable.ic_files),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                    tint = Color.Unspecified
                                                )
                                            },
                                            label = "Files",
                                            onClick = { onToolSelect("files") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Divider(color = GrayLight, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val webSearchSubtitle =
                                        stringResource(R.string.chat_tool_web_search_subtitle) +
                                            " · " +
                                            displaySearchEngineName(webSearchEngine)
                                    ToolListItem(
                                        icon = { Icon(AppIcons.Globe, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.chat_tool_web_search),
                                        subtitle = webSearchSubtitle,
                                        onClick = { onToolSelect("web") }
                                    )
                                    ToolListItem(
                                        icon = { Icon(AppIcons.CreateImage, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.chat_tool_create_image),
                                        subtitle = stringResource(R.string.chat_tool_create_image_subtitle),
                                        onClick = { onToolSelect("image") }
                                    )
                                    ToolListItem(
                                        icon = { Icon(AppIcons.MCPTools, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.settings_item_mcp_tools),
                                        subtitle = "Choose MCP providers",
                                        onClick = {
                                            onToolSelect("mcp")
                                            onMcpPageChange(ToolMenuPage.McpServers)
                                        }
                                    )
                                    ToolListItem(
                                        icon = { Icon(AppIcons.AppDeveloper, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.chat_tool_app_builder),
                                        subtitle = stringResource(R.string.chat_tool_app_builder_subtitle),
                                        onClick = { onToolSelect("app_builder") }
                                    )
                                }
                            } else {
                                McpServerPickerPanel(
                                    loading = mcpLoading,
                                    errorText = mcpErrorText,
                                    servers = mcpServers,
                                    selectedServerIds = selectedMcpServerIds,
                                    onBack = { onMcpPageChange(ToolMenuPage.Tools) },
                                    onOpenSettings = onOpenMcpSettings,
                                    onToggle = onToggleMcpServer,
                                    maxHeight = panelMaxHeight
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
private fun McpServerPickerPanel(
    loading: Boolean,
    errorText: String?,
    servers: List<McpServerPickerItem>,
    selectedServerIds: Set<String>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    maxHeight: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.pressableScale(pressedScale = 0.97f, onClick = onBack)
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.settings_item_mcp_tools),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(10.dp))
                    .pressableScale(pressedScale = 0.95f, onClick = onOpenSettings)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "设置",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
        }

        if (!errorText.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.chat_mcp_picker_sync_error, errorText),
                fontSize = 12.sp,
                color = Color(0xFFB42318)
            )
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.2.dp,
                        color = TextPrimary
                    )
                }
            }

            servers.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.chat_mcp_picker_empty_enabled),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = servers,
                        key = { item -> item.serverId }
                    ) { item ->
                        val checked = selectedServerIds.contains(item.serverId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White, RoundedCornerShape(14.dp))
                                .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(14.dp))
                                .pressableScale(
                                    pressedScale = 0.985f,
                                    onClick = { onToggle(item.serverId, !checked) }
                                )
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.serverName,
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.subtitle,
                                    fontSize = 12.sp,
                                    color = Color(0xFF4B5563),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = { value -> onToggle(item.serverId, value) },
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color.Black,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFFD1D5DB)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatModelPickerSheetContent(
    groupedModels: List<Pair<String, List<ModelConfig>>>,
    selectedModelId: String?,
    onSelectModel: (ModelConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp, max = 640.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(GrayLight, RoundedCornerShape(2.dp))
            )
        }

        Text(
            text = "选择默认对话模型",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (groupedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无可用模型",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                groupedModels.forEach { (providerName, providerModels) ->
                    item(key = "chat_model_provider_$providerName") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = providerName.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = GrayLighter)
                            ) {
                                providerModels.forEachIndexed { index, model ->
                                    val selected = isSameStoredOrRemoteModelId(model.id, selectedModelId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pressableScale(
                                                pressedScale = 0.985f,
                                                onClick = { onSelectModel(model) }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = model.displayName,
                                            fontSize = 16.sp,
                                            color = TextPrimary
                                        )
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .alpha(if (selected) 1f else 0f)
                                        )
                                    }
                                    if (index != providerModels.lastIndex) {
                                        Divider(color = GrayLight)
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

@Composable
fun QuickActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ChatBackground, RoundedCornerShape(14.dp))
            .pressableScale(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SourceSans3,
            color = TextPrimary
        )
    }
}

private suspend fun handleImageGeneration(
    repository: AppRepository,
    chatApiClient: ChatApiClient,
    safeConversationId: String,
    userPrompt: String,
    assistantMessage: Message,
    updateAssistantContent: (String) -> Unit
) {
    // 显示正在生成的提示
    updateAssistantContent("🎨 Generating image...")

    // 获取生图模型配置
    val imageModelId = repository.defaultImageModelIdFlow.first()
    if (imageModelId.isNullOrBlank()) {
        updateAssistantContent("❌ Please configure Image Model in Settings → Default model before generating images.")
        repository.appendMessage(
            safeConversationId,
            assistantMessage.copy(content = "Please configure Image Model in Settings → Default model before generating images.")
        )
        return
    }

    val allModels = repository.modelsFlow.first()
    val imageModel = allModels.firstOrNull { it.id == imageModelId }
        ?: allModels.firstOrNull { extractRemoteModelId(it.id) == imageModelId }
    if (imageModel == null) {
        updateAssistantContent("❌ Image model not found: $imageModelId. Enable or add it in Models.")
        repository.appendMessage(
            safeConversationId,
            assistantMessage.copy(content = "Image model not found: $imageModelId. Enable or add it in Models.")
        )
        return
    }

    val providerList = repository.providersFlow.first()
    val provider = imageModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } }
        ?: providerList.firstOrNull()
    if (provider == null || provider.apiUrl.isBlank() || provider.apiKey.isBlank()) {
        updateAssistantContent("❌ Please add a provider with valid API URL and API Key.")
        repository.appendMessage(
            safeConversationId,
            assistantMessage.copy(content = "Please add a provider with valid API URL and API Key.")
        )
        return
    }

    // 调用图片生成API
    val result = chatApiClient.generateImage(
        provider = provider,
        modelId = extractRemoteModelId(imageModel.id),
        prompt = userPrompt,
        extraHeaders = imageModel.headers,
        size = "1024x1024",
        quality = "standard",
        n = 1
    )

    result.fold(
        onSuccess = { imageUrl ->
            val markdownImage = "![Generated Image]($imageUrl)"
            updateAssistantContent(markdownImage)
            repository.appendMessage(
                safeConversationId,
                assistantMessage.copy(content = markdownImage)
            )
        },
        onFailure = { error ->
            val errorMsg = "❌ Image generation failed: ${error.message ?: error.toString()}"
            updateAssistantContent(errorMsg)
            repository.appendMessage(
                safeConversationId,
                assistantMessage.copy(content = errorMsg)
            )
        }
    )
}

@Composable
fun ToolListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontFamily = SourceSans3,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

private fun bottomInputBottomPadding(imeVisible: Boolean): Dp {
    return if (imeVisible) 14.dp else 10.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomInputArea(
    selectedTool: String?,
    mcpSelectedCount: Int,
    webSearchEngine: String,
    attachments: List<PendingImageAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    onToolToggle: () -> Unit,
    onClearTool: () -> Unit,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopStreaming: () -> Unit,
    sendAllowed: Boolean = true,
    isStreaming: Boolean = false,
    imeVisible: Boolean = false,
    onInputFocusChanged: (Boolean) -> Unit = {},
    actionActiveColor: Color = TextPrimary
) {
    val hasText = messageText.trim().isNotEmpty()
    val hasAttachments = attachments.isNotEmpty()
    val sendEnabled = (hasText || hasAttachments) && sendAllowed
    val actionIsStop = isStreaming
    val actionEnabled = if (actionIsStop) true else sendEnabled
    val actionBackground by animateColorAsState(
        targetValue =
            when {
                actionIsStop -> actionActiveColor
                sendEnabled -> actionActiveColor
                else -> GrayLight
            },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )
    val actionIconTint by animateColorAsState(
        targetValue = if (sendEnabled) Color.White else TextSecondary,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
    )
    val maxTextHeight = if (selectedTool != null) 140.dp else 120.dp
    val maxLines = if (selectedTool != null) 6 else 5
    val toolLabel = when (selectedTool) {
        "files" -> "Files"
        "web" -> "Search · ${displaySearchEngineName(webSearchEngine)}"
        "image" -> "Image"
        "mcp" ->
            if (mcpSelectedCount > 0) {
                stringResource(R.string.chat_mcp_selected_count, mcpSelectedCount)
            } else {
                stringResource(R.string.settings_item_mcp_tools)
            }
        "app_builder" -> stringResource(R.string.chat_tool_app_builder)
        else -> selectedTool?.replaceFirstChar { it.uppercase() }.orEmpty()
    }
    val toolIconRes: Int? = when (selectedTool) {
        "files" -> R.drawable.ic_files
        else -> null
    }
    val toolIconVector = when (selectedTool) {
        "files" -> null
        "web" -> AppIcons.Globe
        "image" -> AppIcons.CreateImage
        "mcp" -> AppIcons.MCPTools
        "app_builder" -> AppIcons.AppDeveloper
        else -> AppIcons.Globe
    }
    val bottomPadding = bottomInputBottomPadding(imeVisible)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = bottomPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 工具图标按钮 - 46dp，与输入框等高
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(CircleShape)
                    .background(Surface, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onToolToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.ChatGPTLogo,
                    contentDescription = stringResource(R.string.settings_item_mcp_tools),
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 输入框容器 - 默认46dp与工具按钮对齐，可多行扩展；发送按钮固定右下角
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(23.dp), clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .background(Surface, RoundedCornerShape(23.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
                        .padding(start = 12.dp, end = 48.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    AnimatedVisibility(
                        visible = attachments.isNotEmpty(),
                        enter =
                            fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                        exit =
                            fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                                shrinkVertically(animationSpec = tween(160, easing = FastOutSlowInEasing))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                attachments.forEachIndexed { index, attachment ->
                                    Box(
                                        modifier = Modifier
                                            .size(92.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(GrayLighter),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = attachment.uri ?: attachment.bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.92f))
                                                .pressableScale(
                                                    pressedScale = 0.92f,
                                                    onClick = { onRemoveAttachment(index) }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = "Remove",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedTool != null,
                        enter =
                            fadeIn(animationSpec = tween(140, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                        exit =
                            fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing)) +
                                shrinkVertically(animationSpec = tween(140, easing = FastOutSlowInEasing))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .offset(x = (-4).dp)
                                    .background(Color(0xFFE8F4FD), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (toolIconRes != null) {
                                    Icon(
                                        painter = rememberResourceDrawablePainter(toolIconRes),
                                        contentDescription = null,
                                        tint = Color(0xFF007AFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = toolIconVector ?: AppIcons.Globe,
                                        contentDescription = null,
                                        tint = Color(0xFF007AFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = toolLabel,
                                    fontSize = 15.sp,
                                    color = Color(0xFF007AFF),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.offset(y = (-1).dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .pressableScale(
                                            pressedScale = 0.95f,
                                            onClick = onClearTool
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF007AFF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // 输入框 - 多行自动增高，最大高度120dp
                    BasicTextField(
                        value = messageText,
                        onValueChange = onMessageChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state -> onInputFocusChanged(state.isFocused) }
                            .heightIn(min = 24.dp, max = maxTextHeight),
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = if (actionIsStop) ImeAction.Done else ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { if (sendEnabled) onSend() },
                            onDone = { if (actionIsStop) onStopStreaming() }
                        ),
                        minLines = 1,
                        maxLines = maxLines,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (messageText.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.chat_placeholder_ask_anything),
                                        fontSize = 17.sp,
                                        lineHeight = 22.sp,
                                        color = TextSecondary
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // 发送按钮 - 固定在右下角（随输入框高度一起上移）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(actionBackground, CircleShape)
                        .zIndex(2f)
                        .pressableScale(
                            enabled = actionEnabled,
                            pressedScale = if (actionIsStop) 0.93f else 0.95f,
                            onClick = {
                                if (actionIsStop) onStopStreaming() else onSend()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = actionIsStop,
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = tween(130, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        initialScale = 0.84f,
                                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                                    )
                                ) togetherWith
                                (
                                    fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing)) +
                                        scaleOut(
                                            targetScale = 0.9f,
                                            animationSpec = tween(130, easing = FastOutSlowInEasing)
                                        )
                                    )
                        }
                    ) { stopMode ->
                        if (stopMode) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White)
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.Send,
                                contentDescription = "Send",
                                tint = actionIconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun encodeImageAttachmentToDataUrl(
    context: Context,
    attachment: PendingImageAttachment,
    maxDimension: Int = 1024,
    jpegQuality: Int = 85
): String? {
    return withContext(Dispatchers.IO) {
        val bitmap =
            attachment.bitmap
                ?: attachment.uri?.let { uri -> decodeBitmapFromUri(context, uri, maxDimension) }
                ?: return@withContext null

        val scaled = scaleBitmapDown(bitmap, maxDimension)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

    resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val opts =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
        }

    return resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, opts)
    }
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    val maxDim = maxOf(width, height)
    if (maxDim <= maxDimension) return 1
    var sample = 1
    while (maxDim / sample > maxDimension) {
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}

private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val maxDim = maxOf(width, height)
    if (maxDim <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / maxDim.toFloat()
    val newWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val newHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

private fun buildConversationTranscript(
    messages: List<Message>,
    maxMessages: Int,
    maxCharsPerMessage: Int
): String {
    if (messages.isEmpty()) return ""
    val tail = messages.takeLast(maxMessages)
    return buildString {
        tail.forEach { msg ->
            val prefix =
                when (msg.role) {
                    "user" -> "User"
                    "assistant" -> "Assistant"
                    else -> msg.role.replaceFirstChar { it.uppercase() }
                }
            append(prefix)
            append(": ")
            val cleanContent = stripMcpTagMarkers(msg.content)
            append(cleanContent.replace('\n', ' ').trim().take(maxCharsPerMessage))
            append('\n')
        }
    }.trim()
}

private suspend fun collectStreamContent(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    messages: List<Message>,
    extraHeaders: List<HttpHeader>,
    onChunk: ((String) -> Unit)? = null
): String {
    val sb = StringBuilder()
    chatApiClient.chatCompletionsStream(
        provider = provider,
        modelId = modelId,
        messages = messages,
        extraHeaders = extraHeaders
    ).collect { delta ->
        delta.content?.let { chunk ->
            sb.append(chunk)
            onChunk?.invoke(chunk)
        }
    }
    return sb.toString().trim()
}

private fun stripMarkdownCodeFences(text: String): String {
    var t = text.trim()
    if (t.startsWith("```")) {
        t = t.substringAfter('\n', t).trim()
    }
    if (t.endsWith("```")) {
        t = t.dropLast(3).trim()
    }
    return t.trim()
}

private fun parseJsonStringArray(text: String): List<String> {
    val cleaned = stripMarkdownCodeFences(text).trim()
    if (cleaned.isBlank()) return emptyList()

    val candidate = run {
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }

    val element = runCatching { JsonParser.parseString(candidate) }.getOrNull() ?: return emptyList()
    val array =
        when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> element.asJsonObject.getAsJsonArray("memories")
            else -> null
        } ?: return emptyList()

    return array.mapNotNull { el ->
        runCatching { el.asString }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }
}

private data class AppDevToolSpec(
    val mode: String,
    val name: String,
    val description: String,
    val style: String,
    val features: List<String>,
    val targetAppId: String? = null,
    val targetAppName: String? = null,
    val editRequest: String? = null
)

private data class AppDevTagPayload(
    val name: String,
    val subtitle: String,
    val description: String,
    val style: String,
    val features: List<String>,
    val progress: Int,
    val status: String,
    val html: String,
    val error: String? = null,
    val sourceAppId: String? = null,
    val mode: String = "create",
    val deployUrl: String? = null,
    val deployError: String? = null,
    val runtimeStatus: String? = null,
    val runtimeMessage: String? = null,
    val runtimeRunUrl: String? = null,
    val runtimeArtifactName: String? = null,
    val runtimeArtifactUrl: String? = null
)

private data class McpServerPickerItem(
    val serverId: String,
    val serverName: String,
    val subtitle: String,
    val toolCount: Int
)

private fun groupEnabledModelsByProvider(
    providers: List<ProviderConfig>,
    models: List<ModelConfig>
): List<Pair<String, List<ModelConfig>>> {
    if (models.isEmpty()) return emptyList()
    val providerNameById = providers.associateBy({ it.id }, { it.name })
    return models
        .filter { it.enabled }
        .groupBy { model ->
            model.providerId?.let { providerId ->
                providerNameById[providerId]
            } ?: "Other"
        }
        .toList()
        .sortedBy { it.first }
        .map { (providerName, providerModels) ->
            providerName to providerModels.sortedBy { it.displayName.lowercase() }
        }
}

private fun isSameStoredOrRemoteModelId(lhs: String?, rhs: String?): Boolean {
    val left = lhs?.trim().orEmpty()
    val right = rhs?.trim().orEmpty()
    if (left.isBlank() || right.isBlank()) return false
    if (left == right) return true
    return extractRemoteModelId(left) == extractRemoteModelId(right)
}

private data class PlannedMcpToolCall(
    val serverId: String,
    val toolName: String,
    val arguments: Map<String, Any?>
)

private fun buildMcpServerPickerItems(servers: List<McpConfig>): List<McpServerPickerItem> {
    return servers
        .sortedBy { it.name.trim().lowercase() }
        .map { server ->
            val toolCount = server.tools.size
            val subtitle =
                when {
                    server.description.isNotBlank() -> server.description.trim().take(80)
                    toolCount > 0 -> "$toolCount tools available"
                    else -> "No tools synced"
                }
            McpServerPickerItem(
                serverId = server.id,
                serverName = server.name.trim().ifBlank { "Unnamed MCP" },
                subtitle = subtitle,
                toolCount = toolCount
            )
        }
}

private fun displaySearchEngineName(raw: String): String {
    return when (raw.trim().lowercase()) {
        "exa" -> "Exa"
        "tavily" -> "Tavily"
        "linkup" -> "Linkup"
        else -> "Bing"
    }
}

private fun shouldAutoSearchForPrompt(userPrompt: String): Boolean {
    val text = userPrompt.trim().lowercase()
    if (text.isBlank()) return false

    val timeSensitivePatterns =
        listOf(
            Regex("(?i)\\b(latest|today|recent|current|news|breaking|update|live|price|stock|weather|score|schedule|release)\\b"),
            Regex("(最新|今天|最近|当前|新闻|快讯|实时|价格|股价|天气|比分|赛程|发布)")
        )
    val lookupPatterns =
        listOf(
            Regex("(?i)\\b(search|look up|find|check|verify|compare|summarize)\\b"),
            Regex("(搜索|查一下|查找|检索|核实|对比|汇总)")
        )
    val questionHint =
        text.endsWith("?") ||
            text.endsWith("？") ||
            Regex("(?i)\\b(what|who|when|where|how|is|are|can|could|should|which)\\b").containsMatchIn(text) ||
            Regex("(什么|谁|何时|哪里|怎么|是否|能否|哪个)").containsMatchIn(text)

    val hasTimeSensitive = timeSensitivePatterns.any { it.containsMatchIn(text) }
    val hasLookupIntent = lookupPatterns.any { it.containsMatchIn(text) }
    return hasTimeSensitive || (hasLookupIntent && questionHint)
}

private fun shouldEnableAppBuilderForPrompt(userPrompt: String): Boolean {
    val text = userPrompt.trim().lowercase()
    if (text.isBlank()) return false
    val actionRegexes =
        listOf(
            Regex("(?i)\\b(create|build|develop|make|edit|update|revise|modify|refactor|improve)\\b"),
            Regex("(创建|开发|制作|做一个|做个|修改|编辑|更新|优化|重构)")
        )
    val targetRegexes =
        listOf(
            Regex("(?i)\\b(app|website|web app|html app|single page app|landing page|page)\\b"),
            Regex("(应用|网页|网站|页面|小程序)")
        )
    val nonBuildQuestionRegexes =
        listOf(
            Regex("(?i)^\\s*(what|why|who|when|where|how)\\b"),
            Regex("(?i)\\b(what is|how to|why is|can you explain|tutorial|concept)\\b"),
            Regex("^(什么是|为什么|怎么|如何|能否讲解|解释一下)")
        )
    val hasAction = actionRegexes.any { it.containsMatchIn(text) }
    val hasTarget = targetRegexes.any { it.containsMatchIn(text) }
    if (!hasAction || !hasTarget) return false
    if (text.endsWith("?") || text.endsWith("？")) {
        return nonBuildQuestionRegexes.none { it.containsMatchIn(text) }
    }
    return nonBuildQuestionRegexes.none { it.containsMatchIn(text) }
}

private fun isAppBuilderConfirmationReply(userPrompt: String): Boolean {
    val text = userPrompt.trim().lowercase()
    if (text.isBlank()) return false
    val keywords =
        listOf(
            "确认应用开发",
            "确认开发",
            "开始应用开发",
            "开始开发",
            "继续开发",
            "继续应用开发",
            "ok",
            "yes",
            "confirm"
        )
    return keywords.any { key -> text == key || text.contains(key) }
}

private fun buildAppBuilderConfirmationMessage(userPrompt: String): String {
    val summary = userPrompt.trim().replace(Regex("\\s+"), " ").take(90)
    return "检测到你可能想开发应用。回复“确认应用开发”我将进入 APP developer 流程并生成可运行 HTML；也可以继续补充需求。\n识别到的请求：$summary"
}

private fun isBuiltInAppDeveloperCall(call: PlannedMcpToolCall): Boolean {
    val server = call.serverId.trim().lowercase()
    val tool = call.toolName.trim().lowercase()
    return tool in setOf(
        "app_developer",
        "app_builder",
        "build_html_app",
        "develop_html_app"
    ) || server in setOf(
        "builtin_app_developer",
        "app_builder",
        "internal_app_developer"
    )
}

private fun isHanChar(ch: Char): Boolean {
    return Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN
}

private fun normalizeAppDisplayName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "App"

    val hasHan = trimmed.any(::isHanChar)
    val hasLatin = trimmed.any { it in 'A'..'Z' || it in 'a'..'z' }
    if (!hasHan || !hasLatin) {
        return trimmed.take(80)
    }

    val separators = listOf("/", "|", "｜", " - ", " — ", " – ", "·")
    separators.forEach { sep ->
        val parts = trimmed.split(sep).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return@forEach
        val pureHan = parts.firstOrNull { p -> p.any(::isHanChar) && p.none { it in 'A'..'Z' || it in 'a'..'z' } }
        val pureLatin = parts.firstOrNull { p -> p.any { it in 'A'..'Z' || it in 'a'..'z' } && p.none(::isHanChar) }
        val chosen = pureHan ?: pureLatin ?: parts.first()
        return chosen.take(80)
    }

    val hanCount = trimmed.count(::isHanChar)
    val latinCount = trimmed.count { it in 'A'..'Z' || it in 'a'..'z' }
    val keepHan = hanCount >= latinCount
    val filtered =
        buildString {
            trimmed.forEach { ch ->
                val keep =
                    if (keepHan) {
                        isHanChar(ch) || ch.isDigit() || ch.isWhitespace() || ch in setOf('-', '_')
                    } else {
                        (ch in 'A'..'Z') || (ch in 'a'..'z') || ch.isDigit() || ch.isWhitespace() || ch in setOf('-', '_')
                    }
                if (keep) append(ch)
            }
        }.trim()
    return filtered.ifBlank { trimmed }.take(80)
}

private fun parseAppDevToolSpec(arguments: Map<String, Any?>): AppDevToolSpec? {
    fun anyString(vararg keys: String): String {
        return keys.asSequence()
            .mapNotNull { key ->
                val value = arguments.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: return@mapNotNull null
                when (value) {
                    is String -> value.trim().takeIf { it.isNotBlank() }
                    else -> value?.toString()?.trim()?.takeIf { it.isNotBlank() }
                }
            }
            .firstOrNull()
            .orEmpty()
    }

    fun normalizeMetaKey(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
    }

    fun splitFeatureCandidates(text: String): List<String> {
        return text
            .split('\n', ',', '，', ';', '；', '、', '|')
            .map { it.trim().trimStart('-', '*', '•', '·') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
    }

    fun inferFeaturesFromDescription(text: String): List<String> {
        val bullets =
            Regex("(?m)^\\s*(?:[-*•·]|\\d+[\\.)、])\\s*(.+)$")
                .findAll(text)
                .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { value -> value.isNotBlank() } }
                .toList()
        if (bullets.isNotEmpty()) return bullets.take(12)

        val lineChunks =
            text.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .flatMap { line ->
                    line.split('。', '.', '！', '!', '；', ';', '？', '?')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
        if (lineChunks.isNotEmpty()) {
            return lineChunks
                .map { it.take(120) }
                .distinct()
                .take(12)
        }
        return emptyList()
    }

    val nameRaw = anyString("name")
    val rawDescription = anyString("description")
    val name = normalizeAppDisplayName(nameRaw)
    if (name.isBlank() || rawDescription.isBlank()) return null

    var modeRaw = ""
    var targetAppId = ""
    var targetAppName = ""
    var editRequest = ""
    var style = ""

    val explicitFeatures = emptyList<String>()
    val parsedFeatures = mutableListOf<String>()
    val keptDescriptionLines = mutableListOf<String>()
    val metaLineRegex = Regex("^\\s*([A-Za-z0-9_\\-\\u4e00-\\u9fa5 ]{2,36})\\s*[:：]\\s*(.+)$")

    rawDescription
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.isBlank()) return@forEach

            val meta = metaLineRegex.matchEntire(line)
            if (meta == null) {
                keptDescriptionLines += line
                return@forEach
            }

            val key = normalizeMetaKey(meta.groupValues[1])
            val value = meta.groupValues[2].trim()
            if (value.isBlank()) {
                keptDescriptionLines += line
                return@forEach
            }

            when (key) {
                "mode", "operation", "intent", "action", "模式", "操作", "意图" -> modeRaw = value
                "targetappid", "appid", "existingappid", "sourceappid", "目标应用id", "应用id" -> targetAppId = value
                "targetappname", "existingappname", "apptoedit", "editappname", "目标应用名", "应用名" -> targetAppName = value
                "editrequest", "request", "updaterequest", "changerequest", "instruction", "prompt", "修改需求", "编辑需求", "修复需求", "更新需求", "请求" ->
                    editRequest = value
                "style", "theme", "visualstyle", "design", "风格", "样式", "主题", "设计" -> style = value
                "features", "requirements", "specs", "functionalities", "功能", "特性", "需求", "细节" ->
                    parsedFeatures += splitFeatureCandidates(value)
                else -> keptDescriptionLines += line
            }
        }

    val cleanedDescription =
        keptDescriptionLines.joinToString("\n")
            .trim()
            .ifBlank { rawDescription.trim() }
            .take(3000)
    if (cleanedDescription.isBlank()) return null

    var mode =
        when (modeRaw.trim().lowercase()) {
            "edit", "update", "revise", "modify", "refactor", "patch", "fix", "debug_fix", "修复", "编辑", "修改", "更新", "优化" -> "edit"
            else -> "create"
        }
    if (mode == "create") {
        val hasEditHints =
            editRequest.isNotBlank() ||
                targetAppId.isNotBlank() ||
                targetAppName.isNotBlank() ||
                Regex("(?i)\\b(edit|update|revise|modify|refactor|patch|fix|debug)\\b").containsMatchIn(cleanedDescription) ||
                Regex("(编辑|修改|更新|优化|重构|修复)").containsMatchIn(cleanedDescription)
        if (hasEditHints) mode = "edit"
    }

    val features =
        (explicitFeatures + parsedFeatures + inferFeaturesFromDescription(cleanedDescription))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)

    if (mode == "edit") {
        val effectiveEditRequest = editRequest.trim().ifBlank { cleanedDescription }
        if (effectiveEditRequest.isBlank()) return null
        return AppDevToolSpec(
            mode = "edit",
            name = normalizeAppDisplayName(name).take(80),
            description = cleanedDescription.take(1200),
            style = style.take(180),
            features = features.ifEmpty { splitFeatureCandidates(effectiveEditRequest) }.take(12),
            targetAppId = targetAppId.takeIf { it.isNotBlank() }?.take(160),
            targetAppName = targetAppName.takeIf { it.isNotBlank() }?.take(80),
            editRequest = effectiveEditRequest.take(1600)
        )
    }

    val createFeatures = features.ifEmpty { listOf(cleanedDescription.take(140)) }
    return AppDevToolSpec(
        mode = "create",
        name = normalizeAppDisplayName(name).take(80),
        description = cleanedDescription.take(1800),
        style = style.take(180),
        features = createFeatures.take(12),
        targetAppId = targetAppId.takeIf { it.isNotBlank() }?.take(160),
        targetAppName = targetAppName.takeIf { it.isNotBlank() }?.take(80),
        editRequest = editRequest.takeIf { it.isNotBlank() }?.take(1600)
    )
}

private fun encodeAppDevTagPayload(payload: AppDevTagPayload): String {
    return GsonBuilder().disableHtmlEscaping().create().toJson(payload)
}

private fun parseAppDevTagPayload(
    content: String,
    fallbackName: String,
    fallbackStatus: String?
): AppDevTagPayload {
    val json = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
    val name =
        json?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName
    val subtitleRaw =
        json?.get("subtitle")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Developing app"
    val descriptionRaw = json?.get("description")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
    val style = json?.get("style")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
    val features =
        json?.getAsJsonArray("features")
            ?.mapNotNull { runCatching { it.asString.trim() }.getOrNull()?.takeIf { t -> t.isNotBlank() } }
            .orEmpty()
    val progress = json?.get("progress")?.takeIf { it.isJsonPrimitive }?.asInt ?: if (fallbackStatus == "success") 100 else 0
    val status = json?.get("status")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty().ifBlank { fallbackStatus.orEmpty() }
    val html = json?.get("html")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val error = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }
    val sourceAppId = json?.get("sourceAppId")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }
    val mode = json?.get("mode")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() } ?: "create"
    val deployUrl =
        json?.get("deployUrl")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val deployError =
        json?.get("deployError")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeStatus =
        json?.get("runtimeStatus")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
    val runtimeMessage =
        json?.get("runtimeMessage")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeRunUrl =
        json?.get("runtimeRunUrl")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeArtifactName =
        json?.get("runtimeArtifactName")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeArtifactUrl =
        json?.get("runtimeArtifactUrl")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val compactDescription = compactAppDescription(descriptionRaw, subtitleRaw)
    val compactSubtitle = compactAppDescription(subtitleRaw, compactDescription)
    return AppDevTagPayload(
        name = normalizeAppDisplayName(name),
        subtitle = compactSubtitle,
        description = compactDescription,
        style = style,
        features = features,
        progress = progress.coerceIn(0, 100),
        status = status,
        html = html,
        error = error,
        sourceAppId = sourceAppId,
        mode = mode,
        deployUrl = deployUrl,
        deployError = deployError,
        runtimeStatus = runtimeStatus,
        runtimeMessage = runtimeMessage,
        runtimeRunUrl = runtimeRunUrl,
        runtimeArtifactName = runtimeArtifactName,
        runtimeArtifactUrl = runtimeArtifactUrl
    )
}

private fun compactAppDescription(primary: String, fallback: String = "Developing app"): String {
    val raw =
        primary
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
    val simplified = raw.trimEnd('.', '。', '!', '！', '?', '？')
    if (simplified.isBlank()) {
        return fallback.trim().ifBlank { "Developing app" }.take(56)
    }
    return simplified.take(56)
}

private fun resolveExistingSavedApp(
    spec: AppDevToolSpec,
    savedApps: List<SavedApp>
): SavedApp? {
    if (savedApps.isEmpty()) return null
    if (spec.mode != "edit") return null

    val byId = spec.targetAppId?.trim().orEmpty()
    if (byId.isNotBlank()) {
        if (byId.equals("latest", ignoreCase = true) || byId.equals("last", ignoreCase = true)) {
            return savedApps.maxByOrNull { it.updatedAt }
        }
        savedApps.firstOrNull { it.id.equals(byId, ignoreCase = true) }?.let { return it }
    }

    val candidates = mutableListOf<String>()
    spec.targetAppName?.trim()?.takeIf { it.isNotBlank() }?.let { candidates += it }
    spec.name.trim().takeIf { it.isNotBlank() && !it.equals("app", ignoreCase = true) }?.let { candidates += it }

    candidates.forEach { keyword ->
        savedApps.firstOrNull { it.name.trim().equals(keyword, ignoreCase = true) }?.let { return it }
    }
    candidates.forEach { keyword ->
        val lower = keyword.lowercase()
        val byContain = savedApps.filter { it.name.lowercase().contains(lower) }
        if (byContain.size == 1) return byContain.first()
    }

    return if (savedApps.size == 1) savedApps.first() else null
}

private fun summarizeSavedAppsForInstruction(savedApps: List<SavedApp>, limit: Int = 10): String {
    if (savedApps.isEmpty()) return "No saved apps."
    return buildString {
        savedApps
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .forEachIndexed { index, app ->
                append(index + 1)
                append(". id=")
                append(app.id)
                append(" | name=")
                append(normalizeAppDisplayName(app.name).take(80))
                val desc = compactAppDescription(app.description, "")
                if (desc.isNotBlank()) {
                    append(" | desc=")
                    append(desc.take(80))
                }
                append('\n')
            }
    }.trimEnd()
}

private fun shouldUseIos18UiSkill(
    style: String,
    description: String,
    features: List<String>,
    requestText: String = ""
): Boolean {
    val signal =
        buildString {
            append(style)
            append('\n')
            append(description)
            append('\n')
            append(features.joinToString(" "))
            append('\n')
            append(requestText)
        }.lowercase()
    if (signal.isBlank()) return false

    val markers =
        listOf(
            "ios",
            "ios18",
            "ios 18",
            "iphone",
            "ipad",
            "cupertino",
            "apple",
            "human interface",
            "sf pro",
            "inset grouped",
            "tab bar",
            "large title"
        )
    return markers.any { marker -> signal.contains(marker) }
}

private val APP_DEVELOPER_REQUIRED_SYSTEM_PROMPT = APP_DEVELOPER_BASELINE_SYSTEM_PROMPT

@Suppress("UNUSED_PARAMETER")
private fun buildAppGenerationSystemPrompt(useIos18Skill: Boolean): String {
    return APP_DEVELOPER_REQUIRED_SYSTEM_PROMPT
}

private fun buildAppGenerationUserPrompt(
    spec: AppDevToolSpec,
    useIos18Skill: Boolean,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = ""
): String {
    return buildString {
        val rawUser = sourceUserPrompt.trim()
        val rawArgs = rawToolArgsJson.trim()
        if (rawUser.isNotBlank()) {
            appendLine("Original user request (preserve this intent with highest priority):")
            appendLine(rawUser.take(2400))
            appendLine()
        }
        if (rawArgs.isNotBlank()) {
            appendLine("Raw tool arguments JSON:")
            appendLine(rawArgs.take(2400))
            appendLine()
        }
        appendLine("Build one HTML app with these specs:")
        append("Name: ")
        appendLine(spec.name)
        appendLine("Detailed description:")
        appendLine(spec.description)
        if (spec.style.isNotBlank()) {
            appendLine()
            append("Extracted style hint: ")
            appendLine(spec.style)
        }
        if (spec.features.isNotEmpty()) {
            appendLine("Extracted feature checklist:")
            spec.features.forEachIndexed { index, feature ->
                append(index + 1)
                append(". ")
                appendLine(feature)
            }
        }
        appendLine()
        appendLine("Hard requirements:")
        appendLine("- Include all requested features with real, working interactions.")
        appendLine("- Mobile + desktop responsive.")
        appendLine("- Keep the app name in one language only (Chinese OR English, not both).")
        appendLine("- Keep copy short and practical.")
        appendLine("- Do not use placeholder/mock content unless explicitly requested.")
        appendLine("- Use Lucide icon(s) and keep one explicit app icon in the primary header/home area.")
        appendLine("- Reserve top-right safe-area host overlay zone and avoid fixed controls in that collision region.")
        if (useIos18Skill) {
            appendLine("- Follow iOS 18 visual and component constraints from the active skill package.")
        }
    }.trim()
}

@Suppress("UNUSED_PARAMETER")
private fun buildAppRevisionSystemPrompt(useIos18Skill: Boolean): String {
    return APP_DEVELOPER_REQUIRED_SYSTEM_PROMPT
}

private fun buildAppRevisionUserPrompt(
    currentHtml: String,
    requestText: String,
    spec: AppDevToolSpec?,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = ""
): String {
    return buildString {
        val rawUser = sourceUserPrompt.trim()
        val rawArgs = rawToolArgsJson.trim()
        if (rawUser.isNotBlank()) {
            appendLine("Original user request (preserve this intent with highest priority):")
            appendLine(rawUser.take(2400))
            appendLine()
        }
        if (rawArgs.isNotBlank()) {
            appendLine("Raw tool arguments JSON:")
            appendLine(rawArgs.take(2400))
            appendLine()
        }
        appendLine("Update this app based on the request.")
        append("Request: ")
        appendLine(requestText.trim())
        if (spec != null) {
            if (spec.name.isNotBlank()) {
                append("Target app name: ")
                appendLine(spec.name)
            }
            if (spec.style.isNotBlank()) {
                append("Target style: ")
                appendLine(spec.style)
            }
            if (spec.features.isNotEmpty()) {
                appendLine("Target feature deltas:")
                spec.features.forEachIndexed { index, item ->
                    append(index + 1)
                    append(". ")
                    appendLine(item)
                }
            }
        }
        appendLine()
        appendLine("Revision rules:")
        appendLine("- Keep unchanged modules intact.")
        appendLine("- If replacing a component, maintain equivalent UX behavior.")
        appendLine("- Keep interactions complete (no TODO/placeholder handlers).")
        appendLine("- Keep one explicit Lucide app icon in the primary header/home area.")
        appendLine("- Reserve top-right safe-area host overlay zone and avoid fixed controls in that collision region.")
        appendLine("- Return a full HTML file, not a patch.")
        appendLine()
        appendLine("Current HTML:")
        appendLine(currentHtml.trim())
    }.trim()
}

private suspend fun generateHtmlAppFromSpec(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    extraHeaders: List<HttpHeader>,
    spec: AppDevToolSpec,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = "",
    onProgress: ((Int) -> Unit)? = null,
    onDraftHtml: ((String) -> Unit)? = null
): String {
    val useIos18Skill =
        shouldUseIos18UiSkill(
            style = spec.style,
            description = spec.description,
            features = spec.features
        )
    val systemPrompt = buildAppGenerationSystemPrompt(useIos18Skill)
    val userPrompt =
        buildAppGenerationUserPrompt(
            spec = spec,
            useIos18Skill = useIos18Skill,
            sourceUserPrompt = sourceUserPrompt,
            rawToolArgsJson = rawToolArgsJson
        )

    var emittedProgress = 10
    var chunkCount = 0
    var charCount = 0
    val startedAtMs = System.currentTimeMillis()
    var lastDraftUpdateAtMs = 0L
    val draftBuilder = StringBuilder()
    onProgress?.invoke(emittedProgress)

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userPrompt)
            ),
            extraHeaders = extraHeaders,
            onChunk = { chunk ->
                draftBuilder.append(chunk)
                chunkCount += 1
                charCount += chunk.length
                val elapsedBoost = ((System.currentTimeMillis() - startedAtMs) / 550L).toInt().coerceAtMost(18)
                val chunkBoost = (chunkCount * 3).coerceAtMost(48)
                val sizeBoost = (charCount / 140).coerceAtMost(18)
                val nextProgress = (10 + elapsedBoost + chunkBoost + sizeBoost).coerceAtMost(94)
                if (nextProgress > emittedProgress) {
                    emittedProgress = nextProgress
                    onProgress?.invoke(nextProgress)
                }
                val now = System.currentTimeMillis()
                if (now - lastDraftUpdateAtMs >= 180L) {
                    onDraftHtml?.invoke(draftBuilder.toString())
                    lastDraftUpdateAtMs = now
                }
            }
        )
    onDraftHtml?.invoke(draftBuilder.toString())
    if (emittedProgress < 94) {
        onProgress?.invoke(94)
    }
    return normalizeGeneratedHtml(raw)
}

private suspend fun reviseHtmlAppFromPrompt(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    extraHeaders: List<HttpHeader>,
    currentHtml: String,
    requestText: String,
    spec: AppDevToolSpec? = null,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = "",
    onProgress: ((Int) -> Unit)? = null,
    onDraftHtml: ((String) -> Unit)? = null
): String {
    val current = currentHtml.trim()
    if (current.isBlank()) {
        error("Current HTML is empty.")
    }
    val request = requestText.trim()
    if (request.isBlank()) {
        error("Update request is empty.")
    }

    val useIos18Skill =
        shouldUseIos18UiSkill(
            style = spec?.style.orEmpty(),
            description = spec?.description.orEmpty(),
            features = spec?.features.orEmpty(),
            requestText = request
        )
    val systemPrompt = buildAppRevisionSystemPrompt(useIos18Skill)
    val userPrompt =
        buildAppRevisionUserPrompt(
            currentHtml = current,
            requestText = request,
            spec = spec,
            sourceUserPrompt = sourceUserPrompt,
            rawToolArgsJson = rawToolArgsJson
        )

    var emittedProgress = 12
    var chunkCount = 0
    var charCount = 0
    val startedAtMs = System.currentTimeMillis()
    var lastDraftUpdateAtMs = 0L
    val draftBuilder = StringBuilder()
    onProgress?.invoke(emittedProgress)

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userPrompt)
            ),
            extraHeaders = extraHeaders,
            onChunk = { chunk ->
                draftBuilder.append(chunk)
                chunkCount += 1
                charCount += chunk.length
                val elapsedBoost = ((System.currentTimeMillis() - startedAtMs) / 520L).toInt().coerceAtMost(16)
                val chunkBoost = (chunkCount * 3).coerceAtMost(50)
                val sizeBoost = (charCount / 150).coerceAtMost(16)
                val nextProgress = (12 + elapsedBoost + chunkBoost + sizeBoost).coerceAtMost(94)
                if (nextProgress > emittedProgress) {
                    emittedProgress = nextProgress
                    onProgress?.invoke(nextProgress)
                }
                val now = System.currentTimeMillis()
                if (now - lastDraftUpdateAtMs >= 180L) {
                    onDraftHtml?.invoke(draftBuilder.toString())
                    lastDraftUpdateAtMs = now
                }
            }
        )
    onDraftHtml?.invoke(draftBuilder.toString())
    if (emittedProgress < 94) {
        onProgress?.invoke(94)
    }
    return normalizeGeneratedHtml(raw)
}

private fun normalizeGeneratedHtml(raw: String): String {
    val normalized = normalizeGeneratedHtmlCandidate(raw, wrapIfNeeded = true)
    if (normalized.isBlank()) error("Generated HTML is empty.")
    return normalized
}

private fun normalizeGeneratedHtmlDraft(raw: String): String {
    return normalizeGeneratedHtmlCandidate(raw, wrapIfNeeded = false)
}

private fun normalizeGeneratedHtmlCandidate(raw: String, wrapIfNeeded: Boolean): String {
    val extractedPayload = extractGeneratedHtmlPayload(raw)
    val decoded = decodeEscapedHtmlSequences(extractedPayload)
    val normalizedNewline = decoded.replace("\r\n", "\n").replace("\r", "\n").trim()
    if (normalizedNewline.isBlank()) return ""

    val htmlSegment = extractHtmlDocumentSegment(normalizedNewline)
    val cleaned = stripMarkdownCodeFences(htmlSegment).trim()
    if (cleaned.isBlank()) return ""

    val lower = cleaned.lowercase()
    if (lower.startsWith("<!doctype html") || lower.contains("<html")) {
        return cleaned
    }

    if (!wrapIfNeeded) return cleaned

    return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\" />\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n<title>Generated App</title>\n</head>\n<body>\n$cleaned\n</body>\n</html>"
}

private fun extractGeneratedHtmlPayload(raw: String): String {
    val stripped = stripMarkdownCodeFences(raw).trim()
    if (stripped.isBlank()) return ""

    val parsed = runCatching { JsonParser.parseString(stripped) }.getOrNull()
    if (parsed == null) return stripped

    if (parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString) {
        return parsed.asString
    }
    if (parsed.isJsonObject) {
        val obj = parsed.asJsonObject
        val keys = listOf("html", "code", "content", "result", "output")
        keys.forEach { key ->
            val value = obj.get(key)
            if (value != null && value.isJsonPrimitive) {
                val text = value.asString.trim()
                if (text.isNotBlank()) return text
            }
        }
    }
    return stripped
}

private fun decodeEscapedHtmlSequences(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""

    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
        val decodedQuoted =
            runCatching { JsonParser.parseString(trimmed) }
                .getOrNull()
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
        if (!decodedQuoted.isNullOrBlank()) return decodedQuoted
    }

    val looksEscaped =
        trimmed.contains("\\n") ||
            trimmed.contains("\\r") ||
            trimmed.contains("\\t") ||
            trimmed.contains("\\u003", ignoreCase = true) ||
            trimmed.contains("\\\"") ||
            trimmed.contains("\\/")
    if (!looksEscaped) return trimmed

    return trimmed
        .replace(Regex("\\\\u003[cC]"), "<")
        .replace(Regex("\\\\u003[eE]"), ">")
        .replace(Regex("\\\\u0026"), "&")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .trim()
}

private fun extractHtmlDocumentSegment(raw: String): String {
    val source = raw.trim()
    if (source.isBlank()) return ""

    val lower = source.lowercase()
    val doctypeIndex = lower.indexOf("<!doctype html")
    val htmlIndex = lower.indexOf("<html")
    val start =
        when {
            doctypeIndex >= 0 -> doctypeIndex
            htmlIndex >= 0 -> htmlIndex
            else -> -1
        }
    if (start < 0) return source

    val closingTag = "</html>"
    val end = lower.lastIndexOf(closingTag)
    if (end > start) {
        return source.substring(start, end + closingTag.length).trim()
    }
    return source.substring(start).trim()
}

private fun buildMcpCallSignature(call: PlannedMcpToolCall): String {
    val normalizedServer = call.serverId.trim().lowercase()
    val normalizedTool = call.toolName.trim().lowercase()
    val argsPart =
        call.arguments.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) ->
                val key = k.trim().lowercase()
                val value = v?.toString()?.trim().orEmpty().lowercase()
                "$key=$value"
            }
    return "$normalizedServer|$normalizedTool|$argsPart"
}

private fun toMcpFailureResult(error: Throwable): com.zionchat.app.data.McpToolResult {
    val message = error.message.orEmpty().trim().ifBlank { "Tool call failed." }
    return com.zionchat.app.data.McpToolResult(
        success = false,
        content = message,
        error = message
    )
}

private fun extractExplicitApiError(errorText: String): String? {
    val raw = errorText.trim()
    if (raw.isBlank()) return null
    val lower = raw.lowercase()

    val hasHttpLikeSignal =
        (lower.contains("http") || lower.contains("status") || lower.contains("api")) &&
            Regex("\\b(4\\d\\d|5\\d\\d)\\b").containsMatchIn(lower)
    val hasApiKeywords =
        listOf(
            "api error",
            "bad request",
            "unauthorized",
            "forbidden",
            "too many requests",
            "invalid request",
            "internal server error",
            "service unavailable",
            "gateway timeout"
        ).any { marker -> lower.contains(marker) }

    val jsonError =
        runCatching {
            val json = JsonParser.parseString(raw)
            if (!json.isJsonObject) return@runCatching null
            val obj = json.asJsonObject
            val message = obj.get("message")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val error = obj.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val code = obj.get("code")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            listOf(error, message, code)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
                .takeIf { it.isNotBlank() }
        }.getOrNull()

    if (!jsonError.isNullOrBlank()) return jsonError.take(1000)
    if (hasHttpLikeSignal || hasApiKeywords) return raw.take(1000)
    return null
}

private fun buildMcpTagDetailContent(
    round: Int,
    serverName: String?,
    toolName: String,
    argumentsJson: String,
    statusText: String,
    attempts: Int?,
    elapsedMs: Long?,
    resultText: String?,
    errorText: String?
): String {
    return buildString {
        append("Round: ")
        append(round)
        append('\n')
        append("Status: ")
        append(statusText.trim())
        append('\n')
        if (!serverName.isNullOrBlank()) {
            append("Server: ")
            append(serverName.trim())
            append('\n')
        }
        append("Tool: ")
        append(toolName.trim())
        append('\n')
        attempts?.takeIf { it > 0 }?.let {
            append("Attempts: ")
            append(it)
            append('\n')
        }
        elapsedMs?.takeIf { it > 0 }?.let {
            append("Elapsed: ")
            append(formatElapsedDuration(it))
            append('\n')
        }
        append('\n')
        append("Arguments:\n```json\n")
        append(argumentsJson.trim())
        append("\n```")

        resultText?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            append("\n\nResult:\n")
            append(text)
        }
        errorText?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            append("\n\nError:\n")
            append(text)
        }
    }.trim()
}

private data class McpTagDetailView(
    val round: String = "-",
    val status: String = "",
    val server: String = "",
    val tool: String = "",
    val attempts: String = "",
    val elapsed: String = "",
    val argumentsJson: String = "",
    val result: String = "",
    val error: String = ""
)

private fun parseMcpTagDetail(content: String): McpTagDetailView {
    val lines = content.lineSequence().map { it.trim() }.toList()
    fun value(prefix: String): String {
        return lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
    }

    fun block(startMarker: String, endMarker: String? = null): String {
        val startIndex = content.indexOf(startMarker)
        if (startIndex < 0) return ""
        val rawStart = startIndex + startMarker.length
        val endIndex = endMarker?.let { marker -> content.indexOf(marker, startIndex = rawStart).takeIf { it >= 0 } }
            ?: content.length
        return content.substring(rawStart, endIndex).trim()
    }

    val args =
        Regex("(?s)Arguments:\\s*```json\\s*(.*?)\\s*```")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    val result = block("Result:", "\n\nError:")
    val error = block("Error:")

    return McpTagDetailView(
        round = value("Round:").ifBlank { "-" },
        status = value("Status:"),
        server = value("Server:"),
        tool = value("Tool:"),
        attempts = value("Attempts:"),
        elapsed = value("Elapsed:"),
        argumentsJson = args,
        result = result,
        error = error
    )
}

private fun isTagRunning(tag: MessageTag): Boolean {
    return when (tag.status?.trim()?.lowercase()) {
        "running" -> true
        "success", "error" -> false
        else -> {
            val lower = tag.content.lowercase()
            lower.contains("status: running") || lower.contains("retrying")
        }
    }
}

private fun formatElapsedDuration(elapsedMs: Long): String {
    if (elapsedMs <= 0L) return "0.0s"
    val seconds = elapsedMs / 1000.0
    return String.format(java.util.Locale.US, "%.1fs", seconds)
}

private fun formatToolDetailJson(rawText: String): String {
    val raw = rawText.trim()
    if (raw.isBlank()) return "{}"
    val cleaned = stripMarkdownCodeFences(raw)
    val firstChar = cleaned.firstOrNull()
    if (firstChar != '{' && firstChar != '[') return cleaned
    return runCatching {
        GsonBuilder().setPrettyPrinting().create()
            .toJson(JsonParser.parseString(cleaned))
    }.getOrDefault(cleaned)
}

private data class ThinkingSplitResult(
    val visible: String,
    val thinking: String
)

private data class McpTagAnchor(
    val tagId: String,
    val charIndex: Int
)

private data class InlineMcpCallExtraction(
    val visibleText: String,
    val blocks: List<String>
)

private data class MessageInlineSegment(
    val text: String? = null,
    val tagId: String? = null
)

private val mcpTagMarkerRegex =
    Regex("(?is)<!--\\s*mcp_tag:([A-Za-z0-9_-]+)\\s*-->")

private fun buildMcpTagMarker(tagId: String): String {
    return "<!--mcp_tag:${tagId.trim()}-->"
}

private fun insertMcpTagMarkers(visibleContent: String, anchors: List<McpTagAnchor>): String {
    if (anchors.isEmpty()) return visibleContent
    val content = visibleContent
    val sortedAnchors = anchors.sortedBy { it.charIndex }
    val sb = StringBuilder()
    var cursor = 0

    sortedAnchors.forEach { anchor ->
        val safeId = anchor.tagId.trim()
        if (safeId.isBlank()) return@forEach
        val index = anchor.charIndex.coerceIn(0, content.length)
        if (index > cursor) {
            sb.append(content.substring(cursor, index))
        }
        if (sb.isNotEmpty() && sb.last() != '\n') {
            sb.append("\n\n")
        } else if (sb.isNotEmpty()) {
            sb.append('\n')
        }
        sb.append(buildMcpTagMarker(safeId))
        sb.append("\n\n")
        cursor = index
    }

    if (cursor < content.length) {
        sb.append(content.substring(cursor))
    }
    return sb.toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun splitContentByMcpTagMarkers(content: String): List<MessageInlineSegment> {
    if (!mcpTagMarkerRegex.containsMatchIn(content)) return emptyList()
    val result = mutableListOf<MessageInlineSegment>()
    var cursor = 0

    mcpTagMarkerRegex.findAll(content).forEach { match ->
        if (match.range.first > cursor) {
            val text = content.substring(cursor, match.range.first).trim()
            if (text.isNotBlank()) {
                result += MessageInlineSegment(text = text)
            }
        }
        val tagId = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (tagId.isNotBlank()) {
            result += MessageInlineSegment(tagId = tagId)
        }
        cursor = match.range.last + 1
    }

    if (cursor < content.length) {
        val tail = content.substring(cursor).trim()
        if (tail.isNotBlank()) {
            result += MessageInlineSegment(text = tail)
        }
    }
    return result
}

private fun stripMcpTagMarkers(content: String): String {
    if (content.isBlank()) return ""
    return content
        .replace(mcpTagMarkerRegex, " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun appendTextSection(target: StringBuilder, text: String) {
    val cleaned = text.trim()
    if (cleaned.isBlank()) return
    if (target.isNotEmpty()) target.append("\n\n")
    target.append(cleaned)
}

private fun mergeTextSections(existing: String, incoming: String): String {
    val left = existing.trim()
    val right = incoming.trim()
    return when {
        left.isBlank() -> right
        right.isBlank() -> left
        else -> "$left\n\n$right"
    }
}

private fun splitThinkingFromContent(content: String): ThinkingSplitResult {
    val raw = content.trim()
    if (raw.isBlank()) return ThinkingSplitResult(visible = "", thinking = "")

    val blockRegex = Regex("(?is)<(?:think|thinking)>(.*?)</(?:think|thinking)>")
    val thinkingBlocks =
        blockRegex.findAll(raw)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
            .toList()

    val visible =
        raw.replace(blockRegex, " ")
            .replace(Regex("(?i)</?(?:think|thinking)>"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    return ThinkingSplitResult(
        visible = visible,
        thinking = thinkingBlocks.joinToString("\n\n").trim()
    )
}

private fun extractInlineMcpCallBlocks(content: String): InlineMcpCallExtraction {
    val raw = content.trim()
    if (raw.isBlank()) {
        return InlineMcpCallExtraction(visibleText = "", blocks = emptyList())
    }

    val blockRegex = Regex(
        "(?is)<(?:mcp_call|tool_call)\\b[^>]*>(.*?)(?:</(?:mcp_call|tool_call)>|$)"
    )
    val blocks =
        blockRegex.findAll(raw)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
            .toList()

    val visible =
        raw.replace(blockRegex, " ")
            .replace(Regex("(?is)</(?:mcp_call|tool_call)>"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    return InlineMcpCallExtraction(visibleText = visible, blocks = blocks)
}

private fun buildAppDeveloperToolInstruction(
    roundIndex: Int,
    maxCallsPerRound: Int,
    savedApps: List<SavedApp>
): String {
    return buildString {
        appendLine("Built-in tool available: app_developer.")
        appendLine("Current round: $roundIndex")
        appendLine("Use this tool ONLY when the user explicitly asks to create/build/develop an app or HTML page.")
        appendLine("Do not call this tool for generic Q&A.")
        appendLine("First write a normal visible reply, then append tool call tags if needed.")
        appendLine("At most $maxCallsPerRound tool calls in this round.")
        appendLine()
        appendLine("Saved apps context:")
        appendLine(summarizeSavedAppsForInstruction(savedApps))
        appendLine()
        appendLine("Tool call format:")
        appendLine("<tool_call>{\"serverId\":\"builtin_app_developer\",\"toolName\":\"app_developer\",\"arguments\":{...}}</tool_call>")
        appendLine()
        appendLine("Arguments policy (STRICT):")
        appendLine("- ONLY 2 arguments are allowed:")
        appendLine("  1) name: app name in ONE language only (Chinese OR English), never bilingual")
        appendLine("  2) description: detailed product spec. Include style, layout, interactions, states, and edge cases.")
        appendLine("     description must include a Lucide app icon choice and placement.")
        appendLine("     description must reserve top-right host overlay safe-area (avoid fixed controls in that corner).")
        appendLine("- Do NOT emit style/features/mode as top-level arguments.")
        appendLine("- If editing an existing app, still only use name + description.")
        appendLine("- For edit intent, write metadata lines INSIDE description:")
        appendLine("  operation: edit")
        appendLine("  target_app_id: <id from saved apps context> (or target_app_name)")
        appendLine("  request: <detailed modifications>")
        appendLine("- All declared UI interactions must be real and fully functional (no dead buttons/links).")
        appendLine("- If description mentions iOS/iPhone/Cupertino/iOS 18, enforce strict iOS 18 HIG constraints.")
    }.trim()
}

private fun buildPendingAppAutomationPrompt(task: AppAutomationTask): String {
    val request = task.request.trim()
    val html = task.appHtml.trim()
    if (request.isBlank() || html.isBlank()) return ""
    val mode = task.mode.trim().lowercase()
    val appName = normalizeAppDisplayName(task.appName.trim().ifBlank { "App" })
    val appId = task.appId.trim().ifBlank { "unknown-app-id" }
    val htmlPayload = html.take(180_000)

    return buildString {
        if (mode == "debug_fix") {
            appendLine("Please fix an existing saved HTML app.")
        } else {
            appendLine("Please edit an existing saved HTML app.")
        }
        appendLine("Do NOT create a new app. Update the target app in-place.")
        appendLine("Use app_developer tool with ONLY two arguments: name + description.")
        appendLine()
        appendLine("Required tool arguments:")
        appendLine("- name: one-language app name only (Chinese OR English)")
        appendLine("- description: detailed spec and include metadata lines:")
        appendLine("  app_icon: <lucide icon name and placement>")
        appendLine("  safe_zone: reserve top-right host overlay region")
        appendLine("  operation: edit")
        append("  target_app_id: ")
        appendLine(appId)
        append("  target_app_name: ")
        appendLine(appName)
        appendLine("  request: <the modification request below>")
        appendLine("- Do not add any other top-level arguments.")
        appendLine()
        appendLine(if (mode == "debug_fix") "Bug report:" else "Edit request:")
        appendLine(request)
        appendLine()
        appendLine("Hard constraints:")
        appendLine("- No mock/placeholder/simulated-only interactions.")
        appendLine("- Every button/link/tab/form control must have real behavior.")
        appendLine("- Preserve unaffected working modules.")
        appendLine("- Return one full updated HTML document.")
        appendLine()
        appendLine("Current app HTML:")
        appendLine(htmlPayload)
    }.trim()
}

private fun buildMcpToolCallInstruction(
    servers: List<McpConfig>,
    roundIndex: Int,
    maxCallsPerRound: Int
): String {
    val maxToolsPerServer = 24

    return buildString {
        appendLine("You can use MCP tools in this round.")
        appendLine("Current round: $roundIndex")
        appendLine("First write a normal visible reply for the user.")
        appendLine("If tool calls are needed, append tags AFTER your visible reply:")
        appendLine("<mcp_call>{\"serverId\":\"...\",\"toolName\":\"...\",\"arguments\":{}}</mcp_call>")
        appendLine()
        appendLine("Rules:")
        appendLine("- If no tool is needed, do not output any mcp_call tags.")
        appendLine("- At most $maxCallsPerRound tool calls in this round.")
        appendLine("- toolName must match exactly from the list below.")
        appendLine("- arguments must be a JSON object.")
        appendLine("- Do not mention planning, routers, or internal logic.")
        appendLine()
        appendLine("Available MCP servers and tools:")

        servers.forEach { server ->
            append("- Server: ")
            append(server.name.trim().ifBlank { "Unnamed" })
            append(" (id=")
            append(server.id)
            appendLine(")")

            server.tools.take(maxToolsPerServer).forEach { tool ->
                append("  - Tool: ")
                appendLine(tool.name)
                val desc = tool.description.trim()
                if (desc.isNotBlank()) {
                    append("    Description: ")
                    appendLine(desc.take(240))
                }
                if (tool.parameters.isNotEmpty()) {
                    appendLine("    Parameters:")
                    tool.parameters.forEach { param ->
                        append("      - ")
                        append(param.name)
                        append(" (")
                        append(param.type)
                        append(")")
                        if (param.required) append(" [required]")
                        val pDesc = param.description.trim()
                        if (pDesc.isNotBlank()) {
                            append(": ")
                            append(pDesc.take(180))
                        }
                        appendLine()
                    }
                }
            }
        }
    }.trim()
}

private fun buildMcpRoundResultContext(roundIndex: Int, summary: String): String {
    val cleaned = summary.trim().ifBlank { "- No tool output." }
    return buildString {
        append("MCP tool results from round ")
        append(roundIndex)
        appendLine(":")
        appendLine(cleaned.take(5000))
        appendLine()
        appendLine("Use these results to continue the same user request.")
        appendLine("If more data is needed, you may output new <mcp_call> tags.")
    }.trim()
}

private fun parseMcpToolCallsPayload(text: String): List<PlannedMcpToolCall> {
    val cleaned = stripMarkdownCodeFences(text).trim()
    if (cleaned.isBlank()) return emptyList()

    val candidate = extractFirstJsonCandidate(cleaned)
    val root = candidate?.let { raw -> runCatching { JsonParser.parseString(raw) }.getOrNull() }
    if (root == null) {
        return parseLooseMcpToolCall(cleaned)?.let { listOf(it) } ?: emptyList()
    }

    val callsElement =
        when {
            root.isJsonObject && root.asJsonObject.has("calls") -> root.asJsonObject.get("calls")
            root.isJsonObject && root.asJsonObject.has("mcp_call") -> root.asJsonObject.get("mcp_call")
            root.isJsonObject && root.asJsonObject.has("tool_call") -> root.asJsonObject.get("tool_call")
            root.isJsonObject && root.asJsonObject.has("call") -> root.asJsonObject.get("call")
            root.isJsonObject && root.asJsonObject.has("toolCall") -> root.asJsonObject.get("toolCall")
            root.isJsonObject && root.asJsonObject.has("mcpCall") -> root.asJsonObject.get("mcpCall")
            else -> root
        }

    val callsArray =
        when {
            callsElement.isJsonArray -> callsElement.asJsonArray
            callsElement.isJsonObject -> com.google.gson.JsonArray().apply { add(callsElement) }
            else -> return emptyList()
        }

    fun getString(obj: com.google.gson.JsonObject, vararg keys: String): String? {
        return keys.asSequence().mapNotNull { key ->
            val el = runCatching { obj.get(key) }.getOrNull() ?: return@mapNotNull null
            if (!el.isJsonPrimitive) return@mapNotNull null
            val value = runCatching { el.asString }.getOrNull()?.trim().orEmpty()
            value.takeIf { it.isNotBlank() }
        }.firstOrNull()
    }

    fun getArgsObject(obj: com.google.gson.JsonObject): com.google.gson.JsonObject {
        fun fromKey(key: String): com.google.gson.JsonObject? {
            val el = runCatching { obj.get(key) }.getOrNull() ?: return null
            if (el.isJsonObject) return el.asJsonObject
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                val raw = el.asString.trim()
                if (raw.isBlank()) return null
                return runCatching {
                    JsonParser.parseString(raw)
                        .takeIf { it.isJsonObject }
                        ?.asJsonObject
                }.getOrNull()
            }
            return null
        }
        return fromKey("arguments")
            ?: fromKey("args")
            ?: fromKey("input")
            ?: fromKey("params")
            ?: fromKey("parameters")
            ?: com.google.gson.JsonObject()
    }

    fun normalizeCallObject(obj: com.google.gson.JsonObject): com.google.gson.JsonObject {
        val nested =
            listOf("mcp_call", "tool_call", "call", "toolCall", "mcpCall")
                .asSequence()
                .mapNotNull { key ->
                    runCatching { obj.get(key) }.getOrNull()
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                }
                .firstOrNull()
        return nested ?: obj
    }

    val strictParsed = callsArray.mapNotNull { el ->
        val rawObj = runCatching { el.asJsonObject }.getOrNull() ?: return@mapNotNull null
        val obj = normalizeCallObject(rawObj)
        val toolName = getString(obj, "toolName", "tool_name", "tool", "name").orEmpty()
        if (toolName.isBlank()) return@mapNotNull null

        val serverId = getString(obj, "serverId", "server_id", "server", "mcpId", "mcp_id", "id").orEmpty()
        val args = getArgsObject(obj).entrySet().associate { entry ->
            entry.key to entry.value.toKotlinAny()
        }

        PlannedMcpToolCall(serverId = serverId, toolName = toolName, arguments = args)
    }
    if (strictParsed.isNotEmpty()) return strictParsed
    return parseLooseMcpToolCall(cleaned)?.let { listOf(it) } ?: emptyList()
}

private fun extractFirstJsonCandidate(text: String): String? {
    val raw = text.trim()
    if (raw.isBlank()) return null

    val idxObj = raw.indexOf('{')
    val idxArr = raw.indexOf('[')
    val start =
        listOf(idxObj, idxArr)
            .filter { it >= 0 }
            .minOrNull()
            ?: return null

    val open = raw[start]
    val close = if (open == '{') '}' else ']'
    val end = raw.lastIndexOf(close)
    if (end <= start) return null

    return raw.substring(start, end + 1)
}

private fun parseLooseMcpToolCall(text: String): PlannedMcpToolCall? {
    val toolName =
        extractQuotedField(text, listOf("toolName", "tool_name", "tool", "name"))
            .orEmpty()
            .trim()
    if (toolName.isBlank()) return null

    val serverId =
        extractQuotedField(text, listOf("serverId", "server_id", "server", "mcpId", "mcp_id", "id"))
            .orEmpty()
            .trim()

    val argsFromJson =
        extractObjectField(text, listOf("arguments", "args", "input", "params", "parameters"))
            ?.let { raw ->
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            }
            ?.entrySet()
            ?.associate { entry -> entry.key to entry.value.toKotlinAny() }
            .orEmpty()
            .toMutableMap()

    if (argsFromJson.isEmpty()) {
        extractQuotedField(text, listOf("arguments", "args", "input", "params", "parameters"))
            ?.takeIf { it.startsWith("{") && it.endsWith("}") }
            ?.let { raw ->
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            }
            ?.entrySet()
            ?.forEach { entry -> argsFromJson[entry.key] = entry.value.toKotlinAny() }
    }

    if (argsFromJson.isEmpty()) {
        extractQuotedField(text, listOf("value"))?.takeIf { it.isNotBlank() }?.let { value ->
            argsFromJson["value"] = value
        }
    }

    return PlannedMcpToolCall(
        serverId = serverId,
        toolName = toolName,
        arguments = argsFromJson
    )
}

private fun extractQuotedField(text: String, keys: List<String>): String? {
    keys.forEach { key ->
        val escapedKey = Regex.escape(key)
        val quotedRegex = Regex("\"$escapedKey\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", setOf(RegexOption.IGNORE_CASE))
        val looseQuotedRegex = Regex("\\b$escapedKey\\b\\s*[:=]\\s*\"((?:\\\\.|[^\"\\\\])*)\"", setOf(RegexOption.IGNORE_CASE))
        val looseSingleQuotedRegex = Regex("\\b$escapedKey\\b\\s*[:=]\\s*'([^']*)'", setOf(RegexOption.IGNORE_CASE))
        val bareRegex = Regex("\\b$escapedKey\\b\\s*[:=]\\s*([^,\\n\\r}\\]]+)", setOf(RegexOption.IGNORE_CASE))

        val value =
            quotedRegex.find(text)?.groupValues?.getOrNull(1)
                ?: looseQuotedRegex.find(text)?.groupValues?.getOrNull(1)
                ?: looseSingleQuotedRegex.find(text)?.groupValues?.getOrNull(1)
                ?: bareRegex.find(text)?.groupValues?.getOrNull(1)

        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return@forEach

        val normalized = raw.removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'").trim()
        if (normalized.isBlank()) return@forEach

        val decoded = runCatching { JsonParser.parseString("\"$normalized\"").asString }.getOrDefault(normalized)
        return decoded.trim()
    }
    return null
}

private fun extractObjectField(text: String, keys: List<String>): String? {
    keys.forEach { key ->
        val escapedKey = Regex.escape(key)
        val markerRegex = Regex("(?:\"$escapedKey\"|\\b$escapedKey\\b)\\s*:\\s*\\{", setOf(RegexOption.IGNORE_CASE))
        val marker = markerRegex.find(text) ?: return@forEach
        val start = marker.range.last
        val end = findMatchingBraceEnd(text, start)
        if (start >= 0 && end >= start && end < text.length) {
            return text.substring(start, end + 1)
        }
    }
    return null
}

private fun findMatchingBraceEnd(text: String, startIndex: Int): Int {
    if (startIndex !in text.indices || text[startIndex] != '{') return -1
    var depth = 0
    var inString = false
    var escaped = false

    for (index in startIndex until text.length) {
        val c = text[index]
        if (inString) {
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = false
            }
            continue
        }

        when (c) {
            '"' -> inString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return index
                if (depth < 0) return -1
            }
        }
    }
    return -1
}

private fun com.google.gson.JsonElement.toKotlinAny(): Any? {
    return when {
        isJsonNull -> null
        isJsonPrimitive -> {
            val p = asJsonPrimitive
            when {
                p.isBoolean -> p.asBoolean
                p.isNumber -> p.asNumber
                else -> p.asString
            }
        }

        isJsonArray -> asJsonArray.map { it.toKotlinAny() }
        isJsonObject -> asJsonObject.entrySet().associate { it.key to it.value.toKotlinAny() }
        else -> null
    }
}

private suspend fun extractMemoryCandidatesFromTurn(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    userText: String,
    assistantText: String,
    extraHeaders: List<HttpHeader>
): List<String> {
    val u = userText.trim()
    val a = assistantText.trim()
    if (u.isBlank()) return emptyList()

    val systemPrompt =
        buildString {
            append("Extract memory candidates from the user message. Return ONLY a JSON array of strings. ")
            append("Keep only explicit user-stated stable preferences or factual profile details. ")
            append("Never infer, guess, or rewrite speculative statements. ")
            append("Do not save temporary tasks, one-off requests, moods, assumptions, or assistant-generated claims. ")
            append("If there is nothing safe to save, return []. Keep at most 2 short items.")
        }

    val userPrompt =
        buildString {
            append("User message:\n")
            append(u.take(900))
            if (a.isNotBlank()) {
                append("\n\nAssistant reply (context only, not a source of truth):\n")
                append(a.take(1200))
            }
            append("\n\nReturn JSON array only.")
        }

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userPrompt)
            ),
            extraHeaders = extraHeaders
        )

    return parseJsonStringArray(raw)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(2)
}

private fun shouldAttemptMemoryExtraction(userText: String): Boolean {
    val text = userText.trim()
    if (text.isBlank()) return false

    val lower = text.lowercase()
    val explicitMarkersEn =
        listOf(
            "please remember",
            "remember that",
            "for future reference",
            "i prefer",
            "i like",
            "i love",
            "i dislike",
            "i don't like",
            "my favorite",
            "i am ",
            "i'm ",
            "my name is",
            "call me",
            "i usually",
            "i always",
            "i never"
        )
    val explicitMarkersZh =
        listOf(
            "请记住",
            "记住",
            "以后",
            "从现在开始",
            "我喜欢",
            "我偏好",
            "我更喜欢",
            "我不喜欢",
            "我讨厌",
            "我是",
            "我叫",
            "叫我",
            "我通常",
            "我总是",
            "我从不"
        )

    val hasSignal =
        explicitMarkersEn.any { marker -> lower.contains(marker) } ||
            explicitMarkersZh.any { marker -> text.contains(marker) }
    if (!hasSignal) return false

    if (lower.endsWith("?") && !lower.contains("remember")) return false
    return true
}

private fun filterMemoryCandidates(
    candidates: List<String>,
    userText: String
): List<String> {
    if (candidates.isEmpty()) return emptyList()

    val blockedMarkers =
        listOf(
            "maybe",
            "probably",
            "guess",
            "not sure",
            "uncertain",
            "unknown",
            "seems",
            "assistant",
            "chatgpt",
            "model",
            "可能",
            "也许",
            "大概",
            "猜测",
            "不确定",
            "似乎"
        )

    return candidates
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.length in 3..180 }
        .filter { candidate ->
            val lower = candidate.lowercase()
            blockedMarkers.none { marker -> lower.contains(marker) || candidate.contains(marker) }
        }
        .filter { candidate -> memoryCandidateIsGrounded(candidate, userText) }
        .distinct()
        .take(2)
}

private fun memoryCandidateIsGrounded(candidate: String, userText: String): Boolean {
    val candidateLower = candidate.lowercase()
    val userLower = userText.lowercase()
    if (userLower.contains(candidateLower) || candidateLower.contains(userLower.take(36))) {
        return true
    }

    val englishTokens =
        Regex("[a-zA-Z]{4,}")
            .findAll(candidateLower)
            .map { it.value }
            .toSet()
    if (englishTokens.any { token -> userLower.contains(token) }) {
        return true
    }

    val chineseFragments =
        Regex("[\\u4e00-\\u9fa5]{2,}")
            .findAll(candidate)
            .map { it.value }
            .toSet()
    return chineseFragments.any { frag -> userText.contains(frag) }
}

private suspend fun generateConversationTitle(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    transcript: String,
    extraHeaders: List<HttpHeader>
): String? {
    val t = transcript.trim()
    if (t.isBlank()) return null

    val systemPrompt =
        "Generate a concise conversation title (max 6 words). Respond with ONLY the title, no quotes, no punctuation."

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = t.take(1800))
            ),
            extraHeaders = extraHeaders
        )

    val firstLine = stripMarkdownCodeFences(raw).lineSequence().firstOrNull().orEmpty().trim()
    return firstLine.takeIf { it.isNotBlank() }
}

