package com.zionchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.WebSearchConfig
import com.zionchat.app.ui.components.AssetIcon
import com.zionchat.app.ui.components.LiquidGlassSwitch
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
    val subtitle: String,
    val iconAsset: String?
)

@Composable
fun SearchSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()

    var engine by rememberSaveable { mutableStateOf("bing") }
    var exaApiKey by rememberSaveable { mutableStateOf("") }
    var tavilyApiKey by rememberSaveable { mutableStateOf("") }
    var tavilyDepth by rememberSaveable { mutableStateOf("advanced") }
    var linkupApiKey by rememberSaveable { mutableStateOf("") }
    var linkupDepth by rememberSaveable { mutableStateOf("standard") }
    var autoSearchEnabled by rememberSaveable { mutableStateOf(true) }
    var maxResults by rememberSaveable { mutableStateOf(6) }
    var initialized by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    val providerCards = remember {
        listOf(
            SearchProviderUi(
                id = "bing",
                title = "Bing",
                subtitle = "No API key required",
                iconAsset = "bing.png"
            ),
            SearchProviderUi(
                id = "exa",
                title = "Exa",
                subtitle = "Semantic web search",
                iconAsset = "exa.png"
            ),
            SearchProviderUi(
                id = "tavily",
                title = "Tavily",
                subtitle = "Research-focused search",
                iconAsset = "tavily.png"
            ),
            SearchProviderUi(
                id = "linkup",
                title = "Linkup",
                subtitle = "Answer + citation retrieval",
                iconAsset = "linkup.png"
            )
        )
    }

    fun normalizedEngine(raw: String): String {
        return when (raw.trim().lowercase()) {
            "bing", "exa", "tavily", "linkup" -> raw.trim().lowercase()
            else -> "bing"
        }
    }

    fun buildConfig(): WebSearchConfig {
        return WebSearchConfig(
            engine = normalizedEngine(engine),
            exaApiKey = exaApiKey.trim(),
            tavilyApiKey = tavilyApiKey.trim(),
            tavilyDepth = if (tavilyDepth == "basic") "basic" else "advanced",
            linkupApiKey = linkupApiKey.trim(),
            linkupDepth = if (linkupDepth == "deep") "deep" else "standard",
            autoSearchEnabled = autoSearchEnabled,
            maxResults = maxResults.coerceIn(1, 10)
        )
    }

    fun saveConfig() {
        scope.launch {
            repository.setWebSearchConfig(buildConfig())
            statusText = context.getString(R.string.common_save)
        }
    }

    LaunchedEffect(Unit) {
        val config = repository.getWebSearchConfig()
        engine = normalizedEngine(config.engine)
        exaApiKey = config.exaApiKey
        tavilyApiKey = config.tavilyApiKey
        tavilyDepth = if (config.tavilyDepth == "basic") "basic" else "advanced"
        linkupApiKey = config.linkupApiKey
        linkupDepth = if (config.linkupDepth == "deep") "deep" else "standard"
        autoSearchEnabled = config.autoSearchEnabled
        maxResults = config.maxResults.coerceIn(1, 10)
        initialized = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PageTopBar(
            title = stringResource(R.string.settings_item_search),
            onBack = { navController.navigateUp() },
            trailing = {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .background(Surface, RoundedCornerShape(20.dp))
                        .pressableScale(pressedScale = 0.96f) {
                            if (!initialized) return@pressableScale
                            saveConfig()
                            navController.navigateUp()
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.common_save),
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchSectionTitle(stringResource(R.string.settings_group_general))
            SearchCard {
                SearchToggleRow(
                    title = stringResource(R.string.search_settings_auto_title),
                    subtitle = stringResource(R.string.search_settings_auto_subtitle),
                    checked = autoSearchEnabled,
                    onCheckedChange = { autoSearchEnabled = it }
                )
                Divider(color = GrayLight, modifier = Modifier.fillMaxWidth())
                SearchResultCountRow(
                    count = maxResults,
                    onDecrease = { maxResults = (maxResults - 1).coerceAtLeast(1) },
                    onIncrease = { maxResults = (maxResults + 1).coerceAtMost(10) }
                )
            }

            SearchSectionTitle(stringResource(R.string.search_settings_providers_title))
            providerCards.forEach { provider ->
                SearchProviderCard(
                    item = provider,
                    selected = engine == provider.id,
                    onSelect = { engine = provider.id }
                ) {
                    when (provider.id) {
                        "exa" -> {
                            FormField(
                                label = stringResource(R.string.search_settings_exa_key),
                                value = exaApiKey,
                                onValueChange = { exaApiKey = it },
                                placeholder = "exa_..."
                            )
                        }

                        "tavily" -> {
                            FormField(
                                label = stringResource(R.string.search_settings_tavily_key),
                                value = tavilyApiKey,
                                onValueChange = { tavilyApiKey = it },
                                placeholder = "tvly-..."
                            )
                            Divider(color = GrayLight, modifier = Modifier.fillMaxWidth())
                            SearchDepthRow(
                                basicLabel = stringResource(R.string.search_settings_depth_basic),
                                advancedLabel = stringResource(R.string.search_settings_depth_advanced),
                                selected = tavilyDepth,
                                onSelect = { tavilyDepth = it },
                                basicValue = "basic",
                                advancedValue = "advanced"
                            )
                        }

                        "linkup" -> {
                            FormField(
                                label = stringResource(R.string.search_settings_linkup_key),
                                value = linkupApiKey,
                                onValueChange = { linkupApiKey = it },
                                placeholder = "linkup_..."
                            )
                            Divider(color = GrayLight, modifier = Modifier.fillMaxWidth())
                            SearchDepthRow(
                                basicLabel = stringResource(R.string.search_settings_depth_standard),
                                advancedLabel = stringResource(R.string.search_settings_depth_deep),
                                selected = linkupDepth,
                                onSelect = { linkupDepth = it },
                                basicValue = "standard",
                                advancedValue = "deep"
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.search_settings_bing_note),
                                color = TextSecondary,
                                fontFamily = SourceSans3,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(TextPrimary, RoundedCornerShape(16.dp))
                    .pressableScale(pressedScale = 0.98f) {
                        if (!initialized) return@pressableScale
                        saveConfig()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_settings_save),
                    color = Color.White,
                    fontFamily = SourceSans3
                )
            }

            statusText?.let { text ->
                Text(
                    text = text,
                    color = TextSecondary,
                    fontFamily = SourceSans3
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
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
private fun SearchProviderCard(
    item: SearchProviderUi,
    selected: Boolean,
    onSelect: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, if (selected) TextPrimary.copy(alpha = 0.16f) else GrayLight.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(CircleShape)
                            .background(TextPrimary, CircleShape)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_settings_provider_active),
                            fontFamily = SourceSans3,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent, CircleShape)
                            .border(1.dp, GrayLight, CircleShape)
                            .pressableScale(pressedScale = 0.96f, onClick = onSelect)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_settings_provider_use),
                            fontFamily = SourceSans3,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Divider(color = GrayLight, modifier = Modifier.fillMaxWidth())
            content()
        }
    }
}

@Composable
private fun SearchToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = SourceSans3,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontFamily = SourceSans3,
                fontSize = 13.sp
            )
        }
        LiquidGlassSwitch(
            checked = checked,
            onCheckedChange = { onCheckedChange(!checked) }
        )
    }
}

@Composable
private fun SearchResultCountRow(
    count: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.search_settings_result_count),
                color = TextPrimary,
                fontFamily = SourceSans3,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.search_settings_result_count_subtitle),
                color = TextSecondary,
                fontFamily = SourceSans3,
                fontSize = 13.sp
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CounterButton(text = "-", onClick = onDecrease)
            Text(
                text = count.toString(),
                color = TextPrimary,
                fontFamily = SourceSans3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            CounterButton(text = "+", onClick = onIncrease)
        }
    }
}

@Composable
private fun SearchDepthRow(
    basicLabel: String,
    advancedLabel: String,
    selected: String,
    onSelect: (String) -> Unit,
    basicValue: String,
    advancedValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TypeOption(
            text = basicLabel,
            selected = selected == basicValue,
            onClick = { onSelect(basicValue) },
            modifier = Modifier.weight(1f)
        )
        TypeOption(
            text = advancedLabel,
            selected = selected == advancedValue,
            onClick = { onSelect(advancedValue) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CounterButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(GrayLighter, RoundedCornerShape(10.dp))
            .pressableScale(pressedScale = 0.96f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
