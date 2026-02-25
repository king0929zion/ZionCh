package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.GroupChatConfig
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.GrayLighter
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun GroupChatsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val groups by repository.groupChatsFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = "群聊",
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = { navController.navigate("create_group_chat") }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Plus,
                        contentDescription = "创建群聊",
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
            "round_robin" -> "轮流发言"
            "random" -> "随机触发"
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