package com.zionchat.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.material3.Surface as M3Surface
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.LocalOAuthClient
import com.zionchat.app.R
import com.zionchat.app.data.OAuthClient
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.findProviderPreset
import com.zionchat.app.ui.components.AssetIcon
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.UUID

private const val GITHUB_COPILOT_PRESET_ID = "github_copilot"
private const val GITHUB_COPILOT_NAME = "GitHub Copilot"
private const val GITHUB_COPILOT_ICON = "github.svg"
private const val GITHUB_COPILOT_API_URL = "https://api.githubcopilot.com"
private val DeviceSupplierCardGray = Color(0xFFF1F1F1)

private enum class DeviceStep {
    STEP_1_CONNECT,
    STEP_2_CODE,
    STEP_3_COMPLETED
}

@Composable
fun AddDeviceProviderScreen(
    navController: NavController,
    initialProvider: String? = null,
    providerId: String? = null
) {
    val repository = LocalAppRepository.current
    val oauthClient = LocalOAuthClient.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lockedProvider = remember(initialProvider) {
        initialProvider?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: GITHUB_COPILOT_PRESET_ID
    }
    val existingProviderId = remember(providerId) { providerId?.trim()?.takeIf { it.isNotBlank() } }
    val providers by repository.providersFlow.collectAsState(initial = emptyList())

    val matchedBuiltInProvider = remember(providers, existingProviderId, lockedProvider) {
        if (!existingProviderId.isNullOrBlank()) {
            null
        } else {
            providers.firstOrNull { provider ->
                provider.presetId?.trim()?.equals(lockedProvider, ignoreCase = true) == true ||
                    provider.deviceProvider?.trim()?.equals(lockedProvider, ignoreCase = true) == true
            }
        }
    }
    val existingProvider = remember(providers, existingProviderId, matchedBuiltInProvider?.id) {
        existingProviderId?.let { id -> providers.firstOrNull { it.id == id } } ?: matchedBuiltInProvider
    }

    var currentStep by remember { mutableStateOf(DeviceStep.STEP_1_CONNECT) }
    var deviceStart by remember { mutableStateOf<OAuthClient.GitHubDeviceCodeStart?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var connectedProvider by remember { mutableStateOf<ProviderConfig?>(null) }

    val preset = remember(lockedProvider) { findProviderPreset(lockedProvider) }
    val iconAsset = remember(preset?.iconAsset) { preset?.iconAsset ?: GITHUB_COPILOT_ICON }
    val activeProvider = connectedProvider ?: existingProvider
    val activeProviderId = activeProvider?.id?.trim()?.takeIf { it.isNotBlank() }
    val canManageModels = !activeProviderId.isNullOrBlank()

    LaunchedEffect(existingProvider?.id) {
        val providerConfig = existingProvider ?: return@LaunchedEffect
        if (!providerConfig.deviceProvider.isNullOrBlank() && providerConfig.apiKey.isNotBlank()) {
            connectedProvider = providerConfig
            currentStep = DeviceStep.STEP_3_COMPLETED
        }
    }

    SettingsPage(
        title = stringResource(R.string.add_provider),
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            M3Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DeviceSupplierCardGray,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F1F1), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AssetIcon(
                            assetFileName = iconAsset,
                            contentDescription = preset?.name ?: GITHUB_COPILOT_NAME,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            error = {
                                Icon(
                                    imageVector = AppIcons.ChatGPTLogo,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset?.name ?: GITHUB_COPILOT_NAME,
                            fontSize = 17.sp,
                            fontFamily = SourceSans3,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.add_device_step1_tip),
                            fontSize = 13.sp,
                            fontFamily = SourceSans3,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (!errorText.isNullOrBlank()) {
                M3Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GrayLight,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = errorText.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        color = TextPrimary
                    )
                }
            }

            when (currentStep) {
                DeviceStep.STEP_1_CONNECT -> {
                    Button(
                        enabled = !isWorking,
                        onClick = {
                            isWorking = true
                            errorText = null
                            scope.launch {
                                oauthClient.startGitHubCopilotDeviceCode()
                                    .onSuccess { start ->
                                        deviceStart = start
                                        currentStep = DeviceStep.STEP_2_CODE
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(start.verificationUri))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }.onFailure { throwable ->
                                            if (throwable is ActivityNotFoundException) {
                                                errorText = context.getString(R.string.error_open_browser)
                                            } else {
                                                errorText = throwable.message ?: throwable.toString()
                                            }
                                        }
                                    }
                                    .onFailure { throwable ->
                                        errorText = throwable.message ?: throwable.toString()
                                    }
                                isWorking = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = Surface
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.add_device_connect),
                                fontSize = 16.sp,
                                fontFamily = SourceSans3,
                                fontWeight = FontWeight.Medium,
                                color = Surface
                            )
                        }
                    }
                }

                DeviceStep.STEP_2_CODE -> {
                    val start = deviceStart
                    M3Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DeviceSupplierCardGray,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_device_step2_tip),
                                fontSize = 13.sp,
                                fontFamily = SourceSans3,
                                color = TextSecondary
                            )
                            Text(
                                text = stringResource(R.string.add_device_user_code, start?.userCode.orEmpty()),
                                fontSize = 17.sp,
                                fontFamily = SourceSans3,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = start?.verificationUri.orEmpty(),
                                fontSize = 13.sp,
                                fontFamily = SourceSans3,
                                color = TextSecondary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    enabled = !isWorking,
                                    onClick = {
                                        val uri = start?.verificationUri ?: return@Button
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GrayLight)
                                ) {
                                    Text(
                                        text = stringResource(R.string.add_device_open_in_browser),
                                        fontSize = 14.sp,
                                        fontFamily = SourceSans3,
                                        color = TextPrimary
                                    )
                                }
                                Button(
                                    enabled = !isWorking,
                                    onClick = {
                                        val startValue = start ?: return@Button
                                        isWorking = true
                                        errorText = null
                                        scope.launch {
                                            oauthClient.exchangeGitHubCopilotDeviceCode(
                                                domain = startValue.domain,
                                                deviceCode = startValue.deviceCode,
                                                pollIntervalSeconds = startValue.pollIntervalSeconds,
                                                expiresInSeconds = startValue.expiresInSeconds
                                            ).onSuccess { token ->
                                                val resolvedProviderId =
                                                    existingProvider?.id?.trim()?.takeIf { it.isNotBlank() }
                                                        ?: UUID.randomUUID().toString()
                                                val providerName =
                                                    existingProvider?.name?.trim()?.takeIf { it.isNotBlank() }
                                                        ?: GITHUB_COPILOT_NAME
                                                val created =
                                                    ProviderConfig(
                                                        id = resolvedProviderId,
                                                        presetId = GITHUB_COPILOT_PRESET_ID,
                                                        iconAsset = GITHUB_COPILOT_ICON,
                                                        name = providerName,
                                                        type = "openai",
                                                        apiUrl = GITHUB_COPILOT_API_URL,
                                                        apiKey = token.accessToken,
                                                        headers = existingProvider?.headers.orEmpty(),
                                                        deviceProvider = GITHUB_COPILOT_PRESET_ID,
                                                        deviceExpiresAtMs = null
                                                    )
                                                repository.upsertProvider(created)
                                                connectedProvider = created
                                                currentStep = DeviceStep.STEP_3_COMPLETED
                                            }.onFailure { throwable ->
                                                errorText = throwable.message ?: throwable.toString()
                                            }
                                            isWorking = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                                ) {
                                    if (isWorking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Surface
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.common_continue),
                                            fontSize = 14.sp,
                                            fontFamily = SourceSans3,
                                            color = Surface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                DeviceStep.STEP_3_COMPLETED -> {
                    M3Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DeviceSupplierCardGray,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val id = activeProviderId ?: return@Button
                                    scope.launch {
                                        repository.deleteProviderAndModels(id)
                                        connectedProvider = null
                                        deviceStart = null
                                        currentStep = DeviceStep.STEP_1_CONNECT
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GrayLight)
                            ) {
                                Text(
                                    text = stringResource(R.string.add_device_disconnect),
                                    fontSize = 14.sp,
                                    fontFamily = SourceSans3,
                                    color = TextPrimary
                                )
                            }

                            Button(
                                onClick = {
                                    connectedProvider = null
                                    deviceStart = null
                                    errorText = null
                                    currentStep = DeviceStep.STEP_1_CONNECT
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GrayLight)
                            ) {
                                Text(
                                    text = stringResource(R.string.add_device_relogin),
                                    fontSize = 14.sp,
                                    fontFamily = SourceSans3,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            M3Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DeviceSupplierCardGray,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canManageModels) {
                                Modifier.pressableScale(pressedScale = 0.985f) {
                                    val id = activeProviderId
                                    navController.navigate("models?providerId=$id")
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.models),
                            fontSize = 13.sp,
                            fontFamily = SourceSans3,
                            color = TextSecondary
                        )
                        Text(
                            text = stringResource(R.string.model_services_configure_models),
                            fontSize = 17.sp,
                            fontFamily = SourceSans3,
                            fontWeight = FontWeight.Medium,
                            color = if (canManageModels) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.alpha(if (canManageModels) 1f else 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

