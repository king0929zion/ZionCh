package com.zionchat.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.zionchat.app.data.AppRepository
import com.zionchat.app.data.ChatApiClient
import com.zionchat.app.data.DisabledRuntimePackagingService
import com.zionchat.app.data.DisabledWebHostingService
import com.zionchat.app.data.OAuthClient
import com.zionchat.app.data.ProviderAuthManager
import com.zionchat.app.data.RuntimePackagingService
import com.zionchat.app.data.WebHostingService
import com.zionchat.app.zicode.data.ZiCodeAgentRunner
import com.zionchat.app.zicode.data.ZiCodeGitHubService
import com.zionchat.app.zicode.data.ZiCodeRepository

class AppContainer(context: Context) {
    val repository = AppRepository(context)
    val chatApiClient = ChatApiClient()
    val oauthClient = OAuthClient()
    val providerAuthManager = ProviderAuthManager(repository, oauthClient)
    val webHostingService: WebHostingService = DisabledWebHostingService()
    val runtimePackagingService: RuntimePackagingService = DisabledRuntimePackagingService()
    val ziCodeRepository = ZiCodeRepository(context)
    val ziCodeGitHubService = ZiCodeGitHubService()
    val ziCodeAgentRunner = ZiCodeAgentRunner(ziCodeRepository, ziCodeGitHubService)
}

val LocalAppRepository = staticCompositionLocalOf<AppRepository> {
    error("LocalAppRepository not provided")
}

val LocalChatApiClient = staticCompositionLocalOf<ChatApiClient> {
    error("LocalChatApiClient not provided")
}

val LocalOAuthClient = staticCompositionLocalOf<OAuthClient> {
    error("LocalOAuthClient not provided")
}

val LocalProviderAuthManager = staticCompositionLocalOf<ProviderAuthManager> {
    error("LocalProviderAuthManager not provided")
}

val LocalWebHostingService = staticCompositionLocalOf<WebHostingService> {
    error("LocalWebHostingService not provided")
}

val LocalRuntimePackagingService = staticCompositionLocalOf<RuntimePackagingService> {
    error("LocalRuntimePackagingService not provided")
}

val LocalZiCodeRepository = staticCompositionLocalOf<ZiCodeRepository> {
    error("LocalZiCodeRepository not provided")
}

val LocalZiCodeGitHubService = staticCompositionLocalOf<ZiCodeGitHubService> {
    error("LocalZiCodeGitHubService not provided")
}

val LocalZiCodeAgentRunner = staticCompositionLocalOf<ZiCodeAgentRunner> {
    error("LocalZiCodeAgentRunner not provided")
}
