package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.LocalZiCodePolicyService
import com.zionchat.app.R
import com.zionchat.app.data.ZiCodeGitHubRepo
import com.zionchat.app.data.ZiCodeSettings
import com.zionchat.app.data.ZiCodeWorkspace
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private val ZionLabsCardColor = Color(0xFFF1F1F1)
private val ZionLabsBorderColor = Color(0xFFCACAD2)

@Composable
fun ZiCodeSettingsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val gitHubService = LocalZiCodeGitHubService.current
    val scope = rememberCoroutineScope()

    val settings by repository.zicodeSettingsFlow.collectAsState(initial = ZiCodeSettings())
    val savedWorkspaces by repository.zicodeWorkspacesFlow.collectAsState(initial = emptyList())

    var patInput by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var repos by remember { mutableStateOf<List<ZiCodeGitHubRepo>>(emptyList()) }
    val patSavedText = stringResource(R.string.zicode_settings_pat_saved)
    val incompletePatText = stringResource(R.string.zicode_workspace_incomplete_pat)
    val syncSuccessTemplate = stringResource(R.string.zicode_settings_sync_success)
    val syncFailedPrefix = stringResource(R.string.zicode_settings_sync_failed)

    LaunchedEffect(settings.pat) {
        if (patInput.isBlank()) {
            patInput = settings.pat
        }
    }

    val displayWorkspaces = remember(repos, savedWorkspaces) {
        if (repos.isNotEmpty()) {
            repos.map { repo ->
                val existing =
                    savedWorkspaces.firstOrNull {
                        it.owner.equals(repo.owner, ignoreCase = true) &&
                            it.repo.equals(repo.name, ignoreCase = true)
                    }
                if (existing != null) {
                    existing.copy(
                        defaultBranch = repo.defaultBranch,
                        displayName = repo.fullName.ifBlank { "${repo.owner}/${repo.name}" }
                    )
                } else {
                    ZiCodeWorkspace(
                        id = buildStableZiCodeWorkspaceId(repo.owner, repo.name),
                        owner = repo.owner,
                        repo = repo.name,
                        defaultBranch = repo.defaultBranch,
                        displayName = repo.fullName.ifBlank { "${repo.owner}/${repo.name}" }
                    )
                }
            }
        } else {
            savedWorkspaces
        }
    }

    fun savePatOnly() {
        val token = patInput.trim()
        scope.launch {
            repository.setZiCodePat(token)
            syncMessage = patSavedText
        }
    }

    fun syncRepos() {
        val token = patInput.trim()
        if (token.isBlank()) {
            syncMessage = incompletePatText
            return
        }
        scope.launch {
            syncing = true
            syncMessage = null
            repository.setZiCodePat(token)
            val result = gitHubService.listAccessibleRepos(token)
            result.onSuccess { remote ->
                repos = remote
                val now = System.currentTimeMillis()
                val persisted = mutableListOf<ZiCodeWorkspace>()
                remote.forEach { repo ->
                    val existing =
                        savedWorkspaces.firstOrNull {
                            it.owner.equals(repo.owner, ignoreCase = true) &&
                                it.repo.equals(repo.name, ignoreCase = true)
                        }
                    val workspace =
                        if (existing != null) {
                            existing.copy(
                                defaultBranch = repo.defaultBranch,
                                displayName = repo.fullName.ifBlank { "${repo.owner}/${repo.name}" },
                                updatedAt = now
                            )
                        } else {
                            ZiCodeWorkspace(
                                id = buildStableZiCodeWorkspaceId(repo.owner, repo.name),
                                owner = repo.owner,
                                repo = repo.name,
                                defaultBranch = repo.defaultBranch,
                                displayName = repo.fullName.ifBlank { "${repo.owner}/${repo.name}" },
                                createdAt = now,
                                updatedAt = now
                            )
                        }
                    repository.upsertZiCodeWorkspace(workspace)?.let { persisted += it }
                }
                if (settings.currentWorkspaceId.isNullOrBlank()) {
                    persisted.firstOrNull()?.let { repository.setZiCodeCurrentWorkspace(it.id) }
                }
                syncMessage = String.format(syncSuccessTemplate, remote.size)
            }.onFailure { error ->
                syncMessage = "$syncFailedPrefix ${error.message.orEmpty()}"
            }
            syncing = false
        }
    }

    SettingsPage(
        title = stringResource(R.string.zicode_settings_title),
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZionLabsSectionTitle(stringResource(R.string.zicode_workspace_pat))
            ZionLabsCard {
                BasicTextField(
                    value = patInput,
                    onValueChange = {
                        patInput = it
                        syncMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, ZionLabsBorderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = SourceSans3,
                        fontSize = 15.sp,
                        color = TextPrimary
                    ),
                    cursorBrush = SolidColor(TextPrimary),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    decorationBox = { innerField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (patInput.isBlank()) {
                                Text(
                                    text = stringResource(R.string.zicode_workspace_pat_hint),
                                    fontFamily = SourceSans3,
                                    fontSize = 15.sp,
                                    color = TextSecondary
                                )
                            }
                            innerField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZionLabsActionButton(
                        label = stringResource(R.string.zicode_settings_save_pat),
                        filled = false,
                        enabled = patInput.isNotBlank() && !syncing,
                        modifier = Modifier.weight(1f),
                        onClick = ::savePatOnly
                    )
                    ZionLabsActionButton(
                        label = if (syncing) stringResource(R.string.zicode_settings_syncing) else stringResource(R.string.zicode_settings_sync_projects),
                        filled = true,
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                        onClick = ::syncRepos
                    )
                }

                if (!syncMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = syncMessage.orEmpty(),
                        fontFamily = SourceSans3,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            ZionLabsCard {
                ZionLabsLinkRow(
                    label = stringResource(R.string.zicode_settings_available_tools),
                    onClick = { navController.navigate("zicode_tools") }
                )
            }

            ZionLabsSectionTitle(stringResource(R.string.zicode_settings_projects_title))
            if (displayWorkspaces.isEmpty()) {
                ZionLabsCard {
                    Text(
                        text = stringResource(R.string.zicode_settings_projects_empty),
                        fontFamily = SourceSans3,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            } else {
                displayWorkspaces.forEachIndexed { index, workspace ->
                    val selected =
                        settings.currentWorkspaceId == workspace.id ||
                            savedWorkspaces.firstOrNull { it.id == settings.currentWorkspaceId }?.let { current ->
                                current.owner.equals(workspace.owner, ignoreCase = true) &&
                                    current.repo.equals(workspace.repo, ignoreCase = true)
                            } == true
                    ZionLabsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressableScale(
                                    pressedScale = 0.98f,
                                    onClick = {
                                        scope.launch {
                                            val persisted = repository.upsertZiCodeWorkspace(workspace) ?: workspace
                                            repository.setZiCodeCurrentWorkspace(persisted.id)
                                        }
                                    }
                                )
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = rememberResourceDrawablePainter(R.drawable.ic_zicode_repo),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(21.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = workspace.displayName,
                                    fontFamily = SourceSans3,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = workspace.defaultBranch,
                                    fontFamily = SourceSans3,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (index == displayWorkspaces.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ZiCodeToolsScreen(navController: NavController) {
    val policyService = LocalZiCodePolicyService.current
    var tools by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val spec = policyService.getToolspec()
        tools =
            spec.getAsJsonArray("tools")
                ?.mapNotNull { item -> item?.asString?.trim()?.takeIf { it.isNotBlank() } }
                .orEmpty()
    }

    SettingsPage(
        title = stringResource(R.string.zicode_tools_title),
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tools.isEmpty()) {
                ZionLabsCard {
                    Text(
                        text = stringResource(R.string.zicode_tools_empty),
                        fontFamily = SourceSans3,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            } else {
                tools.forEach { tool ->
                    ZionLabsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(TextPrimary, CircleShape)
                            )
                            Text(
                                text = tool,
                                fontFamily = SourceSans3,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ZionLabsSectionTitle(text: String) {
    Text(
        text = text,
        fontFamily = SourceSans3,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

private fun buildStableZiCodeWorkspaceId(owner: String, repo: String): String {
    val ownerKey = owner.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    val repoKey = repo.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    return "zicode_${ownerKey}_${repoKey}"
}

@Composable
private fun ZionLabsCard(content: @Composable Column.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ZionLabsCardColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun ZionLabsActionButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(
                if (filled) TextPrimary else Color.White,
                RoundedCornerShape(21.dp)
            )
            .border(1.dp, if (filled) TextPrimary else GrayLight, RoundedCornerShape(21.dp))
            .pressableScale(
                enabled = enabled,
                pressedScale = 0.98f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = SourceSans3,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else TextPrimary
        )
    }
}

@Composable
private fun ZionLabsLinkRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppIcons.MCPTools,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            fontFamily = SourceSans3,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
