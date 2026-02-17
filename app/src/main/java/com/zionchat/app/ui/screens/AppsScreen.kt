package com.zionchat.app.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.gson.JsonParser
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalChatApiClient
import com.zionchat.app.LocalProviderAuthManager
import com.zionchat.app.LocalRuntimePackagingService
import com.zionchat.app.LocalWebHostingService
import com.zionchat.app.R
import com.zionchat.app.data.AppAutomationTask
import com.zionchat.app.data.ChatApiClient
import com.zionchat.app.data.HttpHeader
import com.zionchat.app.data.ProviderAuthManager
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.RuntimeShellPlugin
import com.zionchat.app.data.SavedApp
import com.zionchat.app.data.WebHostingConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.AppHtmlWebView
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import com.zionchat.app.ui.theme.Surface as SurfaceColor
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val APP_CHROME_COLOR_JS =
    "(function(){try{var meta=document.querySelector('meta[name=\\\"theme-color\\\"]');if(meta&&meta.content){return meta.content;}var body=document.body?window.getComputedStyle(document.body):null;if(body){var bodyBg=body.backgroundColor||'';if(bodyBg&&bodyBg!=='transparent'&&bodyBg!=='rgba(0, 0, 0, 0)'){return bodyBg;}}var root=document.documentElement?window.getComputedStyle(document.documentElement):null;if(root){var rootBg=root.backgroundColor||'';if(rootBg&&rootBg!=='transparent'&&rootBg!=='rgba(0, 0, 0, 0)'){return rootBg;}}return '';}catch(e){return '';}})();"

private data class AppAutoFixUiState(
    val isFixing: Boolean = false,
    val progress: Int = 0,
    val message: String? = null
)

private enum class AppsCategory(val label: String) {
    Featured("Featured"),
    Lifestyle("Lifestyle"),
    Productivity("Productivity")
}

@Composable
fun AppsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = LocalAppRepository.current
    val chatApiClient = LocalChatApiClient.current
    val providerAuthManager = LocalProviderAuthManager.current
    val runtimePackagingService = LocalRuntimePackagingService.current
    val webHostingService = LocalWebHostingService.current
    val scope = rememberCoroutineScope()
    val savedApps by repository.savedAppsFlow.collectAsState(initial = emptyList())
    val savedAppVersions by repository.savedAppVersionsFlow.collectAsState(initial = emptyList())
    val webHostingConfig by repository.webHostingConfigFlow.collectAsState(initial = WebHostingConfig())
    val appVersionModel by repository.appModuleVersionModelFlow.collectAsState(initial = 1)
    var runtimeShellInstalled by remember { mutableStateOf(RuntimeShellPlugin.isInstalled(context)) }
    var selectedSavedApp by remember { mutableStateOf<SavedApp?>(null) }
    var pendingDeleteApp by remember { mutableStateOf<SavedApp?>(null) }
    var openingAppId by remember { mutableStateOf<String?>(null) }
    var autoFixStateByAppId by remember { mutableStateOf<Map<String, AppAutoFixUiState>>(emptyMap()) }

    fun notifyRuntimeShellRequired() {
        Toast.makeText(
            context,
            context.getString(R.string.runtime_shell_required_toast),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun openRuntimeShellDownload() {
        val opened = RuntimeShellPlugin.openDownloadPage(context)
        if (!opened) {
            Toast.makeText(
                context,
                context.getString(R.string.runtime_shell_download_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val appContext = context.applicationContext
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                runtimeShellInstalled = RuntimeShellPlugin.isInstalled(context)
            }
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                        runtimeShellInstalled = RuntimeShellPlugin.isInstalled(appContext)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, intentFilter)
        }
        onDispose {
            runCatching { appContext.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val activeStatuses = setOf("queued", "in_progress")
        while (isActive) {
            val snapshot = repository.savedAppsFlow.first()
            val pendingApps =
                snapshot.filter { app ->
                    activeStatuses.contains(app.runtimeBuildStatus.trim().lowercase())
                }

            if (pendingApps.isNotEmpty()) {
                pendingApps.forEach { app ->
                    val synced = runtimePackagingService.syncRuntimePackaging(app).getOrNull() ?: return@forEach
                    if (synced != app) {
                        repository.upsertSavedApp(synced)
                        if (selectedSavedApp?.id == synced.id) {
                            selectedSavedApp = synced
                        }
                    }
                }
            }

            delay(if (pendingApps.isEmpty()) 12_000L else 4_000L)
        }
    }

    BackHandler(enabled = selectedSavedApp != null) {
        selectedSavedApp = null
    }

    val activePreviewApp = selectedSavedApp
    if (activePreviewApp != null) {
        val autoFixState = autoFixStateByAppId[activePreviewApp.id]
        SavedAppPreviewPage(
            app = activePreviewApp,
            canRedeploy = runtimeShellInstalled && webHostingConfig.autoDeploy && webHostingConfig.token.isNotBlank(),
            autoFixState = autoFixState,
            onDismiss = { selectedSavedApp = null },
            onRequestEdit = { request ->
                repository.enqueueAppAutomationTask(
                    AppAutomationTask(
                        mode = "edit",
                        appId = activePreviewApp.id,
                        appName = activePreviewApp.name,
                        appHtml = activePreviewApp.html,
                        request = request
                    )
                )
                selectedSavedApp = null
                navController.navigate("chat")
            },
            onRequestAutoFix = { issue ->
                if (autoFixState?.isFixing == true) return@SavedAppPreviewPage
                val appId = activePreviewApp.id
                scope.launch {
                    autoFixStateByAppId =
                        autoFixStateByAppId + (appId to AppAutoFixUiState(isFixing = true, progress = 2, message = "修复中"))
                    val fixedHtml =
                        runCatching {
                            runDirectAppDeveloperAutoFix(
                                chatApiClient = chatApiClient,
                                repository = repository,
                                providerAuthManager = providerAuthManager,
                                app = activePreviewApp,
                                issue = issue,
                                onProgress = { progress ->
                                    autoFixStateByAppId =
                                        autoFixStateByAppId + (
                                            appId to AppAutoFixUiState(
                                                isFixing = true,
                                                progress = progress.coerceIn(1, 99),
                                                message = "修复中"
                                            )
                                        )
                                }
                            )
                        }
                    fixedHtml.onSuccess { html ->
                        val updated =
                            activePreviewApp.copy(
                                html = html,
                                deployUrl = null,
                                runtimeBuildStatus = "",
                                runtimeBuildRequestId = null,
                                runtimeBuildRunId = null,
                                runtimeBuildRunUrl = null,
                                runtimeBuildArtifactName = null,
                                runtimeBuildArtifactUrl = null,
                                runtimeBuildError = null,
                                runtimeBuildUpdatedAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        val persisted = repository.upsertSavedApp(updated, note = "AI auto fix") ?: updated
                        selectedSavedApp = persisted
                        autoFixStateByAppId =
                            autoFixStateByAppId + (appId to AppAutoFixUiState(isFixing = false, progress = 100, message = "已修复"))
                        delay(1400)
                        if (autoFixStateByAppId[appId]?.isFixing != true) {
                            autoFixStateByAppId = autoFixStateByAppId - appId
                        }
                    }.onFailure { error ->
                        val message = error.message?.trim().orEmpty().ifBlank { "自动修复失败，请稍后重试。" }
                        autoFixStateByAppId =
                            autoFixStateByAppId + (
                                appId to AppAutoFixUiState(
                                    isFixing = false,
                                    progress = 0,
                                    message = message.take(140)
                                )
                            )
                    }
                }
            },
            onRedeploy = { targetApp ->
                if (!runtimeShellInstalled) {
                    notifyRuntimeShellRequired()
                    openRuntimeShellDownload()
                    return@SavedAppPreviewPage
                }
                if (!webHostingConfig.autoDeploy || webHostingConfig.token.isBlank()) {
                    Toast.makeText(context, "Please configure web hosting first.", Toast.LENGTH_SHORT).show()
                    return@SavedAppPreviewPage
                }
                scope.launch {
                    webHostingService.deployApp(
                        appId = targetApp.id,
                        html = targetApp.html,
                        config = webHostingConfig
                    ).onSuccess { url ->
                        val updated =
                            targetApp.copy(
                                deployUrl = url.trim(),
                                runtimeBuildStatus = "",
                                runtimeBuildRequestId = null,
                                runtimeBuildRunId = null,
                                runtimeBuildRunUrl = null,
                                runtimeBuildArtifactName = null,
                                runtimeBuildArtifactUrl = null,
                                runtimeBuildError = null,
                                runtimeBuildUpdatedAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        val persisted = repository.upsertSavedApp(updated, note = "Manual redeploy") ?: updated
                        val packaged =
                            runtimePackagingService
                                .triggerRuntimePackaging(
                                    app = persisted,
                                    deployUrl = url.trim(),
                                    versionModel = appVersionModel
                                )
                                .getOrElse { throwable ->
                                    persisted.copy(
                                        runtimeBuildStatus = "failed",
                                        runtimeBuildError =
                                            throwable.message?.trim()?.takeIf { it.isNotBlank() }
                                                ?: "Runtime packaging failed",
                                        runtimeBuildVersionModel = appVersionModel.coerceAtLeast(1),
                                        runtimeBuildUpdatedAt = System.currentTimeMillis()
                                    )
                                }
                        val finalApp = repository.upsertSavedApp(packaged) ?: packaged
                        selectedSavedApp = finalApp
                    }
                }
            },
            versionCount = savedAppVersions.count { version -> version.appId == activePreviewApp.id },
            onRestorePreviousVersion = { targetApp ->
                scope.launch {
                    val versions =
                        repository.listSavedAppVersions(targetApp.id)
                            .sortedByDescending { it.versionCode }
                    if (versions.size < 2) return@launch
                    val previousCode = versions[1].versionCode
                    val restored =
                        repository.restoreSavedAppVersion(
                            appId = targetApp.id,
                            versionCode = previousCode
                        )
                    if (restored != null) {
                        selectedSavedApp = restored
                    }
                }
            }
        )
        return
    }

    var selectedCategory by rememberSaveable { mutableStateOf(AppsCategory.Featured) }
    val sortedApps = remember(savedApps) { savedApps.sortedByDescending { it.updatedAt } }
    val visibleApps = remember(sortedApps, selectedCategory) {
        filterAppsByCategory(sortedApps, selectedCategory)
    }
    val listBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = Color.White,
        topBar = {
            AppsTopBar(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("chat") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(top = padding.calculateTopPadding())
        ) {
            AppsCategoryTabs(
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = listBottomInset + 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!runtimeShellInstalled) {
                    item(key = "runtime_shell_required") {
                        RuntimeShellInstallRow(onClick = { openRuntimeShellDownload() })
                    }
                }

                if (visibleApps.isEmpty()) {
                    item(key = "apps_empty_state") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AppsListEmptyState(
                                category = selectedCategory,
                                onCreate = { navController.navigate("chat") }
                            )
                        }
                    }
                } else {
                    items(
                        items = visibleApps,
                        key = { app -> app.id }
                    ) { app ->
                        SavedAppListRow(
                            app = app,
                            isLaunching = openingAppId == app.id,
                            onClick = {
                                if (openingAppId != null) return@SavedAppListRow
                                openingAppId = app.id
                                scope.launch {
                                    delay(120)
                                    selectedSavedApp = app
                                    openingAppId = null
                                }
                            },
                            onLongClick = { pendingDeleteApp = app }
                        )
                    }
                }
            }
        }
    }

    pendingDeleteApp?.let { targetApp ->
        AlertDialog(
            onDismissRequest = { pendingDeleteApp = null },
            title = { Text(text = "删除应用") },
            text = {
                Text(
                    text = "确定删除「${targetApp.name}」吗？该应用及历史版本会被永久移除。",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteApp = null
                        autoFixStateByAppId = autoFixStateByAppId - targetApp.id
                        if (selectedSavedApp?.id == targetApp.id) {
                            selectedSavedApp = null
                        }
                        scope.launch {
                            runCatching { repository.deleteSavedApp(targetApp.id) }
                                .onSuccess {
                                    Toast.makeText(context, "应用已删除", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { error ->
                                    val message = error.message?.trim().orEmpty().ifBlank { "删除失败，请重试。" }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteApp = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun RuntimeShellRequiredCard(
    templateLabel: String,
    isInstalled: Boolean,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.runtime_shell_required_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (isInstalled) Color.White else Color.Black,
                            RoundedCornerShape(999.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isInstalled) Color.Black else Color.Black,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = 130.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isInstalled) Color.Black else Color.White,
                                    CircleShape
                                )
                        )
                        Text(
                            text =
                                stringResource(
                                    if (isInstalled) R.string.runtime_shell_status_installed
                                    else R.string.runtime_shell_status_missing
                                ),
                            fontSize = 11.sp,
                            color = if (isInstalled) Color.Black else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.runtime_shell_required_subtitle, templateLabel),
                fontSize = 13.sp,
                color = TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(Color.Black, RoundedCornerShape(14.dp))
                        .pressableScale(pressedScale = 0.98f, onClick = onDownload),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.runtime_shell_download_button),
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(14.dp))
                        .pressableScale(pressedScale = 0.98f, onClick = onRefresh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.runtime_shell_check_button),
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsTopBar(
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F7), CircleShape)
                .pressableScale(pressedScale = 0.95f, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Back,
                contentDescription = "Back",
                tint = Color(0xFF1C1C1E),
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.apps),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E)
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F7), CircleShape)
                .pressableScale(pressedScale = 0.95f, onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Plus,
                contentDescription = "Add",
                tint = Color(0xFF1C1C1E),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AppsCategoryTabs(
    selected: AppsCategory,
    onSelect: (AppsCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppsCategory.entries.forEach { category ->
            val active = category == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) Color(0xFFF2F2F7) else Color.Transparent, RoundedCornerShape(999.dp))
                    .pressableScale(pressedScale = 0.97f, onClick = { onSelect(category) })
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    color = Color(0xFF1C1C1E),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RuntimeShellInstallRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF5F5F7), RoundedCornerShape(14.dp))
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1E))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Runtime shell required",
                color = Color(0xFF1C1C1E),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Tap to install plugin package",
                color = Color(0xFF8E8E93),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFC7C7CC),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AppsListEmptyState(
    category: AppsCategory,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "No apps in ${category.label}",
            color = Color(0xFF8E8E93),
            fontSize = 14.sp
        )
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1E), RoundedCornerShape(10.dp))
                .pressableScale(pressedScale = 0.97f, onClick = onCreate)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Create app",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SavedAppListRow(
    app: SavedApp,
    isLaunching: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val baseColor = remember(app.id, app.html) {
        inferAppChromeColorFromHtml(app.html) ?: Color(0xFF1F2024)
    }
    val iconStart = remember(baseColor) {
        blendColor(baseColor, Color.White, if (baseColor.luminance() < 0.45f) 0.24f else 0.1f)
    }
    val iconEnd = remember(baseColor) {
        blendColor(baseColor, Color.Black, if (baseColor.luminance() > 0.58f) 0.2f else 0.16f)
    }
    val glyphTint = if (baseColor.luminance() > 0.58f) Color.Black else Color.White
    val runtimeStatus = app.runtimeBuildStatus.trim().lowercase()
    val isBuilding = runtimeStatus == "queued" || runtimeStatus == "in_progress"
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var entered by remember(app.id) { mutableStateOf(false) }
    LaunchedEffect(app.id) { entered = true }
    val entryAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "app_list_row_entry_alpha"
    )
    val entryTranslateY by animateFloatAsState(
        targetValue = if (entered) 0f else 18f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "app_list_row_entry_translate"
    )
    val pressedScale by animateFloatAsState(
        targetValue =
            when {
                isLaunching -> 0.978f
                pressed -> 0.992f
                else -> 1f
            },
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "app_list_row_press_scale"
    )
    val rowBg by animateColorAsState(
        targetValue = if (pressed) Color(0xFFF5F5F7) else Color.Transparent,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "app_list_row_bg"
    )
    val subtitle =
        when {
            isBuilding -> "Runtime packaging in progress"
            app.description.isNotBlank() -> app.description
            else -> "Open app preview"
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entryAlpha
                translationY = entryTranslateY
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg, RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 2.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(iconStart, iconEnd)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appMonogram(app.name),
                color = glyphTint,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isBuilding) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black, CircleShape)
                        .border(width = 2.dp, color = Color.White, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "•",
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 9.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = app.name,
                color = Color(0xFF1C1C1E),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFC7C7CC),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun RuntimeShellReadyBadge(templateLabel: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF2F2F5), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.Black)
        )
        Text(
            text = "Runtime shell ready",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = templateLabel,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmptyDesktopState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CreateAppDesktopTile(onClick = onCreate)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.apps_empty_saved),
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CreateAppDesktopTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(pressedScale = 0.96f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF1F1F5), RoundedCornerShape(20.dp))
                .border(width = 1.dp, color = Color(0xFFE2E2E8), shape = RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Plus,
                contentDescription = "Create app",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = stringResource(R.string.common_add),
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SavedAppDesktopTile(
    app: SavedApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val baseColor = remember(app.id, app.html) {
        inferAppChromeColorFromHtml(app.html) ?: Color(0xFF1F2024)
    }
    val iconStart = remember(baseColor) {
        blendColor(baseColor, Color.White, if (baseColor.luminance() < 0.45f) 0.22f else 0.08f)
    }
    val iconEnd = remember(baseColor) {
        blendColor(baseColor, Color.Black, if (baseColor.luminance() > 0.58f) 0.24f else 0.18f)
    }
    val glyphTint = if (baseColor.luminance() > 0.58f) Color.Black else Color.White

    val runtimeStatus = app.runtimeBuildStatus.trim().lowercase()
    val isBuilding = runtimeStatus == "queued" || runtimeStatus == "in_progress"
    val tileBg by animateColorAsState(
        targetValue = if (isBuilding) Color(0xFFFAFAFC) else Color.Transparent,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "desktop_tile_bg"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tileBg, RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(listOf(iconStart, iconEnd)),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = glyphTint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AppDevRingGlyph(
                modifier = Modifier.size(32.dp),
                tint = glyphTint
            )
            if (isBuilding) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                }
            }
        }

        Text(
            text = app.name,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.widthIn(max = 82.dp)
        )
    }
}

@Composable
private fun AppDevRingGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.24f
        val arcDiameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            x = (size.width - arcDiameter) / 2f,
            y = (size.height - arcDiameter) / 2f
        )
        drawArc(
            color = tint,
            startAngle = -90f,
            sweepAngle = 312f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(arcDiameter, arcDiameter),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun SavedAppPreviewPage(
    app: SavedApp,
    canRedeploy: Boolean,
    autoFixState: AppAutoFixUiState?,
    onDismiss: () -> Unit,
    onRequestEdit: (String) -> Unit,
    onRequestAutoFix: (String) -> Unit,
    onRedeploy: (SavedApp) -> Unit,
    versionCount: Int,
    onRestorePreviousVersion: (SavedApp) -> Unit
) {
    var chromeColor by remember(app.id, app.html) {
        mutableStateOf(inferAppChromeColorFromHtml(app.html) ?: Color(0xFFF5F5F7))
    }
    var showEditDialog by remember(app.id) { mutableStateOf(false) }
    var editRequestText by remember(app.id) { mutableStateOf("") }
    var debugIssue by remember(app.id) { mutableStateOf<String?>(null) }
    var issueFingerprints by remember(app.id) { mutableStateOf(setOf<String>()) }
    val baseUrl = remember(app.id) { "https://saved-app.zionchat.local/app/${app.id}/" }
    val deployUrl = remember(app.deployUrl) { app.deployUrl?.trim()?.takeIf { it.isNotBlank() } }
    val contentSignature = remember(app.id, app.html, deployUrl) { "${app.id}:${app.html.hashCode()}:${deployUrl.orEmpty()}" }
    var firstVisualReady by remember(contentSignature) { mutableStateOf(false) }
    var previewEntering by remember(contentSignature) { mutableStateOf(false) }
    LaunchedEffect(contentSignature) {
        previewEntering = true
    }
    val webContentVisible = previewEntering && firstVisualReady
    val webContentAlpha by animateFloatAsState(
        targetValue = if (webContentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "preview_web_alpha"
    )
    val webContentScale by animateFloatAsState(
        targetValue = if (webContentVisible) 1f else 1.014f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "preview_web_scale"
    )
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (webContentVisible) 0f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "preview_placeholder_alpha"
    )
    val reportIssue = rememberUpdatedState<(String) -> Unit> { raw ->
        if (autoFixState?.isFixing == true) return@rememberUpdatedState
        val normalized = raw.trim().replace(Regex("\\s+"), " ").take(480)
        if (normalized.isBlank()) return@rememberUpdatedState
        val key = normalized.lowercase()
        if (issueFingerprints.contains(key)) return@rememberUpdatedState
        issueFingerprints = issueFingerprints + key
        if (debugIssue.isNullOrBlank()) {
            debugIssue = normalized
        }
    }

    BackHandler(onBack = onDismiss)
    FullscreenPreviewSystemBarsEffect()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chromeColor)
    ) {
        AppHtmlWebView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = webContentAlpha
                        scaleX = webContentScale
                        scaleY = webContentScale
                    },
            contentSignature = contentSignature,
            html = if (deployUrl.isNullOrBlank()) app.html else null,
            baseUrl = baseUrl,
            url = deployUrl,
            enableCookies = true,
            enableThirdPartyCookies = true,
            transparentBackground = false,
            backgroundColor = chromeColor,
            preRenderEnabled = true,
            onRuntimeIssue = { raw -> reportIssue.value(raw) },
            onPageCommitVisible = { firstVisualReady = true },
            onPageFinished = { webView ->
                webView.evaluateJavascript(APP_CHROME_COLOR_JS) { jsResult ->
                    parseCssColorFromJs(jsResult)?.let { parsed ->
                        chromeColor = parsed
                    }
                }
                firstVisualReady = true
            }
        )

        if (placeholderAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = placeholderAlpha }
                    .background(chromeColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Color.Black.copy(alpha = if (chromeColor.luminance() < 0.46f) 0.24f else 0.08f),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AppDevRingGlyph(
                        modifier = Modifier.size(30.dp),
                        tint = if (chromeColor.luminance() < 0.46f) Color.White else Color.Black
                    )
                }
            }
        }

    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(text = "Edit app with AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Describe the changes you want for ${app.name}.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = editRequestText,
                        onValueChange = { editRequestText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val request = editRequestText.trim()
                        if (request.isBlank()) return@TextButton
                        showEditDialog = false
                        editRequestText = ""
                        onRequestEdit(request)
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    debugIssue?.let { issue ->
        AlertDialog(
            onDismissRequest = {
                if (autoFixState?.isFixing != true) {
                    debugIssue = null
                }
            },
            title = { Text("App error detected") },
            text = {
                Text(
                    text = "Detected a runtime issue:\n$issue\n\nRun one-click AI fix?",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (autoFixState?.isFixing == true) return@TextButton
                        debugIssue = null
                        onRequestAutoFix(issue)
                    }
                ) {
                    Text(if (autoFixState?.isFixing == true) "Fixing..." else "Fix now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (autoFixState?.isFixing != true) {
                            debugIssue = null
                        }
                    }
                ) {
                    Text("Later")
                }
            }
        )
    }
}

@Composable
private fun AppPreviewTopChrome(
    appName: String,
    controlsBackground: Color,
    controlsBorder: Color,
    controlsTint: Color,
    canRedeploy: Boolean,
    onDismiss: () -> Unit,
    onRedeploy: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CircleIconButton(
            icon = AppIcons.Back,
            contentDescription = "Back",
            tint = controlsTint,
            backgroundColor = controlsBackground,
            borderColor = controlsBorder,
            onClick = onDismiss
        )

        Text(
            text = appName,
            color = controlsTint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            textAlign = TextAlign.Center
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRedeploy) {
                CircleIconButton(
                    icon = AppIcons.Refresh,
                    contentDescription = "Redeploy",
                    tint = controlsTint,
                    backgroundColor = controlsBackground,
                    borderColor = controlsBorder,
                    onClick = onRedeploy
                )
            }
            CircleIconButton(
                icon = AppIcons.Edit,
                contentDescription = "Edit",
                tint = controlsTint,
                backgroundColor = controlsBackground,
                borderColor = controlsBorder,
                onClick = onEdit
            )
        }
    }
}

@Composable
private fun AppPreviewBottomChrome(
    versionName: String,
    versionCount: Int,
    controlsBackground: Color,
    controlsBorder: Color,
    controlsTint: Color,
    canRestore: Boolean,
    onRestore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(controlsBackground, RoundedCornerShape(14.dp))
                .border(width = 1.dp, color = controlsBorder, shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "$versionName • $versionCount versions",
                color = controlsTint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (canRestore) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(controlsBackground, RoundedCornerShape(14.dp))
                    .border(width = 1.dp, color = controlsBorder, shape = RoundedCornerShape(14.dp))
                    .pressableScale(pressedScale = 0.97f, onClick = onRestore)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Restore previous",
                    color = controlsTint,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    backgroundColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .pressableScale(pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FullscreenPreviewSystemBarsEffect() {
    val view = LocalView.current
    val activity = remember(view.context) { view.context.findActivity() }

    DisposableEffect(activity) {
        val hostActivity = activity
        if (hostActivity == null) {
            onDispose {}
        } else {
            val window = hostActivity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousLightStatus = controller.isAppearanceLightStatusBars
            val previousLightNavigation = controller.isAppearanceLightNavigationBars

            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())

            onDispose {
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.isAppearanceLightStatusBars = previousLightStatus
                controller.isAppearanceLightNavigationBars = previousLightNavigation
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun filterAppsByCategory(
    apps: List<SavedApp>,
    category: AppsCategory
): List<SavedApp> {
    return when (category) {
        AppsCategory.Featured -> apps
        AppsCategory.Lifestyle -> apps.filter { inferAppsCategory(it) == AppsCategory.Lifestyle }
        AppsCategory.Productivity -> apps.filter { inferAppsCategory(it) == AppsCategory.Productivity }
    }
}

private fun inferAppsCategory(app: SavedApp): AppsCategory {
    val text = "${app.name} ${app.description}".lowercase()
    val lifestyleKeywords = listOf(
        "life", "lifestyle", "travel", "trip", "hotel", "booking", "music", "fitness", "health",
        "food", "restaurant", "recipe", "habit", "daily", "journal", "旅行", "酒店", "音乐", "生活", "健康", "餐厅", "饮食"
    )
    val productivityKeywords = listOf(
        "todo", "task", "calendar", "note", "project", "plan", "work", "study", "kanban", "crm",
        "dashboard", "invoice", "report", "editor", "效率", "任务", "计划", "工作", "管理", "日程"
    )
    return when {
        lifestyleKeywords.any { keyword -> text.contains(keyword) } -> AppsCategory.Lifestyle
        productivityKeywords.any { keyword -> text.contains(keyword) } -> AppsCategory.Productivity
        else -> AppsCategory.Featured
    }
}

private fun appMonogram(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return "A"
    val first = trimmed.firstOrNull { it.isLetterOrDigit() } ?: trimmed.first()
    return first.toString().uppercase()
}

private fun blendColor(start: Color, end: Color, ratio: Float): Color {
    val t = ratio.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

private fun inferAppChromeColorFromHtml(html: String): Color? {
    val source = html.trim()
    if (source.isBlank()) return null
    val patterns =
        listOf(
            Regex(
                "<meta[^>]*name\\s*=\\s*[\"']theme-color[\"'][^>]*content\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "<meta[^>]*content\\s*=\\s*[\"']([^\"']+)[\"'][^>]*name\\s*=\\s*[\"']theme-color[\"'][^>]*>",
                RegexOption.IGNORE_CASE
            ),
            Regex("\"theme_color\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("\"background_color\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("background-color\\s*:\\s*([^;\"'>]+)", RegexOption.IGNORE_CASE),
            Regex("background\\s*:\\s*(#[0-9a-fA-F]{3,8}|rgba?\\([^\\)]*\\))", RegexOption.IGNORE_CASE)
        )
    for (pattern in patterns) {
        val match = pattern.find(source) ?: continue
        val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (value.isBlank()) continue
        val parsed = parseCssColorFromJs("\"$value\"")
        if (parsed != null) return parsed
    }
    return null
}

private fun parseCssColorFromJs(jsValue: String?): Color? {
    val raw =
        jsValue
            ?.trim()
            ?.trim('"')
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.trim()
            .orEmpty()
    if (raw.isBlank()) return null
    if (raw.equals("transparent", ignoreCase = true)) return null
    if (raw.equals("rgba(0, 0, 0, 0)", ignoreCase = true)) return null

    if (raw.startsWith("#")) {
        return runCatching { Color(AndroidColor.parseColor(raw)) }.getOrNull()
    }

    val rgba =
        Regex("""rgba?\(\s*([0-9]{1,3})\s*,\s*([0-9]{1,3})\s*,\s*([0-9]{1,3})(?:\s*,\s*([0-9]*\.?[0-9]+)\s*)?\)""")
            .find(raw)
            ?.groupValues
    if (rgba != null) {
        val r = rgba[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val g = rgba[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val b = rgba[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val a =
            rgba.getOrNull(4)?.takeIf { it.isNotBlank() }?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a)
    }

    return runCatching { Color(AndroidColor.parseColor(raw)) }.getOrNull()
}

private fun stripMarkdownCodeFences(text: String): String {
    var trimmed = text.trim()
    if (trimmed.startsWith("```")) {
        trimmed = trimmed.substringAfter('\n', trimmed).trim()
    }
    if (trimmed.endsWith("```")) {
        trimmed = trimmed.dropLast(3).trim()
    }
    return trimmed.trim()
}

private data class ResolvedAppDeveloperModelForAutoFix(
    val provider: ProviderConfig,
    val modelId: String,
    val extraHeaders: List<HttpHeader>
)

private suspend fun runDirectAppDeveloperAutoFix(
    chatApiClient: ChatApiClient,
    repository: com.zionchat.app.data.AppRepository,
    providerAuthManager: ProviderAuthManager,
    app: SavedApp,
    issue: String,
    onProgress: ((Int) -> Unit)? = null
): String {
    val resolved = resolveAppDeveloperModelForAutoFix(repository, providerAuthManager)
    return reviseHtmlForAutoFix(
        chatApiClient = chatApiClient,
        provider = resolved.provider,
        modelId = resolved.modelId,
        extraHeaders = resolved.extraHeaders,
        currentHtml = app.html,
        appName = app.name,
        issue = issue,
        onProgress = onProgress
    )
}

private suspend fun resolveAppDeveloperModelForAutoFix(
    repository: com.zionchat.app.data.AppRepository,
    providerAuthManager: ProviderAuthManager
): ResolvedAppDeveloperModelForAutoFix {
    val allModels = repository.modelsFlow.first()
    val enabledModels = allModels.filter { it.enabled }
    val modelPool = if (enabledModels.isNotEmpty()) enabledModels else allModels
    if (modelPool.isEmpty()) {
        error("No model configured for APP developer.")
    }
    val preferredModelKey = repository.defaultAppBuilderModelIdFlow.first()?.trim().orEmpty()
    if (preferredModelKey.isBlank()) {
        error("App Development model is not configured. Set it in Settings → Default model.")
    }
    val selectedModel =
        modelPool.firstOrNull { it.id == preferredModelKey }
            ?: modelPool.firstOrNull { extractRemoteModelId(it.id) == preferredModelKey }
            ?: error("Configured App Development model was not found. Re-select it in Settings → Default model.")
    val providers = repository.providersFlow.first()
    val providerRaw =
        selectedModel.providerId?.let { providerId -> providers.firstOrNull { it.id == providerId } }
            ?: error("Configured App Development model provider is missing.")
    if (providerRaw.apiUrl.isBlank() || providerRaw.apiKey.isBlank()) {
        error("Configured App Development model provider is not configured.")
    }
    val provider = providerAuthManager.ensureValidProvider(providerRaw)
    return ResolvedAppDeveloperModelForAutoFix(
        provider = provider,
        modelId = extractRemoteModelId(selectedModel.id),
        extraHeaders = selectedModel.headers
    )
}

private suspend fun reviseHtmlForAutoFix(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    extraHeaders: List<HttpHeader>,
    currentHtml: String,
    appName: String,
    issue: String,
    onProgress: ((Int) -> Unit)? = null
): String {
    val html = currentHtml.trim()
    val report = issue.trim()
    if (html.isBlank()) error("Current HTML is empty.")
    if (report.isBlank()) error("Bug report is empty.")

    val systemPrompt =
        buildAppDeveloperSystemPrompt(
            additionalInstructions =
                "You are APP developer model. Fix the provided HTML app and return ONLY one complete updated HTML document. " +
                    "Do not output markdown fences. Keep unaffected behavior unchanged. " +
                    "All UI interactions must be real and functional."
        )
    val userPrompt =
        buildString {
            appendLine("Please fix runtime errors for this app.")
            append("App name: ")
            appendLine(appName.trim().ifBlank { "App" })
            append("Bug report: ")
            appendLine(report)
            appendLine()
            appendLine("Requirements:")
            appendLine("- Preserve current visual style unless bug fix requires minimal UI changes.")
            appendLine("- Keep existing working modules unchanged.")
            appendLine("- No mock handlers, no TODO placeholders.")
            appendLine("- Return full HTML document.")
            appendLine()
            appendLine("Current HTML:")
            appendLine(html)
        }.trim()

    var emittedProgress = 10
    var chunkCount = 0
    var charCount = 0
    val startedAtMs = System.currentTimeMillis()
    onProgress?.invoke(emittedProgress)

    val raw =
        collectStreamContentForAutoFix(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                com.zionchat.app.data.Message(role = "system", content = systemPrompt),
                com.zionchat.app.data.Message(role = "user", content = userPrompt)
            ),
            extraHeaders = extraHeaders,
            onChunk = { chunk ->
                chunkCount += 1
                charCount += chunk.length
                val elapsedBoost = ((System.currentTimeMillis() - startedAtMs) / 520L).toInt().coerceAtMost(16)
                val chunkBoost = (chunkCount * 3).coerceAtMost(50)
                val sizeBoost = (charCount / 150).coerceAtMost(16)
                val nextProgress = (10 + elapsedBoost + chunkBoost + sizeBoost).coerceAtMost(94)
                if (nextProgress > emittedProgress) {
                    emittedProgress = nextProgress
                    onProgress?.invoke(nextProgress)
                }
            }
        )
    if (emittedProgress < 95) {
        onProgress?.invoke(95)
    }
    return normalizeGeneratedHtmlForAutoFix(raw)
}

private suspend fun collectStreamContentForAutoFix(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    messages: List<com.zionchat.app.data.Message>,
    extraHeaders: List<HttpHeader>,
    onChunk: ((String) -> Unit)? = null
): String {
    val contentBuilder = StringBuilder()
    chatApiClient.chatCompletionsStream(
        provider = provider,
        modelId = modelId,
        messages = messages,
        extraHeaders = extraHeaders
    ).collect { delta ->
        val chunk = delta.content ?: return@collect
        if (chunk.isEmpty()) return@collect
        contentBuilder.append(chunk)
        onChunk?.invoke(chunk)
    }
    return contentBuilder.toString().trim()
}

private fun normalizeGeneratedHtmlForAutoFix(raw: String): String {
    val normalized = normalizeGeneratedHtmlCandidateForAutoFix(raw, wrapIfNeeded = true)
    if (normalized.isBlank()) error("Generated HTML is empty.")
    return normalized
}

private fun normalizeGeneratedHtmlCandidateForAutoFix(raw: String, wrapIfNeeded: Boolean): String {
    val extractedPayload = extractGeneratedHtmlPayloadForAutoFix(raw)
    val decoded = decodeEscapedHtmlSequencesForAutoFix(extractedPayload)
    val normalizedNewline = decoded.replace("\r\n", "\n").replace("\r", "\n").trim()
    if (normalizedNewline.isBlank()) return ""

    val htmlSegment = extractHtmlDocumentSegmentForAutoFix(normalizedNewline)
    val cleaned = stripMarkdownCodeFences(htmlSegment).trim()
    if (cleaned.isBlank()) return ""

    val lower = cleaned.lowercase()
    if (lower.startsWith("<!doctype html") || lower.contains("<html")) {
        return cleaned
    }
    if (!wrapIfNeeded) return cleaned
    return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\" />\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n<title>Generated App</title>\n</head>\n<body>\n$cleaned\n</body>\n</html>"
}

private fun extractGeneratedHtmlPayloadForAutoFix(raw: String): String {
    val stripped = stripMarkdownCodeFences(raw).trim()
    if (stripped.isBlank()) return ""
    val parsed = runCatching { JsonParser.parseString(stripped) }.getOrNull()
    if (parsed == null) return stripped
    if (parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString) {
        return parsed.asString
    }
    if (parsed.isJsonObject) {
        val obj = parsed.asJsonObject
        val keys = listOf("html", "code", "content", "result", "output")
        keys.forEach { key ->
            val value = obj.get(key)
            if (value != null && value.isJsonPrimitive) {
                val text = value.asString.trim()
                if (text.isNotBlank()) return text
            }
        }
    }
    return stripped
}

private fun decodeEscapedHtmlSequencesForAutoFix(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
        val decodedQuoted =
            runCatching { JsonParser.parseString(trimmed) }
                .getOrNull()
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
        if (!decodedQuoted.isNullOrBlank()) return decodedQuoted
    }
    val looksEscaped =
        trimmed.contains("\\n") ||
            trimmed.contains("\\r") ||
            trimmed.contains("\\t") ||
            trimmed.contains("\\u003", ignoreCase = true) ||
            trimmed.contains("\\\"") ||
            trimmed.contains("\\/")
    if (!looksEscaped) return trimmed
    return trimmed
        .replace(Regex("\\\\u003[cC]"), "<")
        .replace(Regex("\\\\u003[eE]"), ">")
        .replace(Regex("\\\\u0026"), "&")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .trim()
}

private fun extractHtmlDocumentSegmentForAutoFix(raw: String): String {
    val source = raw.trim()
    if (source.isBlank()) return ""
    val lower = source.lowercase()
    val doctypeIndex = lower.indexOf("<!doctype html")
    val htmlIndex = lower.indexOf("<html")
    val start =
        when {
            doctypeIndex >= 0 -> doctypeIndex
            htmlIndex >= 0 -> htmlIndex
            else -> -1
        }
    if (start < 0) return source
    val closingTag = "</html>"
    val end = lower.lastIndexOf(closingTag)
    if (end > start) {
        return source.substring(start, end + closingTag.length).trim()
    }
    return source.substring(start).trim()
}

