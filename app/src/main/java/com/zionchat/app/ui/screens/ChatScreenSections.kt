package com.zionchat.app.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
import com.zionchat.app.autosoul.runtime.AutoSoulAppPackages
import com.zionchat.app.autosoul.runtime.AutoSoulScriptParser
import com.zionchat.app.data.AppRepository
import com.zionchat.app.data.AppAutomationTask
import com.zionchat.app.data.ChatApiClient
import com.zionchat.app.data.Conversation
import com.zionchat.app.data.GroupChatConfig
import com.zionchat.app.data.HttpHeader
import com.zionchat.app.data.Message
import com.zionchat.app.data.MessageAttachment
import com.zionchat.app.data.MessageTag
import com.zionchat.app.data.MemoryItem
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.McpClient
import com.zionchat.app.data.McpConfig
import com.zionchat.app.data.McpToolCall
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.RuntimeShellPlugin
import com.zionchat.app.data.SavedApp
import com.zionchat.app.data.WebSearchConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.data.isLikelyVisionModel
import com.zionchat.app.ui.components.TopFadeScrim
import com.zionchat.app.ui.components.AppSheetDragHandle
import com.zionchat.app.ui.components.AppHtmlWebView
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dynamic grid layout for message attachments
 * - 1-3 images: single row
 * - 4 images: 2x2 grid
 * - 9 images: 3x3 grid
 * - Other counts: adaptive grid
 */
@Composable
internal fun AttachmentGrid(
    attachments: List<MessageAttachment>,
    modifier: Modifier = Modifier
) {
    val count = attachments.size
    val columns = when {
        count <= 3 -> count
        count == 4 -> 2
        count <= 6 -> 3
        count <= 9 -> 3
        else -> 4
    }
    val rows = (count + columns - 1) / columns

    // Calculate image size based on count
    val imageSize = when {
        count == 1 -> 200.dp
        count <= 3 -> 120.dp
        count <= 4 -> 100.dp
        count <= 6 -> 90.dp
        else -> 80.dp
    }

    val spacing = 6.dp

    // Column wrap content - alignment controlled by parent
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        for (row in 0 until rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                val startIndex = row * columns
                val endIndex = minOf(startIndex + columns, count)
                for (index in startIndex until endIndex) {
                    val attachment = attachments[index]
                    Box(
                        modifier = Modifier
                            .size(imageSize)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GrayLighter)
                    ) {
                        AsyncImage(
                            model = attachment.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: Message,
    conversationId: String?,
    isStreaming: Boolean = false,
    isThinkingActive: Boolean = false,
    showToolbar: Boolean = true,
    groupMode: Boolean = false,
    showGroupSpeaker: Boolean = false,
    onShowReasoning: (String) -> Unit,
    onShowTag: (messageId: String, tagId: String) -> Unit,
    onDownloadAppDevTag: (messageId: String, tag: MessageTag) -> Unit = { _, _ -> },
    userBubbleColor: Color = UserMessageBubble,
    userBubbleSecondaryColor: Color? = null,
    userBubbleTextColor: Color = TextPrimary,
    onEdit: () -> Unit,
    onDelete: (conversationId: String, messageId: String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val isUser = message.role == "user"
    if (isUser) {
        val bubbleShape = RoundedCornerShape(18.dp)
        val attachments = message.attachments.orEmpty()

        // User message (right aligned)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // Show attachments above the bubble
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AttachmentGrid(
                        attachments = attachments,
                        modifier = Modifier
                            .padding(start = 60.dp, bottom = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 60.dp)
                        .then(
                            if (userBubbleSecondaryColor != null) {
                                Modifier
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(userBubbleColor, userBubbleSecondaryColor)
                                        ),
                                        shape = bubbleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.08f),
                                        shape = bubbleShape
                                    )
                            } else {
                                Modifier.background(userBubbleColor, bubbleShape)
                            }
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { },
                            onLongClick = { showMenu = true }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    MentionAwareMessageText(
                        content = message.content,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = userBubbleTextColor
                        ),
                        mentionColor = Color(0xFF007AFF)
                    )
                }
            }
        }
    } else {
        // Assistant message (left aligned)
        val legacySpeaker = remember(message.content) { parseLegacyGroupSpeaker(message.content) }
        val resolvedSpeakerName = message.speakerName?.trim()?.takeIf { it.isNotBlank() } ?: legacySpeaker?.first
        val resolvedSpeakerAvatarUri = message.speakerAvatarUri?.trim()?.takeIf { it.isNotBlank() }
        val resolvedSpeakerAvatarAsset = message.speakerAvatarAssetName?.trim()?.takeIf { it.isNotBlank() }
        val displayAssistantContent =
            if (message.speakerName.isNullOrBlank() && legacySpeaker != null) {
                legacySpeaker.second
            } else {
                message.content
            }
        val groupSpeakerEnabled = groupMode && !resolvedSpeakerName.isNullOrBlank()
        val assistantContentModifier =
            if (isStreaming) {
                Modifier
            } else {
                Modifier.animateContentSize(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
            }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(assistantContentModifier)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { },
                    onLongClick = { showMenu = true }
                )
        ) {
            if (groupSpeakerEnabled && showGroupSpeaker) {
                GroupSpeakerAvatar(
                    avatarUri = resolvedSpeakerAvatarUri,
                    avatarAssetName = resolvedSpeakerAvatarAsset,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 2.dp)
                )
            }
            val assistantColumnPaddingStart = if (groupSpeakerEnabled) 44.dp else 0.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = assistantColumnPaddingStart)
            ) {
            if (groupSpeakerEnabled && showGroupSpeaker) {
                Text(
                    text = resolvedSpeakerName.orEmpty(),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            val reasoningText = message.reasoning?.trim().orEmpty()
            if (reasoningText.isNotBlank()) {
                val thinkingTint =
                    if (isThinkingActive) {
                        val thinkingTransition = rememberInfiniteTransition(label = "thinking_label_pulse")
                        thinkingTransition.animateColor(
                            initialValue = Color(0xFF1C1C1E),
                            targetValue = Color(0xFFCFCFD4),
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(720, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                            label = "thinking_label_color"
                        ).value
                    } else {
                        Color(0xFF1C1C1E)
                    }
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .pressableScale(
                            pressedScale = 0.98f,
                            onClick = { onShowReasoning(reasoningText) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.thinking_label),
                        fontSize = 15.sp,
                        fontFamily = SourceSans3,
                        fontWeight = FontWeight.SemiBold,
                        color = thinkingTint
                    )
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = thinkingTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            val contentSegments = remember(displayAssistantContent) { splitContentByMcpTagMarkers(displayAssistantContent) }
            val tagsById = remember(message.tags) { message.tags.orEmpty().associateBy { it.id } }
            val inlineTagIds = remember(contentSegments) {
                contentSegments.mapNotNull { it.tagId }.toSet()
            }
            val orderedTags = remember(message.tags, inlineTagIds) {
                val base =
                    message.tags.orEmpty()
                    .filterNot { tag -> inlineTagIds.contains(tag.id) }
                    .sortedBy { it.createdAt }
                val dedupFromTail = mutableListOf<MessageTag>()
                var keptAutoSoulTag = false
                base.asReversed().forEach { tag ->
                    if (isAutoSoulMcpTag(tag)) {
                        if (keptAutoSoulTag) return@forEach
                        keptAutoSoulTag = true
                    }
                    dedupFromTail += tag
                }
                dedupFromTail.asReversed()
            }
            val topMemoryTags = remember(orderedTags) {
                orderedTags.filter { isMemoryTag(it) }
            }
            val trailingTags = remember(orderedTags) {
                orderedTags.filterNot { isMemoryTag(it) }
            }

            topMemoryTags.forEach { tag ->
                MessageTagRow(
                    tag = tag,
                    messageId = message.id,
                    onShowTag = onShowTag,
                    onDownloadAppDevTag = onDownloadAppDevTag
                )
            }

            if (contentSegments.isEmpty()) {
                AssistantContentBlock(
                    groupMode = groupSpeakerEnabled,
                    markdown = stripMcpTagMarkers(displayAssistantContent)
                )
            } else {
                val bodyModifier =
                    if (groupSpeakerEnabled) {
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    } else {
                        Modifier
                    }
                Column(modifier = bodyModifier) {
                    contentSegments.forEach { segment ->
                        val text = segment.text
                        val tagId = segment.tagId
                        if (!text.isNullOrBlank()) {
                            MarkdownText(
                                markdown = text,
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    color = TextPrimary
                                )
                            )
                        } else if (!tagId.isNullOrBlank()) {
                            val tag = tagsById[tagId]
                            if (tag != null) {
                                MessageTagRow(
                                    tag = tag,
                                    messageId = message.id,
                                    onShowTag = onShowTag,
                                    onDownloadAppDevTag = onDownloadAppDevTag
                                )
                            }
                        }
                    }
                }
            }

            trailingTags.forEach { tag ->
                MessageTagRow(
                    tag = tag,
                    messageId = message.id,
                    onShowTag = onShowTag,
                    onDownloadAppDevTag = onDownloadAppDevTag
                )
            }

            // Message action buttons (assistant only) - use AnimatedVisibility for smooth transition
            androidx.compose.animation.AnimatedVisibility(
                visible = !isStreaming && showToolbar,
                enter = fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    ActionButton(
                        icon = AppIcons.Copy,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(stripMcpTagMarkers(displayAssistantContent)))
                        }
                    )
                    ActionButton(icon = AppIcons.Edit, onClick = onEdit)
                    ActionButton(icon = AppIcons.Volume, onClick = { })
                    ActionButton(
                        icon = AppIcons.Refresh,
                        onClick = { /* Regenerate */ }
                    )
                    ActionButton(icon = AppIcons.More, onClick = { showMenu = true })
                }
            }
        }
    }
    }

    // Long-press menu
    if (showMenu) {
        MessageOptionsDialog(
            isUser = isUser,
            onCopy = {
                clipboardManager.setText(AnnotatedString(stripMcpTagMarkers(message.content)))
                showMenu = false
            },
            onEdit = {
                onEdit()
                showMenu = false
            },
            onDelete = {
                val convoId = conversationId
                if (!convoId.isNullOrBlank()) {
                    onDelete(convoId, message.id)
                }
                showMenu = false
            },
            onDismiss = { showMenu = false }
        )
    }
}

@Composable
private fun MentionAwareMessageText(
    content: String,
    textStyle: TextStyle,
    mentionColor: Color
) {
    val mentionRegex = remember { Regex("""@[\p{L}\p{N}_\-.]+""") }
    val parsed = remember(content, mentionColor) {
        val builder = AnnotatedString.Builder(content)
        mentionRegex.findAll(content).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start in 0 until end && end <= content.length) {
                builder.addStyle(
                    SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold),
                    start = start,
                    end = end
                )
            }
        }
        builder.toAnnotatedString()
    }
    Text(text = parsed, style = textStyle)
}

@Composable
private fun GroupSpeakerAvatar(
    avatarUri: String?,
    avatarAssetName: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(GrayLighter),
        contentAlignment = Alignment.Center
    ) {
        when {
            !avatarUri.isNullOrBlank() -> {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            !avatarAssetName.isNullOrBlank() -> {
                AsyncImage(
                    model = "file:///android_asset/avatars/$avatarAssetName",
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
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantContentBlock(
    groupMode: Boolean,
    markdown: String
) {
    val contentModifier =
        if (groupMode) {
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        } else {
            Modifier
        }
    Box(modifier = contentModifier) {
        MarkdownText(
            markdown = markdown,
            textStyle = TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = TextPrimary
            )
        )
    }
}

private fun parseLegacyGroupSpeaker(raw: String): Pair<String, String>? {
    val content = raw.trimStart()
    if (!content.startsWith("[")) return null
    val end = content.indexOf(']')
    if (end <= 1) return null
    val speaker = content.substring(1, end).trim()
    if (speaker.isBlank()) return null
    val body = content.substring(end + 1).trimStart()
    return speaker to body
}

@Composable
internal fun MessageTagRow(
    tag: MessageTag,
    messageId: String,
    onShowTag: (messageId: String, tagId: String) -> Unit,
    onDownloadAppDevTag: (messageId: String, tag: MessageTag) -> Unit
) {
    if (tag.kind == "app_dev") {
        AppDevToolTagCard(
            tag = tag,
            onClick = { onShowTag(messageId, tag.id) },
            onDownloadClick = { onDownloadAppDevTag(messageId, tag) }
        )
        return
    }

    val tagRunning = isTagRunning(tag)
    val isAutoSoulTag = remember(tag.kind, tag.title, tag.content) { isAutoSoulMcpTag(tag) }
    val isAutoBrowserTag = remember(tag.kind, tag.title, tag.content) { isAutoBrowserMcpTag(tag) }
    val toolName = remember(tag.title) {
        tag.title.trim().takeIf { it.isNotBlank() && !it.equals("tool", ignoreCase = true) }
    }
    val displayText =
        if (tag.kind == "mcp") {
            if (isAutoSoulTag) {
                stringResource(R.string.settings_item_autosoul)
            } else if (isAutoBrowserTag) {
                tag.title.trim().ifBlank { stringResource(R.string.settings_item_autobrowser) }
            } else if (toolName == null) {
                stringResource(R.string.tool_label)
            } else {
                stringResource(R.string.tool_label_with_name, stringResource(R.string.tool_label), toolName)
            }
        } else {
            tag.title
        }
    Row(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
            .pressableScale(
                pressedScale = 0.98f,
                onClick = { onShowTag(messageId, tag.id) }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (tag.kind == "mcp") {
            if (isAutoSoulTag) {
                Icon(
                    painter = rememberResourceDrawablePainter(R.drawable.ic_autosoul),
                    contentDescription = null,
                    tint = ThinkingLabelColor,
                    modifier = Modifier.size(14.dp)
                )
            } else if (isAutoBrowserTag) {
                Icon(
                    imageVector = AppIcons.AutoBrowserCompass,
                    contentDescription = null,
                    tint = ThinkingLabelColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = displayText,
            fontSize = 15.sp,
            fontFamily = SourceSans3,
            fontWeight = FontWeight.SemiBold,
            color = ThinkingLabelColor
        )
        if (tagRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.7.dp,
                color = ThinkingLabelColor
            )
        } else {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = ThinkingLabelColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

internal fun isMemoryTag(tag: MessageTag): Boolean {
    return tag.kind.trim().equals("memory", ignoreCase = true)
}

internal fun isAutoSoulMcpTag(tag: MessageTag): Boolean {
    if (tag.kind != "mcp") return false
    val titleHit = tag.title.trim().contains("autosoul", ignoreCase = true)
    if (titleHit) return true
    val detail = parseMcpTagDetail(tag.content)
    val serverHit = detail.server.trim().contains("autosoul", ignoreCase = true)
    if (serverHit) return true
    val toolHit = detail.tool.trim().contains("autosoul", ignoreCase = true)
    return toolHit
}

internal fun isAutoBrowserMcpTag(tag: MessageTag): Boolean {
    if (tag.kind != "mcp") return false
    val title = tag.title.trim()
    if (title.contains("autobrowser", ignoreCase = true)) return true
    if (title.contains("browser", ignoreCase = true)) return true
    val detail = parseMcpTagDetail(tag.content)
    val server = detail.server.trim()
    val tool = detail.tool.trim()
    return server.contains("autobrowser", ignoreCase = true) ||
        tool.contains("autobrowser", ignoreCase = true) ||
        tool.startsWith("autobrowser_", ignoreCase = true)
}

@Composable
internal fun AppDevToolTagCard(
    tag: MessageTag,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val payload = remember(tag.content, tag.title, tag.status) {
        parseAppDevTagPayload(
            content = tag.content,
            fallbackName = tag.title.ifBlank { "App development" },
            fallbackStatus = tag.status
        )
    }
    val statusLower = payload.status.trim().lowercase()
    val hasHtml = payload.html.trim().isNotBlank()
    val isCompleted = statusLower == "success" && hasHtml
    val isRunning = statusLower == "running"
    val showSkeleton = !isCompleted && statusLower != "error"
    val runtimeBusy = payload.runtimeStatus == "queued" || payload.runtimeStatus == "in_progress"
    val reportedProgress = payload.progress.coerceIn(0, 100).toFloat()
    val targetProgress =
        when {
            isCompleted -> 100f
            statusLower == "error" -> reportedProgress
            isRunning -> maxOf(reportedProgress.coerceAtMost(98f), 8f)
            else -> reportedProgress.coerceAtMost(99f)
        }
    val initialProgress = if (isRunning) maxOf(8f, targetProgress) else targetProgress
    var monotonicProgress by remember(tag.id) { mutableFloatStateOf(initialProgress) }
    LaunchedEffect(tag.id, targetProgress, isCompleted, statusLower) {
        when {
            isCompleted -> monotonicProgress = 100f
            statusLower == "error" -> monotonicProgress = targetProgress
            targetProgress > monotonicProgress -> monotonicProgress = targetProgress
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = monotonicProgress.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 240, easing = LinearEasing),
        label = "app_dev_progress_value"
    )
    val progressValue = animatedProgress.roundToInt().coerceIn(0, 100)

    val skeletonBrush =
        if (showSkeleton) {
            val shimmerTransition = rememberInfiniteTransition(label = "app_dev_skeleton_shimmer")
            val skeletonShift by shimmerTransition.animateFloat(
                initialValue = 220f,
                targetValue = -220f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2000, easing = LinearEasing)),
                label = "app_dev_skeleton_shift"
            )
            remember(skeletonShift) {
                Brush.linearGradient(
                    colors = listOf(Color(0xFFE5E7EB), Color(0xFFF3F4F6), Color(0xFFE5E7EB)),
                    start = Offset(skeletonShift, 0f),
                    end = Offset(skeletonShift + 220f, 0f)
                )
            }
        } else {
            SolidColor(Color(0xFFE5E7EB))
        }

    val contributionLevels =
        remember(tag.id, payload.name, payload.subtitle, payload.description) {
            buildGitHubContributionLevels(
                seedKey = "${tag.id}|${payload.name}|${payload.subtitle}|${payload.description}",
                totalCells = 48
            )
        }

    val titleText = payload.name.ifBlank { "CodeMaster" }
    val appIcon = remember(payload.appIcon, payload.name, payload.description) {
        mapLucideNameToAppIconForTag(payload.appIcon) ?: mapLucideNameToAppIconForTag(inferLucideIconFromSignal(payload.name, payload.description)) ?: AppIcons.AppDeveloper
    }
    val subtitleText =
        when {
            statusLower == "error" ->
                payload.error?.trim().orEmpty().ifBlank { "Generation failed. Tap to inspect details and retry." }
            else ->
                payload.subtitle.ifBlank { payload.description }
                    .ifBlank { "Full-stack project with 847 commits" }
        }
    val secondaryTint =
        when {
            isCompleted && runtimeBusy -> Color(0xFF9CA3AF)
            isCompleted -> Color(0xFF4B5563)
            else -> Color(0xFF9CA3AF)
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .pressableScale(pressedScale = 0.985f, onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(width = 1.dp, color = Color(0xFFF3F4F6), shape = RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.14f),
                            spotColor = Color.Black.copy(alpha = 0.18f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF111827), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = appIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (showSkeleton) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.90f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(skeletonBrush)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.70f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(skeletonBrush)
                        )
                    } else {
                        Text(
                            text = titleText,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = subtitleText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF6B7280),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Development progress",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = "${progressValue}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF16A34A)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppDevContributionGrid(
                    levels = contributionLevels,
                    progressPercent = progressValue,
                    isRunning = isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSkeleton) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(skeletonBrush)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Preview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent, CircleShape)
                        .pressableScale(
                            enabled = isCompleted && !runtimeBusy,
                            pressedScale = 0.95f,
                            onClick = onDownloadClick
                        )
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        DownloadOutlineGlyph(
                            modifier = Modifier.fillMaxSize(),
                            tint = secondaryTint
                        )
                    } else {
                        EyeOutlineGlyph(
                            modifier = Modifier.fillMaxSize(),
                            tint = secondaryTint
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppDevContributionGrid(
    levels: List<Int>,
    progressPercent: Int,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val rows = 3
    val cols = 16
    val totalCells = rows * cols
    val targetFilledCells =
        ((progressPercent.coerceIn(0, 100) / 100f) * totalCells)
            .toInt()
            .coerceIn(0, totalCells)
    var animatedFilledCells by remember(levels, isRunning) { mutableIntStateOf(0) }

    LaunchedEffect(targetFilledCells, isRunning) {
        when {
            targetFilledCells < animatedFilledCells -> {
                animatedFilledCells = targetFilledCells
            }
            targetFilledCells == animatedFilledCells -> Unit
            else -> {
                val stepDelayMs =
                    when {
                        targetFilledCells - animatedFilledCells > 20 -> 6L
                        targetFilledCells - animatedFilledCells > 8 -> 10L
                        else -> 16L
                    }
                while (animatedFilledCells < targetFilledCells) {
                    animatedFilledCells += 1
                    delay(stepDelayMs)
                }
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (col in 0 until cols) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (row in 0 until rows) {
                    val index = (col * rows) + row
                    val level = levels.getOrElse(index) { 0 }.coerceIn(0, 4)
                    val filled = index < animatedFilledCells
                    val scale by animateFloatAsState(
                        targetValue = if (filled) 1f else 0f,
                        animationSpec =
                            if (filled) {
                                keyframes {
                                    durationMillis = 260
                                    0f at 0
                                    1.12f at 160
                                    1f at 260
                                }
                            } else {
                                tween(durationMillis = 120, easing = LinearEasing)
                            },
                        label = "app_dev_contrib_scale_$index"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (filled) 1f else 0f,
                        animationSpec = tween(durationMillis = 160, easing = LinearEasing),
                        label = "app_dev_contrib_alpha_$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFEBEDF0))
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer(
                                    alpha = alpha,
                                    scaleX = scale,
                                    scaleY = scale
                                )
                                .background(githubContributionColor(level))
                        )
                    }
                }
            }
        }
    }
}

internal fun buildGitHubContributionLevels(seedKey: String, totalCells: Int): List<Int> {
    val safeTotal = totalCells.coerceAtLeast(1)
    val random = kotlin.random.Random(seedKey.hashCode())
    return List(safeTotal) { index ->
        val progress = index.toFloat() / safeTotal.toFloat()
        when {
            progress < 0.2f -> if (random.nextFloat() > 0.3f) random.nextInt(from = 1, until = 3) else 0
            progress < 0.4f -> if (random.nextFloat() > 0.2f) random.nextInt(from = 1, until = 4) else 0
            progress < 0.6f -> if (random.nextFloat() > 0.1f) random.nextInt(from = 1, until = 5) else 0
            progress < 0.8f -> if (random.nextFloat() > 0.05f) random.nextInt(from = 2, until = 5) else 1
            else -> if (random.nextFloat() > 0.1f) random.nextInt(from = 3, until = 5) else 2
        }
    }
}

internal fun githubContributionColor(level: Int): Color {
    return when (level.coerceIn(0, 4)) {
        1 -> Color(0xFF9BE9A8)
        2 -> Color(0xFF40C463)
        3 -> Color(0xFF30A14E)
        4 -> Color(0xFF216E39)
        else -> Color(0xFFEBEDF0)
    }
}

internal fun mapLucideNameToAppIconForTag(name: String?): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (normalizeLucideIconName(name)) {
        "camera", "camera-off", "scan", "scan-line", "scan-face" -> AppIcons.Camera
        "search", "search-check", "search-code", "scan-search" -> AppIcons.Search
        "globe", "earth", "compass", "map", "map-pinned", "navigation" -> AppIcons.Globe
        "image", "images", "picture-in-picture", "wand-sparkles", "sparkles", "palette" -> AppIcons.CreateImage
        "file", "files", "folder", "folder-open", "file-text", "notebook", "notebook-pen", "book-open" -> AppIcons.Files
        "brain", "heart", "smile", "bookmark", "notepad-text", "book-heart" -> AppIcons.Memory
        "layout-dashboard", "monitor", "laptop", "panel-top", "panel-left" -> AppIcons.Monitor
        "list-todo", "calendar", "check-square", "clipboard-list", "target" -> AppIcons.Model
        "wrench", "hammer", "tool", "settings", "code", "code-xml", "terminal", "bot" -> AppIcons.Tool
        "app-window", "square-terminal", "component", "blocks", "rocket" -> AppIcons.AppDeveloper
        else -> null
    }
}

@Composable
internal fun EyeOutlineGlyph(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Canvas(modifier = modifier) {
        val stroke = (size.minDimension * 0.10f).coerceAtLeast(1.2f)
        drawOval(
            color = tint,
            topLeft = Offset(size.width * 0.08f, size.height * 0.24f),
            size = Size(size.width * 0.84f, size.height * 0.52f),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.14f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = stroke * 0.86f)
        )
    }
}

@Composable
internal fun DownloadOutlineGlyph(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Canvas(modifier = modifier) {
        val stroke = (size.minDimension * 0.10f).coerceAtLeast(1.2f)
        val centerX = size.width / 2f
        val startY = size.height * 0.18f
        val arrowY = size.height * 0.62f
        drawLine(
            color = tint,
            start = Offset(centerX, startY),
            end = Offset(centerX, arrowY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, arrowY),
            end = Offset(size.width * 0.35f, size.height * 0.47f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, arrowY),
            end = Offset(size.width * 0.65f, size.height * 0.47f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.24f, size.height * 0.80f),
            end = Offset(size.width * 0.76f, size.height * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun McpTagDetailCard(tag: MessageTag) {
    val detail = remember(tag.content) { parseMcpTagDetail(tag.content) }
    val isRunning = isTagRunning(tag)
    val detailsRaw =
        when {
            isRunning && detail.argumentsJson.isNotBlank() -> detail.argumentsJson
            isRunning -> "Running..."
            detail.error.isNotBlank() -> detail.error
            detail.result.isNotBlank() -> detail.result
            detail.argumentsJson.isNotBlank() -> detail.argumentsJson
            else -> "{}"
        }
    val detailsText = remember(detailsRaw) { formatToolDetailJson(detailsRaw) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolDetailCodeCard(
            title = "Details",
            content = detailsText
        )
        if (isRunning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = ThinkingLabelColor
                )
                Text(
                    text = "Running",
                    fontSize = 12.sp,
                    color = ThinkingLabelColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun AppDevWorkspaceScreen(
    tag: MessageTag,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val payload =
        remember(tag.id, tag.content, tag.status) {
            parseAppDevTagPayload(
                content = tag.content,
                fallbackName = tag.title.ifBlank { "App development" },
                fallbackStatus = tag.status
            )
        }
    val html = remember(payload.html) { normalizeGeneratedHtmlDraft(payload.html) }.trim()
    val isRunning = payload.status.equals("running", ignoreCase = true)
    var showCode by remember(tag.id) { mutableStateOf(isRunning || html.isBlank()) }
    var userToggledTab by remember(tag.id) { mutableStateOf(false) }

    LaunchedEffect(isRunning, html, userToggledTab) {
        if (!userToggledTab) {
            // Default tab policy: coding while generating, preview after completed.
            showCode = isRunning || html.isBlank()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                .padding(top = 8.dp, bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = payload.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!showCode) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                            .pressableScale(
                                pressedScale = 0.97f,
                                onClick = {
                                    if (isRunning) return@pressableScale
                                    userToggledTab = true
                                    showCode = false
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Preview",
                            fontSize = 12.sp,
                            color =
                                when {
                                    isRunning -> TextSecondary.copy(alpha = 0.55f)
                                    !showCode -> TextPrimary
                                    else -> TextSecondary
                                }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (showCode) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                            .pressableScale(
                                pressedScale = 0.97f,
                                onClick = {
                                    userToggledTab = true
                                    showCode = true
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Code",
                            fontSize = 12.sp,
                            color = if (showCode) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (showCode || isRunning) {
                    AppDevCodeSurface(
                        html = html,
                        error = payload.error,
                        isRunning = isRunning
                    )
                } else {
                    if (html.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = payload.error?.ifBlank { null }
                                    ?: stringResource(R.string.app_dev_preview_unavailable),
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        val workspaceBaseUrl =
                            remember(payload.sourceAppId) {
                                val key = payload.sourceAppId?.trim().orEmpty().ifBlank { "draft" }
                                "https://workspace-app.zionchat.local/app/$key/"
                            }
                        val contentSignature =
                            remember(workspaceBaseUrl, html) { "$workspaceBaseUrl:${html.hashCode()}" }
                        AppHtmlWebView(
                            modifier = Modifier.fillMaxSize(),
                            contentSignature = contentSignature,
                            html = html,
                            baseUrl = workspaceBaseUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppDevCodeSurface(
    html: String,
    error: String?,
    isRunning: Boolean
) {
    val codeText =
        remember(html, isRunning) {
            val normalized = normalizeGeneratedHtmlDraft(html)
            if (normalized.isBlank()) {
                ""
            } else if (isRunning) {
                // Stream phase prioritizes low-latency updates.
                normalized
            } else {
                prettyFormatHtmlForCodeView(normalized)
            }
        }
    val scrollState = rememberScrollState()
    val horizontalState = rememberScrollState()
    val lineCount = remember(codeText) { codeText.count { it == '\n' } + 1 }
    val lineNumberText = remember(lineCount) {
        buildString {
            for (i in 1..lineCount) {
                append(i)
                if (i != lineCount) append('\n')
            }
        }
    }

    LaunchedEffect(codeText, isRunning) {
        if (isRunning && codeText.isNotBlank()) {
            // Keep viewport near newest streamed chunks for a terminal-like coding flow.
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (codeText.isBlank()) {
            Text(
                text = error?.ifBlank { null }
                    ?: if (isRunning) "Generating code..." else "No code available.",
                fontSize = 14.sp,
                color = TextSecondary
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalState)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = lineNumberText,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary.copy(alpha = 0.72f),
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = codeText,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    softWrap = false
                )
            }
        }
    }
}

internal fun prettyFormatHtmlForCodeView(raw: String): String {
    val clean = normalizeGeneratedHtmlDraft(raw).trim()
    if (clean.isBlank()) return ""

    // Script/style heavy pages are easy to corrupt with naive token splitting.
    // For those cases, keep original line structure to avoid broken indentation.
    if (Regex("(?is)<\\s*(script|style|pre|textarea)\\b").containsMatchIn(clean)) {
        return clean
    }

    val openTagNameRegex = Regex("^<\\s*([A-Za-z][A-Za-z0-9:_-]*)\\b")
    val voidTags =
        setOf(
            "area",
            "base",
            "br",
            "col",
            "embed",
            "hr",
            "img",
            "input",
            "link",
            "meta",
            "param",
            "source",
            "track",
            "wbr"
        )

    val tokens = clean
        .replace("><", ">\n<")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val sb = StringBuilder()
    var indent = 0
    tokens.forEach { token ->
        val trimmedToken = token.trim()
        val lowerToken = trimmedToken.lowercase()
        val closeTag = lowerToken.startsWith("</")

        if (closeTag) indent = (indent - 1).coerceAtLeast(0)
        repeat(indent) { sb.append("  ") }
        sb.append(trimmedToken)
        sb.append('\n')

        val openMatch = openTagNameRegex.find(trimmedToken)
        val tagName = openMatch?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
        val selfClosing = trimmedToken.endsWith("/>")
        val isDoctypeOrComment = lowerToken.startsWith("<!") || lowerToken.startsWith("<?")
        val isVoid = tagName in voidTags
        val remainder =
            if (openMatch != null && openMatch.range.last + 1 < trimmedToken.length) {
                trimmedToken.substring(openMatch.range.last + 1)
            } else {
                ""
            }
        val hasInlineCloseForSameTag =
            tagName.isNotBlank() &&
                Regex("</\\s*${Regex.escape(tagName)}\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(remainder)

        val openTag =
            openMatch != null &&
                !closeTag &&
                !isDoctypeOrComment &&
                !selfClosing &&
                !isVoid &&
                !hasInlineCloseForSameTag

        if (openTag) indent += 1
    }
    return sb.toString().trimEnd()
}

@Composable
internal fun ToolDetailCodeCard(
    title: String,
    content: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = GrayLighter
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = "json",
                    fontSize = 11.sp,
                    color = TextSecondary.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content.ifBlank { "{}" },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun MessageOptionsDialog(
    isUser: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                DialogOption(text = "Copy", onClick = onCopy)
                if (isUser) {
                    DialogOption(text = "Edit", onClick = onEdit)
                }
                DialogOption(text = "Delete", onClick = onDelete, isDestructive = true)
                DialogOption(text = "Cancel", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun DialogOption(
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            color = if (isDestructive) Color(0xFFFF3B30) else TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyChatState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "What's on your mind today?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0D0D0D)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarContent(
    conversations: List<Conversation>,
    groupChats: List<GroupChatConfig>,
    currentConversationId: String?,
    nickname: String,
    avatarUri: String,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onConversationClick: (Conversation) -> Unit,
    onGroupConversationClick: (GroupChatConfig) -> Unit,
    onDeleteConversation: (String) -> Unit,
    navController: NavController
) {
    var groupExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 顶部搜索区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 搜索框
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = "Search",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.search),
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }

            // 新建对话按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onNewChat),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.NewChat,
                    contentDescription = "New Chat",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 菜单区域
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            SidebarMenuItem(
                icon = { Icon(AppIcons.NewChat, null, Modifier.size(24.dp), TextPrimary) },
                label = stringResource(R.string.new_chat),
                onClick = onNewChat
            )
            SidebarMenuItem(
                icon = { Icon(AppIcons.ChatGPTLogo, null, Modifier.size(24.dp), TextPrimary) },
                label = stringResource(R.string.images),
                onClick = { }
            )
            SidebarMenuItem(
                icon = {
                    Icon(
                        painter = rememberResourceDrawablePainter(R.drawable.ic_group_chat),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = TextPrimary
                    )
                },
                label = stringResource(R.string.group_chats),
                onClick = { groupExpanded = !groupExpanded }
            )
            AnimatedVisibility(
                visible = groupExpanded,
                enter =
                    fadeIn(animationSpec = tween(170, easing = FastOutSlowInEasing)) +
                        expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                exit =
                    fadeOut(animationSpec = tween(110, easing = FastOutSlowInEasing)) +
                        shrinkVertically(animationSpec = tween(160, easing = FastOutSlowInEasing))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (groupChats.isEmpty()) {
                        Text(
                            text = stringResource(R.string.group_chat_sidebar_empty),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    } else {
                        groupChats.forEach { group ->
                            val active = currentConversationId == group.conversationId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) GrayLighter else Color.Transparent)
                                    .pressableScale(
                                        pressedScale = 0.98f,
                                        onClick = { onGroupConversationClick(group) }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = group.name,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = group.memberBotIds.size.toString(),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
            SidebarMenuItem(
                icon = {
                    Icon(
                        painter = rememberResourceDrawablePainter(R.drawable.ic_apps),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                },
                label = stringResource(R.string.apps),
                onClick = {
                    onClose()
                    navController.navigate("apps")
                }
            )
        }

        // 历史记录区域
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            conversations.forEach { conversation ->
                val isSelected = conversation.id == currentConversationId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) GrayLighter else Color.Transparent)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onConversationClick(conversation) },
                            onLongClick = { onDeleteConversation(conversation.id) }
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.new_chat) },
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        // 底部用户信息
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .pressableScale { navController.navigate("settings") }
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像显示
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GrayLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri.isNotBlank()) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.User,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nickname,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.profile_personal),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
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

@Composable
fun SidebarMenuItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressableScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = label,
            fontSize = 18.sp,
            fontFamily = SourceSans3,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

@Composable
fun TopNavBar(
    onMenuClick: () -> Unit,
    onChatModelClick: () -> Unit,
    onNewChatClick: () -> Unit,
    showAutoBrowserButton: Boolean = false,
    onAutoBrowserClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧组
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 汉堡菜单
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(CircleShape)
                    .background(Surface, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(TextPrimary, RoundedCornerShape(1.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(TextPrimary, RoundedCornerShape(1.dp))
                    )
                }
            }

            // ChatGPT 标签
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(21.dp), clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(RoundedCornerShape(21.dp))
                    .background(Surface, RoundedCornerShape(21.dp))
                    .pressableScale(pressedScale = 0.97f, onClick = onChatModelClick)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ChatGPT",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }

        // 右侧：AutoBrowser + 新建对话
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = showAutoBrowserButton,
                enter = fadeIn(animationSpec = tween(170, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.88f, animationSpec = tween(190, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(130, easing = FastOutSlowInEasing)) +
                    scaleOut(targetScale = 0.88f, animationSpec = tween(130, easing = FastOutSlowInEasing))
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = onAutoBrowserClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.AutoBrowserCompass,
                        contentDescription = stringResource(R.string.settings_item_autobrowser),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(CircleShape)
                    .background(Surface, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onNewChatClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.NewChat,
                    contentDescription = "New Chat",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun GroupTopNavBar(
    onMenuClick: () -> Unit,
    onNewThreadClick: () -> Unit,
    avatarUri: String,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(CircleShape)
                    .background(Surface, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(TextPrimary, RoundedCornerShape(1.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(TextPrimary, RoundedCornerShape(1.dp))
                    )
                }
            }
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(21.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(21.dp))
                    .background(Surface, RoundedCornerShape(21.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.group_chats),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }

        Row(
            modifier = Modifier
                .height(42.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(21.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(RoundedCornerShape(21.dp))
                .background(Surface, RoundedCornerShape(21.dp))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onNewThreadClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.NewChat,
                    contentDescription = stringResource(R.string.new_chat),
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(GrayLighter, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri.isNotBlank()) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = stringResource(R.string.settings_item_personalization),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.User,
                        contentDescription = stringResource(R.string.settings_item_personalization),
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .pressableScale(pressedScale = 0.95f, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ActionIcon,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ToolMenuPanel(
    visible: Boolean,
    page: ToolMenuPage,
    webSearchEngine: String,
    mcpLoading: Boolean,
    mcpErrorText: String?,
    mcpServers: List<McpServerPickerItem>,
    selectedMcpServerIds: Set<String>,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onMcpPageChange: (ToolMenuPage) -> Unit,
    onOpenMcpSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onToolSelect: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val panelBaseMaxHeight = screenHeightDp * 0.6f
    val panelExpandLimitDp = screenHeightDp * 0.28f
    var panelOffsetPx by remember { mutableFloatStateOf(0f) }
    var panelExpandPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }
    val panelExpandLimitPx = remember(density, panelExpandLimitDp) { with(density) { panelExpandLimitDp.toPx() } }
    val expandSnapThresholdPx = remember(panelExpandLimitPx) { panelExpandLimitPx * 0.35f }
    val dragProgress = remember(panelOffsetPx, dismissThresholdPx) {
        (panelOffsetPx.coerceAtLeast(0f) / dismissThresholdPx).coerceIn(0f, 1f)
    }
    val currentPanelMaxHeight = remember(panelBaseMaxHeight, panelExpandPx, density) {
        panelBaseMaxHeight + with(density) { panelExpandPx.toDp() }
    }
    val panelDragState = rememberDraggableState { delta ->
        if (delta < 0f) {
            panelExpandPx =
                (panelExpandPx + (-delta)).coerceIn(0f, panelExpandLimitPx)
            return@rememberDraggableState
        }

        var remainingDelta = delta
        if (panelExpandPx > 0f) {
            val collapseAmount = remainingDelta.coerceAtMost(panelExpandPx)
            panelExpandPx = (panelExpandPx - collapseAmount).coerceAtLeast(0f)
            remainingDelta -= collapseAmount
        }

        if (remainingDelta > 0f) {
            panelOffsetPx = (panelOffsetPx + remainingDelta).coerceIn(0f, dismissThresholdPx * 1.8f)
        }
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) (0.5f * (1f - dragProgress * 0.72f)) else 0f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing)
    )
    LaunchedEffect(visible) {
        if (!visible) {
            panelOffsetPx = 0f
            panelExpandPx = 0f
            onMcpPageChange(ToolMenuPage.Tools)
        } else {
            panelOffsetPx = 0f
            panelExpandPx = 0f
        }
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(0, panelOffsetPx.roundToInt()) }
                        .animateEnterExit(
                            enter =
                                slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
                                ) +
                                    fadeIn(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)),
                            exit =
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing)
                                ) +
                                    fadeOut(animationSpec = tween(durationMillis = 95, easing = FastOutSlowInEasing))
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        )
                        .zIndex(10f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = currentPanelMaxHeight)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = panelDragState,
                                onDragStopped = { velocity ->
                                    val shouldDismiss = panelOffsetPx > dismissThresholdPx || velocity > 2000f
                                    if (shouldDismiss) {
                                        onDismiss()
                                        panelOffsetPx = 0f
                                        panelExpandPx = 0f
                                    } else {
                                        val shouldExpand =
                                            panelExpandPx > expandSnapThresholdPx || velocity < -900f
                                        val targetExpand = if (shouldExpand) panelExpandLimitPx else 0f
                                        scope.launch {
                                            animate(
                                                initialValue = panelOffsetPx,
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
                                            ) { value, _ ->
                                                panelOffsetPx = value
                                            }
                                        }
                                        scope.launch {
                                            animate(
                                                initialValue = panelExpandPx,
                                                targetValue = targetExpand,
                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                            ) { value, _ ->
                                                panelExpandPx = value
                                            }
                                        }
                                    }
                                }
                            )
                            .background(Surface)
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(GrayLight, RoundedCornerShape(2.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                if (targetState == ToolMenuPage.McpServers) {
                                    slideInHorizontally(
                                        initialOffsetX = { it / 2 },
                                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                                    ) togetherWith
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 2 },
                                            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                                        )
                                } else {
                                    slideInHorizontally(
                                        initialOffsetX = { -it / 2 },
                                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                                    ) togetherWith
                                        slideOutHorizontally(
                                            targetOffsetX = { it / 2 },
                                            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                                        )
                                }
                            },
                            label = "tool_menu_page_switch"
                        ) { currentPage ->
                            if (currentPage == ToolMenuPage.Tools) {
                                val toolsScrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(toolsScrollState)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        QuickActionButton(
                                            icon = { Icon(AppIcons.Camera, null, Modifier.size(28.dp), TextPrimary) },
                                            label = stringResource(R.string.chat_tool_camera),
                                            onClick = { onToolSelect("camera") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        QuickActionButton(
                                            icon = { Icon(AppIcons.ChatGPTLogo, null, Modifier.size(28.dp), TextPrimary) },
                                            label = stringResource(R.string.chat_tool_photos),
                                            onClick = { onToolSelect("photos") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        QuickActionButton(
                                            icon = {
                                                Icon(
                                                    painter = rememberResourceDrawablePainter(R.drawable.ic_files),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                    tint = Color.Unspecified
                                                )
                                            },
                                            label = stringResource(R.string.chat_tool_files),
                                            onClick = { onToolSelect("files") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = GrayLight, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val webSearchSubtitle =
                                        stringResource(R.string.chat_tool_web_search_subtitle) +
                                            " · " +
                                            displaySearchEngineName(webSearchEngine)
                                    ToolListItem(
                                        icon = { Icon(AppIcons.Globe, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.chat_tool_web_search),
                                        subtitle = webSearchSubtitle,
                                        onClick = { onToolSelect("web") }
                                    )
                                    ToolListItem(
                                        icon = { Icon(AppIcons.CreateImage, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.chat_tool_create_image),
                                        subtitle = stringResource(R.string.chat_tool_create_image_subtitle),
                                        onClick = { onToolSelect("image") }
                                    )
                                    ToolListItem(
                                        icon = {
                                            Icon(
                                                painter = rememberResourceDrawablePainter(R.drawable.ic_autosoul),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = TextPrimary
                                            )
                                        },
                                        title = stringResource(R.string.settings_item_autosoul),
                                        subtitle = stringResource(R.string.chat_tool_autosoul_subtitle),
                                        onClick = { onToolSelect("autosoul") }
                                    )
                                    ToolListItem(
                                        icon = { Icon(AppIcons.AutoBrowserCompass, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.settings_item_autobrowser),
                                        subtitle = stringResource(R.string.chat_tool_autobrowser_subtitle),
                                        onClick = { onToolSelect("autobrowser") }
                                    )
                                    ToolListItem(
                                        icon = { Icon(AppIcons.MCPTools, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.settings_item_mcp_tools),
                                        subtitle = stringResource(R.string.chat_tool_mcp_choose_providers),
                                        onClick = {
                                            onMcpPageChange(ToolMenuPage.McpServers)
                                        }
                                    )
                                    ToolListItem(
                                        icon = { Icon(AppIcons.AppDeveloper, null, Modifier.size(24.dp), TextPrimary) },
                                        title = stringResource(R.string.chat_tool_app_builder),
                                        subtitle = stringResource(R.string.chat_tool_app_builder_subtitle),
                                        onClick = { onToolSelect("app_builder") }
                                    )
                                }
                            } else {
                                McpServerPickerPanel(
                                    loading = mcpLoading,
                                    errorText = mcpErrorText,
                                    servers = mcpServers,
                                    selectedServerIds = selectedMcpServerIds,
                                    onBack = { onMcpPageChange(ToolMenuPage.Tools) },
                                    onOpenSettings = onOpenMcpSettings,
                                    onToggle = onToggleMcpServer,
                                    maxHeight = currentPanelMaxHeight
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
internal fun McpServerPickerPanel(
    loading: Boolean,
    errorText: String?,
    servers: List<McpServerPickerItem>,
    selectedServerIds: Set<String>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    maxHeight: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.pressableScale(pressedScale = 0.97f, onClick = onBack)
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.settings_item_mcp_tools),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .pressableScale(pressedScale = 0.95f, onClick = onOpenSettings)
                    .padding(horizontal = 9.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.More,
                    contentDescription = stringResource(R.string.settings),
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!errorText.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.chat_mcp_picker_sync_error, errorText),
                fontSize = 12.sp,
                color = Color(0xFFB42318)
            )
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.2.dp,
                        color = TextPrimary
                    )
                }
            }

            servers.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.chat_mcp_picker_empty_enabled),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = servers,
                        key = { item -> item.serverId }
                    ) { item ->
                        val checked = selectedServerIds.contains(item.serverId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White, RoundedCornerShape(14.dp))
                                .pressableScale(
                                    pressedScale = 0.985f,
                                    onClick = { onToggle(item.serverId, !checked) }
                                )
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.serverName,
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.subtitle,
                                    fontSize = 12.sp,
                                    color = Color(0xFF4B5563),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = { value -> onToggle(item.serverId, value) },
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color.Black,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFFD1D5DB)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatModelPickerSheetContent(
    groupedModels: List<Pair<String, List<ModelConfig>>>,
    selectedModelId: String?,
    thinkingEnabled: Boolean,
    onToggleThinking: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onSelectModel: (ModelConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = remember(density) { with(density) { 86.dp.toPx() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp, max = 640.dp)
            .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
                    },
                    onDragStopped = { velocity ->
                        val shouldDismiss = dragOffsetPx > dismissThresholdPx || velocity > 1800f
                        if (shouldDismiss) {
                            onDismissRequest()
                            dragOffsetPx = 0f
                        } else {
                            scope.launch {
                                animate(
                                    initialValue = dragOffsetPx,
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                                ) { value, _ ->
                                    dragOffsetPx = value
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .background(GrayLight, RoundedCornerShape(2.dp))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.chat_model_picker_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            ChatThinkingToggleButton(
                enabled = thinkingEnabled,
                onToggle = onToggleThinking
            )
        }

        if (groupedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.chat_model_picker_empty),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    groupedModels.forEach { (providerName, providerModels) ->
                        item(key = "chat_model_provider_$providerName") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = providerName.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = GrayLighter)
                                ) {
                                    providerModels.forEachIndexed { index, model ->
                                        val selected = isSameStoredOrRemoteModelId(model.id, selectedModelId)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pressableScale(
                                                    pressedScale = 0.985f,
                                                    onClick = { onSelectModel(model) }
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
                                                    .size(20.dp)
                                                    .alpha(if (selected) 1f else 0f)
                                            )
                                        }
                                        if (index != providerModels.lastIndex) {
                                            HorizontalDivider(color = GrayLight)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Surface, Surface.copy(alpha = 0f))
                            )
                        )
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
internal fun ChatThinkingToggleButton(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (enabled) Color(0xFF18181B) else Color(0xFFE4E4E7),
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "thinking_switch_track"
    )
    val thumbOffsetX by animateDpAsState(
        targetValue = if (enabled) 21.dp else 3.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "thinking_switch_thumb"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.chat_model_picker_thinking_label),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(trackColor, RoundedCornerShape(8.dp))
                .pressableScale(pressedScale = 0.98f, onClick = { onToggle(!enabled) })
        )
        {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetX, y = 3.dp)
                    .size(20.dp)
                    .shadow(
                        elevation = 1.5.dp,
                        shape = RoundedCornerShape(6.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.15f),
                        spotColor = Color.Black.copy(alpha = 0.15f)
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White, RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
fun QuickActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ChatBackground, RoundedCornerShape(14.dp))
            .pressableScale(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SourceSans3,
            color = TextPrimary
        )
    }
}

internal suspend fun handleImageGeneration(
    repository: AppRepository,
    chatApiClient: ChatApiClient,
    safeConversationId: String,
    userPrompt: String,
    assistantMessage: Message,
    updateAssistantContent: (String) -> Unit
) {
    // 显示正在生成的提示
    updateAssistantContent("🎨 Generating image...")

    // 获取生图模型配置
    val imageModelId = repository.defaultImageModelIdFlow.first()
    if (imageModelId.isNullOrBlank()) {
        updateAssistantContent("❌ Please configure Image Model in Settings → Default model before generating images.")
        repository.appendMessage(
            safeConversationId,
            assistantMessage.copy(content = "Please configure Image Model in Settings → Default model before generating images.")
        )
        return
    }

    val allModels = repository.modelsFlow.first()
    val imageModel = allModels.firstOrNull { it.id == imageModelId }
        ?: allModels.firstOrNull { extractRemoteModelId(it.id) == imageModelId }
    if (imageModel == null) {
        updateAssistantContent("❌ Image model not found: $imageModelId. Enable or add it in Models.")
        repository.appendMessage(
            safeConversationId,
            assistantMessage.copy(content = "Image model not found: $imageModelId. Enable or add it in Models.")
        )
        return
    }

    val providerList = repository.providersFlow.first()
    val provider = imageModel.providerId?.let { pid -> providerList.firstOrNull { it.id == pid } }
        ?: providerList.firstOrNull()
    if (provider == null || provider.apiUrl.isBlank() || provider.apiKey.isBlank()) {
        updateAssistantContent("❌ Please add a provider with valid API URL and API Key.")
        repository.appendMessage(
            safeConversationId,
            assistantMessage.copy(content = "Please add a provider with valid API URL and API Key.")
        )
        return
    }

    // 调用图片生成API
    val optimizedPrompt = buildImageGenerationPrompt(userPrompt)
    val result = chatApiClient.generateImage(
        provider = provider,
        modelId = extractRemoteModelId(imageModel.id),
        prompt = optimizedPrompt,
        extraHeaders = imageModel.headers,
        size = "1024x1024",
        quality = "standard",
        n = 1
    )

    result.fold(
        onSuccess = { imageUrl ->
            val markdownImage = "![Generated Image]($imageUrl)"
            updateAssistantContent(markdownImage)
            repository.appendMessage(
                safeConversationId,
                assistantMessage.copy(content = markdownImage)
            )
        },
        onFailure = { error ->
            val errorMsg = "❌ Image generation failed: ${error.message ?: error.toString()}"
            updateAssistantContent(errorMsg)
            repository.appendMessage(
                safeConversationId,
                assistantMessage.copy(content = errorMsg)
            )
        }
    )
}

internal fun buildImageGenerationPrompt(rawPrompt: String): String {
    val prompt = rawPrompt.trim()
    if (prompt.isBlank()) return rawPrompt

    val hasTypographySignal =
        Regex("(?i)\\b(text|typography|title|caption|poster|banner|flyer|slogan|logo|cover)\\b|文字|文案|标题|海报|字体|排版|标语|封面")
            .containsMatchIn(prompt)
    val quotedTextBlocks = extractImageQuotedTextBlocks(prompt)

    val guardrail =
        buildString {
            appendLine("Generation constraints:")
            appendLine("- Keep composition clean with stable visual hierarchy and balanced spacing.")
            appendLine("- If any text appears in the image, keep typography clear and aligned.")
            appendLine("- Avoid scrambled letters, random symbols, mirrored text, and broken line spacing.")

            if (hasTypographySignal || quotedTextBlocks.isNotEmpty()) {
                appendLine("- Prioritize readability over decorative distortion for all textual elements.")
                appendLine("- Keep consistent baseline, kerning, and line-height for Chinese and English text.")
            }

            if (quotedTextBlocks.isNotEmpty()) {
                appendLine("- Render these text blocks exactly as written (same order, punctuation, and language):")
                quotedTextBlocks.forEachIndexed { index, text ->
                    append("- ")
                    append(index + 1)
                    append(") ")
                    appendLine(text)
                }
            }
        }.trim()

    return if (guardrail.isBlank()) prompt else "$prompt\n\n$guardrail"
}

internal fun extractImageQuotedTextBlocks(prompt: String): List<String> {
    val regex =
        Regex("\"([^\"]{1,80})\"|'([^']{1,80})'|“([^”]{1,80})”|「([^」]{1,80})」|『([^』]{1,80})』")
    return regex.findAll(prompt)
        .mapNotNull { match ->
            match.groupValues
                .drop(1)
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        .distinct()
        .take(6)
        .toList()
}

internal data class AutoSoulExecutionResult(
    val success: Boolean,
    val script: String,
    val stepCount: Int,
    val statusText: String,
    val error: String?,
    val logs: List<String>,
    val timedOut: Boolean
)

internal data class AutoSoulPlannerDecision(
    val done: Boolean,
    val message: String?,
    val action: String?,
    val args: Map<String, String>,
    val reason: String?
)

internal data class AutoSoulNormalizedStep(
    val action: String,
    val args: Map<String, String>
)

internal data class AutoSoulStepTrace(
    val stepNo: Int,
    val action: String,
    val args: Map<String, String>,
    val success: Boolean,
    val statusText: String,
    val error: String?,
    val logs: List<String>,
    val timedOut: Boolean
)

internal suspend fun handleAutoSoulInvocation(
    repository: AppRepository,
    chatApiClient: ChatApiClient,
    context: Context,
    userMessage: Message
): String {
    val taskPrompt = userMessage.content.trim().ifBlank { "请根据当前需求执行 Android 自动化任务。" }
    val execution =
        executeAutoSoulTask(
            repository = repository,
            chatApiClient = chatApiClient,
            context = context,
            taskPrompt = taskPrompt,
            attachments = userMessage.attachments
        ).getOrElse { error ->
            return error.message?.trim().orEmpty().ifBlank { "AutoSoul 执行失败。" }
        }

    val logsText = execution.logs.takeLast(10).joinToString("\n")
    return buildString {
        append(if (execution.success) "✅ AutoSoul 任务结束" else "❌ AutoSoul 执行失败")
        append("（${execution.stepCount} 步）")
        append("\n状态：")
        append(execution.statusText.ifBlank { if (execution.success) "任务结束" else "执行失败" })
        execution.error?.trim()?.takeIf { it.isNotBlank() }?.let { err ->
            append("\n错误：")
            append(err.take(280))
        }
        if (logsText.isNotBlank()) {
            append("\n\n最近日志：\n")
            append(logsText)
        }
        append("\n\n```json\n")
        append(execution.script)
        append("\n```")
    }.trim()
}

internal suspend fun executeAutoSoulTask(
    repository: AppRepository,
    chatApiClient: ChatApiClient,
    context: Context,
    taskPrompt: String,
    attachments: List<MessageAttachment>?
): Result<AutoSoulExecutionResult> {
    return runCatching {
        try {
            val modelKey =
                repository.defaultAutoSoulModelIdFlow.first()
                    ?.trim()
                    .orEmpty()
                    .ifBlank {
                        throw IllegalStateException("AutoSoul 模型未配置。请先在 Settings → AutoSoul 里选择支持视觉的模型。")
                    }

            val allModels = repository.modelsFlow.first()
            val providers = repository.providersFlow.first()
            val selectedModel =
                allModels.firstOrNull { it.id == modelKey }
                    ?: allModels.firstOrNull { extractRemoteModelId(it.id) == modelKey }
                    ?: throw IllegalStateException("AutoSoul 模型不存在：$modelKey。请在 Settings → AutoSoul 重新选择。")

            if (!selectedModel.enabled) {
                throw IllegalStateException("当前 AutoSoul 模型已被禁用，请在 Settings → AutoSoul 选择已启用模型。")
            }

            if (!isLikelyVisionModel(selectedModel)) {
                throw IllegalStateException("当前 AutoSoul 模型不符合视觉模型要求，请在 Settings → AutoSoul 重新选择视觉模型。")
            }

            val provider =
                selectedModel.providerId?.let { pid -> providers.firstOrNull { it.id == pid } }
                    ?: providers.firstOrNull()
            if (provider == null || provider.apiUrl.isBlank() || provider.apiKey.isBlank()) {
                throw IllegalStateException("AutoSoul 模型的提供方未正确配置 API URL / API Key。")
            }

            val maxAgentRounds = 14
            val stepTraces = mutableListOf<AutoSoulStepTrace>()
            var finishMessage: String? = null

            for (round in 1..maxAgentRounds) {
                val plannerMessages =
                    buildList {
                        add(Message(role = "system", content = buildAutoSoulPlannerSystemPrompt()))
                        add(
                            Message(
                                role = "user",
                                content =
                                    buildAutoSoulPlannerRoundPrompt(
                                        taskPrompt = taskPrompt,
                                        currentRound = round,
                                        maxRounds = maxAgentRounds,
                                        traces = stepTraces
                                    ),
                                attachments = if (round == 1) attachments else null
                            )
                        )
                    }

                val plannerText =
                    chatApiClient.chatCompletions(
                        provider = provider,
                        modelId = extractRemoteModelId(selectedModel.id),
                        messages = plannerMessages,
                        extraHeaders = selectedModel.headers
                    ).getOrElse { error ->
                        throw IllegalStateException("AutoSoul 决策失败：${error.message ?: error.toString()}")
                    }.trim()
                if (plannerText.isBlank()) {
                    throw IllegalStateException("AutoSoul 决策失败：模型没有返回有效内容。")
                }

                val decision =
                    parseAutoSoulPlannerDecision(plannerText).getOrElse { error ->
                        throw IllegalStateException("AutoSoul 决策解析失败：${error.message ?: "unknown"}")
                    }

                if (decision.done) {
                    finishMessage = decision.message?.trim()?.ifBlank { null } ?: "任务结束"
                    break
                }

                val normalizedStep =
                    normalizeAutoSoulPlannerStep(
                        actionRaw = decision.action?.trim().orEmpty(),
                        rawArgs = decision.args
                    )
                val sanitizedStepResult = enforceAutoSoulLaunchWhitelist(normalizedStep)
                if (sanitizedStepResult.isFailure) {
                    val errorText =
                        sanitizedStepResult.exceptionOrNull()?.message?.trim().orEmpty()
                            .ifBlank { "Launch 参数校验失败。" }
                    stepTraces +=
                        AutoSoulStepTrace(
                            stepNo = stepTraces.size + 1,
                            action = normalizedStep.action.ifBlank { "Launch" },
                            args = normalizedStep.args,
                            success = false,
                            statusText = "参数校验失败",
                            error = errorText,
                            logs = listOf(errorText),
                            timedOut = false
                        )
                    continue
                }
                val sanitizedStep = sanitizedStepResult.getOrThrow()
                val actionName =
                    sanitizedStep.action.trim().ifBlank {
                        throw IllegalStateException("AutoSoul 决策缺少 action。")
                    }
                val currentFingerprint = buildAutoSoulActionFingerprint(actionName, sanitizedStep.args)
                val repeatedSuccessCount =
                    stepTraces
                        .asReversed()
                        .takeWhile { trace ->
                            trace.success && buildAutoSoulActionFingerprint(trace.action, trace.args) == currentFingerprint
                        }
                        .size
                val strictLoopActions = setOf("tap", "longpress", "long_press", "launch", "home", "back")
                val repeatLimit = if (strictLoopActions.contains(actionName.trim().lowercase())) 1 else 2
                if (repeatedSuccessCount >= repeatLimit) {
                    throw IllegalStateException("AutoSoul 检测到重复动作循环（$actionName），已自动停止。请补充更具体任务目标后重试。")
                }
                val singleStepScript = buildAutoSoulSingleStepScript(actionName, sanitizedStep.args)
                val parsedStep =
                    AutoSoulScriptParser.parse(singleStepScript).getOrElse { error ->
                        throw IllegalStateException("AutoSoul 单步脚本校验失败：${error.message ?: "unknown"}")
                    }.firstOrNull() ?: throw IllegalStateException("AutoSoul 单步脚本为空。")

                val execution =
                    awaitAutoSoulCompletion(
                        context = context.applicationContext,
                        script = singleStepScript,
                        stepCount = 1,
                        timeoutMs = 95_000L,
                        keepOverlayVisibleAfterFinish = true
                    )
                stepTraces +=
                    AutoSoulStepTrace(
                        stepNo = stepTraces.size + 1,
                        action = parsedStep.action,
                        args = parsedStep.args,
                        success = execution.success,
                        statusText = execution.statusText,
                        error = execution.error,
                        logs = execution.logs.takeLast(12),
                        timedOut = execution.timedOut
                    )
            }

            val exhaustedRounds = finishMessage == null && stepTraces.size >= maxAgentRounds
            val timedOut = stepTraces.any { it.timedOut }
            val success = finishMessage != null
            val errorText =
                when {
                    success -> null
                    exhaustedRounds -> "达到最大执行轮次（$maxAgentRounds）仍未完成任务。"
                    timedOut -> stepTraces.lastOrNull { it.timedOut }?.error ?: "执行超时"
                    else -> stepTraces.lastOrNull()?.error ?: "任务未完成"
                }
            val statusText =
                when {
                    success -> finishMessage.orEmpty()
                    exhaustedRounds -> "达到最大执行轮次"
                    else -> stepTraces.lastOrNull()?.statusText?.ifBlank { "执行失败" } ?: "执行失败"
                }
            val mergedLogs =
                stepTraces
                    .flatMap { trace ->
                        val prefix = "Step ${trace.stepNo}(${trace.action})"
                        trace.logs.map { line -> "$prefix | $line" }
                    }
                    .takeLast(80)
                    .ifEmpty { AutoSoulAutomationManager.logs.value.takeLast(40) }

            AutoSoulExecutionResult(
                success = success,
                script = buildAutoSoulExecutionTraceJson(taskPrompt, stepTraces, finishMessage, maxAgentRounds),
                stepCount = stepTraces.size,
                statusText = statusText,
                error = errorText,
                logs = mergedLogs,
                timedOut = timedOut
            )
        } finally {
            AutoSoulAutomationManager.closeOverlay()
        }
    }
}

private suspend fun awaitAutoSoulCompletion(
    context: Context,
    script: String,
    stepCount: Int,
    timeoutMs: Long,
    keepOverlayVisibleAfterFinish: Boolean = false
): AutoSoulExecutionResult {
    AutoSoulAutomationManager.bindOverlayActions(context.applicationContext)
    val logStartIndex = AutoSoulAutomationManager.logs.value.size
    AutoSoulAutomationManager.start(
        context = context.applicationContext,
        script = script,
        keepOverlayVisibleAfterFinish = keepOverlayVisibleAfterFinish
    ).getOrThrow()

    val startedAt = System.currentTimeMillis()
    var seenRunning = false
    while (System.currentTimeMillis() - startedAt < timeoutMs) {
        val state = AutoSoulAutomationManager.state.value
        if (state.running) seenRunning = true

        val error = state.lastError?.trim()?.takeIf { it.isNotBlank() }
        val logs = AutoSoulAutomationManager.logs.value.drop(logStartIndex).takeLast(40)
        if (!state.running && !error.isNullOrBlank() && !seenRunning) {
            return AutoSoulExecutionResult(
                success = false,
                script = script,
                stepCount = stepCount,
                statusText = state.statusText,
                error = error,
                logs = logs,
                timedOut = false
            )
        }
        if (seenRunning && !state.running) {
            return AutoSoulExecutionResult(
                success = error.isNullOrBlank(),
                script = script,
                stepCount = stepCount,
                statusText = state.statusText,
                error = error,
                logs = logs,
                timedOut = false
            )
        }
        delay(260L)
    }

    AutoSoulAutomationManager.stop(
        reason = "执行超时自动停止",
        hideOverlay = !keepOverlayVisibleAfterFinish
    )
    return AutoSoulExecutionResult(
        success = false,
        script = script,
        stepCount = stepCount,
        statusText = "执行超时",
        error = "执行超时（>${timeoutMs / 1000}s）",
        logs = AutoSoulAutomationManager.logs.value.drop(logStartIndex).takeLast(40),
        timedOut = true
    )
}

internal fun buildAutoSoulToolResultText(result: AutoSoulExecutionResult): String {
    val headline =
        buildString {
            append(if (result.success) "success" else "failed")
            append(", steps=")
            append(result.stepCount)
            append(", status=")
            append(result.statusText.ifBlank { if (result.success) "done" else "failed" })
            if (result.timedOut) append(", timeout=true")
            result.error?.trim()?.takeIf { it.isNotBlank() }?.let { err ->
                append(", error=")
                append(err.take(220))
            }
        }
    val compactLogs = result.logs.takeLast(8).joinToString("\n").trim()
    return if (compactLogs.isBlank()) {
        headline
    } else {
        "$headline\nLogs:\n$compactLogs"
    }
}

internal fun normalizeAutoSoulScriptCandidate(raw: String): Result<String> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return Result.failure(IllegalStateException("empty response"))
    }

    val candidates = linkedSetOf<String>()
    candidates += trimmed

    Regex("```(?:json)?\\s*([\\s\\S]*?)```", setOf(RegexOption.IGNORE_CASE))
        .findAll(trimmed)
        .forEach { match ->
            val code = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (code.isNotBlank()) candidates += code
        }

    val arrayStart = trimmed.indexOf('[')
    val arrayEnd = trimmed.lastIndexOf(']')
    if (arrayStart >= 0 && arrayEnd > arrayStart) {
        candidates += trimmed.substring(arrayStart, arrayEnd + 1).trim()
    }

    val objectStart = trimmed.indexOf('{')
    val objectEnd = trimmed.lastIndexOf('}')
    if (objectStart >= 0 && objectEnd > objectStart) {
        candidates += trimmed.substring(objectStart, objectEnd + 1).trim()
    }

    candidates.forEach { candidate ->
        if (AutoSoulScriptParser.parse(candidate).isSuccess) {
            return Result.success(candidate)
        }
    }
    return Result.failure(IllegalStateException("no valid autosoul json script found"))
}

internal fun buildAutoSoulPlannerRoundPrompt(
    taskPrompt: String,
    currentRound: Int,
    maxRounds: Int,
    traces: List<AutoSoulStepTrace>
): String {
    return buildString {
        appendLine("任务目标：")
        appendLine(taskPrompt.trim().ifBlank { "请完成当前用户请求。" })
        appendLine()
        appendLine("当前轮次：$currentRound / $maxRounds")
        appendLine()
        if (traces.isEmpty()) {
            appendLine("当前还未执行任何步骤，请给出第一步动作。")
        } else {
            appendLine("历史执行结果（最近优先）：")
            traces.takeLast(6).forEach { trace ->
                append("- Step ")
                append(trace.stepNo)
                append(" | action=")
                append(trace.action)
                append(" | result=")
                append(if (trace.success) "success" else "failed")
                append(" | status=")
                append(trace.statusText.ifBlank { if (trace.success) "done" else "failed" })
                trace.error?.trim()?.takeIf { it.isNotBlank() }?.let { err ->
                    append(" | error=")
                    append(err.take(120))
                }
                append('\n')
            }
        }
        appendLine()
        appendLine("请只输出一个 JSON 对象，不要 Markdown：")
        appendLine("完成任务时：{\"status\":\"done\",\"message\":\"...\"}")
        appendLine("继续执行时：{\"status\":\"continue\",\"step\":{\"action\":\"Tap\",\"x\":\"0.50\",\"y\":\"0.72\"},\"reason\":\"...\"}")
        appendLine("注意：Swipe 的 start/end 必须是完整坐标对（例如 \"0.50,0.78\"），Launch 只能给 app/package。")
    }.trim()
}

internal fun parseAutoSoulPlannerDecision(raw: String): Result<AutoSoulPlannerDecision> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return Result.failure(IllegalStateException("empty decision"))

    val objectCandidates = linkedSetOf<String>()
    Regex("```(?:json)?\\s*([\\s\\S]*?)```", setOf(RegexOption.IGNORE_CASE))
        .findAll(trimmed)
        .forEach { match ->
            val body = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (body.startsWith("{") && body.endsWith("}")) objectCandidates += body
        }
    val objStart = trimmed.indexOf('{')
    val objEnd = trimmed.lastIndexOf('}')
    if (objStart >= 0 && objEnd > objStart) {
        objectCandidates += trimmed.substring(objStart, objEnd + 1).trim()
    }

    objectCandidates.forEach { candidate ->
        parseJsonObjectLenient(candidate)?.let { obj ->
            parseAutoSoulPlannerDecisionFromObject(obj)?.let { return Result.success(it) }
        }
    }

    parseAutoSoulDoFinishDecision(trimmed)?.let { return Result.success(it) }

    val fallbackScript = normalizeAutoSoulScriptCandidate(trimmed).getOrNull()
    if (!fallbackScript.isNullOrBlank()) {
        val steps = AutoSoulScriptParser.parse(fallbackScript).getOrNull().orEmpty()
        val firstStep = steps.firstOrNull()
        if (firstStep != null) {
            return Result.success(
                AutoSoulPlannerDecision(
                    done = false,
                    message = null,
                    action = firstStep.action,
                    args = firstStep.args,
                    reason = null
                )
            )
        }
    }

    parseAutoSoulLooseDecision(trimmed)?.let { return Result.success(it) }

    return Result.failure(IllegalStateException("invalid decision json"))
}

internal fun parseAutoSoulPlannerDecisionFromObject(obj: JsonObject): AutoSoulPlannerDecision? {
    val status = obj.readString("status")?.trim()?.lowercase()
    val doneFlag = obj.readBoolean("done") ?: false
    val doneByStatus =
        status == "done" || status == "finish" || status == "finished" ||
            status == "complete" || status == "completed" || status == "stop"
    if (doneFlag || doneByStatus) {
        return AutoSoulPlannerDecision(
            done = true,
            message =
                obj.readString("message")
                    ?: obj.readString("result")
                    ?: obj.readString("summary"),
            action = null,
            args = emptyMap(),
            reason = obj.readString("reason")
        )
    }

    val stepObj =
        when {
            obj.get("step")?.isJsonObject == true -> obj.getAsJsonObject("step")
            obj.get("next_step")?.isJsonObject == true -> obj.getAsJsonObject("next_step")
            obj.get("next")?.isJsonObject == true -> obj.getAsJsonObject("next")
            else -> obj
        }
    val args = linkedMapOf<String, String>()

    appendAutoSoulArgsFromObject(args, stepObj.readObject("args"))
    appendAutoSoulArgsFromObject(args, stepObj.readObject("parameters"))
    appendAutoSoulArgsFromObject(args, stepObj.readObject("params"))
    appendAutoSoulArgsFromObject(args, obj.readObject("args"))
    appendAutoSoulArgsFromObject(args, obj.readObject("parameters"))
    appendAutoSoulArgsFromObject(args, obj.readObject("params"))

    appendAutoSoulArgsFromText(args, stepObj.readString("args"))
    appendAutoSoulArgsFromText(args, stepObj.readString("parameters"))
    appendAutoSoulArgsFromText(args, stepObj.readString("params"))
    appendAutoSoulArgsFromText(args, obj.readString("args"))
    appendAutoSoulArgsFromText(args, obj.readString("parameters"))
    appendAutoSoulArgsFromText(args, obj.readString("params"))

    stepObj.entrySet().forEach { (key, value) ->
        if (
            key == "action" ||
            key == "tool" ||
            key == "name" ||
            key == "type" ||
            key == "args" ||
            key == "params" ||
            key == "parameters" ||
            key == "status" ||
            key == "done" ||
            key == "message" ||
            key == "reason"
        ) return@forEach
        value.toAutoSoulValueString()?.let { args.putIfAbsent(key, it) }
    }
    obj.entrySet().forEach { (key, value) ->
        if (
            key == "step" ||
            key == "next" ||
            key == "next_step" ||
            key == "action" ||
            key == "tool" ||
            key == "name" ||
            key == "type" ||
            key == "args" ||
            key == "params" ||
            key == "parameters" ||
            key == "status" ||
            key == "done" ||
            key == "message" ||
            key == "reason"
        ) return@forEach
        value.toAutoSoulValueString()?.let { args.putIfAbsent(key, it) }
    }

    val action =
        canonicalizeAutoSoulAction(
            stepObj.readString("action")
                ?: stepObj.readString("tool")
                ?: stepObj.readString("name")
                ?: stepObj.readString("type")
                ?: obj.readString("action")
                ?: obj.readString("tool")
                ?: obj.readString("name")
                ?: obj.readString("type")
        )
    if (action.isNullOrBlank()) {
        val looseStepText = stepObj.readString("step") ?: obj.readString("step")
        val loose = parseAutoSoulLooseDecision(looseStepText.orEmpty())
        if (loose != null && !loose.done) {
            return loose.copy(reason = obj.readString("reason") ?: stepObj.readString("reason") ?: loose.reason)
        }
        return null
    }

    return AutoSoulPlannerDecision(
        done = false,
        message = null,
        action = action,
        args = args,
        reason = obj.readString("reason") ?: stepObj.readString("reason")
    )
}

internal fun buildAutoSoulSingleStepScript(
    action: String,
    args: Map<String, String>
): String {
    val step = linkedMapOf<String, String>()
    step["action"] = action.trim()
    args.forEach { (key, value) ->
        val k = key.trim()
        val v = value.trim()
        if (k.isNotBlank() && v.isNotBlank()) {
            step[k] = v
        }
    }
    return GsonBuilder().disableHtmlEscaping().create().toJson(listOf(step))
}

internal fun buildAutoSoulExecutionTraceJson(
    taskPrompt: String,
    traces: List<AutoSoulStepTrace>,
    finishMessage: String?,
    maxRounds: Int
): String {
    val payload = linkedMapOf<String, Any?>()
    payload["task"] = taskPrompt.trim()
    payload["max_rounds"] = maxRounds
    payload["finished"] = finishMessage != null
    payload["finish_message"] = finishMessage
    payload["steps"] =
        traces.map { trace ->
            linkedMapOf<String, Any?>(
                "step" to trace.stepNo,
                "action" to trace.action,
                "args" to trace.args,
                "success" to trace.success,
                "status" to trace.statusText,
                "error" to trace.error,
                "timed_out" to trace.timedOut
            )
        }
    return GsonBuilder().disableHtmlEscaping().create().toJson(payload)
}

internal fun buildAutoSoulActionFingerprint(
    action: String,
    args: Map<String, String>
): String {
    val normalizedAction = action.trim().lowercase()
    val filteredArgs =
        args.entries
            .filterNot { (key, _) ->
                key.equals("reason", ignoreCase = true) ||
                    key.equals("explain", ignoreCase = true) ||
                    key.equals("note", ignoreCase = true)
            }
            .associate { (key, value) -> key.trim().lowercase() to value.trim() }

    return when (normalizedAction) {
        "home", "back" -> normalizedAction
        "wait" -> {
            val duration = filteredArgs["duration_ms"] ?: filteredArgs["duration"] ?: ""
            "$normalizedAction|$duration"
        }
        "launch" -> {
            val target =
                sequenceOf(
                    filteredArgs["app"],
                    filteredArgs["package"],
                    filteredArgs["target"],
                    filteredArgs["name"]
                ).firstOrNull { !it.isNullOrBlank() }
                    ?.lowercase()
                    ?.replace(" ", "")
                    ?.replace("　", "")
                    .orEmpty()
            "$normalizedAction|$target"
        }
        "tap", "longpress", "long_press" -> {
            val point =
                normalizeAutoSoulCoordinatePair(
                    sequenceOf(
                        filteredArgs["point"],
                        if (filteredArgs["x"].isNullOrBlank() || filteredArgs["y"].isNullOrBlank()) filteredArgs["x"] else null,
                        if (!filteredArgs["x"].isNullOrBlank() && !filteredArgs["y"].isNullOrBlank()) {
                            "${filteredArgs["x"]},${filteredArgs["y"]}"
                        } else {
                            null
                        }
                    ).firstOrNull { !it.isNullOrBlank() }
                ).orEmpty()
            "$normalizedAction|point=$point"
        }
        "swipe" -> {
            val start =
                normalizeAutoSoulCoordinatePair(
                    sequenceOf(
                        filteredArgs["start"],
                        if (!filteredArgs["start_x"].isNullOrBlank() && !filteredArgs["start_y"].isNullOrBlank()) {
                            "${filteredArgs["start_x"]},${filteredArgs["start_y"]}"
                        } else {
                            null
                        }
                    ).firstOrNull { !it.isNullOrBlank() }
                ).orEmpty()
            val end =
                normalizeAutoSoulCoordinatePair(
                    sequenceOf(
                        filteredArgs["end"],
                        if (!filteredArgs["end_x"].isNullOrBlank() && !filteredArgs["end_y"].isNullOrBlank()) {
                            "${filteredArgs["end_x"]},${filteredArgs["end_y"]}"
                        } else {
                            null
                        }
                    ).firstOrNull { !it.isNullOrBlank() }
                ).orEmpty()
            "$normalizedAction|$start->$end"
        }
        else -> {
            val sorted = filteredArgs.toSortedMap()
            val tail = sorted.entries.joinToString(separator = "&") { (k, v) -> "$k=$v" }
            "$normalizedAction|$tail"
        }
    }
}

internal fun normalizeAutoSoulCoordinatePair(raw: String?): String? {
    val text =
        raw
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            ?.trim()
            .orEmpty()
    if (text.isBlank()) return null

    val xRaw = Regex("""(?i)\bx\s*[:=]\s*([-+]?\d*\.?\d+%?)""").findAll(text).lastOrNull()?.groupValues?.getOrNull(1)
    val yRaw = Regex("""(?i)\by\s*[:=]\s*([-+]?\d*\.?\d+%?)""").findAll(text).lastOrNull()?.groupValues?.getOrNull(1)
    if (!xRaw.isNullOrBlank() && !yRaw.isNullOrBlank()) {
        val x = normalizeAutoSoulCoordinateToken(xRaw)
        val y = normalizeAutoSoulCoordinateToken(yRaw)
        if (!x.isNullOrBlank() && !y.isNullOrBlank()) return "$x,$y"
    }

    val pairPattern = Regex("""([-+]?\d*\.?\d+%?)\s*[,，]\s*([-+]?\d*\.?\d+%?)""")
    pairPattern.findAll(text).lastOrNull()?.let { match ->
        val x = normalizeAutoSoulCoordinateToken(match.groupValues.getOrNull(1))
        val y = normalizeAutoSoulCoordinateToken(match.groupValues.getOrNull(2))
        if (!x.isNullOrBlank() && !y.isNullOrBlank()) return "$x,$y"
    }

    val compact = text.replace("，", " ").replace(",", " ").trim()
    val parts = compact.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.size == 2) {
        val x = normalizeAutoSoulCoordinateToken(parts[0])
        val y = normalizeAutoSoulCoordinateToken(parts[1])
        if (!x.isNullOrBlank() && !y.isNullOrBlank()) return "$x,$y"
    }
    return null
}

private fun normalizeAutoSoulCoordinateToken(raw: String?): String? {
    val token =
        raw
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            ?.replace(Regex("^[xyXY]\\s*[:=]\\s*"), "")
            ?.trim()
            .orEmpty()
    if (token.isBlank()) return null
    val normalized =
        if (token.endsWith("%")) {
            token.removeSuffix("%").trim().toDoubleOrNull()?.div(100.0)
        } else {
            token.toDoubleOrNull()
        } ?: return null
    val formatted =
        if (kotlin.math.abs(normalized) <= 1.0) {
            String.format(Locale.US, "%.4f", normalized)
        } else {
            String.format(Locale.US, "%.3f", normalized)
        }
    return formatted.trimEnd('0').trimEnd('.').ifBlank { "0" }
}

internal fun normalizeAutoSoulPlannerStep(
    actionRaw: String,
    rawArgs: Map<String, String>
): AutoSoulNormalizedStep {
    val actionHintFromNarrative = extractActionFromAutoSoulNarrative(rawArgs)
    val normalizedAction =
        canonicalizeAutoSoulAction(actionRaw)
            ?.trim()
            .orEmpty()
            .ifBlank { actionHintFromNarrative ?: actionRaw.trim() }
    val normalizedArgs = linkedMapOf<String, String>()
    rawArgs.forEach { (key, value) ->
        val canonicalKey = canonicalizeAutoSoulArgKey(key)
        val canonicalValue = value.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (canonicalKey.isNotBlank() && canonicalValue.isNotBlank()) {
            normalizedArgs[canonicalKey] = canonicalValue
        }
    }
    extractAutoSoulNestedArgs(rawArgs).forEach { (key, value) ->
        val canonicalKey = canonicalizeAutoSoulArgKey(key)
        val canonicalValue = value.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (canonicalKey.isNotBlank() && canonicalValue.isNotBlank()) {
            normalizedArgs.putIfAbsent(canonicalKey, canonicalValue)
        }
    }

    normalizeAutoSoulCoordinateArg(normalizedArgs, targetKey = "point", xKey = "x", yKey = "y")
    normalizeAutoSoulCoordinateArg(normalizedArgs, targetKey = "start", xKey = "start_x", yKey = "start_y")
    normalizeAutoSoulCoordinateArg(normalizedArgs, targetKey = "end", xKey = "end_x", yKey = "end_y")

    if (normalizedAction.equals("Launch", ignoreCase = true) && !hasLaunchTargetArg(normalizedArgs)) {
        resolveLaunchTargetFromArgs(normalizedArgs, includeLoose = true)?.let { inferred ->
            normalizedArgs["app"] = inferred
        }
    }

    val inferredAction = inferAutoSoulActionFromArgs(normalizedArgs)
    val correctedAction: String =
        when {
            normalizedAction.equals("Launch", ignoreCase = true) &&
                !hasLaunchTargetArg(normalizedArgs) &&
                !inferredAction.isNullOrBlank() &&
                !inferredAction.equals("Launch", ignoreCase = true) -> inferredAction.orEmpty()

            normalizedAction.equals("Tap", ignoreCase = true) &&
                normalizedArgs["point"].isNullOrBlank() &&
                (!normalizedArgs["start"].isNullOrBlank() || !normalizedArgs["end"].isNullOrBlank()) -> "Swipe"

            normalizedAction.equals("Swipe", ignoreCase = true) &&
                normalizedArgs["start"].isNullOrBlank() &&
                normalizedArgs["end"].isNullOrBlank() &&
                !normalizedArgs["point"].isNullOrBlank() -> "Tap"

            !actionHintFromNarrative.isNullOrBlank() && normalizedAction.isBlank() ->
                actionHintFromNarrative

            !actionHintFromNarrative.isNullOrBlank() &&
                normalizedAction.equals("Tap", ignoreCase = true) &&
                actionHintFromNarrative.equals("Launch", ignoreCase = true) &&
                hasLaunchTargetArg(normalizedArgs) -> actionHintFromNarrative

            else -> normalizedAction
        }
    val finalAction = canonicalizeAutoSoulAction(correctedAction)?.trim().orEmpty().ifBlank { correctedAction }
    if (finalAction.equals("Tap", ignoreCase = true) || finalAction.equals("LongPress", ignoreCase = true)) {
        val pointRaw =
            sequenceOf(
                normalizedArgs["point"],
                normalizedArgs["x"],
                normalizedArgs["value"],
                normalizedArgs["coord"],
                normalizedArgs["coords"],
                normalizedArgs["position"]
            ).firstOrNull { !it.isNullOrBlank() }
        normalizeAutoSoulCoordinatePair(pointRaw)?.let { normalizedPoint ->
            normalizedArgs["point"] = normalizedPoint
        }
    }
    if (finalAction.equals("Swipe", ignoreCase = true)) {
        val startRaw =
            sequenceOf(
                normalizedArgs["start"],
                normalizedArgs["from"],
                normalizedArgs["start_point"],
                normalizedArgs["from_point"]
            ).firstOrNull { !it.isNullOrBlank() }
        val endRaw =
            sequenceOf(
                normalizedArgs["end"],
                normalizedArgs["to"],
                normalizedArgs["end_point"],
                normalizedArgs["to_point"]
            ).firstOrNull { !it.isNullOrBlank() }
        normalizeAutoSoulCoordinatePair(startRaw)?.let { normalizedStart ->
            normalizedArgs["start"] = normalizedStart
        }
        normalizeAutoSoulCoordinatePair(endRaw)?.let { normalizedEnd ->
            normalizedArgs["end"] = normalizedEnd
        }
    }
    val filteredArgs = filterAutoSoulArgsForAction(finalAction, normalizedArgs)

    return AutoSoulNormalizedStep(
        action = finalAction,
        args = filteredArgs
    )
}

internal fun enforceAutoSoulLaunchWhitelist(
    step: AutoSoulNormalizedStep
): Result<AutoSoulNormalizedStep> {
    if (!step.action.equals("Launch", ignoreCase = true)) {
        return Result.success(step)
    }

    val args = LinkedHashMap(step.args)
    val rawTarget = resolveLaunchTargetFromArgs(args, includeLoose = true)?.trim().orEmpty()
    if (rawTarget.isBlank()) {
        return Result.failure(IllegalStateException("Launch 缺少 app/package 参数。"))
    }

    val resolvedPackage = AutoSoulAppPackages.resolvePackage(rawTarget)
    if (resolvedPackage.isNullOrBlank()) {
        val examples =
            AutoSoulAppPackages.supportedMappings
                .take(6)
                .joinToString(separator = "、") { (name, pkg) -> "$name($pkg)" }
        return Result.failure(
            IllegalStateException(
                "Launch 目标 \"$rawTarget\" 不在 Open-AutoGLM 支持列表中。请改用受支持 app 名或 package，例如：$examples"
            )
        )
    }

    val filtered =
        LinkedHashMap<String, String>().apply {
            put("package", resolvedPackage)
            args["app"]?.trim()?.takeIf { it.isNotBlank() }?.let { put("app", it) }
        }
    return Result.success(
        step.copy(
            action = "Launch",
            args = filtered.toMap()
        )
    )
}

internal fun extractAutoSoulNestedArgs(rawArgs: Map<String, String>): Map<String, String> {
    val extracted = linkedMapOf<String, String>()
    rawArgs.values.forEach { value ->
        val text = value.trim()
        if (text.isBlank()) return@forEach
        appendAutoSoulArgsFromText(extracted, text)
        extractDoCallBodies(text).forEach { body ->
            appendAutoSoulArgsFromText(extracted, body)
        }
        extractPipeCommandBodies(text).forEach { body ->
            appendAutoSoulArgsFromText(extracted, body)
        }
    }
    return extracted
}

internal fun extractActionFromAutoSoulNarrative(rawArgs: Map<String, String>): String? {
    val actionFromArgs =
        rawArgs.entries
            .firstNotNullOfOrNull { (key, value) ->
                if (key.equals("action", ignoreCase = true)) {
                    canonicalizeAutoSoulAction(value)
                } else {
                    null
                }
            }
    if (!actionFromArgs.isNullOrBlank()) return actionFromArgs

    val fromText =
        rawArgs.values.firstNotNullOfOrNull { value ->
            extractActionFromFreeText(value)
        }
    return fromText
}

internal fun extractActionFromFreeText(raw: String): String? {
    val text = raw.trim()
    if (text.isBlank()) return null

    val doActionRegex = Regex("""(?i)do\s*\([^)]*?\baction\s*[:=]\s*["']?([A-Za-z_ ]+)["']?""")
    doActionRegex.findAll(text).lastOrNull()?.groupValues?.getOrNull(1)?.trim()?.let { action ->
        canonicalizeAutoSoulAction(action)?.let { return it }
    }

    val pipeActionRegex = Regex("""(?i)\b(launch|tap|swipe|long[_ ]?press|type|input|wait|back|home|tap[_ ]?text)\b\s*\|""")
    pipeActionRegex.findAll(text).lastOrNull()?.groupValues?.getOrNull(1)?.trim()?.let { action ->
        canonicalizeAutoSoulAction(action)?.let { return it }
    }

    val actionRegex =
        Regex(
            "(?i)\\b(launch|open|start|tap|click|touch|press|swipe|scroll(?:_[a-z]+)?|scrollup|scrollleft|scrollright|scroll_down|long[_ ]?press|longtap|type|input|wait|sleep|delay|back|home|tap[_ ]?text|click[_ ]?text)\\b"
        )
    return actionRegex
        .findAll(text)
        .mapNotNull { match -> canonicalizeAutoSoulAction(match.value) }
        .lastOrNull()
}

internal fun filterAutoSoulArgsForAction(
    action: String,
    args: Map<String, String>
): Map<String, String> {
    val normalizedAction = action.trim().lowercase()
    val genericKeys = setOf("ai_retry", "retry", "retries", "max_retry", "max_retries", "duration", "duration_ms")
    val allowed =
        when (normalizedAction) {
            "launch" -> genericKeys + setOf("app", "package", "target", "name", "app_name")
            "tap" -> genericKeys + setOf("point", "value", "coord", "coords", "position", "x", "y")
            "longpress", "long_press" -> genericKeys + setOf("point", "value", "coord", "coords", "position", "x", "y")
            "swipe" -> genericKeys + setOf(
                "start",
                "end",
                "from",
                "to",
                "start_point",
                "end_point",
                "from_point",
                "to_point",
                "start_x",
                "start_y",
                "end_x",
                "end_y",
                "x1",
                "y1",
                "x2",
                "y2",
                "direction",
                "dir",
                "swipe_direction"
            )
            "type", "input", "taptext", "tap_text" -> genericKeys + setOf("text", "point", "x", "y")
            "wait" -> genericKeys + setOf("duration", "duration_ms")
            "home", "back" -> genericKeys
            else -> args.keys
        }

    val maxValueLength =
        when (normalizedAction) {
            "launch" -> 80
            "tap", "longpress", "long_press", "swipe" -> 96
            else -> 220
        }

    val filtered =
        args
        .filter { (key, value) ->
            val normalizedKey = canonicalizeAutoSoulArgKey(key)
            normalizedKey in allowed && value.trim().isNotBlank() && value.trim().length <= maxValueLength
        }
        .toMap(LinkedHashMap())
        .toMutableMap()

    if ((normalizedAction == "tap" || normalizedAction == "longpress" || normalizedAction == "long_press")) {
        normalizeAutoSoulCoordinatePair(filtered["point"])?.let { normalizedPoint ->
            filtered["point"] = normalizedPoint
        }
        if (!filtered["point"].isNullOrBlank()) {
            filtered.remove("x")
            filtered.remove("y")
            filtered.remove("value")
            filtered.remove("coord")
            filtered.remove("coords")
            filtered.remove("position")
        }
    }

    if (normalizedAction == "swipe") {
        normalizeAutoSoulCoordinatePair(filtered["start"])?.let { normalizedStart ->
            filtered["start"] = normalizedStart
        }
        normalizeAutoSoulCoordinatePair(filtered["end"])?.let { normalizedEnd ->
            filtered["end"] = normalizedEnd
        }
    }

    return filtered.toMap(LinkedHashMap())
}

internal fun extractDoCallBodies(raw: String): List<String> {
    val text = raw.trim()
    if (text.isBlank()) return emptyList()
    val matches = Regex("""(?is)do\s*\((.*?)\)""").findAll(text)
    return matches
        .mapNotNull { match ->
            match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
        .toList()
}

internal fun extractPipeCommandBodies(raw: String): List<String> {
    val text = raw.trim()
    if (text.isBlank()) return emptyList()
    val regex =
        Regex(
            """(?i)\b(?:launch|tap|swipe|long[_ ]?press|type|input|wait|back|home|tap[_ ]?text)\b\s*\|\s*([^\n\r]+)"""
        )
    return regex
        .findAll(text)
        .mapNotNull { match ->
            match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
        .toList()
}

internal fun canonicalizeAutoSoulArgKey(rawKey: String): String {
    val normalized =
        rawKey
            .trim()
            .lowercase()
            .replace("-", "_")
            .replace(" ", "_")
    return when (normalized) {
        "durationms", "duration_millis", "duration_milliseconds", "durationmillis", "durationmilliseconds" -> "duration_ms"
        "element" -> "point"
        "pkg", "packagename", "package_name", "bundle", "bundle_id" -> "package"
        "appname", "app_name", "application", "application_name" -> "app"
        "startx", "fromx", "x1" -> "start_x"
        "starty", "fromy", "y1" -> "start_y"
        "endx", "tox", "x2" -> "end_x"
        "endy", "toy", "y2" -> "end_y"
        "taptext", "tap_text", "clicktext", "click_text", "tap_label" -> "text"
        else -> normalized
    }
}

internal fun normalizeAutoSoulCoordinateArg(
    args: MutableMap<String, String>,
    targetKey: String,
    xKey: String,
    yKey: String
) {
    val existed = args[targetKey]?.trim().orEmpty()
    if (existed.isNotBlank()) return
    val x = args[xKey]?.trim().orEmpty()
    val y = args[yKey]?.trim().orEmpty()
    if (x.isBlank() || y.isBlank()) return
    args[targetKey] = "$x,$y"
}

internal fun inferAutoSoulActionFromArgs(args: Map<String, String>): String? {
    if (hasLaunchTargetArg(args)) return "Launch"
    val hasSwipeSignal =
        !args["start"].isNullOrBlank() ||
            !args["end"].isNullOrBlank() ||
            !args["direction"].isNullOrBlank() ||
            !args["dir"].isNullOrBlank() ||
            !args["swipe_direction"].isNullOrBlank()
    if (hasSwipeSignal) return "Swipe"
    if (!args["point"].isNullOrBlank()) return "Tap"
    if (!args["text"].isNullOrBlank()) return "Type"
    return null
}

internal fun hasLaunchTargetArg(args: Map<String, String>): Boolean {
    return !resolveLaunchTargetFromArgs(args, includeLoose = false).isNullOrBlank()
}

internal fun resolveLaunchTargetFromArgs(
    args: Map<String, String>,
    includeLoose: Boolean
): String? {
    val candidates =
        buildList {
            add(args["package"])
            add(args["app"])
            add(args["target"])
            add(args["name"])
            if (includeLoose) {
                add(args["value"])
                add(args["text"])
            }
        }
    return candidates
        .asSequence()
        .map { it?.trim().orEmpty() }
        .firstOrNull { candidate ->
            candidate.isNotBlank() &&
                candidate.length <= 120 &&
                !looksLikeAutoSoulCoordinateValue(candidate)
        }
}

internal fun looksLikeAutoSoulCoordinateValue(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false
    if (Regex("""^[-+]?\d*\.?\d+%?$""").matches(trimmed)) return true
    if (Regex("""^\[?\(?\s*[-+]?\d*\.?\d+%?\s*[,，]\s*[-+]?\d*\.?\d+%?\s*\)?\]?$""").matches(trimmed)) return true
    return false
}

internal fun buildAutoSoulPlannerSystemPrompt(): String {
    return buildString {
        appendLine("你是 AutoSoul 执行智能体（规则对齐 Open-AutoGLM Android Agent）。")
        appendLine("目标：基于任务目标和历史执行结果，每一轮只输出一个“下一步动作”，直到任务完成。")
        appendLine("输出必须是 JSON 对象，不要 Markdown，不要代码块，不要多余解释。")
        appendLine()
        appendLine("动作定义：")
        appendLine("- Launch：启动目标应用。")
        appendLine("- Tap：点击坐标点。")
        appendLine("- Swipe：从 start 滑动到 end。")
        appendLine("- LongPress：长按坐标点。")
        appendLine("- Type：在当前输入框输入 text。")
        appendLine("- TapText：点击包含指定 text 的控件。")
        appendLine("- Back / Home / Wait。")
        appendLine()
        appendLine("关键规则：")
        appendLine("1. 每轮只给一个动作，全部要求完成后才能输出 done。")
        appendLine("2. 若当前不在目标 app，优先 Launch；若页面无关或卡死，优先 Back 或 Wait 再调整。")
        appendLine("3. 坐标统一使用 0~1 比例，Tap/LongPress 用 point，Swipe 必须同时给 start 与 end。")
        appendLine("4. Launch 只能使用 app/package，禁止携带 point/start/end 坐标参数。")
        appendLine("5. Launch 必须命中白名单包名；如果输入是 app 名，先映射为 package 再输出。")
        appendLine("6. 不要连续重复同一成功动作导致循环（尤其 Home/Back/Wait/Tap 同坐标）。")
        appendLine("7. 如果上一步失败，先换策略重试；确实无法完成时再 done 并说明原因。")
        appendLine()
        appendLine("允许应用包名白名单（app => package，来自 Open-AutoGLM）：")
        appendLine(AutoSoulAppPackages.renderWhitelistLines())
        appendLine()
        appendLine("输出格式：")
        appendLine("- 完成任务：{\"status\":\"done\",\"message\":\"...\"}")
        appendLine("- 继续执行：{\"status\":\"continue\",\"step\":{\"action\":\"Launch\",\"package\":\"com.tencent.mm\"},\"reason\":\"...\"}")
        appendLine("- 继续执行（点击示例）：{\"status\":\"continue\",\"step\":{\"action\":\"Tap\",\"point\":\"0.50,0.72\"},\"reason\":\"...\"}")
    }.trim()
}

internal fun JsonObject.readString(key: String): String? {
    val element = get(key) ?: return null
    return element.toAutoSoulValueString()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.readBoolean(key: String): Boolean? {
    val element = get(key) ?: return null
    val primitive = element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
    return when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isString -> primitive.asString.trim().equals("true", ignoreCase = true)
        primitive.isNumber -> primitive.asInt != 0
        else -> null
    }
}

internal fun JsonObject.readObject(key: String): JsonObject? {
    val element = get(key) ?: return null
    return if (element.isJsonObject) element.asJsonObject else null
}

internal fun parseJsonObjectLenient(candidate: String): JsonObject? {
    val trimmed = candidate.trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

    runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull()?.let { return it }
    runCatching {
        val reader = JsonReader(StringReader(trimmed)).apply { isLenient = true }
        val element = JsonParser.parseReader(reader)
        if (element.isJsonObject) element.asJsonObject else null
    }.getOrNull()?.let { return it }
    if ('\'' in trimmed && '"' !in trimmed) {
        runCatching { JsonParser.parseString(trimmed.replace('\'', '"')).asJsonObject }.getOrNull()?.let { return it }
    }
    return null
}

internal fun canonicalizeAutoSoulAction(raw: String?): String? {
    val normalized = raw?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_").orEmpty()
    if (normalized.isBlank()) return null
    return when (normalized) {
        "launch", "open", "start", "app_launch" -> "Launch"
        "tap", "click", "touch", "press", "tap_screen" -> "Tap"
        "swipe", "scroll", "scrollup", "scroll_up", "scroll_down", "scrollleft", "scroll_left", "scrollright", "scroll_right",
        "swipe_up", "swipe_down", "swipe_left", "swipe_right" -> "Swipe"
        "longpress", "long_press", "long_tap", "longtap", "hold", "press_hold" -> "LongPress"
        "type", "input", "type_text", "enter_text", "text" -> "Type"
        "wait", "sleep", "delay", "pause" -> "Wait"
        "back", "return" -> "Back"
        "home", "homepage" -> "Home"
        "taptext", "tap_text", "clicktext", "click_text", "tap_label" -> "TapText"
        else -> raw?.trim()?.takeIf { it.isNotBlank() }
    }
}

internal fun appendAutoSoulArgsFromObject(
    target: MutableMap<String, String>,
    source: JsonObject?
) {
    source?.entrySet()?.forEach { (key, value) ->
        value.toAutoSoulValueString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { target.putIfAbsent(key, it) }
    }
}

internal fun appendAutoSoulArgsFromText(
    target: MutableMap<String, String>,
    raw: String?
) {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return
    val normalizedText =
        text
            .replace("，", ",")
            .replace("；", ";")
            .replace("：", ":")
    parseAutoSoulInlineArgs(normalizedText).forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        if (key.isBlank()) return@forEach
        val normalizedKey =
            when (key.lowercase()) {
                "durationms", "duration-millis", "duration_millis" -> "duration_ms"
                "taptext", "tap_text", "clicktext", "click_text" -> "text"
                else -> key
            }
        if (
            normalizedKey.equals("status", ignoreCase = true) ||
            normalizedKey.equals("done", ignoreCase = true) ||
            normalizedKey.equals("message", ignoreCase = true) ||
            normalizedKey.equals("reason", ignoreCase = true) ||
            normalizedKey.equals("action", ignoreCase = true) ||
            normalizedKey.equals("step", ignoreCase = true)
        ) {
            return@forEach
        }
        val value = rawValue.removeSurrounding("\"").removeSurrounding("'").trim()
        if (value.isNotBlank()) {
            target.putIfAbsent(normalizedKey, value)
        }
    }
    if (!target.containsKey("point")) {
        val coordMatch = Regex("""([-+]?\d*\.?\d+%?)\s*[,，\s]\s*([-+]?\d*\.?\d+%?)""").find(normalizedText)
        if (coordMatch != null) {
            val x = coordMatch.groupValues.getOrNull(1)?.trim().orEmpty()
            val y = coordMatch.groupValues.getOrNull(2)?.trim().orEmpty()
            if (x.isNotBlank() && y.isNotBlank()) {
                target["point"] = "$x,$y"
            }
        }
    }
}

internal fun parseAutoSoulInlineArgs(text: String): List<Pair<String, String>> {
    if (text.isBlank()) return emptyList()
    val result = mutableListOf<Pair<String, String>>()
    var cursor = 0
    val length = text.length

    while (cursor < length) {
        while (cursor < length && (text[cursor].isWhitespace() || text[cursor] == ',' || text[cursor] == ';')) {
            cursor++
        }
        if (cursor >= length) break

        if (!isAutoSoulKeyStartChar(text[cursor])) {
            cursor++
            continue
        }

        val keyStart = cursor
        cursor++
        while (cursor < length && isAutoSoulKeyBodyChar(text[cursor])) {
            cursor++
        }
        val key = text.substring(keyStart, cursor).trim()
        if (key.isBlank()) continue

        while (cursor < length && text[cursor].isWhitespace()) {
            cursor++
        }
        if (cursor >= length || (text[cursor] != ':' && text[cursor] != '=')) {
            continue
        }
        cursor++
        while (cursor < length && text[cursor].isWhitespace()) {
            cursor++
        }
        if (cursor >= length) {
            result += key to ""
            break
        }

        val valueStart = cursor
        var quote: Char? = null
        var bracketDepth = 0
        var parenDepth = 0
        var braceDepth = 0

        while (cursor < length) {
            val ch = text[cursor]
            if (quote != null) {
                if (ch == quote && (cursor == valueStart || text[cursor - 1] != '\\')) {
                    quote = null
                }
                cursor++
                continue
            }
            when (ch) {
                '"', '\'' -> quote = ch
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
                ',', ';' -> {
                    if (bracketDepth == 0 && parenDepth == 0 && braceDepth == 0) {
                        if (looksLikeNextAutoSoulKey(text, cursor + 1)) {
                            break
                        }
                    }
                }
            }
            cursor++
        }

        val value = text.substring(valueStart, cursor).trim()
        result += key to value
    }

    return result
}

internal fun looksLikeNextAutoSoulKey(text: String, startIndex: Int): Boolean {
    val length = text.length
    var cursor = startIndex
    while (cursor < length && text[cursor].isWhitespace()) {
        cursor++
    }
    if (cursor >= length || !isAutoSoulKeyStartChar(text[cursor])) {
        return false
    }

    cursor++
    while (cursor < length && isAutoSoulKeyBodyChar(text[cursor])) {
        cursor++
    }
    while (cursor < length && text[cursor].isWhitespace()) {
        cursor++
    }
    return cursor < length && (text[cursor] == ':' || text[cursor] == '=')
}

internal fun isAutoSoulKeyStartChar(ch: Char): Boolean {
    return ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z'
}

internal fun isAutoSoulKeyBodyChar(ch: Char): Boolean {
    return ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'
}

internal fun parseAutoSoulLooseDecision(raw: String): AutoSoulPlannerDecision? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    val doneSignal =
        Regex("(?i)\\b(done|finish(?:ed)?|complete(?:d)?|stop)\\b|完成|结束|停止")
            .containsMatchIn(trimmed)
    val action = extractActionFromFreeText(trimmed)
    if (action == null) {
        return if (doneSignal) {
            AutoSoulPlannerDecision(
                done = true,
                message = trimmed.take(180),
                action = null,
                args = emptyMap(),
                reason = null
            )
        } else {
            null
        }
    }

    val args = linkedMapOf<String, String>()
    appendAutoSoulArgsFromText(args, trimmed)
    return AutoSoulPlannerDecision(
        done = false,
        message = null,
        action = action,
        args = args,
        reason = null
    )
}

internal fun parseAutoSoulDoFinishDecision(raw: String): AutoSoulPlannerDecision? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    val finishRegex = Regex("""(?is)\bfinish\s*\(\s*message\s*[:=]\s*["']?(.+?)["']?\s*\)""")
    finishRegex.findAll(trimmed).lastOrNull()?.let { match ->
        val message = match.groupValues.getOrNull(1)?.trim().orEmpty().ifBlank { "任务结束" }
        return AutoSoulPlannerDecision(
            done = true,
            message = message.take(240),
            action = null,
            args = emptyMap(),
            reason = null
        )
    }

    val doBodies = extractDoCallBodies(trimmed)
    val body = doBodies.lastOrNull()?.trim().orEmpty()
    if (body.isBlank()) return null

    val parsed = linkedMapOf<String, String>()
    appendAutoSoulArgsFromText(parsed, body)
    val action =
        canonicalizeAutoSoulAction(
            parsed["action"]
                ?: parsed["tool"]
                ?: parsed["name"]
                ?: parsed["type"]
        ) ?: return null

    parsed.remove("action")
    parsed.remove("tool")
    parsed.remove("name")
    parsed.remove("type")
    parsed.remove("status")
    parsed.remove("done")
    parsed.remove("message")

    return AutoSoulPlannerDecision(
        done = false,
        message = null,
        action = action,
        args = parsed,
        reason = null
    )
}

internal fun JsonElement.toAutoSoulValueString(): String? {
    if (isJsonNull) return null
    if (isJsonPrimitive) {
        val primitive = asJsonPrimitive
        return when {
            primitive.isString -> primitive.asString
            primitive.isBoolean -> primitive.asBoolean.toString()
            primitive.isNumber -> primitive.asNumber.toString()
            else -> primitive.toString()
        }
    }
    return toString()
}

@Composable
fun ToolListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontFamily = SourceSans3,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

internal data class MentionCandidate(
    val key: String,
    val label: String,
    val token: String
)

private object MentionHighlightVisualTransformation : VisualTransformation {
    private val mentionRegex = Regex("""@[\p{L}\p{N}_\-.]+""")

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = AnnotatedString.Builder(text.text)
        mentionRegex.findAll(text.text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start >= 0 && end <= text.length) {
                styled.addStyle(
                    style = SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Bold),
                    start = start,
                    end = end
                )
            }
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

internal fun bottomInputBottomPadding(imeVisible: Boolean): Dp {
    return if (imeVisible) 14.dp else 10.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomInputArea(
    selectedTool: String?,
    mcpSelectedCount: Int,
    webSearchEngine: String,
    attachments: List<PendingImageAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    onToolToggle: () -> Unit,
    onClearTool: () -> Unit,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopStreaming: () -> Unit,
    sendAllowed: Boolean = true,
    sendBusy: Boolean = false,
    isStreaming: Boolean = false,
    imeVisible: Boolean = false,
    onInputFocusChanged: (Boolean) -> Unit = {},
    actionActiveColor: Color = TextPrimary
) {
    val hasText = messageText.trim().isNotEmpty()
    val hasAttachments = attachments.isNotEmpty()
    val sendEnabled = (hasText || hasAttachments) && sendAllowed && !sendBusy
    val actionIsStop = isStreaming
    val actionEnabled = if (actionIsStop) true else sendEnabled
    val actionBackground by animateColorAsState(
        targetValue =
            when {
                actionIsStop -> actionActiveColor
                sendEnabled -> actionActiveColor
                else -> GrayLight
            },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )
    val actionIconTint by animateColorAsState(
        targetValue = if (sendEnabled) Color.White else TextSecondary,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
    )
    val maxTextHeight = if (selectedTool != null) 140.dp else 120.dp
    val maxLines = if (selectedTool != null) 6 else 5
    val toolLabel = when (selectedTool) {
        "files" -> "Files"
        "web" -> "Search · ${displaySearchEngineName(webSearchEngine)}"
        "image" -> "Image"
        "autosoul" -> stringResource(R.string.settings_item_autosoul)
        "autobrowser" -> stringResource(R.string.settings_item_autobrowser)
        "mcp" ->
            if (mcpSelectedCount > 0) {
                stringResource(R.string.chat_mcp_selected_count, mcpSelectedCount)
            } else {
                stringResource(R.string.settings_item_mcp_tools)
            }
        "app_builder" -> stringResource(R.string.chat_tool_app_builder)
        else -> selectedTool?.replaceFirstChar { it.uppercase() }.orEmpty()
    }
    val toolIconRes: Int? = when (selectedTool) {
        "files" -> R.drawable.ic_files
        "autosoul" -> R.drawable.ic_autosoul
        else -> null
    }
    val toolIconVector = when (selectedTool) {
        "files" -> null
        "web" -> AppIcons.Globe
        "image" -> AppIcons.CreateImage
        "autosoul" -> null
        "autobrowser" -> AppIcons.AutoBrowserCompass
        "mcp" -> AppIcons.MCPTools
        "app_builder" -> AppIcons.AppDeveloper
        else -> AppIcons.Globe
    }
    val inputCapsuleShape = RoundedCornerShape(23.dp)
    val inputContainerColor = Surface
    val toolButtonBrush = remember(imeVisible) {
        Brush.verticalGradient(
            colors =
                if (imeVisible) {
                    listOf(
                        Surface.copy(alpha = 0.985f),
                        Surface.copy(alpha = 0.95f)
                    )
                } else {
                    listOf(
                        Surface.copy(alpha = 0.99f),
                        Surface.copy(alpha = 0.95f)
                    )
                }
        )
    }
    val bottomPadding = bottomInputBottomPadding(imeVisible)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = bottomPadding)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
            // 工具图标按钮 - 46dp，与输入框等高
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(CircleShape)
                    .background(toolButtonBrush, CircleShape)
                    .pressableScale(pressedScale = 0.95f, onClick = onToolToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.ChatGPTLogo,
                    contentDescription = stringResource(R.string.settings_item_mcp_tools),
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 输入框容器 - 默认46dp与工具按钮对齐，可多行扩展；发送按钮固定右下角
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .shadow(elevation = 8.dp, shape = inputCapsuleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(inputCapsuleShape)
                    .background(inputContainerColor, inputCapsuleShape),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
                        .padding(start = 12.dp, end = 48.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    AnimatedVisibility(
                        visible = attachments.isNotEmpty(),
                        enter =
                            fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                        exit =
                            fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                                shrinkVertically(animationSpec = tween(160, easing = FastOutSlowInEasing))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                attachments.forEachIndexed { index, attachment ->
                                    Box(
                                        modifier = Modifier
                                            .size(92.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(GrayLighter),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = attachment.uri ?: attachment.bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.92f))
                                                .pressableScale(
                                                    pressedScale = 0.92f,
                                                    onClick = { onRemoveAttachment(index) }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = "Remove",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedTool != null,
                        enter =
                            fadeIn(animationSpec = tween(140, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                        exit =
                            fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing)) +
                                shrinkVertically(animationSpec = tween(140, easing = FastOutSlowInEasing))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .offset(x = (-4).dp)
                                    .background(Color(0xFFE8F4FD), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (toolIconRes != null) {
                                    Icon(
                                        painter = rememberResourceDrawablePainter(toolIconRes),
                                        contentDescription = null,
                                        tint = Color(0xFF007AFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = toolIconVector ?: AppIcons.Globe,
                                        contentDescription = null,
                                        tint = Color(0xFF007AFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = toolLabel,
                                    fontSize = 15.sp,
                                    color = Color(0xFF007AFF),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.offset(y = (-1).dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .pressableScale(
                                            pressedScale = 0.95f,
                                            onClick = onClearTool
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF007AFF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // 输入框 - 多行自动增高，最大高度120dp
                    BasicTextField(
                        value = messageText,
                        onValueChange = onMessageChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state -> onInputFocusChanged(state.isFocused) }
                            .heightIn(min = 24.dp, max = maxTextHeight),
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            color = TextPrimary
                        ),
                        visualTransformation = MentionHighlightVisualTransformation,
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = if (actionIsStop) ImeAction.Done else ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { if (sendEnabled) onSend() },
                            onDone = { if (actionIsStop) onStopStreaming() }
                        ),
                        minLines = 1,
                        maxLines = maxLines,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (messageText.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.chat_placeholder_ask_anything),
                                        fontSize = 17.sp,
                                        lineHeight = 22.sp,
                                        color = TextSecondary
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // 发送按钮 - 固定在右下角（随输入框高度一起上移）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(actionBackground, CircleShape)
                        .zIndex(2f)
                        .pressableScale(
                            enabled = actionEnabled,
                            pressedScale = if (actionIsStop) 0.93f else 0.95f,
                            onClick = {
                                if (actionIsStop) onStopStreaming() else onSend()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = actionIsStop,
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = tween(130, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        initialScale = 0.84f,
                                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                                    )
                                ) togetherWith
                                (
                                    fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing)) +
                                        scaleOut(
                                            targetScale = 0.9f,
                                            animationSpec = tween(130, easing = FastOutSlowInEasing)
                                        )
                                    )
                        }
                    ) { stopMode ->
                        if (stopMode) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White)
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.Send,
                                contentDescription = "Send",
                                tint = actionIconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MentionPickerFloatingPanel(
    visible: Boolean,
    mentionCandidates: List<MentionCandidate>,
    onMentionSelect: (MentionCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleMentionItems = remember(mentionCandidates) { mentionCandidates.take(6) }
    val mentionPopupShape = RoundedCornerShape(16.dp)
    val mentionItemShape = RoundedCornerShape(12.dp)
    androidx.compose.animation.AnimatedVisibility(
        visible = visible && visibleMentionItems.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(150, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(110, easing = FastOutSlowInEasing))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = mentionPopupShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(mentionPopupShape)
                .background(Color.White, mentionPopupShape)
                .border(1.dp, Color.Black.copy(alpha = 0.05f), mentionPopupShape)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            visibleMentionItems.forEachIndexed { index, candidate ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(mentionItemShape)
                        .background(Color.White, mentionItemShape)
                        .pressableScale(
                            pressedScale = 0.98f,
                            onClick = { onMentionSelect(candidate) }
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = candidate.label,
                        color = Color(0xFF007AFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (index < visibleMentionItems.lastIndex) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

}

internal suspend fun encodeImageAttachmentToDataUrl(
    context: Context,
    attachment: PendingImageAttachment,
    maxDimension: Int = 1024,
    jpegQuality: Int = 85
): String? {
    return withContext(Dispatchers.IO) {
        val bitmap =
            attachment.bitmap
                ?: attachment.uri?.let { uri -> decodeBitmapFromUri(context, uri, maxDimension) }
                ?: return@withContext null

        val scaled = scaleBitmapDown(bitmap, maxDimension)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    }
}

internal fun decodeBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

    resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val opts =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
        }

    return resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, opts)
    }
}

internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    val maxDim = maxOf(width, height)
    if (maxDim <= maxDimension) return 1
    var sample = 1
    while (maxDim / sample > maxDimension) {
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}

internal fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val maxDim = maxOf(width, height)
    if (maxDim <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / maxDim.toFloat()
    val newWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val newHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

internal fun normalizeMentionLookup(value: String): String {
    return value.trim().lowercase()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")
}

internal fun buildGroupMentionToken(model: ModelConfig): String {
    val remote = extractRemoteModelId(model.id).trim()
    return remote.ifBlank { model.displayName.trim().replace(Regex("\\s+"), "_") }
}

internal fun extractTrailingMentionQuery(input: String): String? {
    if (input.isEmpty()) return null
    val atIndex = input.lastIndexOf('@')
    if (atIndex < 0) return null
    if (atIndex > 0 && !input[atIndex - 1].isWhitespace()) return null
    val suffix = input.substring(atIndex + 1)
    if (suffix.contains(' ') || suffix.contains('\n') || suffix.contains('\t')) return null
    return suffix
}

internal fun replaceTrailingMentionWithToken(input: String, token: String): String {
    val safeToken = token.trim().trimStart('@')
    if (safeToken.isBlank()) return input
    val atIndex = input.lastIndexOf('@')
    if (atIndex >= 0 && (atIndex == 0 || input[atIndex - 1].isWhitespace())) {
        val suffix = input.substring(atIndex + 1)
        if (!suffix.contains(' ') && !suffix.contains('\n') && !suffix.contains('\t')) {
            return input.substring(0, atIndex) + "@$safeToken "
        }
    }
    val base = input.trimEnd()
    return if (base.isBlank()) "@$safeToken " else "$base @$safeToken "
}

internal fun extractMentionTokens(text: String): Set<String> {
    val regex = Regex("""@([\p{L}\p{N}_\-.]+)""")
    return regex.findAll(text).mapNotNull { match ->
        match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }.toSet()
}

internal fun buildConversationTranscript(
    messages: List<Message>,
    maxMessages: Int,
    maxCharsPerMessage: Int
): String {
    if (messages.isEmpty()) return ""
    val tail = messages.takeLast(maxMessages)
    return buildString {
        tail.forEach { msg ->
            val prefix =
                when (msg.role) {
                    "user" -> "User"
                    "assistant" -> "Assistant"
                    else -> msg.role.replaceFirstChar { it.uppercase() }
                }
            append(prefix)
            append(": ")
            val cleanContent = stripMcpTagMarkers(msg.content)
            append(cleanContent.replace('\n', ' ').trim().take(maxCharsPerMessage))
            append('\n')
        }
    }.trim()
}

private suspend fun collectStreamContent(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    messages: List<Message>,
    extraHeaders: List<HttpHeader>,
    onChunk: ((String) -> Unit)? = null
): String {
    val sb = StringBuilder()
    chatApiClient.chatCompletionsStream(
        provider = provider,
        modelId = modelId,
        messages = messages,
        extraHeaders = extraHeaders
    ).collect { delta ->
        delta.content?.let { chunk ->
            sb.append(chunk)
            onChunk?.invoke(chunk)
        }
    }
    return sb.toString().trim()
}

private fun stripMarkdownCodeFences(text: String): String {
    var t = text.trim()
    if (t.startsWith("```")) {
        t = t.substringAfter('\n', t).trim()
    }
    if (t.endsWith("```")) {
        t = t.dropLast(3).trim()
    }
    return t.trim()
}

internal fun parseJsonStringArray(text: String): List<String> {
    val cleaned = stripMarkdownCodeFences(text).trim()
    if (cleaned.isBlank()) return emptyList()

    val candidate = run {
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }

    val element = runCatching { JsonParser.parseString(candidate) }.getOrNull() ?: return emptyList()
    val array =
        when {
            element.isJsonArray -> element.asJsonArray
            element.isJsonObject -> element.asJsonObject.getAsJsonArray("memories")
            else -> null
        } ?: return emptyList()

    return array.mapNotNull { el ->
        runCatching { el.asString }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }
}

internal data class AppDevToolSpec(
    val mode: String,
    val name: String,
    val description: String,
    val appIcon: String,
    val style: String,
    val features: List<String>,
    val targetAppId: String? = null,
    val targetAppName: String? = null,
    val editRequest: String? = null
)

internal data class AppDevTagPayload(
    val name: String,
    val subtitle: String,
    val description: String,
    val appIcon: String = "app-window",
    val style: String,
    val features: List<String>,
    val progress: Int,
    val status: String,
    val html: String,
    val error: String? = null,
    val sourceAppId: String? = null,
    val mode: String = "create",
    val deployUrl: String? = null,
    val deployError: String? = null,
    val runtimeStatus: String? = null,
    val runtimeMessage: String? = null,
    val runtimeRunUrl: String? = null,
    val runtimeArtifactName: String? = null,
    val runtimeArtifactUrl: String? = null
)

internal data class McpServerPickerItem(
    val serverId: String,
    val serverName: String,
    val subtitle: String,
    val toolCount: Int
)

internal fun groupEnabledModelsByProvider(
    providers: List<ProviderConfig>,
    models: List<ModelConfig>
): List<Pair<String, List<ModelConfig>>> {
    if (models.isEmpty()) return emptyList()
    val enabledModels = models.filter { it.enabled }
    if (enabledModels.isEmpty()) return emptyList()

    val providerById = providers.associateBy { it.id }
    val groupedByProviderId = enabledModels.groupBy { it.providerId?.trim().orEmpty() }
    val ordered = mutableListOf<Pair<String, List<ModelConfig>>>()
    val usedProviderIds = mutableSetOf<String>()

    providers.forEach { provider ->
        val providerId = provider.id.trim()
        if (providerId.isBlank()) return@forEach
        val providerModels = groupedByProviderId[providerId].orEmpty()
        if (providerModels.isEmpty()) return@forEach
        usedProviderIds += providerId
        ordered += provider.name to providerModels.sortedBy { it.displayName.lowercase() }
    }

    groupedByProviderId.forEach { (providerId, providerModels) ->
        if (providerModels.isEmpty()) return@forEach
        if (providerId in usedProviderIds) return@forEach
        val fallbackName =
            providerById[providerId]?.name
                ?: providerId.takeIf { it.isNotBlank() }
                ?: "Other"
        ordered += fallbackName to providerModels.sortedBy { it.displayName.lowercase() }
    }

    return ordered
}

internal fun isSameStoredOrRemoteModelId(lhs: String?, rhs: String?): Boolean {
    val left = lhs?.trim().orEmpty()
    val right = rhs?.trim().orEmpty()
    if (left.isBlank() || right.isBlank()) return false
    if (left == right) return true
    return extractRemoteModelId(left) == extractRemoteModelId(right)
}

internal fun ProviderConfig.isQwenCodeProvider(): Boolean {
    val oauth = oauthProvider?.trim().orEmpty()
    val preset = presetId?.trim().orEmpty()
    val typeValue = type.trim()
    return oauth.equals("qwen", ignoreCase = true) ||
        preset.equals("qwen_code", ignoreCase = true) ||
        typeValue.equals("qwen", ignoreCase = true) ||
        apiUrl.contains("portal.qwen.ai", ignoreCase = true) ||
        apiUrl.contains("chat.qwen.ai", ignoreCase = true)
}

internal data class PlannedMcpToolCall(
    val serverId: String,
    val toolName: String,
    val arguments: Map<String, Any?>
)

internal fun buildMcpServerPickerItems(servers: List<McpConfig>): List<McpServerPickerItem> {
    return servers
        .sortedBy { it.name.trim().lowercase() }
        .map { server ->
            val toolCount = server.tools.size
            val subtitle =
                when {
                    server.description.isNotBlank() -> server.description.trim().take(80)
                    toolCount > 0 -> "$toolCount tools available"
                    else -> "No tools synced"
                }
            McpServerPickerItem(
                serverId = server.id,
                serverName = server.name.trim().ifBlank { "Unnamed MCP" },
                subtitle = subtitle,
                toolCount = toolCount
            )
        }
}

internal fun displaySearchEngineName(raw: String): String {
    return when (raw.trim().lowercase()) {
        "exa" -> "Exa"
        "tavily" -> "Tavily"
        "linkup" -> "Linkup"
        else -> "Bing"
    }
}

internal fun shouldAutoSearchForPrompt(userPrompt: String): Boolean {
    val text = userPrompt.trim().lowercase()
    if (text.isBlank()) return false

    val timeSensitivePatterns =
        listOf(
            Regex("(?i)\\b(latest|today|recent|current|news|breaking|update|live|price|stock|weather|score|schedule|release)\\b"),
            Regex("(最新|今天|最近|当前|新闻|快讯|实时|价格|股价|天气|比分|赛程|发布)")
        )
    val lookupPatterns =
        listOf(
            Regex("(?i)\\b(search|look up|find|check|verify|compare|summarize)\\b"),
            Regex("(搜索|查一下|查找|检索|核实|对比|汇总)")
        )
    val questionHint =
        text.endsWith("?") ||
            text.endsWith("？") ||
            Regex("(?i)\\b(what|who|when|where|how|is|are|can|could|should|which)\\b").containsMatchIn(text) ||
            Regex("(什么|谁|何时|哪里|怎么|是否|能否|哪个)").containsMatchIn(text)

    val hasTimeSensitive = timeSensitivePatterns.any { it.containsMatchIn(text) }
    val hasLookupIntent = lookupPatterns.any { it.containsMatchIn(text) }
    return hasTimeSensitive || (hasLookupIntent && questionHint)
}

internal fun shouldEnableAppBuilderForPrompt(userPrompt: String): Boolean {
    val text = userPrompt.trim().lowercase()
    if (text.isBlank()) return false
    val actionRegexes =
        listOf(
            Regex("(?i)\\b(create|build|develop|make|edit|update|revise|modify|refactor|improve)\\b"),
            Regex("(创建|开发|制作|做一个|做个|修改|编辑|更新|优化|重构)")
        )
    val targetRegexes =
        listOf(
            Regex("(?i)\\b(app|website|web app|html app|single page app|landing page|page)\\b"),
            Regex("(应用|网页|网站|页面|小程序)")
        )
    val nonBuildQuestionRegexes =
        listOf(
            Regex("(?i)^\\s*(what|why|who|when|where|how)\\b"),
            Regex("(?i)\\b(what is|how to|why is|can you explain|tutorial|concept)\\b"),
            Regex("^(什么是|为什么|怎么|如何|能否讲解|解释一下)")
        )
    val hasAction = actionRegexes.any { it.containsMatchIn(text) }
    val hasTarget = targetRegexes.any { it.containsMatchIn(text) }
    if (!hasAction || !hasTarget) return false
    if (text.endsWith("?") || text.endsWith("？")) {
        return nonBuildQuestionRegexes.none { it.containsMatchIn(text) }
    }
    return nonBuildQuestionRegexes.none { it.containsMatchIn(text) }
}

internal fun isAppBuilderConfirmationReply(userPrompt: String): Boolean {
    val text = userPrompt.trim().lowercase()
    if (text.isBlank()) return false
    val keywords =
        listOf(
            "确认应用开发",
            "确认开发",
            "开始应用开发",
            "开始开发",
            "继续开发",
            "继续应用开发",
            "ok",
            "yes",
            "confirm"
        )
    return keywords.any { key -> text == key || text.contains(key) }
}

internal fun buildAppBuilderConfirmationMessage(userPrompt: String): String {
    val summary = userPrompt.trim().replace(Regex("\\s+"), " ").take(90)
    return "检测到你可能想开发应用。回复“确认应用开发”我将进入 APP developer 流程并生成可运行 HTML；也可以继续补充需求。\n识别到的请求：$summary"
}

internal fun isBuiltInAppDeveloperCall(call: PlannedMcpToolCall): Boolean {
    val server = call.serverId.trim().lowercase()
    val tool = call.toolName.trim().lowercase()
    return tool in setOf(
        "app_developer",
        "app_builder",
        "build_html_app",
        "develop_html_app"
    ) || server in setOf(
        "builtin_app_developer",
        "app_builder",
        "internal_app_developer"
    )
}

internal fun isBuiltInAutoSoulCall(call: PlannedMcpToolCall): Boolean {
    val server = call.serverId.trim().lowercase()
    val tool = call.toolName.trim().lowercase()
    return tool in setOf(
        "autosoul",
        "autosoul_agent",
        "autosoul_execute",
        "phone_automation",
        "android_automation"
    ) || server in setOf(
        "builtin_autosoul",
        "autosoul",
        "internal_autosoul"
    )
}

internal fun isBuiltInAutoBrowserCall(call: PlannedMcpToolCall): Boolean {
    val server = call.serverId.trim().lowercase()
    val tool = call.toolName.trim().lowercase()
    return tool in setOf(
        "autobrowser",
        "autobrowser_start_session",
        "autobrowser_navigate",
        "autobrowser_open_url",
        "autobrowser_click_ref",
        "autobrowser_fill_css",
        "autobrowser_exec_js",
        "autobrowser_wait",
        "autobrowser_snapshot",
        "autobrowser_upload_file",
        "autobrowser_close_session",
        "browser_start_session",
        "browser_navigate",
        "browser_click_ref",
        "browser_fill_css",
        "browser_exec_js",
        "browser_wait",
        "browser_snapshot",
        "browser_upload_file",
        "browser_close_session"
    ) || server in setOf(
        "builtin_autobrowser",
        "autobrowser",
        "internal_autobrowser"
    )
}

internal fun isBuiltInMemoryCall(call: PlannedMcpToolCall): Boolean {
    val server = call.serverId.trim().lowercase()
    val tool = call.toolName.trim().lowercase()
    return tool in setOf(
        "memory",
        "memory_write",
        "memory_add",
        "memory_save",
        "save_memory",
        "remember_memory",
        "memory_delete",
        "memory_remove",
        "memory_forget",
        "delete_memory",
        "forget_memory",
        "memory_list",
        "list_memory",
        "show_memory",
        "memory_read",
        "memory_get"
    ) || server in setOf(
        "builtin_memory",
        "memory",
        "internal_memory"
    )
}

internal fun deriveMemoryIntentActions(userPrompt: String): Set<String> {
    val text = userPrompt.trim()
    if (text.isBlank()) return emptySet()
    val lower = text.lowercase()
    val actions = linkedSetOf<String>()

    val writeSignal =
        listOf("记住", "记一下", "帮我记", "请记住", "保存记忆", "存入记忆", "加入记忆", "写入记忆", "add to memory")
            .any { token -> text.contains(token, ignoreCase = true) } ||
            Regex("(?i)\\bremember\\b").containsMatchIn(lower) ||
            Regex("(?i)\\b(save|store|add|write)\\b.{0,24}\\bmemory\\b").containsMatchIn(lower) ||
            Regex("(保存|存入|加入|写入|添加).{0,8}(记忆|memory)").containsMatchIn(text)

    val deleteSignal =
        listOf("忘记", "删除记忆", "移除记忆", "清空记忆")
            .any { token -> text.contains(token, ignoreCase = true) } ||
            Regex("(?i)\\b(forget|delete|remove|clear)\\b.{0,24}\\bmemory\\b").containsMatchIn(lower) ||
            Regex("(忘记|删除|移除|清空).{0,8}(记忆|memory)").containsMatchIn(text)

    val listSignal =
        listOf("查看记忆", "列出记忆", "显示记忆", "我的记忆", "记忆列表")
            .any { token -> text.contains(token, ignoreCase = true) } ||
            Regex("(?i)\\b(show|list|view|read|display)\\b.{0,24}\\bmemories?\\b").containsMatchIn(lower) ||
            Regex("(查看|列出|显示|读取).{0,8}(记忆|memory)").containsMatchIn(text)

    if (writeSignal) actions += "write"
    if (deleteSignal) actions += "delete"
    if (listSignal) actions += "list"
    return actions
}

internal fun resolveAutoSoulTaskPrompt(
    arguments: Map<String, Any?>,
    fallbackUserPrompt: String
): String {
    fun anyString(vararg keys: String): String {
        return keys
            .asSequence()
            .mapNotNull { key ->
                val raw = arguments.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: return@mapNotNull null
                when (raw) {
                    is String -> raw.trim().takeIf { it.isNotBlank() }
                    is Number, is Boolean -> raw.toString()
                    else -> raw.toString().trim().takeIf { it.isNotBlank() }
                }
            }.firstOrNull()
            .orEmpty()
    }

    val direct =
        anyString(
            "task",
            "prompt",
            "goal",
            "instruction",
            "instructions",
            "query",
            "request",
            "target"
        )
    if (direct.isNotBlank()) return direct.take(2000)

    val app = anyString("app", "package", "package_name")
    val action = anyString("action", "operation", "intent")
    val resolvedPackage = AutoSoulAppPackages.resolvePackage(app)
    if (!resolvedPackage.isNullOrBlank() && action.isNotBlank()) {
        return "在 Android 手机上执行自动化任务：先启动包名 $resolvedPackage，然后完成 $action。".take(2000)
    }
    if (!resolvedPackage.isNullOrBlank()) {
        return "在 Android 手机上执行自动化任务：启动包名 $resolvedPackage 并完成用户请求。".take(2000)
    }
    if (app.isNotBlank() && action.isNotBlank()) {
        return "在 Android 手机上执行自动化任务：先打开 $app，然后完成 $action。".take(2000)
    }
    if (app.isNotBlank()) {
        return "在 Android 手机上执行自动化任务：打开 $app 并完成用户请求。".take(2000)
    }

    return fallbackUserPrompt.trim().ifBlank { "请执行安卓手机自动化任务。" }.take(2000)
}

internal fun isHanChar(ch: Char): Boolean {
    return Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN
}

internal fun normalizeAppDisplayName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "App"

    val hasHan = trimmed.any(::isHanChar)
    val hasLatin = trimmed.any { it in 'A'..'Z' || it in 'a'..'z' }
    if (!hasHan || !hasLatin) {
        return trimmed.take(80)
    }

    val separators = listOf("/", "|", "｜", " - ", " — ", " – ", "·")
    separators.forEach { sep ->
        val parts = trimmed.split(sep).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return@forEach
        val pureHan = parts.firstOrNull { p -> p.any(::isHanChar) && p.none { it in 'A'..'Z' || it in 'a'..'z' } }
        val pureLatin = parts.firstOrNull { p -> p.any { it in 'A'..'Z' || it in 'a'..'z' } && p.none(::isHanChar) }
        val chosen = pureHan ?: pureLatin ?: parts.first()
        return chosen.take(80)
    }

    val hanCount = trimmed.count(::isHanChar)
    val latinCount = trimmed.count { it in 'A'..'Z' || it in 'a'..'z' }
    val keepHan = hanCount >= latinCount
    val filtered =
        buildString {
            trimmed.forEach { ch ->
                val keep =
                    if (keepHan) {
                        isHanChar(ch) || ch.isDigit() || ch.isWhitespace() || ch in setOf('-', '_')
                    } else {
                        (ch in 'A'..'Z') || (ch in 'a'..'z') || ch.isDigit() || ch.isWhitespace() || ch in setOf('-', '_')
                    }
                if (keep) append(ch)
            }
        }.trim()
    return filtered.ifBlank { trimmed }.take(80)
}

private fun normalizeLucideIconName(raw: String?): String? {
    val normalized =
        raw
            ?.trim()
            ?.lowercase()
            ?.removePrefix("lucide-")
            ?.replace(Regex("[^a-z0-9\\-]"), "")
            .orEmpty()
            .take(48)
    return normalized.takeIf { it.isNotBlank() }
}

internal fun extractLucideIconName(raw: String?): String? {
    val normalized = normalizeLucideIconName(raw)
    if (!normalized.isNullOrBlank()) return normalized
    val source = raw?.trim().orEmpty()
    if (source.isBlank()) return null
    val matched =
        Regex("([a-zA-Z0-9\\-]{2,48})")
            .find(source)
            ?.groupValues
            ?.getOrNull(1)
    return normalizeLucideIconName(matched)
}

internal fun inferLucideIconFromSignal(name: String, description: String): String {
    val signal = "$name $description".lowercase()
    return when {
        signal.contains("note") || signal.contains("记事") || signal.contains("笔记") -> "notebook-pen"
        signal.contains("task") || signal.contains("todo") || signal.contains("任务") || signal.contains("计划") -> "list-todo"
        signal.contains("calendar") || signal.contains("日程") -> "calendar"
        signal.contains("finance") || signal.contains("账单") || signal.contains("预算") -> "wallet"
        signal.contains("camera") || signal.contains("拍照") || signal.contains("扫码") -> "camera"
        signal.contains("image") || signal.contains("图片") || signal.contains("photo") -> "image"
        signal.contains("search") || signal.contains("检索") || signal.contains("搜索") -> "search"
        signal.contains("travel") || signal.contains("地图") || signal.contains("导航") -> "map-pinned"
        signal.contains("chat") || signal.contains("对话") || signal.contains("客服") -> "messages-square"
        signal.contains("music") || signal.contains("音频") || signal.contains("音乐") -> "music"
        signal.contains("health") || signal.contains("健身") || signal.contains("健康") -> "heart-pulse"
        signal.contains("code") || signal.contains("开发") || signal.contains("程序") -> "code-xml"
        else -> "app-window"
    }
}

internal fun parseAppDevToolSpec(arguments: Map<String, Any?>): AppDevToolSpec? {
    fun anyString(vararg keys: String): String {
        return keys.asSequence()
            .mapNotNull { key ->
                val value = arguments.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: return@mapNotNull null
                when (value) {
                    is String -> value.trim().takeIf { it.isNotBlank() }
                    else -> value.toString().trim().takeIf { it.isNotBlank() }
                }
            }
            .firstOrNull()
            .orEmpty()
    }

    fun normalizeMetaKey(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
    }

    fun splitFeatureCandidates(text: String): List<String> {
        return text
            .split('\n', ',', '，', ';', '；', '、', '|')
            .map { it.trim().trimStart('-', '*', '•', '·') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
    }

    fun inferFeaturesFromDescription(text: String): List<String> {
        val bullets =
            Regex("(?m)^\\s*(?:[-*•·]|\\d+[\\.)、])\\s*(.+)$")
                .findAll(text)
                .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { value -> value.isNotBlank() } }
                .toList()
        if (bullets.isNotEmpty()) return bullets.take(12)

        val lineChunks =
            text.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .flatMap { line ->
                    line.split('。', '.', '！', '!', '；', ';', '？', '?')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
        if (lineChunks.isNotEmpty()) {
            return lineChunks
                .map { it.take(120) }
                .distinct()
                .take(12)
        }
        return emptyList()
    }

    val nameRaw = anyString("name")
    var appIconRaw = anyString("app_icon", "appIcon", "icon", "icon_name", "lucide_icon", "lucideIcon")
    val rawDescription = anyString("description")
    val name = normalizeAppDisplayName(nameRaw)
    if (name.isBlank() || rawDescription.isBlank()) return null

    var modeRaw = ""
    var targetAppId = ""
    var targetAppName = ""
    var editRequest = ""
    var style = ""

    val explicitFeatures = emptyList<String>()
    val parsedFeatures = mutableListOf<String>()
    val keptDescriptionLines = mutableListOf<String>()
    val metaLineRegex = Regex("^\\s*([A-Za-z0-9_\\-\\u4e00-\\u9fa5 ]{2,36})\\s*[:：]\\s*(.+)$")

    rawDescription
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.isBlank()) return@forEach

            val meta = metaLineRegex.matchEntire(line)
            if (meta == null) {
                keptDescriptionLines += line
                return@forEach
            }

            val key = normalizeMetaKey(meta.groupValues[1])
            val value = meta.groupValues[2].trim()
            if (value.isBlank()) {
                keptDescriptionLines += line
                return@forEach
            }

            when (key) {
                "mode", "operation", "intent", "action", "模式", "操作", "意图" -> modeRaw = value
                "targetappid", "appid", "existingappid", "sourceappid", "目标应用id", "应用id" -> targetAppId = value
                "targetappname", "existingappname", "apptoedit", "editappname", "目标应用名", "应用名" -> targetAppName = value
                "editrequest", "request", "updaterequest", "changerequest", "instruction", "prompt", "修改需求", "编辑需求", "修复需求", "更新需求", "请求" ->
                    editRequest = value
                "appicon", "icon", "iconname", "lucideicon", "应用图标", "图标" -> appIconRaw = value
                "style", "theme", "visualstyle", "design", "风格", "样式", "主题", "设计" -> style = value
                "features", "requirements", "specs", "functionalities", "功能", "特性", "需求", "细节" ->
                    parsedFeatures += splitFeatureCandidates(value)
                else -> keptDescriptionLines += line
            }
        }

    val cleanedDescription =
        keptDescriptionLines.joinToString("\n")
            .trim()
            .ifBlank { rawDescription.trim() }
            .take(3000)
    if (cleanedDescription.isBlank()) return null

    var mode =
        when (modeRaw.trim().lowercase()) {
            "edit", "update", "revise", "modify", "refactor", "patch", "fix", "debug_fix", "修复", "编辑", "修改", "更新", "优化" -> "edit"
            else -> "create"
        }
    if (mode == "create") {
        val hasExplicitTarget = targetAppId.isNotBlank() || targetAppName.isNotBlank()
        val hasStrongEditIntent =
            editRequest.isNotBlank() ||
                Regex(
                    "(?i)\\b(edit|update|revise|modify|refactor|patch)\\s+(existing|current|saved|previous|last)\\b"
                ).containsMatchIn(cleanedDescription) ||
                Regex("(编辑|修改|更新|重构)(当前|已有|现有|上一个|最近|这个)应用").containsMatchIn(cleanedDescription) ||
                Regex("(继续|接着)(编辑|修改|更新)").containsMatchIn(cleanedDescription)
        if (hasExplicitTarget || hasStrongEditIntent) mode = "edit"
    }

    val features =
        (explicitFeatures + parsedFeatures + inferFeaturesFromDescription(cleanedDescription))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
    val appIcon = extractLucideIconName(appIconRaw) ?: inferLucideIconFromSignal(name, cleanedDescription)

    if (mode == "edit") {
        val effectiveEditRequest = editRequest.trim().ifBlank { cleanedDescription }
        if (effectiveEditRequest.isBlank()) return null
        return AppDevToolSpec(
            mode = "edit",
            name = normalizeAppDisplayName(name).take(80),
            description = cleanedDescription.take(1200),
            appIcon = appIcon,
            style = style.take(180),
            features = features.ifEmpty { splitFeatureCandidates(effectiveEditRequest) }.take(12),
            targetAppId = targetAppId.takeIf { it.isNotBlank() }?.take(160),
            targetAppName = targetAppName.takeIf { it.isNotBlank() }?.take(80),
            editRequest = effectiveEditRequest.take(1600)
        )
    }

    val createFeatures = features.ifEmpty { listOf(cleanedDescription.take(140)) }
    return AppDevToolSpec(
        mode = "create",
        name = normalizeAppDisplayName(name).take(80),
        description = cleanedDescription.take(1800),
        appIcon = appIcon,
        style = style.take(180),
        features = createFeatures.take(12),
        targetAppId = targetAppId.takeIf { it.isNotBlank() }?.take(160),
        targetAppName = targetAppName.takeIf { it.isNotBlank() }?.take(80),
        editRequest = editRequest.takeIf { it.isNotBlank() }?.take(1600)
    )
}

internal fun encodeAppDevTagPayload(payload: AppDevTagPayload): String {
    return GsonBuilder().disableHtmlEscaping().create().toJson(payload)
}

internal fun parseAppDevTagPayload(
    content: String,
    fallbackName: String,
    fallbackStatus: String?
): AppDevTagPayload {
    val json = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
    val name =
        json?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName
    val subtitleRaw =
        json?.get("subtitle")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Developing app"
    val descriptionRaw = json?.get("description")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
    val appIcon =
        extractLucideIconName(
            json?.get("appIcon")?.takeIf { it.isJsonPrimitive }?.asString
                ?: json?.get("app_icon")?.takeIf { it.isJsonPrimitive }?.asString
                ?: extractSavedAppIconNameFromRaw(descriptionRaw, json?.get("html")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
        ) ?: inferLucideIconFromSignal(name, descriptionRaw)
    val style = json?.get("style")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
    val features =
        json?.getAsJsonArray("features")
            ?.mapNotNull { runCatching { it.asString.trim() }.getOrNull()?.takeIf { t -> t.isNotBlank() } }
            .orEmpty()
    val progress = json?.get("progress")?.takeIf { it.isJsonPrimitive }?.asInt ?: if (fallbackStatus == "success") 100 else 0
    val status = json?.get("status")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty().ifBlank { fallbackStatus.orEmpty() }
    val html = json?.get("html")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val error = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }
    val sourceAppId = json?.get("sourceAppId")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }
    val mode = json?.get("mode")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() } ?: "create"
    val deployUrl =
        json?.get("deployUrl")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val deployError =
        json?.get("deployError")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeStatus =
        json?.get("runtimeStatus")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
    val runtimeMessage =
        json?.get("runtimeMessage")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeRunUrl =
        json?.get("runtimeRunUrl")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeArtifactName =
        json?.get("runtimeArtifactName")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val runtimeArtifactUrl =
        json?.get("runtimeArtifactUrl")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val compactDescription = compactAppDescription(descriptionRaw, subtitleRaw)
    val compactSubtitle = compactAppDescription(subtitleRaw, compactDescription)
    return AppDevTagPayload(
        name = normalizeAppDisplayName(name),
        subtitle = compactSubtitle,
        description = compactDescription,
        appIcon = appIcon,
        style = style,
        features = features,
        progress = progress.coerceIn(0, 100),
        status = status,
        html = html,
        error = error,
        sourceAppId = sourceAppId,
        mode = mode,
        deployUrl = deployUrl,
        deployError = deployError,
        runtimeStatus = runtimeStatus,
        runtimeMessage = runtimeMessage,
        runtimeRunUrl = runtimeRunUrl,
        runtimeArtifactName = runtimeArtifactName,
        runtimeArtifactUrl = runtimeArtifactUrl
    )
}

internal fun compactAppDescription(primary: String, fallback: String = "Developing app"): String {
    val raw =
        primary
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
    val simplified = raw.trimEnd('.', '。', '!', '！', '?', '？')
    if (simplified.isBlank()) {
        return fallback.trim().ifBlank { "Developing app" }.take(56)
    }
    return simplified.take(56)
}

internal fun extractSavedAppIconNameFromRaw(description: String, html: String): String? {
    val sources = listOf(description, html.take(20_000))
    val patterns =
        listOf(
            Regex("(?i)app[_\\- ]?icon\\s*[:：]\\s*([a-z0-9\\-]{2,48})"),
            Regex("(?i)data-lucide\\s*=\\s*[\"']([a-z0-9\\-]{2,48})[\"']"),
            Regex("(?i)class\\s*=\\s*[\"'][^\"']*lucide-([a-z0-9\\-]{2,48})[^\"']*[\"']"),
            Regex("(?i)lucide-([a-z0-9\\-]{2,48})")
        )
    for (source in sources) {
        if (source.isBlank()) continue
        for (pattern in patterns) {
            val matched = pattern.find(source)?.groupValues?.getOrNull(1).orEmpty()
            val normalized = extractLucideIconName(matched)
            if (!normalized.isNullOrBlank()) return normalized
        }
    }
    return null
}

internal fun ensureAppIconMetadataInHtml(html: String, iconName: String): String {
    val normalizedHtml = html.trim()
    if (normalizedHtml.isBlank()) return normalizedHtml
    val icon = extractLucideIconName(iconName) ?: return normalizedHtml
    val hasMetadata =
        Regex("(?i)app[_\\- ]?icon\\s*[:：]\\s*[a-z0-9\\-]{2,48}")
            .containsMatchIn(normalizedHtml)
    if (hasMetadata) return normalizedHtml

    val marker = "<!-- app_icon: $icon -->"
    val headRegex = Regex("(?i)<head\\b[^>]*>")
    val headMatch = headRegex.find(normalizedHtml)
    if (headMatch != null) {
        val insertPos = headMatch.range.last + 1
        return buildString(normalizedHtml.length + marker.length + 2) {
            append(normalizedHtml.substring(0, insertPos))
            append('\n')
            append(marker)
            append('\n')
            append(normalizedHtml.substring(insertPos))
        }
    }
    return "$marker\n$normalizedHtml"
}

internal fun resolveExistingSavedApp(
    spec: AppDevToolSpec,
    savedApps: List<SavedApp>
): SavedApp? {
    if (savedApps.isEmpty()) return null
    if (spec.mode != "edit") return null

    val byId = spec.targetAppId?.trim().orEmpty()
    if (byId.isNotBlank()) {
        if (byId.equals("latest", ignoreCase = true) || byId.equals("last", ignoreCase = true)) {
            return savedApps.maxByOrNull { it.updatedAt }
        }
        savedApps.firstOrNull { it.id.equals(byId, ignoreCase = true) }?.let { return it }
    }

    val candidates = mutableListOf<String>()
    spec.targetAppName?.trim()?.takeIf { it.isNotBlank() }?.let { candidates += it }
    spec.name.trim().takeIf { it.isNotBlank() && !it.equals("app", ignoreCase = true) }?.let { candidates += it }

    candidates.forEach { keyword ->
        savedApps.firstOrNull { it.name.trim().equals(keyword, ignoreCase = true) }?.let { return it }
    }
    candidates.forEach { keyword ->
        val lower = keyword.lowercase()
        val byContain = savedApps.filter { it.name.lowercase().contains(lower) }
        if (byContain.size == 1) return byContain.first()
    }

    val normalizedCandidates =
        candidates.map { value ->
            value
                .lowercase()
                .replace(Regex("[\\s\\-_/\\\\|]+"), "")
                .trim()
        }.filter { it.isNotBlank() }
    if (normalizedCandidates.isNotEmpty()) {
        savedApps.firstOrNull { app ->
            val normalizedName =
                app.name
                    .lowercase()
                    .replace(Regex("[\\s\\-_/\\\\|]+"), "")
                    .trim()
            normalizedCandidates.any { candidate ->
                normalizedName == candidate ||
                    normalizedName.contains(candidate) ||
                    candidate.contains(normalizedName)
            }
        }?.let { return it }
    }

    if (spec.targetAppId.isNullOrBlank() && spec.targetAppName.isNullOrBlank()) {
        savedApps.maxByOrNull { it.updatedAt }?.let { return it }
    }

    return if (savedApps.size == 1) savedApps.first() else null
}

internal fun summarizeSavedAppsForInstruction(savedApps: List<SavedApp>, limit: Int = 10): String {
    if (savedApps.isEmpty()) return "No saved apps."
    return buildString {
        savedApps
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .forEachIndexed { index, app ->
                append(index + 1)
                append(". id=")
                append(app.id)
                append(" | name=")
                append(normalizeAppDisplayName(app.name).take(80))
                val desc = compactAppDescription(app.description, "")
                if (desc.isNotBlank()) {
                    append(" | desc=")
                    append(desc.take(80))
                }
                append('\n')
            }
    }.trimEnd()
}

internal fun shouldUseIos18UiSkill(
    style: String,
    description: String,
    features: List<String>,
    requestText: String = ""
): Boolean {
    val signal =
        buildString {
            append(style)
            append('\n')
            append(description)
            append('\n')
            append(features.joinToString(" "))
            append('\n')
            append(requestText)
        }.lowercase()
    if (signal.isBlank()) return false

    val markers =
        listOf(
            "ios",
            "ios18",
            "ios 18",
            "iphone",
            "ipad",
            "cupertino",
            "apple",
            "human interface",
            "sf pro",
            "inset grouped",
            "tab bar",
            "large title"
        )
    return markers.any { marker -> signal.contains(marker) }
}

private val APP_DEVELOPER_REQUIRED_SYSTEM_PROMPT = APP_DEVELOPER_BASELINE_SYSTEM_PROMPT

@Suppress("UNUSED_PARAMETER")
internal fun buildAppGenerationSystemPrompt(useIos18Skill: Boolean): String {
    return APP_DEVELOPER_REQUIRED_SYSTEM_PROMPT
}

internal fun buildAppGenerationUserPrompt(
    spec: AppDevToolSpec,
    useIos18Skill: Boolean,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = ""
): String {
    return buildString {
        val rawUser = sourceUserPrompt.trim()
        val rawArgs = rawToolArgsJson.trim()
        if (rawUser.isNotBlank()) {
            appendLine("Original user request (preserve this intent with highest priority):")
            appendLine(rawUser.take(2400))
            appendLine()
        }
        if (rawArgs.isNotBlank()) {
            appendLine("Raw tool arguments JSON:")
            appendLine(rawArgs.take(2400))
            appendLine()
        }
        appendLine("Build one HTML app with these specs:")
        append("Name: ")
        appendLine(spec.name)
        append("App icon (Lucide): ")
        appendLine(spec.appIcon)
        appendLine("Detailed description:")
        appendLine(spec.description)
        if (spec.style.isNotBlank()) {
            appendLine()
            append("Extracted style hint: ")
            appendLine(spec.style)
        }
        if (spec.features.isNotEmpty()) {
            appendLine("Extracted feature checklist:")
            spec.features.forEachIndexed { index, feature ->
                append(index + 1)
                append(". ")
                appendLine(feature)
            }
        }
        appendLine()
        appendLine("Hard requirements:")
        appendLine("- Include all requested features with real, working interactions.")
        appendLine("- Mobile + desktop responsive.")
        appendLine("- Keep the app name in one language only (Chinese OR English, not both).")
        appendLine("- Keep copy short and practical.")
        appendLine("- Do not use placeholder/mock content unless explicitly requested.")
        appendLine("- Use Lucide icon(s) and keep one explicit app icon in the primary header/home area.")
        appendLine("- Include explicit icon metadata: `app_icon: <lucide-icon-name>` in the specification/comments.")
        appendLine("- Ensure the chosen app icon is rendered by Lucide (for example with `data-lucide=\"...\"`).")
        appendLine("- Never call string operations on uncertain values; guard `.split/.trim/.toLowerCase/.map` with safe defaults (e.g. `String(value ?? '')`).")
        appendLine("- Reserve top-right safe-area host overlay zone and avoid fixed controls in that collision region.")
        if (useIos18Skill) {
            appendLine("- Follow iOS 18 visual and component constraints from the active skill package.")
        }
    }.trim()
}

@Suppress("UNUSED_PARAMETER")
internal fun buildAppRevisionSystemPrompt(useIos18Skill: Boolean): String {
    return APP_DEVELOPER_REQUIRED_SYSTEM_PROMPT
}

internal fun buildAppRevisionUserPrompt(
    currentHtml: String,
    requestText: String,
    spec: AppDevToolSpec?,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = ""
): String {
    return buildString {
        val rawUser = sourceUserPrompt.trim()
        val rawArgs = rawToolArgsJson.trim()
        if (rawUser.isNotBlank()) {
            appendLine("Original user request (preserve this intent with highest priority):")
            appendLine(rawUser.take(2400))
            appendLine()
        }
        if (rawArgs.isNotBlank()) {
            appendLine("Raw tool arguments JSON:")
            appendLine(rawArgs.take(2400))
            appendLine()
        }
        appendLine("Update this app based on the request.")
        append("Request: ")
        appendLine(requestText.trim())
        if (spec != null) {
            if (spec.name.isNotBlank()) {
                append("Target app name: ")
                appendLine(spec.name)
            }
            if (spec.appIcon.isNotBlank()) {
                append("Target app icon (Lucide): ")
                appendLine(spec.appIcon)
            }
            if (spec.style.isNotBlank()) {
                append("Target style: ")
                appendLine(spec.style)
            }
            if (spec.features.isNotEmpty()) {
                appendLine("Target feature deltas:")
                spec.features.forEachIndexed { index, item ->
                    append(index + 1)
                    append(". ")
                    appendLine(item)
                }
            }
        }
        appendLine()
        appendLine("Revision rules:")
        appendLine("- Keep unchanged modules intact.")
        appendLine("- If replacing a component, maintain equivalent UX behavior.")
        appendLine("- Keep interactions complete (no TODO/placeholder handlers).")
        appendLine("- Keep one explicit Lucide app icon in the primary header/home area.")
        appendLine("- Keep explicit icon metadata `app_icon: <lucide-icon-name>` and render that icon with Lucide.")
        appendLine("- Ensure null-safe parsing: avoid `.split/.trim/.toLowerCase/.map` on undefined/null values.")
        appendLine("- Reserve top-right safe-area host overlay zone and avoid fixed controls in that collision region.")
        appendLine("- Return a full HTML file, not a patch.")
        appendLine()
        appendLine("Current HTML:")
        appendLine(currentHtml.trim())
    }.trim()
}

private const val APP_DEV_PROGRESS_TARGET_LINES = 1500

internal fun estimateDraftLineCount(rawDraft: String): Int {
    val normalized = rawDraft.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.isBlank()) return 0
    val actualLineCount = normalized.count { it == '\n' } + 1
    val virtualLineCount = kotlin.math.ceil(normalized.length / 88.0).toInt().coerceAtLeast(1)
    return maxOf(actualLineCount, virtualLineCount)
}

internal fun computeAppDevProgressFromDraft(
    draftHtml: String,
    baseProgress: Int,
    maxProgress: Int = 98
): Int {
    val safeBase = baseProgress.coerceIn(0, maxProgress)
    val lines = estimateDraftLineCount(draftHtml).coerceAtLeast(0)
    val ratio = (lines / APP_DEV_PROGRESS_TARGET_LINES.toFloat()).coerceIn(0f, 1f)
    var progress = safeBase + ((maxProgress - safeBase) * ratio).roundToInt()
    val normalizedDraft = draftHtml.trim()
    if (normalizedDraft.contains("<body", ignoreCase = true)) {
        progress = maxOf(progress, safeBase + 12)
    }
    if (normalizedDraft.contains("</html>", ignoreCase = true)) {
        progress = maxOf(progress, maxProgress - 1)
    }
    return progress.coerceIn(safeBase, maxProgress)
}

internal suspend fun generateHtmlAppFromSpec(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    extraHeaders: List<HttpHeader>,
    spec: AppDevToolSpec,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = "",
    onProgress: ((Int) -> Unit)? = null,
    onDraftHtml: ((String) -> Unit)? = null
): String {
    val useIos18Skill =
        shouldUseIos18UiSkill(
            style = spec.style,
            description = spec.description,
            features = spec.features
        )
    val systemPrompt = buildAppGenerationSystemPrompt(useIos18Skill)
    val userPrompt =
        buildAppGenerationUserPrompt(
            spec = spec,
            useIos18Skill = useIos18Skill,
            sourceUserPrompt = sourceUserPrompt,
            rawToolArgsJson = rawToolArgsJson
        )

    var emittedProgress = 8
    var lastDraftUpdateAtMs = 0L
    val draftBuilder = StringBuilder()
    onProgress?.invoke(emittedProgress)

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userPrompt)
            ),
            extraHeaders = extraHeaders,
            onChunk = { chunk ->
                draftBuilder.append(chunk)
                val nextProgress = computeAppDevProgressFromDraft(draftBuilder.toString(), baseProgress = 8, maxProgress = 98)
                if (nextProgress > emittedProgress) {
                    emittedProgress = nextProgress
                    onProgress?.invoke(nextProgress)
                }
                val now = System.currentTimeMillis()
                if (now - lastDraftUpdateAtMs >= 180L) {
                    onDraftHtml?.invoke(draftBuilder.toString())
                    lastDraftUpdateAtMs = now
                }
            }
    )
    onDraftHtml?.invoke(draftBuilder.toString())
    if (emittedProgress < 98) {
        onProgress?.invoke(98)
    }
    return normalizeGeneratedHtml(raw)
}

internal suspend fun reviseHtmlAppFromPrompt(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    extraHeaders: List<HttpHeader>,
    currentHtml: String,
    requestText: String,
    spec: AppDevToolSpec? = null,
    sourceUserPrompt: String = "",
    rawToolArgsJson: String = "",
    onProgress: ((Int) -> Unit)? = null,
    onDraftHtml: ((String) -> Unit)? = null
): String {
    val current = currentHtml.trim()
    if (current.isBlank()) {
        error("Current HTML is empty.")
    }
    val request = requestText.trim()
    if (request.isBlank()) {
        error("Update request is empty.")
    }

    val useIos18Skill =
        shouldUseIos18UiSkill(
            style = spec?.style.orEmpty(),
            description = spec?.description.orEmpty(),
            features = spec?.features.orEmpty(),
            requestText = request
        )
    val systemPrompt = buildAppRevisionSystemPrompt(useIos18Skill)
    val userPrompt =
        buildAppRevisionUserPrompt(
            currentHtml = current,
            requestText = request,
            spec = spec,
            sourceUserPrompt = sourceUserPrompt,
            rawToolArgsJson = rawToolArgsJson
        )

    var emittedProgress = 10
    var lastDraftUpdateAtMs = 0L
    val draftBuilder = StringBuilder()
    onProgress?.invoke(emittedProgress)

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userPrompt)
            ),
            extraHeaders = extraHeaders,
            onChunk = { chunk ->
                draftBuilder.append(chunk)
                val nextProgress = computeAppDevProgressFromDraft(draftBuilder.toString(), baseProgress = 10, maxProgress = 98)
                if (nextProgress > emittedProgress) {
                    emittedProgress = nextProgress
                    onProgress?.invoke(nextProgress)
                }
                val now = System.currentTimeMillis()
                if (now - lastDraftUpdateAtMs >= 180L) {
                    onDraftHtml?.invoke(draftBuilder.toString())
                    lastDraftUpdateAtMs = now
                }
            }
    )
    onDraftHtml?.invoke(draftBuilder.toString())
    if (emittedProgress < 98) {
        onProgress?.invoke(98)
    }
    return normalizeGeneratedHtml(raw)
}

internal fun normalizeGeneratedHtml(raw: String): String {
    val normalized = normalizeGeneratedHtmlCandidate(raw, wrapIfNeeded = true)
    if (normalized.isBlank()) error("Generated HTML is empty.")
    return normalized
}

internal fun normalizeGeneratedHtmlDraft(raw: String): String {
    return normalizeGeneratedHtmlCandidate(raw, wrapIfNeeded = false)
}

internal fun normalizeGeneratedHtmlCandidate(raw: String, wrapIfNeeded: Boolean): String {
    val extractedPayload = extractGeneratedHtmlPayload(raw)
    val decoded = decodeEscapedHtmlSequences(extractedPayload)
    val normalizedNewline = decoded.replace("\r\n", "\n").replace("\r", "\n").trim()
    if (normalizedNewline.isBlank()) return ""

    val htmlSegment = extractHtmlDocumentSegment(normalizedNewline)
    val cleaned = stripMarkdownCodeFences(htmlSegment).trim()
    if (cleaned.isBlank()) return ""

    val lower = cleaned.lowercase()
    if (lower.startsWith("<!doctype html") || lower.contains("<html")) {
        return cleaned
    }

    if (!wrapIfNeeded) return cleaned

    return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\" />\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n<title>Generated App</title>\n</head>\n<body>\n$cleaned\n</body>\n</html>"
}

internal fun extractGeneratedHtmlPayload(raw: String): String {
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

internal fun decodeEscapedHtmlSequences(raw: String): String {
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

internal fun extractHtmlDocumentSegment(raw: String): String {
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

internal fun buildMcpCallSignature(call: PlannedMcpToolCall): String {
    val normalizedServer = call.serverId.trim().lowercase()
    val normalizedTool = call.toolName.trim().lowercase()
    val argsPart =
        call.arguments.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) ->
                val key = k.trim().lowercase()
                val value = v?.toString()?.trim().orEmpty().lowercase()
                "$key=$value"
            }
    return "$normalizedServer|$normalizedTool|$argsPart"
}

internal fun toMcpFailureResult(error: Throwable): com.zionchat.app.data.McpToolResult {
    val message = error.message.orEmpty().trim().ifBlank { "Tool call failed." }
    return com.zionchat.app.data.McpToolResult(
        success = false,
        content = message,
        error = message
    )
}

internal fun extractExplicitApiError(errorText: String): String? {
    val raw = errorText.trim()
    if (raw.isBlank()) return null
    val lower = raw.lowercase()

    val hasHttpLikeSignal =
        (lower.contains("http") || lower.contains("status") || lower.contains("api")) &&
            Regex("\\b(4\\d\\d|5\\d\\d)\\b").containsMatchIn(lower)
    val hasApiKeywords =
        listOf(
            "api error",
            "bad request",
            "unauthorized",
            "forbidden",
            "too many requests",
            "invalid request",
            "internal server error",
            "service unavailable",
            "gateway timeout"
        ).any { marker -> lower.contains(marker) }

    val jsonError =
        runCatching {
            val json = JsonParser.parseString(raw)
            if (!json.isJsonObject) return@runCatching null
            val obj = json.asJsonObject
            val message = obj.get("message")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val error = obj.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val code = obj.get("code")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            listOf(error, message, code)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
                .takeIf { it.isNotBlank() }
        }.getOrNull()

    if (!jsonError.isNullOrBlank()) return jsonError.take(1000)
    if (hasHttpLikeSignal || hasApiKeywords) return raw.take(1000)
    return null
}

internal fun buildMcpTagDetailContent(
    round: Int,
    serverName: String?,
    toolName: String,
    argumentsJson: String,
    statusText: String,
    attempts: Int?,
    elapsedMs: Long?,
    resultText: String?,
    errorText: String?
): String {
    return buildString {
        append("Round: ")
        append(round)
        append('\n')
        append("Status: ")
        append(statusText.trim())
        append('\n')
        if (!serverName.isNullOrBlank()) {
            append("Server: ")
            append(serverName.trim())
            append('\n')
        }
        append("Tool: ")
        append(toolName.trim())
        append('\n')
        attempts?.takeIf { it > 0 }?.let {
            append("Attempts: ")
            append(it)
            append('\n')
        }
        elapsedMs?.takeIf { it > 0 }?.let {
            append("Elapsed: ")
            append(formatElapsedDuration(it))
            append('\n')
        }
        append('\n')
        append("Arguments:\n```json\n")
        append(argumentsJson.trim())
        append("\n```")

        resultText?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            append("\n\nResult:\n")
            append(text)
        }
        errorText?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            append("\n\nError:\n")
            append(text)
        }
    }.trim()
}

internal data class McpTagDetailView(
    val round: String = "-",
    val status: String = "",
    val server: String = "",
    val tool: String = "",
    val attempts: String = "",
    val elapsed: String = "",
    val argumentsJson: String = "",
    val result: String = "",
    val error: String = ""
)

internal fun parseMcpTagDetail(content: String): McpTagDetailView {
    val lines = content.lineSequence().map { it.trim() }.toList()
    fun value(prefix: String): String {
        return lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
    }

    fun block(startMarker: String, endMarker: String? = null): String {
        val startIndex = content.indexOf(startMarker)
        if (startIndex < 0) return ""
        val rawStart = startIndex + startMarker.length
        val endIndex = endMarker?.let { marker -> content.indexOf(marker, startIndex = rawStart).takeIf { it >= 0 } }
            ?: content.length
        return content.substring(rawStart, endIndex).trim()
    }

    val args =
        Regex("(?s)Arguments:\\s*```json\\s*(.*?)\\s*```")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    val result = block("Result:", "\n\nError:")
    val error = block("Error:")

    return McpTagDetailView(
        round = value("Round:").ifBlank { "-" },
        status = value("Status:"),
        server = value("Server:"),
        tool = value("Tool:"),
        attempts = value("Attempts:"),
        elapsed = value("Elapsed:"),
        argumentsJson = args,
        result = result,
        error = error
    )
}

internal fun isTagRunning(tag: MessageTag): Boolean {
    return when (tag.status?.trim()?.lowercase()) {
        "running" -> true
        "success", "error" -> false
        else -> {
            val lower = tag.content.lowercase()
            lower.contains("status: running") || lower.contains("retrying")
        }
    }
}

internal fun formatElapsedDuration(elapsedMs: Long): String {
    if (elapsedMs <= 0L) return "0.0s"
    val seconds = elapsedMs / 1000.0
    return String.format(java.util.Locale.US, "%.1fs", seconds)
}

internal fun formatToolDetailJson(rawText: String): String {
    val raw = rawText.trim()
    if (raw.isBlank()) return "{}"
    val cleaned = stripMarkdownCodeFences(raw)
    val firstChar = cleaned.firstOrNull()
    if (firstChar != '{' && firstChar != '[') return cleaned
    return runCatching {
        GsonBuilder().setPrettyPrinting().create()
            .toJson(JsonParser.parseString(cleaned))
    }.getOrDefault(cleaned)
}

internal data class ThinkingSplitResult(
    val visible: String,
    val thinking: String
)

internal data class McpTagAnchor(
    val tagId: String,
    val charIndex: Int
)

internal data class InlineMcpCallExtraction(
    val visibleText: String,
    val blocks: List<String>
)

internal data class MessageInlineSegment(
    val text: String? = null,
    val tagId: String? = null
)

private val mcpTagMarkerRegex =
    Regex("(?is)<!--\\s*mcp_tag:([A-Za-z0-9_-]+)\\s*-->")

internal fun buildMcpTagMarker(tagId: String): String {
    return "<!--mcp_tag:${tagId.trim()}-->"
}

internal fun insertMcpTagMarkers(visibleContent: String, anchors: List<McpTagAnchor>): String {
    if (anchors.isEmpty()) return visibleContent
    val content = visibleContent
    val sortedAnchors = anchors.sortedBy { it.charIndex }
    val sb = StringBuilder()
    var cursor = 0

    sortedAnchors.forEach { anchor ->
        val safeId = anchor.tagId.trim()
        if (safeId.isBlank()) return@forEach
        val index = anchor.charIndex.coerceIn(0, content.length)
        if (index > cursor) {
            sb.append(content.substring(cursor, index))
        }
        if (sb.isNotEmpty() && sb.last() != '\n') {
            sb.append("\n\n")
        } else if (sb.isNotEmpty()) {
            sb.append('\n')
        }
        sb.append(buildMcpTagMarker(safeId))
        sb.append("\n\n")
        cursor = index
    }

    if (cursor < content.length) {
        sb.append(content.substring(cursor))
    }
    return sb.toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

internal fun splitContentByMcpTagMarkers(content: String): List<MessageInlineSegment> {
    if (!mcpTagMarkerRegex.containsMatchIn(content)) return emptyList()
    val result = mutableListOf<MessageInlineSegment>()
    var cursor = 0

    mcpTagMarkerRegex.findAll(content).forEach { match ->
        if (match.range.first > cursor) {
            val text = content.substring(cursor, match.range.first).trim()
            if (text.isNotBlank()) {
                result += MessageInlineSegment(text = text)
            }
        }
        val tagId = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (tagId.isNotBlank()) {
            result += MessageInlineSegment(tagId = tagId)
        }
        cursor = match.range.last + 1
    }

    if (cursor < content.length) {
        val tail = content.substring(cursor).trim()
        if (tail.isNotBlank()) {
            result += MessageInlineSegment(text = tail)
        }
    }
    return result
}

internal fun stripMcpTagMarkers(content: String): String {
    if (content.isBlank()) return ""
    return content
        .replace(mcpTagMarkerRegex, " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

internal fun appendTextSection(target: StringBuilder, text: String) {
    val cleaned = text.trim()
    if (cleaned.isBlank()) return
    if (target.isNotEmpty()) target.append("\n\n")
    target.append(cleaned)
}

internal fun mergeTextSections(existing: String, incoming: String): String {
    val left = existing.trim()
    val right = incoming.trim()
    return when {
        left.isBlank() -> right
        right.isBlank() -> left
        else -> "$left\n\n$right"
    }
}

internal fun splitThinkingFromContent(content: String): ThinkingSplitResult {
    val raw = content.trim()
    if (raw.isBlank()) return ThinkingSplitResult(visible = "", thinking = "")

    val blockRegex = Regex("(?is)<(?:think|thinking)>(.*?)</(?:think|thinking)>")
    val thinkingBlocks =
        blockRegex.findAll(raw)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
            .toList()

    val visible =
        raw.replace(blockRegex, " ")
            .replace(Regex("(?i)</?(?:think|thinking)>"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    return ThinkingSplitResult(
        visible = visible,
        thinking = thinkingBlocks.joinToString("\n\n").trim()
    )
}

internal fun extractInlineMcpCallBlocks(content: String): InlineMcpCallExtraction {
    val raw = content.trim()
    if (raw.isBlank()) {
        return InlineMcpCallExtraction(visibleText = "", blocks = emptyList())
    }

    val blockRegex = Regex(
        "(?is)<(?:mcp_call|tool_call)\\b[^>]*>(.*?)(?:</(?:mcp_call|tool_call)>|$)"
    )
    val blocks =
        blockRegex.findAll(raw)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
            .toList()

    val visible =
        raw.replace(blockRegex, " ")
            .replace(Regex("(?is)</(?:mcp_call|tool_call)>"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    return InlineMcpCallExtraction(visibleText = visible, blocks = blocks)
}

internal fun buildAppDeveloperToolInstruction(
    roundIndex: Int,
    maxCallsPerRound: Int,
    savedApps: List<SavedApp>
): String {
    return buildString {
        appendLine("Built-in tool available: app_developer.")
        appendLine("Current round: $roundIndex")
        appendLine("Use this tool ONLY when the user explicitly asks to create/build/develop an app or HTML page.")
        appendLine("Do not call this tool for generic Q&A.")
        appendLine("First write a normal visible reply, then append tool call tags if needed.")
        appendLine("At most $maxCallsPerRound tool calls in this round.")
        appendLine()
        appendLine("Saved apps context:")
        appendLine(summarizeSavedAppsForInstruction(savedApps))
        appendLine()
        appendLine("Tool call format:")
        appendLine("<tool_call>{\"serverId\":\"builtin_app_developer\",\"toolName\":\"app_developer\",\"arguments\":{...}}</tool_call>")
        appendLine()
        appendLine("Arguments policy (STRICT):")
        appendLine("- ONLY 3 arguments are allowed:")
        appendLine("  1) name: app name in ONE language only (Chinese OR English), never bilingual")
        appendLine("  2) description: detailed product spec. Include style, layout, interactions, states, and edge cases.")
        appendLine("  3) app_icon: one Lucide icon name (e.g. `notebook-pen`, `list-todo`, `camera`, `wallet`).")
        appendLine("     description must include a Lucide app icon choice and placement.")
        appendLine("     description must reserve top-right host overlay safe-area (avoid fixed controls in that corner).")
        appendLine("- Do NOT emit style/features/mode as extra top-level arguments.")
        appendLine("- If editing an existing app, still only use name + description + app_icon.")
        appendLine("- For edit intent, write metadata lines INSIDE description:")
        appendLine("  operation: edit")
        appendLine("  target_app_id: <id from saved apps context> (or target_app_name)")
        appendLine("  request: <detailed modifications>")
        appendLine("- All declared UI interactions must be real and fully functional (no dead buttons/links).")
        appendLine("- If description mentions iOS/iPhone/Cupertino/iOS 18, enforce strict iOS 18 HIG constraints.")
    }.trim()
}

internal fun buildPendingAppAutomationPrompt(task: AppAutomationTask): String {
    val request = task.request.trim()
    val html = task.appHtml.trim()
    if (request.isBlank() || html.isBlank()) return ""
    val mode = task.mode.trim().lowercase()
    val appName = normalizeAppDisplayName(task.appName.trim().ifBlank { "App" })
    val appId = task.appId.trim().ifBlank { "unknown-app-id" }
    val htmlPayload = html.take(180_000)

    return buildString {
        if (mode == "debug_fix") {
            appendLine("Please fix an existing saved HTML app.")
        } else {
            appendLine("Please edit an existing saved HTML app.")
        }
        appendLine("Do NOT create a new app. Update the target app in-place.")
        appendLine("Use app_developer tool with ONLY three arguments: name + description + app_icon.")
        appendLine()
        appendLine("Required tool arguments:")
        appendLine("- name: one-language app name only (Chinese OR English)")
        appendLine("- app_icon: one Lucide icon name that best represents the app")
        appendLine("- description: detailed spec and include metadata lines:")
        appendLine("  app_icon: <lucide icon name and placement>")
        appendLine("  safe_zone: reserve top-right host overlay region")
        appendLine("  operation: edit")
        append("  target_app_id: ")
        appendLine(appId)
        append("  target_app_name: ")
        appendLine(appName)
        appendLine("  request: <the modification request below>")
        appendLine("- Do not add any other top-level arguments.")
        appendLine()
        appendLine(if (mode == "debug_fix") "Bug report:" else "Edit request:")
        appendLine(request)
        appendLine()
        appendLine("Hard constraints:")
        appendLine("- No mock/placeholder/simulated-only interactions.")
        appendLine("- Every button/link/tab/form control must have real behavior.")
        appendLine("- Preserve unaffected working modules.")
        appendLine("- Return one full updated HTML document.")
        appendLine()
        appendLine("Current app HTML:")
        appendLine(htmlPayload)
    }.trim()
}

internal fun buildAutoSoulToolInstruction(
    roundIndex: Int,
    maxCallsPerRound: Int,
    alreadyInvoked: Boolean = false
): String {
    return buildString {
        appendLine("Built-in tool available: autosoul_agent (Android automation, Open-AutoGLM rules).")
        appendLine("Current round: $roundIndex")
        if (alreadyInvoked) {
            appendLine("autosoul_agent has already been invoked for this user task.")
            appendLine("Do NOT call autosoul_agent again in this task.")
            appendLine("Continue with a normal user-facing reply based on existing tool results.")
        } else {
            appendLine("Use this tool only for Android phone automation tasks (Launch/Tap/Swipe/Type/Back/Home).")
            appendLine("Do not call this tool for normal Q&A or memory tasks.")
            appendLine("First write a normal visible reply, then append tool call tags if needed.")
            appendLine("At most $maxCallsPerRound tool calls in this round.")
            appendLine()
            appendLine("Tool call format:")
            appendLine("<tool_call>{\"serverId\":\"builtin_autosoul\",\"toolName\":\"autosoul_agent\",\"arguments\":{\"task\":\"...\"}}</tool_call>")
            appendLine()
            appendLine("Arguments policy:")
            appendLine("- task: required, concise and executable Android automation goal.")
            appendLine("- Optional: app/package for app hint. If provided, use Open-AutoGLM supported app names or package names.")
            appendLine("- Prefer package when available, e.g. com.tencent.mm / com.taobao.taobao / com.jingdong.app.mall.")
            appendLine("- For one user task, call autosoul_agent only once with the complete goal.")
            appendLine("- Do not split one task into multiple autosoul_agent calls.")
            appendLine("- Never fabricate execution results. Wait for tool result context in next round.")
        }
    }.trim()
}

internal fun buildAutoBrowserToolInstruction(
    roundIndex: Int,
    maxCallsPerRound: Int
): String {
    val callLimit = minOf(maxCallsPerRound.coerceAtLeast(1), 8)
    return buildString {
        appendLine("Built-in tool available: autobrowser (Android WebView browser automation).")
        appendLine("Language support: follow the user's language (Chinese or English).")
        appendLine("语言支持：按用户语言回复（中文/英文均可）。")
        appendLine("Current round: $roundIndex")
        appendLine("Use this tool only when the user explicitly selected AutoBrowser in this chat.")
        appendLine("Do not call AutoBrowser for generic Q&A, memory tasks, or AutoSoul phone automation tasks.")
        appendLine("First write a normal visible reply, then append tool call tags if needed.")
        appendLine("At most $callLimit AutoBrowser call(s) in this round.")
        appendLine()
        appendLine("Tool call format:")
        appendLine("<tool_call>{\"serverId\":\"builtin_autobrowser\",\"toolName\":\"autobrowser_start_session\",\"arguments\":{...}}</tool_call>")
        appendLine()
        appendLine("Supported toolName values:")
        appendLine("- autobrowser_start_session: start browser session, optional arguments.url")
        appendLine("- autobrowser_navigate: navigate to URL, arguments.url required")
        appendLine("- autobrowser_click_ref: click element by snapshot ref, arguments.ref required")
        appendLine("- autobrowser_fill_css: fill input by css selector, arguments.selector + arguments.value")
        appendLine("- autobrowser_exec_js: execute JS, arguments.script required")
        appendLine("- autobrowser_wait: wait seconds, arguments.seconds (default 1)")
        appendLine("- autobrowser_snapshot: capture compact accessibility tree snapshot and refs (@e1, @e2...)")
        appendLine("- autobrowser_upload_file: upload system file to file input, arguments.selector required")
        appendLine("- autobrowser_close_session: close current browser session")
        appendLine()
        appendLine("Rules:")
        appendLine("- For click_ref, call autobrowser_snapshot first in same task to obtain refs.")
        appendLine("- Prefer compact a11y snapshot refs; do not ask for full DOM/HTML/JSON dumps.")
        appendLine("- Re-snapshot after navigate/click when page state changes.")
        appendLine("- If the task is still in progress, emit executable <tool_call> tags in this round; do not stop at narrative-only planning text.")
        appendLine("- Keep arguments concise and deterministic.")
        appendLine("- Never fabricate browser execution results. Wait for tool results in next round.")
    }.trim()
}

internal fun buildMemoryToolInstruction(
    roundIndex: Int,
    maxCallsPerRound: Int,
    memories: List<MemoryItem>,
    allowedActions: Set<String>
): String {
    val shown = memories.take(12)
    val callLimit = minOf(maxCallsPerRound.coerceAtLeast(1), 2)
    val normalizedAllowed =
        allowedActions
            .map { it.trim().lowercase() }
            .filter { it in setOf("write", "delete", "list") }
            .toSet()
    val allowedSummary =
        if (normalizedAllowed.isEmpty()) {
            "none"
        } else {
            normalizedAllowed.sorted().joinToString(", ")
        }
    return buildString {
        appendLine("Built-in memory tools available: memory_write, memory_delete, memory_list.")
        appendLine("Current round: $roundIndex")
        appendLine("Use memory tools ONLY when the user explicitly asks to remember, forget, or show memories.")
        appendLine("Allowed memory actions in this user turn: $allowedSummary")
        appendLine("Do not call autosoul_agent for memory tasks.")
        appendLine("First write a normal visible reply, then append tool call tags if needed.")
        appendLine("At most $callLimit memory tool call(s) in this round.")
        appendLine()
        appendLine("Tool call format:")
        appendLine("<tool_call>{\"serverId\":\"builtin_memory\",\"toolName\":\"memory_write\",\"arguments\":{\"content\":\"...\"}}</tool_call>")
        appendLine()
        appendLine("Memory tool arguments:")
        appendLine("- memory_write: arguments.content (or memory/text/item).")
        appendLine("- memory_delete: arguments.id OR arguments.content.")
        appendLine("- memory_list: arguments can be empty object {}.")
        appendLine()
        appendLine("Current saved memories:")
        if (shown.isEmpty()) {
            appendLine("- (empty)")
        } else {
            shown.forEach { memory ->
                append("- id=")
                append(memory.id)
                append(": ")
                appendLine(memory.content.trim().take(180))
            }
            val more = memories.size - shown.size
            if (more > 0) {
                append("- ... and ")
                append(more)
                appendLine(" more")
            }
        }
    }.trim()
}

internal fun buildMcpToolCallInstruction(
    servers: List<McpConfig>,
    roundIndex: Int,
    maxCallsPerRound: Int
): String {
    val maxToolsPerServer = 24

    return buildString {
        appendLine("You can use MCP tools in this round.")
        appendLine("Current round: $roundIndex")
        appendLine("First write a normal visible reply for the user.")
        appendLine("If tool calls are needed, append tags AFTER your visible reply:")
        appendLine("<mcp_call>{\"serverId\":\"...\",\"toolName\":\"...\",\"arguments\":{}}</mcp_call>")
        appendLine()
        appendLine("Rules:")
        appendLine("- If no tool is needed, do not output any mcp_call tags.")
        appendLine("- At most $maxCallsPerRound tool calls in this round.")
        appendLine("- toolName must match exactly from the list below.")
        appendLine("- arguments must be a JSON object.")
        appendLine("- Do not mention planning, routers, or internal logic.")
        appendLine()
        appendLine("Available MCP servers and tools:")

        servers.forEach { server ->
            append("- Server: ")
            append(server.name.trim().ifBlank { "Unnamed" })
            append(" (id=")
            append(server.id)
            appendLine(")")

            server.tools.take(maxToolsPerServer).forEach { tool ->
                append("  - Tool: ")
                appendLine(tool.name)
                val desc = tool.description.trim()
                if (desc.isNotBlank()) {
                    append("    Description: ")
                    appendLine(desc.take(240))
                }
                if (tool.parameters.isNotEmpty()) {
                    appendLine("    Parameters:")
                    tool.parameters.forEach { param ->
                        append("      - ")
                        append(param.name)
                        append(" (")
                        append(param.type)
                        append(")")
                        if (param.required) append(" [required]")
                        val pDesc = param.description.trim()
                        if (pDesc.isNotBlank()) {
                            append(": ")
                            append(pDesc.take(180))
                        }
                        appendLine()
                    }
                }
            }
        }
    }.trim()
}

internal fun buildMcpRoundResultContext(roundIndex: Int, summary: String): String {
    val cleaned = summary.trim().ifBlank { "- No tool output." }
    return buildString {
        append("Tool results from round ")
        append(roundIndex)
        appendLine(":")
        appendLine(cleaned.take(5000))
        appendLine()
        appendLine("Use these results to continue the same user request.")
        appendLine("If more data is needed, you may output new <tool_call> tags.")
    }.trim()
}

internal fun parseMcpToolCallsPayload(text: String): List<PlannedMcpToolCall> {
    val cleaned = stripMarkdownCodeFences(text).trim()
    if (cleaned.isBlank()) return emptyList()

    val candidate = extractFirstJsonCandidate(cleaned)
    val root = candidate?.let { raw -> runCatching { JsonParser.parseString(raw) }.getOrNull() }
    if (root == null) {
        return parseLooseMcpToolCall(cleaned)?.let { listOf(it) } ?: emptyList()
    }

    val callsElement =
        when {
            root.isJsonObject && root.asJsonObject.has("calls") -> root.asJsonObject.get("calls")
            root.isJsonObject && root.asJsonObject.has("mcp_call") -> root.asJsonObject.get("mcp_call")
            root.isJsonObject && root.asJsonObject.has("tool_call") -> root.asJsonObject.get("tool_call")
            root.isJsonObject && root.asJsonObject.has("call") -> root.asJsonObject.get("call")
            root.isJsonObject && root.asJsonObject.has("toolCall") -> root.asJsonObject.get("toolCall")
            root.isJsonObject && root.asJsonObject.has("mcpCall") -> root.asJsonObject.get("mcpCall")
            else -> root
        }

    val callsArray =
        when {
            callsElement.isJsonArray -> callsElement.asJsonArray
            callsElement.isJsonObject -> com.google.gson.JsonArray().apply { add(callsElement) }
            else -> return emptyList()
        }

    fun getString(obj: com.google.gson.JsonObject, vararg keys: String): String? {
        return keys.asSequence().mapNotNull { key ->
            val el = runCatching { obj.get(key) }.getOrNull() ?: return@mapNotNull null
            if (!el.isJsonPrimitive) return@mapNotNull null
            val value = runCatching { el.asString }.getOrNull()?.trim().orEmpty()
            value.takeIf { it.isNotBlank() }
        }.firstOrNull()
    }

    fun getArgsObject(obj: com.google.gson.JsonObject): com.google.gson.JsonObject {
        fun fromKey(key: String): com.google.gson.JsonObject? {
            val el = runCatching { obj.get(key) }.getOrNull() ?: return null
            if (el.isJsonObject) return el.asJsonObject
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                val raw = el.asString.trim()
                if (raw.isBlank()) return null
                return runCatching {
                    JsonParser.parseString(raw)
                        .takeIf { it.isJsonObject }
                        ?.asJsonObject
                }.getOrNull()
            }
            return null
        }
        return fromKey("arguments")
            ?: fromKey("args")
            ?: fromKey("input")
            ?: fromKey("params")
            ?: fromKey("parameters")
            ?: com.google.gson.JsonObject()
    }

    fun normalizeCallObject(obj: com.google.gson.JsonObject): com.google.gson.JsonObject {
        val nested =
            listOf("mcp_call", "tool_call", "call", "toolCall", "mcpCall")
                .asSequence()
                .mapNotNull { key ->
                    runCatching { obj.get(key) }.getOrNull()
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                }
                .firstOrNull()
        return nested ?: obj
    }

    val strictParsed = callsArray.mapNotNull { el ->
        val rawObj = runCatching { el.asJsonObject }.getOrNull() ?: return@mapNotNull null
        val obj = normalizeCallObject(rawObj)
        val toolName = getString(obj, "toolName", "tool_name", "tool", "name").orEmpty()
        if (toolName.isBlank()) return@mapNotNull null

        val serverId = getString(obj, "serverId", "server_id", "server", "mcpId", "mcp_id", "id").orEmpty()
        val args = getArgsObject(obj).entrySet().associate { entry ->
            entry.key to entry.value.toKotlinAny()
        }

        PlannedMcpToolCall(serverId = serverId, toolName = toolName, arguments = args)
    }
    if (strictParsed.isNotEmpty()) return strictParsed
    return parseLooseMcpToolCall(cleaned)?.let { listOf(it) } ?: emptyList()
}

internal fun extractFirstJsonCandidate(text: String): String? {
    val raw = text.trim()
    if (raw.isBlank()) return null

    val idxObj = raw.indexOf('{')
    val idxArr = raw.indexOf('[')
    val start =
        listOf(idxObj, idxArr)
            .filter { it >= 0 }
            .minOrNull()
            ?: return null

    val open = raw[start]
    val close = if (open == '{') '}' else ']'
    val end = raw.lastIndexOf(close)
    if (end <= start) return null

    return raw.substring(start, end + 1)
}

internal fun parseLooseMcpToolCall(text: String): PlannedMcpToolCall? {
    val toolName =
        extractQuotedField(text, listOf("toolName", "tool_name", "tool", "name"))
            .orEmpty()
            .trim()
    if (toolName.isBlank()) return null

    val serverId =
        extractQuotedField(text, listOf("serverId", "server_id", "server", "mcpId", "mcp_id", "id"))
            .orEmpty()
            .trim()

    val argsFromJson =
        extractObjectField(text, listOf("arguments", "args", "input", "params", "parameters"))
            ?.let { raw ->
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            }
            ?.entrySet()
            ?.associate { entry -> entry.key to entry.value.toKotlinAny() }
            .orEmpty()
            .toMutableMap()

    if (argsFromJson.isEmpty()) {
        extractQuotedField(text, listOf("arguments", "args", "input", "params", "parameters"))
            ?.takeIf { it.startsWith("{") && it.endsWith("}") }
            ?.let { raw ->
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            }
            ?.entrySet()
            ?.forEach { entry -> argsFromJson[entry.key] = entry.value.toKotlinAny() }
    }

    if (argsFromJson.isEmpty()) {
        extractQuotedField(text, listOf("value"))?.takeIf { it.isNotBlank() }?.let { value ->
            argsFromJson["value"] = value
        }
    }

    return PlannedMcpToolCall(
        serverId = serverId,
        toolName = toolName,
        arguments = argsFromJson
    )
}

internal fun extractQuotedField(text: String, keys: List<String>): String? {
    keys.forEach { key ->
        val escapedKey = Regex.escape(key)
        val quotedRegex = Regex("\"$escapedKey\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", setOf(RegexOption.IGNORE_CASE))
        val looseQuotedRegex = Regex("\\b$escapedKey\\b\\s*[:=]\\s*\"((?:\\\\.|[^\"\\\\])*)\"", setOf(RegexOption.IGNORE_CASE))
        val looseSingleQuotedRegex = Regex("\\b$escapedKey\\b\\s*[:=]\\s*'([^']*)'", setOf(RegexOption.IGNORE_CASE))
        val bareRegex = Regex("\\b$escapedKey\\b\\s*[:=]\\s*([^,\\n\\r}\\]]+)", setOf(RegexOption.IGNORE_CASE))

        val value =
            quotedRegex.find(text)?.groupValues?.getOrNull(1)
                ?: looseQuotedRegex.find(text)?.groupValues?.getOrNull(1)
                ?: looseSingleQuotedRegex.find(text)?.groupValues?.getOrNull(1)
                ?: bareRegex.find(text)?.groupValues?.getOrNull(1)

        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return@forEach

        val normalized = raw.removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'").trim()
        if (normalized.isBlank()) return@forEach

        val decoded = runCatching { JsonParser.parseString("\"$normalized\"").asString }.getOrDefault(normalized)
        return decoded.trim()
    }
    return null
}

internal fun extractObjectField(text: String, keys: List<String>): String? {
    keys.forEach { key ->
        val escapedKey = Regex.escape(key)
        val markerRegex = Regex("(?:\"$escapedKey\"|\\b$escapedKey\\b)\\s*:\\s*\\{", setOf(RegexOption.IGNORE_CASE))
        val marker = markerRegex.find(text) ?: return@forEach
        val start = marker.range.last
        val end = findMatchingBraceEnd(text, start)
        if (start >= 0 && end >= start && end < text.length) {
            return text.substring(start, end + 1)
        }
    }
    return null
}

internal fun findMatchingBraceEnd(text: String, startIndex: Int): Int {
    if (startIndex !in text.indices || text[startIndex] != '{') return -1
    var depth = 0
    var inString = false
    var escaped = false

    for (index in startIndex until text.length) {
        val c = text[index]
        if (inString) {
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = false
            }
            continue
        }

        when (c) {
            '"' -> inString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return index
                if (depth < 0) return -1
            }
        }
    }
    return -1
}

internal fun com.google.gson.JsonElement.toKotlinAny(): Any? {
    return when {
        isJsonNull -> null
        isJsonPrimitive -> {
            val p = asJsonPrimitive
            when {
                p.isBoolean -> p.asBoolean
                p.isNumber -> p.asNumber
                else -> p.asString
            }
        }

        isJsonArray -> asJsonArray.map { it.toKotlinAny() }
        isJsonObject -> asJsonObject.entrySet().associate { it.key to it.value.toKotlinAny() }
        else -> null
    }
}

internal suspend fun extractMemoryCandidatesFromTurn(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    userText: String,
    assistantText: String,
    extraHeaders: List<HttpHeader>
): List<String> {
    val u = userText.trim()
    val a = assistantText.trim()
    if (u.isBlank()) return emptyList()

    val systemPrompt =
        buildString {
            append("You are the memory gate for a chat assistant. Return ONLY a JSON array of strings. ")
            append("Use semantic judgment from the user message itself; do not rely on keyword matching. ")
            append("Only keep explicit long-term user memory: stable preferences, profile facts, or persistent requirements. ")
            append("Never infer, speculate, or rewrite uncertain statements. ")
            append("Never store one-off tasks, temporary requests, current-session actions, moods, or assistant-generated claims. ")
            append("If nothing should be stored, return []. Keep at most 2 short memory items.")
        }

    val userPrompt =
        buildString {
            append("User message:\n")
            append(u.take(900))
            if (a.isNotBlank()) {
                append("\n\nAssistant reply (context only, not a source of truth):\n")
                append(a.take(1200))
            }
            append("\n\nReturn JSON array only.")
        }

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userPrompt)
            ),
            extraHeaders = extraHeaders
        )

    return parseJsonStringArray(raw)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(2)
}

internal fun shouldAttemptMemoryExtraction(userText: String): Boolean {
    val text = userText.trim()
    if (text.isBlank()) return false
    if (text.startsWith("/")) return false
    val compact = text.replace(Regex("\\s+"), "")
    if (compact.length < 2) return false
    val hasSemanticChar = Regex("[\\p{L}\\p{N}\\u4e00-\\u9fa5]").containsMatchIn(compact)
    if (!hasSemanticChar) return false
    return true
}

internal fun filterMemoryCandidates(
    candidates: List<String>,
    userText: String
): List<String> {
    if (candidates.isEmpty()) return emptyList()

    val blockedMarkers =
        listOf(
            "maybe",
            "probably",
            "guess",
            "not sure",
            "uncertain",
            "unknown",
            "seems",
            "assistant",
            "chatgpt",
            "model",
            "可能",
            "也许",
            "大概",
            "猜测",
            "不确定",
            "似乎"
        )

    return candidates
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.length in 3..180 }
        .filter { candidate ->
            val lower = candidate.lowercase()
            blockedMarkers.none { marker -> lower.contains(marker) || candidate.contains(marker) }
        }
        .filter { candidate -> memoryCandidateIsGrounded(candidate, userText) }
        .distinct()
        .take(2)
}

internal fun memoryCandidateIsGrounded(candidate: String, userText: String): Boolean {
    val candidateLower = candidate.lowercase()
    val userLower = userText.lowercase()
    if (userLower.contains(candidateLower) || candidateLower.contains(userLower.take(36))) {
        return true
    }

    val englishTokens =
        Regex("[a-zA-Z]{4,}")
            .findAll(candidateLower)
            .map { it.value }
            .toSet()
    if (englishTokens.any { token -> userLower.contains(token) }) {
        return true
    }

    val chineseFragments =
        Regex("[\\u4e00-\\u9fa5]{2,}")
            .findAll(candidate)
            .map { it.value }
            .toSet()
    return chineseFragments.any { frag -> userText.contains(frag) }
}

internal suspend fun generateConversationTitle(
    chatApiClient: ChatApiClient,
    provider: ProviderConfig,
    modelId: String,
    transcript: String,
    extraHeaders: List<HttpHeader>
): String? {
    val t = transcript.trim()
    if (t.isBlank()) return null

    val systemPrompt =
        "Generate a concise conversation title (max 6 words). Respond with ONLY the title, no quotes, no punctuation."

    val raw =
        collectStreamContent(
            chatApiClient = chatApiClient,
            provider = provider,
            modelId = modelId,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = t.take(1800))
            ),
            extraHeaders = extraHeaders
        )

    val firstLine = stripMarkdownCodeFences(raw).lineSequence().firstOrNull().orEmpty().trim()
    return firstLine.takeIf { it.isNotBlank() }
}


