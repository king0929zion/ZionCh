package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.LocalZiCodeRepository
import com.zionchat.app.R
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.zicode.data.ZiCodeSettings
import com.zionchat.app.zicode.data.buildZiCodeToolCapabilities
import kotlinx.coroutines.launch

@Composable
fun ZiCodeSettingsScreen(navController: NavController) {
    val repository = LocalZiCodeRepository.current
    val gitHubService = LocalZiCodeGitHubService.current
    val appRepository = LocalAppRepository.current
    val settings by repository.settingsFlow.collectAsState(initial = ZiCodeSettings())
    val models by appRepository.modelsFlow.collectAsState(initial = emptyList())
    val ziCodeModelId by appRepository.defaultZiCodeModelIdFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var tokenText by rememberSaveable(settings.githubToken) { mutableStateOf(settings.githubToken) }
    var validating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val tokenSavedText = stringResource(R.string.zicode_token_saved)
    val enterTokenText = stringResource(R.string.zicode_enter_token)
    val tokenValidationFailedText = stringResource(R.string.zicode_token_validation_failed)

    val modelName =
        remember(models, ziCodeModelId) {
            val key = ziCodeModelId?.trim().orEmpty()
            if (key.isBlank()) null else models.firstOrNull { it.id == key }?.displayName ?: models.firstOrNull { extractRemoteModelId(it.id) == key }?.displayName
        }
    val groupedCapabilities = remember { buildZiCodeToolCapabilities().groupBy { it.group } }

    SettingsPage(title = stringResource(R.string.zicode_settings_title), onBack = { navController.navigateUp() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ZiCodeSectionTitle(title = stringResource(R.string.zicode_github_section))
            ZiCodePanel {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.zicode_token_label)) },
                        placeholder = { Text("ghp_xxx / github_pat_xxx") },
                        singleLine = true,
                        colors = ziCodeFieldColors()
                    )
                    settings.viewer?.let { viewer ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = AppIcons.GitHub, contentDescription = null, tint = TextPrimary)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = viewer.displayName ?: viewer.login, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
                                ZiCodeMetaText(text = "@${viewer.login}")
                            }
                        }
                    }
                    statusText?.takeIf { it.isNotBlank() }?.let { ZiCodeMetaText(text = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                repository.setGitHubToken(tokenText)
                                statusText = tokenSavedText
                            }
                        }) { Text(stringResource(R.string.common_save), color = TextPrimary) }
                        TextButton(enabled = !validating, onClick = {
                            scope.launch {
                                val token = tokenText.trim()
                                if (token.isBlank()) {
                                    statusText = enterTokenText
                                    return@launch
                                }
                                validating = true
                                gitHubService.fetchViewer(token)
                                    .onSuccess { viewer ->
                                        repository.setGitHubToken(token)
                                        repository.updateViewer(viewer)
                                        statusText = "${stringResource(R.string.zicode_connection_ready)} ${viewer.login}"
                                    }
                                    .onFailure { throwable ->
                                        statusText = throwable.message?.trim().orEmpty().ifBlank { tokenValidationFailedText }
                                    }
                                validating = false
                            }
                        }) { Text(if (validating) stringResource(R.string.zicode_validating) else stringResource(R.string.zicode_validate_connection), color = TextPrimary) }
                    }
                }
            }

            ZiCodeSectionTitle(title = stringResource(R.string.zicode_model_section))
            ZiCodePanel {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ZiCodeSettingRow(
                        title = stringResource(R.string.zicode_default_model_title),
                        subtitle = modelName ?: stringResource(R.string.zicode_default_model_missing),
                        trailing = stringResource(R.string.zicode_open_default_model),
                        onClick = { navController.navigate("default_model") }
                    )
                    HorizontalDivider(color = ZiCodeDividerColor)
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = stringResource(R.string.zicode_model_note_title), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
                        ZiCodeMetaText(text = stringResource(R.string.zicode_model_note_body))
                    }
                }
            }

            ZiCodeSectionTitle(title = stringResource(R.string.zicode_tools_section))
            groupedCapabilities.forEach { (group, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZiCodeSectionTitle(title = group)
                    ZiCodePanel {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            items.forEachIndexed { index, capability ->
                                if (index > 0) HorizontalDivider(color = ZiCodeDividerColor)
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = capability.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
                                    ZiCodeMetaText(text = capability.description)
                                }
                            }
                        }
                    }
                }
            }

            ZiCodeSectionTitle(title = stringResource(R.string.zicode_about_section))
            ZiCodePanel {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = stringResource(R.string.zicode_about_title), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
                    ZiCodeMetaText(text = stringResource(R.string.zicode_about_body))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ZiCodeSettingRow(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZiCodePanelGray)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = SourceSans3)
            ZiCodeMetaText(text = subtitle)
        }
        TextButton(onClick = onClick) {
            Text(trailing, color = TextPrimary)
        }
    }
}
