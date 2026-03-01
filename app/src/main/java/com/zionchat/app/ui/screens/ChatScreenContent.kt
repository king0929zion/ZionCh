package com.zionchat.app.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.navigation.NavController
import com.zionchat.app.R
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalChatApiClient
import com.zionchat.app.LocalProviderAuthManager
import com.zionchat.app.LocalRuntimePackagingService
import com.zionchat.app.LocalWebHostingService
import com.zionchat.app.autosoul.runtime.AutoSoulAutomationManager
import com.zionchat.app.autosoul.runtime.AutoSoulScriptParser
import com.zionchat.app.data.AppRepository
import com.zionchat.app.data.AppAutomationTask
import com.zionchat.app.data.ChatApiClient
import com.zionchat.app.data.Conversation
import com.zionchat.app.data.HttpHeader
import com.zionchat.app.data.Message
import com.zionchat.app.data.MessageAttachment
import com.zionchat.app.data.MessageTag
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.McpClient
import com.zionchat.app.data.McpConfig
import com.zionchat.app.data.McpToolCall
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.RuntimeShellPlugin
import com.zionchat.app.data.SavedApp
import com.zionchat.app.data.WebSearchConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.data.isCodexProvider
import com.zionchat.app.data.isGrok2ApiProvider
import com.zionchat.app.data.isLikelyVisionModel
import com.zionchat.app.ui.components.TopFadeScrim
import com.zionchat.app.ui.components.AppSheetDragHandle
import com.zionchat.app.ui.components.AppHtmlWebView
import com.zionchat.app.ui.components.AppHtmlWebViewState
import com.zionchat.app.ui.components.MarkdownText
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import coil3.compose.AsyncImage
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import java.io.StringReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

// 颜色常量 - 完全匹配HTML原型
private data class PendingMessage(val conversationId: String, val message: Message)

// Keep streaming alive even if ChatScreen leaves composition (e.g. app switch/navigation).
private val chatStreamingExecutionScope by lazy {
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
internal data class PendingImageAttachment(val uri: Uri? = null, val bitmap: Bitmap? = null)
internal enum class ToolMenuPage { Tools, McpServers }

private data class AutoBrowserSessionState(
    val conversationId: String,
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val isActive: Boolean = true,
    val currentUrl: String = "about:blank",
    val pageTitle: String = "",
    val renderNonce: Int = 0,
    val lastPageFinishedAt: Long = 0L,
    val snapshotRefs: Map<String, String> = emptyMap(),
    val snapshotText: String = "",
    val history: List<String> = emptyList(),
    val lastActionTitle: String = "AutoBrowser",
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

private data class AutoBrowserFilePickRequest(
    val conversationId: String,
    val mimeType: String,
    val deferred: CompletableDeferred<Uri?>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreenContent(
    navController: NavController,
    forcedGroupId: String? = null
) {
    val repository = LocalAppRepository.current
    val chatApiClient = LocalChatApiClient.current
    val providerAuthManager = LocalProviderAuthManager.current
    val runtimePackagingService = LocalRuntimePackagingService.current
    val webHostingService = LocalWebHostingService.current
    val mcpClient = remember { McpClient() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showToolMenu by remember { mutableStateOf(false) }
    var toolMenuPage by remember { mutableStateOf(ToolMenuPage.Tools) }
    var showChatModelPicker by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var showAutoBrowserPreview by remember { mutableStateOf(false) }
    var selectedMcpServerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mcpToolPickerLoading by remember { mutableStateOf(false) }
    var mcpToolPickerError by remember { mutableStateOf<String?>(null) }
    val autoBrowserSessions = remember { mutableStateMapOf<String, AutoBrowserSessionState>() }
    val autoBrowserWebViewRefs = remember { mutableStateMapOf<String, WebView?>() }
    val autoBrowserWebViewStates = remember { mutableStateMapOf<String, AppHtmlWebViewState>() }
    var autoBrowserFilePickRequest by remember { mutableStateOf<AutoBrowserFilePickRequest?>(null) }
    val chatModelPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var messageText by remember { mutableStateOf("") }
    var imageAttachments by remember { mutableStateOf<List<PendingImageAttachment>>(emptyList()) }
    var inputFieldFocused by remember { mutableStateOf(false) }
    var lastKeyboardHideRequestAtMs by remember { mutableLongStateOf(0L) }
    var showThinkingSheet by remember { mutableStateOf(false) }
    var thinkingSheetText by remember { mutableStateOf<String?>(null) }
    val thinkingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tagSheetMessageId by remember { mutableStateOf<String?>(null) }
    var tagSheetTagId by remember { mutableStateOf<String?>(null) }
    val tagSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var appWorkspaceMessageId by remember { mutableStateOf<String?>(null) }
    var appWorkspaceTagId by remember { mutableStateOf<String?>(null) }
    var appDevDownloadBusyIds by remember { mutableStateOf(setOf<String>()) }
    var pendingAppBuilderConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = !appWorkspaceTagId.isNullOrBlank()) {
        appWorkspaceMessageId = null
        appWorkspaceTagId = null
    }
    BackHandler(enabled = showToolMenu) {
        if (toolMenuPage == ToolMenuPage.McpServers) {
            toolMenuPage = ToolMenuPage.Tools
        } else {
            showToolMenu = false
        }
    }
    BackHandler(enabled = showChatModelPicker) {
        showChatModelPicker = false
    }
    BackHandler(enabled = showAutoBrowserPreview) {
        showAutoBrowserPreview = false
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                imageAttachments = imageAttachments + PendingImageAttachment(uri = uri)
            }
        }

    val autoBrowserFilePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            autoBrowserFilePickRequest?.let { request ->
                if (!request.deferred.isCompleted) {
                    request.deferred.complete(uri)
                }
            }
            autoBrowserFilePickRequest = null
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                imageAttachments = imageAttachments + PendingImageAttachment(bitmap = bitmap)
            }
        }

    val conversations by repository.conversationsFlow.collectAsState(initial = emptyList())
    val groupChats by repository.groupChatsFlow.collectAsState(initial = emptyList())
    val currentConversationId by repository.currentConversationIdFlow.collectAsState(initial = null)
    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val nickname by repository.nicknameFlow.collectAsState(initial = "")
    val avatarUri by repository.avatarUriFlow.collectAsState(initial = "")
    val customInstructions by repository.customInstructionsFlow.collectAsState(initial = "")
    val defaultChatModelId by repository.defaultChatModelIdFlow.collectAsState(initial = null)
    val chatThinkingEnabled by repository.chatThinkingEnabledFlow.collectAsState(initial = true)
    val defaultImageModelId by repository.defaultImageModelIdFlow.collectAsState(initial = null)
    val defaultAppBuilderModelId by repository.defaultAppBuilderModelIdFlow.collectAsState(initial = null)
    val webSearchConfig by repository.webSearchConfigFlow.collectAsState(initial = WebSearchConfig())
    val mcpList by repository.mcpListFlow.collectAsState(initial = emptyList())
    val pendingAppAutomationTask by repository.pendingAppAutomationTaskFlow.collectAsState(initial = null)
    val appAccentColor by repository.appAccentColorFlow.collectAsState(initial = "default")
    val accentPalette = remember(appAccentColor) { accentPaletteForKey(appAccentColor) }
    val enabledMcpServers = remember(mcpList) { mcpList.filter { it.enabled } }
    val mcpServerPickerItems = remember(enabledMcpServers) { buildMcpServerPickerItems(enabledMcpServers) }
    val chatModelGroups = remember(providers, models) { groupEnabledModelsByProvider(providers, models) }
    var lastAutoDispatchedTaskId by remember { mutableStateOf<String?>(null) }

    // Avoid IME restore loops on cold start/resume.
    LaunchedEffect(Unit) {
        delay(80)
        focusManager.clearFocus(force = true)
    }

    // 本地优先的会话选择：避免 DataStore 状态滞后导致“首条消息消失/会话跳回”
    var preferredConversationId by remember { mutableStateOf<String?>(null) }
    var preferredConversationSetAtMs by remember { mutableStateOf(0L) }

    val forcedGroupIdKey = forcedGroupId?.trim().orEmpty().takeIf { it.isNotBlank() }
    val forcedGroupConfig = remember(groupChats, forcedGroupIdKey) {
        val key = forcedGroupIdKey ?: return@remember null
        groupChats.firstOrNull { it.id == key }
    }

    LaunchedEffect(forcedGroupConfig?.id, forcedGroupConfig?.conversationId) {
        val targetConversationId = forcedGroupConfig?.conversationId?.trim().orEmpty()
        if (targetConversationId.isBlank()) return@LaunchedEffect
        if (currentConversationId != targetConversationId) {
            repository.setCurrentConversationId(targetConversationId)
        }
        preferredConversationId = targetConversationId
        preferredConversationSetAtMs = System.currentTimeMillis()
    }

    LaunchedEffect(currentConversationId) {
        if (preferredConversationId.isNullOrBlank() && !currentConversationId.isNullOrBlank()) {
            preferredConversationId = currentConversationId
            preferredConversationSetAtMs = System.currentTimeMillis()
        }
    }

    val effectiveConversationId = remember(
        conversations,
        currentConversationId,
        preferredConversationId,
        preferredConversationSetAtMs,
        forcedGroupConfig?.conversationId
    ) {
        val forcedConversationId = forcedGroupConfig?.conversationId?.trim().orEmpty().takeIf { !it.isNullOrBlank() }
        if (!forcedConversationId.isNullOrBlank()) {
            return@remember forcedConversationId
        }
        val preferred = preferredConversationId?.trim().takeIf { !it.isNullOrBlank() }
        val fromStore = currentConversationId?.trim().takeIf { !it.isNullOrBlank() }
        if (preferred == null) {
            fromStore ?: conversations.firstOrNull()?.id
        } else {
            val inList = conversations.any { it.id == preferred }
            val withinGrace = System.currentTimeMillis() - preferredConversationSetAtMs < 2500
            when {
                inList || withinGrace -> preferred
                !fromStore.isNullOrBlank() -> fromStore
                else -> conversations.firstOrNull()?.id
            }
        }
    }

    val currentConversation = remember(conversations, effectiveConversationId) {
        val cid = effectiveConversationId?.trim().orEmpty()
        if (cid.isBlank()) null else conversations.firstOrNull { it.id == cid }
    }
    val activeGroupChatConfig = remember(groupChats, effectiveConversationId) {
        val cid = effectiveConversationId?.trim().orEmpty()
        if (cid.isBlank()) null else groupChats.firstOrNull { it.conversationId == cid }
    }
    val groupChatPageMode = !forcedGroupIdKey.isNullOrBlank()
    val bots by repository.botsFlow.collectAsState(initial = emptyList())
    val mentionCandidates = remember(activeGroupChatConfig, bots) {
        val memberBotIds = activeGroupChatConfig?.memberBotIds.orEmpty().toSet()
        bots
            .asSequence()
            .filter { memberBotIds.contains(it.id) }
            .map { bot ->
                MentionCandidate(
                    key = normalizeMentionLookup(bot.name),
                    label = bot.name,
                    token = "@${buildBotMentionToken(bot)}"
                )
            }
            .toList()
    }
    val mentionQuery = remember(activeGroupChatConfig?.id, messageText) {
        if (activeGroupChatConfig == null) null else extractTrailingMentionQuery(messageText)
    }
    val visibleMentionCandidates = remember(mentionQuery, mentionCandidates) {
        val query = mentionQuery?.trim().orEmpty()
        if (query.isBlank()) {
            mentionCandidates.take(6)
        } else {
            val key = normalizeMentionLookup(query)
            mentionCandidates
                .filter { candidate ->
                    normalizeMentionLookup(candidate.label).contains(key) ||
                        normalizeMentionLookup(candidate.token).contains(key)
                }
                .take(6)
        }
    }
    val showMentionPicker = activeGroupChatConfig != null && mentionQuery != null && visibleMentionCandidates.isNotEmpty()
    val activeAutoBrowserSession = effectiveConversationId?.let { cid -> autoBrowserSessions[cid] }
    val hasActiveAutoBrowserSession = activeAutoBrowserSession?.isActive == true

    fun normalizeAutoBrowserUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        return if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            value
        } else {
            "https://$value"
        }
    }

    fun updateAutoBrowserSession(
        conversationId: String,
        transform: (AutoBrowserSessionState?) -> AutoBrowserSessionState?
    ) {
        val key = conversationId.trim()
        if (key.isBlank()) return
        val current = autoBrowserSessions[key]
        val next = transform(current)
        if (next == null) {
            autoBrowserSessions.remove(key)
            autoBrowserWebViewRefs.remove(key)
            autoBrowserWebViewStates.remove(key)
        } else {
            autoBrowserSessions[key] = next.copy(conversationId = key, updatedAt = System.currentTimeMillis())
        }
    }

    fun appendAutoBrowserHistory(conversationId: String, line: String) {
        val content = line.trim()
        if (content.isBlank()) return
        updateAutoBrowserSession(conversationId) { current ->
            val session = current ?: return@updateAutoBrowserSession null
            session.copy(history = (session.history + content).takeLast(60))
        }
    }

    fun decodeJavascriptResult(raw: String): String {
        val text = raw.trim()
        if (text.isBlank() || text == "null") return ""
        val parsed = runCatching { JsonParser.parseString(text) }.getOrNull()
        if (parsed != null && parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString) {
            return parsed.asString
        }
        return text
    }

    suspend fun awaitAutoBrowserWebView(
        conversationId: String,
        timeoutMs: Long = 12_000L
    ): WebView? {
        val key = conversationId.trim()
        if (key.isBlank()) return null
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val webView = autoBrowserWebViewRefs[key]
                if (webView != null) return@withTimeoutOrNull webView
                delay(40)
            }
            null
        }
    }

    suspend fun evaluateAutoBrowserScript(
        conversationId: String,
        script: String,
        timeoutMs: Long = 18_000L
    ): String {
        val webView = awaitAutoBrowserWebView(conversationId, timeoutMs = timeoutMs)
            ?: return "{\"ok\":false,\"error\":\"webview_unavailable\"}"
        return withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.suspendCancellableCoroutine<String> { continuation ->
                webView.post {
                    runCatching {
                        webView.evaluateJavascript(script) { raw ->
                            if (!continuation.isActive) return@evaluateJavascript
                            continuation.resume(raw.orEmpty())
                        }
                    }.onFailure { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
            }
        } ?: "{\"ok\":false,\"error\":\"timeout\"}"
    }

    fun readDisplayName(uri: Uri): String {
        val fallback = "upload.bin"
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    cursor.getString(idx)?.trim()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull() ?: fallback
    }

    suspend fun pickAutoBrowserFile(
        conversationId: String,
        mimeType: String
    ): Uri? {
        val deferred = CompletableDeferred<Uri?>()
        autoBrowserFilePickRequest = AutoBrowserFilePickRequest(conversationId = conversationId, mimeType = mimeType, deferred = deferred)
        autoBrowserFilePickerLauncher.launch(mimeType)
        val result = withTimeoutOrNull(120_000L) { deferred.await() }
        if (autoBrowserFilePickRequest?.deferred == deferred) {
            autoBrowserFilePickRequest = null
        }
        return result
    }

    suspend fun captureAutoBrowserSnapshot(conversationId: String): Pair<String, Map<String, String>> {
        val script =
            """
            (function(){
              function cssPath(el){
                if(!(el instanceof Element)) return '';
                var path=[];
                while(el && el.nodeType===1 && path.length<8){
                  var selector=el.nodeName.toLowerCase();
                  if(el.id){ selector += '#' + el.id.replace(/([^a-zA-Z0-9_-])/g,'\\$1'); path.unshift(selector); break; }
                  var sib=el, nth=1;
                  while((sib=sib.previousElementSibling)!=null){ if(sib.nodeName.toLowerCase()===selector) nth++; }
                  selector += ':nth-of-type(' + nth + ')';
                  path.unshift(selector);
                  el = el.parentElement;
                }
                return path.join(' > ');
              }

              function isVisible(el){
                if(!(el instanceof Element)) return false;
                var r=el.getBoundingClientRect();
                if(!r || r.width<1 || r.height<1) return false;
                var style=window.getComputedStyle(el);
                if(!style) return true;
                if(style.display==='none' || style.visibility==='hidden') return false;
                if(parseFloat(style.opacity||'1')<=0) return false;
                return true;
              }

              function inferRole(el){
                var role=(el.getAttribute('role')||'').trim().toLowerCase();
                if(role) return role;
                var tag=(el.tagName||'').toLowerCase();
                if(tag==='a' && el.getAttribute('href')) return 'link';
                if(tag==='button') return 'button';
                if(tag==='input'){
                  var type=(el.getAttribute('type')||'text').toLowerCase();
                  if(type==='button' || type==='submit' || type==='reset') return 'button';
                  if(type==='checkbox') return 'checkbox';
                  if(type==='radio') return 'radio';
                  return 'textbox';
                }
                if(tag==='textarea') return 'textbox';
                if(tag==='select') return 'combobox';
                if(/^h[1-6]$/.test(tag)) return 'heading';
                if(tag==='li') return 'listitem';
                if(tag==='nav') return 'navigation';
                if(tag==='main') return 'main';
                if(tag==='section') return 'region';
                return tag || 'node';
              }

              function inferName(el){
                var text=(
                  el.getAttribute('aria-label') ||
                  el.getAttribute('title') ||
                  el.innerText ||
                  el.textContent ||
                  el.value ||
                  el.getAttribute('placeholder') ||
                  el.getAttribute('name') ||
                  el.id ||
                  ''
                ).replace(/\\s+/g,' ').trim();
                return text.slice(0,80);
              }

              function depth(el){
                var d=0, cur=el;
                while(cur && cur!==document.body && d<8){ d++; cur=cur.parentElement; }
                return d;
              }

              var pool=[];
              function addAll(nodeList){
                for(var i=0;i<nodeList.length;i++){
                  var el=nodeList[i];
                  if(pool.indexOf(el)===-1) pool.push(el);
                }
              }

              addAll(document.querySelectorAll('button,a[href],input,textarea,select,[role],h1,h2,h3,h4,h5,h6,main,nav,section,li'));
              if(document.activeElement && pool.indexOf(document.activeElement)===-1){
                pool.unshift(document.activeElement);
              }

              var nodes=[];
              var i=1;
              for(var n=0;n<pool.length && nodes.length<32;n++){
                var el=pool[n];
                if(!isVisible(el)) continue;
                var selector=cssPath(el);
                if(!selector) continue;
                var role=inferRole(el);
                var name=inferName(el);
                if(!name && (role==='region' || role==='node' || role==='section')) continue;
                nodes.push({
                  ref:'e'+(i++),
                  selector:selector,
                  role:role,
                  name:name,
                  depth:depth(el)
                });
              }

              return JSON.stringify({
                ok:true,
                url:location.href||'',
                title:document.title||'',
                nodes:nodes
              });
            })();
            """.trimIndent()
        val raw = evaluateAutoBrowserScript(conversationId, script, timeoutMs = 22_000L)
        val decoded = decodeJavascriptResult(raw)
        val obj = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
            ?: return "Snapshot failed: invalid JSON payload." to emptyMap()
        if (obj.get("ok")?.asBoolean == false) {
            val error = obj.get("error")?.asString?.trim().orEmpty().ifBlank { "unknown_error" }
            return "Snapshot failed: $error" to emptyMap()
        }
        val url = obj.get("url")?.asString?.trim().orEmpty()
        val title = obj.get("title")?.asString?.trim().orEmpty()
        val refsArray = obj.getAsJsonArray("nodes")
        val refs = linkedMapOf<String, String>()
        val treeLines = mutableListOf<String>()
        refsArray?.forEach { element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
            val ref = item.get("ref")?.asString?.trim().orEmpty()
            val selector = item.get("selector")?.asString?.trim().orEmpty()
            if (ref.isBlank() || selector.isBlank()) return@forEach
            val role = item.get("role")?.asString?.trim().orEmpty()
            val label = item.get("name")?.asString?.trim().orEmpty()
            val depth = runCatching { item.get("depth")?.asInt ?: 0 }.getOrDefault(0).coerceIn(0, 3)
            refs[ref] = selector
            val line =
                buildString {
                    repeat(depth) { append("  ") }
                    append("@")
                    append(ref)
                    append(" [")
                    append(role.ifBlank { "node" })
                    append("]")
                    if (label.isNotBlank()) {
                        append(" \"")
                        append(label)
                        append('"')
                    }
                }
            treeLines += line.take(120)
        }
        val snapshotText =
            buildString {
                append("URL: ")
                appendLine(url.ifBlank { "(unknown)" })
                if (title.isNotBlank()) {
                    append("Title: ")
                    appendLine(title)
                }
                appendLine()
                append("Accessibility tree refs (")
                append(refs.size)
                appendLine("):")
                treeLines.take(24).forEach { line ->
                    append("- ")
                    appendLine(line)
                }
                appendLine()
                appendLine("Use @eN refs for click_ref/fill steps. Avoid full DOM dumps.")
            }.trim()
        return snapshotText to refs
    }

    suspend fun waitForAutoBrowserLoadAfter(
        conversationId: String,
        startAtMs: Long,
        timeoutMs: Long = 22_000L
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val latest = autoBrowserSessions[conversationId] ?: return@withTimeoutOrNull false
                if (latest.lastPageFinishedAt > startAtMs) break
                delay(80)
            }
            true
        } == true
    }

    suspend fun clickAutoBrowserSelector(
        conversationId: String,
        selector: String
    ): Pair<Boolean, String> {
        val selectorJson = GsonBuilder().create().toJson(selector)
        val clickScript =
            """
            (function(){
              try{
                var el=document.querySelector($selectorJson);
                if(!el){return JSON.stringify({ok:false,error:'element_not_found'});}
                el.scrollIntoView({block:'center',inline:'center'});
                el.click();
                var text=(el.innerText||el.value||el.getAttribute('aria-label')||'').trim().slice(0,120);
                return JSON.stringify({ok:true,text:text});
              }catch(e){
                return JSON.stringify({ok:false,error:String(e)});
              }
            })();
            """.trimIndent()
        val decoded = decodeJavascriptResult(evaluateAutoBrowserScript(conversationId, clickScript))
        val resultObj = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
        val ok = resultObj?.get("ok")?.asBoolean == true
        return if (ok) {
            true to (resultObj.get("text")?.asString?.trim().orEmpty().ifBlank { "clicked" })
        } else {
            false to (resultObj?.get("error")?.asString?.trim().orEmpty().ifBlank { "click_failed" })
        }
    }

    suspend fun fillAutoBrowserSelector(
        conversationId: String,
        selector: String,
        value: String
    ): Pair<Boolean, String> {
        val gson = GsonBuilder().create()
        val selectorJson = gson.toJson(selector)
        val valueJson = gson.toJson(value)
        val script =
            """
            (function(){
              try{
                var el=document.querySelector($selectorJson);
                if(!el){return JSON.stringify({ok:false,error:'element_not_found'});}
                el.focus();
                el.value=$valueJson;
                el.dispatchEvent(new Event('input',{bubbles:true}));
                el.dispatchEvent(new Event('change',{bubbles:true}));
                return JSON.stringify({ok:true});
              }catch(e){
                return JSON.stringify({ok:false,error:String(e)});
              }
            })();
            """.trimIndent()
        val decoded = decodeJavascriptResult(evaluateAutoBrowserScript(conversationId, script))
        val resultObj = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
        val ok = resultObj?.get("ok")?.asBoolean == true
        return if (ok) {
            true to "filled"
        } else {
            false to (resultObj?.get("error")?.asString?.trim().orEmpty().ifBlank { "fill_failed" })
        }
    }

    suspend fun uploadAutoBrowserFileToSelector(
        conversationId: String,
        selector: String,
        mimeType: String
    ): Pair<Boolean, String> {
        val picked = pickAutoBrowserFile(conversationId, mimeType)
        if (picked == null) return false to "file_picker_cancelled"
        val bytes =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(picked)?.use { input -> input.readBytes() }
                }.getOrNull()
            }
        if (bytes == null || bytes.isEmpty()) return false to "file_read_failed"
        if (bytes.size > 1_500_000) return false to "file_too_large"

        val fileName = readDisplayName(picked)
        val resolvedMime =
            context.contentResolver.getType(picked)?.trim().orEmpty().ifBlank { "application/octet-stream" }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val gson = GsonBuilder().create()
        val selectorJson = gson.toJson(selector)
        val fileNameJson = gson.toJson(fileName)
        val mimeJson = gson.toJson(resolvedMime)
        val base64Json = gson.toJson(encoded)
        val script =
            """
            (function(){
              try{
                var input=document.querySelector($selectorJson);
                if(!input){return JSON.stringify({ok:false,error:'element_not_found'});}
                var b64=$base64Json;
                var bin=atob(b64);
                var len=bin.length;
                var bytes=new Uint8Array(len);
                for(var i=0;i<len;i++){bytes[i]=bin.charCodeAt(i);}
                var blob=new Blob([bytes],{type:$mimeJson});
                var file=new File([blob],$fileNameJson,{type:$mimeJson});
                var dt=new DataTransfer();
                dt.items.add(file);
                input.files=dt.files;
                input.dispatchEvent(new Event('input',{bubbles:true}));
                input.dispatchEvent(new Event('change',{bubbles:true}));
                return JSON.stringify({ok:true,name:file.name});
              }catch(e){
                return JSON.stringify({ok:false,error:String(e)});
              }
            })();
            """.trimIndent()
        val decoded = decodeJavascriptResult(evaluateAutoBrowserScript(conversationId, script, timeoutMs = 28_000L))
        val resultObj = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
        val ok = resultObj?.get("ok")?.asBoolean == true
        return if (ok) {
            true to (resultObj.get("name")?.asString?.trim().orEmpty().ifBlank { fileName })
        } else {
            false to (resultObj?.get("error")?.asString?.trim().orEmpty().ifBlank { "upload_failed" })
        }
    }

    LaunchedEffect(effectiveConversationId, hasActiveAutoBrowserSession) {
        if (!hasActiveAutoBrowserSession) {
            showAutoBrowserPreview = false
        }
    }

    LaunchedEffect(conversations, currentConversationId) {
        if (currentConversationId.isNullOrBlank() && conversations.isNotEmpty()) {
            repository.setCurrentConversationId(conversations.first().id)
        }
    }

    val messages = currentConversation?.messages.orEmpty()

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeightDp = with(density) { if (topBarHeightPx == 0) 66.dp else topBarHeightPx.toDp() }
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listTopPadding = statusBarTopPadding + topBarHeightDp + 8.dp
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisibilityThresholdPx = with(density) { 24.dp.roundToPx() }
    val imeVisible = inputFieldFocused && imeBottomPx > imeVisibilityThresholdPx
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSystemPadding =
        if (imeVisible) maxOf(imeBottomPadding, navBottomPadding) else navBottomPadding
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }
    val bottomContentPadding = maxOf(80.dp, bottomBarHeightDp + 12.dp + bottomSystemPadding)
    val inputBottomInset = bottomSystemPadding

    // Pending 消息：在 DataStore 落盘前立即显示，彻底修复“首条消息消失”
    var pendingMessages by remember { mutableStateOf<List<PendingMessage>>(emptyList()) }

    // 流式输出：用同一条 assistant 消息实时更新，避免结束时“闪一下重新渲染”
    var isStreaming by remember { mutableStateOf(false) }
    var sendInFlight by remember { mutableStateOf(false) }
    var streamingMessageId by remember { mutableStateOf<String?>(null) }
    var streamingConversationId by remember { mutableStateOf<String?>(null) }
    var streamingThinkingActive by remember { mutableStateOf(false) }
    var streamingJob by remember { mutableStateOf<Job?>(null) }
    var stopRequestedByUser by remember { mutableStateOf(false) }

    // 清理已落盘的 pending，避免列表不断增长
    LaunchedEffect(conversations, pendingMessages) {
        if (pendingMessages.isEmpty()) return@LaunchedEffect
        val updated = pendingMessages.filter { pending ->
            val convo = conversations.firstOrNull { it.id == pending.conversationId } ?: return@filter true
            convo.messages.none { it.id == pending.message.id }
        }
        if (updated.size != pendingMessages.size) {
            pendingMessages = updated
        }
    }

    val localMessages = remember(messages, pendingMessages, effectiveConversationId) {
        val convoId = effectiveConversationId?.trim().orEmpty()
        val dataStoreMessages = messages
        if (convoId.isBlank()) return@remember dataStoreMessages
        val pendingForConversation = pendingMessages
            .filter { it.conversationId == convoId }
            .map { it.message }
            .filterNot { pendingMsg -> dataStoreMessages.any { it.id == pendingMsg.id } }
        dataStoreMessages + pendingForConversation
    }

    val shouldAutoScroll by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= total - 2
        }
    }
    val latestLocalMessagesSize by rememberUpdatedState(localMessages.size)
    val latestShouldAutoScroll by rememberUpdatedState(shouldAutoScroll)
    val latestEffectiveConversationId by rememberUpdatedState(effectiveConversationId)
    val latestStreamingConversationId by rememberUpdatedState(streamingConversationId)

    var lastAutoScrolledConversationId by remember { mutableStateOf<String?>(null) }
    var scrollToBottomToken by remember { mutableIntStateOf(0) }

    // 进入新会话时定位到最新消息
    LaunchedEffect(effectiveConversationId) {
        val convoId = effectiveConversationId?.trim().orEmpty()
        if (convoId.isBlank() || localMessages.isEmpty()) return@LaunchedEffect

        if (lastAutoScrolledConversationId != convoId) {
            lastAutoScrolledConversationId = convoId
            if (localMessages.isNotEmpty()) {
                listState.scrollToItem(localMessages.size - 1, scrollOffset = 0)
            }
        }
    }

    // 发送消息时滚动到最新消息
    LaunchedEffect(scrollToBottomToken, localMessages.size) {
        if (scrollToBottomToken > 0 && localMessages.isNotEmpty()) {
            listState.scrollToItem(localMessages.size - 1, scrollOffset = 0)
        }
    }

    // 流式过程中保持显示最新内容
    LaunchedEffect(isStreaming, streamingMessageId, streamingConversationId) {
        if (!isStreaming) return@LaunchedEffect
        while (isStreaming) {
            val convoId = latestEffectiveConversationId?.trim().orEmpty()
            val targetConversationId = latestStreamingConversationId?.trim().orEmpty()
            if (convoId.isNotBlank() && convoId == targetConversationId && latestShouldAutoScroll) {
                val lastIndex = latestLocalMessagesSize - 1
                if (lastIndex >= 0) {
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                    val viewportBottom = layoutInfo.viewportEndOffset
                    val needsScroll =
                        when {
                            lastVisible == null -> true
                            lastVisible.index < lastIndex -> true
                            lastVisible.index > lastIndex -> false
                            else -> (lastVisible.offset + lastVisible.size) > (viewportBottom + 4)
                        }
                    if (needsScroll) {
                        listState.scrollToItem(lastIndex, scrollOffset = 0)
                    }
                }
            }
            delay(220)
        }
    }

    fun startNewChat() {
        scope.launch {
            val activeGroup = activeGroupChatConfig
            val createRegularChatFromGroup = groupChatPageMode && activeGroup != null
            val created =
                if (createRegularChatFromGroup) {
                    repository.createConversation(title = "Chat")
                } else {
                    repository.createConversation()
                }
            preferredConversationId = created.id
            preferredConversationSetAtMs = System.currentTimeMillis()
            repository.setCurrentConversationId(created.id)
            selectedTool = null
            showAutoBrowserPreview = false
            messageText = ""
            drawerState.close()
            if (createRegularChatFromGroup) {
                navController.navigate("chat")
            }
            scrollToBottomToken++
        }
    }

    fun stopStreaming() {
        if (!isStreaming) return
        stopRequestedByUser = true
        streamingJob?.cancel(CancellationException("Stopped by user."))
    }

    fun hideKeyboardIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force) {
            if (now - lastKeyboardHideRequestAtMs < 220L) return
            if (!imeVisible && !inputFieldFocused) return
        } else if (now - lastKeyboardHideRequestAtMs < 120L) {
            return
        }
        lastKeyboardHideRequestAtMs = now
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun openMcpToolPicker() {
        showToolMenu = true
        toolMenuPage = ToolMenuPage.McpServers
        mcpToolPickerError = null
        if (selectedTool == "mcp") {
            selectedTool = null
        }
        hideKeyboardIfNeeded(force = true)
        scope.launch {
            mcpToolPickerLoading = true
            runCatching {
                val enabledServersSnapshot = repository.mcpListFlow.first().filter { it.enabled }
                enabledServersSnapshot.forEach { server ->
                    if (server.tools.isNotEmpty()) return@forEach
                    val fetched = mcpClient.fetchTools(server).getOrNull().orEmpty()
                    if (fetched.isNotEmpty()) {
                        repository.updateMcpTools(server.id, fetched)
                    }
                }
            }.onFailure { error ->
                mcpToolPickerError =
                    error.message?.trim()?.takeIf { it.isNotBlank() }
                        ?: "Failed to sync MCP tools."
            }
            val refreshedEnabledServers = repository.mcpListFlow.first().filter { it.enabled }
            val refreshedIds = refreshedEnabledServers.map { it.id }.toSet()
            val normalizedCurrent = selectedMcpServerIds.filter { it in refreshedIds }.toSet()
            selectedMcpServerIds =
                when {
                    refreshedIds.isEmpty() -> emptySet()
                    normalizedCurrent.isNotEmpty() -> normalizedCurrent
                    selectedMcpServerIds.isNotEmpty() -> normalizedCurrent
                    else -> refreshedIds
                }
            mcpToolPickerLoading = false
        }
    }

    data class DeployOutcome(
        val app: SavedApp,
        val errorText: String?,
        val deployedNow: Boolean
    )

    suspend fun deploySavedAppIfEnabled(app: SavedApp): DeployOutcome {
        val hostingConfig = repository.getWebHostingConfig()
        if (!hostingConfig.autoDeploy) {
            return DeployOutcome(
                app = app,
                errorText = "Auto deploy is off. Enable it in Web hosting.",
                deployedNow = false
            )
        }
        if (hostingConfig.token.isBlank()) {
            return DeployOutcome(
                app = app,
                errorText = "Missing Vercel token in Web hosting settings.",
                deployedNow = false
            )
        }
        return webHostingService.deployApp(
            appId = app.id,
            html = app.html,
            config = hostingConfig
        ).fold(
            onSuccess = { deployUrl ->
                DeployOutcome(
                    app =
                        app.copy(
                            deployUrl = deployUrl.trim(),
                            runtimeBuildStatus = "",
                            runtimeBuildRequestId = null,
                            runtimeBuildRunId = null,
                            runtimeBuildRunUrl = null,
                            runtimeBuildArtifactName = null,
                            runtimeBuildArtifactUrl = null,
                            runtimeBuildError = null,
                            runtimeBuildUpdatedAt = System.currentTimeMillis()
                        ),
                    errorText = null,
                    deployedNow = true
                )
            },
            onFailure = { throwable ->
                val errorText =
                    throwable.message?.trim()?.takeIf { it.isNotBlank() }
                        ?: "Deploy failed"
                DeployOutcome(app = app, errorText = errorText, deployedNow = false)
            }
        )
    }

    suspend fun triggerRuntimePackagingIfNeeded(
        app: SavedApp,
        deployedNow: Boolean
    ): SavedApp {
        if (!deployedNow) return app
        if (!RuntimeShellPlugin.isInstalled(context)) {
            return app.copy(
                runtimeBuildStatus = "disabled",
                runtimeBuildError = "Runtime shell template is required. Open Apps and download it first.",
                runtimeBuildUpdatedAt = System.currentTimeMillis()
            )
        }
        val deployUrl = app.deployUrl?.trim().orEmpty()
        if (deployUrl.isBlank()) {
            return app.copy(
                runtimeBuildStatus = "skipped",
                runtimeBuildError = "Deploy URL is missing",
                runtimeBuildUpdatedAt = System.currentTimeMillis()
            )
        }

        val versionModel = repository.appModuleVersionModelFlow.first().coerceAtLeast(1)
        return runtimePackagingService
            .triggerRuntimePackaging(
                app = app,
                deployUrl = deployUrl,
                versionModel = versionModel
            )
            .getOrElse { throwable ->
                app.copy(
                    runtimeBuildStatus = "failed",
                    runtimeBuildError = throwable.message?.trim()?.takeIf { it.isNotBlank() } ?: "Runtime packaging failed",
                    runtimeBuildVersionModel = versionModel,
                    runtimeBuildUpdatedAt = System.currentTimeMillis()
                )
            }
    }

    fun runtimeBuildStatusText(status: String?, errorText: String?): String? {
        val value = status?.trim()?.lowercase().orEmpty()
        return when (value) {
            "queued" -> "APK packaging queued"
            "in_progress" -> "APK packaging in progress"
            "success" -> "APK is ready"
            "failed" -> errorText?.takeIf { it.isNotBlank() } ?: "APK packaging failed"
            "disabled" -> errorText?.takeIf { it.isNotBlank() } ?: "Runtime shell template is required"
            "skipped" -> errorText?.takeIf { it.isNotBlank() } ?: "APK packaging skipped"
            else -> null
        }
    }

    fun shouldTrackRuntimeBuild(status: String?): Boolean {
        return when (status?.trim()?.lowercase()) {
            "queued", "in_progress" -> true
            else -> false
        }
    }

    fun startRuntimeBuildTracking(
        appId: String,
        conversationId: String,
        messageId: String,
        tagId: String
    ) {
        scope.launch {
            repeat(30) {
                delay(4000)
                val currentApp =
                    repository.savedAppsFlow.first().firstOrNull { it.id == appId }
                        ?: return@launch
                if (!shouldTrackRuntimeBuild(currentApp.runtimeBuildStatus)) {
                    return@launch
                }
                val synced =
                    runtimePackagingService.syncRuntimePackaging(currentApp)
                        .getOrElse { return@launch }
                if (synced == currentApp) return@repeat

                val persisted = repository.upsertSavedApp(synced) ?: synced
                repository.updateMessageTag(
                    conversationId = conversationId,
                    messageId = messageId,
                    tagId = tagId
                ) { current ->
                    val existingPayload =
                        parseAppDevTagPayload(
                            content = current.content,
                            fallbackName = current.title.ifBlank { "App development" },
                            fallbackStatus = current.status
                        )
                    val runtimeText = runtimeBuildStatusText(persisted.runtimeBuildStatus, persisted.runtimeBuildError)
                    val updatedPayload =
                        existingPayload.copy(
                            deployUrl = persisted.deployUrl,
                            runtimeStatus = persisted.runtimeBuildStatus.takeIf { it.isNotBlank() },
                            runtimeMessage = runtimeText,
                            runtimeRunUrl = persisted.runtimeBuildRunUrl,
                            runtimeArtifactName = persisted.runtimeBuildArtifactName,
                            runtimeArtifactUrl = persisted.runtimeBuildArtifactUrl
                        )
                    current.copy(
                        content = encodeAppDevTagPayload(updatedPayload),
                        status = if (persisted.runtimeBuildStatus == "failed") "error" else current.status
                    )
                }
            }
        }
    }

    fun downloadApkToDevice(
        artifactUrl: String,
        suggestedName: String,
        fallbackAppName: String
    ): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
        val uri = runCatching { Uri.parse(artifactUrl.trim()) }.getOrNull() ?: return false
        val rawName =
            suggestedName.trim().takeIf { it.isNotBlank() }
                ?: buildString {
                    append(
                        fallbackAppName
                            .trim()
                            .ifBlank { "zionchat-app" }
                            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
                            .trim('_')
                            .ifBlank { "zionchat-app" }
                            .take(64)
                    )
                    append("-")
                    append(System.currentTimeMillis())
                }
        val fileName = if (rawName.endsWith(".apk", ignoreCase = true)) rawName else "$rawName.apk"
        return runCatching {
            val request =
                DownloadManager.Request(uri)
                    .setTitle(fileName)
                    .setDescription("ZionChat APK download")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            manager.enqueue(request)
            true
        }.getOrDefault(false)
    }

    fun handleAppDevTagDownload(conversationId: String?, messageId: String, tag: MessageTag) {
        val convoId = conversationId?.trim().orEmpty()
        if (convoId.isBlank()) {
            Toast.makeText(context, "Conversation is unavailable.", Toast.LENGTH_SHORT).show()
            return
        }
        val initialPayload =
            parseAppDevTagPayload(
                content = tag.content,
                fallbackName = tag.title.ifBlank { "App development" },
                fallbackStatus = tag.status
            )
        val appId = initialPayload.sourceAppId?.trim().orEmpty()
        if (appId.isBlank()) {
            Toast.makeText(context, "App is not ready for APK download yet.", Toast.LENGTH_SHORT).show()
            return
        }
        if (appDevDownloadBusyIds.contains(appId)) {
            Toast.makeText(context, "APK packaging is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            appDevDownloadBusyIds = appDevDownloadBusyIds + appId
            try {
                suspend fun updateTagRuntime(saved: SavedApp, statusOverride: String? = null, messageOverride: String? = null) {
                    val runtimeText = messageOverride ?: runtimeBuildStatusText(
                        statusOverride ?: saved.runtimeBuildStatus,
                        saved.runtimeBuildError
                    )
                    repository.updateMessageTag(
                        conversationId = convoId,
                        messageId = messageId,
                        tagId = tag.id
                    ) { current ->
                        val existingPayload =
                            parseAppDevTagPayload(
                                content = current.content,
                                fallbackName = current.title.ifBlank { initialPayload.name },
                                fallbackStatus = current.status
                            )
                        val updatedPayload =
                            existingPayload.copy(
                                sourceAppId = saved.id,
                                deployUrl = saved.deployUrl,
                                deployError = null,
                                runtimeStatus = (statusOverride ?: saved.runtimeBuildStatus).takeIf { it.isNotBlank() },
                                runtimeMessage = runtimeText,
                                runtimeRunUrl = saved.runtimeBuildRunUrl,
                                runtimeArtifactName = saved.runtimeBuildArtifactName,
                                runtimeArtifactUrl = saved.runtimeBuildArtifactUrl
                            )
                        current.copy(content = encodeAppDevTagPayload(updatedPayload), status = current.status)
                    }
                }

                var savedApp =
                    repository.savedAppsFlow.first().firstOrNull { it.id == appId }
                        ?: run {
                            Toast.makeText(context, "Saved app was not found.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                val existingArtifact = savedApp.runtimeBuildArtifactUrl?.trim().orEmpty()
                if (existingArtifact.isNotBlank()) {
                    val downloaded =
                        downloadApkToDevice(
                            artifactUrl = existingArtifact,
                            suggestedName = savedApp.runtimeBuildArtifactName.orEmpty(),
                            fallbackAppName = savedApp.name
                        )
                    if (downloaded) {
                        updateTagRuntime(savedApp, statusOverride = "success", messageOverride = "APK download started")
                        Toast.makeText(context, "APK download started.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to start APK download.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                updateTagRuntime(savedApp, statusOverride = "queued", messageOverride = "Preparing APK packaging")

                val deployUrlReady =
                    savedApp.deployUrl?.trim()?.takeIf { it.isNotBlank() }
                        ?: run {
                            val deployOutcome = deploySavedAppIfEnabled(savedApp)
                            savedApp = repository.upsertSavedApp(deployOutcome.app) ?: deployOutcome.app
                            if (!deployOutcome.errorText.isNullOrBlank()) {
                                updateTagRuntime(
                                    savedApp,
                                    statusOverride = "failed",
                                    messageOverride = deployOutcome.errorText
                                )
                                Toast.makeText(context, deployOutcome.errorText, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            savedApp.deployUrl?.trim()?.takeIf { it.isNotBlank() }
                        }
                if (deployUrlReady.isNullOrBlank()) {
                    updateTagRuntime(savedApp, statusOverride = "failed", messageOverride = "Deploy URL is missing.")
                    Toast.makeText(context, "Deploy URL is missing.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val versionModel = repository.appModuleVersionModelFlow.first().coerceAtLeast(1)
                val triggered =
                    runtimePackagingService
                        .triggerRuntimePackaging(
                            app = savedApp,
                            deployUrl = deployUrlReady,
                            versionModel = versionModel
                        )
                        .getOrElse { throwable ->
                            savedApp.copy(
                                runtimeBuildStatus = "failed",
                                runtimeBuildError =
                                    throwable.message?.trim()?.takeIf { it.isNotBlank() }
                                        ?: "Runtime packaging failed",
                                runtimeBuildVersionModel = versionModel,
                                runtimeBuildUpdatedAt = System.currentTimeMillis()
                            )
                        }
                savedApp = repository.upsertSavedApp(triggered) ?: triggered
                updateTagRuntime(savedApp)

                suspend fun tryDownloadCurrentArtifact(): Boolean {
                    val artifactUrl = savedApp.runtimeBuildArtifactUrl?.trim().orEmpty()
                    if (artifactUrl.isBlank()) return false
                    val downloaded =
                        downloadApkToDevice(
                            artifactUrl = artifactUrl,
                            suggestedName = savedApp.runtimeBuildArtifactName.orEmpty(),
                            fallbackAppName = savedApp.name
                        )
                    if (downloaded) {
                        updateTagRuntime(savedApp, statusOverride = "success", messageOverride = "APK download started")
                        Toast.makeText(context, "APK download started.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to start APK download.", Toast.LENGTH_SHORT).show()
                    }
                    return downloaded
                }

                if (savedApp.runtimeBuildStatus.equals("success", ignoreCase = true)) {
                    if (!tryDownloadCurrentArtifact()) {
                        Toast.makeText(context, "APK artifact URL is missing.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (savedApp.runtimeBuildStatus.equals("failed", ignoreCase = true)) {
                    Toast.makeText(
                        context,
                        savedApp.runtimeBuildError?.ifBlank { null } ?: "APK packaging failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                if (savedApp.runtimeBuildStatus.equals("queued", ignoreCase = true) ||
                    savedApp.runtimeBuildStatus.equals("in_progress", ignoreCase = true)
                ) {
                    Toast.makeText(context, "APK packaging started, checking status...", Toast.LENGTH_SHORT).show()
                    repeat(30) {
                        delay(4000)
                        val synced =
                            runtimePackagingService
                                .syncRuntimePackaging(savedApp)
                                .getOrElse { return@repeat }
                        if (synced != savedApp) {
                            savedApp = repository.upsertSavedApp(synced) ?: synced
                            updateTagRuntime(savedApp)
                        }
                        when (savedApp.runtimeBuildStatus.trim().lowercase()) {
                            "success" -> {
                                if (!tryDownloadCurrentArtifact()) {
                                    Toast.makeText(context, "APK artifact URL is missing.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            "failed", "disabled", "skipped" -> {
                                Toast.makeText(
                                    context,
                                    savedApp.runtimeBuildError?.ifBlank { null } ?: "APK packaging failed.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                        }
                    }
                    Toast.makeText(context, "APK packaging is still running. Please try download again soon.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                appDevDownloadBusyIds = appDevDownloadBusyIds - appId
            }
        }
    }

    fun sendMessage() {
        val trimmed = messageText.trim()
        val attachmentsSnapshot = imageAttachments
        if ((trimmed.isEmpty() && attachmentsSnapshot.isEmpty()) || isStreaming || sendInFlight) return
        val selectedToolSnapshot = selectedTool
        val webSearchConfigSnapshot = webSearchConfig
        val explicitWebSearchRequest = selectedToolSnapshot == "web"
        val explicitAppBuilderRequest = selectedToolSnapshot == "app_builder"
        val explicitAutoSoulRequest = selectedToolSnapshot == "autosoul"
        val explicitAutoBrowserRequest = selectedToolSnapshot == "autobrowser"
        val confirmationMatched = pendingAppBuilderConfirmation && isAppBuilderConfirmationReply(trimmed)
        val weakAutoWebSearchIntent =
            selectedToolSnapshot == null &&
                !explicitAppBuilderRequest &&
                !explicitAutoSoulRequest &&
                !explicitAutoBrowserRequest &&
                !confirmationMatched &&
                attachmentsSnapshot.isEmpty() &&
                webSearchConfigSnapshot.autoSearchEnabled &&
                shouldAutoSearchForPrompt(trimmed)
        val weakAutoAppBuilderIntent =
            selectedToolSnapshot == null &&
                !explicitWebSearchRequest &&
                !weakAutoWebSearchIntent &&
                !explicitAppBuilderRequest &&
                !explicitAutoSoulRequest &&
                !explicitAutoBrowserRequest &&
                !confirmationMatched &&
                shouldEnableAppBuilderForPrompt(trimmed)

        stopRequestedByUser = false
        sendInFlight = true
        streamingJob = chatStreamingExecutionScope.launch {
            try {
                val nowMs = System.currentTimeMillis()
                val provisionalTitle = trimmed.lineSequence().firstOrNull().orEmpty().trim().take(24)
                val initialConversations = repository.conversationsFlow.first()

                var conversationId = effectiveConversationId?.trim().takeIf { !it.isNullOrBlank() }
                    ?: repository.currentConversationIdFlow.first()?.trim().takeIf { !it.isNullOrBlank() }
                var conversation = conversationId?.let { cid -> initialConversations.firstOrNull { it.id == cid } }

                if (conversation == null) {
                    val created = repository.createConversation()
                    conversation = created
                    conversationId = created.id
                }

                val safeConversationId = conversationId ?: return@launch
                preferredConversationId = safeConversationId
                preferredConversationSetAtMs = nowMs
                repository.setCurrentConversationId(safeConversationId)

                val encodedImages = mutableListOf<String>()
                for (attachment in attachmentsSnapshot) {
                    val url = encodeImageAttachmentToDataUrl(context, attachment)
                    if (url.isNullOrBlank()) {
                        repository.appendMessage(
                            safeConversationId,
                            Message(
                                role = "assistant",
                                content = "Failed to read the selected image. Please try again."
                            )
                        )
                        return@launch
                    }
                    encodedImages.add(url)
                }
            
                // Build attachments list instead of encoding into content
                val messageAttachments = encodedImages.map { url ->
                    MessageAttachment(url = url)
                }
            
                // Only include text content, attachments will be displayed separately
                val userContent = trimmed.trim()
                val hasVisionInput = encodedImages.isNotEmpty()
                val userMessage = Message(
                    role = "user",
                    content = userContent,
                    attachments = messageAttachments.takeIf { it.isNotEmpty() }
                )
                pendingMessages = pendingMessages + PendingMessage(safeConversationId, userMessage)
                messageText = ""
                imageAttachments = emptyList()
                scrollToBottomToken++
                repository.appendMessage(safeConversationId, userMessage)

                if (pendingAppBuilderConfirmation) {
                    pendingAppBuilderConfirmation = false
                }
                if (weakAutoAppBuilderIntent) {
                    pendingAppBuilderConfirmation = true
                    repository.appendMessage(
                        safeConversationId,
                        Message(
                            role = "assistant",
                            content = buildAppBuilderConfirmationMessage(trimmed)
                        )
                    )
                    return@launch
                }
                if (confirmationMatched && selectedTool != "app_builder") {
                    selectedTool = "app_builder"
                }

                // Update conversation title only using the latest persisted conversation to avoid wiping messages.
                val latestConversationForTitle =
                    repository.conversationsFlow.first().firstOrNull { it.id == safeConversationId }
                if (
                    latestConversationForTitle != null &&
                        (latestConversationForTitle.title.isBlank() || latestConversationForTitle.title == "New chat")
                ) {
                    if (provisionalTitle.isNotBlank()) {
                        repository.updateConversationTitle(safeConversationId, provisionalTitle)
                    }
                }

                val activeGroupChat =
                    repository.groupChatsFlow.first().firstOrNull { it.conversationId == safeConversationId }
                if (activeGroupChat != null) {
                    // Group chat always uses group dispatch path, do not fall back to single-model tool mode.
                    if (selectedToolSnapshot != null) {
                        selectedTool = null
                    }
                    val allModelsSnapshot = repository.modelsFlow.first()
                    val providerListSnapshot = repository.providersFlow.first()
                    val conversationMessagesSnapshot =
                        repository.conversationsFlow.first()
                            .firstOrNull { it.id == safeConversationId }
                            ?.messages
                            .orEmpty()
                    val customInstructionSnapshot = repository.customInstructionsFlow.first().trim()
                    val botsSnapshot = repository.botsFlow.first()
                    val groupDispatch =
                        runGroupChatDispatch(
                            chatApiClient = chatApiClient,
                            providerAuthManager = providerAuthManager,
                            group = activeGroupChat,
                            userMessage = userMessage,
                            conversationMessages = conversationMessagesSnapshot,
                            allBots = botsSnapshot,
                            allModels = allModelsSnapshot,
                            providers = providerListSnapshot,
                            customInstructions = customInstructionSnapshot,
                            systemTimeText = buildCurrentSystemTimeText()
                        )
                    groupDispatch.nextRoundRobinCursor?.let { cursor ->
                        repository.updateGroupChatRoundRobinCursor(activeGroupChat.id, cursor)
                    }
                    val replies =
                        if (groupDispatch.replies.isEmpty()) {
                            listOf(
                                Message(
                                    role = "assistant",
                                    content = groupDispatch.warning ?: "群聊暂无可用回复模型。"
                                )
                            )
                        } else {
                            groupDispatch.replies
                        }
                    replies.forEach { reply ->
                        repository.appendMessage(safeConversationId, reply)
                    }
                    selectedTool = null
                    return@launch
                }

                val latestDefaultChatModelId = repository.defaultChatModelIdFlow.first()
                if (latestDefaultChatModelId.isNullOrBlank()) {
                    if (explicitAutoSoulRequest) {
                        val autoSoulResult =
                            handleAutoSoulInvocation(
                                repository = repository,
                                chatApiClient = chatApiClient,
                                context = context,
                                userMessage = userMessage
                            )
                        repository.appendMessage(
                            safeConversationId,
                            Message(role = "assistant", content = autoSoulResult)
                        )
                        selectedTool = null
                        return@launch
                    }
                    repository.appendMessage(
                        safeConversationId,
                        Message(
                            role = "assistant",
                            content = "Please configure Chat Model (required) in Settings → Default model before chatting."
                        )
                    )
                    return@launch
                }

            val effectiveModelId =
                if (hasVisionInput) {
                    repository.defaultVisionModelIdFlow.first()?.trim().takeIf { !it.isNullOrBlank() }
                        ?: latestDefaultChatModelId
                } else {
                    latestDefaultChatModelId
                }

            val allModels = repository.modelsFlow.first()
            val providerList = repository.providersFlow.first()
            var selectedModel =
                allModels.firstOrNull { it.id == effectiveModelId }
                    ?: allModels.firstOrNull { extractRemoteModelId(it.id) == effectiveModelId }
            if (selectedModel == null) {
                repository.appendMessage(
                    safeConversationId,
                    Message(
                        role = "assistant",
                        content = "Default model not found: $effectiveModelId. Enable or add it in Models, then re-select it in Settings → Default model."
                    )
                )
                return@launch
            }

            var provider = selectedModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } } ?: providerList.firstOrNull()
            if (hasVisionInput && provider != null && provider.isQwenCodeProvider()) {
                val qwenVisionModel =
                    allModels.firstOrNull { model ->
                        model.enabled &&
                            model.providerId == provider.id &&
                            isLikelyVisionModel(model)
                    }
                if (qwenVisionModel != null && qwenVisionModel.id != selectedModel.id) {
                    selectedModel = qwenVisionModel
                    provider = selectedModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } } ?: provider
                }
            }
            if (provider == null || provider.apiUrl.isBlank() || provider.apiKey.isBlank()) {
                repository.appendMessage(
                    safeConversationId,
                    Message(
                        role = "assistant",
                        content = "Please add a provider in Settings → Model services, and fill in API URL and API Key."
                    )
                )
                return@launch
            }

            val systemMessage = run {
                val latestNickname = repository.personalNicknameFlow.first().trim()
                val latestInstructions = repository.customInstructionsFlow.first().trim()
                val latestMemories =
                    repository.memoriesFlow.first()
                        .map { it.content.trim() }
                        .filter { it.isNotBlank() }
                        .take(12)
                val content = buildString {
                    if (latestNickname.isNotBlank()) {
                        append("Nickname: ")
                        append(latestNickname)
                    }
                    if (latestInstructions.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(latestInstructions)
                    }
                    if (latestMemories.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Memories:\n")
                        latestMemories.forEach { memory ->
                            append("- ")
                            append(memory)
                            append('\n')
                        }
                    }
                    if (isNotEmpty()) append("\n\n")
                    val currentTimeText = buildCurrentSystemTimeText()
                    append("Current system time: ")
                    append(currentTimeText)
                    append('\n')
                    append("当前系统时间：")
                    append(currentTimeText)
                    if (isNotEmpty()) append("\n\n")
                    append("Memory policy:\n")
                    append("- Only store memory when the user explicitly states stable preference/profile or long-term requirements.\n")
                    append("- Never store one-off task instructions or temporary requests.\n")
                    append("- 仅在用户明确表达稳定偏好、个人画像或长期要求时写入记忆。\n")
                    append("- 不要保存一次性任务、临时指令或当前会话动作。")
                    append("\n\nTool policy:\n")
                    append("- 仅使用后续 system 消息里明确列出的工具。\n")
                    append("- 如果本轮没有工具清单，直接回答，不要输出 <tool_call>/<mcp_call> 标签。\n")
                    append("- 对于“记住/忘记/查看记忆”类请求，优先使用 memory_* 工具。\n")
                    append("- 除非用户明确选择 AutoSoul 工具，否则不要调用 autosoul_agent。\n")
                    append("- 除非用户明确选择 AutoBrowser 工具，否则不要调用任何 autobrowser_* 工具。")
                }.trim()
                if (content.isBlank()) null else Message(role = "system", content = content)
            }

            // 获取最新的消息列表
            val refreshedConversations = repository.conversationsFlow.first()
            val latestConversation = refreshedConversations.firstOrNull { it.id == safeConversationId }
            val currentMessages = latestConversation?.messages.orEmpty()

            // 检查是否为图片生成请求
            val isImageGeneration = selectedTool == "image"

            // 流式输出
            val assistantMessage = Message(role = "assistant", content = "")
            var assistantTags = emptyList<MessageTag>()
            val mcpTagAnchors = mutableListOf<McpTagAnchor>()
            var latestAssistantContent = ""
            var latestAssistantReasoning: String? = null
            var dropPendingAssistantOnExit = false
            pendingMessages = pendingMessages + PendingMessage(safeConversationId, assistantMessage)
            isStreaming = true
            streamingMessageId = assistantMessage.id
            streamingConversationId = safeConversationId
            streamingThinkingActive = false
            scrollToBottomToken++

            fun updateAssistantPending(update: (Message) -> Message) {
                pendingMessages =
                    pendingMessages.map { pending ->
                        if (pending.conversationId == safeConversationId && pending.message.id == assistantMessage.id) {
                            pending.copy(message = update(pending.message))
                        } else {
                            pending
                        }
                    }
            }

            fun updateAssistantContent(content: String, reasoning: String?) {
                val normalizedReasoning = reasoning?.trim()?.ifBlank { null }
                latestAssistantContent = content
                latestAssistantReasoning = normalizedReasoning
                val contentWithMarkers = insertMcpTagMarkers(content, mcpTagAnchors)
                updateAssistantPending { it.copy(content = contentWithMarkers, reasoning = normalizedReasoning) }
            }

            fun appendAssistantTag(tag: MessageTag) {
                assistantTags = assistantTags + tag
                if (tag.kind == "mcp" || tag.kind == "app_dev") {
                    mcpTagAnchors += McpTagAnchor(tag.id, latestAssistantContent.length)
                }
                val contentWithMarkers = insertMcpTagMarkers(latestAssistantContent, mcpTagAnchors)
                updateAssistantPending { it.copy(content = contentWithMarkers, tags = assistantTags) }
            }

            fun updateAssistantTag(tagId: String, update: (MessageTag) -> MessageTag) {
                assistantTags =
                    assistantTags.map { tag ->
                        if (tag.id == tagId) update(tag) else tag
                    }
                updateAssistantPending { it.copy(tags = assistantTags) }
            }

            try {
                val resolvedProvider = providerAuthManager.ensureValidProvider(provider)
                if (isImageGeneration) {
                    // 图片生成流程
                    handleImageGeneration(
                        repository = repository,
                        chatApiClient = chatApiClient,
                        safeConversationId = safeConversationId,
                        userPrompt = trimmed,
                        assistantMessage = assistantMessage,
                        updateAssistantContent = { updateAssistantContent(it, null) }
                    )
                } else {
                    // 普通聊天流程
                    val selectedMcpServerIdsSnapshot = selectedMcpServerIds
                    val explicitAppBuilder = selectedToolSnapshot == "app_builder"
                    val useWebSearch = explicitWebSearchRequest || weakAutoWebSearchIntent
                    val canUseAppBuilder = explicitAppBuilder
                    val canUseAutoSoulTool = explicitAutoSoulRequest
                    val canUseAutoBrowserTool = explicitAutoBrowserRequest
                    val memoryIntentActions = deriveMemoryIntentActions(trimmed)
                    val canUseMemoryTool = memoryIntentActions.isNotEmpty()
                    val configuredAppBuilderModel =
                        defaultAppBuilderModelId?.trim()?.takeIf { it.isNotBlank() }?.let { key ->
                            allModels.firstOrNull { it.id == key }
                                ?: allModels.firstOrNull { extractRemoteModelId(it.id) == key }
                        }
                    val configuredAppBuilderProvider =
                        configuredAppBuilderModel?.providerId?.let { pid ->
                            providerList.firstOrNull { it.id == pid }
                        }
                    if (explicitAppBuilder && configuredAppBuilderModel == null) {
                        val msg = "App Development model is not configured. Set it in Settings → Default model."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }
                    if (
                        explicitAppBuilder &&
                            (configuredAppBuilderProvider == null ||
                                configuredAppBuilderProvider.apiUrl.isBlank() ||
                                configuredAppBuilderProvider.apiKey.isBlank())
                    ) {
                        val msg = "Configured App Development model provider is not configured."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }
                    val webSearchResult =
                        if (useWebSearch) {
                            chatApiClient.webSearch(trimmed, webSearchConfigSnapshot)
                        } else {
                            null
                        }
                    if (explicitWebSearchRequest && webSearchResult?.isFailure == true) {
                        val reason =
                            webSearchResult.exceptionOrNull()?.message?.trim()?.takeIf { it.isNotBlank() }
                                ?: "Unknown error"
                        val msg = "Web search failed: $reason. Configure it in Settings → Search."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }
                    val webContextMessage =
                        webSearchResult
                            ?.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { content ->
                                Message(
                                    role = "system",
                                    content =
                                        "Use the following web search results as reference. Prefer these sources for factual/real-time questions.\n\n$content"
                                )
                            }

                    val enabledServers =
                        if (useWebSearch) {
                            emptyList<McpConfig>()
                        } else {
                            repository.mcpListFlow.first().filter { it.enabled }
                        }
                    val enabledServerIds = enabledServers.map { it.id }.toSet()
                    val effectiveSelectedMcpServerIds =
                        when {
                            enabledServerIds.isEmpty() -> emptySet()
                            selectedMcpServerIdsSnapshot.isEmpty() -> enabledServerIds
                            else -> selectedMcpServerIdsSnapshot.filter { it in enabledServerIds }.toSet()
                        }
                    val mcpAutoEnabled = effectiveSelectedMcpServerIds.isNotEmpty()

                    if (selectedMcpServerIdsSnapshot.isEmpty() && effectiveSelectedMcpServerIds.isNotEmpty()) {
                        selectedMcpServerIds = effectiveSelectedMcpServerIds
                    }

                    if (selectedMcpServerIdsSnapshot.isNotEmpty() && enabledServers.isEmpty()) {
                        val msg = "No MCP servers enabled. Configure one in Settings → MCP Tools."
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }

                    val serversWithTools =
                        enabledServers.map { server ->
                            if (server.tools.isNotEmpty()) return@map server
                            val fetched = mcpClient.fetchTools(server).getOrNull().orEmpty()
                            if (fetched.isNotEmpty()) {
                                repository.updateMcpTools(server.id, fetched)
                            }
                            server.copy(tools = fetched)
                        }
                    val scopedMcpServers =
                        if (mcpAutoEnabled) {
                            serversWithTools.filter { server ->
                                effectiveSelectedMcpServerIds.contains(server.id)
                            }
                        } else {
                            emptyList()
                        }
                    val availableMcpServers = scopedMcpServers.filter { it.tools.isNotEmpty() }
                    val canUseMcp = mcpAutoEnabled && availableMcpServers.isNotEmpty()
                    val canUseAnyTool =
                        canUseMcp || canUseAppBuilder || canUseAutoSoulTool || canUseAutoBrowserTool || canUseMemoryTool

                    if (mcpAutoEnabled && !canUseMcp) {
                        val msg =
                            if (effectiveSelectedMcpServerIds.isNotEmpty()) {
                                "No selected MCP servers available. Re-select MCP providers."
                            } else {
                                "No MCP tools available. Sync tools in MCP Tools first."
                            }
                        updateAssistantContent(msg, null)
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(content = msg)
                        )
                        return@launch
                    }

                    val prettyGson = GsonBuilder().setPrettyPrinting().create()
                    val serversById = availableMcpServers.associateBy { it.id }
                    val maxRounds =
                        when {
                            !canUseAnyTool -> 1
                            canUseAutoBrowserTool -> 24
                            canUseMcp -> 14
                            canUseAppBuilder || canUseAutoSoulTool -> 6
                            canUseMemoryTool -> 5
                            else -> 6
                        }
                    val maxCallsPerRound =
                        when {
                            canUseAutoBrowserTool -> 8
                            canUseMcp -> 6
                            canUseAppBuilder || canUseAutoSoulTool -> 2
                            canUseMemoryTool -> 2
                            else -> 3
                        }
                    val streamMaxTokens =
                        when {
                            canUseAutoBrowserTool -> 8192
                            canUseMcp -> 6144
                            canUseAppBuilder || canUseAutoSoulTool -> 4096
                            canUseMemoryTool -> 3072
                            else -> null
                        }
                    val modelId = extractRemoteModelId(selectedModel.id)

                    val baseMessages = mutableListOf<Message>().apply {
                        if (systemMessage != null) add(systemMessage)
                        if (webContextMessage != null) add(webContextMessage)
                        addAll(
                            currentMessages.map { msg ->
                                if (msg.role == "assistant") {
                                    msg.copy(content = stripMcpTagMarkers(msg.content))
                                } else {
                                    msg
                                }
                            }
                        )
                        if (currentMessages.lastOrNull()?.id != userMessage.id) add(userMessage)
                    }

                    val visibleContent = StringBuilder()
                    val thinkingContent = StringBuilder()
                    var autoSoulInvokedInThisTask = false
                    var toolNoCallRetryCount = 0
                    var totalToolCallsExecuted = 0

                    fun updateAssistantFromCombined(roundVisible: String, roundThinking: String) {
                        val mergedVisible = mergeTextSections(visibleContent.toString(), roundVisible)
                        val mergedThinking = mergeTextSections(thinkingContent.toString(), roundThinking).trim()
                        updateAssistantContent(mergedVisible, mergedThinking.ifBlank { null })
                    }

                    fun appendRoundToCombined(roundVisible: String, roundThinking: String) {
                        appendTextSection(visibleContent, roundVisible)
                        appendTextSection(thinkingContent, roundThinking)
                    }

                    fun isLikelyToolTaskStillInProgress(assistantText: String): Boolean {
                        val normalized = assistantText.trim()
                        if (normalized.isBlank()) return false

                        val completionHints =
                            Regex(
                                "(?is)(任务已完成|已完成|完成了|已结束|结束了|全部完成|总结如下|最终结果|final answer|all done|task completed|finished)"
                            )
                        if (completionHints.containsMatchIn(normalized)) return false

                        val pendingHints =
                            Regex(
                                "(?is)(继续|接下来|下一步|让我|我来|再试|重试|正在|继续尝试|scroll|click|open|navigate|snapshot|wait|retry|continue|next|let me|i'll)"
                            )
                        if (pendingHints.containsMatchIn(normalized)) return true

                        return canUseAutoBrowserTool && hasActiveAutoBrowserSession
                    }

                    var roundIndex = 1
                    while (roundIndex <= maxRounds) {
                        val savedAppsSnapshot = repository.savedAppsFlow.first()
                        val memoriesSnapshot = repository.memoriesFlow.first()
                        val memoryInstruction =
                            if (canUseMemoryTool) {
                                buildMemoryToolInstruction(
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound,
                                    memories = memoriesSnapshot,
                                    allowedActions = memoryIntentActions
                                )
                            } else {
                                null
                            }
                        val mcpInstruction =
                            if (canUseMcp) {
                                buildMcpToolCallInstruction(
                                    servers = availableMcpServers,
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound
                                )
                            } else {
                                null
                            }
                        val appBuilderInstruction =
                            if (canUseAppBuilder) {
                                buildAppDeveloperToolInstruction(
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound,
                                    savedApps = savedAppsSnapshot
                                )
                            } else {
                                null
                            }
                        val autoSoulInstruction =
                            if (canUseAutoSoulTool) {
                                buildAutoSoulToolInstruction(
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound,
                                    alreadyInvoked = autoSoulInvokedInThisTask
                                )
                            } else {
                                null
                            }
                        val autoBrowserInstruction =
                            if (canUseAutoBrowserTool) {
                                buildAutoBrowserToolInstruction(
                                    roundIndex = roundIndex,
                                    maxCallsPerRound = maxCallsPerRound
                                )
                            } else {
                                null
                            }

                        val requestMessages = buildList {
                            if (!memoryInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = memoryInstruction))
                            }
                            if (!appBuilderInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = appBuilderInstruction))
                            }
                            if (!autoSoulInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = autoSoulInstruction))
                            }
                            if (!autoBrowserInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = autoBrowserInstruction))
                            }
                            if (!mcpInstruction.isNullOrBlank()) {
                                add(Message(role = "system", content = mcpInstruction))
                            }
                            addAll(baseMessages)
                        }

                        val roundVisibleRaw = StringBuilder()
                        val roundThinkingRaw = StringBuilder()
                        val streamedCallBlocks = mutableListOf<String>()
                        var streamHasReadyValidCall = false
                        val inlineCallRegex = Regex("(?is)<(?:mcp_call|tool_call)\\b[^>]*>(.*?)</(?:mcp_call|tool_call)>")
                        val inlineCallStartRegex = Regex("(?is)<(?:mcp_call|tool_call)\\b")
                        var inlineCallTail = ""
                        var thinkingInlineCallTail = ""
                        var stoppedForToolCall = false
                        var inThink = false
                        var remainder = ""
                        val keepTail = 12
                        var lastUiUpdateMs = 0L
                        var lastThinkingSignalAtMs = 0L
                        var streamFinishReason: String? = null

                        fun captureStreamedCallPayload(payload: String) {
                            val cleaned = payload.trim()
                            if (cleaned.isBlank()) return
                            streamedCallBlocks += cleaned
                            if (!streamHasReadyValidCall && parseMcpToolCallsPayload(cleaned).isNotEmpty()) {
                                streamHasReadyValidCall = true
                            }
                        }

                        fun appendVisibleWithInlineCallExtraction(text: String, flush: Boolean = false) {
                            if (text.isEmpty() && !flush) return
                            var source = inlineCallTail + text
                            inlineCallTail = ""
                            if (source.isEmpty()) return

                            var cursor = 0
                            inlineCallRegex.findAll(source).forEach { match ->
                                if (match.range.first > cursor) {
                                    roundVisibleRaw.append(source.substring(cursor, match.range.first))
                                }
                                val payload = match.groupValues.getOrNull(1)?.trim().orEmpty()
                                if (payload.isNotBlank()) {
                                    captureStreamedCallPayload(payload)
                                }
                                cursor = match.range.last + 1
                            }

                            if (cursor >= source.length) return
                            val remaining = source.substring(cursor)
                            val openIdx = inlineCallStartRegex.find(remaining)?.range?.first ?: -1
                            if (openIdx >= 0) {
                                if (openIdx > 0) {
                                    roundVisibleRaw.append(remaining.substring(0, openIdx))
                                }
                                val tailCandidate = remaining.substring(openIdx)
                                if (flush) {
                                    roundVisibleRaw.append(tailCandidate)
                                } else {
                                    inlineCallTail = tailCandidate
                                }
                            } else if (flush) {
                                roundVisibleRaw.append(remaining)
                            } else {
                                val safeTailLen = 24
                                if (remaining.length > safeTailLen) {
                                    roundVisibleRaw.append(remaining.dropLast(safeTailLen))
                                    inlineCallTail = remaining.takeLast(safeTailLen)
                                } else {
                                    inlineCallTail = remaining
                                }
                            }
                        }

                        fun appendThinkingWithInlineCallExtraction(text: String, flush: Boolean = false) {
                            if (text.isEmpty() && !flush) return
                            var source = thinkingInlineCallTail + text
                            thinkingInlineCallTail = ""
                            if (source.isEmpty()) return

                            var cursor = 0
                            inlineCallRegex.findAll(source).forEach { match ->
                                if (match.range.first > cursor) {
                                    roundThinkingRaw.append(source.substring(cursor, match.range.first))
                                }
                                val payload = match.groupValues.getOrNull(1)?.trim().orEmpty()
                                if (payload.isNotBlank()) {
                                    captureStreamedCallPayload(payload)
                                }
                                cursor = match.range.last + 1
                            }

                            if (cursor >= source.length) return
                            val remaining = source.substring(cursor)
                            val openIdx = inlineCallStartRegex.find(remaining)?.range?.first ?: -1
                            if (openIdx >= 0) {
                                if (openIdx > 0) {
                                    roundThinkingRaw.append(remaining.substring(0, openIdx))
                                }
                                val tailCandidate = remaining.substring(openIdx)
                                if (flush) {
                                    roundThinkingRaw.append(tailCandidate)
                                } else {
                                    thinkingInlineCallTail = tailCandidate
                                }
                            } else if (flush) {
                                roundThinkingRaw.append(remaining)
                            } else {
                                val safeTailLen = 24
                                if (remaining.length > safeTailLen) {
                                    roundThinkingRaw.append(remaining.dropLast(safeTailLen))
                                    thinkingInlineCallTail = remaining.takeLast(safeTailLen)
                                } else {
                                    thinkingInlineCallTail = remaining
                                }
                            }
                        }

                        fun appendWithThinkExtraction(input: String) {
                            if (input.isEmpty()) return
                            var source = remainder + input
                            remainder = ""
                            var cursor = 0

                            while (cursor < source.length) {
                                if (!inThink) {
                                    val idxThink = source.indexOf("<think>", cursor, ignoreCase = true)
                                    val idxThinking = source.indexOf("<thinking>", cursor, ignoreCase = true)
                                    val next =
                                        when {
                                            idxThink < 0 -> idxThinking
                                            idxThinking < 0 -> idxThink
                                            else -> minOf(idxThink, idxThinking)
                                        }

                                    if (next < 0) {
                                        val safeEnd = maxOf(cursor, source.length - keepTail)
                                        if (safeEnd > cursor) {
                                            appendVisibleWithInlineCallExtraction(source.substring(cursor, safeEnd))
                                        }
                                        remainder = source.substring(safeEnd)
                                        return
                                    }

                                    if (next > cursor) {
                                        appendVisibleWithInlineCallExtraction(source.substring(cursor, next))
                                    }

                                    inThink = true
                                    cursor =
                                        if (idxThinking == next) {
                                            next + "<thinking>".length
                                        } else {
                                            next + "<think>".length
                                        }
                                } else {
                                    val idxEndThink = source.indexOf("</think>", cursor, ignoreCase = true)
                                    val idxEndThinking = source.indexOf("</thinking>", cursor, ignoreCase = true)
                                    val next =
                                        when {
                                            idxEndThink < 0 -> idxEndThinking
                                            idxEndThinking < 0 -> idxEndThink
                                            else -> minOf(idxEndThink, idxEndThinking)
                                        }

                                    if (next < 0) {
                                        val safeEnd = maxOf(cursor, source.length - keepTail)
                                        if (safeEnd > cursor) {
                                            appendThinkingWithInlineCallExtraction(source.substring(cursor, safeEnd))
                                        }
                                        remainder = source.substring(safeEnd)
                                        return
                                    }

                                    if (next > cursor) {
                                        appendThinkingWithInlineCallExtraction(source.substring(cursor, next))
                                    }

                                    inThink = false
                                    cursor =
                                        if (idxEndThinking == next) {
                                            next + "</thinking>".length
                                        } else {
                                            next + "</think>".length
                                        }
                                }
                            }
                        }

                        val normalizedReasoningEffort =
                            selectedModel.reasoningEffort
                                ?.trim()
                                ?.lowercase()
                                ?.takeIf {
                                    it == "none" ||
                                        it == "minimal" ||
                                        it == "low" ||
                                        it == "medium" ||
                                        it == "high" ||
                                        it == "xhigh"
                                }
                        val providerSupportsThinkingParam =
                            resolvedProvider.isCodexProvider() ||
                                resolvedProvider.isGrok2ApiProvider()
                        val effectiveReasoningEffort =
                            when {
                                !chatThinkingEnabled -> "none"
                                normalizedReasoningEffort != null -> normalizedReasoningEffort
                                providerSupportsThinkingParam -> "medium"
                                else -> null
                            }
                        val enableThinking = chatThinkingEnabled

                        chatApiClient.chatCompletionsStream(
                            provider = resolvedProvider,
                            modelId = modelId,
                            messages = requestMessages,
                            extraHeaders = selectedModel.headers,
                            reasoningEffort = effectiveReasoningEffort,
                            enableThinking = enableThinking,
                            maxTokens = streamMaxTokens,
                            conversationId = safeConversationId
                        ).takeWhile { delta ->
                            val now = System.currentTimeMillis()
                            delta.finishReason
                                ?.trim()
                                ?.lowercase()
                                ?.takeIf { it.isNotBlank() }
                                ?.let { reason -> streamFinishReason = reason }
                            var sawThinkingSignal = false
                            delta.reasoning?.takeIf { it.isNotBlank() }?.let {
                                appendThinkingWithInlineCallExtraction(it)
                                sawThinkingSignal = true
                            }
                            delta.content?.let { appendWithThinkExtraction(it) }
                            if (inThink) {
                                sawThinkingSignal = true
                            }
                            if (sawThinkingSignal) {
                                lastThinkingSignalAtMs = now
                            }
                            val thinkingActiveNow = (inThink || (now - lastThinkingSignalAtMs) <= 650L)
                            if (streamingThinkingActive != thinkingActiveNow) {
                                streamingThinkingActive = thinkingActiveNow
                            }

                            val shouldUpdate =
                                now - lastUiUpdateMs >= 33L || (roundVisibleRaw.length + roundThinkingRaw.length) % 120 == 0
                            if (shouldUpdate) {
                                updateAssistantFromCombined(
                                    roundVisible = roundVisibleRaw.toString(),
                                    roundThinking = roundThinkingRaw.toString()
                                )
                                lastUiUpdateMs = now
                            }

                            val hasOpenInlineTagTail =
                                inlineCallStartRegex.containsMatchIn(inlineCallTail) ||
                                    inlineCallStartRegex.containsMatchIn(thinkingInlineCallTail)
                            val hasReadyValidCall =
                                canUseAnyTool &&
                                    streamHasReadyValidCall &&
                                    !hasOpenInlineTagTail
                            if (hasReadyValidCall) {
                                stoppedForToolCall = true
                            }
                            !hasReadyValidCall
                        }.collect()

                        if (remainder.isNotEmpty()) {
                            if (inThink) {
                                if (!stoppedForToolCall) {
                                    appendThinkingWithInlineCallExtraction(remainder, flush = true)
                                }
                            } else if (!stoppedForToolCall) {
                                appendVisibleWithInlineCallExtraction(remainder, flush = true)
                            }
                            remainder = ""
                        }
                        if (!stoppedForToolCall) {
                            appendVisibleWithInlineCallExtraction("", flush = true)
                            appendThinkingWithInlineCallExtraction("", flush = true)
                        }

                        val thinkingSplit = splitThinkingFromContent(roundVisibleRaw.toString())
                        var roundVisible = thinkingSplit.visible
                        var roundThinking = mergeTextSections(roundThinkingRaw.toString(), thinkingSplit.thinking)

                        val visibleCallBlocks = extractInlineMcpCallBlocks(roundVisible)
                        roundVisible = visibleCallBlocks.visibleText.trim()
                        val thinkingCallBlocks = extractInlineMcpCallBlocks(roundThinking)
                        roundThinking = thinkingCallBlocks.visibleText.trim()

                        val allRawCallBlocks = streamedCallBlocks + visibleCallBlocks.blocks + thinkingCallBlocks.blocks
                        val hadRawCallBlocks = allRawCallBlocks.any { it.isNotBlank() }
                        val combinedRoundReplyText =
                            buildString {
                                if (roundVisible.isNotBlank()) {
                                    append(roundVisible)
                                }
                                if (roundThinking.isNotBlank()) {
                                    if (isNotEmpty()) append('\n')
                                    append(roundThinking)
                                }
                            }.trim()
                        val looksLikeDirectToolPayload =
                            combinedRoundReplyText.isNotBlank() &&
                                Regex(
                                    "(?is)<(?:mcp_call|tool_call)\\b|\\b(?:mcp_call|tool_call)\\b|\"(?:toolName|tool_name|tool|serverId|server_id|arguments|args|calls)\"\\s*:"
                                ).containsMatchIn(combinedRoundReplyText)
                        val callPayloadBlocks =
                            buildList {
                                addAll(allRawCallBlocks.filter { it.isNotBlank() })
                                if (isEmpty() && looksLikeDirectToolPayload) {
                                    add(combinedRoundReplyText)
                                }
                            }
                        val hadAnyToolPayloadCandidate = callPayloadBlocks.isNotEmpty()
                        val parseFailedBlocks = mutableListOf<String>()
                        val roundSeenSignatures = linkedSetOf<String>()
                        val parsedCallsRaw =
                            callPayloadBlocks
                                .flatMap { block ->
                                    val parsed = parseMcpToolCallsPayload(block)
                                    if (parsed.isEmpty()) {
                                        parseFailedBlocks += block
                                    }
                                    parsed
                                }
                                .map { call ->
                                    call.copy(
                                        serverId = call.serverId.trim(),
                                        toolName = call.toolName.trim(),
                                        arguments =
                                            call.arguments.mapNotNull { (k, v) ->
                                                val key = k.trim()
                                                if (key.isBlank()) return@mapNotNull null
                                                key to v
                                            }.toMap()
                                    )
                                }
                                .filter { it.toolName.isNotBlank() }
                                .filter { call ->
                                    roundSeenSignatures.add(buildMcpCallSignature(call))
                                }
                                .take(maxCallsPerRound)
                        var droppedAutoSoulCallSummary: String? = null
                        var keptAutoSoulInRound = false
                        val parsedCalls =
                            parsedCallsRaw.filter { call ->
                                if (!isBuiltInAutoSoulCall(call)) {
                                    return@filter true
                                }
                                if (!canUseAutoSoulTool) {
                                    droppedAutoSoulCallSummary =
                                        "- autosoul_agent is disabled in this turn.\n" +
                                            "- Do NOT call autosoul_agent unless the user explicitly selected AutoSoul.\n" +
                                            "- Continue with a direct user-facing answer."
                                    return@filter false
                                }
                                if (autoSoulInvokedInThisTask) {
                                    droppedAutoSoulCallSummary =
                                        "- autosoul_agent was already invoked for this user task.\n" +
                                            "- Do NOT call autosoul_agent again.\n" +
                                            "- Continue with direct user-facing answer based on existing AutoSoul result."
                                    return@filter false
                                }
                                if (keptAutoSoulInRound) {
                                    droppedAutoSoulCallSummary =
                                        "- autosoul_agent should be called at most once in one round.\n" +
                                            "- Keep one call only and continue with a direct user-facing answer."
                                    return@filter false
                                }
                                keptAutoSoulInRound = true
                                true
                            }

                        updateAssistantFromCombined(roundVisible = roundVisible, roundThinking = roundThinking)
                        appendRoundToCombined(roundVisible = roundVisible, roundThinking = roundThinking)

                        parseFailedBlocks.take(2).forEach { rawBlock ->
                            val rawPreview = rawBlock.trim().take(1800)
                            appendAssistantTag(
                                MessageTag(
                                    kind = "mcp",
                                    title = "Tool",
                                    content = buildMcpTagDetailContent(
                                        round = roundIndex,
                                        serverName = null,
                                        toolName = "unknown",
                                        argumentsJson = rawPreview,
                                        statusText = "Failed",
                                        attempts = 1,
                                        elapsedMs = null,
                                        resultText = null,
                                        errorText = "Invalid mcp_call payload."
                                    ),
                                    status = "error"
                                )
                            )
                        }

                        if (roundVisible.isNotBlank()) {
                            baseMessages.add(Message(role = "assistant", content = roundVisible))
                        }

                        if (!canUseAnyTool) {
                            break
                        }
                        if (parsedCalls.isEmpty()) {
                            val streamWasTruncated =
                                streamFinishReason == "length" ||
                                    streamFinishReason == "max_tokens" ||
                                    streamFinishReason == "max_output_tokens"
                            if (!droppedAutoSoulCallSummary.isNullOrBlank() && roundIndex < maxRounds) {
                                baseMessages.add(
                                    Message(
                                        role = "system",
                                        content = buildMcpRoundResultContext(
                                            roundIndex = roundIndex,
                                            summary = droppedAutoSoulCallSummary.orEmpty()
                                        )
                                    )
                                )
                                roundIndex += 1
                                continue
                            }
                            if (streamWasTruncated && roundIndex < maxRounds) {
                                baseMessages.add(
                                    Message(
                                        role = "system",
                                        content = buildMcpRoundResultContext(
                                            roundIndex = roundIndex,
                                            summary =
                                                "- The previous response ended due to token limit.\n" +
                                                    "- Continue the same task without repeating finished steps.\n" +
                                                    "- If a tool call is needed, output valid <tool_call> JSON."
                                        )
                                    )
                                )
                                roundIndex += 1
                                continue
                            }
                            if (hadAnyToolPayloadCandidate && roundIndex < maxRounds) {
                                baseMessages.add(
                                    Message(
                                        role = "system",
                                        content = buildMcpRoundResultContext(
                                            roundIndex = roundIndex,
                                            summary =
                                                "- Failed to parse executable tool_call payload from the previous reply.\n" +
                                                    "- Keep the visible reply, then emit a valid JSON payload inside " +
                                                    "<tool_call>...</tool_call>."
                                        )
                                    )
                                )
                                roundIndex += 1
                                continue
                            }
                            val roundCombinedText =
                                buildString {
                                    if (roundVisible.isNotBlank()) {
                                        append(roundVisible)
                                    }
                                    if (roundThinking.isNotBlank()) {
                                        if (isNotEmpty()) append('\n')
                                        append(roundThinking)
                                    }
                                }.trim()
                            val shouldRetryWithoutToolCall =
                                roundIndex < maxRounds &&
                                    (canUseAutoBrowserTool || canUseMcp || totalToolCallsExecuted > 0) &&
                                    toolNoCallRetryCount < (if (canUseAutoBrowserTool) 3 else 2) &&
                                    isLikelyToolTaskStillInProgress(roundCombinedText)
                            if (shouldRetryWithoutToolCall) {
                                toolNoCallRetryCount += 1
                                baseMessages.add(
                                    Message(
                                        role = "system",
                                        content = buildMcpRoundResultContext(
                                            roundIndex = roundIndex,
                                            summary =
                                                "- The previous reply did not include an executable tool_call.\n" +
                                                    "- The task still appears to be in progress.\n" +
                                                    "- Continue with concrete <tool_call> steps now.\n" +
                                                    "- If the task is already complete, provide a final answer and stop."
                                        )
                                    )
                                )
                                roundIndex += 1
                                continue
                            }
                            break
                        }
                        toolNoCallRetryCount = 0

                        val roundSummary = StringBuilder()
                        var processedCallCount = 0
                        var autoBrowserTagIdInRound: String? = null
                        val autoBrowserRoundHistory = mutableListOf<String>()
                        var autoBrowserCallCountInRound = 0

                        suspend fun handleAutoBrowserCall(
                            call: PlannedMcpToolCall,
                            args: Map<String, Any>,
                            argsJson: String
                        ): Boolean {
                            if (!isBuiltInAutoBrowserCall(call)) return false
                            autoBrowserCallCountInRound += 1

                            fun argString(vararg keys: String): String {
                                return keys
                                    .asSequence()
                                    .mapNotNull { key ->
                                        val raw = args.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: return@mapNotNull null
                                        when (raw) {
                                            is String -> raw.trim().takeIf { it.isNotBlank() }
                                            is Number, is Boolean -> raw.toString()
                                            else -> raw.toString().trim().takeIf { it.isNotBlank() }
                                        }
                                    }
                                    .firstOrNull()
                                    .orEmpty()
                            }

                            fun argDouble(vararg keys: String): Double? {
                                return keys.asSequence().mapNotNull { key ->
                                    val raw = args.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: return@mapNotNull null
                                    when (raw) {
                                        is Number -> raw.toDouble()
                                        is String -> raw.trim().toDoubleOrNull()
                                        else -> null
                                    }
                                }.firstOrNull()
                            }

                            val toolLower = call.toolName.trim().lowercase()
                            val actionHint = argString("action", "op", "operation", "type", "intent").lowercase()
                            val action =
                                when {
                                    toolLower in setOf("autobrowser_start_session", "browser_start_session") -> "start_session"
                                    toolLower in setOf("autobrowser_navigate", "autobrowser_open_url", "browser_navigate") -> "navigate"
                                    toolLower in setOf("autobrowser_click_ref", "browser_click_ref") -> "click_ref"
                                    toolLower in setOf("autobrowser_fill_css", "browser_fill_css") -> "fill_css"
                                    toolLower in setOf("autobrowser_exec_js", "browser_exec_js") -> "exec_js"
                                    toolLower in setOf("autobrowser_wait", "browser_wait") -> "wait"
                                    toolLower in setOf("autobrowser_snapshot", "browser_snapshot") -> "snapshot"
                                    toolLower in setOf("autobrowser_upload_file", "browser_upload_file") -> "upload_file"
                                    toolLower in setOf("autobrowser_close_session", "browser_close_session") -> "close_session"
                                    actionHint in setOf("start", "start_session", "launch", "open_session") -> "start_session"
                                    actionHint in setOf("navigate", "goto", "open", "open_url") -> "navigate"
                                    actionHint in setOf("click_ref", "click", "tap") -> "click_ref"
                                    actionHint in setOf("fill", "fill_css", "type", "input") -> "fill_css"
                                    actionHint in setOf("exec_js", "js", "javascript", "run_js") -> "exec_js"
                                    actionHint in setOf("wait", "sleep", "delay") -> "wait"
                                    actionHint in setOf("snapshot", "capture") -> "snapshot"
                                    actionHint in setOf("upload", "upload_file", "choose_file") -> "upload_file"
                                    actionHint in setOf("close", "close_session", "stop", "end") -> "close_session"
                                    else -> "unknown"
                                }

                            fun ensureTag(actionTitle: String): String {
                                val existing = autoBrowserTagIdInRound
                                if (!existing.isNullOrBlank()) return existing
                                val pendingTag =
                                    MessageTag(
                                        kind = "mcp",
                                        title = "AutoBrowser · $actionTitle",
                                        content = buildMcpTagDetailContent(
                                            round = roundIndex,
                                            serverName = "AutoBrowser",
                                            toolName = call.toolName.ifBlank { "autobrowser" },
                                            argumentsJson = argsJson,
                                            statusText = "Running...",
                                            attempts = autoBrowserCallCountInRound,
                                            elapsedMs = null,
                                            resultText = null,
                                            errorText = null
                                        ),
                                        status = "running"
                                    )
                                appendAssistantTag(pendingTag)
                                autoBrowserTagIdInRound = pendingTag.id
                                return pendingTag.id
                            }

                            fun refreshTag(
                                actionTitle: String,
                                statusText: String,
                                status: String,
                                detailLine: String,
                                errorText: String? = null
                            ) {
                                val tagId = ensureTag(actionTitle)
                                autoBrowserRoundHistory += "[${autoBrowserCallCountInRound}] ${detailLine.trim()}"
                                val resultText = autoBrowserRoundHistory.joinToString("\n").trim().take(6000)
                                val content =
                                    buildMcpTagDetailContent(
                                        round = roundIndex,
                                        serverName = "AutoBrowser",
                                        toolName = call.toolName.ifBlank { "autobrowser" },
                                        argumentsJson = argsJson,
                                        statusText = statusText,
                                        attempts = autoBrowserCallCountInRound,
                                        elapsedMs = null,
                                        resultText = resultText,
                                        errorText = errorText
                                    )
                                updateAssistantTag(tagId) {
                                    it.copy(
                                        title = "AutoBrowser · $actionTitle",
                                        content = content,
                                        status = status
                                    )
                                }
                            }

                            if (!canUseAutoBrowserTool) {
                                refreshTag(
                                    actionTitle = "已拒绝",
                                    statusText = "Failed",
                                    status = "error",
                                    detailLine = "AutoBrowser 未启用，本轮调用已拒绝。",
                                    errorText = "AutoBrowser is not enabled in this turn."
                                )
                                roundSummary.append("- autobrowser: rejected (not enabled)\n")
                                return true
                            }

                            val session = autoBrowserSessions[safeConversationId]
                            val startedAt = System.currentTimeMillis()
                            var actionTitle = "执行操作"
                            var detail = ""
                            var status = "success"
                            var errorText: String? = null

                            when (action) {
                                "start_session" -> {
                                    val startUrl = normalizeAutoBrowserUrl(argString("url", "target", "start_url", "homepage"))
                                    val targetUrl = startUrl.ifBlank { session?.currentUrl?.ifBlank { "about:blank" } ?: "about:blank" }
                                    updateAutoBrowserSession(safeConversationId) { current ->
                                        val base = current ?: AutoBrowserSessionState(conversationId = safeConversationId)
                                        base.copy(
                                            isActive = true,
                                            currentUrl = targetUrl,
                                            renderNonce = base.renderNonce + 1,
                                            lastActionTitle = "启动会话",
                                            lastError = null
                                        )
                                    }
                                    val loaded = if (targetUrl == "about:blank") true else waitForAutoBrowserLoadAfter(safeConversationId, startedAt)
                                    actionTitle = "打开 ${targetUrl.take(42)}"
                                    detail = if (loaded) "会话已启动并打开 $targetUrl" else "会话已启动，页面加载超时：$targetUrl"
                                }

                                "navigate" -> {
                                    val url = normalizeAutoBrowserUrl(argString("url", "target", "href", "link"))
                                    if (url.isBlank()) {
                                        status = "error"
                                        errorText = "autobrowser_navigate requires arguments.url"
                                        actionTitle = "跳转失败"
                                        detail = "缺少 arguments.url"
                                    } else {
                                        updateAutoBrowserSession(safeConversationId) { current ->
                                            val base = current ?: AutoBrowserSessionState(conversationId = safeConversationId)
                                            base.copy(isActive = true, currentUrl = url, renderNonce = base.renderNonce + 1, lastActionTitle = "打开网页")
                                        }
                                        val loaded = waitForAutoBrowserLoadAfter(safeConversationId, startedAt, timeoutMs = 24_000L)
                                        actionTitle = "打开 ${url.take(42)}"
                                        detail = if (loaded) "已跳转到 $url" else "跳转超时（仍可能在加载）: $url"
                                    }
                                }

                                "snapshot" -> {
                                    if (session?.isActive != true) {
                                        status = "error"
                                        errorText = "No active browser session. Call autobrowser_start_session first."
                                        actionTitle = "快照失败"
                                        detail = "未检测到活动网页会话。"
                                    } else {
                                        val (snapshotText, refs) = captureAutoBrowserSnapshot(safeConversationId)
                                        updateAutoBrowserSession(safeConversationId) {
                                            it?.copy(snapshotRefs = refs, snapshotText = snapshotText, lastActionTitle = "抓取快照", lastError = null)
                                        }
                                        actionTitle = "快照 ${refs.size} 项"
                                        detail = snapshotText.take(2600)
                                    }
                                }

                                "click_ref" -> {
                                    val ref = argString("ref", "snapshot_ref", "id").trim()
                                    val normalizedRef = ref.removePrefix("@")
                                    val selector =
                                        session?.snapshotRefs?.get(ref)
                                            ?: session?.snapshotRefs?.get(normalizedRef)
                                    if (session?.isActive != true || ref.isBlank() || selector.isNullOrBlank()) {
                                        status = "error"
                                        errorText = "snapshot ref not found: $ref"
                                        actionTitle = "点击失败"
                                        detail = "找不到 ref=$ref，请先调用 autobrowser_snapshot（ref 如 @e1）。"
                                    } else {
                                        val (ok, message) = clickAutoBrowserSelector(safeConversationId, selector)
                                        if (!ok) {
                                            status = "error"
                                            errorText = message
                                            actionTitle = "点击失败"
                                            detail = "点击 ref=$ref 失败：$message"
                                        } else {
                                            actionTitle = "点击 $ref"
                                            detail = "已点击 ref=$ref ${message.take(90)}".trim()
                                        }
                                    }
                                }

                                "fill_css" -> {
                                    val selector = argString("selector", "css", "css_selector", "target").trim()
                                    val value = argString("value", "text", "input", "content")
                                    if (session?.isActive != true || selector.isBlank()) {
                                        status = "error"
                                        errorText = "autobrowser_fill_css requires selector"
                                        actionTitle = "填写失败"
                                        detail = "缺少活动会话或 selector。"
                                    } else {
                                        val (ok, message) = fillAutoBrowserSelector(safeConversationId, selector, value)
                                        if (!ok) {
                                            status = "error"
                                            errorText = message
                                            actionTitle = "填写失败"
                                            detail = "填写 selector=$selector 失败：$message"
                                        } else {
                                            actionTitle = "填写 ${selector.take(26)}"
                                            detail = "已填写 selector=$selector"
                                        }
                                    }
                                }

                                "exec_js" -> {
                                    val js = argString("script", "js", "javascript", "code")
                                    if (session?.isActive != true || js.isBlank()) {
                                        status = "error"
                                        errorText = "autobrowser_exec_js requires script"
                                        actionTitle = "JS 失败"
                                        detail = "缺少活动会话或 script。"
                                    } else {
                                        val output = decodeJavascriptResult(evaluateAutoBrowserScript(safeConversationId, js, timeoutMs = 24_000L))
                                        actionTitle = "执行 JS"
                                        detail = "JS execution output:\n${output.take(1800).ifBlank { "(empty)" }}"
                                    }
                                }

                                "wait" -> {
                                    val seconds = (argDouble("seconds", "sec", "duration", "wait") ?: 1.0).coerceIn(0.1, 20.0)
                                    delay((seconds * 1000.0).toLong())
                                    actionTitle = "等待 ${"%.1f".format(java.util.Locale.US, seconds)}s"
                                    detail = "已等待 ${"%.1f".format(java.util.Locale.US, seconds)} 秒"
                                }

                                "upload_file" -> {
                                    val selector = argString("selector", "css", "css_selector", "target").trim()
                                    val mimeType = argString("mime", "mime_type", "accept").ifBlank { "*/*" }
                                    if (session?.isActive != true || selector.isBlank()) {
                                        status = "error"
                                        errorText = "autobrowser_upload_file requires selector"
                                        actionTitle = "上传失败"
                                        detail = "缺少活动会话或 selector。"
                                    } else {
                                        val (ok, message) = uploadAutoBrowserFileToSelector(safeConversationId, selector, mimeType)
                                        if (!ok) {
                                            status = "error"
                                            errorText = message
                                            actionTitle = if (message == "file_picker_cancelled") "上传取消" else "上传失败"
                                            detail = "文件上传失败：$message"
                                        } else {
                                            actionTitle = "上传文件"
                                            detail = "已上传文件：$message"
                                        }
                                    }
                                }

                                "close_session" -> {
                                    updateAutoBrowserSession(safeConversationId) { null }
                                    if (effectiveConversationId == safeConversationId) {
                                        showAutoBrowserPreview = false
                                    }
                                    actionTitle = "关闭会话"
                                    detail = "网页会话已关闭。"
                                }

                                else -> {
                                    status = "error"
                                    errorText = "unknown_autobrowser_action"
                                    actionTitle = "未知操作"
                                    detail = "无法识别 AutoBrowser 操作：${call.toolName.ifBlank { actionHint }}"
                                }
                            }

                            if (status == "success") {
                                appendAutoBrowserHistory(safeConversationId, detail.replace('\n', ' ').take(320))
                                updateAutoBrowserSession(safeConversationId) { current ->
                                    current?.copy(lastActionTitle = actionTitle, lastError = null)
                                }
                            } else {
                                updateAutoBrowserSession(safeConversationId) { current ->
                                    current?.copy(lastActionTitle = actionTitle, lastError = errorText)
                                }
                            }

                            refreshTag(
                                actionTitle = actionTitle,
                                statusText = if (status == "success") "Completed" else "Failed",
                                status = status,
                                detailLine = detail,
                                errorText = errorText
                            )
                            roundSummary.append("- autobrowser: ")
                            roundSummary.append(detail.replace('\n', ' ').take(560))
                            roundSummary.append('\n')
                            return true
                        }

                        suspend fun processParsedCall(call: PlannedMcpToolCall) {
                            processedCallCount += 1
                            val args =
                                call.arguments
                                    .mapNotNull { (k, v) ->
                                        val key = k.trim()
                                        if (key.isBlank()) return@mapNotNull null
                                        val value = v ?: return@mapNotNull null
                                        key to value
                                    }
                                    .toMap()
                            val argsJson = prettyGson.toJson(args)

                            if (isBuiltInMemoryCall(call)) {
                                fun anyString(vararg keys: String): String {
                                    return keys
                                        .asSequence()
                                        .mapNotNull { key ->
                                            val raw =
                                                args.entries.firstOrNull { entry ->
                                                    entry.key.equals(key, ignoreCase = true)
                                                }?.value ?: return@mapNotNull null
                                            when (raw) {
                                                is String -> raw.trim().takeIf { it.isNotBlank() }
                                                is Number, is Boolean -> raw.toString()
                                                else -> raw.toString().trim().takeIf { it.isNotBlank() }
                                            }
                                        }.firstOrNull()
                                        .orEmpty()
                                }

                                val toolLower = call.toolName.trim().lowercase()
                                val actionHint = anyString("action", "op", "operation", "type", "mode", "intent").lowercase()
                                val action =
                                    when {
                                        toolLower in setOf(
                                            "memory",
                                            "memory_write",
                                            "memory_add",
                                            "memory_save",
                                            "save_memory",
                                            "remember_memory"
                                        ) -> "write"
                                        toolLower in setOf(
                                            "memory_delete",
                                            "memory_remove",
                                            "memory_forget",
                                            "delete_memory",
                                            "forget_memory"
                                        ) -> "delete"
                                        toolLower in setOf(
                                            "memory_list",
                                            "list_memory",
                                            "show_memory",
                                            "memory_read",
                                            "memory_get"
                                        ) -> "list"
                                        actionHint in setOf("write", "add", "save", "remember", "upsert", "create", "store") -> "write"
                                        actionHint in setOf("delete", "remove", "forget", "drop") -> "delete"
                                        actionHint in setOf("list", "show", "read", "get", "view") -> "list"
                                        else -> "unknown"
                                    }

                                if (!canUseMemoryTool) {
                                    appendAssistantTag(
                                        MessageTag(
                                            kind = "memory",
                                            title = "Memory",
                                            content =
                                                "Memory call rejected.\n- This user message does not explicitly request memory operations.\n- Allowed only when user clearly asks remember/forget/list memories.",
                                            status = "error"
                                        )
                                    )
                                    roundSummary.append("- memory: rejected (no explicit user memory intent)\n")
                                    return
                                }
                                if (action != "unknown" && action !in memoryIntentActions) {
                                    appendAssistantTag(
                                        MessageTag(
                                            kind = "memory",
                                            title = "Memory",
                                            content =
                                                "Memory call rejected.\n- Requested action: $action\n- Allowed actions for this turn: ${
                                                    memoryIntentActions.sorted().joinToString(", ")
                                                }",
                                            status = "error"
                                        )
                                    )
                                    roundSummary.append("- memory: rejected (action not allowed in this turn)\n")
                                    return
                                }

                                val (detail, status, summaryLine) =
                                    when (action) {
                                        "write" -> {
                                            val content =
                                                anyString(
                                                    "content",
                                                    "memory",
                                                    "text",
                                                    "item",
                                                    "value",
                                                    "fact",
                                                    "note"
                                                ).trim()
                                            if (content.isBlank()) {
                                                Triple(
                                                    "Memory write failed.\n- Missing arguments.content (or memory/text/item).",
                                                    "error",
                                                    "- memory_write: invalid arguments"
                                                )
                                            } else {
                                                val memoriesBefore = repository.memoriesFlow.first()
                                                val existed =
                                                    memoriesBefore.any { item ->
                                                        item.content.trim().equals(content, ignoreCase = true)
                                                    }
                                                val saved = repository.addMemory(content)
                                                if (saved == null) {
                                                    Triple(
                                                        "Memory write failed.\n- Could not save memory item.",
                                                        "error",
                                                        "- memory_write: failed"
                                                    )
                                                } else {
                                                    val detailText =
                                                        buildString {
                                                            append("Memory ")
                                                            append(if (existed) "already exists" else "saved")
                                                            appendLine(".")
                                                            append("- id=")
                                                            appendLine(saved.id)
                                                            append("- content=")
                                                            append(saved.content)
                                                        }
                                                    Triple(
                                                        detailText,
                                                        "success",
                                                        if (existed) {
                                                            "- memory_write: already exists"
                                                        } else {
                                                            "- memory_write: saved"
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        "delete" -> {
                                            val idHint = anyString("id", "memory_id", "item_id", "target_id").trim()
                                            val contentHint =
                                                anyString(
                                                    "content",
                                                    "memory",
                                                    "text",
                                                    "item",
                                                    "value",
                                                    "target",
                                                    "query"
                                                ).trim()
                                            val memoriesNow = repository.memoriesFlow.first()
                                            val target =
                                                when {
                                                    idHint.isNotBlank() -> {
                                                        memoriesNow.firstOrNull { item ->
                                                            item.id.equals(idHint, ignoreCase = true)
                                                        }
                                                    }

                                                    contentHint.isNotBlank() -> {
                                                        memoriesNow.firstOrNull { item ->
                                                            val itemContent = item.content.trim()
                                                            itemContent.equals(contentHint, ignoreCase = true) ||
                                                                itemContent.contains(contentHint, ignoreCase = true)
                                                        }
                                                    }

                                                    else -> null
                                                }

                                            if (target == null) {
                                                Triple(
                                                    "Memory delete failed.\n- Target memory not found. Use memory_list to inspect available ids.",
                                                    "error",
                                                    "- memory_delete: target not found"
                                                )
                                            } else {
                                                repository.deleteMemory(target.id)
                                                val detailText =
                                                    buildString {
                                                        appendLine("Memory deleted.")
                                                        append("- id=")
                                                        appendLine(target.id)
                                                        append("- content=")
                                                        append(target.content)
                                                    }
                                                Triple(
                                                    detailText,
                                                    "success",
                                                    "- memory_delete: deleted"
                                                )
                                            }
                                        }

                                        "list" -> {
                                            val memoriesNow = repository.memoriesFlow.first()
                                            val detailText =
                                                if (memoriesNow.isEmpty()) {
                                                    "No saved memories."
                                                } else {
                                                    buildString {
                                                        append("Saved memories (")
                                                        append(memoriesNow.size)
                                                        appendLine("):")
                                                        memoriesNow.take(20).forEach { item ->
                                                            append("- id=")
                                                            append(item.id)
                                                            append(": ")
                                                            appendLine(item.content.trim().take(220))
                                                        }
                                                        val more = memoriesNow.size - 20
                                                        if (more > 0) {
                                                            append("- ... and ")
                                                            append(more)
                                                            appendLine(" more")
                                                        }
                                                    }
                                                }
                                            Triple(
                                                detailText,
                                                "success",
                                                "- memory_list: returned ${memoriesNow.size} item(s)"
                                            )
                                        }

                                        else -> {
                                            Triple(
                                                "Unknown memory tool action.\n- Supported tools: memory_write, memory_delete, memory_list.\n- You can also provide arguments.action = write/delete/list.",
                                                "error",
                                                "- memory: unknown action"
                                            )
                                        }
                                    }

                                appendAssistantTag(
                                    MessageTag(
                                        kind = "memory",
                                        title = "Memory",
                                        content = detail,
                                        status = status
                                    )
                                )
                                roundSummary.append(summaryLine)
                                roundSummary.append('\n')
                                return
                            }
                            if (handleAutoBrowserCall(call = call, args = args, argsJson = argsJson)) {
                                return
                            }

                            if (isBuiltInAutoSoulCall(call)) {
                                autoSoulInvokedInThisTask = true
                                val taskPrompt = resolveAutoSoulTaskPrompt(args, trimmed)
                                val pendingTag =
                                    MessageTag(
                                        kind = "mcp",
                                        title = "AutoSoul",
                                        content = buildMcpTagDetailContent(
                                            round = roundIndex,
                                            serverName = "AutoSoul",
                                            toolName = call.toolName.ifBlank { "autosoul_agent" },
                                            argumentsJson = argsJson,
                                            statusText = "Running...",
                                            attempts = 1,
                                            elapsedMs = null,
                                            resultText = null,
                                            errorText = null
                                        ),
                                        status = "running"
                                    )
                                appendAssistantTag(pendingTag)

                                val startedAt = System.currentTimeMillis()
                                val execution =
                                    executeAutoSoulTask(
                                        repository = repository,
                                        chatApiClient = chatApiClient,
                                        context = context,
                                        taskPrompt = taskPrompt,
                                        attachments = userMessage.attachments
                                    ).getOrElse { error ->
                                        val errorText =
                                            error.message?.trim().orEmpty().ifBlank { "AutoSoul execution failed." }
                                        val tagContent = buildMcpTagDetailContent(
                                            round = roundIndex,
                                            serverName = "AutoSoul",
                                            toolName = call.toolName.ifBlank { "autosoul_agent" },
                                            argumentsJson = argsJson,
                                            statusText = "Failed",
                                            attempts = 1,
                                            elapsedMs = System.currentTimeMillis() - startedAt,
                                            resultText = null,
                                            errorText = errorText
                                        )
                                        updateAssistantTag(pendingTag.id) {
                                            it.copy(content = tagContent, status = "error")
                                        }
                                        roundSummary.append("- autosoul_agent: failed - ")
                                        roundSummary.append(errorText.take(300))
                                        roundSummary.append('\n')
                                        return
                                    }

                                val elapsedMs = System.currentTimeMillis() - startedAt
                                val resultText = buildAutoSoulToolResultText(execution)
                                val tagContent = buildMcpTagDetailContent(
                                    round = roundIndex,
                                    serverName = "AutoSoul",
                                    toolName = call.toolName.ifBlank { "autosoul_agent" },
                                    argumentsJson = argsJson,
                                    statusText = if (execution.success) "Completed" else "Failed",
                                    attempts = 1,
                                    elapsedMs = elapsedMs,
                                    resultText = resultText,
                                    errorText = if (execution.success) null else execution.error
                                )
                                updateAssistantTag(pendingTag.id) {
                                    it.copy(content = tagContent, status = if (execution.success) "success" else "error")
                                }
                                roundSummary.append("- autosoul_agent: ")
                                roundSummary.append(if (execution.success) "success" else "failed")
                                roundSummary.append(" | ")
                                roundSummary.append(resultText.replace('\n', ' ').take(560))
                                roundSummary.append('\n')
                                return
                            }

                            if (isBuiltInAppDeveloperCall(call)) {
                                val parsedSpec = parseAppDevToolSpec(args)
                                val savedAppsSnapshot = repository.savedAppsFlow.first()
                                val targetSavedApp = parsedSpec?.let { resolveExistingSavedApp(it, savedAppsSnapshot) }
                                val rawName =
                                    normalizeAppDisplayName(
                                        (args["name"] as? String)?.trim().orEmpty()
                                            .ifBlank { parsedSpec?.name.orEmpty() }
                                            .ifBlank { targetSavedApp?.name.orEmpty() }
                                            .ifBlank { "App development" }
                                    )
                                val mode = parsedSpec?.mode ?: "create"
                                val pendingPayload =
                                    AppDevTagPayload(
                                        name = rawName,
                                        subtitle = if (mode == "edit") "Updating app" else "Developing app",
                                        description = parsedSpec?.description?.takeIf { it.isNotBlank() }
                                            ?: targetSavedApp?.description.orEmpty(),
                                        appIcon = parsedSpec?.appIcon ?: inferLucideIconFromSignal(rawName, parsedSpec?.description.orEmpty()),
                                        style = parsedSpec?.style.orEmpty(),
                                        features = parsedSpec?.features.orEmpty(),
                                        progress = 8,
                                        status = "running",
                                        html = if (mode == "edit") targetSavedApp?.html.orEmpty() else "",
                                        error = null,
                                        sourceAppId = targetSavedApp?.id,
                                        mode = mode
                                    )
                                val pendingTag =
                                    MessageTag(
                                        kind = "app_dev",
                                        title = rawName,
                                        content = encodeAppDevTagPayload(pendingPayload),
                                        status = "running"
                                    )
                                appendAssistantTag(pendingTag)

                                if (!canUseAppBuilder) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "App builder is disabled for this request."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: disabled\n")
                                    return
                                }

                                if (parsedSpec == null) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error =
                                                "Invalid arguments. app_developer requires: name + description + app_icon."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: invalid arguments\n")
                                    return
                                }
                                if (parsedSpec.mode == "edit" && targetSavedApp == null) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "Target saved app was not found. Provide targetAppId or exact targetAppName."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: target app missing\n")
                                    return
                                }

                                val preferredAppModelKey =
                                    defaultAppBuilderModelId?.trim()?.takeIf { it.isNotBlank() }
                                if (preferredAppModelKey.isNullOrBlank()) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "App Development model is not configured. Set it in Settings → Default model."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: model not configured\n")
                                    return
                                }

                                val appModel =
                                    allModels.firstOrNull { it.id == preferredAppModelKey }
                                        ?: allModels.firstOrNull { extractRemoteModelId(it.id) == preferredAppModelKey }
                                if (appModel == null) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "Configured App Development model was not found. Re-select it in Settings → Default model."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: model missing\n")
                                    return
                                }

                                val appProviderRaw =
                                    appModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } }

                                if (appProviderRaw == null || appProviderRaw.apiUrl.isBlank() || appProviderRaw.apiKey.isBlank()) {
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = "Configured App Development model provider is not configured."
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: provider unavailable\n")
                                    return
                                }

                                val appProvider = runCatching { providerAuthManager.ensureValidProvider(appProviderRaw) }.getOrElse { error ->
                                    val payload =
                                        pendingPayload.copy(
                                            progress = 0,
                                            status = "error",
                                            error = error.message?.trim().orEmpty().ifBlank { "Provider auth failed." }
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: auth failed\n")
                                    return
                                }

                                val startedAt = System.currentTimeMillis()
                                var streamedProgress = pendingPayload.progress
                                var lastProgressUpdateAtMs = 0L
                                var lastDraftUpdateAtMs = 0L
                                fun updateAppDevDraft(rawDraft: String) {
                                    val draft = normalizeGeneratedHtmlDraft(rawDraft)
                                    if (draft.length < 40) return
                                    val now = System.currentTimeMillis()
                                    if (now - lastDraftUpdateAtMs < 170L) return
                                    lastDraftUpdateAtMs = now
                                    updateAssistantTag(pendingTag.id) { current ->
                                        val existing =
                                            parseAppDevTagPayload(
                                                content = current.content,
                                                fallbackName = parsedSpec.name.ifBlank { rawName },
                                                fallbackStatus = "running"
                                            )
                                        val updated =
                                            existing.copy(
                                                html = draft.take(120_000),
                                                status = "running",
                                                error = null,
                                                mode = parsedSpec.mode,
                                                sourceAppId = targetSavedApp?.id ?: existing.sourceAppId
                                            )
                                        current.copy(content = encodeAppDevTagPayload(updated), status = "running")
                                    }
                                }
                                fun updateAppDevProgress(progressValue: Int) {
                                    val incoming = progressValue.coerceIn(6, 98)
                                    if (incoming <= streamedProgress) return
                                    val normalized = incoming
                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressUpdateAtMs < 90L && normalized < 97) return
                                    streamedProgress = normalized
                                    lastProgressUpdateAtMs = now
                                    updateAssistantTag(pendingTag.id) { current ->
                                        val existing =
                                            parseAppDevTagPayload(
                                                content = current.content,
                                                fallbackName = parsedSpec.name,
                                                fallbackStatus = "running"
                                            )
                                        val updated =
                                            existing.copy(
                                                progress = normalized,
                                                status = "running",
                                                error = null,
                                                mode = parsedSpec.mode,
                                                sourceAppId = targetSavedApp?.id ?: existing.sourceAppId
                                            )
                                        current.copy(
                                            content = encodeAppDevTagPayload(updated),
                                            status = "running"
                                        )
                                    }
                                }
                                val htmlResult =
                                    runCatching {
                                        if (parsedSpec.mode == "edit") {
                                            reviseHtmlAppFromPrompt(
                                                chatApiClient = chatApiClient,
                                                provider = appProvider,
                                                modelId = extractRemoteModelId(appModel.id),
                                                extraHeaders = appModel.headers,
                                                currentHtml = targetSavedApp?.html.orEmpty(),
                                                requestText = parsedSpec.editRequest.orEmpty(),
                                                spec = parsedSpec,
                                                sourceUserPrompt = trimmed,
                                                rawToolArgsJson = argsJson,
                                                onProgress = ::updateAppDevProgress,
                                                onDraftHtml = ::updateAppDevDraft
                                            )
                                        } else {
                                            generateHtmlAppFromSpec(
                                                chatApiClient = chatApiClient,
                                                provider = appProvider,
                                                modelId = extractRemoteModelId(appModel.id),
                                                extraHeaders = appModel.headers,
                                                spec = parsedSpec,
                                                sourceUserPrompt = trimmed,
                                                rawToolArgsJson = argsJson,
                                                onProgress = ::updateAppDevProgress,
                                                onDraftHtml = ::updateAppDevDraft
                                            )
                                        }
                                    }
                                val elapsedMs = System.currentTimeMillis() - startedAt
                                val html = htmlResult.getOrNull()
                                if (html != null) {
                                    val finalName =
                                        if (parsedSpec.mode == "edit") {
                                            normalizeAppDisplayName(
                                                parsedSpec.name.takeIf { it.isNotBlank() } ?: targetSavedApp?.name.orEmpty()
                                            )
                                        } else {
                                            parsedSpec.name
                                        }
                                    val finalSubtitle =
                                        if (parsedSpec.mode == "edit") {
                                            compactAppDescription(parsedSpec.editRequest.orEmpty(), "App updated")
                                        } else {
                                            compactAppDescription(parsedSpec.description, "HTML app ready")
                                        }
                                    val finalDescription =
                                        if (parsedSpec.mode == "edit") {
                                            compactAppDescription(
                                                parsedSpec.editRequest.orEmpty(),
                                                targetSavedApp?.description.orEmpty().ifBlank { "Updated HTML app" }
                                            )
                                        } else {
                                            compactAppDescription(parsedSpec.description, "HTML app")
                                        }
                                    val finalIcon =
                                        extractLucideIconName(parsedSpec.appIcon)
                                            ?: extractSavedAppIconNameFromRaw(parsedSpec.description, html)
                                            ?: inferLucideIconFromSignal(finalName, finalDescription)
                                    val finalHtml = ensureAppIconMetadataInHtml(html, finalIcon)
                                    val savedAppId = targetSavedApp?.id ?: java.util.UUID.randomUUID().toString()
                                    val baseSavedApp =
                                        SavedApp(
                                            id = savedAppId,
                                            sourceTagId = pendingTag.id,
                                            name = finalName,
                                            description = finalDescription,
                                            html = finalHtml,
                                            versionCode = targetSavedApp?.versionCode ?: 1,
                                            versionName = targetSavedApp?.versionName ?: "v1",
                                            createdAt = targetSavedApp?.createdAt ?: System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    val persistedApp =
                                        repository.upsertSavedApp(
                                            baseSavedApp,
                                            note = if (parsedSpec.mode == "edit") "AI edited app" else "AI generated app"
                                        ) ?: baseSavedApp
                                    val payload =
                                        pendingPayload.copy(
                                            name = finalName,
                                            subtitle = finalSubtitle,
                                            description = finalDescription,
                                            appIcon = finalIcon,
                                            style = parsedSpec.style.ifBlank { pendingPayload.style },
                                            features = if (parsedSpec.features.isNotEmpty()) parsedSpec.features else pendingPayload.features,
                                            progress = 100,
                                            status = "success",
                                            html = finalHtml,
                                            error = null,
                                            sourceAppId = persistedApp.id,
                                            mode = parsedSpec.mode,
                                            deployUrl = null,
                                            deployError = null,
                                            runtimeStatus = null,
                                            runtimeMessage = null,
                                            runtimeRunUrl = null,
                                            runtimeArtifactName = null,
                                            runtimeArtifactUrl = null
                                        )
                                    updateAssistantTag(pendingTag.id) {
                                        it.copy(
                                            title = finalName,
                                            content = encodeAppDevTagPayload(payload),
                                            status = "success"
                                        )
                                    }

                                    roundSummary.append("- app_developer: ")
                                    roundSummary.append(if (parsedSpec.mode == "edit") "updated " else "generated ")
                                    roundSummary.append(finalName.take(80))
                                    roundSummary.append(" in ")
                                    roundSummary.append(formatElapsedDuration(elapsedMs))
                                    roundSummary.append('\n')
                                } else {
                                    val error = htmlResult.exceptionOrNull()
                                    updateAssistantTag(pendingTag.id) { current ->
                                        val existing =
                                            parseAppDevTagPayload(
                                                content = current.content,
                                                fallbackName = rawName,
                                                fallbackStatus = "running"
                                            )
                                        val payload =
                                            existing.copy(
                                                progress = 0,
                                                status = "error",
                                                error = error?.message?.trim().orEmpty().ifBlank { "App generation failed." },
                                                sourceAppId = targetSavedApp?.id ?: existing.sourceAppId,
                                                mode = parsedSpec.mode
                                            )
                                        current.copy(content = encodeAppDevTagPayload(payload), status = "error")
                                    }
                                    roundSummary.append("- app_developer: failed\n")
                                }
                                return
                            }

                            fun resolveServer(): McpConfig? {
                                val serverId = call.serverId.trim()
                                if (serverId.isNotBlank()) {
                                    serversById[serverId]?.let { return it }
                                    availableMcpServers.firstOrNull {
                                        it.name.trim().equals(serverId, ignoreCase = true)
                                    }?.let { return it }
                                }
                                val candidates = availableMcpServers.filter { server ->
                                    server.tools.any { tool -> tool.name == call.toolName }
                                }
                                return if (candidates.size == 1) candidates.first() else null
                            }

                            val server = resolveServer()
                            val tagTitle = call.toolName.trim().ifBlank { "Tool" }
                            val pendingTag =
                                MessageTag(
                                    kind = "mcp",
                                    title = tagTitle,
                                    content = buildMcpTagDetailContent(
                                        round = roundIndex,
                                        serverName = server?.name,
                                        toolName = call.toolName,
                                        argumentsJson = argsJson,
                                        statusText = "Running...",
                                        attempts = 1,
                                        elapsedMs = null,
                                        resultText = null,
                                        errorText = null
                                    ),
                                    status = "running"
                                )
                            appendAssistantTag(pendingTag)

                            if (server == null) {
                                val tagContent = buildMcpTagDetailContent(
                                    round = roundIndex,
                                    serverName = null,
                                    toolName = call.toolName,
                                    argumentsJson = argsJson,
                                    statusText = "Failed",
                                    attempts = 1,
                                    elapsedMs = null,
                                    resultText = null,
                                    errorText = "MCP server not found for this tool call."
                                )
                                updateAssistantTag(pendingTag.id) {
                                    it.copy(content = tagContent, status = "error")
                                }
                                roundSummary.append("- ")
                                roundSummary.append(call.toolName)
                                roundSummary.append(": server unavailable\n")
                                return
                            }

                            val startedAt = System.currentTimeMillis()
                            val callResult =
                                mcpClient.callTool(
                                    config = server,
                                    toolCall = McpToolCall(toolName = call.toolName, arguments = args)
                                ).getOrElse(::toMcpFailureResult)
                            val elapsedMs = System.currentTimeMillis() - startedAt

                            val compactContent = callResult.content.trim().take(1800)
                            val finalError = (callResult.error ?: callResult.content).trim()
                            val apiError = if (callResult.success) null else extractExplicitApiError(finalError)
                            val hasExplicitApiError = !apiError.isNullOrBlank()
                            val tagContent = buildMcpTagDetailContent(
                                round = roundIndex,
                                serverName = server.name,
                                toolName = call.toolName,
                                argumentsJson = argsJson,
                                statusText = if (hasExplicitApiError) "Failed" else "Completed",
                                attempts = 1,
                                elapsedMs = elapsedMs,
                                resultText = if (callResult.success) compactContent.ifBlank { "{}" } else "{}",
                                errorText = apiError
                            )
                            updateAssistantTag(pendingTag.id) {
                                it.copy(
                                    title = call.toolName.trim().ifBlank { server.name },
                                    content = tagContent,
                                    status = if (hasExplicitApiError) "error" else "success"
                                )
                            }

                            roundSummary.append("- ")
                            roundSummary.append(server.name)
                            roundSummary.append("/")
                            roundSummary.append(call.toolName)
                            roundSummary.append(": ")
                            roundSummary.append(
                                when {
                                    callResult.success -> compactContent.ifBlank { "{}" }
                                    hasExplicitApiError -> "api error: $apiError"
                                    else -> "failed"
                                }.take(600)
                            )
                            roundSummary.append('\n')
                        }

                        parsedCalls.forEach { call ->
                            processParsedCall(call)
                        }

                        if (processedCallCount == 0) {
                            break
                        }
                        totalToolCallsExecuted += processedCallCount

                        baseMessages.add(
                            Message(
                                role = "system",
                                content = buildMcpRoundResultContext(
                                    roundIndex,
                                    if (canUseAutoBrowserTool) {
                                        roundSummary.toString().take(1200)
                                    } else {
                                        roundSummary.toString().take(2200)
                                    }
                                )
                            )
                        )
                        val toolContextPrefix = "Tool results from round "
                        val toolContextIndexes =
                            baseMessages.withIndex()
                                .filter { entry ->
                                    entry.value.role == "system" &&
                                        entry.value.content.startsWith(toolContextPrefix)
                                }
                                .map { entry -> entry.index }
                        val keepToolContextCount = if (canUseAutoBrowserTool) 3 else 5
                        val extraToolContextCount = toolContextIndexes.size - keepToolContextCount
                        if (extraToolContextCount > 0) {
                            toolContextIndexes
                                .take(extraToolContextCount)
                                .asReversed()
                                .forEach { indexToRemove ->
                                    baseMessages.removeAt(indexToRemove)
                                }
                        }

                        roundIndex += 1
                    }

                    val finalContent = visibleContent.toString().trim()
                    val finalReasoning = thinkingContent.toString().trim().ifBlank { null }
                    val finalContentWithMarkers = insertMcpTagMarkers(finalContent, mcpTagAnchors)
                    updateAssistantContent(finalContent, finalReasoning)
                    val hasAssistantOutput =
                        finalContent.isNotBlank() || !finalReasoning.isNullOrBlank() || assistantTags.isNotEmpty()
                    if (hasAssistantOutput) {
                        repository.appendMessage(
                            safeConversationId,
                            assistantMessage.copy(
                                content = finalContentWithMarkers,
                                reasoning = finalReasoning,
                                tags = assistantTags.takeIf { it.isNotEmpty() }
                            )
                        )
                    }

                    val chatModelId = extractRemoteModelId(selectedModel.id)
                    val chatExtraHeaders = selectedModel.headers
                    val userTextForMemory = trimmed
                    val assistantTextForMemory = finalContent
                    val assistantMessageIdForTags = assistantMessage.id

                    if (shouldAttemptMemoryExtraction(userTextForMemory)) {
                        scope.launch(Dispatchers.IO) {
                            val result =
                                runCatching {
                                    extractMemoryCandidatesFromTurn(
                                        chatApiClient = chatApiClient,
                                        provider = resolvedProvider,
                                        modelId = chatModelId,
                                        userText = userTextForMemory,
                                        assistantText = assistantTextForMemory,
                                        extraHeaders = chatExtraHeaders
                                    )
                                }

                            if (result.isFailure) return@launch

                            val candidates =
                                filterMemoryCandidates(
                                    candidates = result.getOrDefault(emptyList()),
                                    userText = userTextForMemory
                                )
                            if (candidates.isEmpty()) return@launch

                            val existingBeforeSave =
                                repository.memoriesFlow.first()
                                    .map { it.content.trim().lowercase() }
                                    .toMutableSet()
                            val saved = mutableListOf<String>()
                            candidates.forEach { candidate ->
                                val key = candidate.trim().lowercase()
                                if (key in existingBeforeSave) return@forEach
                                repository.addMemory(candidate)?.let { added ->
                                    saved.add(added.content)
                                    existingBeforeSave += key
                                }
                            }
                            if (saved.isEmpty()) return@launch

                            if (hasAssistantOutput) {
                                val detail =
                                    buildString {
                                        append("Saved memories:\n")
                                        saved.forEach { item ->
                                            append("- ")
                                            append(item)
                                            append('\n')
                                        }
                                    }.trim()

                                repository.appendMessageTag(
                                    conversationId = safeConversationId,
                                    messageId = assistantMessageIdForTags,
                                    tag = MessageTag(kind = "memory", title = "Memory", content = detail)
                                )
                            }
                        }
                    }

                    if (finalContent.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                val titleModelKey = repository.defaultTitleModelIdFlow.first()?.trim().orEmpty()
                                if (titleModelKey.isBlank()) return@runCatching

                                val modelsAll = repository.modelsFlow.first()
                                val titleModel =
                                    modelsAll.firstOrNull { it.id == titleModelKey }
                                        ?: modelsAll.firstOrNull { extractRemoteModelId(it.id) == titleModelKey }
                                        ?: return@runCatching

                                val providersAll = repository.providersFlow.first()
                                val titleProvider =
                                    titleModel.providerId?.let { pid -> providersAll.firstOrNull { it.id == pid } }
                                        ?: providersAll.firstOrNull()
                                        ?: return@runCatching

                                if (titleProvider.apiUrl.isBlank() || titleProvider.apiKey.isBlank()) return@runCatching

                                val validTitleProvider = providerAuthManager.ensureValidProvider(titleProvider)
                                val convo = repository.conversationsFlow.first().firstOrNull { it.id == safeConversationId }
                                    ?: return@runCatching

                                val currentTitle = convo.title.trim()
                                val shouldGenerateTitle =
                                    currentTitle.isBlank() ||
                                        currentTitle == "New chat" ||
                                        (provisionalTitle.isNotBlank() && currentTitle == provisionalTitle.trim())
                                if (!shouldGenerateTitle) return@runCatching

                                val transcript =
                                    buildConversationTranscript(
                                        messages = convo.messages,
                                        maxMessages = 12,
                                        maxCharsPerMessage = 420
                                    )
                                if (transcript.isBlank()) return@runCatching

                                val generatedTitle =
                                    generateConversationTitle(
                                        chatApiClient = chatApiClient,
                                        provider = validTitleProvider,
                                        modelId = extractRemoteModelId(titleModel.id),
                                        transcript = transcript,
                                        extraHeaders = titleModel.headers
                                    )?.trim().orEmpty()

                                val finalTitle = generatedTitle.trim().trim('"', '\'').take(48)
                                if (finalTitle.isBlank()) return@runCatching

                                repository.updateConversationTitle(safeConversationId, finalTitle)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (!stopRequestedByUser) throw e
                val partialContent = latestAssistantContent.trim()
                val partialReasoning = latestAssistantReasoning?.trim()?.ifBlank { null }
                val hasPartialOutput =
                    partialContent.isNotBlank() ||
                        !partialReasoning.isNullOrBlank() ||
                        assistantTags.isNotEmpty()
                val alreadyPersisted =
                    repository.conversationsFlow.first()
                        .firstOrNull { it.id == safeConversationId }
                        ?.messages
                        ?.any { it.id == assistantMessage.id } == true
                if (!alreadyPersisted && hasPartialOutput) {
                    val partialContentWithMarkers = insertMcpTagMarkers(partialContent, mcpTagAnchors)
                    repository.appendMessage(
                        safeConversationId,
                        assistantMessage.copy(
                            content = partialContentWithMarkers,
                            reasoning = partialReasoning,
                            tags = assistantTags.takeIf { it.isNotEmpty() }
                        )
                    )
                } else if (!alreadyPersisted) {
                    dropPendingAssistantOnExit = true
                }
            } catch (e: Exception) {
                val errorMsg = "Request failed: ${e.message ?: e.toString()}"
                updateAssistantContent(errorMsg, null)
                repository.appendMessage(safeConversationId, assistantMessage.copy(content = errorMsg, reasoning = null))
            } finally {
                if (dropPendingAssistantOnExit) {
                    pendingMessages =
                        pendingMessages.filterNot { pending ->
                            pending.conversationId == safeConversationId && pending.message.id == assistantMessage.id
                        }
                }
                sendInFlight = false
                isStreaming = false
                streamingMessageId = null
                streamingConversationId = null
                streamingThinkingActive = false
                streamingJob = null
                stopRequestedByUser = false
                selectedTool = null // 清除选中的工具
            }
            } finally {
                sendInFlight = false
                if (!isStreaming) {
                    streamingJob = null
                    stopRequestedByUser = false
                }
            }
        }
    }

    LaunchedEffect(pendingAppAutomationTask?.id, isStreaming) {
        val task = pendingAppAutomationTask ?: return@LaunchedEffect
        if (task.id == lastAutoDispatchedTaskId) return@LaunchedEffect
        if (isStreaming) return@LaunchedEffect
        val automationPrompt = buildPendingAppAutomationPrompt(task)
        lastAutoDispatchedTaskId = task.id
        repository.clearPendingAppAutomationTask()
        if (automationPrompt.isBlank()) return@LaunchedEffect
        showToolMenu = false
        selectedTool = "app_builder"
        messageText = automationPrompt
        sendMessage()
    }

    val displayName = nickname.takeIf { it.isNotBlank() } ?: "Kendall Williamson"
    val appWorkspaceActive = !appWorkspaceTagId.isNullOrBlank()

    LaunchedEffect(appWorkspaceActive) {
        if (appWorkspaceActive && drawerState.isOpen) {
            drawerState.close()
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible && showToolMenu) {
            showToolMenu = false
            toolMenuPage = ToolMenuPage.Tools
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !appWorkspaceActive,
        drawerContent = {
            SidebarContent(
                conversations = conversations,
                groupChats = groupChats,
                currentConversationId = effectiveConversationId,
                nickname = displayName,
                avatarUri = avatarUri,
                onClose = { scope.launch { drawerState.close() } },
                onNewChat = ::startNewChat,
                onConversationClick = { convo ->
                    scope.launch {
                        preferredConversationId = convo.id
                        preferredConversationSetAtMs = System.currentTimeMillis()
                        repository.setCurrentConversationId(convo.id)
                        drawerState.close()
                        scrollToBottomToken++
                    }
                    if (groupChatPageMode) {
                        navController.navigate("chat")
                    }
                },
                onGroupConversationClick = { group ->
                    scope.launch {
                        drawerState.close()
                    }
                    navController.navigate("group_chat/${group.id}")
                },
                onDeleteConversation = { id ->
                    scope.launch {
                        repository.deleteConversation(id)
                        if (preferredConversationId == id) {
                            preferredConversationId = null
                        }
                    }
                },
                navController = navController
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.25f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatBackground)
        ) {
            // Chat content (behind the top bar), so messages can scroll into the fade region.
            if (localMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = listTopPadding, bottom = bottomContentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyChatState()
                }
            } else {
                val latestAssistantToolbarIds = remember(localMessages) {
                    localMessages
                        .asReversed()
                        .asSequence()
                        .filter { it.role == "assistant" }
                        .take(3)
                        .map { it.id }
                        .toSet()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = listTopPadding,
                        bottom = bottomContentPadding + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (activeGroupChatConfig != null) 10.dp else 16.dp)
                ) {
                    itemsIndexed(localMessages, key = { _, item -> item.id }) { index, message ->
                        val previousMessage = if (index > 0) localMessages[index - 1] else null
                        val showGroupSpeakerHeader =
                            if (activeGroupChatConfig == null || message.role != "assistant") {
                                false
                            } else {
                                val currentBatch = message.speakerBatchId?.trim().orEmpty()
                                val previousBatch = previousMessage?.speakerBatchId?.trim().orEmpty()
                                currentBatch.isBlank() || currentBatch != previousBatch
                            }
                        val showToolbar =
                            message.role == "assistant" &&
                                latestAssistantToolbarIds.contains(message.id) &&
                                activeGroupChatConfig == null
                        MessageItem(
                            message = message,
                            conversationId = effectiveConversationId,
                            isStreaming = isStreaming
                                && streamingConversationId == effectiveConversationId
                                && message.id == streamingMessageId,
                            isThinkingActive = isStreaming
                                && streamingConversationId == effectiveConversationId
                                && message.id == streamingMessageId
                                && streamingThinkingActive,
                            showToolbar = showToolbar,
                            groupMode = activeGroupChatConfig != null,
                            showGroupSpeaker = showGroupSpeakerHeader,
                            onShowReasoning = { reasoning ->
                                hideKeyboardIfNeeded(force = true)
                                showToolMenu = false
                                tagSheetMessageId = null
                                tagSheetTagId = null
                                thinkingSheetText = reasoning
                                showThinkingSheet = true
                            },
                            onShowTag = { messageId, tagId ->
                                hideKeyboardIfNeeded(force = true)
                                showToolMenu = false
                                showThinkingSheet = false
                                thinkingSheetText = null
                                val clickedTag =
                                    localMessages
                                        .firstOrNull { it.id == messageId }
                                        ?.tags
                                        .orEmpty()
                                        .firstOrNull { it.id == tagId }
                                if (clickedTag?.kind == "app_dev") {
                                    tagSheetMessageId = null
                                    tagSheetTagId = null
                                    appWorkspaceMessageId = messageId
                                    appWorkspaceTagId = tagId
                                } else {
                                    appWorkspaceMessageId = null
                                    appWorkspaceTagId = null
                                    tagSheetMessageId = messageId
                                    tagSheetTagId = tagId
                                }
                            },
                            onDownloadAppDevTag = { messageId, tag ->
                                hideKeyboardIfNeeded(force = true)
                                showToolMenu = false
                                handleAppDevTagDownload(
                                    conversationId = effectiveConversationId,
                                    messageId = messageId,
                                    tag = tag
                                )
                            },
                            userBubbleColor = accentPalette.bubbleColor,
                            userBubbleSecondaryColor = accentPalette.bubbleColorSecondary,
                            userBubbleTextColor = accentPalette.bubbleTextColor,
                            onEdit = { },
                            onDelete = { convoId, messageId ->
                                scope.launch { repository.deleteMessage(convoId, messageId) }
                            }
                        )
                    }
                }
            }

            // Top fade: start at the bottom of TopNavBar (blue line), fully hidden above (orange line).
            val topFadeHeight = 52.dp
            val topFadeTopPadding = maxOf(
                statusBarTopPadding,
                statusBarTopPadding + topBarHeightDp - topFadeHeight
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topFadeTopPadding)
                    .background(ChatBackground)
                    .zIndex(1f)
            )
            TopFadeScrim(
                color = ChatBackground,
                height = topFadeHeight,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topFadeTopPadding)
                    .zIndex(1f)
            )
            TopFadeScrim(
                color = ChatBackground.copy(alpha = 0.9f),
                height = topFadeHeight * 0.62f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topFadeTopPadding + 1.dp)
                    .zIndex(1.1f)
            )

            // Bottom fade mask follows the input area so keyboard-up state keeps the same
            // "outside-area" gradient blur feeling as the normal bottom state.
            val bottomFadeBottomPadding = if (imeVisible) inputBottomInset else 0.dp
            val bottomFadeHeightTarget =
                if (imeVisible) {
                    (bottomBarHeightDp + 36.dp).coerceAtLeast(88.dp)
                } else {
                    // 键盘收起时覆盖导航条区域，并把输入框上方的渐变缩小到一小段。
                    (bottomBarHeightDp + navBottomPadding + 10.dp).coerceAtLeast(72.dp)
                }
            val bottomFadeColors =
                remember(imeVisible) {
                    if (imeVisible) {
                        listOf(
                            ChatBackground.copy(alpha = 0f),
                            ChatBackground.copy(alpha = 0.3f),
                            ChatBackground.copy(alpha = 0.62f),
                            ChatBackground.copy(alpha = 0.92f),
                            ChatBackground
                        )
                    } else {
                        listOf(
                            ChatBackground.copy(alpha = 0f),
                            ChatBackground.copy(alpha = 0.24f),
                            ChatBackground.copy(alpha = 0.56f),
                            ChatBackground.copy(alpha = 0.9f),
                            ChatBackground
                        )
                    }
                }
            val bottomFadeHeight by animateDpAsState(
                targetValue = bottomFadeHeightTarget,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                label = "bottom_fade_height"
            )
            if (bottomFadeHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomFadeBottomPadding)
                        .fillMaxWidth()
                        .height(bottomFadeHeight)
                        .zIndex(3f)
                        .background(
                            Brush.verticalGradient(
                                colors = bottomFadeColors
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .zIndex(2f)
            ) {
                Box(modifier = Modifier.onSizeChanged { topBarHeightPx = it.height }) {
                    if (groupChatPageMode && activeGroupChatConfig != null) {
                        GroupTopNavBar(
                            onMenuClick = {
                                if (!appWorkspaceActive) {
                                    scope.launch { drawerState.open() }
                                }
                            },
                            onNewThreadClick = ::startNewChat,
                            avatarUri = avatarUri,
                            onAvatarClick = { navController.navigate("personalization") }
                        )
                    } else {
                        TopNavBar(
                            onMenuClick = {
                                if (!appWorkspaceActive) {
                                    scope.launch { drawerState.open() }
                                }
                            },
                            onChatModelClick = { showChatModelPicker = true },
                            onNewChatClick = ::startNewChat,
                            showAutoBrowserButton = hasActiveAutoBrowserSession,
                            onAutoBrowserClick = { showAutoBrowserPreview = !showAutoBrowserPreview }
                        )
                    }
                }
            }

            if (hasActiveAutoBrowserSession) {
                val session = requireNotNull(activeAutoBrowserSession)
                val webViewState =
                    autoBrowserWebViewStates.getOrPut(session.conversationId) { AppHtmlWebViewState() }
                val isExpanded = showAutoBrowserPreview
                val desktopViewportWidth = 1366
                val desktopViewportHeight = 768
                val desktopAspectRatio = desktopViewportWidth.toFloat() / desktopViewportHeight.toFloat()
                val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                val expandedPreviewWidth = (screenWidthDp * 0.84f).coerceIn(260.dp, 360.dp)
                val expandedPreviewHeight =
                    ((expandedPreviewWidth / desktopAspectRatio) + 40.dp).coerceIn(188.dp, 290.dp)
                val previewWidth = if (isExpanded) expandedPreviewWidth else 1.dp
                val previewHeight = if (isExpanded) expandedPreviewHeight else 1.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = statusBarTopPadding + topBarHeightDp + 10.dp, end = 16.dp)
                        .width(previewWidth)
                        .height(previewHeight)
                        .graphicsLayer { alpha = if (isExpanded) 1f else 0f }
                        .shadow(
                            elevation = if (isExpanded) 16.dp else 0.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.16f),
                            spotColor = Color.Black.copy(alpha = 0.16f)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF8F8F8))
                        .zIndex(14f)
                ) {
                    if (isExpanded) {
                        Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.06f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Desktop ${desktopViewportWidth}x${desktopViewportHeight}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White, CircleShape)
                                    .pressableScale(pressedScale = 0.94f, onClick = { showAutoBrowserPreview = false })
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Close,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                        ) {
                            AppHtmlWebView(
                                modifier = Modifier.fillMaxSize(),
                                state = webViewState,
                                contentSignature = "autobrowser:${session.sessionId}:${session.renderNonce}:${session.currentUrl}",
                                url = session.currentUrl,
                                enableCookies = true,
                                enableThirdPartyCookies = true,
                                desktopMode = true,
                                desktopViewportWidth = desktopViewportWidth,
                                desktopViewportHeight = desktopViewportHeight,
                                desktopScaleToFit = true,
                                onRuntimeIssue = { issue ->
                                    updateAutoBrowserSession(session.conversationId) { current ->
                                        current?.copy(lastError = issue)
                                    }
                                    appendAutoBrowserHistory(session.conversationId, "Runtime issue: $issue")
                                },
                                onPageFinished = { webView ->
                                    autoBrowserWebViewRefs[session.conversationId] = webView
                                    val latestUrl = webView.url?.trim().orEmpty().ifBlank { session.currentUrl }
                                    val title = webView.title?.trim().orEmpty()
                                    updateAutoBrowserSession(session.conversationId) { current ->
                                        current?.copy(
                                            currentUrl = latestUrl,
                                            pageTitle = title,
                                            lastPageFinishedAt = System.currentTimeMillis()
                                        )
                                    }
                                }
                            )
                        }
                        }
                    } else {
                        AppHtmlWebView(
                            modifier = Modifier.fillMaxSize(),
                            state = webViewState,
                            contentSignature = "autobrowser:${session.sessionId}:${session.renderNonce}:${session.currentUrl}",
                            url = session.currentUrl,
                            enableCookies = true,
                            enableThirdPartyCookies = true,
                            desktopMode = true,
                            desktopViewportWidth = desktopViewportWidth,
                            desktopViewportHeight = desktopViewportHeight,
                            desktopScaleToFit = true,
                            onRuntimeIssue = { issue ->
                                updateAutoBrowserSession(session.conversationId) { current ->
                                    current?.copy(lastError = issue)
                                }
                            },
                            onPageFinished = { webView ->
                                autoBrowserWebViewRefs[session.conversationId] = webView
                            }
                        )
                    }
                }
            }

            // 底部输入框区域 - 固定在底部（高度动态，支持多行/工具标签）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = inputBottomInset)
                    .zIndex(5f)
            ) {
                Box(
                    modifier = Modifier
                        .onSizeChanged { bottomBarHeightPx = it.height }
                ) {
                    BottomInputArea(
                        selectedTool = selectedTool,
                        mcpSelectedCount = selectedMcpServerIds.size,
                        webSearchEngine = webSearchConfig.engine,
                        attachments = imageAttachments,
                        onRemoveAttachment = { index ->
                            if (index < 0 || index >= imageAttachments.size) return@BottomInputArea
                            val updated = imageAttachments.toMutableList()
                            updated.removeAt(index)
                            imageAttachments = updated.toList()
                        },
                        onToolToggle = {
                            if (showToolMenu) {
                                showToolMenu = false
                                toolMenuPage = ToolMenuPage.Tools
                                return@BottomInputArea
                            }
                            if (imeVisible) {
                                hideKeyboardIfNeeded(force = true)
                                scope.launch {
                                    delay(180)
                                    showToolMenu = true
                                    toolMenuPage = ToolMenuPage.Tools
                                }
                            } else {
                                showToolMenu = true
                                toolMenuPage = ToolMenuPage.Tools
                            }
                        },
                        onClearTool = { selectedTool = null },
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        onSend = ::sendMessage,
                        onStopStreaming = ::stopStreaming,
                        sendAllowed = activeGroupChatConfig != null || selectedTool == "autosoul" || !defaultChatModelId.isNullOrBlank(),
                        sendBusy = sendInFlight,
                        isStreaming = isStreaming,
                        imeVisible = imeVisible,
                        onInputFocusChanged = { focused ->
                            inputFieldFocused = focused
                            if (focused && showToolMenu) {
                                showToolMenu = false
                                toolMenuPage = ToolMenuPage.Tools
                            }
                        },
                        actionActiveColor = accentPalette.actionColor
                    )
                }
            }

            MentionPickerFloatingPanel(
                visible = showMentionPicker && !showToolMenu,
                mentionCandidates = visibleMentionCandidates,
                onMentionSelect = { candidate ->
                    messageText = replaceTrailingMentionWithToken(messageText, candidate.token)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = inputBottomInset + bottomBarHeightDp + 8.dp)
                    .fillMaxWidth()
                    .padding(start = 74.dp, end = 18.dp)
                    .zIndex(12f)
            )

            // 底部工具面板（覆盖在输入框上方）
            ToolMenuPanel(
                visible = showToolMenu,
                page = toolMenuPage,
                webSearchEngine = webSearchConfig.engine,
                mcpLoading = mcpToolPickerLoading,
                mcpErrorText = mcpToolPickerError,
                mcpServers = mcpServerPickerItems,
                selectedMcpServerIds = selectedMcpServerIds,
                onToggleMcpServer = { serverId, enabled ->
                    val updated =
                        if (enabled) {
                            selectedMcpServerIds + serverId
                        } else {
                            selectedMcpServerIds - serverId
                        }
                    selectedMcpServerIds = updated
                },
                onMcpPageChange = { page ->
                    toolMenuPage = page
                    if (page == ToolMenuPage.McpServers) {
                        openMcpToolPicker()
                    }
                },
                onOpenMcpSettings = {
                    showToolMenu = false
                    toolMenuPage = ToolMenuPage.Tools
                    navController.navigate("mcp")
                },
                modifier = Modifier.zIndex(20f),
                onDismiss = {
                    showToolMenu = false
                    toolMenuPage = ToolMenuPage.Tools
                },
                onToolSelect = { tool ->
                    when (tool) {
                        "camera" -> {
                            showToolMenu = false
                            toolMenuPage = ToolMenuPage.Tools
                            hideKeyboardIfNeeded(force = true)
                            cameraLauncher.launch(null)
                        }
                        "photos" -> {
                            showToolMenu = false
                            toolMenuPage = ToolMenuPage.Tools
                            hideKeyboardIfNeeded(force = true)
                            photoPickerLauncher.launch("image/*")
                        }
                        "mcp" -> {
                            openMcpToolPicker()
                        }
                        else -> {
                            selectedTool = tool
                            showToolMenu = false
                            toolMenuPage = ToolMenuPage.Tools
                        }
                    }
                }
            )

            if (showChatModelPicker) {
                ModalBottomSheet(
                    onDismissRequest = { showChatModelPicker = false },
                    sheetState = chatModelPickerState,
                    sheetGesturesEnabled = false,
                    containerColor = Surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = null
                ) {
                    ChatModelPickerSheetContent(
                        groupedModels = chatModelGroups,
                        selectedModelId = defaultChatModelId,
                        thinkingEnabled = chatThinkingEnabled,
                        onToggleThinking = { enabled ->
                            scope.launch { repository.setChatThinkingEnabled(enabled) }
                        },
                        onDismissRequest = { showChatModelPicker = false },
                        onSelectModel = { model ->
                            scope.launch {
                                repository.setDefaultChatModelId(model.id)
                            }
                            showChatModelPicker = false
                        }
                    )
                }
            }

            // 获取当前对话的实时thinking内容
            val currentMessageThinking = localMessages.lastOrNull { it.reasoning != null }?.reasoning
            val displayThinkingText = if (showThinkingSheet && currentMessageThinking != null) {
                currentMessageThinking
            } else thinkingSheetText

            if (showThinkingSheet && !displayThinkingText.isNullOrBlank()) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showThinkingSheet = false
                        thinkingSheetText = null
                    },
                    sheetState = thinkingSheetState,
                    containerColor = ThinkingBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { AppSheetDragHandle(backgroundColor = ThinkingBackground) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.67f)
                            .background(ThinkingBackground)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.thinking_label),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MarkdownText(
                                markdown = displayThinkingText.orEmpty(),
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            val activeTag = remember(localMessages, tagSheetMessageId, tagSheetTagId) {
                val messageId = tagSheetMessageId?.trim().orEmpty()
                val tagId = tagSheetTagId?.trim().orEmpty()
                if (messageId.isBlank() || tagId.isBlank()) {
                    null
                } else {
                    localMessages.firstOrNull { it.id == messageId }
                        ?.tags
                        .orEmpty()
                        .firstOrNull { it.id == tagId }
                }
            }

            if (!tagSheetTagId.isNullOrBlank() && activeTag == null) {
                LaunchedEffect(localMessages, tagSheetTagId, tagSheetMessageId) {
                    tagSheetMessageId = null
                    tagSheetTagId = null
                }
            }

            activeTag?.let { tag ->
                ModalBottomSheet(
                    onDismissRequest = {
                        tagSheetMessageId = null
                        tagSheetTagId = null
                    },
                    sheetState = tagSheetState,
                    containerColor = ThinkingBackground,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { AppSheetDragHandle(backgroundColor = ThinkingBackground) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.67f)
                            .background(ThinkingBackground)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = if (tag.kind == "mcp") "Details" else tag.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (tag.kind == "mcp") {
                                McpTagDetailCard(tag = tag)
                            } else {
                                MarkdownText(
                                    markdown = tag.content,
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val activeAppWorkspaceTag = remember(localMessages, appWorkspaceMessageId, appWorkspaceTagId) {
                val messageId = appWorkspaceMessageId?.trim().orEmpty()
                val tagId = appWorkspaceTagId?.trim().orEmpty()
                if (messageId.isBlank() || tagId.isBlank()) {
                    null
                } else {
                    localMessages.firstOrNull { it.id == messageId }
                        ?.tags
                        .orEmpty()
                        .firstOrNull { it.id == tagId && it.kind == "app_dev" }
                }
            }
            if (!appWorkspaceTagId.isNullOrBlank() && activeAppWorkspaceTag == null) {
                LaunchedEffect(localMessages, appWorkspaceTagId, appWorkspaceMessageId) {
                    appWorkspaceMessageId = null
                    appWorkspaceTagId = null
                }
            }

            activeAppWorkspaceTag?.let { tag ->
                AppDevWorkspaceScreen(
                    tag = tag,
                    onDismiss = {
                        appWorkspaceMessageId = null
                        appWorkspaceTagId = null
                    }
                )
            }
        }
    }
}
private data class GroupDispatchOutcome(
    val replies: List<Message>,
    val nextRoundRobinCursor: Int? = null,
    val warning: String? = null
)

private fun buildCurrentSystemTimeText(nowMs: Long = System.currentTimeMillis()): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    formatter.timeZone = java.util.TimeZone.getDefault()
    val zone = formatter.timeZone.id.trim()
    val value = formatter.format(java.util.Date(nowMs))
    return if (zone.isBlank()) value else "$value ($zone)"
}

private data class GroupResponder(
    val bot: com.zionchat.app.data.BotConfig,
    val model: ModelConfig
)

private fun resolveEnabledModelForBot(
    bot: com.zionchat.app.data.BotConfig,
    allModels: List<ModelConfig>
): ModelConfig? {
    val key = bot.defaultModelId?.trim().orEmpty()
    if (key.isBlank()) return null
    return allModels.firstOrNull { it.enabled && it.id == key }
        ?: allModels.firstOrNull { it.enabled && extractRemoteModelId(it.id) == key }
}

private fun buildBotMentionToken(bot: com.zionchat.app.data.BotConfig): String {
    return bot.name.trim().replace(Regex("\\s+"), "_")
}

private fun splitGroupReplySegments(raw: String): List<String> {
    return raw.split('¦')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private suspend fun selectDynamicGroupResponders(
    chatApiClient: ChatApiClient,
    providerAuthManager: com.zionchat.app.data.ProviderAuthManager,
    group: com.zionchat.app.data.GroupChatConfig,
    responders: List<GroupResponder>,
    allModels: List<ModelConfig>,
    providers: List<ProviderConfig>,
    conversationMessages: List<Message>,
    userMessage: Message,
    systemTimeText: String
): List<GroupResponder> {
    if (responders.isEmpty()) return emptyList()
    val coordinatorId = group.dynamicCoordinatorModelId?.trim().orEmpty()
    if (coordinatorId.isBlank()) return responders.take(1)

    val coordinatorModel =
        allModels.firstOrNull { it.enabled && it.id == coordinatorId }
            ?: allModels.firstOrNull { it.enabled && extractRemoteModelId(it.id) == coordinatorId }
            ?: return responders.take(1)
    val provider =
        coordinatorModel.providerId?.let { pid -> providers.firstOrNull { it.id == pid } }
            ?: return responders.take(1)
    if (provider.apiUrl.isBlank() || provider.apiKey.isBlank()) return responders.take(1)
    val resolvedProvider = runCatching { providerAuthManager.ensureValidProvider(provider) }.getOrNull() ?: return responders.take(1)

    val optionsText =
        responders.joinToString("\n") { responder ->
            val token = buildBotMentionToken(responder.bot)
            "- bot_id=${responder.bot.id} | token=@$token | bot_name=${responder.bot.name} | model_name=${responder.model.displayName}"
        }
    val conversationContextText =
        conversationMessages
            .takeLast(24)
            .mapNotNull { msg ->
                val clean =
                    stripMcpTagMarkers(msg.content)
                        .replace(Regex("\\s+"), " ")
                        .trim()
                if (clean.isBlank()) {
                    null
                } else {
                    val roleLabel =
                        when (msg.role.trim().lowercase()) {
                            "assistant" -> msg.speakerName?.trim()?.takeIf { it.isNotBlank() } ?: "assistant"
                            "user" -> "user"
                            "system" -> "system"
                            else -> msg.role.trim().ifBlank { "message" }
                        }
                    "[$roleLabel] ${clean.take(280)}"
                }
            }
            .joinToString("\n")
            .ifBlank { "(no_context)" }
    val selectorPrompt =
        buildString {
            appendLine("你是群聊调度器，需要基于完整上下文，从候选 Bot 中选择最适合回复本条消息的 1~2 个。")
            append("当前系统时间：")
            appendLine(systemTimeText)
            appendLine("请只返回 JSON 数组，数组元素必须是 bot_id、token 或 bot_name。")
            appendLine("候选 Bot：")
            appendLine(optionsText)
            appendLine("最近对话上下文（按时间顺序）：")
            appendLine(conversationContextText)
            appendLine("当前用户消息：")
            appendLine(userMessage.content.trim().ifBlank { "(empty_user_message)" })
        }.trim()

    val schedulerReply =
        chatApiClient.chatCompletions(
            provider = resolvedProvider,
            modelId = extractRemoteModelId(coordinatorModel.id).ifBlank { coordinatorModel.id },
            messages = listOf(Message(role = "user", content = selectorPrompt)),
            extraHeaders = coordinatorModel.headers,
            reasoningEffort = "none",
            enableThinking = false,
            maxTokens = 1024
        ).getOrNull().orEmpty()

    val rawCandidates = linkedSetOf<String>()
    parseJsonStringArray(schedulerReply).forEach { rawCandidates += it }
    extractMentionTokens(schedulerReply).forEach { token -> rawCandidates += token }
    if (rawCandidates.isEmpty()) return responders.take(1)

    val normalized = rawCandidates.map(::normalizeMentionLookup).toSet()
    val selected =
        responders.filter { responder ->
            val aliases =
                setOf(
                    normalizeMentionLookup(responder.bot.id),
                    normalizeMentionLookup(responder.bot.name),
                    normalizeMentionLookup(buildBotMentionToken(responder.bot))
                )
            aliases.any { normalized.contains(it) }
        }.take(2)
    return if (selected.isEmpty()) responders.take(1) else selected
}

private suspend fun runGroupChatDispatch(
    chatApiClient: ChatApiClient,
    providerAuthManager: com.zionchat.app.data.ProviderAuthManager,
    group: com.zionchat.app.data.GroupChatConfig,
    userMessage: Message,
    conversationMessages: List<Message>,
    allBots: List<com.zionchat.app.data.BotConfig>,
    allModels: List<ModelConfig>,
    providers: List<ProviderConfig>,
    customInstructions: String,
    systemTimeText: String
): GroupDispatchOutcome {
    val memberBots =
        group.memberBotIds.mapNotNull { botId ->
            allBots.firstOrNull { it.id == botId }
        }
    if (memberBots.isEmpty()) {
        return GroupDispatchOutcome(
            replies = emptyList(),
            warning = "群聊成员为空，请先在 Group 设置里添加 Bot 好友。"
        )
    }

    val responders =
        memberBots.mapNotNull { bot ->
            val model = resolveEnabledModelForBot(bot, allModels) ?: return@mapNotNull null
            GroupResponder(bot = bot, model = model)
        }

    if (responders.isEmpty()) {
        return GroupDispatchOutcome(
            replies = emptyList(),
            warning = "群聊成员模型未配置，请为 Bot 好友设置默认模型。"
        )
    }

    val mentionKeys =
        extractMentionTokens(userMessage.content)
            .map(::normalizeMentionLookup)
            .toSet()
    val mentionedResponders =
        responders.filter { responder ->
            val aliases =
                setOf(
                    normalizeMentionLookup(responder.bot.id),
                    normalizeMentionLookup(responder.bot.name),
                    normalizeMentionLookup(buildBotMentionToken(responder.bot))
                )
            aliases.any { mentionKeys.contains(it) }
        }
    val strategyKey = group.strategy.trim().lowercase()

    val scheduledResponders =
        when (strategyKey) {
            "round_robin" -> {
                val responderCount = responders.size
                val startIndex =
                    if (responderCount == 0) {
                        0
                    } else {
                        ((group.roundRobinCursor % responderCount) + responderCount) % responderCount
                    }
                List(responderCount) { offset ->
                    responders[(startIndex + offset) % responderCount]
                }
            }
            else -> {
                selectDynamicGroupResponders(
                    chatApiClient = chatApiClient,
                    providerAuthManager = providerAuthManager,
                    group = group,
                    responders = responders,
                    allModels = allModels,
                    providers = providers,
                    conversationMessages = conversationMessages,
                    userMessage = userMessage,
                    systemTimeText = systemTimeText
                )
            }
        }

    val selectedResponders =
        if (strategyKey == "round_robin") {
            if (scheduledResponders.isEmpty()) responders else scheduledResponders
        } else {
            val ordered = LinkedHashMap<String, GroupResponder>()
            mentionedResponders.forEach { responder -> ordered[responder.bot.id] = responder }
            scheduledResponders.forEach { responder -> ordered[responder.bot.id] = responder }
            if (ordered.isEmpty()) {
                val first = responders.first()
                ordered[first.bot.id] = first
            }
            ordered.values.toList()
        }
    if (selectedResponders.isEmpty()) {
        return GroupDispatchOutcome(
            replies = emptyList(),
            warning = "群聊暂无可用回复成员。"
        )
    }

    val cleanHistory =
        conversationMessages.takeLast(16).mapNotNull { msg ->
            val clean = stripMcpTagMarkers(msg.content).trim()
            if (clean.isBlank()) {
                null
            } else {
                msg.copy(content = clean, reasoning = null, tags = null, attachments = null)
            }
        }
    val rollingHistory = cleanHistory.toMutableList()

    val replies = mutableListOf<Message>()
    selectedResponders.forEach { responder ->
        val bot = responder.bot
        val model = responder.model
        val provider = model.providerId?.let { pid -> providers.firstOrNull { it.id == pid } }
        if (provider == null || provider.apiUrl.isBlank() || provider.apiKey.isBlank()) {
            val fallbackText = "暂时无法回复（模型提供方未配置）。"
            val batchId = java.util.UUID.randomUUID().toString()
            val fallbackReply = Message(
                role = "assistant",
                content = fallbackText,
                speakerBotId = bot.id,
                speakerName = bot.name,
                speakerAvatarUri = bot.avatarUri,
                speakerAvatarAssetName = bot.avatarAssetName,
                speakerBatchId = batchId
            )
            replies += fallbackReply
            rollingHistory += fallbackReply.copy(reasoning = null, tags = null, attachments = null)
            return@forEach
        }
        val resolvedProvider = runCatching { providerAuthManager.ensureValidProvider(provider) }.getOrElse {
            val fallbackText = "暂时无法回复（鉴权失败）。"
            val batchId = java.util.UUID.randomUUID().toString()
            val fallbackReply = Message(
                role = "assistant",
                content = fallbackText,
                speakerBotId = bot.id,
                speakerName = bot.name,
                speakerAvatarUri = bot.avatarUri,
                speakerAvatarAssetName = bot.avatarAssetName,
                speakerBatchId = batchId
            )
            replies += fallbackReply
            rollingHistory += fallbackReply.copy(reasoning = null, tags = null, attachments = null)
            return@forEach
        }

        val mustReply = mentionedResponders.any { it.bot.id == bot.id }
        val systemPrompt =
            buildString {
                appendLine("你是群聊中的 Bot 成员。")
                append("你的 Bot 名称：")
                appendLine(bot.name)
                append("你的模型：")
                appendLine(model.displayName)
                append("当前系统时间：")
                appendLine(systemTimeText)
                if (customInstructions.isNotBlank()) {
                    appendLine("用户偏好：")
                    appendLine(customInstructions)
                }
                if (mustReply) {
                    appendLine("用户本条消息明确@了你，你必须给出回复。")
                }
                appendLine("请分句回答，并使用字符 ¦ 作为分割。")
                appendLine("示例：第一句¦第二句¦第三句")
                appendLine("不要输出工具调用标签。")
            }.trim()

        val requestMessages = mutableListOf<Message>()
        requestMessages += Message(role = "system", content = systemPrompt)
        requestMessages += rollingHistory
        if (rollingHistory.lastOrNull()?.id != userMessage.id) {
            requestMessages += userMessage
        }
        val replyText =
            chatApiClient.chatCompletions(
                provider = resolvedProvider,
                modelId = extractRemoteModelId(model.id).ifBlank { model.id },
                messages = requestMessages,
                extraHeaders = model.headers,
                reasoningEffort = "none",
                enableThinking = false,
                maxTokens = 1536
            ).getOrElse { error ->
                "暂时无法回复：${error.message?.trim().orEmpty().ifBlank { "unknown_error" }}"
            }.trim()

        val segments = splitGroupReplySegments(replyText).ifEmpty { listOf(replyText.ifBlank { "(empty)" }) }
        val batchId = java.util.UUID.randomUUID().toString()
        segments.forEach { segment ->
            val reply =
                Message(
                    role = "assistant",
                    content = segment,
                    speakerBotId = bot.id,
                    speakerName = bot.name,
                    speakerAvatarUri = bot.avatarUri,
                    speakerAvatarAssetName = bot.avatarAssetName,
                    speakerBatchId = batchId
                )
            replies += reply
            rollingHistory += reply.copy(reasoning = null, tags = null, attachments = null)
        }
    }

    val nextCursor =
        if (strategyKey == "round_robin" && responders.isNotEmpty()) {
            val responderCount = responders.size
            val current =
                ((group.roundRobinCursor % responderCount) + responderCount) % responderCount
            (current + 1) % responderCount
        } else {
            null
        }
    return GroupDispatchOutcome(replies = replies, nextRoundRobinCursor = nextCursor, warning = null)
}

