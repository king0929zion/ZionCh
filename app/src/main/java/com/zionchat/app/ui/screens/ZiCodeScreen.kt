package com.zionchat.app.ui.screens

import com.google.gson.Gson
import com.google.gson.JsonObject
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalZiCodeAgentOrchestrator
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.LocalZiCodeToolDispatcher
import com.zionchat.app.LocalZiCodeWorkflowTemplateService
import com.zionchat.app.R
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.ZiCodeAgentRunSummary
import com.zionchat.app.data.ZiCodeAgentTask
import com.zionchat.app.data.ZiCodeMessage
import com.zionchat.app.data.ZiCodePlannedToolCall
import com.zionchat.app.data.ZiCodeRunRecord
import com.zionchat.app.data.ZiCodeSettings
import com.zionchat.app.data.ZiCodeToolCall
import com.zionchat.app.data.ZiCodeWorkspace
import com.zionchat.app.ui.components.AppModalBottomSheet
import com.zionchat.app.ui.components.headerActionButtonShadow
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.ChatBackground
import com.zionchat.app.ui.theme.GrayLighter
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
    val gitHubService = LocalZiCodeGitHubService.current
    val toolDispatcher = LocalZiCodeToolDispatcher.current
    val orchestrator = LocalZiCodeAgentOrchestrator.current
    val workflowTemplateService = LocalZiCodeWorkflowTemplateService.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    val enabledModels by repository.modelsFlow.collectAsState(initial = emptyList())
    val modelNames = remember(enabledModels) { buildZiCodeModelNames(enabledModels) }

    val zicodeSettings by repository.zicodeSettingsFlow.collectAsState(initial = ZiCodeSettings())
    val workspaces by repository.zicodeWorkspacesFlow.collectAsState(initial = emptyList())
    val sessions by repository.zicodeSessionsFlow.collectAsState(initial = emptyList())
    val allMessages by repository.zicodeMessagesFlow.collectAsState(initial = emptyList())
    val allToolCalls by repository.zicodeToolCallsFlow.collectAsState(initial = emptyList())
    val allRuns by repository.zicodeRunsFlow.collectAsState(initial = emptyList())

    val currentWorkspace = remember(workspaces, zicodeSettings.currentWorkspaceId) {
        val selected = zicodeSettings.currentWorkspaceId?.trim().orEmpty()
        if (selected.isBlank()) workspaces.firstOrNull() else workspaces.firstOrNull { it.id == selected }
    }

    var showChatPage by remember { mutableStateOf(false) }
    var selectedModelName by remember { mutableStateOf(modelNames.firstOrNull().orEmpty()) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var isRunningTask by remember { mutableStateOf(false) }
    var initializedWorkspaceId by remember { mutableStateOf<String?>(null) }

    var showWorkspaceSheet by remember { mutableStateOf(false) }
    val workspaceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hintLoadToolSpecText = stringResource(R.string.zicode_hint_load_toolspec)
    val hintListTreeText = stringResource(R.string.zicode_hint_list_tree)
    val hintSearchText = stringResource(R.string.zicode_hint_search)
    val hintReadFileText = stringResource(R.string.zicode_hint_read_file)
    val assistantMockReply = stringResource(R.string.zicode_assistant_mock_reply)

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

    fun ensureSession(modelName: String, forceNew: Boolean) {
        val workspace = currentWorkspace
        if (workspace == null) {
            showWorkspaceSheet = true
            return
        }
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
                            content = "Welcome to ZiCode. Describe your coding task and I will orchestrate the GitHub workflow.",
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

    fun openChat(modelName: String) {
        selectedModelName = modelName
        showChatPage = true
        ensureSession(modelName, forceNew = false)
    }

    fun sendMessage() {
        val sid = selectedSessionId?.trim().orEmpty()
        val trimmed = inputText.trim()
        if (trimmed.isBlank()) return
        val workspace = currentWorkspace
        if (workspace == null) {
            showWorkspaceSheet = true
            return
        }
        if (sid.isBlank()) {
            ensureSession(selectedModelName, forceNew = false)
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

                if (zicodeSettings.autoInitWorkflowTemplates && initializedWorkspaceId != workspace.id) {
                    val initResult =
                        workflowTemplateService.ensureWorkflowTemplates(
                            sessionId = sid,
                            workspace = workspace,
                            settings = zicodeSettings,
                            baseBranch = workspace.defaultBranch
                        )
                    initializedWorkspaceId = workspace.id
                    repository.appendZiCodeMessage(
                        ZiCodeMessage(
                            sessionId = sid,
                            role = "assistant",
                            content = initResult.message,
                            toolHints =
                                listOfNotNull(
                                    "📋 正在创建 Pull Request…".takeIf { initResult.pullRequestUrl != null },
                                    "📄 正在读取文件 `.github/workflows`…"
                                )
                        )
                    )
                }

                val task =
                    buildZiCodeTaskFromPrompt(
                        gson = gson,
                        sessionId = sid,
                        workspace = workspace,
                        modelName = selectedModelName,
                        prompt = trimmed
                    )
                val summary = orchestrator.executeTask(task, zicodeSettings)
                val latestHints =
                    repository.zicodeToolCallsFlow.first()
                        .filter { it.sessionId == sid }
                        .sortedByDescending { it.startedAt }
                        .mapNotNull { call -> call.userHint.takeIf { it.isNotBlank() } }
                        .distinct()
                        .take(4)
                repository.appendZiCodeMessage(
                    ZiCodeMessage(
                        sessionId = sid,
                        role = "assistant",
                        content = buildZiCodeSummaryText(summary, assistantMockReply),
                        toolHints = latestHints.ifEmpty { listOf(hintListTreeText, hintSearchText, hintReadFileText) }
                    )
                )
                summary.latestRunId?.let { runId ->
                    repository.upsertZiCodeRun(
                        ZiCodeRunRecord(
                            sessionId = sid,
                            workflow = task.workflowFile.orEmpty().ifBlank { "manual-task" },
                            runId = runId,
                            status = if (summary.success) "success" else "failure",
                            summary = summary.message,
                            runUrl = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
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
        if (selectedModelName.isBlank()) {
            selectedModelName = modelNames.firstOrNull().orEmpty()
        }
        ensureSession(selectedModelName, forceNew = true)
    }

    LaunchedEffect(currentWorkspace?.id, selectedModelName, sessions) {
        val workspaceId = currentWorkspace?.id.orEmpty()
        if (workspaceId.isBlank() || selectedModelName.isBlank()) return@LaunchedEffect
        val currentSession = selectedSessionId?.trim().orEmpty()
        val exists = sessions.any { it.id == currentSession }
        if (currentSession.isBlank() || !exists) {
            selectedSessionId =
                sessions.firstOrNull {
                    it.workspaceId == workspaceId && it.modelName.equals(selectedModelName, ignoreCase = true)
                }?.id
        }
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
                .windowInsetsPadding(WindowInsets.statusBars)
                .graphicsLayer {
                    translationX = -widthPx * 0.08f * pageProgress
                    alpha = 1f - 0.08f * pageProgress
                }
        ) {
            ZiCodeListHeader(
                onBack = { navController.popBackStack() }
            )
            ZiCodeModelList(
                models = modelNames,
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
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            ZiCodeChatHeader(
                modelName = selectedModelName,
                subtitle = currentWorkspace?.displayName.orEmpty(),
                onBack = { showChatPage = false },
                onOpenWorkspace = { showWorkspaceSheet = true },
                onNewChat = ::createNewChat
            )
            ZiCodeChatMessages(
                messages = currentSessionMessages,
                toolCalls = currentSessionToolCalls,
                runs = currentSessionRuns,
                isRunningTask = isRunningTask,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                imageVector = AppIcons.HamburgerMenu,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
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
        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ZiCodeModelList(
    models: List<String>,
    onModelClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(models, key = { it }) { model ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GrayLighter, RoundedCornerShape(16.dp))
                    .pressableScale(pressedScale = 0.98f, onClick = { onModelClick(model) })
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = rememberResourceDrawablePainter(R.drawable.ic_files),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = model,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SourceSans3,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                        imageVector = AppIcons.GitHub,
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
            .background(ChatBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(list, key = { it.id }) { message ->
            if (message.role == "user") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                    ) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = SourceSans3
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE9EAEE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Z",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SourceSans3
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontFamily = SourceSans3
                            )
                        }
                        message.toolHints.forEach { hint ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F7))
                            ) {
                                Text(
                                    text = hint,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = SourceSans3
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isRunningTask) {
            item(key = "zicode_running") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F7))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
        }
        if (callsToShow.isNotEmpty()) {
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
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F7))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
        }
        if (runsToShow.isNotEmpty()) {
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
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
}

@Composable
private fun ZiCodeInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(999.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.zicode_input_placeholder),
                        fontSize = 15.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary
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
                    fontSize = 15.sp,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                ),
                maxLines = 4
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black, CircleShape)
                .pressableScale(pressedScale = 0.95f, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Send,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
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

private fun buildZiCodeTaskFromPrompt(
    gson: Gson,
    sessionId: String,
    workspace: ZiCodeWorkspace,
    modelName: String,
    prompt: String
): ZiCodeAgentTask {
    val calls = mutableListOf<ZiCodePlannedToolCall>()

    calls += ZiCodePlannedToolCall(
        toolName = "policy.get_toolspec",
        argsJson = "{}"
    )
    calls += ZiCodePlannedToolCall(
        toolName = "repo.list_tree",
        argsJson =
            gson.toJson(
                JsonObject().apply {
                    addProperty("ref", workspace.defaultBranch)
                }
            )
    )

    if (prompt.contains("mcp", ignoreCase = true) || prompt.contains("tool", ignoreCase = true)) {
        calls += ZiCodePlannedToolCall(
            toolName = "mcp.list_servers",
            argsJson = "{}"
        )
    }

    extractSearchKeyword(prompt)?.let { keyword ->
        calls += ZiCodePlannedToolCall(
            toolName = "repo.search",
            argsJson =
                gson.toJson(
                    JsonObject().apply {
                        addProperty("keyword", keyword)
                        addProperty("per_page", 20)
                    }
                )
        )
    }

    extractReadablePath(prompt)?.let { path ->
        calls += ZiCodePlannedToolCall(
            toolName = "repo.read_file",
            argsJson =
                gson.toJson(
                    JsonObject().apply {
                        addProperty("path", path)
                        addProperty("ref", workspace.defaultBranch)
                    }
                )
        )
    }

    val workflowFile = extractWorkflowFile(prompt)
    if (workflowFile != null) {
        calls += ZiCodePlannedToolCall(
            toolName = "actions.trigger_workflow",
            argsJson =
                gson.toJson(
                    JsonObject().apply {
                        addProperty("workflow", workflowFile)
                        addProperty("ref", workspace.defaultBranch)
                    }
                )
        )
        calls += ZiCodePlannedToolCall(
            toolName = "actions.get_latest_run",
            argsJson =
                gson.toJson(
                    JsonObject().apply {
                        addProperty("workflow", workflowFile)
                        addProperty("branch", workspace.defaultBranch)
                    }
                )
        )
    }

    return ZiCodeAgentTask(
        taskId = "task-${System.currentTimeMillis()}",
        sessionId = sessionId,
        workspace = workspace,
        plannedCalls = calls,
        workflowFile = workflowFile,
    )
}

private fun buildZiCodeSummaryText(summary: ZiCodeAgentRunSummary, fallback: String): String {
    if (summary.totalCalls <= 0 && summary.success) return fallback
    return if (summary.success) {
        "执行完成：${summary.message}（共 ${summary.totalCalls} 个工具调用）"
    } else {
        "执行失败：${summary.message}（失败点：${summary.failedCall ?: "unknown"}）"
    }
}

private fun extractSearchKeyword(prompt: String): String? {
    val normalized = prompt.trim()
    if (normalized.isBlank()) return null
    val direct =
        Regex("(?:搜索|search)\\s*[:：]?\\s*([A-Za-z0-9_./-]{2,})", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    if (direct != null) return direct
    return if (normalized.contains("搜索") || normalized.contains("search", ignoreCase = true)) {
        normalized.split(" ").lastOrNull()?.trim()?.takeIf { it.length >= 2 }
    } else {
        null
    }
}

private fun extractReadablePath(prompt: String): String? {
    val normalized = prompt.trim()
    if (normalized.isBlank()) return null
    val pathInBackticks = Regex("`([^`]+)`").find(normalized)?.groupValues?.getOrNull(1)?.trim()
    if (!pathInBackticks.isNullOrBlank() && pathInBackticks.contains("/")) return pathInBackticks
    val genericPath =
        Regex("([A-Za-z0-9_./-]+\\.[A-Za-z0-9]{1,8})")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    return genericPath?.takeIf { it.contains("/") || it.contains(".") }
}

private fun extractWorkflowFile(prompt: String): String? {
    val normalized = prompt.trim()
    if (normalized.isBlank()) return null
    val explicit =
        Regex("([A-Za-z0-9_.-]+\\.ya?ml)", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    if (!explicit.isNullOrBlank()) return explicit
    return if (normalized.contains("workflow", ignoreCase = true) || normalized.contains("工作流")) {
        "build.yml"
    } else {
        null
    }
}

private fun buildZiCodeModelNames(models: List<ModelConfig>): List<String> {
    val enabled = models.filter { it.enabled }.map { it.displayName.trim().ifBlank { it.id } }
    if (enabled.isNotEmpty()) return enabled
    return listOf("GPT-4", "Claude 3", "Gemini", "Llama 3", "Mistral", "DeepSeek")
}
