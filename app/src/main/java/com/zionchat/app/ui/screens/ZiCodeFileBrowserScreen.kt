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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
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
    val missingTokenText = stringResource(R.string.zicode_missing_token)
    val loadFilesFailedText = stringResource(R.string.zicode_files_load_failed)
    val previewFailedText = stringResource(R.string.zicode_preview_failed)

    LaunchedEffect(settings.githubToken, currentPath, owner, repo, refreshNonce) {
        val token = settings.githubToken.trim()
        if (token.isBlank()) {
            entries = emptyList()
            errorText = missingTokenText
            loading = false
            return@LaunchedEffect
        }
        loading = true
        errorText = null
        gitHubService.listDirectory(token, owner, repo, currentPath)
            .onSuccess { entries = it }
            .onFailure { throwable ->
                entries = emptyList()
                errorText = throwable.message?.trim().orEmpty().ifBlank { loadFilesFailedText }
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
                errorText = missingTokenText
                return@launch
            }
            loadingPreview = true
            gitHubService.readFile(token, owner, repo, node.path)
                .onSuccess { preview = it }
                .onFailure { throwable ->
                    errorText = throwable.message?.trim().orEmpty().ifBlank { previewFailedText }
                }
            loadingPreview = false
        }
    }

    SettingsPage(title = repo, onBack = { navController.navigateUp() }) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ZiCodeSectionTitle(title = stringResource(R.string.zicode_files))
            }
            item {
                ZiCodePanel {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ZiCodeMetaText(text = "$owner / $repo")
                        Text(text = if (currentPath.isBlank()) stringResource(R.string.zicode_repo_root) else currentPath, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = SourceSans3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { currentPath = "" }) { Text(stringResource(R.string.zicode_root), color = TextPrimary) }
                            if (currentPath.isNotBlank()) {
                                TextButton(onClick = ::openParent) { Text(stringResource(R.string.zicode_back_parent), color = TextPrimary) }
                            }
                        }
                    }
                }
            }

            when {
                loading -> item { ZiCodeLoadingPanel(text = stringResource(R.string.zicode_loading_files)) }
                errorText != null -> item {
                    ZiCodeEmptyPanel(
                        title = stringResource(R.string.zicode_files_error_title),
                        body = errorText.orEmpty(),
                        actionLabel = stringResource(R.string.zicode_retry),
                        onAction = { refreshNonce += 1 }
                    )
                }
                entries.isEmpty() -> item {
                    ZiCodeEmptyPanel(
                        title = stringResource(R.string.zicode_files_empty_title),
                        body = stringResource(R.string.zicode_files_empty_body),
                        actionLabel = stringResource(R.string.zicode_root),
                        onAction = { currentPath = "" }
                    )
                }
                else -> items(entries, key = { it.path }) { node ->
                    ZiCodeFileNodeRow(node = node, onClick = {
                        if (node.type == "dir") currentPath = node.path else openFile(node)
                    })
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (preview != null) {
            AppModalBottomSheet(onDismissRequest = { preview = null }, sheetState = previewSheetState) {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = preview?.path.orEmpty(), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SourceSans3)
                    ZiCodeMetaText(text = buildString {
                        append(stringResource(R.string.zicode_file_size, preview?.size ?: 0L))
                        if (preview?.truncated == true) append(" · ${stringResource(R.string.zicode_preview_truncated)}")
                    })
                    Text(text = preview?.content.orEmpty(), color = TextPrimary, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = SourceSans3)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (loadingPreview) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    ZiCodePanel(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZiCodeMiniStatusBadge(text = stringResource(if (node.type == "dir") R.string.zicode_dir else R.string.zicode_file))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = node.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
                ZiCodeMetaText(text = node.path)
            }
            if (node.type == "file") {
                Icon(painter = rememberResourceDrawablePainter(R.drawable.ic_files), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
            } else {
                Text(text = ">", color = ZiCodeSecondaryText, fontSize = 18.sp, fontFamily = SourceSans3)
            }
        }
    }
}
