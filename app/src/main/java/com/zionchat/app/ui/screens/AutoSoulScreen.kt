package com.zionchat.app.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.data.isLikelyVisionModel
import com.zionchat.app.autosoul.AutoSoulPermissions
import com.zionchat.app.autosoul.AutoSoulShizukuBridge
import com.zionchat.app.autosoul.overlay.AutoSoulFloatingOverlay
import com.zionchat.app.autosoul.runtime.AutoSoulAutomationManager
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.GrayLighter
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 4102

private const val DEFAULT_AUTOSOUL_SCRIPT = """
[
  { "action": "Launch", "app": "抖音" },
  { "action": "Wait", "duration": "1.2" },
  { "action": "Swipe", "start": "[0.50,0.78]", "end": "[0.50,0.22]", "duration_ms": "420" },
  { "action": "Wait", "duration": "0.8" },
  { "action": "Back" }
]
"""

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AutoSoulScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val autoSoulModelId by repository.defaultAutoSoulModelIdFlow.collectAsState(initial = null)
    val logs by AutoSoulAutomationManager.logs.collectAsState()
    val scope = rememberCoroutineScope()
    var permissionSnapshot by remember { mutableStateOf(AutoSoulPermissions.snapshot(context)) }
    var script by rememberSaveable { mutableStateOf(DEFAULT_AUTOSOUL_SCRIPT.trimIndent()) }
    var showModelPicker by remember { mutableStateOf(false) }
    val openedSettingsText = stringResource(R.string.autosoul_opened_settings)
    val shizukuOpenHintText = stringResource(R.string.autosoul_shizuku_open_hint)
    val needOverlayPermissionText = stringResource(R.string.autosoul_need_overlay_permission)
    val copiedLogsText = stringResource(R.string.autosoul_logs_copied)

    val visionModels = remember(models) { models.filter { it.enabled && isLikelyVisionModel(it) } }
    val selectedAutoSoulModelName =
        remember(visionModels, autoSoulModelId) {
            val key = autoSoulModelId?.trim().orEmpty()
            if (key.isBlank()) null
            else visionModels.firstOrNull { it.id == key }?.displayName
                ?: visionModels.firstOrNull { extractRemoteModelId(it.id) == key }?.displayName
        }
    val displayLogs = remember(logs) { logs.takeLast(60) }
    val logsText = remember(displayLogs) { displayLogs.joinToString(separator = "\n") }

    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionSnapshot = AutoSoulPermissions.snapshot(context)
        }

    fun refreshPermissions() {
        permissionSnapshot = AutoSoulPermissions.snapshot(context)
    }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        AutoSoulAutomationManager.bindOverlayActions(context.applicationContext)
        refreshPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val binderListener = Shizuku.OnBinderReceivedListener { refreshPermissions() }
        val permissionListener =
            Shizuku.OnRequestPermissionResultListener { _, _ ->
                refreshPermissions()
            }
        runCatching {
            Shizuku.addBinderReceivedListener(binderListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
        onDispose {
            runCatching {
                Shizuku.removeBinderReceivedListener(binderListener)
                Shizuku.removeRequestPermissionResultListener(permissionListener)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = stringResource(R.string.settings_item_autosoul),
            onBack = { navController.navigateUp() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AutoSoulSectionTitle(stringResource(R.string.autosoul_section_model))
            AutoSoulCard {
                Text(
                    text = stringResource(R.string.autosoul_model_desc),
                    color = TextSecondary,
                    fontFamily = SourceSans3,
                    fontSize = 13.sp
                )
                AutoSoulModelPickerRow(
                    modelName = selectedAutoSoulModelName,
                    onClick = { showModelPicker = true }
                )
            }

            AutoSoulSectionTitle(stringResource(R.string.autosoul_section_permissions))
            AutoSoulCard {
                AutoSoulPermissionRow(
                    title = stringResource(R.string.autosoul_permission_accessibility),
                    subtitle = stringResource(R.string.autosoul_permission_accessibility_desc),
                    granted = permissionSnapshot.accessibilityEnabled,
                    actionLabel = stringResource(R.string.autosoul_action_open_settings),
                    onClick = {
                        AutoSoulPermissions.openAccessibilitySettings(context)
                        showToast(openedSettingsText)
                    }
                )
                Divider(color = GrayLight)
                AutoSoulPermissionRow(
                    title = stringResource(R.string.autosoul_permission_shizuku),
                    subtitle =
                        if (permissionSnapshot.shizukuRunning) {
                            stringResource(R.string.autosoul_permission_shizuku_desc)
                        } else {
                            stringResource(R.string.autosoul_permission_shizuku_not_running)
                        },
                    granted = permissionSnapshot.shizukuGranted,
                    actionLabel = stringResource(R.string.autosoul_action_authorize),
                    onClick = {
                        if (!permissionSnapshot.shizukuRunning) {
                            AutoSoulShizukuBridge.openShizukuAppOrWebsite(context)
                            showToast(shizukuOpenHintText)
                        } else {
                            AutoSoulShizukuBridge.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                        }
                    }
                )
                Divider(color = GrayLight)
                AutoSoulPermissionRow(
                    title = stringResource(R.string.autosoul_permission_overlay),
                    subtitle = stringResource(R.string.autosoul_permission_overlay_desc),
                    granted = permissionSnapshot.overlayEnabled,
                    actionLabel = stringResource(R.string.autosoul_action_open_settings),
                    onClick = {
                        AutoSoulPermissions.openOverlaySettings(context)
                        showToast(openedSettingsText)
                    }
                )
                Divider(color = GrayLight)
                AutoSoulPermissionRow(
                    title = stringResource(R.string.autosoul_permission_notification),
                    subtitle = stringResource(R.string.autosoul_permission_notification_desc),
                    granted = permissionSnapshot.notificationGranted,
                    actionLabel = stringResource(R.string.autosoul_action_optional),
                    optional = true,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            AutoSoulPermissions.openNotificationSettings(context)
                        }
                    }
                )
            }

            AutoSoulSectionTitle(stringResource(R.string.autosoul_section_overlay))
            AutoSoulCard {
                Text(
                    text = stringResource(R.string.autosoul_overlay_desc),
                    color = TextSecondary,
                    fontFamily = SourceSans3,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSoulActionButton(
                        text = stringResource(R.string.autosoul_overlay_show),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!permissionSnapshot.overlayEnabled) {
                                showToast(needOverlayPermissionText)
                                return@AutoSoulActionButton
                            }
                            AutoSoulFloatingOverlay.show(context.applicationContext)
                            AutoSoulAutomationManager.bindOverlayActions(context.applicationContext)
                        }
                    )
                    AutoSoulActionButton(
                        text = stringResource(R.string.autosoul_overlay_hide),
                        modifier = Modifier.weight(1f),
                        onClick = { AutoSoulFloatingOverlay.hide() },
                        primary = false
                    )
                }
                AutoSoulActionButton(
                    text = stringResource(R.string.autosoul_overlay_capture_test),
                    onClick = {
                        scope.launch {
                            val capture = withContext(Dispatchers.IO) { AutoSoulShizukuBridge.captureScreenshot(context) }
                            showToast(
                                if (capture.isSuccess) {
                                    context.getString(R.string.autosoul_capture_success, capture.getOrNull()?.absolutePath.orEmpty())
                                } else {
                                    context.getString(
                                        R.string.autosoul_capture_failed,
                                        capture.exceptionOrNull()?.message ?: "unknown"
                                    )
                                }
                            )
                        }
                    },
                    primary = false
                )
            }

            AutoSoulSectionTitle(stringResource(R.string.autosoul_section_script))
            AutoSoulCard {
                Text(
                    text = stringResource(R.string.autosoul_script_desc),
                    color = TextSecondary,
                    fontFamily = SourceSans3,
                    fontSize = 13.sp
                )
                TextField(
                    value = script,
                    onValueChange = { script = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = GrayLighter,
                            unfocusedContainerColor = GrayLighter,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                    textStyle =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TextPrimary,
                            fontFamily = SourceSans3
                        )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSoulActionButton(
                        text = stringResource(R.string.autosoul_run_script),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val result = AutoSoulAutomationManager.start(context.applicationContext, script)
                            if (result.isFailure) {
                                showToast(result.exceptionOrNull()?.message ?: "start failed")
                            }
                        }
                    )
                    AutoSoulActionButton(
                        text = stringResource(R.string.autosoul_stop_script),
                        modifier = Modifier.weight(1f),
                        onClick = { AutoSoulAutomationManager.stop() },
                        primary = false
                    )
                }
                AutoSoulActionButton(
                    text = stringResource(R.string.autosoul_fill_example),
                    onClick = { script = DEFAULT_AUTOSOUL_SCRIPT.trimIndent() },
                    primary = false
                )
            }

            AutoSoulSectionTitle(stringResource(R.string.autosoul_section_logs))
            AutoSoulCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.autosoul_section_logs),
                        color = TextPrimary,
                        fontFamily = SourceSans3,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier =
                            Modifier
                                .height(30.dp)
                                .background(GrayLighter, RoundedCornerShape(12.dp))
                                .pressableScale(
                                    pressedScale = 0.96f,
                                    onClick = {
                                        if (logsText.isNotBlank()) {
                                            clipboardManager.setText(AnnotatedString(logsText))
                                            showToast(copiedLogsText)
                                        }
                                    }
                                )
                                .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.autosoul_logs_copy),
                            color = TextPrimary,
                            fontFamily = SourceSans3,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Divider(color = GrayLight)
                if (logs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.autosoul_logs_empty),
                        color = TextSecondary,
                        fontFamily = SourceSans3,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = logsText,
                        color = TextPrimary,
                        fontFamily = SourceSans3,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }

    if (showModelPicker) {
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            containerColor = Surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AutoSoulVisionModelPickerContent(
                providers = providers,
                visionModels = visionModels,
                selectedModelId = autoSoulModelId,
                onSelect = { selectedId ->
                    scope.launch { repository.setDefaultAutoSoulModelId(selectedId) }
                    showModelPicker = false
                },
                onClear = {
                    scope.launch { repository.setDefaultAutoSoulModelId(null) }
                    showModelPicker = false
                }
            )
        }
    }
}

@Composable
private fun AutoSoulSectionTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontFamily = SourceSans3,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun AutoSoulCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun AutoSoulPermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    optional: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = SourceSans3,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontFamily = SourceSans3,
                fontSize = 12.sp
            )
        }
        Box(
            modifier =
                Modifier
                    .height(28.dp)
                    .background(
                        if (granted) TextPrimary else GrayLighter,
                        RoundedCornerShape(14.dp)
                    )
                    .pressableScale(pressedScale = 0.96f, onClick = onClick)
                    .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text =
                    if (granted) {
                        stringResource(R.string.autosoul_status_enabled)
                    } else {
                        if (optional) actionLabel else stringResource(R.string.autosoul_status_enable)
                    },
                color = if (granted) Color.White else TextPrimary,
                fontFamily = SourceSans3,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AutoSoulActionButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(
                    if (primary) TextPrimary else GrayLighter,
                    RoundedCornerShape(12.dp)
                )
                .pressableScale(pressedScale = 0.96f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else TextPrimary,
            fontFamily = SourceSans3,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AutoSoulModelPickerRow(
    modelName: String?,
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GrayLighter, RoundedCornerShape(12.dp))
                .pressableScale(pressedScale = 0.97f, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = modelName ?: stringResource(R.string.autosoul_model_not_set),
            color = if (modelName.isNullOrBlank()) TextSecondary else TextPrimary,
            fontFamily = SourceSans3,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.autosoul_action_choose_model),
            color = TextPrimary,
            fontFamily = SourceSans3,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AutoSoulVisionModelPickerContent(
    providers: List<ProviderConfig>,
    visionModels: List<ModelConfig>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    val grouped = remember(providers, visionModels) { groupAutoSoulModelsByProvider(providers, visionModels) }
    val selectedKey = selectedModelId?.trim().orEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 620.dp)
    ) {
        Text(
            text = stringResource(R.string.autosoul_model_picker_title),
            color = TextPrimary,
            fontFamily = SourceSans3,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.autosoul_model_only_vision_hint),
            color = TextSecondary,
            fontFamily = SourceSans3,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = GrayLighter)
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pressableScale(pressedScale = 0.97f, onClick = onClear)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.autosoul_model_not_set),
                        color = TextPrimary,
                        fontFamily = SourceSans3,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (selectedKey.isBlank()) "✓" else "",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (grouped.isEmpty()) {
                Text(
                    text = stringResource(R.string.autosoul_model_empty_vision),
                    color = TextSecondary,
                    fontFamily = SourceSans3,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            } else {
                grouped.forEach { (providerName, providerModels) ->
                    Column {
                        Text(
                            text = providerName.uppercase(),
                            color = TextSecondary,
                            fontFamily = SourceSans3,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = GrayLighter)
                        ) {
                            providerModels.forEachIndexed { index, model ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .pressableScale(pressedScale = 0.97f, onClick = { onSelect(model.id) })
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model.displayName,
                                        color = TextPrimary,
                                        fontFamily = SourceSans3,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val selected =
                                        selectedKey.isNotBlank() &&
                                            (model.id == selectedKey || extractRemoteModelId(model.id) == selectedKey)
                                    Text(
                                        text = if (selected) "✓" else "",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (index != providerModels.lastIndex) {
                                    Divider(color = GrayLight)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun groupAutoSoulModelsByProvider(
    providers: List<ProviderConfig>,
    models: List<ModelConfig>
): List<Pair<String, List<ModelConfig>>> {
    val providerById = providers.associateBy { it.id }
    val grouped = linkedMapOf<String, MutableList<ModelConfig>>()

    providers.forEach { provider ->
        val providerModels =
            models
                .filter { it.providerId == provider.id }
                .sortedBy { it.displayName.lowercase() }
        if (providerModels.isNotEmpty()) {
            grouped[provider.name] = providerModels.toMutableList()
        }
    }

    val others =
        models
            .filter { it.providerId.isNullOrBlank() || providerById[it.providerId] == null }
            .sortedBy { it.displayName.lowercase() }
    if (others.isNotEmpty()) {
        grouped["Other"] = others.toMutableList()
    }

    return grouped.map { it.key to it.value.toList() }
}
