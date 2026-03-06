package com.zionchat.app.ui.screens

import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.LocalZiCodeRepository
import com.zionchat.app.R
import com.zionchat.app.ui.components.AppModalBottomSheet
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.zicode.data.ZiCodeFilePreview
import com.zionchat.app.zicode.data.ZiCodeRepoNode
import com.zionchat.app.zicode.data.ZiCodeSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZiCodeFileBrowserScreen(
    navController: NavController,
    ownerArg: String,
    repoArg: String
) {
    val owner = Uri.decode(ownerArg)
    val repo = Uri.decode(repoArg)
    val repository = LocalZiCodeRepository.current
    val gitHubService = LocalZiCodeGitHubService.current
    val settings by repository.settingsFlow.collectAsState(initial = ZiCodeSettings())
    val scope = rememberCoroutineScope()
    val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentPath by rememberSaveable(owner, repo) { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<ZiCodeRepoNode>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ZiCodeFilePreview?>(null) }
    var loadingPreview by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(settings.githubToken, currentPath, owner, repo, refreshNonce) {
        val token = settings.githubToken.trim()
        if (token.isBlank()) {
            entries = emptyList()
            errorText = "请先配置 GitHub Token。"
            loading = false
            return@LaunchedEffect
        }
        loading = true
        errorText = null
        gitHubService.listDirectory(token, owner, repo, currentPath)
            .onSuccess { nodes ->
                entries = nodes
            }
            .onFailure { throwable ->
                entries = emptyList()
                errorText = throwable.message?.trim().orEmpty().ifBlank { "文件列表读取失败。" }
            }
        loading = false
    }

    fun openParent() {
        if (currentPath.isBlank()) return
        currentPath = currentPath.substringBeforeLast('/', "")
    }

    fun openFile(node: ZiCodeRepoNode) {
        scope.launch {
            val token = settings.githubToken.trim()
            if (token.isBlank()) {
                errorText = "请先配置 GitHub Token。"
                return@launch
            }
            loadingPreview = true
            gitHubService.readFile(token, owner, repo, node.path)
                .onSuccess { file ->
                    preview = file
                }
                .onFailure { throwable ->
                    errorText = throwable.message?.trim().orEmpty().ifBlank { "文件预览失败。" }
                }
            loadingPreview = false
        }
    }

    SettingsPage(
        title = repo,
        onBack = { navController.navigateUp() }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                ZiCodePanel {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZiCodeMetaText(text = "$owner / $repo")
                        Text(
                            text = if (currentPath.isBlank()) "仓库根目录" else currentPath,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SourceSans3
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZiCodeChip(text = "root", selected = currentPath.isBlank(), onClick = { currentPath = "" })
                            if (currentPath.isNotBlank()) {
                                ZiCodeChip(text = "返回上级", onClick = ::openParent)
                            }
                        }
                    }
                }
            }

            when {
                loading -> {
                    item {
                        ZiCodeLoadingPanel(text = "正在读取当前目录…")
                    }
                }

                errorText != null -> {
                    item {
                        ZiCodeEmptyPanel(
                            title = "文件读取失败",
                            body = errorText.orEmpty(),
                            actionLabel = "重试",
                            onAction = { refreshNonce += 1 }
                        )
                    }
                }

                entries.isEmpty() -> {
                    item {
                        ZiCodeEmptyPanel(
                            title = "目录为空",
                            body = "当前路径下没有可展示的文件或文件夹。",
                            actionLabel = "返回根目录",
                            onAction = { currentPath = "" }
                        )
                    }
                }

                else -> {
                    items(entries, key = { it.path }) { node ->
                        ZiCodeFileNodeRow(
                            node = node,
                            onClick = {
                                if (node.type == "dir") {
                                    currentPath = node.path
                                } else {
                                    openFile(node)
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (preview != null) {
            AppModalBottomSheet(
                onDismissRequest = { preview = null },
                sheetState = previewSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = preview?.path.orEmpty(),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SourceSans3
                    )
                    ZiCodeMetaText(
                        text =
                            buildString {
                                append("大小 ${preview?.size ?: 0L} bytes")
                                if (preview?.truncated == true) append(" · 已截断预览")
                            }
                    )
                    Text(
                        text = preview?.content.orEmpty(),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontFamily = SourceSans3
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (loadingPreview) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TextPrimary)
            }
        }
    }
}

@Composable
private fun ZiCodeFileNodeRow(
    node: ZiCodeRepoNode,
    onClick: () -> Unit
) {
    ZiCodePanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                .pressableScale(pressedScale = 0.98f, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (node.type == "dir") "DIR" else "FILE",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SourceSans3
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SourceSans3
                )
                ZiCodeMetaText(text = node.path)
            }
            if (node.type == "file") {
                Icon(
                    painter = rememberResourceDrawablePainter(R.drawable.ic_files),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = ">",
                    color = ZiCodeSecondaryText,
                    fontSize = 18.sp,
                    fontFamily = SourceSans3
                )
            }
        }
    }
}
