@file:Suppress("DEPRECATION")

package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.BotConfig
import com.zionchat.app.data.GroupChatConfig
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.headerActionButtonShadow
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.settingsBottomInsets
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.GrayLighter
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GroupChatsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val groups by repository.groupChatsFlow.collectAsState(initial = emptyList())
    val bots by repository.botsFlow.collectAsState(initial = emptyList())
    var openedSwipeId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
    ) {
        PageTopBar(
            title = stringResource(R.string.group_chats_title),
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .headerActionButtonShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = { navController.navigate("create_group_chat") }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Plus,
                        contentDescription = stringResource(R.string.group_chat_create),
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
                    text = stringResource(R.string.group_chat_empty_hint),
                    fontSize = 15.sp,
                    color = TextSecondary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .settingsBottomInsets()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groups.forEach { group ->
                    SwipeableGroupCard(
                        group = group,
                        allBots = bots,
                        isOpened = openedSwipeId == group.id,
                        onOpenChanged = { opened ->
                            openedSwipeId = if (opened) group.id else null
                        },
                        onOpen = { navController.navigate("edit_group_chat/${group.id}") },
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeableGroupCard(
    group: GroupChatConfig,
    allBots: List<BotConfig>,
    isOpened: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val itemScope = rememberCoroutineScope()
    val actionWidth = 72.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val swipeableState = rememberSwipeableState(
        initialValue = if (isOpened) 1 else 0,
        confirmStateChange = { target ->
            onOpenChanged(target != 0)
            true
        }
    )
    val anchors = remember(actionWidthPx) { mapOf(0f to 0, -actionWidthPx to 1) }

    LaunchedEffect(isOpened) {
        val target = if (isOpened) 1 else 0
        if (swipeableState.currentValue != target) {
            swipeableState.animateTo(target)
        }
    }

    val memberBots = remember(group.memberBotIds, allBots) {
        group.memberBotIds.mapNotNull { botId -> allBots.firstOrNull { it.id == botId } }
    }
    val firstBot = memberBots.firstOrNull()
    val summaryText = remember(memberBots) {
        memberBots.joinToString(" · ") { it.name }.ifBlank { "" }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 4.dp)
                .size(actionWidth)
                .align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .clickable {
                        onOpenChanged(false)
                        onDelete()
                        itemScope.launch { swipeableState.animateTo(0) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Trash,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
                .background(Color(0xFFF1F1F1), RoundedCornerShape(20.dp))
                .swipeable(
                    state = swipeableState,
                    anchors = anchors,
                    thresholds = { _, _ -> FractionalThreshold(0.3f) },
                    orientation = Orientation.Horizontal
                )
                .clickable {
                    if (swipeableState.currentValue != 0) {
                        itemScope.launch { swipeableState.animateTo(0) }
                    } else {
                        onOpen()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BotAvatar(firstBot = firstBot)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = group.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (summaryText.isNotBlank()) {
                    Text(
                        text = summaryText,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun BotAvatar(firstBot: BotConfig?) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(GrayLighter),
        contentAlignment = Alignment.Center
    ) {
        when {
            firstBot?.avatarUri?.isNotBlank() == true -> {
                AsyncImage(
                    model = firstBot.avatarUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            firstBot?.avatarAssetName?.isNotBlank() == true -> {
                AsyncImage(
                    model = "file:///android_asset/avatars/${firstBot.avatarAssetName}",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            else -> {
                Icon(
                    imageVector = AppIcons.Bot,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
