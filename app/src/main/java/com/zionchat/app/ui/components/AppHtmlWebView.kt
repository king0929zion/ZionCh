package com.zionchat.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

private const val APP_RUNTIME_ERROR_MARKER = "ZION_APP_RUNTIME_ERROR:"
private const val APP_RUNTIME_DEBUG_HOOK_JS =
    "(function(){try{if(window.__zionDebugHookInstalled){return 'ok';}window.__zionDebugHookInstalled=true;window.addEventListener('error',function(e){try{var msg=(e&&e.message)?String(e.message):'Unknown runtime error';var src=(e&&e.filename)?String(e.filename):'';var ln=(e&&e.lineno)?String(e.lineno):'0';console.error('ZION_APP_RUNTIME_ERROR:'+msg+' @'+src+':'+ln);}catch(_){}});window.addEventListener('unhandledrejection',function(e){try{var reason='';try{reason=String(e.reason);}catch(_){reason='[unknown]';}console.error('ZION_APP_RUNTIME_ERROR:UnhandledPromiseRejection '+reason);}catch(_){}});return 'ok';}catch(err){return 'err';}})();"

@Stable
class AppHtmlWebViewState {
    var isLoading: Boolean by mutableStateOf(false)
        internal set
    var loadingProgress: Float by mutableFloatStateOf(0f)
        internal set
    var pageTitle: String? by mutableStateOf(null)
        internal set
    var currentUrl: String? by mutableStateOf(null)
        internal set
    var lastError: String? by mutableStateOf(null)
        internal set
    var consoleMessages: List<ConsoleMessage> by mutableStateOf(emptyList())
        internal set

    internal fun pushConsoleMessage(message: ConsoleMessage) {
        consoleMessages = (consoleMessages + message).takeLast(64)
    }
}

@Composable
fun rememberAppHtmlWebViewState(): AppHtmlWebViewState {
    return remember { AppHtmlWebViewState() }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppHtmlWebView(
    modifier: Modifier = Modifier,
    state: AppHtmlWebViewState = rememberAppHtmlWebViewState(),
    contentSignature: String,
    html: String? = null,
    baseUrl: String? = null,
    url: String? = null,
    enableCookies: Boolean = false,
    enableThirdPartyCookies: Boolean = false,
    allowMixedContent: Boolean = true,
    transparentBackground: Boolean = false,
    injectRuntimeDebugHook: Boolean = true,
    onRuntimeIssue: ((String) -> Unit)? = null,
    onPageFinished: ((WebView) -> Unit)? = null
) {
    val runtimeIssueCallback by rememberUpdatedState(onRuntimeIssue)
    val pageFinishedCallback by rememberUpdatedState(onPageFinished)

    val normalizedHtml = html.orEmpty()
    val normalizedUrl = url?.trim().orEmpty()
    val normalizedBaseUrl = baseUrl?.trim().orEmpty().ifBlank { "https://workspace-app.zionchat.local/" }

    val webChromeClient =
        remember {
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    state.loadingProgress = (newProgress / 100f).coerceIn(0f, 1f)
                    super.onProgressChanged(view, newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    state.pageTitle = title
                    super.onReceivedTitle(view, title)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    state.pushConsoleMessage(consoleMessage)
                    val message = consoleMessage.message()?.trim().orEmpty()
                    if (message.isNotBlank()) {
                        when {
                            message.contains(APP_RUNTIME_ERROR_MARKER, ignoreCase = true) -> {
                                val detail =
                                    message.substringAfter(APP_RUNTIME_ERROR_MARKER).trim().ifBlank {
                                        "Unknown runtime error."
                                    }
                                runtimeIssueCallback?.invoke(detail)
                            }
                            consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR -> {
                                runtimeIssueCallback?.invoke("Console error: $message")
                            }
                        }
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
        }

    val webViewClient =
        remember {
            object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    state.isLoading = true
                    state.currentUrl = url
                    state.lastError = null
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    state.isLoading = false
                    state.loadingProgress = 0f
                    state.currentUrl = url
                    state.pageTitle = view?.title
                    if (injectRuntimeDebugHook) {
                        view?.evaluateJavascript(APP_RUNTIME_DEBUG_HOOK_JS, null)
                    }
                    if (view != null) {
                        pageFinishedCallback?.invoke(view)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == false) return
                    val detail =
                        buildString {
                            append("Load error")
                            val desc = error?.description?.toString()?.trim().orEmpty()
                            if (desc.isNotBlank()) {
                                append(": ")
                                append(desc)
                            }
                        }
                    state.lastError = detail
                    runtimeIssueCallback?.invoke(detail)
                }

                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    val detail = "Load error: ${description?.trim().orEmpty().ifBlank { "Unknown" }}"
                    state.lastError = detail
                    runtimeIssueCallback?.invoke(detail)
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    val message =
                        if (detail?.didCrash() == true) {
                            "WebView render process crashed and preview was reset."
                        } else {
                            "WebView render process was reclaimed and preview was reset."
                        }
                    state.lastError = message
                    runtimeIssueCallback?.invoke(message)
                    view?.stopLoading()
                    view?.loadUrl("about:blank")
                    view?.removeAllViews()
                    view?.destroy()
                    return true
                }
            }
        }

    val containerModifier =
        if (transparentBackground) {
            modifier
        } else {
            modifier.background(Color.White)
        }

    Box(modifier = containerModifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    if (transparentBackground) {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                    } else {
                        setBackgroundColor(AndroidColor.WHITE)
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.mixedContentMode =
                        if (allowMixedContent) {
                            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        } else {
                            WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.textZoom = 100
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.safeBrowsingEnabled = true
                    }
                    overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS

                    if (enableCookies) {
                        CookieManager.getInstance().setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, enableThirdPartyCookies)
                        }
                    }

                    setWebViewClient(webViewClient)
                    setWebChromeClient(webChromeClient)
                }
            },
            update = { webView ->
                if (webView.tag != contentSignature) {
                    webView.tag = contentSignature
                    if (transparentBackground) {
                        webView.setBackgroundColor(AndroidColor.TRANSPARENT)
                    } else {
                        webView.setBackgroundColor(AndroidColor.WHITE)
                    }
                    if (normalizedUrl.isNotBlank()) {
                        webView.loadUrl(normalizedUrl)
                    } else {
                        webView.loadDataWithBaseURL(
                            normalizedBaseUrl,
                            normalizedHtml,
                            "text/html",
                            "utf-8",
                            null
                        )
                    }
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            }
        )

    }
}
