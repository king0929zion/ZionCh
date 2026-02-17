package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zionchat.app.LocalAppRepository
import com.zionchat.app.R
import com.zionchat.app.data.WebSearchConfig
import com.zionchat.app.ui.components.PageTopBar
import com.zionchat.app.ui.components.pressableScale
import com.zionchat.app.ui.theme.Background
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.Surface
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SearchSettingsScreen(navController: NavController) {
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
    val savedText = stringResource(R.string.common_save)

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

    fun saveConfig() {
        scope.launch {
            repository.setWebSearchConfig(buildConfig())
            statusText = savedText
        }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.search_settings_engine),
                color = TextSecondary,
                fontFamily = SourceSans3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeOption(
                    text = "Bing",
                    selected = engine == "bing",
                    onClick = { engine = "bing" },
                    modifier = Modifier.weight(1f)
                )
                TypeOption(
                    text = "Exa",
                    selected = engine == "exa",
                    onClick = { engine = "exa" },
                    modifier = Modifier.weight(1f)
                )
                TypeOption(
                    text = "Tavily",
                    selected = engine == "tavily",
                    onClick = { engine = "tavily" },
                    modifier = Modifier.weight(1f)
                )
                TypeOption(
                    text = "Linkup",
                    selected = engine == "linkup",
                    onClick = { engine = "linkup" },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.search_settings_auto_title),
                        color = TextPrimary,
                        fontFamily = SourceSans3
                    )
                    Text(
                        text = stringResource(R.string.search_settings_auto_subtitle),
                        color = TextSecondary,
                        fontFamily = SourceSans3
                    )
                }
                Switch(
                    checked = autoSearchEnabled,
                    onCheckedChange = { autoSearchEnabled = it },
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.search_settings_result_count),
                        color = TextPrimary,
                        fontFamily = SourceSans3
                    )
                    Text(
                        text = stringResource(R.string.search_settings_result_count_subtitle),
                        color = TextSecondary,
                        fontFamily = SourceSans3
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .background(Background, RoundedCornerShape(10.dp))
                            .pressableScale(pressedScale = 0.96f) {
                                maxResults = (maxResults - 1).coerceAtLeast(1)
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "-", color = TextPrimary)
                    }
                    Text(
                        text = maxResults.toString(),
                        color = TextPrimary,
                        fontFamily = SourceSans3
                    )
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .background(Background, RoundedCornerShape(10.dp))
                            .pressableScale(pressedScale = 0.96f) {
                                maxResults = (maxResults + 1).coerceAtMost(10)
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", color = TextPrimary)
                    }
                }
            }

            when (engine) {
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
                    Text(
                        text = stringResource(R.string.search_settings_depth),
                        color = TextSecondary,
                        fontFamily = SourceSans3
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypeOption(
                            text = stringResource(R.string.search_settings_depth_basic),
                            selected = tavilyDepth == "basic",
                            onClick = { tavilyDepth = "basic" },
                            modifier = Modifier.weight(1f)
                        )
                        TypeOption(
                            text = stringResource(R.string.search_settings_depth_advanced),
                            selected = tavilyDepth == "advanced",
                            onClick = { tavilyDepth = "advanced" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                "linkup" -> {
                    FormField(
                        label = stringResource(R.string.search_settings_linkup_key),
                        value = linkupApiKey,
                        onValueChange = { linkupApiKey = it },
                        placeholder = "linkup_..."
                    )
                    Text(
                        text = stringResource(R.string.search_settings_depth),
                        color = TextSecondary,
                        fontFamily = SourceSans3
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypeOption(
                            text = stringResource(R.string.search_settings_depth_standard),
                            selected = linkupDepth == "standard",
                            onClick = { linkupDepth = "standard" },
                            modifier = Modifier.weight(1f)
                        )
                        TypeOption(
                            text = stringResource(R.string.search_settings_depth_deep),
                            selected = linkupDepth == "deep",
                            onClick = { linkupDepth = "deep" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.search_settings_bing_note),
                            color = TextSecondary,
                            fontFamily = SourceSans3
                        )
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
