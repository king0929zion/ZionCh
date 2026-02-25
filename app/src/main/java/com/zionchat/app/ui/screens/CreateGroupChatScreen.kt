package com.zionchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
fun CreateGroupChatScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val enabledModels = remember(models) { models.filter { it.enabled } }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = "创建群聊",
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canCreate) Color(0xFF0A84FF) else GrayLight)
                        .pressableScale(
                            pressedScale = 0.96f,
                            onClick = {
                                if (!canCreate) return@pressableScale
                                scope.launch {
                                    val finalName = name.trim()
                                    val sortedMembers = selectedMembers.toList()
                                    val coordinator =
                                        if (strategy == GROUP_STRATEGY_DYNAMIC) {
                                            dynamicCoordinatorId?.takeIf { sortedMembers.contains(it) }
                                                ?: sortedMembers.firstOrNull()
                                        } else {
                                            null
                                        }
                                    val conversation = repository.createConversation(title = finalName)
                                    repository.upsertGroupChat(
                                        GroupChatConfig(
                                            name = finalName,
                                            memberModelIds = sortedMembers,
                                            strategy = strategy,
                                            dynamicCoordinatorModelId = coordinator,
                                            conversationId = conversation.id
                                        )
                                    )
                                    repository.setCurrentConversationId(conversation.id)
                                    navController.navigate("chat") {
                                        popUpTo("group_chats") { inclusive = false }
                                    }
                                }
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "创建",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canCreate) Color.White else TextSecondary
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 群聊名称
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "群聊名称",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
                        placeholder = { Text(text = "输入群聊名称") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 响应策略
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "响应策略",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StrategyRow(
                        title = "动态调配",
                        subtitle = "由协调者模型决定谁发言",
                        selected = strategy == GROUP_STRATEGY_DYNAMIC,
                        onClick = { strategy = GROUP_STRATEGY_DYNAMIC }
                    )
                    StrategyRow(
                        title = "轮流发言",
                        subtitle = "成员按顺序依次发言",
                        selected = strategy == GROUP_STRATEGY_ROUND_ROBIN,
                        onClick = { strategy = GROUP_STRATEGY_ROUND_ROBIN }
                    )
                    StrategyRow(
                        title = "随机触发",
                        subtitle = "随机选择成员发言",
                        selected = strategy == GROUP_STRATEGY_RANDOM,
                        onClick = { strategy = GROUP_STRATEGY_RANDOM }
                    )
                }
            }

            // 动态调配模型选择
            AnimatedVisibility(
                visible = strategy == GROUP_STRATEGY_DYNAMIC,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "协调者模型",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        enabledModels.filter { selectedMembers.contains(it.id) }.forEach { model ->
                            val selected = dynamicCoordinatorId == model.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(0xFFE8F2FF) else Color.Transparent)
                                    .pressableScale(
                                        pressedScale = 0.98f,
                                        onClick = { dynamicCoordinatorId = model.id }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = model.displayName,
                                        fontSize = 14.sp,
                                        color = TextPrimary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        text = extractRemoteModelId(model.id),
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF0A84FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        if (enabledModels.none { selectedMembers.contains(it.id) }) {
                            Text(
                                text = "请先选择至少2个成员",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            // 成员选择
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "选择成员（至少2个）",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "已选择 ${selectedMembers.size} 个模型",
                    fontSize = 12.sp,
                    color = if (selectedMembers.size >= 2) Color(0xFF0A84FF) else TextSecondary
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    enabledModels.forEach { model ->
                        val checked = selectedMembers.contains(model.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (checked) Color(0xFFE8F2FF) else Color.Transparent)
                                .pressableScale(
                                    pressedScale = 0.98f,
                                    onClick = {
                                        selectedMembers =
                                            if (checked) selectedMembers - model.id else selectedMembers + model.id
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
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
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = model.displayName,
                                        fontSize = 14.sp,
                                        color = TextPrimary,
                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        text = extractRemoteModelId(model.id),
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            if (checked) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF0A84FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (enabledModels.isEmpty()) {
                        Text(
                            text = "当前没有已启用模型，请先在 Models 中启用。",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StrategyRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFE8F2FF) else Color.Transparent)
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = if (selected) Color(0xFF0A84FF) else TextPrimary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = Color(0xFF0A84FF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}