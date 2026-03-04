package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.data.ZiCodeRepoEntry
import com.zionchat.app.data.ZiCodeRepoFile
import com.zionchat.app.ui.components.AppModalBottomSheet
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class RepoDirUiState(
    val loading: Boolean = false,
    val entries: List<ZiCodeRepoEntry> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZiCodeRepoBrowserScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val gitHubService = LocalZiCodeGitHubService.current
    val scope = rememberCoroutineScope()

    val settings by repository.zicodeSettingsFlow.collectAsState(initial = com.zionchat.app.data.ZiCodeSettings())
    val workspaces by repository.zicodeWorkspacesFlow.collectAsState(initial = emptyList())
    val currentWorkspace = remember(workspaces, settings.currentWorkspaceId) {
        val key = settings.currentWorkspaceId?.trim().orEmpty()
        if (key.isBlank()) workspaces.firstOrNull() else workspaces.firstOrNull { it.id == key }
    }

    val pathStack = remember(currentWorkspace?.id) { mutableStateListOf("") }
    val dirStates = remember(currentWorkspace?.id) { mutableStateMapOf<String, RepoDirUiState>() }
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewFile by remember { mutableStateOf<ZiCodeRepoFile?>(null) }
    val previewSheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun currentPath(): String = pathStack.lastOrNull().orEmpty()

    fun loadDir(path: String, force: Boolean = false) {
        val workspace = currentWorkspace ?: return
        val token = settings.pat.trim()
        if (token.isBlank()) {
            dirStates[path] = RepoDirUiState(error = "请先在 ZiCode 设置中配置 PAT。")
            return
        }
        val existing = dirStates[path]
        if (!force && existing != null && !existing.loading && existing.error == null && existing.entries.isNotEmpty()) {
            return
        }
        dirStates[path] = RepoDirUiState(loading = true)
        scope.launch {
            val result = gitHubService.listRepoDir(workspace, token, workspace.defaultBranch, path)
            result.onSuccess { entries ->
                dirStates[path] = RepoDirUiState(entries = entries)
            }.onFailure { error ->
                dirStates[path] = RepoDirUiState(error = toFriendlyRepoError(error.message.orEmpty()))
            }
        }
    }

    fun openFile(path: String) {
        val workspace = currentWorkspace ?: return
        val token = settings.pat.trim()
        if (token.isBlank()) {
            previewError = "请先在 ZiCode 设置中配置 PAT。"
            previewFile = null
            return
        }
        previewLoading = true
        previewError = null
        previewFile = null
        scope.launch {
            val result = gitHubService.readRepoFile(workspace, token, workspace.defaultBranch, path)
            result.onSuccess { file ->
                previewFile = file
            }.onFailure { error ->
                previewError = toFriendlyRepoError(error.message.orEmpty())
            }
            previewLoading = false
        }
    }

    LaunchedEffect(currentWorkspace?.id, settings.pat) {
        pathStack.clear()
        pathStack.add("")
        dirStates.clear()
        loadDir("")
    }

    val nowPath = currentPath()
    val currentState = dirStates[nowPath] ?: RepoDirUiState(loading = true)
    val headerTitle = currentWorkspace?.repo?.ifBlank { "ZiCode" } ?: "ZiCode"
    val headerSubtitle = currentWorkspace?.defaultBranch?.ifBlank { "" } ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
        ) {
            if (headerSubtitle.isNotBlank()) {
                Text(
                    text = "分支：$headerSubtitle",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    fontFamily = SourceSans3,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            if (nowPath.isNotBlank()) {
                Text(
                    text = nowPath,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    fontFamily = SourceSans3,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (nowPath.isNotBlank()) {
                    item(key = "repo_up") {
                        RepoEntryRow(
                            title = "返回上一级",
                            subtitle = nowPath.substringBeforeLast('/', ""),
                            isDir = true,
                            onClick = {
                                if (pathStack.size > 1) {
                                    pathStack.removeAt(pathStack.lastIndex)
                                }
                            }
                        )
                    }
                }

                if (currentState.loading) {
                    item(key = "repo_loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TextPrimary
                            )
                            Text(
                                text = "正在加载目录...",
                                fontFamily = SourceSans3,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                currentState.error?.takeIf { it.isNotBlank() }?.let { errorText ->
                    item(key = "repo_error") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = errorText,
                                fontFamily = SourceSans3,
                                fontSize = 13.sp,
                                color = Color(0xFFB3261E)
                            )
                        }
                    }
                }

                if (!currentState.loading && currentState.error.isNullOrBlank() && currentState.entries.isEmpty()) {
                    item(key = "repo_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "当前目录为空。",
                                fontFamily = SourceSans3,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                items(currentState.entries, key = { "${it.type}:${it.path}" }) { entry ->
                    RepoEntryRow(
                        title = entry.name,
                        subtitle = if (entry.type.equals("dir", ignoreCase = true)) "目录" else "${entry.size} B",
                        isDir = entry.type.equals("dir", ignoreCase = true),
                        onClick = {
                            if (entry.type.equals("dir", ignoreCase = true)) {
                                pathStack.add(entry.path)
                                loadDir(entry.path)
                            } else {
                                openFile(entry.path)
                            }
                        }
                    )
                }

                item(key = "repo_bottom") {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }

        PageTopBar(
            title = headerTitle,
            onBack = { navController.navigateUp() }
        )
    }

    if (previewLoading || previewError != null || previewFile != null) {
        AppModalBottomSheet(
            onDismissRequest = {
                previewLoading = false
                previewError = null
                previewFile = null
            },
            sheetState = previewSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = previewFile?.name ?: "文件预览",
                    fontFamily = SourceSans3,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                when {
                    previewLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TextPrimary
                            )
                            Text(
                                text = "正在读取文件...",
                                fontFamily = SourceSans3,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    !previewError.isNullOrBlank() -> {
                        Text(
                            text = previewError.orEmpty(),
                            fontFamily = SourceSans3,
                            fontSize = 13.sp,
                            color = Color(0xFFB3261E)
                        )
                    }

                    previewFile != null -> {
                        Text(
                            text = previewFile?.path.orEmpty(),
                            fontFamily = SourceSans3,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFFF1F1F1), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = previewFile?.content.orEmpty().take(12000),
                                fontFamily = SourceSans3,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoEntryRow(
    title: String,
    subtitle: String,
    isDir: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp))
            .pressableScale(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = AppIcons.Files,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = SourceSans3,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontFamily = SourceSans3,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isDir) {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun toFriendlyRepoError(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return "读取仓库失败，请稍后重试。"
    return when {
        text.contains("Git Repository is empty", ignoreCase = true) -> "该仓库为空，请先提交初始代码后再使用 ZiCode。"
        text.contains("404", ignoreCase = true) -> "目标目录或文件不存在，或当前账号没有访问权限。"
        text.contains("PAT", ignoreCase = true) -> "PAT 无效或已过期，请在 ZiCode 设置中更新。"
        text.contains("rate limit", ignoreCase = true) -> "GitHub 请求频率受限，请稍后再试。"
        else -> text
    }
}
