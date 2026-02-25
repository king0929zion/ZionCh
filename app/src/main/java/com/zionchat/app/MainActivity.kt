package com.zionchat.app

import android.graphics.Color
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.zionchat.app.ui.screens.*
import com.zionchat.app.ui.theme.ZionChatTheme
import okhttp3.OkHttpClient
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            ZionChatTheme {
                val okHttpClient = remember { OkHttpClient() }
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .components {
                            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                            add(SvgDecoder.Factory())
                        }
                        .build()
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val appContainer = remember { AppContainer(applicationContext) }
                    val navController = rememberNavController()
                    CompositionLocalProvider(
                        LocalAppRepository provides appContainer.repository,
                        LocalChatApiClient provides appContainer.chatApiClient,
                        LocalOAuthClient provides appContainer.oauthClient,
                        LocalProviderAuthManager provides appContainer.providerAuthManager,
                        LocalWebHostingService provides appContainer.webHostingService,
                        LocalRuntimePackagingService provides appContainer.runtimePackagingService
                    ) {
                        val appLanguage by appContainer.repository.appLanguageFlow.collectAsState(initial = "__pending__")
                        LaunchedEffect(appLanguage) {
                            runCatching {
                                val normalizedLanguage = appLanguage.trim().lowercase()
                                if (normalizedLanguage !in setOf("system", "en", "zh")) return@runCatching
                                val locales =
                                    when (normalizedLanguage) {
                                        "en" -> LocaleListCompat.forLanguageTags("en")
                                        "zh" -> LocaleListCompat.forLanguageTags("zh-CN")
                                        else -> LocaleListCompat.getEmptyLocaleList()
                                    }
                                val currentLocales = AppCompatDelegate.getApplicationLocales()
                                val currentLanguage = currentLocales[0]?.language?.lowercase()
                                val shouldApply =
                                    when (normalizedLanguage) {
                                        "en" -> currentLanguage != "en"
                                        "zh" -> currentLanguage != "zh"
                                        else -> !currentLocales.isEmpty
                                    }
                                if (shouldApply) {
                                    AppCompatDelegate.setApplicationLocales(locales)
                                }
                            }
                        }
                        LaunchedEffect(Unit) {
                            runCatching { appContainer.repository.migratePersonalNicknameIfNeeded() }
                        }
                        NavHost(
                            navController = navController,
                            startDestination = "chat",
                            enterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.28f).roundToInt() },
                                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(durationMillis = 240))
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> (-fullWidth * 0.1f).roundToInt() },
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(durationMillis = 200))
                            },
                            popEnterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> (-fullWidth * 0.18f).roundToInt() },
                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(durationMillis = 220))
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> (fullWidth * 0.32f).roundToInt() },
                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(durationMillis = 220))
                            }
                        ) {
                            composable("chat") { ChatScreen(navController) }
                            composable(
                                route = "group_chat/{groupId}",
                                arguments = listOf(navArgument("groupId") { defaultValue = "" })
                            ) { backStackEntry ->
                                val groupId = backStackEntry.arguments?.getString("groupId")
                                GroupChatScreen(navController, groupId)
                            }
                            composable("settings") { SettingsScreen(navController) }
                            composable("group_chats") { GroupChatsScreen(navController) }
                            composable("create_group_chat") { CreateGroupChatScreen(navController) }
                            composable(
                                route = "edit_group_chat/{groupId}",
                                arguments = listOf(navArgument("groupId") { defaultValue = "" })
                            ) { backStackEntry ->
                                val groupId = backStackEntry.arguments?.getString("groupId")
                                CreateGroupChatScreen(navController, groupId)
                            }
                            composable("group_bots") { GroupBotsScreen(navController) }
                            composable("add_bot") { AddBotScreen(navController) }
                            composable(
                                route = "edit_bot/{botId}",
                                arguments = listOf(navArgument("botId") { defaultValue = "" })
                            ) { backStackEntry ->
                                val botId = backStackEntry.arguments?.getString("botId")
                                AddBotScreen(navController, botId)
                            }
                            composable("apps") { AppsScreen(navController) }
                            composable("language") { LanguageScreen(navController) }
                            composable("personalization") { PersonalizationScreen(navController) }
                            composable("memories") { MemoriesScreen(navController) }
                            composable("default_model") { DefaultModelScreen(navController) }
                            composable("model_services") { ModelServicesScreen(navController) }
                            composable(
                                route = "add_oauth_provider?provider={provider}&providerId={providerId}",
                                arguments = listOf(
                                    navArgument("provider") { defaultValue = "" },
                                    navArgument("providerId") { defaultValue = "" }
                                )
                            ) { backStackEntry ->
                                val provider = backStackEntry.arguments?.getString("provider")?.trim().takeIf { !it.isNullOrBlank() }
                                val providerId = backStackEntry.arguments?.getString("providerId")?.trim().takeIf { !it.isNullOrBlank() }
                                AddOAuthProviderScreen(navController, provider, providerId)
                            }
                            composable(
                                route = "add_device_provider?provider={provider}&providerId={providerId}",
                                arguments = listOf(
                                    navArgument("provider") { defaultValue = "" },
                                    navArgument("providerId") { defaultValue = "" }
                                )
                            ) { backStackEntry ->
                                val provider = backStackEntry.arguments?.getString("provider")?.trim().takeIf { !it.isNullOrBlank() }
                                val providerId = backStackEntry.arguments?.getString("providerId")?.trim().takeIf { !it.isNullOrBlank() }
                                AddDeviceProviderScreen(navController, provider, providerId)
                            }
                            composable(
                                route = "add_provider?preset={preset}&providerId={providerId}",
                                arguments = listOf(
                                    navArgument("preset") { defaultValue = "" },
                                    navArgument("providerId") { defaultValue = "" }
                                )
                            ) { backStackEntry ->
                                val preset = backStackEntry.arguments?.getString("preset")
                                val providerId = backStackEntry.arguments?.getString("providerId")
                                AddProviderScreen(navController, preset, providerId)
                            }
                            composable(
                                route = "models?providerId={providerId}",
                                arguments = listOf(navArgument("providerId") { defaultValue = "" })
                            ) { backStackEntry ->
                                val providerId = backStackEntry.arguments?.getString("providerId")
                                ModelsScreen(navController, providerId)
                            }
                            composable(
                                route = "model_config?id={id}",
                                arguments = listOf(navArgument("id") { defaultValue = "" })
                            ) { backStackEntry ->
                                val modelId = backStackEntry.arguments?.getString("id")
                                ModelConfigScreen(navController, modelId)
                            }
                            composable("mcp") { McpScreen(navController) }
                            composable(
                                route = "mcp_detail/{mcpId}",
                                arguments = listOf(navArgument("mcpId") { defaultValue = "" })
                            ) { backStackEntry ->
                                val mcpId = backStackEntry.arguments?.getString("mcpId") ?: ""
                                McpDetailScreen(navController, mcpId)
                            }
                            composable("search_settings") { SearchSettingsScreen(navController) }
                            composable(
                                route = "search_provider_config/{provider}",
                                arguments = listOf(navArgument("provider") { defaultValue = "bing" })
                            ) { backStackEntry ->
                                val provider = backStackEntry.arguments?.getString("provider")
                                SearchProviderConfigScreen(navController, provider)
                            }
                            composable("autosoul") { AutoSoulScreen(navController) }
                            composable("about") { AboutScreen(navController) }
                            composable("web_hosting") { WebHostingScreen(navController) }
                        }
                    }
                }
            }
        }
    }
}
