package com.zionchat.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalZiCodeAgentRunner
import com.zionchat.app.LocalZiCodeRepository
import com.zionchat.app.R
import com.zionchat.app.ui.components.AppModalBottomSheet
import com.zionchat.app.ui.components.FooterTranslucentBackdrop
import com.zionchat.app.ui.components.HeaderTranslucentBackdrop
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.zicode.data.ZiCodeRunStatus
import com.zionchat.app.zicode.data.ZiCodeSession
import com.zionchat.app.zicode.data.ZiCodeToolCallState
import com.zionchat.app.zicode.data.ZiCodeToolStatus
import com.zionchat.app.zicode.data.ZiCodeTurn
import com.zionchat.app.zicode.data.buildZiCodeToolCapabilities
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZiCodeConversationScreen(
    navController: NavController,
    ownerArg: String,
    repoArg: String
) {
    val owner = Uri.decode(ownerArg)
    val repo = Uri.decode(repoArg)
    val repository = LocalZiCodeRepository.current
    val agentRunner = LocalZiCodeAgentRunner.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val sessions by repository.sessionsForRepoFlow(owner, repo).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var selectedSessionId by rememberSaveable(owner, repo) { mutableStateOf<String?>(null) }
    var inputText by rememberSaveable(owner, repo) { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    var showSessionSheet by remember { mutableStateOf(false) }
    var showToolSheet by remember { mutableStateOf(false) }
    val newConversationTitle = stringResource(R.string.zicode_new_conversation)

    LaunchedEffect(owner, repo, sessions.size) {
        if (sessions.isEmpty()) {
            val session = repository.createSession(owner = owner, repo = repo, title = newConversationTitle)
            selectedSessionId = session.id
        }
    }

    LaunchedEffect(sessions, selectedSessionId) {
        if (sessions.isNotEmpty() && sessions.none { it.id == selectedSessionId }) {
            selectedSessionId = sessions.first().id
        }
    }

    val activeSession = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
    val turns = activeSession?.turns.orEmpty().sortedBy { it.createdAt }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeThresholdPx = with(density) { 24.dp.roundToPx() }
    val imeVisible = inputFocused && imeBottomPx > imeThresholdPx
    val imeBottomPadding = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val navBottomPadding = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val inputBottomInset = if (imeVisible) imeBottomPadding else navBottomPadding
    val inputBottomSpacing = bottomInputBottomPadding(imeVisible)
    val composerHeightDp = with(density) { composerHeightPx.toDp() }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    fun createSession() {
        scope.launch {
            val session = repository.createSession(owner = owner, repo = repo, title = newConversationTitle)
            selectedSessionId = session.id
            showSessionSheet = false
        }
    }

    fun sendPrompt() {
        val session = activeSession ?: return
        val prompt = inputText.trim()
        if (prompt.isBlank()) return
        scope.launch {
            val turn = repository.appendTurn(session.id, prompt)
            inputText = ""
            if (turn != null) {
                agentRunner.enqueue(session.id, owner, repo, turn.id, prompt)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ZiCodePageBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 104.dp,
                bottom = composerHeightDp + inputBottomInset + inputBottomSpacing + 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                if (turns.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
                        ZiCodeMetaText(text = stringResource(R.string.zicode_conversation_empty_hint))
                    }
                }
            }
            items(turns, key = { it.id }) { turn ->
                ZiCodeTurnCard(turn = turn)
            }
        }

        ZiCodeConversationTopBar(
            repo = repo,
            onBack = { navController.navigateUp() },
            onOpenFiles = { navController.navigate("zicode_files/${Uri.encode(owner)}/${Uri.encode(repo)}") },
            onOpenSessions = {
                focusManager.clearFocus(force = true)
                inputFocused = false
                showToolSheet = false
                showSessionSheet = true
            }
        )

        ZiCodeComposer(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = ::sendPrompt,
            onToggleTools = {
                focusManager.clearFocus(force = true)
                inputFocused = false
                showSessionSheet = false
                showToolSheet = !showToolSheet
            },
            onFocusChanged = { focused ->
                inputFocused = focused
                if (focused) showToolSheet = false
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = inputBottomInset + inputBottomSpacing)
                .fillMaxWidth(),
            onMeasured = { composerHeightPx = it }
        )
    }

    if (showSessionSheet) {
        val sessionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppModalBottomSheet(onDismissRequest = { showSessionSheet = false }, sheetState = sessionSheetState) {
            ZiCodeSessionSheet(sessions = sessions, activeSessionId = activeSession?.id, onSelect = {
                selectedSessionId = it.id
                showSessionSheet = false
            }, onNewConversation = ::createSession)
        }
    }

    if (showToolSheet) {
        val toolSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppModalBottomSheet(onDismissRequest = { showToolSheet = false }, sheetState = toolSheetState) {
            ZiCodeToolSheet()
        }
    }
}

@Composable
private fun ZiCodeConversationTopBar(
    repo: String,
    onBack: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSessions: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        HeaderTranslucentBackdrop(modifier = Modifier.fillMaxWidth().height(102.dp), containerColor = ZiCodePageBackground, containerAlpha = 0.92f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZiCodeCircleButton(onClick = onBack) { ZiCodeBackIcon() }
            Text(text = repo, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = SourceSans3, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZiCodeCircleButton(onClick = onOpenFiles) {
                    Icon(painter = rememberResourceDrawablePainter(R.drawable.ic_files), contentDescription = stringResource(R.string.zicode_files), tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                }
                ZiCodeCircleButton(onClick = onOpenSessions) {
                    Icon(imageVector = AppIcons.HamburgerMenu, contentDescription = stringResource(R.string.zicode_conversation_sessions), tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ZiCodeTurnCard(turn: ZiCodeTurn) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(ZiCodePanelGray).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = turn.prompt, color = TextPrimary, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = SourceSans3)
            }
        }
        ZiCodePanel {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.zicode_agent_title), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SourceSans3, modifier = Modifier.weight(1f))
                        ZiCodeMiniStatusBadge(text = stringResource(turn.status.labelRes()))
                    }
                    turn.response.takeIf { it.isNotBlank() }?.let {
                        Text(text = it, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = SourceSans3)
                    }
                    if (turn.toolCalls.isNotEmpty()) {
                        Column {
                            turn.toolCalls.forEachIndexed { index, tool ->
                                if (index > 0) HorizontalDivider(color = ZiCodeDividerColor)
                                ZiCodeToolCallRow(tool = tool)
                            }
                        }
                    }
                    turn.resultLink?.takeIf { it.isNotBlank() }?.let { url ->
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                            Text(turn.resultLabel ?: stringResource(R.string.zicode_open_result), color = TextPrimary)
                        }
                    }
                }
                ZiCodeRunningShimmer(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(ZiCodePanelRadius)), visible = turn.status == ZiCodeRunStatus.RUNNING)
            }
        }
    }
}

@Composable
private fun ZiCodeToolCallRow(tool: ZiCodeToolCallState) {
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded }.padding(vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(if (tool.status == ZiCodeToolStatus.SUCCESS) TextPrimary else ZiCodeSecondaryText, CircleShape))
                Text(text = tool.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = SourceSans3, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(text = stringResource(tool.status.labelRes()), color = ZiCodeSecondaryText, fontSize = 12.sp, fontFamily = SourceSans3)
            }
            if (tool.summary.isNotBlank()) ZiCodeMetaText(text = tool.summary)
            if (expanded && tool.detailLog.isNotBlank()) Text(text = tool.detailLog, color = ZiCodeSecondaryText, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = SourceSans3)
        }
        ZiCodeRunningShimmer(modifier = Modifier.matchParentSize(), visible = tool.status == ZiCodeToolStatus.RUNNING)
    }
}

@Composable
private fun BoxScope.ZiCodeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleTools: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onMeasured: (Int) -> Unit
) {
    Box(modifier = modifier) {
        FooterTranslucentBackdrop(modifier = Modifier.fillMaxWidth().height(138.dp), containerColor = ZiCodePageBackground, containerAlpha = 0.94f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onMeasured(it.height) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ZiCodeCircleButton(onClick = onToggleTools, size = 46.dp, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(painter = rememberResourceDrawablePainter(R.drawable.ic_zicode), contentDescription = stringResource(R.string.zicode_tool_sheet_title), tint = Color.Unspecified, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
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
                            .heightIn(min = 24.dp, max = 128.dp)
                            .onFocusChanged { onFocusChanged(it.isFocused) }
                            .background(Color.Transparent),
                        textStyle = TextStyle(color = TextPrimary, fontSize = 17.sp, lineHeight = 22.sp, fontFamily = SourceSans3),
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (value.trim().isNotBlank()) onSend() }),
                        minLines = 1,
                        maxLines = 5,
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (value.isBlank()) {
                                    Text(text = stringResource(R.string.zicode_input_placeholder), color = ZiCodeSecondaryText, fontSize = 17.sp, lineHeight = 22.sp, fontFamily = SourceSans3)
                                }
                                inner()
                            }
                        }
                    )
                }
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp).size(36.dp).clip(CircleShape).background(if (value.trim().isNotBlank()) TextPrimary else ZiCodePanelPressedGray).pressableScale(pressedScale = 0.95f, onClick = onSend), contentAlignment = Alignment.Center) {
                    Icon(imageVector = AppIcons.Send, contentDescription = stringResource(R.string.zicode_send), tint = if (value.trim().isNotBlank()) Color.White else ZiCodeSecondaryText, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ZiCodeSessionSheet(
    sessions: List<ZiCodeSession>,
    activeSessionId: String?,
    onSelect: (ZiCodeSession) -> Unit,
    onNewConversation: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.zicode_conversation_sessions), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = SourceSans3)
        TextButton(onClick = onNewConversation) {
            Text(stringResource(R.string.zicode_new_conversation), color = TextPrimary)
        }
        sessions.forEachIndexed { index, session ->
            ZiCodePanel(onClick = { onSelect(session) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ZiCodeMiniStatusBadge(text = if (session.id == activeSessionId) stringResource(R.string.zicode_active) else (index + 1).toString())
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = session.title.ifBlank { stringResource(R.string.zicode_new_conversation) }, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
                        ZiCodeMetaText(text = "${session.turns.size} ${stringResource(R.string.zicode_turns)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ZiCodeToolSheet() {
    val grouped = remember { buildZiCodeToolCapabilities().groupBy { it.group } }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.zicode_tool_sheet_title), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = SourceSans3)
        grouped.forEach { (group, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZiCodeSectionTitle(title = group)
                ZiCodePanel {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        items.forEachIndexed { index, capability ->
                            if (index > 0) HorizontalDivider(color = ZiCodeDividerColor)
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = capability.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
                                ZiCodeMetaText(text = capability.description)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ZiCodeRunStatus.labelRes(): Int {
    return when (this) {
        ZiCodeRunStatus.QUEUED -> R.string.zicode_status_queued
        ZiCodeRunStatus.RUNNING -> R.string.zicode_status_running
        ZiCodeRunStatus.SUCCESS -> R.string.zicode_status_done
        ZiCodeRunStatus.FAILED -> R.string.zicode_status_failed
    }
}

private fun ZiCodeToolStatus.labelRes(): Int {
    return when (this) {
        ZiCodeToolStatus.QUEUED -> R.string.zicode_status_queued
        ZiCodeToolStatus.RUNNING -> R.string.zicode_status_running
        ZiCodeToolStatus.SUCCESS -> R.string.zicode_status_done
        ZiCodeToolStatus.FAILED -> R.string.zicode_status_failed
    }
}
