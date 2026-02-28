package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.McpClient
import com.zionchat.app.data.McpConfig
import com.zionchat.app.data.McpProtocol
import com.zionchat.app.data.McpTool
import com.zionchat.app.ui.components.AppModalBottomSheet
import com.zionchat.app.ui.components.LiquidGlassSwitch
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun McpDetailScreen(
    navController: NavController,
    mcpId: String
) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val mcpClient = remember { McpClient() }

    val mcpListOrNull by produceState<List<McpConfig>?>(initialValue = null, repository) {
        repository.mcpListFlow.collect { value = it }
    }
    val mcp = mcpListOrNull?.firstOrNull { it.id == mcpId }

    var syncingTools by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var showToolDetail by remember { mutableStateOf<McpTool?>(null) }

    LaunchedEffect(mcpListOrNull, mcpId) {
        if (mcpListOrNull != null && mcp == null) {
            navController.navigateUp()
        }
    }

    if (mcpListOrNull == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFFFF)))
        return
    }

    if (mcp == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFFFF)))
        return
    }

    var serverUrl by remember(mcp.id) { mutableStateOf(mcp.url) }
    var serverUrlFocused by remember { mutableStateOf(false) }
    val latestStoredUrl by rememberUpdatedState(mcp.url)

    LaunchedEffect(mcp.url, serverUrlFocused) {
        if (!serverUrlFocused && serverUrl != mcp.url) {
            serverUrl = mcp.url
        }
    }

    LaunchedEffect(mcp.id) {
        snapshotFlow { serverUrl }
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce(350)
            .filter { it != latestStoredUrl.trim() }
            .onEach { value ->
                val current = repository.mcpListFlow.first().firstOrNull { it.id == mcp.id } ?: return@onEach
                repository.upsertMcp(current.copy(url = value))
            }
            .collect()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFFFF))) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageTopBar(
                title = mcp.name,
                onBack = { navController.navigateUp() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                McpSectionTitle(title = "Server URL")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        BasicTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { serverUrlFocused = it.isFocused },
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = TextPrimary
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(TextPrimary),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (serverUrl.isBlank()) {
                                        Text(
                                            text = "Enter server URL",
                                            fontSize = 16.sp,
                                            color = Color(0xFFC7C7CC)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                McpSectionTitle(title = "Available tools")
                McpToolsCard(
                    tools = mcp.tools,
                    syncing = syncingTools,
                    syncError = syncError,
                    onSync = {
                        if (syncingTools) return@McpToolsCard
                        syncingTools = true
                        syncError = null
                        scope.launch {
                            try {
                                mcpClient.fetchTools(mcp)
                                    .onSuccess { tools ->
                                        repository.updateMcpTools(mcp.id, tools)
                                    }
                                    .onFailure { error ->
                                        syncError = error.message.orEmpty().ifBlank { "Sync failed" }
                                    }
                            } finally {
                                syncingTools = false
                            }
                        }
                    },
                    onToolClick = { tool -> showToolDetail = tool }
                )
            }
        }

        showToolDetail?.let { tool ->
            val toolSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            AppModalBottomSheet(
                onDismissRequest = { showToolDetail = null },
                sheetState = toolSheetState
            ) {
                ToolDetailSheetContent(
                    tool = tool,
                    onDismiss = { showToolDetail = null }
                )
            }
        }
    }
}

@Composable
private fun McpSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = SourceSans3,
        color = TextSecondary,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun McpDetailItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(GrayLight, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontFamily = SourceSans3,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun McpInfoCard(
    mcp: McpConfig,
    onToggleEnabled: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GrayLighter),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Globe,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = mcp.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SourceSans3,
                color = TextPrimary
            )
            
            Text(
                text = mcp.protocol.name,
                fontSize = 15.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (mcp.enabled) Color(0xFF34C759).copy(alpha = 0.15f)
                            else GrayLighter
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (mcp.enabled) Color(0xFF34C759) else TextSecondary)
                    )
                    Text(
                        text = if (mcp.enabled) "Enabled" else "Disabled",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (mcp.enabled) Color(0xFF34C759) else TextSecondary
                    )
                }

                LiquidGlassSwitch(
                    checked = mcp.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }
        }
    }
}

@Composable
fun McpConnectionCard(mcp: McpConfig) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Connection",
                fontSize = 13.sp,
                fontFamily = SourceSans3,
                color = TextSecondary
            )
            
            ConnectionInfoRow(
                label = "Server URL",
                value = mcp.url
            )
            
            if (mcp.description.isNotBlank()) {
                HorizontalDivider(color = GrayLighter, thickness = 1.dp)
                ConnectionInfoRow(
                    label = "Description",
                    value = mcp.description
                )
            }
        }
    }
}

@Composable
fun ConnectionInfoRow(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = TextPrimary,
            maxLines = 3
        )
    }
}

@Composable
fun McpToolsCard(
    tools: List<McpTool>,
    syncing: Boolean,
    syncError: String?,
    onSync: () -> Unit,
    onToolClick: (McpTool) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tools.size} tools",
                    fontSize = 13.sp,
                    color = TextSecondary.copy(alpha = 0.7f)
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(GrayLighter, CircleShape)
                        .pressableScale(pressedScale = 0.95f) { if (!syncing) onSync() },
                    contentAlignment = Alignment.Center
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = TextSecondary
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "Sync",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (!syncError.isNullOrBlank()) {
                Text(
                    text = syncError,
                    fontSize = 13.sp,
                    color = Color(0xFFFF3B30)
                )
            }
            
            if (tools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(GrayLighter),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Globe,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = TextSecondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "No Tools Available",
                            fontSize = 16.sp,
                            color = TextSecondary
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "This MCP doesn't expose any tools",
                            fontSize = 13.sp,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tools.forEach { tool ->
                        ToolItem(
                            tool = tool,
                            onClick = { onToolClick(tool) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolItem(
    tool: McpTool,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = GrayLighter,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Globe,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextSecondary
                )
            }
            Text(
                text = tool.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFFC7C7CC)
            )
        }
    }
}

@Composable
fun ToolDetailSheetContent(
    tool: McpTool,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tool.name,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GrayLighter,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Description",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = tool.description,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }
            }

            Text(
                text = "Parameters",
                fontSize = 13.sp,
                fontFamily = SourceSans3,
                color = TextSecondary
            )

            if (tool.parameters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No parameters required",
                        fontSize = 14.sp,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tool.parameters.forEach { param ->
                        ParameterItem(param = param)
                    }
                }
            }
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = TextPrimary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Close",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ParameterItem(param: com.zionchat.app.data.McpToolParameter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GrayLighter,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = param.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = param.type,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                if (param.required) {
                    Text(
                        text = "required",
                        fontSize = 12.sp,
                        color = Color(0xFFFF3B30)
                    )
                } else {
                    Text(
                        text = "optional",
                        fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (param.description.isNotBlank()) {
                Text(
                    text = param.description,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

