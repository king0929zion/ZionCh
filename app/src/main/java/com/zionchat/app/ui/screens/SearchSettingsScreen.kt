package com.zionchat.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.ui.components.AssetIcon
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

private data class SearchProviderUi(
    val id: String,
    val title: String,
    val iconAsset: String?
)

private val SEARCH_PROVIDER_ITEMS =
    listOf(
        SearchProviderUi(
            id = "bing",
            title = "Bing",
            iconAsset = "bing.png"
        ),
        SearchProviderUi(
            id = "exa",
            title = "Exa",
            iconAsset = "exa.png"
        ),
        SearchProviderUi(
            id = "tavily",
            title = "Tavily",
            iconAsset = "tavily.png"
        ),
        SearchProviderUi(
            id = "linkup",
            title = "Linkup",
            iconAsset = "linkup.png"
        )
    )

private fun normalizeSearchProvider(raw: String): String {
    return when (raw.trim().lowercase()) {
        "bing", "exa", "tavily", "linkup" -> raw.trim().lowercase()
        else -> "bing"
    }
}

private fun findSearchProvider(providerId: String): SearchProviderUi {
    val normalized = normalizeSearchProvider(providerId)
    return SEARCH_PROVIDER_ITEMS.firstOrNull { it.id == normalized } ?: SEARCH_PROVIDER_ITEMS.first()
}

@Composable
fun SearchSettingsScreen(navController: NavController) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    var engine by rememberSaveable { mutableStateOf("bing") }
    var autoSearchEnabled by rememberSaveable { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(currentBackStackEntry) {
        val config = repository.getWebSearchConfig()
        engine = normalizeSearchProvider(config.engine)
        autoSearchEnabled = config.autoSearchEnabled
        initialized = true
    }

    fun saveGeneralSettings() {
        scope.launch {
            val existing = repository.getWebSearchConfig()
            repository.setWebSearchConfig(
                existing.copy(
                    engine = normalizeSearchProvider(engine),
                    autoSearchEnabled = autoSearchEnabled
                )
            )
            navController.navigateUp()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFFFFFF))
    ) {
        PageTopBar(
            title = stringResource(R.string.settings_item_search),
            onBack = { navController.navigateUp() },
            trailing = {
                SaveAction(
                    enabled = initialized,
                    onClick = ::saveGeneralSettings
                )
            }
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchSectionTitle(stringResource(R.string.settings_group_general))
            SearchCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_settings_auto_title),
                        color = TextPrimary,
                        fontFamily = SourceSans3,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    SearchOutlineSwitch(
                        checked = autoSearchEnabled,
                        onClick = { autoSearchEnabled = !autoSearchEnabled }
                    )
                }

                Text(
                    text = stringResource(R.string.search_settings_auto_subtitle),
                    color = TextSecondary,
                    fontFamily = SourceSans3,
                    fontSize = 13.sp
                )
            }

            SearchSectionTitle(stringResource(R.string.search_settings_providers_title))
            SEARCH_PROVIDER_ITEMS.forEach { provider ->
                SearchProviderItem(
                    item = provider,
                    selected = engine == provider.id,
                    onClick = { navController.navigate("search_provider_config/${provider.id}") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SearchProviderConfigScreen(
    navController: NavController,
    providerId: String?
) {
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val provider = remember(providerId) { findSearchProvider(providerId ?: "bing") }

    var exaApiKey by rememberSaveable { mutableStateOf("") }
    var tavilyApiKey by rememberSaveable { mutableStateOf("") }
    var tavilyDepth by rememberSaveable { mutableStateOf("advanced") }
    var linkupApiKey by rememberSaveable { mutableStateOf("") }
    var linkupDepth by rememberSaveable { mutableStateOf("standard") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(provider.id) {
        val config = repository.getWebSearchConfig()
        exaApiKey = config.exaApiKey
        tavilyApiKey = config.tavilyApiKey
        tavilyDepth = if (config.tavilyDepth == "basic") "basic" else "advanced"
        linkupApiKey = config.linkupApiKey
        linkupDepth = if (config.linkupDepth == "deep") "deep" else "standard"
        initialized = true
    }

    fun saveProviderSettings() {
        scope.launch {
            val existing = repository.getWebSearchConfig()
            repository.setWebSearchConfig(
                existing.copy(
                    engine = provider.id,
                    exaApiKey = exaApiKey.trim(),
                    tavilyApiKey = tavilyApiKey.trim(),
                    tavilyDepth = if (tavilyDepth == "basic") "basic" else "advanced",
                    linkupApiKey = linkupApiKey.trim(),
                    linkupDepth = if (linkupDepth == "deep") "deep" else "standard"
                )
            )
            navController.navigateUp()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFFFFFF))
    ) {
        PageTopBar(
            title = provider.title,
            onBack = { navController.navigateUp() },
            trailing = {
                SaveAction(
                    enabled = initialized,
                    onClick = ::saveProviderSettings
                )
            }
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchSectionTitle(stringResource(R.string.search_settings_api_key))
            when (provider.id) {
                "exa" -> {
                    SearchApiKeyCard(
                        value = exaApiKey,
                        onValueChange = { exaApiKey = it },
                        placeholder = "exa_..."
                    )
                }

                "tavily" -> {
                    SearchApiKeyCard(
                        value = tavilyApiKey,
                        onValueChange = { tavilyApiKey = it },
                        placeholder = "tvly-..."
                    )
                }

                "linkup" -> {
                    SearchApiKeyCard(
                        value = linkupApiKey,
                        onValueChange = { linkupApiKey = it },
                        placeholder = "linkup_..."
                    )
                }

                else -> {
                    SearchCard {
                        Text(
                            text = stringResource(R.string.search_settings_bing_note),
                            color = TextSecondary,
                            fontFamily = SourceSans3,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SaveAction(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .height(40.dp)
                .background(
                    color = if (enabled) Surface else GrayLighter,
                    shape = RoundedCornerShape(20.dp)
                ).pressableScale(pressedScale = 0.96f) {
                    if (!enabled) return@pressableScale
                    onClick()
                }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.common_save),
            color = if (enabled) TextPrimary else TextSecondary,
            fontFamily = SourceSans3
        )
    }
}

@Composable
private fun SearchSectionTitle(text: String) {
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
private fun SearchCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SearchProviderItem(
    item: SearchProviderUi,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .pressableScale(pressedScale = 0.98f, onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GrayLighter, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.iconAsset.isNullOrBlank()) {
                    AssetIcon(
                        assetFileName = item.iconAsset,
                        contentDescription = item.title,
                        modifier = Modifier.size(22.dp),
                        contentScale = ContentScale.Fit,
                        error = {
                            Icon(
                                imageVector = AppIcons.Globe,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.Globe,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = item.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(TextPrimary, CircleShape)
                            .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_settings_provider_active),
                        color = Color.White,
                        fontFamily = SourceSans3,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = GrayLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SearchApiKeyCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    SearchCard {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle =
                TextStyle(
                    fontSize = 17.sp,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                ),
            cursorBrush = SolidColor(TextPrimary),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = TextSecondary,
                            fontFamily = SourceSans3,
                            fontSize = 17.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun SearchOutlineSwitch(
    checked: Boolean,
    onClick: () -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        label = "search_switch_thumb"
    )
    val activeColor = Color(0xFF1C1C1E)
    val inactiveColor = Color(0xFF8E8E93)

    Box(
        modifier =
            Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(if (checked) activeColor else Color.Transparent, CircleShape)
                .border(
                    width = 2.dp,
                    color = if (checked) activeColor else inactiveColor,
                    shape = CircleShape
                ).pressableScale(pressedScale = 0.96f, onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier =
                Modifier
                    .padding(start = 2.dp)
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else inactiveColor, CircleShape)
        )
    }
}
