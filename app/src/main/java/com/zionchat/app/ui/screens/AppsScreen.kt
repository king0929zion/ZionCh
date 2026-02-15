package com.zionchat.app.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private data class AppItemUi(
    val name: String,
    val subtitle: String,
    val icon: AppIconStyle,
    val selected: Boolean = false
)

private enum class AppIconStyle {
    Airtable,
    AppleMusic,
    Booking,
    Canva,
    Figma,
    Lovable,
    OpenTable
}

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
                override fun onReceive(context: android.content.Context?, intent: Intent?) {
                    if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                        runtimeShellInstalled = RuntimeShellPlugin.isInstalled(appContext)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, intentFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
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

    Scaffold(
        containerColor = Background,
        topBar = {
            AppsTopBar(
                onBack = { navController.popBackStack() },
                onAdd = {
                    if (!runtimeShellInstalled) {
                        notifyRuntimeShellRequired()
                        openRuntimeShellDownload()
                        return@AppsTopBar
                    }
                    navController.navigate("chat")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "runtime_shell_required_section") {
                    Text(
                        text = stringResource(R.string.runtime_shell_required_section),
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                    )
                }

                item(key = "runtime_shell_required") {
                    RuntimeShellRequiredCard(
                        templateLabel = "${RuntimeShellPlugin.packageName()} / ${RuntimeShellPlugin.templateFileName()}",
                        isInstalled = runtimeShellInstalled,
                        onDownload = { openRuntimeShellDownload() },
                        onRefresh = { runtimeShellInstalled = RuntimeShellPlugin.isInstalled(context) }
                    )
                }

                item(key = "saved_title") {
                    Text(
                        text = stringResource(R.string.apps_my_apps),
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                    )
                }

                if (savedApps.isEmpty()) {
                    item(key = "saved_empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceColor
                        ) {
                            Text(
                                text = stringResource(R.string.apps_empty_saved),
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                            )
                        }
                    }
                } else {
                    items(items = savedApps, key = { it.id }) { app ->
                        SavedAppRow(
                            app = app,
                            onClick = { selectedSavedApp = app }
                        )
                    }
                }
            }
        }
    }

    selectedSavedApp?.let { app ->
        SavedAppPreviewDialog(
            app = app,
            onDismiss = { selectedSavedApp = null },
            onRequestEdit = { request ->
                if (!runtimeShellInstalled) {
                    notifyRuntimeShellRequired()
                    openRuntimeShellDownload()
                    return@SavedAppPreviewDialog
                }
                repository.enqueueAppAutomationTask(
                    AppAutomationTask(
                        mode = "edit",
                        appId = app.id,
                        appName = app.name,
                        appHtml = app.html,
                        request = request
                    )
                )
                selectedSavedApp = null
                navController.navigate("chat")
            },
            onRequestAutoFix = { issue ->
                if (!runtimeShellInstalled) {
                    notifyRuntimeShellRequired()
                    openRuntimeShellDownload()
                    return@SavedAppPreviewDialog
                }
                repository.enqueueAppAutomationTask(
                    AppAutomationTask(
                        mode = "debug_fix",
                        appId = app.id,
                        appName = app.name,
                        appHtml = app.html,
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
                    return@SavedAppPreviewDialog
                }
                if (!webHostingConfig.autoDeploy || webHostingConfig.token.isBlank()) {
                    return@SavedAppPreviewDialog
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
            versionCount = savedAppVersions.count { version -> version.appId == app.id },
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
    }
}

@Composable
private fun RuntimeShellRequiredCard(
    templateLabel: String,
    isInstalled: Boolean,
    onDownload: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
private fun SavedAppRow(
    app: SavedApp,
    onClick: () -> Unit
) {
    val runtimeStatusText = runtimeBuildStatusText(app.runtimeBuildStatus, app.runtimeBuildError)
    val runtimeStatusColor = runtimeBuildStatusColor(app.runtimeBuildStatus)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedBackground by animateColorAsState(
        targetValue = if (isPressed) Color(0xFFF1F1F4) else Color.White,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "saved_app_pressed_bg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(pressedBackground, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AppDevRingGlyph(modifier = Modifier.size(30.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(app.description.ifBlank { stringResource(R.string.apps_saved_from_chat) })
                    append(" • ")
                    append(app.versionName.ifBlank { "v${app.versionCode}" })
                },
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!runtimeStatusText.isNullOrBlank()) {
                Text(
                    text = runtimeStatusText,
                    fontSize = 12.sp,
                    color = runtimeStatusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
private fun AppDevRingGlyph(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.24f
        val arcDiameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            x = (size.width - arcDiameter) / 2f,
            y = (size.height - arcDiameter) / 2f
        )
        drawArc(
            color = Color.Black,
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
private fun SavedAppPreviewDialog(
    app: SavedApp,
    onDismiss: () -> Unit,
    onRequestEdit: (String) -> Unit,
    onRequestAutoFix: (String) -> Unit,
    onRedeploy: (SavedApp) -> Unit,
    versionCount: Int,
    onRestorePreviousVersion: (SavedApp) -> Unit
) {
    var chromeColor by remember(app.id, app.html) {
        mutableStateOf(inferAppChromeColorFromHtml(app.html) ?: Color.White)
    }
    var showEditDialog by remember(app.id) { mutableStateOf(false) }
    var editRequestText by remember(app.id) { mutableStateOf("") }
    var debugIssue by remember(app.id) { mutableStateOf<String?>(null) }
    var issueFingerprints by remember(app.id) { mutableStateOf(setOf<String>()) }
    val baseUrl = remember(app.id) { "https://saved-app.zionchat.local/app/${app.id}/" }
    val deployUrl = remember(app.deployUrl) { app.deployUrl?.trim()?.takeIf { it.isNotBlank() } }
    val contentSignature = remember(app.id, app.html, deployUrl) { "${app.id}:${app.html.hashCode()}:${deployUrl.orEmpty()}" }
    val controlsBackground =
        if (chromeColor.luminance() < 0.45f) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f)
    val controlsTint =
        if (chromeColor.luminance() < 0.45f) Color.White else TextPrimary
    val runtimeStatusText = runtimeBuildStatusText(app.runtimeBuildStatus, app.runtimeBuildError)
    val runtimeStatusColor = runtimeBuildStatusColor(app.runtimeBuildStatus)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chromeColor)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
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
                                    view?.evaluateJavascript(
                                        "(function(){try{var b=document.body||document.documentElement;if(!b)return '';var c=window.getComputedStyle(b).backgroundColor||'';if(!c||c==='transparent'||c==='rgba(0, 0, 0, 0)'){var r=document.documentElement?window.getComputedStyle(document.documentElement).backgroundColor:'';if(r)c=r;}return c||'';}catch(e){return '';}})();"
                                    ) { jsResult ->
                                        parseCssColorFromJs(jsResult)?.let { parsed ->
                                            chromeColor = parsed
                                        }
                                    }
                                    view?.evaluateJavascript(
                                        "(function(){try{if(window.__zionDebugHookInstalled){return 'ok';}window.__zionDebugHookInstalled=true;window.addEventListener('error',function(e){try{var msg=(e&&e.message)?String(e.message):'Unknown runtime error';var src=(e&&e.filename)?String(e.filename):'';var ln=(e&&e.lineno)?String(e.lineno):'0';console.error('ZION_APP_RUNTIME_ERROR:'+msg+' @'+src+':'+ln);}catch(_){}});window.addEventListener('unhandledrejection',function(e){try{var reason='';try{reason=String(e.reason);}catch(_){reason='[unknown]';}console.error('ZION_APP_RUNTIME_ERROR:UnhandledPromiseRejection '+reason);}catch(_){}});return 'ok';}catch(err){return 'err';}})();",
                                        null
                                    )
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
                                        val level = consoleMessage?.messageLevel()
                                        val marker = "ZION_APP_RUNTIME_ERROR:"
                                        when {
                                            msg.contains(marker, ignoreCase = true) ->
                                                reportIssue.value(msg.substringAfter(marker).trim())
                                            level == ConsoleMessage.MessageLevel.ERROR ->
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

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(controlsBackground, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = null,
                        tint = controlsTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(controlsBackground, CircleShape)
                            .pressableScale(pressedScale = 0.95f, onClick = { onRedeploy(app) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = null,
                            tint = controlsTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(controlsBackground, CircleShape)
                            .pressableScale(pressedScale = 0.95f, onClick = { showEditDialog = true }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Edit,
                            contentDescription = null,
                            tint = controlsTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 54.dp)
                    .background(controlsBackground, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${app.versionName.ifBlank { "v${app.versionCode}" }} • $versionCount versions",
                        color = controlsTint,
                        fontSize = 12.sp
                    )
                    if (!runtimeStatusText.isNullOrBlank()) {
                        Text(
                            text = runtimeStatusText,
                            color = if (runtimeStatusColor == TextSecondary) controlsTint else runtimeStatusColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (versionCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 54.dp, end = 12.dp)
                        .background(controlsBackground, RoundedCornerShape(12.dp))
                        .pressableScale(pressedScale = 0.98f, onClick = { onRestorePreviousVersion(app) })
                        .padding(horizontal = 10.dp, vertical = 6.dp)
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

private fun runtimeBuildStatusText(status: String?, errorText: String?): String? {
    return when (status?.trim()?.lowercase()) {
        "queued" -> "APK packaging queued"
        "in_progress" -> "APK packaging in progress"
        "success" -> "APK ready"
        "failed" -> errorText?.trim()?.takeIf { it.isNotBlank() } ?: "APK packaging failed"
        "disabled" -> errorText?.trim()?.takeIf { it.isNotBlank() } ?: "Runtime shell template is required"
        "skipped" -> errorText?.trim()?.takeIf { it.isNotBlank() } ?: "APK packaging skipped"
        else -> null
    }
}

private fun runtimeBuildStatusColor(status: String?): Color {
    return when (status?.trim()?.lowercase()) {
        "success" -> Color(0xFF34C759)
        "failed" -> Color(0xFFFF3B30)
        "queued", "in_progress" -> Color(0xFF007AFF)
        else -> TextSecondary
    }
}

private fun inferAppChromeColorFromHtml(html: String): Color? {
    val source = html.trim()
    if (source.isBlank()) return null
    val patterns =
        listOf(
            Regex("<meta\\s+name\\s*=\\s*[\"']theme-color[\"']\\s+content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
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
            ?: return null
    val r = rgba[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
    val g = rgba[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
    val b = rgba[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
    val a =
        rgba.getOrNull(4)?.takeIf { it.isNotBlank() }?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
    return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a)
}

@Composable
private fun AppItemRow(
    item: AppItemUi,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedBackground by animateColorAsState(
        targetValue = if (isPressed) Color(0xFFF1F1F4) else Color.Transparent,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "app_item_pressed_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressedBackground, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(style = item.icon, selected = item.selected)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = TextPrimary
            )
            Text(
                text = item.subtitle,
                fontSize = 14.sp,
                color = TextSecondary
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
private fun AppIcon(
    style: AppIconStyle,
    selected: Boolean
) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            AppIconStyle.Airtable -> {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "A", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            AppIconStyle.AppleMusic -> {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(listOf(Color(0xFFFA57C1), Color(0xFFF44336))),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "♪", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            AppIconStyle.Booking -> {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF003B95), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "B.", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            AppIconStyle.Canva -> {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(listOf(Color(0xFF00C4CC), Color(0xFF7B2CBF))),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "C", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            AppIconStyle.Figma -> {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        .border(width = 1.dp, color = Color(0xFFE5E5EA), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(28.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopStart)
                                .clip(CircleShape)
                                .background(Color(0xFFF24E1E), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(Color(0xFFFF7262), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.BottomStart)
                                .clip(CircleShape)
                                .background(Color(0xFFA259FF), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.CenterEnd)
                                .clip(CircleShape)
                                .background(Color(0xFF1ABCFE), CircleShape)
                        )
                    }
                }
            }
            AppIconStyle.Lovable -> {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFE66D))),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u2665", fontSize = 20.sp, color = Color.White)
                }
            }
            AppIconStyle.OpenTable -> {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDA3743), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(20.dp)) {
                        Dot(Modifier.align(Alignment.Center), 6.dp)
                        Dot(Modifier.align(Alignment.TopCenter), 4.dp)
                        Dot(Modifier.align(Alignment.BottomCenter), 4.dp)
                        Dot(Modifier.align(Alignment.CenterStart), 4.dp)
                        Dot(Modifier.align(Alignment.CenterEnd), 4.dp)
                    }
                }
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black, CircleShape)
                    .border(width = 2.dp, color = Color.White, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun Dot(modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White, CircleShape)
    )
}
