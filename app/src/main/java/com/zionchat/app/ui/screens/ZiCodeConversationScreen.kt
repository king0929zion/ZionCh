package com.zionchat.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalZiCodeAgentRunner
import com.zionchat.app.LocalZiCodeRepository
import com.zionchat.app.R
import com.zionchat.app.ui.components.FooterTranslucentBackdrop
import com.zionchat.app.ui.components.HeaderTranslucentBackdrop
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.zicode.data.ZiCodeRunStatus
import com.zionchat.app.zicode.data.ZiCodeToolCallState
import com.zionchat.app.zicode.data.ZiCodeToolStatus
import com.zionchat.app.zicode.data.ZiCodeTurn
import kotlinx.coroutines.launch

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
    val sessions by repository.sessionsForRepoFlow(owner, repo).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    var selectedSessionId by rememberSaveable(owner, repo) { mutableStateOf<String?>(null) }
    var inputText by rememberSaveable(owner, repo) { mutableStateOf("") }

    LaunchedEffect(owner, repo, sessions.size) {
        if (sessions.isEmpty()) {
            repository.createSession(owner = owner, repo = repo, title = "New conversation")
        }
    }

    LaunchedEffect(sessions, selectedSessionId) {
        if (sessions.isNotEmpty() && sessions.none { it.id == selectedSessionId }) {
            selectedSessionId = sessions.first().id
        }
    }

    val activeSession = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
    val turns = activeSession?.turns.orEmpty().sortedBy { it.createdAt }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex.coerceAtLeast(0))
        }
    }

    fun createSession() {
        scope.launch {
            val session = repository.createSession(owner = owner, repo = repo, title = "New conversation")
            selectedSessionId = session.id
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
                agentRunner.enqueue(
                    sessionId = session.id,
                    repoOwner = owner,
                    repoName = repo,
                    turnId = turn.id,
                    prompt = prompt
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZiCodePageBackground)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 108.dp,
                bottom = 146.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ZiCodePanel {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZiCodeMetaText(text = "GitHub Remote Agent")
                        Text(
                            text = "$owner / $repo",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SourceSans3
                        )
                        ZiCodeMetaText(text = "对话优先，工具调用和异步执行状态会直接展示在会话里。")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessions.forEachIndexed { index, session ->
                        ZiCodeChip(
                            text = session.title.ifBlank { "会话 ${index + 1}" },
                            selected = session.id == activeSession?.id,
                            onClick = { selectedSessionId = session.id }
                        )
                    }
                }
            }

            if (turns.isEmpty()) {
                item {
                    ZiCodeEmptyPanel(
                        title = "从目标开始",
                        body = "直接告诉 ZiCode 你想在这个仓库里完成什么，例如检查结构、梳理部署入口、查看关键文件或继续拆解任务。",
                        actionLabel = "新建会话",
                        onAction = ::createSession
                    )
                }
            } else {
                items(turns, key = { it.id }) { turn ->
                    ZiCodeTurnCard(turn = turn)
                }
            }
        }

        ZiCodeConversationTopBar(
            repo = repo,
            onBack = { navController.navigateUp() },
            onOpenFiles = {
                navController.navigate("zicode_files/${Uri.encode(owner)}/${Uri.encode(repo)}")
            },
            onNewSession = ::createSession
        )

        ZiCodeComposer(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = ::sendPrompt
        )
    }
}

@Composable
private fun ZiCodeConversationTopBar(
    repo: String,
    onBack: () -> Unit,
    onOpenFiles: () -> Unit,
    onNewSession: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        HeaderTranslucentBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            containerColor = ZiCodePageBackground,
            containerAlpha = 0.92f
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZiCodeCircleButton(onClick = onBack) {
                ZiCodeBackIcon()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                ZiCodeMetaText(text = "Project")
                Text(
                    text = repo,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SourceSans3
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZiCodeCircleButton(onClick = onOpenFiles) {
                    Icon(
                        painter = rememberResourceDrawablePainter(R.drawable.ic_files),
                        contentDescription = "文件",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                }
                ZiCodeCircleButton(onClick = onNewSession) {
                    Icon(
                        imageVector = AppIcons.NewChat,
                        contentDescription = "新建会话",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZiCodeTurnCard(turn: ZiCodeTurn) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(ZiCodePanelGray)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = turn.prompt,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    fontFamily = SourceSans3
                )
            }
        }

        ZiCodePanel {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ZiCode Agent",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SourceSans3,
                            modifier = Modifier.weight(1f)
                        )
                        ZiCodeMiniStatusBadge(text = turn.status.label())
                    }
                    turn.response.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontFamily = SourceSans3
                        )
                    }
                    if (turn.toolCalls.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            turn.toolCalls.forEach { tool ->
                                ZiCodeToolCallRow(tool = tool)
                            }
                        }
                    }
                    turn.resultLink?.takeIf { it.isNotBlank() }?.let { url ->
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        ) {
                            Text(turn.resultLabel ?: "打开结果", color = TextPrimary)
                        }
                    }
                }
                ZiCodeRunningShimmer(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(ZiCodeInnerRadius)),
                    visible = turn.status == ZiCodeRunStatus.RUNNING
                )
            }
        }
    }
}

@Composable
private fun ZiCodeToolCallRow(tool: ZiCodeToolCallState) {
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .pressableScale(pressedScale = 0.98f, onClick = { expanded = !expanded })
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = when (tool.status) {
                                ZiCodeToolStatus.RUNNING -> ZiCodeSecondaryText
                                ZiCodeToolStatus.SUCCESS -> TextPrimary
                                ZiCodeToolStatus.FAILED -> ZiCodeTertiaryText
                                ZiCodeToolStatus.QUEUED -> ZiCodeTertiaryText
                            },
                            shape = CircleShape
                        )
                )
                Text(
                    text = tool.label,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SourceSans3
                )
                ZiCodeMetaText(text = tool.status.label())
            }
            if (tool.summary.isNotBlank()) {
                ZiCodeMetaText(text = tool.summary)
            }
            if (expanded && tool.detailLog.isNotBlank()) {
                Text(
                    text = tool.detailLog,
                    color = ZiCodeSecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = SourceSans3
                )
            }
        }
        ZiCodeRunningShimmer(
            modifier = Modifier.matchParentSize(),
            visible = tool.status == ZiCodeToolStatus.RUNNING
        )
    }
}

@Composable
private fun BoxScope.ZiCodeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
    ) {
        FooterTranslucentBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            containerColor = ZiCodePageBackground,
            containerAlpha = 0.92f
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(ZiCodePanelGray)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontFamily = SourceSans3
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isBlank()) {
                            ZiCodeMetaText(text = "继续告诉 ZiCode 你的目标…")
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            ZiCodeCircleButton(onClick = onSend) {
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = "发送",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun ZiCodeRunStatus.label(): String {
    return when (this) {
        ZiCodeRunStatus.QUEUED -> "Queued"
        ZiCodeRunStatus.RUNNING -> "Running"
        ZiCodeRunStatus.SUCCESS -> "Done"
        ZiCodeRunStatus.FAILED -> "Failed"
    }
}

private fun ZiCodeToolStatus.label(): String {
    return when (this) {
        ZiCodeToolStatus.QUEUED -> "Queued"
        ZiCodeToolStatus.RUNNING -> "Running"
        ZiCodeToolStatus.SUCCESS -> "Done"
        ZiCodeToolStatus.FAILED -> "Failed"
    }
}
