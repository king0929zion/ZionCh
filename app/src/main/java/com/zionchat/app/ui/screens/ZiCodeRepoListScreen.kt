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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.zicode.data.ZiCodeRemoteRepo
import com.zionchat.app.zicode.data.ZiCodeSettings
import kotlinx.coroutines.launch

private data class ZiCodeRepoListItem(
    val repo: ZiCodeRemoteRepo,
    val lastAccessedAt: Long
)

@Composable
fun ZiCodeRepoListScreen(navController: NavController) {
    val repository = LocalZiCodeRepository.current
    val gitHubService = LocalZiCodeGitHubService.current
    val scope = rememberCoroutineScope()
    val settings by repository.settingsFlow.collectAsState(initial = ZiCodeSettings())
    val recentRepos by repository.recentReposFlow.collectAsState(initial = emptyList())

    var remoteRepos by remember { mutableStateOf<List<ZiCodeRemoteRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var creatingRepo by remember { mutableStateOf(false) }

    LaunchedEffect(settings.githubToken) {
        val token = settings.githubToken.trim()
        if (token.isBlank()) {
            remoteRepos = emptyList()
            errorText = null
            loading = false
            repository.clearViewer()
            return@LaunchedEffect
        }
        loading = true
        errorText = null
        val viewerResult = gitHubService.fetchViewer(token)
        viewerResult.getOrNull()?.let { viewer ->
            repository.updateViewer(viewer)
        } ?: repository.clearViewer()
        val reposResult = gitHubService.listRepos(token)
        reposResult.onSuccess { repos ->
            remoteRepos = repos
            repository.syncRecentRepos(repos)
        }.onFailure { throwable ->
            remoteRepos = emptyList()
            errorText = throwable.message?.trim().orEmpty().ifBlank { "GitHub 仓库读取失败。" }
        }
        loading = false
    }

    val repoItems =
        remember(remoteRepos, recentRepos) {
            val recentMap = recentRepos.associateBy { "${it.owner.lowercase()}/${it.name.lowercase()}" }
            val fromRemote =
                remoteRepos.map { repo ->
                    ZiCodeRepoListItem(
                        repo = repo,
                        lastAccessedAt = recentMap["${repo.owner.lowercase()}/${repo.name.lowercase()}"]?.lastAccessedAt ?: 0L
                    )
                }
            fromRemote.sortedWith(
                compareByDescending<ZiCodeRepoListItem> { it.lastAccessedAt }
                    .thenByDescending { it.repo.updatedAt }
                    .thenByDescending { it.repo.pushedAt }
            )
        }

    fun openRepo(repo: ZiCodeRemoteRepo) {
        scope.launch {
            repository.touchRecentRepo(repo)
            val owner = Uri.encode(repo.owner)
            val name = Uri.encode(repo.name)
            navController.navigate("zicode_repo/$owner/$name")
        }
    }

    SettingsPage(
        title = "ZiCode",
        onBack = { navController.navigateUp() },
        trailing = {
            ZiCodeCircleButton(
                onClick = { showCreateRepoDialog = true }
            ) {
                Icon(
                    imageVector = AppIcons.Plus,
                    contentDescription = "新建仓库",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ZiCodePageBackground)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                ZiCodeConnectionCard(
                    settings = settings,
                    loading = loading,
                    errorText = errorText,
                    onOpenSettings = { navController.navigate("zicode_settings") }
                )
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
                ZiCodeSectionTitle("Repositories")
            }

            if (settings.githubToken.isBlank()) {
                item {
                    ZiCodeEmptyPanel(
                        title = "先连接 GitHub",
                        body = "ZiCode 把 GitHub 当成远程执行环境。先在设置里填入 Token，仓库列表、文件查看和项目会话才会解锁。",
                        actionLabel = "打开设置",
                        onAction = { navController.navigate("zicode_settings") }
                    )
                }
            } else if (loading) {
                item {
                    ZiCodeLoadingPanel(text = "正在同步仓库列表…")
                }
            } else if (repoItems.isEmpty()) {
                item {
                    ZiCodeEmptyPanel(
                        title = "还没有可访问仓库",
                        body = "你可以直接新建一个仓库，或者确认当前 Token 是否具备 `repo` 读取权限。",
                        actionLabel = "新建仓库",
                        onAction = { showCreateRepoDialog = true }
                    )
                }
            } else {
                items(repoItems, key = { "${it.repo.owner}/${it.repo.name}" }) { item ->
                    ZiCodeRepoRow(
                        item = item,
                        onClick = { openRepo(item.repo) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showCreateRepoDialog) {
            ZiCodeCreateRepoDialog(
                creating = creatingRepo,
                onDismiss = { showCreateRepoDialog = false },
                onCreate = { name, description, privateRepo ->
                    scope.launch {
                        val token = settings.githubToken.trim()
                        if (token.isBlank()) {
                            errorText = "请先配置 GitHub Token。"
                            showCreateRepoDialog = false
                            return@launch
                        }
                        creatingRepo = true
                        gitHubService.createRepo(token, name, description, privateRepo)
                            .onSuccess { repo ->
                                showCreateRepoDialog = false
                                remoteRepos = listOf(repo) + remoteRepos.filterNot {
                                    it.owner == repo.owner && it.name == repo.name
                                }
                                repository.touchRecentRepo(repo)
                                openRepo(repo)
                            }
                            .onFailure { throwable ->
                                errorText = throwable.message?.trim().orEmpty().ifBlank { "新建仓库失败。" }
                            }
                        creatingRepo = false
                    }
                }
            )
        }
    }
}

@Composable
private fun ZiCodeConnectionCard(
    settings: ZiCodeSettings,
    loading: Boolean,
    errorText: String?,
    onOpenSettings: () -> Unit
) {
    ZiCodePanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.GitHub,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = settings.viewer?.displayName ?: settings.viewer?.login ?: "GitHub 连接",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SourceSans3
                    )
                    ZiCodeMetaText(
                        text =
                            when {
                                settings.githubToken.isBlank() -> "当前未连接 GitHub Token"
                                loading -> "正在校验账号和仓库权限"
                                else -> "仓库列表按最近访问优先排序"
                            }
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text("设置", color = TextPrimary)
                }
            }
            errorText?.takeIf { it.isNotBlank() }?.let {
                ZiCodeMetaText(text = it)
            }
        }
    }
}

@Composable
private fun ZiCodeRepoRow(
    item: ZiCodeRepoListItem,
    onClick: () -> Unit
) {
    ZiCodePanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                .pressableScale(pressedScale = 0.98f, onClick = onClick)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.repo.name,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SourceSans3
                    )
                    ZiCodeMetaText(text = item.repo.owner)
                }
                ZiCodeMiniStatusBadge(text = if (item.repo.privateRepo) "Private" else "Public")
            }
            if (item.repo.description.isNotBlank()) {
                ZiCodeMetaText(text = item.repo.description)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZiCodeChip(
                    text =
                        if (item.lastAccessedAt > 0) {
                            "最近访问"
                        } else {
                            "仓库项目"
                        }
                )
                item.repo.homepageUrl?.takeIf { it.isNotBlank() }?.let {
                    ZiCodeChip(text = "GitHub Pages")
                }
            }
        }
    }
}

@Composable
internal fun ZiCodeEmptyPanel(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    ZiCodePanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SourceSans3
            )
            ZiCodeMetaText(text = body)
            TextButton(onClick = onAction) {
                Text(actionLabel, color = TextPrimary)
            }
        }
    }
}

@Composable
internal fun ZiCodeLoadingPanel(text: String) {
    ZiCodePanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = TextPrimary,
                strokeWidth = 2.dp
            )
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SourceSans3
            )
        }
    }
}

@Composable
private fun ZiCodeCreateRepoDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, privateRepo: Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var privateRepo by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !creating && name.trim().isNotBlank(),
                onClick = {
                    onCreate(name.trim(), description.trim(), privateRepo)
                }
            ) {
                Text(if (creating) "创建中…" else "创建", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text("取消", color = TextPrimary)
            }
        },
        title = { Text("新建仓库", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("仓库名称") },
                    colors = ziCodeFieldColors()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("仓库描述") },
                    colors = ziCodeFieldColors()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("设为私有仓库", color = TextPrimary, fontFamily = SourceSans3)
                    Switch(
                        checked = privateRepo,
                        onCheckedChange = { privateRepo = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TextPrimary,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = ZiCodePanelPressedGray,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        },
        containerColor = Color.White
    )
}

@Composable
internal fun ziCodeFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ZiCodePanelGray,
        unfocusedContainerColor = ZiCodePanelGray,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = ZiCodeSecondaryText,
        unfocusedLabelColor = ZiCodeSecondaryText,
        cursorColor = TextPrimary
    )
