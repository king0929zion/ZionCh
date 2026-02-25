package com.zionchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.GroupChatConfig
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.launch

private const val GROUP_STRATEGY_DYNAMIC = "dynamic"
private const val GROUP_STRATEGY_ROUND_ROBIN = "round_robin"

@Composable
fun CreateGroupChatScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val enabledModels = remember(models) { models.filter { it.enabled } }

    var name by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf(GROUP_STRATEGY_DYNAMIC) }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var coordinatorModelId by remember { mutableStateOf<String?>(null) }
    var showCoordinatorSelector by remember { mutableStateOf(false) }

    val canCreate = name.trim().isNotBlank() && selectedMembers.size >= 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = "Create Group",
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canCreate) Color(0xFF1C1C1E) else GrayLight)
                        .pressableScale(
                            pressedScale = 0.96f,
                            onClick = {
                                if (!canCreate) return@pressableScale
                                scope.launch {
                                    val finalName = name.trim()
                                    val sortedMembers = selectedMembers.toList()
                                    val coordinator =
                                        if (strategy == GROUP_STRATEGY_DYNAMIC) coordinatorModelId
                                        else null
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
                        text = "Create",
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
            // Group Name - 简约小输入框
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "GROUP NAME",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SourceSans3,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(TextPrimary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (name.isEmpty()) {
                                    Text(
                                        text = "Enter group name",
                                        fontSize = 15.sp,
                                        color = Color(0xFFC7C7CC)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            // Response Strategy - 两段式横向胶囊
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RESPONSE STRATEGY",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SourceSans3,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                
                // 两段式胶囊选择器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GrayLighter)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StrategyCapsule(
                        text = "Dynamic",
                        selected = strategy == GROUP_STRATEGY_DYNAMIC,
                        onClick = { strategy = GROUP_STRATEGY_DYNAMIC },
                        modifier = Modifier.weight(1f)
                    )
                    StrategyCapsule(
                        text = "Round Robin",
                        selected = strategy == GROUP_STRATEGY_ROUND_ROBIN,
                        onClick = { strategy = GROUP_STRATEGY_ROUND_ROBIN },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Coordinator Model - 只在Dynamic模式下显示
            AnimatedVisibility(
                visible = strategy == GROUP_STRATEGY_DYNAMIC,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "COORDINATOR MODEL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SourceSans3,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    
                    // 使用DefaultModel样式
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showCoordinatorSelector = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val display = coordinatorModelId?.let { id ->
                                models.firstOrNull { it.id == id }?.displayName
                                    ?: models.firstOrNull { extractRemoteModelId(it.id) == id }?.displayName
                            }
                            val isEmpty = display.isNullOrBlank()
                            Text(
                                text = if (isEmpty) "Select coordinator" else display.orEmpty(),
                                fontSize = 16.sp,
                                color = if (isEmpty) Color(0xFFC7C7CC) else TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
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

            // Members Selection - 使用DefaultModel样式
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEMBERS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SourceSans3,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${selectedMembers.size} selected",
                        fontSize = 13.sp,
                        color = if (selectedMembers.size >= 2) TextPrimary else TextSecondary
                    )
                }
                
                val grouped = remember(providers, enabledModels) { 
                    groupModelsByProvider(providers, enabledModels) 
                }
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    grouped.forEach { (providerName, providerModels) ->
                        Column {
                            Text(
                                text = providerName.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = GrayLighter)
                            ) {
                                providerModels.forEachIndexed { index, model ->
                                    val isSelected = selectedMembers.contains(model.id)
                                    MemberOptionRow(
                                        model = model,
                                        selected = isSelected,
                                        onClick = {
                                            selectedMembers =
                                                if (isSelected) selectedMembers - model.id 
                                                else selectedMembers + model.id
                                        }
                                    )
                                    if (index != providerModels.lastIndex) {
                                        Divider(color = GrayLight)
                                    }
                                }
                            }
                        }
                    }
                    if (enabledModels.isEmpty()) {
                        Text(
                            text = "No enabled models. Enable models in Settings first.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Coordinator Model Selector Modal
    CoordinatorModelSelectorModal(
        visible = showCoordinatorSelector,
        providers = providers,
        models = enabledModels.filter { selectedMembers.contains(it.id) },
        selectedModelId = coordinatorModelId,
        onSelect = { 
            coordinatorModelId = it
            showCoordinatorSelector = false 
        },
        onDismiss = { showCoordinatorSelector = false }
    )
}

@Composable
private fun StrategyCapsule(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Surface else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) TextPrimary else TextSecondary
        )
    }
}

@Composable
private fun MemberOptionRow(
    model: ModelConfig,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = model.displayName,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Text(
                text = extractRemoteModelId(model.id),
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        Icon(
            imageVector = AppIcons.Check,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier
                .size(22.dp)
                .alpha(if (selected) 1f else 0f)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CoordinatorModelSelectorModal(
    visible: Boolean,
    providers: List<ProviderConfig>,
    models: List<ModelConfig>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            M3Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    ),
                color = Surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .heightIn(max = 640.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
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
                        text = "Select Coordinator",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    )

                    val grouped = remember(providers, models) { 
                        groupModelsByProvider(providers, models) 
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (models.isEmpty()) {
                            Text(
                                text = "Select at least 2 members first",
                                fontSize = 15.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        grouped.forEach { (providerName, providerModels) ->
                            Column {
                                Text(
                                    text = providerName.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = GrayLighter)
                                ) {
                                    providerModels.forEachIndexed { index, model ->
                                        val isSelected = selectedModelId == model.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { onSelect(model.id) }
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
                                                    .size(22.dp)
                                                    .alpha(if (isSelected) 1f else 0f)
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

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun groupModelsByProvider(
    providers: List<ProviderConfig>,
    models: List<ModelConfig>
): List<Pair<String, List<ModelConfig>>> {
    val providerNameById = providers.associateBy({ it.id }, { it.name })
    return models
        .filter { it.enabled }
        .groupBy { model ->
            model.providerId?.let { providerNameById[it] } ?: "Other"
        }
        .toList()
        .sortedBy { it.first }
        .map { (name, list) -> name to list.sortedBy { it.displayName.lowercase() } }
}