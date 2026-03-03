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
import com.zionchat.app.data.DefaultZiCodeGitHubService
import com.zionchat.app.data.ZiCodeGitHubService
import com.zionchat.app.data.DefaultZiCodeToolDispatcher
import com.zionchat.app.data.ZiCodeToolDispatcher
import com.zionchat.app.data.DefaultZiCodePolicyService
import com.zionchat.app.data.ZiCodePolicyService
import com.zionchat.app.data.ZiCodeAgentOrchestrator
import com.zionchat.app.data.ZiCodeWorkflowTemplateService

class AppContainer(context: Context) {
    val repository = AppRepository(context)
    val chatApiClient = ChatApiClient()
    val oauthClient = OAuthClient()
    val providerAuthManager = ProviderAuthManager(repository, oauthClient)
    val webHostingService: WebHostingService = DisabledWebHostingService()
    val runtimePackagingService: RuntimePackagingService = DisabledRuntimePackagingService()
    val zicodeGitHubService: ZiCodeGitHubService = DefaultZiCodeGitHubService()
    val zicodePolicyService: ZiCodePolicyService = DefaultZiCodePolicyService()
    val zicodeToolDispatcher: ZiCodeToolDispatcher =
        DefaultZiCodeToolDispatcher(repository, zicodeGitHubService, zicodePolicyService)
    val zicodeAgentOrchestrator = ZiCodeAgentOrchestrator(repository, zicodeToolDispatcher, zicodePolicyService)
    val zicodeWorkflowTemplateService = ZiCodeWorkflowTemplateService(zicodeToolDispatcher)
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

val LocalZiCodeGitHubService = staticCompositionLocalOf<ZiCodeGitHubService> {
    error("LocalZiCodeGitHubService not provided")
}

val LocalZiCodeToolDispatcher = staticCompositionLocalOf<ZiCodeToolDispatcher> {
    error("LocalZiCodeToolDispatcher not provided")
}

val LocalZiCodePolicyService = staticCompositionLocalOf<ZiCodePolicyService> {
    error("LocalZiCodePolicyService not provided")
}

val LocalZiCodeAgentOrchestrator = staticCompositionLocalOf<ZiCodeAgentOrchestrator> {
    error("LocalZiCodeAgentOrchestrator not provided")
}

val LocalZiCodeWorkflowTemplateService = staticCompositionLocalOf<ZiCodeWorkflowTemplateService> {
    error("LocalZiCodeWorkflowTemplateService not provided")
}
