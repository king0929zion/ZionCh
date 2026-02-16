package com.zionchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalWebHostingService
import com.zionchat.app.data.WebHostingConfig
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun WebHostingScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val hostingService = LocalWebHostingService.current
    val scope = rememberCoroutineScope()

    var token by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf("") }
    var teamId by rememberSaveable { mutableStateOf("") }
    var customDomain by rememberSaveable { mutableStateOf("") }
    var autoDeploy by rememberSaveable { mutableStateOf(true) }
    var versionModel by rememberSaveable { mutableStateOf(1) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }

    fun buildConfig(): WebHostingConfig {
        return WebHostingConfig(
            provider = "vercel",
            token = token.trim(),
            projectId = projectId.trim(),
            teamId = teamId.trim(),
            customDomain = customDomain.trim(),
            autoDeploy = autoDeploy
        )
    }

    val latestConfig by rememberUpdatedState(newValue = buildConfig())
    val latestVersionModel by rememberUpdatedState(newValue = versionModel.coerceAtLeast(1))

    LaunchedEffect(Unit) {
        val persistedConfig = repository.getWebHostingConfig()
        val persistedVersionModel = repository.appModuleVersionModelFlow.first().coerceAtLeast(1)
        token = persistedConfig.token
        projectId = persistedConfig.projectId
        teamId = persistedConfig.teamId
        customDomain = persistedConfig.customDomain
        autoDeploy = persistedConfig.autoDeploy
        versionModel = persistedVersionModel
        initialized = true
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!initialized) return@onDispose
            scope.launch {
                repository.setWebHostingConfig(latestConfig)
                repository.setAppModuleVersionModel(latestVersionModel)
            }
        }
    }

    LaunchedEffect(initialized, token, projectId, teamId, customDomain, autoDeploy, versionModel) {
        if (!initialized) return@LaunchedEffect
        delay(350)
        repository.setWebHostingConfig(buildConfig())
        repository.setAppModuleVersionModel(versionModel)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = "Web hosting",
            onBack = { navController.navigateUp() },
            trailing = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .height(40.dp)
                        .background(Surface, RoundedCornerShape(20.dp))
                        .pressableScale(pressedScale = 0.96f) {
                            scope.launch {
                                repository.setWebHostingConfig(buildConfig())
                                repository.setAppModuleVersionModel(versionModel)
                                statusText = "Saved"
                                navController.navigateUp()
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save",
                        color = TextPrimary,
                        fontFamily = SourceSans3
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FormField(
                label = "Vercel token",
                value = token,
                onValueChange = { token = it },
                placeholder = "vercel_access_token"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto deploy after app generation",
                        color = TextPrimary,
                        fontFamily = SourceSans3
                    )
                    Text(
                        text = "Enable Vercel deployment for create/edit workflows",
                        color = TextSecondary,
                        fontFamily = SourceSans3
                    )
                }
                Switch(
                    checked = autoDeploy,
                    onCheckedChange = { autoDeploy = it },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Black,
                            checkedBorderColor = Color.Black,
                            uncheckedThumbColor = Color.Black,
                            uncheckedTrackColor = Color.White,
                            uncheckedBorderColor = Color.Black
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(20.dp))
                    .pressableScale(pressedScale = 0.98f) { showAdvanced = !showAdvanced }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced settings",
                    color = TextPrimary,
                    fontFamily = SourceSans3,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (showAdvanced) "Hide" else "Show",
                    color = TextSecondary,
                    fontFamily = SourceSans3
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FormField(
                        label = "Project ID or Name",
                        value = projectId,
                        onValueChange = { projectId = it },
                        placeholder = "zionchat-project"
                    )
                    FormField(
                        label = "Team ID (optional)",
                        value = teamId,
                        onValueChange = { teamId = it },
                        placeholder = "team_xxx"
                    )
                    FormField(
                        label = "Custom domain (optional)",
                        value = customDomain,
                        onValueChange = { customDomain = it },
                        placeholder = "apps.example.com"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Version model",
                                color = TextPrimary,
                                fontFamily = SourceSans3
                            )
                            Text(
                                text = "Schema version for future module expansion",
                                color = TextSecondary,
                                fontFamily = SourceSans3
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .background(Background, RoundedCornerShape(10.dp))
                                    .pressableScale(pressedScale = 0.96f) {
                                        versionModel = (versionModel - 1).coerceAtLeast(1)
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "-", color = TextPrimary)
                            }
                            Text(
                                text = "v$versionModel",
                                color = TextPrimary,
                                fontFamily = SourceSans3
                            )
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .background(Background, RoundedCornerShape(10.dp))
                                    .pressableScale(pressedScale = 0.96f) {
                                        versionModel = (versionModel + 1).coerceAtMost(99)
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "+", color = TextPrimary)
                            }
                        }
                    }

                    Text(
                        text = "SavedApp versioning is active with history and rollback.",
                        color = TextSecondary,
                        fontFamily = SourceSans3
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(Surface, RoundedCornerShape(16.dp))
                        .pressableScale(pressedScale = 0.98f) {
                            if (isValidating) return@pressableScale
                            isValidating = true
                            statusText = null
                            scope.launch {
                                val result = hostingService.validateConfig(buildConfig())
                                statusText =
                                    result.fold(
                                        onSuccess = { "Connected" },
                                        onFailure = { throwable ->
                                            throwable.message?.takeIf { it.isNotBlank() } ?: "Validation failed"
                                        }
                                    )
                                isValidating = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = TextPrimary
                        )
                    } else {
                        Text(
                            text = "Validate token",
                            color = TextPrimary,
                            fontFamily = SourceSans3
                        )
                    }
                }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(TextPrimary, RoundedCornerShape(16.dp))
                        .pressableScale(pressedScale = 0.98f) {
                            scope.launch {
                                repository.setWebHostingConfig(buildConfig())
                                repository.setAppModuleVersionModel(versionModel)
                                statusText = "Saved"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save config",
                        color = Color.White,
                        fontFamily = SourceSans3
                    )
                }
            }

            statusText?.let { text ->
                Text(
                    text = text,
                    color = TextSecondary,
                    fontFamily = SourceSans3,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
