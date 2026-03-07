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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.LocalZiCodeRepository
import com.zionchat.app.R
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
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
    var refreshNonce by remember { mutableStateOf(0) }
    val repoLoadFailedText = stringResource(R.string.zicode_repo_load_failed)
    val missingTokenText = stringResource(R.string.zicode_missing_token)
    val createRepoFailedText = stringResource(R.string.zicode_create_repo_failed)

    LaunchedEffect(settings.githubToken, refreshNonce) {
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
        gitHubService.fetchViewer(token).onSuccess { repository.updateViewer(it) }.onFailure { repository.clearViewer() }
        gitHubService.listRepos(token)
            .onSuccess { repos ->
                remoteRepos = repos
                repository.syncRecentRepos(repos)
            }
            .onFailure { throwable ->
                remoteRepos = emptyList()
                errorText = throwable.message?.trim().orEmpty().ifBlank { repoLoadFailedText }
            }
        loading = false
    }

    val repoItems =
        remember(remoteRepos, recentRepos) {
            val recentMap = recentRepos.associateBy { "${it.owner.lowercase()}/${it.name.lowercase()}" }
            remoteRepos.map { repo ->
                ZiCodeRepoListItem(
                    repo = repo,
                    lastAccessedAt = recentMap["${repo.owner.lowercase()}/${repo.name.lowercase()}"]?.lastAccessedAt ?: 0L
                )
            }.sortedWith(
                compareByDescending<ZiCodeRepoListItem> { it.lastAccessedAt }
                    .thenByDescending { it.repo.updatedAt }
                    .thenByDescending { it.repo.pushedAt }
            )
        }

    fun openRepo(repo: ZiCodeRemoteRepo) {
        scope.launch {
            repository.touchRecentRepo(repo)
            navController.navigate("zicode_repo/${Uri.encode(repo.owner)}/${Uri.encode(repo.name)}")
        }
    }

    SettingsPage(
        title = stringResource(R.string.zicode_name),
        onBack = { navController.navigateUp() },
        trailing = {
            ZiCodeCircleButton(onClick = { showCreateRepoDialog = true }) {
                Icon(
                    imageVector = AppIcons.Plus,
                    contentDescription = stringResource(R.string.zicode_create_repo),
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
                Spacer(modifier = Modifier.height(8.dp))
                ZiCodeSectionTitle(title = stringResource(R.string.zicode_repo_section))
            }

            when {
                settings.githubToken.isBlank() -> {
                    item {
                        ZiCodeEmptyPanel(
                            title = stringResource(R.string.zicode_connect_title),
                            body = stringResource(R.string.zicode_connect_body),
                            actionLabel = stringResource(R.string.zicode_open_settings),
                            onAction = { navController.navigate("zicode_settings") }
                        )
                    }
                }

                loading -> {
                    item { ZiCodeLoadingPanel(text = stringResource(R.string.zicode_loading_repos)) }
                }

                errorText != null -> {
                    item {
                        ZiCodeEmptyPanel(
                            title = stringResource(R.string.zicode_repo_error_title),
                            body = errorText.orEmpty(),
                            actionLabel = stringResource(R.string.zicode_retry),
                            onAction = { refreshNonce += 1 }
                        )
                    }
                }

                repoItems.isEmpty() -> {
                    item {
                        ZiCodeEmptyPanel(
                            title = stringResource(R.string.zicode_repo_empty_title),
                            body = stringResource(R.string.zicode_repo_empty_body),
                            actionLabel = stringResource(R.string.zicode_create_repo),
                            onAction = { showCreateRepoDialog = true }
                        )
                    }
                }

                else -> {
                    items(repoItems, key = { "${it.repo.owner}/${it.repo.name}" }) { item ->
                        ZiCodeRepoRow(item.repo, onClick = { openRepo(item.repo) })
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (showCreateRepoDialog) {
            ZiCodeCreateRepoDialog(
                creating = creatingRepo,
                onDismiss = { showCreateRepoDialog = false },
                onCreate = { name, description, privateRepo ->
                    scope.launch {
                        val token = settings.githubToken.trim()
                        if (token.isBlank()) {
                            errorText = missingTokenText
                            showCreateRepoDialog = false
                            return@launch
                        }
                        creatingRepo = true
                        gitHubService.createRepo(token, name, description, privateRepo)
                            .onSuccess { repo ->
                                showCreateRepoDialog = false
                                remoteRepos = listOf(repo) + remoteRepos.filterNot { it.owner == repo.owner && it.name == repo.name }
                                repository.touchRecentRepo(repo)
                                openRepo(repo)
                            }
                            .onFailure { throwable ->
                                errorText = throwable.message?.trim().orEmpty().ifBlank { createRepoFailedText }
                            }
                        creatingRepo = false
                    }
                }
            )
        }
    }
}

@Composable
private fun ZiCodeRepoRow(
    repo: ZiCodeRemoteRepo,
    onClick: () -> Unit
) {
    ZiCodePanel(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            ZiCodeMiniStatusBadge(
                text = stringResource(if (repo.privateRepo) R.string.zicode_private else R.string.zicode_public),
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = repo.name,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SourceSans3
                )
                repo.description.takeIf { it.isNotBlank() }?.let {
                    ZiCodeMetaText(text = it)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = SourceSans3)
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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextPrimary, strokeWidth = 2.dp)
            Text(text = text, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = SourceSans3)
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
            TextButton(enabled = !creating && name.trim().isNotBlank(), onClick = { onCreate(name.trim(), description.trim(), privateRepo) }) {
                Text(if (creating) stringResource(R.string.zicode_creating) else stringResource(R.string.zicode_create), color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text(stringResource(R.string.common_cancel), color = TextPrimary)
            }
        },
        title = { Text(stringResource(R.string.zicode_create_repo), color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.zicode_repo_name)) },
                    colors = ziCodeFieldColors()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.zicode_repo_description)) },
                    colors = ziCodeFieldColors()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.zicode_private_repo_switch), color = TextPrimary, fontFamily = SourceSans3)
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
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp)
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
