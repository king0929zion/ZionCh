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
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalRuntimePackagingService
import com.zionchat.app.LocalWebHostingService
import com.zionchat.app.R
import com.zionchat.app.data.AppAutomationTask
import com.zionchat.app.data.RuntimeShellPlugin
import com.zionchat.app.data.SavedApp
import com.zionchat.app.data.WebHostingConfig
import com.zionchat.app.ui.components.BottomFadeScrim
import com.zionchat.app.ui.components.TopFadeScrim
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import com.zionchat.app.ui.theme.Surface as SurfaceColor
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val APP_CHROME_COLOR_JS =
    "(function(){try{var meta=document.querySelector('meta[name=\\\"theme-color\\\"]');if(meta&&meta.content){return meta.content;}var body=document.body?window.getComputedStyle(document.body):null;if(body){var bodyBg=body.backgroundColor||'';if(bodyBg&&bodyBg!=='transparent'&&bodyBg!=='rgba(0, 0, 0, 0)'){return bodyBg;}}var root=document.documentElement?window.getComputedStyle(document.documentElement):null;if(root){var rootBg=root.backgroundColor||'';if(rootBg&&rootBg!=='transparent'&&rootBg!=='rgba(0, 0, 0, 0)'){return rootBg;}}return '';}catch(e){return '';}})();"

private const val APP_RUNTIME_DEBUG_HOOK_JS =
    "(function(){try{if(window.__zionDebugHookInstalled){return 'ok';}window.__zionDebugHookInstalled=true;window.addEventListener('error',function(e){try{var msg=(e&&e.message)?String(e.message):'Unknown runtime error';var src=(e&&e.filename)?String(e.filename):'';var ln=(e&&e.lineno)?String(e.lineno):'0';console.error('ZION_APP_RUNTIME_ERROR:'+msg+' @'+src+':'+ln);}catch(_){}});window.addEventListener('unhandledrejection',function(e){try{var reason='';try{reason=String(e.reason);}catch(_){reason='[unknown]';}console.error('ZION_APP_RUNTIME_ERROR:UnhandledPromiseRejection '+reason);}catch(_){}});return 'ok';}catch(err){return 'err';}})();"

@Composable
fun AppsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = LocalAppRepository.current
    val runtimePackagingService = LocalRuntimePackagingService.current
    val webHostingService = LocalWebHostingService.current
    val scope = rememberCoroutineScope()
    val savedApps by repository.savedAppsFlow.collectAsState(initial = emptyList())
    val savedAppVersions by repository.savedAppVersionsFlow.collectAsState(initial = emptyList())
    val webHostingConfig by repository.webHostingConfigFlow.collectAsState(initial = WebHostingConfig())
    val appVersionModel by repository.appModuleVersionModelFlow.collectAsState(initial = 1)
    var runtimeShellInstalled by remember { mutableStateOf(RuntimeShellPlugin.isInstalled(context)) }
    var selectedSavedApp by remember { mutableStateOf<SavedApp?>(null) }

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
        SavedAppPreviewPage(
            app = activePreviewApp,
            canRedeploy = runtimeShellInstalled && webHostingConfig.autoDeploy && webHostingConfig.token.isNotBlank(),
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
                repository.enqueueAppAutomationTask(
                    AppAutomationTask(
                        mode = "debug_fix",
                        appId = activePreviewApp.id,
                        appName = activePreviewApp.name,
                        appHtml = activePreviewApp.html,
                        request = issue
                    )
                )
                selectedSavedApp = null
                navController.navigate("chat")
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

    Scaffold(
        containerColor = Background,
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
                .background(Background)
                .padding(padding)
        ) {
            if (!runtimeShellInstalled) {
                Text(
                    text = stringResource(R.string.runtime_shell_required_section),
                    fontSize = 13.sp,
                    fontFamily = SourceSans3,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 8.dp)
                )
                RuntimeShellRequiredCard(
                    templateLabel = "${RuntimeShellPlugin.packageName()} / ${RuntimeShellPlugin.templateFileName()}",
                    isInstalled = runtimeShellInstalled,
                    onDownload = { openRuntimeShellDownload() },
                    onRefresh = { runtimeShellInstalled = RuntimeShellPlugin.isInstalled(context) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                RuntimeShellReadyBadge(
                    templateLabel = "${RuntimeShellPlugin.packageName()} / ${RuntimeShellPlugin.templateFileName()}"
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = SurfaceColor,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
            ) {
                if (savedApps.isEmpty()) {
                    EmptyDesktopState(onCreate = { navController.navigate("chat") })
                } else {
                    val desktopEntries = remember(savedApps) { listOf<SavedApp?>(null) + savedApps }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(
                            items = desktopEntries,
                            key = { app -> app?.id ?: "desktop_create_tile" }
                        ) { app ->
                            if (app == null) {
                                CreateAppDesktopTile(onClick = { navController.navigate("chat") })
                            } else {
                                SavedAppDesktopTile(
                                    app = app,
                                    onClick = { selectedSavedApp = app }
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
            .background(Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceColor, CircleShape)
                .pressableScale(pressedScale = 0.95f, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Back,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
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
                color = TextPrimary
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceColor, CircleShape)
                .pressableScale(pressedScale = 0.95f, onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Plus,
                contentDescription = "Add",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
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
private fun SavedAppDesktopTile(
    app: SavedApp,
    onClick: () -> Unit
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
            .pressableScale(pressedScale = 0.96f, onClick = onClick)
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
    val controlsBackground =
        if (chromeColor.luminance() < 0.46f) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.10f)
    val controlsBorder =
        if (chromeColor.luminance() < 0.46f) Color.White.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.14f)
    val controlsTint =
        if (chromeColor.luminance() < 0.46f) Color.White else TextPrimary
    val reportIssue = rememberUpdatedState<(String) -> Unit> { raw ->
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
    PreviewSystemBarsEffect(color = chromeColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chromeColor)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(APP_CHROME_COLOR_JS) { jsResult ->
                                    parseCssColorFromJs(jsResult)?.let { parsed ->
                                        chromeColor = parsed
                                    }
                                }
                                view?.evaluateJavascript(APP_RUNTIME_DEBUG_HOOK_JS, null)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame != false) {
                                    val detail =
                                        buildString {
                                            append("Load error")
                                            val desc = error?.description?.toString()?.trim().orEmpty()
                                            if (desc.isNotBlank()) {
                                                append(": ")
                                                append(desc)
                                            }
                                        }
                                    reportIssue.value(detail)
                                }
                            }

                            @Suppress("DEPRECATION")
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                val detail = "Load error: ${description?.trim().orEmpty().ifBlank { "Unknown" }}"
                                reportIssue.value(detail)
                            }
                        }
                    webChromeClient =
                        object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                val msg = consoleMessage?.message()?.trim().orEmpty()
                                if (msg.isNotBlank()) {
                                    val marker = "ZION_APP_RUNTIME_ERROR:"
                                    when {
                                        msg.contains(marker, ignoreCase = true) ->
                                            reportIssue.value(msg.substringAfter(marker).trim())
                                        consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR ->
                                            reportIssue.value("Console error: $msg")
                                    }
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }
                }
            },
            update = { webView ->
                if (webView.tag != contentSignature) {
                    webView.tag = contentSignature
                    if (!deployUrl.isNullOrBlank()) {
                        webView.loadUrl(deployUrl)
                    } else {
                        webView.loadDataWithBaseURL(
                            baseUrl,
                            app.html,
                            "text/html",
                            "utf-8",
                            null
                        )
                    }
                }
            }
        )

        TopFadeScrim(
            color = chromeColor,
            height = 132.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        BottomFadeScrim(
            color = chromeColor,
            height = 124.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            AppPreviewTopChrome(
                appName = app.name,
                controlsBackground = controlsBackground,
                controlsBorder = controlsBorder,
                controlsTint = controlsTint,
                canRedeploy = canRedeploy,
                onDismiss = onDismiss,
                onRedeploy = { onRedeploy(app) },
                onEdit = { showEditDialog = true }
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            AppPreviewBottomChrome(
                versionName = app.versionName.ifBlank { "v${app.versionCode}" },
                versionCount = versionCount,
                controlsBackground = controlsBackground,
                controlsBorder = controlsBorder,
                controlsTint = controlsTint,
                canRestore = versionCount > 1,
                onRestore = { onRestorePreviousVersion(app) }
            )
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
            onDismissRequest = { debugIssue = null },
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
                        debugIssue = null
                        onRequestAutoFix(issue)
                    }
                ) {
                    Text("Fix now")
                }
            },
            dismissButton = {
                TextButton(onClick = { debugIssue = null }) {
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
private fun PreviewSystemBarsEffect(color: Color) {
    val view = LocalView.current
    val activity = remember(view.context) { view.context.findActivity() }

    DisposableEffect(activity, color) {
        val hostActivity = activity
        if (hostActivity == null) {
            onDispose {}
        } else {
            val window = hostActivity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatus = controller.isAppearanceLightStatusBars
            val previousLightNavigation = controller.isAppearanceLightNavigationBars

            val targetColor = color.toArgb()
            window.statusBarColor = targetColor
            window.navigationBarColor = targetColor
            val useDarkIcons = color.luminance() > 0.58f
            controller.isAppearanceLightStatusBars = useDarkIcons
            controller.isAppearanceLightNavigationBars = useDarkIcons

            onDispose {
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
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

