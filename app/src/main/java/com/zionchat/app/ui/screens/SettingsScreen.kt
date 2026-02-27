package com.zionchat.app.ui.screens

import android.content.Intent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import coil3.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.zionchat.app.R
import com.zionchat.app.LocalAppRepository
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.zionchat.app.data.extractRemoteModelId
import com.zionchat.app.ui.components.TopFadeScrim
import com.zionchat.app.ui.components.liquidGlass
import com.zionchat.app.ui.components.rememberResourceDrawablePainter
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val SettingsPageBackgroundColor = Color(0xFFFCFCFC)
private val SettingsItemContainerColor = Color(0xFFF3F3F3)
private val SettingsItemPressedColor = Color(0xFFEAEAEA)
private val SettingsGroupCornerRadius = 22.dp

@OptIn(FlowPreview::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val models by repository.modelsFlow.collectAsState(initial = emptyList())
    val defaultChatModelId by repository.defaultChatModelIdFlow.collectAsState(initial = null)
    val nickname by repository.nicknameFlow.collectAsState(initial = "")
    val avatarUri by repository.avatarUriFlow.collectAsState(initial = "")
    val appLanguage by repository.appLanguageFlow.collectAsState(initial = "system")
    val appAccentColor by repository.appAccentColorFlow.collectAsState(initial = "default")

    var showEditProfile by remember { mutableStateOf(false) }

    // Appearance & Accent Color 菜单状态
    var showAppearanceMenu by remember { mutableStateOf(false) }
    var showAccentColorMenu by remember { mutableStateOf(false) }
    var selectedAppearance by remember { mutableStateOf("light") }

    // 菜单锚点位置
    var appearanceAnchorY by remember { mutableFloatStateOf(0f) }
    var accentColorAnchorY by remember { mutableFloatStateOf(0f) }
    var accentColorAnchorHeight by remember { mutableFloatStateOf(0f) }

    val defaultChatModelName = remember(models, defaultChatModelId) {
        val id = defaultChatModelId?.trim().orEmpty()
        if (id.isBlank()) null
        else models.firstOrNull { it.id == id }?.displayName
            ?: models.firstOrNull { extractRemoteModelId(it.id) == id }?.displayName
    }

    val displayName = nickname.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_name)
    val screenBackdrop = rememberLayerBackdrop()
    val languageLabel =
        when (appLanguage.trim().lowercase()) {
            "zh" -> stringResource(R.string.language_option_zh)
            "en" -> stringResource(R.string.language_option_en)
            else -> stringResource(R.string.language_option_system)
        }
    val accentColorLabel =
        when (appAccentColor.trim().lowercase()) {
            "blue" -> stringResource(R.string.accent_color_blue)
            "pink" -> stringResource(R.string.accent_color_pink)
            "orange" -> stringResource(R.string.accent_color_orange)
            "black" -> stringResource(R.string.accent_color_black)
            else -> stringResource(R.string.accent_color_default)
        }

    Scaffold(
        topBar = { SettingsTopBar(navController) },
        containerColor = SettingsPageBackgroundColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .layerBackdrop(screenBackdrop)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // User Profile Section
                UserProfileSection(
                    nickname = displayName,
                    avatarUri = avatarUri,
                    onEditClick = { showEditProfile = true }
                )

                // My ChatGPT 分组
                SettingsGroup(title = stringResource(R.string.settings_group_my_chatgpt), itemCount = 2) {
                    SettingsItem(
                        icon = { Icon(AppIcons.Personalization, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.settings_item_personalization),
                        showDivider = true,
                        onClick = { navController.navigate("personalization") }
                    )
                    SettingsItem(
                        icon = {
                            Icon(
                                painter = rememberResourceDrawablePainter(R.drawable.ic_apps),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.Unspecified
                            )
                        },
                        label = stringResource(R.string.apps),
                        onClick = { navController.navigate("apps") }
                    )
                }

                // General 分组
                SettingsGroup(title = stringResource(R.string.settings_group_general), itemCount = 4) {
                    Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                        appearanceAnchorY = coordinates.positionInWindow().y
                    }) {
                        SettingsItem(
                            icon = { Icon(AppIcons.Appearance, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                            label = stringResource(R.string.settings_item_appearance),
                            value = when(selectedAppearance) {
                                "system" -> stringResource(R.string.appearance_option_system)
                                "light" -> stringResource(R.string.appearance_option_light)
                                "dark" -> stringResource(R.string.appearance_option_dark)
                                else -> stringResource(R.string.appearance_option_light)
                            },
                            showChevron = true,
                            showDivider = true,
                            onClick = {
                                showAccentColorMenu = false
                                showAppearanceMenu = true
                            }
                        )
                    }
                    Box(modifier = Modifier.onGloballyPositioned { coordinates ->
                        accentColorAnchorY = coordinates.positionInWindow().y
                        accentColorAnchorHeight = coordinates.size.height.toFloat()
                    }) {
                        SettingsItem(
                            icon = { Icon(AppIcons.Accent, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                            label = stringResource(R.string.settings_item_accent_color),
                            value = accentColorLabel,
                            showChevron = true,
                            showDivider = true,
                            onClick = {
                                showAppearanceMenu = false
                                showAccentColorMenu = true
                            }
                        )
                    }
                    SettingsItem(
                        icon = { Icon(AppIcons.Language, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.settings_item_language),
                        value = languageLabel,
                        showChevron = true,
                        showDivider = true,
                        onClick = { navController.navigate("language") }
                    )
                    SettingsItem(
                        icon = { Icon(AppIcons.Notifications, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.settings_item_notifications),
                        showChevron = true,
                        onClick = { }
                    )
                }

                SettingsGroup(title = stringResource(R.string.settings_group_group), itemCount = 2) {
                    SettingsItem(
                        icon = {
                            Icon(
                                painter = rememberResourceDrawablePainter(R.drawable.ic_group_chat),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = TextPrimary
                            )
                        },
                        label = stringResource(R.string.settings_item_group),
                        showChevron = true,
                        showDivider = true,
                        onClick = { navController.navigate("group_chats") }
                    )
                    SettingsItem(
                        icon = { Icon(AppIcons.Bot, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.settings_item_bots),
                        showChevron = true,
                        onClick = { navController.navigate("group_bots") }
                    )
                }

                // AI Model 分组
                SettingsGroup(title = stringResource(R.string.settings_group_ai_model), itemCount = 5) {
                    SettingsItem(
                        icon = {
                            Icon(
                                painter = rememberResourceDrawablePainter(R.drawable.ic_model),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.Unspecified
                            )
                        },
                        label = stringResource(R.string.settings_item_default_model),
                        value = defaultChatModelName ?: stringResource(R.string.not_set),
                        showChevron = true,
                        showDivider = true,
                        onClick = { navController.navigate("default_model") }
                    )
                    SettingsItem(
                        icon = {
                            Icon(
                                painter = rememberResourceDrawablePainter(R.drawable.ic_model_services),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.Unspecified
                            )
                        },
                        label = stringResource(R.string.settings_item_model_services),
                        showChevron = true,
                        showDivider = true,
                        onClick = { navController.navigate("model_services") }
                    )
                    SettingsItem(
                        icon = { Icon(AppIcons.Globe, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.settings_item_search),
                        showChevron = true,
                        showDivider = true,
                        onClick = { navController.navigate("search_settings") }
                    )
                    SettingsItem(
                        icon = { Icon(AppIcons.MCPTools, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.settings_item_mcp_tools),
                        showChevron = true,
                        showDivider = true,
                        onClick = { navController.navigate("mcp") }
                    )
                    SettingsItem(
                        icon = {
                            Icon(
                                painter = rememberResourceDrawablePainter(R.drawable.ic_autosoul),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = TextPrimary
                            )
                        },
                        label = stringResource(R.string.settings_item_autosoul),
                        showChevron = true,
                        onClick = { navController.navigate("autosoul") }
                    )
                }

                // About 分组
                SettingsGroup(title = stringResource(R.string.about_title), itemCount = 1) {
                    SettingsItem(
                        icon = { Icon(AppIcons.Info, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.about_title),
                        showChevron = true,
                        onClick = { navController.navigate("about") }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            TopFadeScrim(
                color = SettingsPageBackgroundColor,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    // 编辑资料弹窗
    EditProfileModal(
        visible = showEditProfile,
        onDismiss = { showEditProfile = false },
        currentNickname = nickname,
        currentAvatarUri = avatarUri,
        onAutoSave = { newNickname, newAvatarUri ->
            scope.launch {
                repository.setNickname(newNickname)
                repository.setAvatarUri(newAvatarUri)
            }
        }
    )

    // Appearance 菜单
    AppearanceMenu(
        visible = showAppearanceMenu,
        selected = selectedAppearance,
        anchorY = appearanceAnchorY,
        backdrop = screenBackdrop,
        onDismiss = { showAppearanceMenu = false },
        onSelect = { selectedAppearance = it }
    )

    // Accent Color 菜单
    AccentColorMenu(
        visible = showAccentColorMenu,
        selected = appAccentColor,
        anchorY = accentColorAnchorY,
        anchorHeight = accentColorAnchorHeight,
        backdrop = screenBackdrop,
        onDismiss = { showAccentColorMenu = false },
        onSelect = { key ->
            scope.launch { repository.setAppAccentColor(key) }
        }
    )
}

@Composable
fun SettingsTopBar(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SettingsPageBackgroundColor.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 返回按钮
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Surface, CircleShape)
                .pressableScale(pressedScale = 0.95f) { navController.navigateUp() }
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Back,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // 标题
        Text(
            text = stringResource(R.string.settings),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SourceSans3,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun UserAvatar(
    avatarUri: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(GrayLight, CircleShape)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri.isNotBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = AppIcons.User,
                contentDescription = "Avatar",
                tint = TextSecondary,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
fun UserProfileSection(
    nickname: String,
    avatarUri: String,
    onEditClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 用户头像
        UserAvatar(
            avatarUri = avatarUri,
            size = 80.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 用户名
        Text(
            text = nickname,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 编辑资料按钮
        Button(
            onClick = onEditClick,
            modifier = Modifier.height(34.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Surface
            ),
            contentPadding = PaddingValues(horizontal = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_item_edit_profile),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditProfileModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    currentNickname: String,
    currentAvatarUri: String,
    onAutoSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = remember(density) { with(density) { 120.dp.toPx() } }

    var nickname by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf("") }
    var originalNickname by remember { mutableStateOf("") }
    var originalAvatarUri by remember { mutableStateOf("") }

    val latestOnAutoSave by rememberUpdatedState(onAutoSave)

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            avatarUri = it.toString()
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            nickname = currentNickname
            avatarUri = currentAvatarUri
            originalNickname = currentNickname
            originalAvatarUri = currentAvatarUri
            dragOffsetPx = 0f
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        var isFirst = true
        snapshotFlow { nickname to avatarUri }
            .map { (n, a) -> n.trimEnd() to a }
            .debounce(400)
            .distinctUntilChanged()
            .collect { (n, a) ->
                if (isFirst) {
                    isFirst = false
                    return@collect
                }
                latestOnAutoSave(n, a)
            }
    }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = { velocity ->
                                val shouldDismiss = dragOffsetPx > dismissThresholdPx || velocity > 2400f
                                if (shouldDismiss) {
                                    onDismiss()
                                    dragOffsetPx = 0f
                                } else {
                                    scope.launch {
                                        animate(
                                            initialValue = dragOffsetPx,
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 180, easing = LinearEasing)
                                        ) { value, _ ->
                                            dragOffsetPx = value
                                        }
                                    }
                                }
                            }
                        )
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
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 拖动条
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(GrayLight, RoundedCornerShape(2.dp))
                            )
                        }

                        // 标题
                        Text(
                            text = stringResource(R.string.edit_profile_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SourceSans3,
                            color = TextPrimary,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        // 头像上传区域 - 居中
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 可点击的头像
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(GrayLight, CircleShape)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            imagePicker.launch(arrayOf("image/*"))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarUri.isNotBlank()) {
                                        AsyncImage(
                                            model = avatarUri,
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = AppIcons.User,
                                            contentDescription = "Avatar",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(50.dp)
                                        )
                                    }

                                    // 相机图标叠加
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Camera,
                                            contentDescription = "Change Photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.edit_profile_tap_change_photo),
                                    fontSize = 13.sp,
                                    fontFamily = SourceSans3,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Name 输入框
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.edit_profile_name),
                                fontSize = 13.sp,
                                fontFamily = SourceSans3,
                                color = TextSecondary
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = GrayLighter,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                BasicTextField(
                                    value = nickname,
                                    onValueChange = { nickname = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        color = TextPrimary
                                    ),
                                    cursorBrush = SolidColor(TextPrimary),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    latestOnAutoSave(originalNickname.trimEnd(), originalAvatarUri)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GrayLight)
                            ) {
                                Text(text = stringResource(R.string.common_cancel), fontSize = 17.sp, color = TextPrimary)
                            }

                            Button(
                                onClick = {
                                    latestOnAutoSave(nickname.trimEnd(), avatarUri)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                            ) {
                                Text(text = stringResource(R.string.common_save), fontSize = 17.sp, color = Surface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    itemCount: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 分组标题
        Text(
            text = title.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = SourceSans3,
            color = TextSecondary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        // 分组内容 - 使用更圆角与留白分隔
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(0.dp, RoundedCornerShape(SettingsGroupCornerRadius)),
            shape = RoundedCornerShape(SettingsGroupCornerRadius),
            colors = CardDefaults.cardColors(containerColor = SettingsPageBackgroundColor)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 点击时的缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .background(if (isPressed) SettingsItemPressedColor else SettingsItemContainerColor)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SourceSans3,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            if (value != null) {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (showChevron) {
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 模块之间留空线，露出更白背景
        if (showDivider) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

private data class AppearanceOption(
    val key: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val appearanceOptions = listOf(
    AppearanceOption("system", R.string.appearance_option_system, AppIcons.Monitor),
    AppearanceOption("light", R.string.appearance_option_light, AppIcons.Sun),
    AppearanceOption("dark", R.string.appearance_option_dark, AppIcons.Moon)
)

private data class AccentOption(
    val key: String,
    val labelRes: Int,
    val color: Color
)

private val accentColorOptions = listOf(
    AccentOption("default", R.string.accent_color_default, Color(0xFF9CA3AF)),
    AccentOption("blue", R.string.accent_color_blue, Color(0xFF3B82F6)),
    AccentOption("pink", R.string.accent_color_pink, Color(0xFFEC4899)),
    AccentOption("orange", R.string.accent_color_orange, Color(0xFFF97316)),
    AccentOption("black", R.string.accent_color_black, Color(0xFF111214))
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppearanceMenu(
    visible: Boolean,
    selected: String,
    anchorY: Float,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(100)),
        exit = fadeOut(animationSpec = tween(100))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                ) + androidx.compose.animation.scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { 20 },
                    animationSpec = tween(200, easing = androidx.compose.animation.core.EaseInCubic)
                ) + androidx.compose.animation.scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(200, easing = androidx.compose.animation.core.EaseInCubic)
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 80.dp)
                    .graphicsLayer {
                        translationY = anchorY - 20
                    }
            ) {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .shadow(elevation = 16.dp, shape = shape, clip = false)
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = shape,
                            overlayColor = Color(0xFFF5F5F5).copy(alpha = 0.72f),
                            fallbackColor = Color(0xFFF5F5F5),
                            blurRadius = 24.dp,
                            refractionHeight = 6.dp,
                            refractionAmount = 10.dp,
                            highlightAlpha = 0.22f,
                            shadowAlpha = 0f
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        appearanceOptions.forEach { option ->
                            val key = option.key
                            val isSelected = key == selected
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Transparent)
                                    .clickable {
                                        onSelect(key)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = TextPrimary
                                        )
                                        Text(
                                            text = stringResource(option.labelRes),
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                            color = TextPrimary
                                        )
                                    }

                                    // 选中打勾 - 带动画
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = isSelected,
                                        enter = androidx.compose.animation.scaleIn(
                                            initialScale = 0f,
                                            animationSpec = tween(200, easing = androidx.compose.animation.core.EaseOutBack)
                                        ),
                                        exit = androidx.compose.animation.scaleOut(
                                            targetScale = 0f,
                                            animationSpec = tween(150)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AccentColorMenu(
    visible: Boolean,
    selected: String,
    anchorY: Float,
    anchorHeight: Float,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val topOffsetPx = with(density) { 20.dp.toPx() }
    val screenMarginPx = with(density) { 8.dp.toPx() }
    val optionHeightPx = with(density) { 40.dp.toPx() }
    val optionSpacingPx = with(density) { 2.dp.toPx() }
    val contentPaddingPx = with(density) { 12.dp.toPx() }
    val optionCount = accentColorOptions.size
    val menuHeightPx =
        contentPaddingPx +
            optionCount * optionHeightPx +
            ((optionCount - 1).coerceAtLeast(0) * optionSpacingPx)
    val spaceBelowPx = screenHeightPx - (anchorY + anchorHeight)
    val spaceAbovePx = anchorY
    val openUpward = spaceBelowPx < menuHeightPx && spaceAbovePx > spaceBelowPx
    val targetTranslationY =
        (if (openUpward) {
            anchorY - menuHeightPx + anchorHeight
        } else {
            anchorY - topOffsetPx
        }).coerceIn(
            screenMarginPx,
            (screenHeightPx - menuHeightPx - screenMarginPx).coerceAtLeast(screenMarginPx)
        )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(100)),
        exit = fadeOut(animationSpec = tween(100))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                ) + androidx.compose.animation.scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { 20 },
                    animationSpec = tween(200, easing = androidx.compose.animation.core.EaseInCubic)
                ) + androidx.compose.animation.scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(200, easing = androidx.compose.animation.core.EaseInCubic)
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 80.dp)
                    .graphicsLayer {
                        translationY = targetTranslationY
                    }
            ) {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .shadow(elevation = 16.dp, shape = shape, clip = false)
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = shape,
                            overlayColor = Color(0xFFF5F5F5).copy(alpha = 0.72f),
                            fallbackColor = Color(0xFFF5F5F5),
                            blurRadius = 24.dp,
                            refractionHeight = 6.dp,
                            refractionAmount = 10.dp,
                            highlightAlpha = 0.22f,
                            shadowAlpha = 0f
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        accentColorOptions.forEach { option ->
                            val key = option.key
                            val color = option.color
                            val isSelected = key == selected
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Transparent)
                                    .clickable {
                                        onSelect(key)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 颜色圆点
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )

                                        Text(
                                            text = stringResource(option.labelRes),
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                            color = TextPrimary
                                        )
                                    }

                                    // 选中打勾 - 带动画
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = isSelected,
                                        enter = androidx.compose.animation.scaleIn(
                                            initialScale = 0f,
                                            animationSpec = tween(200, easing = androidx.compose.animation.core.EaseOutBack)
                                        ),
                                        exit = androidx.compose.animation.scaleOut(
                                            targetScale = 0f,
                                            animationSpec = tween(150)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
