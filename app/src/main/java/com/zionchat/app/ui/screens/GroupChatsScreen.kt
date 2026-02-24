package com.zionchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.GroupChatConfig
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.GrayLighter
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private const val GROUP_STRATEGY_DYNAMIC = "dynamic"
private const val GROUP_STRATEGY_ROUND_ROBIN = "round_robin"
private const val GROUP_STRATEGY_RANDOM = "random"

@Composable
fun GroupChatsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val groups by repository.groupChatsFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val enabledModels = remember(models) { models.filter { it.enabled } }
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = "Group",
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = { showCreateDialog = true }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Plus,
                        contentDescription = "Create Group",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有群聊，点击右上角创建",
                    fontSize = 15.sp,
                    color = TextSecondary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groups.forEach { group ->
                    GroupChatCard(
                        group = group,
                        allModels = models,
                        onOpen = {
                            scope.launch {
                                repository.setCurrentConversationId(group.conversationId)
                                navController.navigate("chat")
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repository.deleteGroupChat(group.id)
                                repository.deleteConversation(group.conversationId)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            models = enabledModels,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, members, strategy, dynamicCoordinator ->
                scope.launch {
                    val conversation = repository.createConversation(title = name)
                    repository.upsertGroupChat(
                        GroupChatConfig(
                            name = name,
                            memberModelIds = members,
                            strategy = strategy,
                            dynamicCoordinatorModelId = dynamicCoordinator,
                            conversationId = conversation.id
                        )
                    )
                    repository.setCurrentConversationId(conversation.id)
                    showCreateDialog = false
                    navController.navigate("chat")
                }
            }
        )
    }
}

@Composable
fun GroupBotsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val enabledModels = remember(models) { models.filter { it.enabled } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = "Bots",
            onBack = { navController.popBackStack() }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "这里展示可加入群聊的模型。创建群聊时可直接选择这些模型。",
                color = TextSecondary,
                fontSize = 13.sp
            )
            if (enabledModels.isEmpty()) {
                Text(
                    text = "当前没有已启用模型，请先在 Models 中启用。",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                enabledModels.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(GrayLighter)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.displayName,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = SourceSans3
                        )
                        Text(
                            text = extractRemoteModelId(model.id),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChatCard(
    group: GroupChatConfig,
    allModels: List<ModelConfig>,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val strategyLabel =
        when (group.strategy) {
            GROUP_STRATEGY_ROUND_ROBIN -> "轮流发言"
            GROUP_STRATEGY_RANDOM -> "随机触发"
            else -> "动态调配"
        }
    val memberNames =
        group.memberModelIds.mapNotNull { memberId ->
            allModels.firstOrNull { it.id == memberId }?.displayName
        }
    val summary = memberNames.joinToString(" · ").ifBlank { "无成员" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .pressableScale(pressedScale = 0.98f, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(GrayLighter),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.ChatGPTLogo,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = group.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${memberNames.size} 个模型 · $strategyLabel",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = summary,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 2
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFFFCECEC), CircleShape)
                .pressableScale(pressedScale = 0.93f, onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Trash,
                contentDescription = null,
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CreateGroupDialog(
    models: List<ModelConfig>,
    onDismiss: () -> Unit,
    onCreate: (name: String, memberModelIds: List<String>, strategy: String, dynamicCoordinatorId: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf(GROUP_STRATEGY_DYNAMIC) }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dynamicCoordinatorId by remember { mutableStateOf<String?>(null) }

    val canCreate =
        name.trim().isNotBlank() &&
            selectedMembers.size >= 2 &&
            (
                strategy != GROUP_STRATEGY_DYNAMIC ||
                    (!dynamicCoordinatorId.isNullOrBlank() && selectedMembers.contains(dynamicCoordinatorId))
                )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    val finalName = name.trim()
                    val sortedMembers = selectedMembers.toList()
                    val coordinator =
                        if (strategy == GROUP_STRATEGY_DYNAMIC) {
                            dynamicCoordinatorId?.takeIf { sortedMembers.contains(it) } ?: sortedMembers.firstOrNull()
                        } else {
                            null
                        }
                    onCreate(finalName, sortedMembers, strategy, coordinator)
                }
            ) {
                Text(text = "创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
                    placeholder = { Text(text = "群聊名称") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF7F7F8),
                        unfocusedContainerColor = Color(0xFFF7F7F8),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "响应策略", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyChip(
                        title = "动态调配",
                        selected = strategy == GROUP_STRATEGY_DYNAMIC,
                        onClick = { strategy = GROUP_STRATEGY_DYNAMIC }
                    )
                    StrategyChip(
                        title = "轮流发言",
                        selected = strategy == GROUP_STRATEGY_ROUND_ROBIN,
                        onClick = { strategy = GROUP_STRATEGY_ROUND_ROBIN }
                    )
                    StrategyChip(
                        title = "随机触发",
                        selected = strategy == GROUP_STRATEGY_RANDOM,
                        onClick = { strategy = GROUP_STRATEGY_RANDOM }
                    )
                }

                if (strategy == GROUP_STRATEGY_DYNAMIC) {
                    Text(text = "动态调配模型", color = TextSecondary, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        models.forEach { model ->
                            val selected = dynamicCoordinatorId == model.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) Color(0xFFE8F2FF) else GrayLighter)
                                    .pressableScale(pressedScale = 0.98f, onClick = { dynamicCoordinatorId = model.id })
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.displayName,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF0A84FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(text = "选择群成员（至少 2 个）", color = TextSecondary, fontSize = 12.sp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    models.forEach { model ->
                        val checked = selectedMembers.contains(model.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (checked) Color(0xFFE8F2FF) else GrayLighter)
                                .pressableScale(
                                    pressedScale = 0.98f,
                                    onClick = {
                                        selectedMembers =
                                            if (checked) selectedMembers - model.id else selectedMembers + model.id
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(text = model.displayName, fontSize = 13.sp, color = TextPrimary)
                                Text(
                                    text = extractRemoteModelId(model.id),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            if (checked) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF0A84FF),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun StrategyChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFE8F2FF) else GrayLight)
            .pressableScale(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = Color(0xFF0A84FF),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = title,
            fontSize = 12.sp,
            color = if (selected) Color(0xFF0A84FF) else TextPrimary
        )
    }
}
