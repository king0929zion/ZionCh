package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.HttpHeader
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.isCodexProvider
import com.zionchat.app.data.isGrok2ApiProvider
import com.zionchat.app.data.isLikelyVisionModel
import com.zionchat.app.data.normalizeInputModality
import com.zionchat.app.ui.components.BottomFadeScrim
import com.zionchat.app.ui.components.EditableHeader
import com.zionchat.app.ui.components.HeadersEditorCard
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.GrayLighter
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ModelConfigScreen(
    navController: NavController,
    modelId: String? = null
) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()

    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val existingModel = remember(models, modelId) { models.firstOrNull { it.id == modelId } }
    val provider = remember(existingModel?.providerId, providers) {
        val pid = existingModel?.providerId?.trim().orEmpty()
        if (pid.isBlank()) null else providers.firstOrNull { it.id == pid }
    }
    val supportsThinkingDepth = remember(provider?.id, provider?.apiUrl, provider?.type, provider?.presetId) {
        provider?.isCodexProvider() == true || provider?.isGrok2ApiProvider() == true
    }

    var modelName by remember(existingModel?.id) { mutableStateOf(existingModel?.displayName.orEmpty()) }
    var selectedModality by remember(existingModel?.id) {
        mutableStateOf(
            when (normalizeInputModality(existingModel?.inputModality)) {
                "text" -> "text"
                "text-image" -> "text-image"
                else -> if (existingModel?.let { isLikelyVisionModel(it) } == true) "text-image" else "text"
            }
        )
    }
    var reasoningEffort by remember(existingModel?.id) {
        mutableStateOf(existingModel?.reasoningEffort?.trim()?.lowercase()?.takeIf { it.isNotBlank() })
    }
    val headers = remember(existingModel?.id) {
        mutableStateListOf<EditableHeader>().apply {
            existingModel?.headers?.forEach { add(EditableHeader(it.key, it.value)) }
        }
    }

    fun save() {
        val id = existingModel?.id ?: modelId ?: return
        val fallbackName =
            existingModel?.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: com.zionchat.app.data.extractRemoteModelId(id)

        val finalDisplayName = modelName.trim().ifBlank { fallbackName }
        scope.launch {
            repository.upsertModel(
                ModelConfig(
                    id = id,
                    displayName = finalDisplayName,
                    enabled = existingModel?.enabled ?: true,
                    providerId = existingModel?.providerId,
                    headers = headers
                        .filter { it.key.isNotBlank() }
                        .map { HttpHeader(it.key.trim(), it.value.trim()) },
                    reasoningEffort = reasoningEffort.takeIf { supportsThinkingDepth },
                    inputModality = if (selectedModality == "text") "text" else "text-image"
                )
            )
            navController.popBackStack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        PageTopBar(
            title = "Model configuration",
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface, CircleShape)
                        .pressableScale(pressedScale = 0.95f, onClick = ::save),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = "Save",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Model Name",
                    fontSize = 13.sp,
                    fontFamily = SourceSans3,
                    color = TextSecondary
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Surface,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    TextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        placeholder = {
                            Text(
                                text = "Enter display name",
                                fontSize = 17.sp,
                                color = TextSecondary
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            color = TextPrimary
                        ),
                        singleLine = true
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Input Modality",
                    fontSize = 13.sp,
                    fontFamily = SourceSans3,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GrayLighter, RoundedCornerShape(20.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModalityOption(
                        iconVector = AppIcons.TextOnly,
                        text = "Text Only",
                        selected = selectedModality == "text",
                        onClick = { selectedModality = "text" },
                        modifier = Modifier.weight(1f)
                    )
                    ModalityOption(
                        iconVector = AppIcons.TextImage,
                        text = "Text & Image",
                        selected = selectedModality == "text-image",
                        onClick = { selectedModality = "text-image" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (supportsThinkingDepth) {
                    Text(
                        text = "Thinking Depth",
                        fontSize = 13.sp,
                        fontFamily = SourceSans3,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GrayLighter, RoundedCornerShape(20.dp))
                            .padding(6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ReasoningOptionChip(
                            text = "Auto",
                            selected = reasoningEffort == null,
                            onClick = { reasoningEffort = null }
                        )
                        ReasoningOptionChip(
                            text = "Minimal",
                            selected = reasoningEffort == "minimal",
                            onClick = { reasoningEffort = "minimal" }
                        )
                        ReasoningOptionChip(
                            text = "Low",
                            selected = reasoningEffort == "low",
                            onClick = { reasoningEffort = "low" }
                        )
                        ReasoningOptionChip(
                            text = "Medium",
                            selected = reasoningEffort == "medium",
                            onClick = { reasoningEffort = "medium" }
                        )
                        ReasoningOptionChip(
                            text = "High",
                            selected = reasoningEffort == "high",
                            onClick = { reasoningEffort = "high" }
                        )
                        ReasoningOptionChip(
                            text = "XHigh",
                            selected = reasoningEffort == "xhigh",
                            onClick = { reasoningEffort = "xhigh" }
                        )
                    }
                }
            }

            HeadersEditorCard(headers = headers)

            Spacer(modifier = Modifier.height(32.dp))
            }

            BottomFadeScrim(
                color = Background,
                height = 44.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun ModalityOption(
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TextPrimary else Color.Transparent, RoundedCornerShape(16.dp))
            .pressableScale(pressedScale = 0.95f, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = if (selected) Surface else TextPrimary,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 4.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SourceSans3,
            color = if (selected) Surface else TextPrimary
        )
    }
}

@Composable
private fun ReasoningOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TextPrimary else Color.Transparent, RoundedCornerShape(16.dp))
            .pressableScale(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SourceSans3,
            color = if (selected) Surface else TextPrimary
        )
    }
}
