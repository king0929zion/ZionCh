package com.zionchat.app.ui.screens

import com.google.gson.Gson
import com.google.gson.JsonObject
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalChatApiClient
import com.zionchat.app.LocalProviderAuthManager
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.LocalZiCodePolicyService
import com.zionchat.app.LocalZiCodeToolDispatcher
import com.zionchat.app.R
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.ZiCodeGitHubRepo
import com.zionchat.app.data.ZiCodeMessage
import com.zionchat.app.data.ZiCodeModelAgent
import com.zionchat.app.data.ZiCodeRunRecord
import com.zionchat.app.data.ZiCodeSettings
import com.zionchat.app.data.ZiCodeToolCall
import com.zionchat.app.data.ZiCodeWorkspace
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.AppModalBottomSheet
import com.zionchat.app.ui.components.HeaderTranslucentBackdrop
import com.zionchat.app.ui.components.headerActionButtonShadow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZiCodeScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val chatApiClient = LocalChatApiClient.current
    val providerAuthManager = LocalProviderAuthManager.current
    val gitHubService = LocalZiCodeGitHubService.current
    val policyService = LocalZiCodePolicyService.current
    val toolDispatcher = LocalZiCodeToolDispatcher.current
    val scope = rememberCoroutineScope()
    val modelAgent = remember(chatApiClient, providerAuthManager, toolDispatcher, policyService) {
        ZiCodeModelAgent(
            chatApiClient = chatApiClient,
            providerAuthManager = providerAuthManager,
            toolDispatcher = toolDispatcher,
            policyService = policyService
        )
    }

    val zicodeSettings by repository.zicodeSettingsFlow.collectAsState(initial = ZiCodeSettings())
    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val defaultZiCodeModelId by repository.defaultZiCodeModelIdFlow.collectAsState(initial = null)
    val defaultChatModelId by repository.defaultChatModelIdFlow.collectAsState(initial = null)
    val workspaces by repository.zicodeWorkspacesFlow.collectAsState(initial = emptyList())
    val sessions by repository.zicodeSessionsFlow.collectAsState(initial = emptyList())
    val allMessages by repository.zicodeMessagesFlow.collectAsState(initial = emptyList())
    val allToolCalls by repository.zicodeToolCallsFlow.collectAsState(initial = emptyList())
    val allRuns by repository.zicodeRunsFlow.collectAsState(initial = emptyList())

    var remoteRepos by remember { mutableStateOf<List<ZiCodeGitHubRepo>>(emptyList()) }
    var reposLoading by remember { mutableStateOf(false) }
    var reposError by remember { mutableStateOf<String?>(null) }

    val projectWorkspaces = remember(remoteRepos, workspaces) {
        if (remoteRepos.isNotEmpty()) {
            remoteRepos.map { repo ->
                val existing =
                    workspaces.firstOrNull {
                        it.owner.equals(repo.owner, ignoreCase = true) &&
                            it.repo.equals(repo.name, ignoreCase = true)
                    }
                if (existing != null) {
                    existing.copy(
                        defaultBranch = repo.defaultBranch,
                        displayName = repo.fullName.ifBlank { "${repo.owner}/${repo.name}" }
                    )
                } else {
                    ZiCodeWorkspace(
                        id = buildStableZiCodeWorkspaceId(repo.owner, repo.name),
                        owner = repo.owner,
                        repo = repo.name,
                        defaultBranch = repo.defaultBranch,
                        displayName = repo.fullName.ifBlank { "${repo.owner}/${repo.name}" }
                    )
                }
            }
        } else {
            workspaces
        }
    }

    val currentWorkspace = remember(workspaces, zicodeSettings.currentWorkspaceId) {
        val selected = zicodeSettings.currentWorkspaceId?.trim().orEmpty()
        if (selected.isBlank()) workspaces.firstOrNull() else workspaces.firstOrNull { it.id == selected }
    }

    var showChatPage by remember { mutableStateOf(false) }
    var selectedModelName by remember { mutableStateOf("") }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var isRunningTask by remember { mutableStateOf(false) }
    var showDebugDetails by remember { mutableStateOf(false) }

    var showWorkspaceSheet by remember { mutableStateOf(false) }
    val workspaceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hintLoadToolSpecText = stringResource(R.string.zicode_hint_load_toolspec)
    val hintListTreeText = stringResource(R.string.zicode_hint_list_tree)
    val hintSearchText = stringResource(R.string.zicode_hint_search)
    val hintReadFileText = stringResource(R.string.zicode_hint_read_file)

    LaunchedEffect(zicodeSettings.pat) {
        val token = zicodeSettings.pat.trim()
        if (token.isBlank()) {
            remoteRepos = emptyList()
            reposError = null
            reposLoading = false
            return@LaunchedEffect
        }
        reposLoading = true
        reposError = null
        gitHubService.listAccessibleRepos(token)
            .onSuccess { repos ->
                remoteRepos = repos
            }
            .onFailure { throwable ->
                remoteRepos = emptyList()
                reposError = throwable.message ?: "Failed to load repositories."
            }
        reposLoading = false
    }

    LaunchedEffect(projectWorkspaces, currentWorkspace?.id) {
        if (selectedModelName.isNotBlank()) return@LaunchedEffect
        selectedModelName =
            currentWorkspace?.repo
                ?: projectWorkspaces.firstOrNull()?.repo
                ?: ""
    }

    LaunchedEffect(currentWorkspace?.id, selectedSessionId) {
        if (!selectedSessionId.isNullOrBlank()) return@LaunchedEffect
        currentWorkspace?.repo
            ?.takeIf { it.isNotBlank() }
            ?.let { selectedModelName = it }
    }

    val currentSessionMessages = remember(allMessages, selectedSessionId) {
        val sid = selectedSessionId?.trim().orEmpty()
        if (sid.isBlank()) emptyList() else allMessages.filter { it.sessionId == sid }.sortedBy { it.createdAt }
    }
    val currentSessionToolCalls = remember(allToolCalls, selectedSessionId) {
        val sid = selectedSessionId?.trim().orEmpty()
        if (sid.isBlank()) {
            emptyList()
        } else {
            allToolCalls
                .filter { it.sessionId == sid }
                .sortedByDescending { it.startedAt }
                .take(16)
        }
    }
    val currentSessionRuns = remember(allRuns, selectedSessionId) {
        val sid = selectedSessionId?.trim().orEmpty()
        if (sid.isBlank()) {
            emptyList()
        } else {
            allRuns
                .filter { it.sessionId == sid }
                .sortedByDescending { it.updatedAt }
                .take(6)
        }
    }
    val selectedSession = remember(sessions, selectedSessionId) {
        val sid = selectedSessionId?.trim().orEmpty()
        if (sid.isBlank()) null else sessions.firstOrNull { it.id == sid }
    }
    val sessionWorkspace = remember(workspaces, selectedSession?.workspaceId) {
        val workspaceId = selectedSession?.workspaceId?.trim().orEmpty()
        if (workspaceId.isBlank()) null else workspaces.firstOrNull { it.id == workspaceId }
    }
    val chatWorkspace = sessionWorkspace ?: currentWorkspace

    fun ensureSession(workspace: ZiCodeWorkspace, modelName: String, forceNew: Boolean) {
        scope.launch {
            val session =
                if (forceNew) {
                    repository.createZiCodeSession(
                        workspaceId = workspace.id,
                        modelName = modelName,
                        title = "$modelName Session"
                    )
                } else {
                    repository.findLatestZiCodeSession(workspace.id, modelName)
                        ?: repository.createZiCodeSession(
                            workspaceId = workspace.id,
                            modelName = modelName,
                            title = "$modelName Session"
                        )
                }
            selectedSessionId = session?.id
            val sid = session?.id.orEmpty()
            if (sid.isNotBlank()) {
                val existing = repository.listZiCodeMessages(sid)
                if (existing.isEmpty()) {
                    repository.appendZiCodeMessage(
                        ZiCodeMessage(
                            sessionId = sid,
                            role = "assistant",
                            content = "Welcome to ZiCode. Describe your coding task and I will run a model-driven GitHub agent loop.",
                            toolHints =
                                listOf(
                                    hintLoadToolSpecText,
                                    hintListTreeText,
                                    "输入 `/tool <tool_name> <json>` 可直接调用工具，例如 `/tool repo.list_tree {\"ref\":\"main\"}`",
                                    "MCP 示例：`/tool mcp.list_servers {}`、`/tool mcp.call_tool {\"server_id\":\"...\",\"tool_name\":\"...\",\"arguments\":{}}`"
                                )
                        )
                    )
                }
            }
        }
    }

    fun openChat(workspace: ZiCodeWorkspace) {
        scope.launch {
            val now = System.currentTimeMillis()
            val merged =
                repository.upsertZiCodeWorkspace(
                    workspace.copy(
                        displayName = workspace.displayName.ifBlank { "${workspace.owner}/${workspace.repo}" },
                        createdAt = workspace.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = now
                    )
                ) ?: return@launch
            repository.setZiCodeCurrentWorkspace(merged.id)
            selectedModelName = merged.repo
            showChatPage = true
            ensureSession(merged, merged.repo, forceNew = false)
        }
    }

    fun sendMessage() {
        val sid = selectedSessionId?.trim().orEmpty()
        val trimmed = inputText.trim()
        if (trimmed.isBlank()) return
        val workspace =
            if (sid.isBlank()) {
                currentWorkspace
            } else {
                val session = sessions.firstOrNull { it.id == sid }
                val workspaceId = session?.workspaceId?.trim().orEmpty()
                workspaces.firstOrNull { it.id == workspaceId } ?: currentWorkspace
            }
        if (workspace == null) {
            showWorkspaceSheet = true
            return
        }
        if (sid.isBlank()) {
            val sessionLabel = selectedModelName.ifBlank { workspace.displayName }
            ensureSession(workspace, sessionLabel, forceNew = false)
            return
        }
        inputText = ""
        scope.launch {
            repository.appendZiCodeMessage(
                ZiCodeMessage(
                    sessionId = sid,
                    role = "user",
                    content = trimmed
                )
            )
            isRunningTask = true
            try {
                val directTool = parseDirectToolCommand(trimmed)
                if (directTool != null) {
                    val toolResult =
                        toolDispatcher.dispatch(
                            sessionId = sid,
                            workspace = workspace,
                            settings = zicodeSettings,
                            toolName = directTool.first,
                            argsJson = directTool.second
                        )
                    val preview = toolResult.resultJson.orEmpty().take(900)
                    repository.appendZiCodeMessage(
                        ZiCodeMessage(
                            sessionId = sid,
                            role = "assistant",
                            content =
                                if (toolResult.success) {
                                    "工具 `${directTool.first}` 执行成功。\n\n$preview"
                                } else {
                                    "工具 `${directTool.first}` 执行失败：${toolResult.error.orEmpty()}"
                                },
                            toolHints = listOf(toolResult.userHint.ifBlank { hintLoadToolSpecText })
                        )
                    )
                    return@launch
                }

                val selected = resolveZiCodeModelSelection(
                    models = models,
                    providers = providers,
                    preferredZiCodeModelId = defaultZiCodeModelId,
                    fallbackChatModelId = defaultChatModelId
                )
                if (selected == null) {
                    repository.appendZiCodeMessage(
                        ZiCodeMessage(
                            sessionId = sid,
                            role = "assistant",
                            content = "ZiCode 模型未配置或不可用。请前往 Settings -> Default Model 先设置 ZiCode 默认模型。",
                            toolHints = listOf("前往 Settings -> Default Model 设置 ZiCode 默认模型")
                        )
                    )
                    return@launch
                }

                val recentMessages = repository.listZiCodeMessages(sid).takeLast(16)
                val summary =
                    modelAgent.runTask(
                        sessionId = sid,
                        workspace = workspace,
                        settings = zicodeSettings,
                        provider = selected.provider,
                        model = selected.model,
                        userPrompt = trimmed,
                        recentMessages = recentMessages
                    )
                val latestHints =
                    repository.zicodeToolCallsFlow.first()
                        .filter { it.sessionId == sid }
                        .sortedByDescending { it.startedAt }
                        .mapNotNull { call -> call.userHint.takeIf { it.isNotBlank() } }
                        .distinct()
                        .take(6)
                repository.appendZiCodeMessage(
                    ZiCodeMessage(
                        sessionId = sid,
                        role = "assistant",
                        content = summary.finalMessage,
                        toolHints =
                            summary.toolHints
                                .ifEmpty {
                                    latestHints.ifEmpty { listOf(hintListTreeText, hintSearchText, hintReadFileText) }
                                }
                    )
                )
            } catch (throwable: Throwable) {
                repository.appendZiCodeMessage(
                    ZiCodeMessage(
                        sessionId = sid,
                        role = "assistant",
                        content = "任务执行失败：${throwable.message ?: "Unknown error"}",
                        toolHints = listOf(hintLoadToolSpecText)
                    )
                )
            } finally {
                isRunningTask = false
            }
        }
    }

    fun createNewChat() {
        val workspace = chatWorkspace ?: currentWorkspace
        if (workspace == null) {
            showWorkspaceSheet = true
            return
        }
        if (selectedModelName.isBlank()) {
            selectedModelName = workspace.repo
        }
        ensureSession(workspace, selectedModelName, forceNew = true)
    }

    LaunchedEffect(currentWorkspace?.id, selectedModelName, sessions, selectedSessionId) {
        val currentSession = selectedSessionId?.trim().orEmpty()
        if (currentSession.isNotBlank() && sessions.any { it.id == currentSession }) return@LaunchedEffect
        val workspaceId = currentWorkspace?.id.orEmpty()
        if (workspaceId.isBlank() || selectedModelName.isBlank()) return@LaunchedEffect
        selectedSessionId =
            sessions.firstOrNull {
                it.workspaceId == workspaceId && it.modelName.equals(selectedModelName, ignoreCase = true)
            }?.id
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val pageProgress by animateFloatAsState(
            targetValue = if (showChatPage) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "zicode_page_progress"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -widthPx * 0.08f * pageProgress
                    alpha = 1f - 0.08f * pageProgress
                }
        ) {
            ZiCodeListHeader(
                onBack = { navController.popBackStack() }
            )
            ZiCodeModelList(
                workspaces = projectWorkspaces,
                isLoading = reposLoading,
                errorMessage = reposError,
                onOpenSettings = { navController.navigate("zicode_settings") },
                onModelClick = ::openChat
            )
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = widthPx * (1f - pageProgress)
                }
                .background(Surface)
        ) {
            ZiCodeChatHeader(
                modelName = chatWorkspace?.repo.orEmpty().ifBlank { selectedModelName },
                subtitle = chatWorkspace?.defaultBranch.orEmpty(),
                onBack = { showChatPage = false },
                onOpenWorkspace = {
                    scope.launch {
                        chatWorkspace?.let { repository.setZiCodeCurrentWorkspace(it.id) }
                        navController.navigate("zicode_repo_browser")
                    }
                },
                onNewChat = ::createNewChat
            )
            ZiCodeChatMessages(
                messages = currentSessionMessages,
                toolCalls = currentSessionToolCalls,
                runs = currentSessionRuns,
                isRunningTask = isRunningTask,
                showDebugDetails = showDebugDetails,
                onToggleDebug = { showDebugDetails = !showDebugDetails },
                modifier = Modifier.weight(1f)
            )
            ZiCodeInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = ::sendMessage
            )
        }
    }

    if (showWorkspaceSheet) {
        ZiCodeWorkspaceSheet(
            sheetState = workspaceSheetState,
            settings = zicodeSettings,
            workspaces = workspaces,
            currentWorkspaceId = currentWorkspace?.id,
            onDismiss = { showWorkspaceSheet = false },
            onSetCurrentWorkspace = { workspaceId ->
                scope.launch {
                    repository.setZiCodeCurrentWorkspace(workspaceId)
                }
            },
            onSave = { owner, repo, branch, pat ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    val workspace =
                        repository.upsertZiCodeWorkspace(
                            ZiCodeWorkspace(
                                owner = owner,
                                repo = repo,
                                defaultBranch = branch,
                                displayName = "$owner/$repo",
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    if (!pat.isNullOrBlank()) {
                        repository.setZiCodePat(pat)
                    }
                    if (workspace != null) {
                        repository.setZiCodeCurrentWorkspace(workspace.id)
                    }
                }
            },
            onCheckConnectivity = { owner, repo, branch, pat ->
                val token = pat.trim().ifBlank { zicodeSettings.pat.trim() }
                if (token.isBlank()) {
                    Result.failure<Any>(IllegalArgumentException("PAT 为空，无法检查连接"))
                } else {
                    gitHubService.checkWorkspaceAccess(
                        ZiCodeWorkspace(owner = owner, repo = repo, defaultBranch = branch),
                        token
                    ).map { it as Any }
                }
            }
        )
    }
}

@Composable
private fun ZiCodeListHeader(
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        HeaderTranslucentBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = onBack),
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
                Text(
                    text = stringResource(R.string.zicode_title),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.size(42.dp))
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ZiCodeModelList(
    workspaces: List<ZiCodeWorkspace>,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenSettings: () -> Unit,
    onModelClick: (ZiCodeWorkspace) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLoading) {
            item(key = "zicode_loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary
                    )
                    Text(
                        text = "正在加载 GitHub 项目...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SourceSans3,
                        color = TextSecondary
                    )
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item(key = "zicode_error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        color = Color(0xFFB3261E),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .pressableScale(pressedScale = 0.97f, onClick = onOpenSettings)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "去 ZiCode 设置",
                            fontSize = 13.sp,
                            fontFamily = SourceSans3,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        if (workspaces.isEmpty() && !isLoading) {
            item(key = "zicode_empty_projects") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "暂无项目，请先在 ZiCode 设置中配置 PAT 并同步项目。",
                        fontSize = 14.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .pressableScale(pressedScale = 0.97f, onClick = onOpenSettings)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "打开 ZiCode 设置",
                            fontSize = 13.sp,
                            fontFamily = SourceSans3,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        items(workspaces, key = { "${it.owner}/${it.repo}" }) { workspace ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
                    .pressableScale(pressedScale = 0.98f, onClick = { onModelClick(workspace) })
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = rememberResourceDrawablePainter(R.drawable.ic_zicode_repo),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.Unspecified
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = workspace.displayName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SourceSans3,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = workspace.defaultBranch,
                        fontSize = 12.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item(key = "zicode_list_bottom_spacing") { Spacer(modifier = Modifier.height(10.dp)) }
    }
}

@Composable
private fun ZiCodeChatHeader(
    modelName: String,
    subtitle: String,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onNewChat: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        HeaderTranslucentBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .headerActionButtonShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = modelName.ifBlank { "ZiCode" },
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SourceSans3,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = SourceSans3,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleActionButton(
                        icon = {
                            Icon(
                                imageVector = AppIcons.Files,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        },
                        onClick = onOpenWorkspace
                    )
                    CircleActionButton(
                        icon = {
                            Icon(
                                imageVector = AppIcons.Plus,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        },
                        onClick = onNewChat
                    )
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .headerActionButtonShadow(CircleShape)
            .clip(CircleShape)
            .background(Color.White, CircleShape)
            .pressableScale(pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun ZiCodeChatMessages(
    messages: List<ZiCodeMessage>,
    toolCalls: List<ZiCodeToolCall>,
    runs: List<ZiCodeRunRecord>,
    isRunningTask: Boolean,
    showDebugDetails: Boolean,
    onToggleDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    val list = if (messages.isEmpty()) {
        listOf(
            ZiCodeMessage(
                sessionId = "empty",
                role = "assistant",
                content = "Start by describing your coding goal. ZiCode will run a GitHub-based execution loop.",
                toolHints = emptyList()
            )
        )
    } else {
        messages
    }
    val callsToShow = toolCalls.take(8)
    val runsToShow = runs.take(3)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(list, key = { it.id }) { message ->
            val isUser = message.role == "user"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                if (isUser) {
                    val bubbleShape = RoundedCornerShape(18.dp)
                    Box(
                        modifier = Modifier
                            .padding(start = 60.dp)
                            .clip(bubbleShape)
                            .background(Color(0xFF1C1C1E), bubbleShape)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.content,
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontFamily = SourceSans3
                        )
                    }
                } else {
                    val bubbleShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .clip(bubbleShape)
                            .background(Color.White, bubbleShape)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.content,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = SourceSans3
                        )
                    }
                }
            }
            if (!isUser && message.toolHints.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    message.toolHints.forEach { hint ->
                        Text(
                            text = "• $hint",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = SourceSans3
                        )
                    }
                }
            }
        }
        if (isRunningTask) {
            item(key = "zicode_running") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, start = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary
                    )
                    Text(
                        text = "ZiCode 正在执行任务...",
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
        }
        if ((callsToShow.isNotEmpty() || runsToShow.isNotEmpty()) && !showDebugDetails) {
            item(key = "zicode_debug_toggle_open") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressableScale(pressedScale = 0.98f, onClick = onToggleDebug)
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "显示执行详情",
                        fontSize = 12.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (showDebugDetails && (callsToShow.isNotEmpty() || runsToShow.isNotEmpty())) {
            item(key = "zicode_debug_toggle_close") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressableScale(pressedScale = 0.98f, onClick = onToggleDebug)
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "收起执行详情",
                        fontSize = 12.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = 90f }
                    )
                }
            }
        }
        if (showDebugDetails && callsToShow.isNotEmpty()) {
            item(key = "zicode_tool_call_title") {
                Text(
                    text = "Tool Calls",
                    fontSize = 12.sp,
                    fontFamily = SourceSans3,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                )
            }
            items(callsToShow, key = { "tool_${it.id}" }) { call ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = call.userHint.ifBlank { "⏳ 正在执行工具调用…" },
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "`${call.toolName}` · ${call.status}",
                        fontSize = 12.sp,
                        fontFamily = SourceSans3,
                        color =
                            when (call.status) {
                                "success" -> Color(0xFF0A7D34)
                                "error" -> Color(0xFFB3261E)
                                else -> TextSecondary
                            }
                    )
                    if (!call.error.isNullOrBlank()) {
                        Text(
                            text = call.error.orEmpty(),
                            fontSize = 12.sp,
                            fontFamily = SourceSans3,
                            color = Color(0xFFB3261E),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (showDebugDetails && runsToShow.isNotEmpty()) {
            item(key = "zicode_runs_title") {
                Text(
                    text = "Workflow Runs",
                    fontSize = 12.sp,
                    fontFamily = SourceSans3,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                )
            }
            items(runsToShow, key = { "run_${it.id}" }) { run ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${run.workflow} · #${run.runId ?: 0}",
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = run.summary.ifBlank { run.status },
                        fontSize = 12.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ZiCodeInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val hasText = value.trim().isNotEmpty()
    val actionBackground by animateColorAsState(
        targetValue = if (hasText) TextPrimary else GrayLight,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "zicode_send_bg"
    )
    val actionIconTint by animateColorAsState(
        targetValue = if (hasText) Color.White else TextSecondary,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "zicode_send_icon"
    )
    val inputCapsuleShape = RoundedCornerShape(23.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = inputCapsuleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(inputCapsuleShape)
                .background(Surface, inputCapsuleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 48.dp, top = 8.dp, bottom = 8.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp, max = 120.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = TextPrimary
                    ),
                    cursorBrush = SolidColor(TextPrimary),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (hasText) onSend() }
                    ),
                    minLines = 1,
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.zicode_input_placeholder),
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

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(actionBackground, CircleShape)
                    .pressableScale(
                        enabled = hasText,
                        pressedScale = 0.95f,
                        onClick = {
                            if (hasText) onSend()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Send,
                    contentDescription = null,
                    tint = actionIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZiCodeWorkspaceSheet(
    sheetState: SheetState,
    settings: ZiCodeSettings,
    workspaces: List<ZiCodeWorkspace>,
    currentWorkspaceId: String?,
    onDismiss: () -> Unit,
    onSetCurrentWorkspace: (String) -> Unit,
    onSave: (owner: String, repo: String, branch: String, pat: String?) -> Unit,
    onCheckConnectivity: suspend (owner: String, repo: String, branch: String, pat: String) -> Result<*>
) {
    val scope = rememberCoroutineScope()
    var owner by remember(workspaces, currentWorkspaceId) {
        mutableStateOf(workspaces.firstOrNull { it.id == currentWorkspaceId }?.owner.orEmpty())
    }
    var repo by remember(workspaces, currentWorkspaceId) {
        mutableStateOf(workspaces.firstOrNull { it.id == currentWorkspaceId }?.repo.orEmpty())
    }
    var branch by remember(workspaces, currentWorkspaceId) {
        mutableStateOf(workspaces.firstOrNull { it.id == currentWorkspaceId }?.defaultBranch ?: "main")
    }
    var patInput by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf<String?>(null) }
    var checkSuccess by remember { mutableStateOf(false) }
    val incompleteText = stringResource(R.string.zicode_workspace_incomplete)
    val checkSuccessText = stringResource(R.string.zicode_workspace_check_success)
    val checkFailedText = stringResource(R.string.zicode_workspace_check_failed)
    val checkingText = stringResource(R.string.zicode_workspace_checking)
    val checkButtonText = stringResource(R.string.zicode_workspace_check)
    val saveAndUseText = stringResource(R.string.zicode_workspace_save_use)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.zicode_workspace_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SourceSans3,
                color = TextPrimary
            )

            if (workspaces.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.zicode_workspace_saved),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SourceSans3,
                    color = TextSecondary
                )

                workspaces.forEach { item ->
                    val selected = item.id == currentWorkspaceId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                owner = item.owner
                                repo = item.repo
                                branch = item.defaultBranch
                                onSetCurrentWorkspace(item.id)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) Color(0xFFEFF2FF) else Color(0xFFF1F1F1)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = item.displayName,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                fontFamily = SourceSans3,
                                color = TextPrimary
                            )
                            Text(
                                text = "${item.owner}/${item.repo} · ${item.defaultBranch}",
                                fontSize = 12.sp,
                                fontFamily = SourceSans3,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            ZiCodeField(
                label = stringResource(R.string.zicode_workspace_owner),
                value = owner,
                onValueChange = { owner = it },
                placeholder = "octocat"
            )
            ZiCodeField(
                label = stringResource(R.string.zicode_workspace_repo),
                value = repo,
                onValueChange = { repo = it },
                placeholder = "hello-world"
            )
            ZiCodeField(
                label = stringResource(R.string.zicode_workspace_branch),
                value = branch,
                onValueChange = { branch = it },
                placeholder = "main"
            )
            ZiCodeField(
                label = stringResource(R.string.zicode_workspace_pat),
                value = patInput,
                onValueChange = { patInput = it },
                placeholder = stringResource(R.string.zicode_workspace_pat_hint),
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation()
            )

            Text(
                text = stringResource(R.string.zicode_workspace_pat_current_mask, buildMaskedToken(settings.pat)),
                fontSize = 12.sp,
                fontFamily = SourceSans3,
                color = TextSecondary
            )

            if (!checkMessage.isNullOrBlank()) {
                Text(
                    text = checkMessage.orEmpty(),
                    fontSize = 13.sp,
                    fontFamily = SourceSans3,
                    color = if (checkSuccess) Color(0xFF0A7D34) else Color(0xFFB3261E)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val ownerValue = owner.trim()
                        val repoValue = repo.trim()
                        val branchValue = branch.trim().ifBlank { "main" }
                        if (ownerValue.isBlank() || repoValue.isBlank()) {
                            checkSuccess = false
                            checkMessage = incompleteText
                            return@Button
                        }
                        checking = true
                        checkMessage = null
                        scope.launch {
                            val result = onCheckConnectivity(ownerValue, repoValue, branchValue, patInput)
                            checking = false
                            result.fold(
                                onSuccess = {
                                    checkSuccess = true
                                    checkMessage = checkSuccessText
                                },
                                onFailure = { throwable ->
                                    checkSuccess = false
                                    checkMessage = throwable.message ?: checkFailedText
                                }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F1F1), contentColor = TextPrimary)
                ) {
                    Text(
                        text = if (checking) checkingText else checkButtonText,
                        fontSize = 14.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = {
                        val ownerValue = owner.trim()
                        val repoValue = repo.trim()
                        val branchValue = branch.trim().ifBlank { "main" }
                        if (ownerValue.isBlank() || repoValue.isBlank()) {
                            checkSuccess = false
                            checkMessage = incompleteText
                            return@Button
                        }
                        onSave(ownerValue, repoValue, branchValue, patInput.trim().ifBlank { null })
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
                ) {
                    Text(
                        text = saveAndUseText,
                        fontSize = 14.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ZiCodeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SourceSans3,
            color = TextSecondary
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
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
                    fontSize = 15.sp,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                ),
                singleLine = true
            )
        }
    }
}

private fun buildMaskedToken(token: String): String {
    val value = token.trim()
    if (value.isBlank()) return "(none)"
    if (value.length <= 8) return "****"
    return "${value.take(4)}****${value.takeLast(4)}"
}

private fun parseDirectToolCommand(prompt: String): Pair<String, String>? {
    val normalized = prompt.trim()
    if (!normalized.startsWith("/tool ")) return null
    val payload = normalized.removePrefix("/tool ").trim()
    if (payload.isBlank()) return null

    val firstSpace = payload.indexOf(' ')
    val toolName = if (firstSpace < 0) payload else payload.substring(0, firstSpace).trim()
    if (toolName.isBlank()) return null

    val argsRaw = if (firstSpace < 0) "{}" else payload.substring(firstSpace + 1).trim().ifBlank { "{}" }
    val argsJson =
        runCatching {
            Gson().fromJson(argsRaw, JsonObject::class.java)
            argsRaw
        }.getOrElse { "{}" }
    return toolName to argsJson
}

private data class ZiCodeResolvedModel(
    val model: ModelConfig,
    val provider: ProviderConfig
)

private fun resolveZiCodeModelSelection(
    models: List<ModelConfig>,
    providers: List<ProviderConfig>,
    preferredZiCodeModelId: String?,
    fallbackChatModelId: String?
): ZiCodeResolvedModel? {
    val enabledModels = models.filter { it.enabled }
    if (enabledModels.isEmpty()) return null

    val preferred =
        findEnabledModelByKey(enabledModels, preferredZiCodeModelId)
            ?: findEnabledModelByKey(enabledModels, fallbackChatModelId)
            ?: enabledModels.firstOrNull()
            ?: return null
    val providerId = preferred.providerId?.trim().orEmpty()
    if (providerId.isBlank()) return null
    val provider = providers.firstOrNull { it.id == providerId } ?: return null
    return ZiCodeResolvedModel(model = preferred, provider = provider)
}

private fun findEnabledModelByKey(models: List<ModelConfig>, key: String?): ModelConfig? {
    val target = key?.trim().orEmpty()
    if (target.isBlank()) return null
    return models.firstOrNull { it.id == target || extractRemoteModelId(it.id) == target }
}

private fun buildStableZiCodeWorkspaceId(owner: String, repo: String): String {
    val ownerKey = owner.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    val repoKey = repo.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    return "zicode_${ownerKey}_${repoKey}"
}
