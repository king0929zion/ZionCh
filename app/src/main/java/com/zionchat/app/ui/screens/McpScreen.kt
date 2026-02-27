@file:Suppress("DEPRECATION")

package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.HttpHeader
import com.zionchat.app.data.McpClient
import com.zionchat.app.data.McpConfig
import com.zionchat.app.data.McpProtocol
import com.zionchat.app.ui.components.AppModalBottomSheet
import com.zionchat.app.ui.components.EditableHeader
import com.zionchat.app.ui.components.HeadersEditorCard
import com.zionchat.app.ui.components.LiquidGlassSwitch
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val mcpClient = remember { McpClient() }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val mcpList by repository.mcpListFlow.collectAsState(initial = emptyList())
    
    var showAddModal by remember { mutableStateOf(false) }
    var editingMcp by remember { mutableStateOf<McpConfig?>(null) }
    
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageTopBar(
                title = stringResource(R.string.settings_item_mcp_tools),
                onBack = { navController.navigateUp() },
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Surface, CircleShape)
                            .pressableScale(pressedScale = 0.95f) {
                                editingMcp = null
                                showAddModal = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Plus,
                            contentDescription = stringResource(R.string.common_add),
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
            
            if (mcpList.isEmpty()) {
                McpEmptyState()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    mcpList.forEach { mcp ->
                        McpListItem(
                            mcp = mcp,
                            onClick = { navController.navigate("mcp_detail/${mcp.id}") },
                            onToggle = {
                                scope.launch {
                                    repository.toggleMcpEnabled(mcp.id)
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repository.deleteMcp(mcp.id)
                                }
                            }
                        )
                    }
                }
            }
        }
        
        if (showAddModal) {
            AppModalBottomSheet(
                onDismissRequest = { showAddModal = false },
                sheetState = addSheetState
            ) {
                McpEditSheetContent(
                    mcp = editingMcp,
                    onDismiss = { showAddModal = false },
                    onSave = { mcp ->
                        scope.launch {
                            repository.upsertMcp(mcp)
                            runCatching {
                                val tools = mcpClient.fetchTools(mcp).getOrNull()
                                if (tools != null) {
                                    repository.updateMcpTools(mcp.id, tools)
                                }
                            }
                            showAddModal = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun McpEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Globe,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.mcp_no_tools),
            fontSize = 17.sp,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = stringResource(R.string.mcp_tap_add_first),
            fontSize = 14.sp,
            color = TextSecondary.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun McpListItem(
    mcp: McpConfig,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val itemScope = rememberCoroutineScope()
    val actionWidth = 64.dp
    val density = LocalDensity.current
    val actionWidthPx = remember(density) { with(density) { actionWidth.toPx() } }
    val swipeableState = rememberSwipeableState(initialValue = 0)
    val anchors = remember(actionWidthPx) { mapOf(0f to 0, -actionWidthPx to 1) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(actionWidth)
                .align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDelete()
                        itemScope.launch { swipeableState.animateTo(0) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Trash,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
                .background(Surface, RoundedCornerShape(14.dp))
                .swipeable(
                    state = swipeableState,
                    anchors = anchors,
                    thresholds = { _, _ -> FractionalThreshold(0.3f) },
                    orientation = Orientation.Horizontal
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (swipeableState.currentValue != 0) {
                        itemScope.launch { swipeableState.animateTo(0) }
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Globe,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = TextSecondary
                )
            }

            Text(
                text = mcp.name.trim().ifBlank { "Unnamed" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SourceSans3,
                color = if (mcp.enabled) TextPrimary else TextSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            LiquidGlassSwitch(
                checked = mcp.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpEditSheetContent(
    mcp: McpConfig?,
    onDismiss: () -> Unit,
    onSave: (McpConfig) -> Unit
) {
    val isEditing = mcp != null
    
    var name by remember(mcp?.id) { mutableStateOf(mcp?.name.orEmpty()) }
    var url by remember(mcp?.id) { mutableStateOf(mcp?.url.orEmpty()) }
    var description by remember(mcp?.id) { mutableStateOf(mcp?.description.orEmpty()) }
    var protocol by remember(mcp?.id) { mutableStateOf(mcp?.protocol ?: McpProtocol.HTTP) }
    val headers = remember(mcp?.id) {
        mutableStateListOf<EditableHeader>().apply {
            mcp?.headers?.forEach { header -> add(EditableHeader(header.key, header.value)) }
        }
    }
    
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) stringResource(R.string.mcp_edit_title) else stringResource(R.string.mcp_add_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SourceSans3,
                color = TextPrimary
            )
            
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GrayLighter)
                    .pressableScale(pressedScale = 0.95f, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Protocol Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.mcp_protocol),
                    fontSize = 13.sp,
                    fontFamily = SourceSans3,
                    color = TextSecondary
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GrayLighter, RoundedCornerShape(20.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProtocolOption(
                        text = stringResource(R.string.mcp_protocol_http),
                        selected = protocol == McpProtocol.HTTP,
                        onClick = { protocol = McpProtocol.HTTP },
                        modifier = Modifier.weight(1f)
                    )
                    ProtocolOption(
                        text = stringResource(R.string.mcp_protocol_sse),
                        selected = protocol == McpProtocol.SSE,
                        onClick = { protocol = McpProtocol.SSE },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Name input
            McpConfigInputField(
                label = stringResource(R.string.mcp_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.mcp_name_placeholder)
            )
            
            // URL input
            McpConfigInputField(
                label = stringResource(R.string.mcp_server_url),
                value = url,
                onValueChange = { url = it },
                placeholder = "https://api.example.com/mcp"
            )
            
            // Description input
            McpConfigInputField(
                label = stringResource(R.string.mcp_description_optional),
                value = description,
                onValueChange = { description = it },
                placeholder = stringResource(R.string.mcp_description_placeholder)
            )
            
            HeadersEditorCard(headers = headers, title = stringResource(R.string.mcp_custom_headers))
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Buttons - fixed at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GrayLight,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            
            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onSave(
                            McpConfig(
                                id = mcp?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                url = url.trim(),
                                protocol = protocol,
                                enabled = mcp?.enabled ?: true,
                                description = description.trim(),
                                headers = headers
                                    .mapNotNull { header ->
                                        val key = header.key.trim()
                                        if (key.isBlank()) return@mapNotNull null
                                        HttpHeader(key = key, value = header.value.trim())
                                    }
                                    .distinctBy { it.key.trim().lowercase() },
                                tools = mcp?.tools.orEmpty(),
                                lastSyncAt = mcp?.lastSyncAt ?: 0L
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(
                    text = stringResource(R.string.common_save),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ProtocolOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TextPrimary else Color.Transparent)
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
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else TextPrimary
        )
    }
}

@Composable
fun McpConfigInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = SourceSans3,
            color = TextSecondary
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                placeholder = {
                    Text(
                        text = placeholder,
                        fontSize = 17.sp,
                        color = TextSecondary
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    color = TextPrimary
                ),
                singleLine = true
            )
        }
    }
}

