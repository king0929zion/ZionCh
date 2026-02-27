package com.zionchat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.BotConfig
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// 预设头像列表
val PRESET_AVATARS = listOf(
    "avatar_1.jpg", "avatar_2.jpg", "avatar_4.jpg", "avatar_5.jpg",
    "avatar_6.jpg", "avatar_7.jpg", "avatar_8.jpg", "avatar_10.jpg",
    "avatar_11.jpg", "avatar_12.jpg"
)

@Composable
fun AddBotScreen(navController: NavController, botId: String? = null) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val providers by repository.providersFlow.collectAsState(initial = emptyList())
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val enabledModels = remember(models) { models.filter { it.enabled } }
    
    // 如果是编辑模式，加载现有bot数据
    val existingBot by repository.getBotById(botId ?: "").collectAsState(initial = null)
    
    var name by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var avatarAssetName by remember { mutableStateOf<String?>(null) }
    var defaultModelId by remember { mutableStateOf<String?>(null) }
    var systemPrompt by remember { mutableStateOf("") }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    
    // 加载现有数据
    LaunchedEffect(existingBot) {
        existingBot?.let { bot ->
            name = bot.name
            avatarUri = bot.avatarUri
            avatarAssetName = bot.avatarAssetName
            defaultModelId = bot.defaultModelId
            systemPrompt = bot.systemPrompt
        }
    }
    
    val canSave = name.trim().isNotBlank()
    
    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 复制图片到应用私有目录
            val savedUri = copyImageToApp(context, it)
            avatarUri = savedUri
            avatarAssetName = null
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = if (botId != null) stringResource(R.string.group_bot_edit_title) else stringResource(R.string.group_bot_add_title),
            onBack = { navController.popBackStack() },
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canSave) Color(0xFF1C1C1E) else GrayLight)
                        .pressableScale(
                            pressedScale = 0.96f,
                            onClick = {
                                if (!canSave) return@pressableScale
                                scope.launch {
                                    val bot = BotConfig(
                                        id = botId ?: UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        avatarUri = avatarUri,
                                        avatarAssetName = avatarAssetName,
                                        defaultModelId = defaultModelId,
                                        systemPrompt = systemPrompt.trim(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    if (botId != null) {
                                        repository.updateBot(bot)
                                    } else {
                                        repository.addBot(bot)
                                    }
                                    navController.popBackStack()
                                }
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.common_save),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSave) Color.White else TextSecondary
                    )
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 头像和名字区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(GrayLighter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showAvatarPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (avatarAssetName != null) {
                        AsyncImage(
                            model = "file:///android_asset/avatars/$avatarAssetName",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Bot,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // 编辑图标
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Surface)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Plus,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                
                // 名字输入
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.group_bot_name_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SourceSans3,
                        color = TextSecondary
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (name.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.group_bot_name_placeholder),
                                            fontSize = 15.sp,
                                            color = Color(0xFFC7C7CC)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }
            
            // Default Model
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.group_bot_default_model_label),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SourceSans3,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showModelSelector = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val display = defaultModelId?.let { id ->
                            models.firstOrNull { it.id == id }?.displayName
                                ?: models.firstOrNull { extractRemoteModelId(it.id) == id }?.displayName
                        }
                        val isEmpty = display.isNullOrBlank()
                        Text(
                            text = if (isEmpty) stringResource(R.string.group_bot_default_model_placeholder) else display.orEmpty(),
                            fontSize = 16.sp,
                            color = if (isEmpty) Color(0xFFC7C7CC) else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // System Prompt
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.group_bot_system_prompt_label),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SourceSans3,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
                ) {
                    BasicTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        minLines = 4,
                        maxLines = 8,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = TextPrimary,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(TextPrimary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (systemPrompt.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.group_bot_system_prompt_placeholder),
                                        fontSize = 15.sp,
                                        color = Color(0xFFC7C7CC)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
    
    // Avatar Picker Dialog
    AvatarPickerDialog(
        visible = showAvatarPicker,
        selectedAsset = avatarAssetName,
        selectedUri = avatarUri,
        onSelectAsset = { assetName ->
            avatarAssetName = assetName
            avatarUri = null
            showAvatarPicker = false
        },
        onSelectCustom = {
            imagePicker.launch("image/*")
            showAvatarPicker = false
        },
        onDismiss = { showAvatarPicker = false }
    )
    
    // Model Selector Modal
    BotModelSelectorModal(
        visible = showModelSelector,
        providers = providers,
        models = enabledModels,
        selectedModelId = defaultModelId,
        onSelect = { 
            defaultModelId = it
            showModelSelector = false 
        },
        onDismiss = { showModelSelector = false }
    )
}

@Composable
private fun AvatarPickerDialog(
    visible: Boolean,
    selectedAsset: String?,
    selectedUri: String?,
    onSelectAsset: (String) -> Unit,
    onSelectCustom: () -> Unit,
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.group_bot_avatar_picker_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    // Preset avatars grid
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.group_bot_avatar_preset),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        
                        // Grid of preset avatars
                        val rows = PRESET_AVATARS.chunked(5)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    row.forEach { assetName ->
                                        val isSelected = selectedAsset == assetName
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(GrayLighter)
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.padding(3.dp)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { onSelectAsset(assetName) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = "file:///android_asset/avatars/$assetName",
                                                contentDescription = null,
                                                modifier = if (isSelected) {
                                                    Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                } else {
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(2.dp)
                                                        .clip(CircleShape)
                                                },
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Custom upload option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSelectCustom
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = GrayLighter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Plus,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(R.string.group_bot_avatar_upload_custom),
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotModelSelectorModal(
    visible: Boolean,
    providers: List<ProviderConfig>,
    models: List<com.zionchat.app.data.ModelConfig>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .heightIn(max = screenHeight * 0.78f)
                ) {
                    // Handle
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
                                .background(GrayLight, RoundedCornerShape(2.dp))
                        )
                    }
                    
                    Text(
                        text = stringResource(R.string.group_bot_select_model_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Surface.copy(alpha = 0.88f),
                                        Surface.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val providerNameById = remember(providers) {
                        providers.associateBy({ it.id }, { it.name })
                    }
                    
                    val grouped = remember(models, providerNameById) {
                        models
                            .filter { it.enabled }
                            .groupBy { model ->
                                model.providerId?.let { providerNameById[it] } ?: "Other"
                            }
                            .toList()
                            .sortedBy { it.first }
                    }
                    
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = GrayLighter)
                                ) {
                                    providerModels.forEachIndexed { index, model ->
                                        val isSelected = selectedModelId == model.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { onSelect(model.id) }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = model.displayName,
                                                fontSize = 16.sp,
                                                color = TextPrimary
                                            )
                                            Icon(
                                                imageVector = AppIcons.Check,
                                                contentDescription = null,
                                                tint = TextPrimary,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .alpha(if (isSelected) 1f else 0f)
                                            )
                                        }
                                        if (index != providerModels.lastIndex) {
                                            androidx.compose.material3.HorizontalDivider(
                                                color = GrayLight,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
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

// 复制图片到应用私有目录
private fun copyImageToApp(context: android.content.Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
    val fileName = "avatar_${System.currentTimeMillis()}.jpg"
    val file = File(context.filesDir, "avatars/$fileName")
    file.parentFile?.mkdirs()
    
    inputStream?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }
    
    return file.absolutePath
}

