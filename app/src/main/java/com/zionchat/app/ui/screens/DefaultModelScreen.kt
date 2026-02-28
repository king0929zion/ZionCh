package com.zionchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.launch

private enum class DefaultModelType(
    val title: String,
    val required: Boolean,
    val selectorTitle: String,
    val emptyLabel: String = "Not set"
) {
    CHAT(title = "Chat Model", required = true, selectorTitle = "Select Chat Model", emptyLabel = "Not set"),
    VISION(title = "Vision Model", required = false, selectorTitle = "Select Vision Model"),
    IMAGE(title = "Image Generation", required = false, selectorTitle = "Select Image Generation"),
    TITLE(title = "Title Summary", required = false, selectorTitle = "Select Title Summary"),
    APP_BUILDER(title = "App Development", required = false, selectorTitle = "Select App Development")
}

private val NeutralSelectorCard = Color(0xFFF3F3F3)
private val NeutralSelectorDivider = Color(0xFFE4E4E4)
private val NeutralHandle = Color(0xFFE0E0E0)

@Composable
fun DefaultModelScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()

    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())

    val chatModelId by repository.defaultChatModelIdFlow.collectAsState(initial = null)
    val visionModelId by repository.defaultVisionModelIdFlow.collectAsState(initial = null)
    val imageModelId by repository.defaultImageModelIdFlow.collectAsState(initial = null)
    val titleModelId by repository.defaultTitleModelIdFlow.collectAsState(initial = null)
    val appBuilderModelId by repository.defaultAppBuilderModelIdFlow.collectAsState(initial = null)

    var selectorType by remember { mutableStateOf<DefaultModelType?>(null) }

    val selectedMap = remember(chatModelId, visionModelId, imageModelId, titleModelId, appBuilderModelId) {
        mapOf(
            DefaultModelType.CHAT to chatModelId,
            DefaultModelType.VISION to visionModelId,
            DefaultModelType.IMAGE to imageModelId,
            DefaultModelType.TITLE to titleModelId,
            DefaultModelType.APP_BUILDER to appBuilderModelId
        )
    }

    SettingsPage(
        title = "Default model",
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            DefaultModelType.values().forEachIndexed { index, type ->
                DefaultModelSection(
                    type = type,
                    selectedModelId = selectedMap[type],
                    resolveDisplayName = { id ->
                        val key = id?.trim().orEmpty()
                        if (key.isBlank()) null
                        else models.firstOrNull { it.id == key }?.displayName
                            ?: models.firstOrNull { extractRemoteModelId(it.id) == key }?.displayName
                    },
                    onOpenSelector = { selectorType = type }
                )
                if (index != DefaultModelType.values().lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Configure default models for different tasks. Chat Model is required, others are optional.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 24.dp)
            )
            if (appBuilderModelId.isNullOrBlank()) {
                Text(
                    text = "APP developer requires App Development model. Set it before generating or fixing apps.",
                    fontSize = 13.sp,
                    color = Color(0xFFB45309),
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    DefaultModelSelectorModal(
        visible = selectorType != null,
        title = selectorType?.selectorTitle.orEmpty(),
        required = selectorType?.required == true,
        providers = providers,
        models = models,
        selectedModelId = selectorType?.let { selectedMap[it] },
        onSelect = { modelId ->
            val type = selectorType ?: return@DefaultModelSelectorModal
            scope.launch {
                when (type) {
                    DefaultModelType.CHAT -> repository.setDefaultChatModelId(modelId)
                    DefaultModelType.VISION -> repository.setDefaultVisionModelId(modelId)
                    DefaultModelType.IMAGE -> repository.setDefaultImageModelId(modelId)
                    DefaultModelType.TITLE -> repository.setDefaultTitleModelId(modelId)
                    DefaultModelType.APP_BUILDER -> repository.setDefaultAppBuilderModelId(modelId)
                }
            }
            selectorType = null
        },
        onClear = {
            val type = selectorType ?: return@DefaultModelSelectorModal
            when (type) {
                DefaultModelType.CHAT -> Unit
                DefaultModelType.VISION -> scope.launch { repository.setDefaultVisionModelId(null) }
                DefaultModelType.IMAGE -> scope.launch { repository.setDefaultImageModelId(null) }
                DefaultModelType.TITLE -> scope.launch { repository.setDefaultTitleModelId(null) }
                DefaultModelType.APP_BUILDER -> scope.launch { repository.setDefaultAppBuilderModelId(null) }
            }
            selectorType = null
        },
        onDismiss = { selectorType = null }
    )
}

@Composable
private fun DefaultModelSection(
    type: DefaultModelType,
    selectedModelId: String?,
    resolveDisplayName: (String?) -> String?,
    onOpenSelector: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = type.title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SourceSans3,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        if (type.required) {
            Text(
                text = "*",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF3B30),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenSelector() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val display = resolveDisplayName(selectedModelId)
            val isEmpty = display.isNullOrBlank()
            Text(
                text = if (isEmpty) type.emptyLabel else display.orEmpty(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEmpty) Color(0xFFC7C7CC) else TextPrimary,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DefaultModelSelectorModal(
    visible: Boolean,
    title: String,
    required: Boolean,
    providers: List<ProviderConfig>,
    models: List<ModelConfig>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            M3Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    ),
                color = Surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .heightIn(max = 640.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(NeutralHandle, RoundedCornerShape(2.dp))
                        )
                    }

                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    )

                    val grouped = remember(providers, models) { groupModelsByProvider(providers, models) }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!required) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = NeutralSelectorCard)
                            ) {
                                DefaultModelNoneOptionRow(
                                    selected = selectedModelId.isNullOrBlank(),
                                    onClick = onClear
                                )
                            }
                        }

                        grouped.forEach { (providerName, providerModels) ->
                            Column {
                                Text(
                                    text = providerName.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = NeutralSelectorCard)
                                ) {
                                    providerModels.forEachIndexed { index, model ->
                                        DefaultModelOptionRow(
                                            model = model,
                                            selected = run {
                                                val selectedKey = selectedModelId?.trim().orEmpty()
                                                selectedKey.isNotBlank() &&
                                                    (model.id == selectedKey || extractRemoteModelId(model.id) == selectedKey)
                                            },
                                            onClick = { onSelect(model.id) }
                                        )
                                        if (index != providerModels.lastIndex) {
                                            HorizontalDivider(color = NeutralSelectorDivider)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DefaultModelOptionRow(
    model: ModelConfig,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = model.displayName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        androidx.compose.material3.Icon(
            imageVector = AppIcons.Check,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier
                .size(22.dp)
                .alpha(if (selected) 1f else 0f)
        )
    }
}

@Composable
private fun DefaultModelNoneOptionRow(
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Not set",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        androidx.compose.material3.Icon(
            imageVector = AppIcons.Check,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier
                .size(22.dp)
                .alpha(if (selected) 1f else 0f)
        )
    }
}

private fun groupModelsByProvider(
    providers: List<ProviderConfig>,
    models: List<ModelConfig>
): List<Pair<String, List<ModelConfig>>> {
    val providerNameById = providers.associateBy({ it.id }, { it.name })
    return models
        .filter { it.enabled }
        .groupBy { model ->
            model.providerId?.let { providerNameById[it] } ?: "Other"
        }
        .toList()
        .sortedBy { it.first }
        .map { (name, list) -> name to list.sortedBy { it.displayName.lowercase() } }
}

