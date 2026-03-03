package com.zionchat.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.ModelConfig
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
import androidx.compose.runtime.collectAsState
import java.util.UUID

private data class ZiCodeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val toolHints: List<String> = emptyList()
)

@Composable
fun ZiCodeScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val enabledModels by repository.modelsFlow.collectAsState(initial = emptyList())
    val modelNames = remember(enabledModels) { buildZiCodeModelNames(enabledModels) }
    var showChatPage by remember { mutableStateOf(false) }
    var selectedModelName by remember { mutableStateOf(modelNames.firstOrNull().orEmpty()) }
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ZiCodeChatMessage>() }
    val density = LocalDensity.current
    val hintLoadToolSpecText = stringResource(R.string.zicode_hint_load_toolspec)
    val hintListTreeText = stringResource(R.string.zicode_hint_list_tree)
    val hintSearchText = stringResource(R.string.zicode_hint_search)
    val hintReadFileText = stringResource(R.string.zicode_hint_read_file)
    val assistantMockReply = stringResource(R.string.zicode_assistant_mock_reply)

    fun openChat(modelName: String) {
        selectedModelName = modelName
        showChatPage = true
        if (messages.isEmpty()) {
            messages += ZiCodeChatMessage(
                role = "assistant",
                content = "Welcome to ZiCode. Describe your coding task and I will orchestrate the GitHub workflow.",
                toolHints = listOf(
                    hintLoadToolSpecText,
                    hintListTreeText
                )
            )
        }
    }

    fun sendMessage() {
        val trimmed = inputText.trim()
        if (trimmed.isBlank()) return
        inputText = ""
        messages += ZiCodeChatMessage(role = "user", content = trimmed)
        messages += ZiCodeChatMessage(
            role = "assistant",
            content = assistantMockReply,
            toolHints = listOf(
                hintListTreeText,
                hintSearchText,
                hintReadFileText
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
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
                onBack = { showChatPage = false },
                onOpenWorkspace = { },
                onNewChat = { messages.clear() }
            )
            ZiCodeChatMessages(
                messages = messages,
                modifier = Modifier.weight(1f)
            )
            ZiCodeInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = ::sendMessage
            )
        }
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
        Text(
            text = modelName.ifBlank { "ZiCode" },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SourceSans3,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    messages: List<ZiCodeChatMessage>,
    modifier: Modifier = Modifier
) {
    val list = if (messages.isEmpty()) {
        listOf(
            ZiCodeChatMessage(
                role = "assistant",
                content = "Start by describing your coding goal. ZiCode will run a GitHub-based execution loop.",
                toolHints = emptyList()
            )
        )
    } else {
        messages
    }
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

private fun buildZiCodeModelNames(models: List<ModelConfig>): List<String> {
    val enabled = models.filter { it.enabled }.map { it.displayName.trim().ifBlank { it.id } }
    if (enabled.isNotEmpty()) return enabled
    return listOf("GPT-4", "Claude 3", "Gemini", "Llama 3", "Mistral", "DeepSeek")
}
