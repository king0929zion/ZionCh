package com.zionchat.app.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class LocalBridgeRuntimePackagingService(
    private val appContext: android.content.Context,
    private val config: RuntimePackagingConfig
) : RuntimePackagingService {
    private val requestTimeoutMs = config.requestTimeoutMs.coerceIn(5_000L, 120_000L)
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(minOf(requestTimeoutMs, 15_000L), TimeUnit.MILLISECONDS)
            .readTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun triggerRuntimePackaging(
        app: SavedApp,
        deployUrl: String,
        versionModel: Int
    ): Result<SavedApp> {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!RuntimeShellPlugin.isInstalled(appContext)) {
                    return@runCatching app.copy(
                        runtimeBuildStatus = "disabled",
                        runtimeBuildError = "Runtime shell template is required. Download it from Apps page.",
                        runtimeBuildUpdatedAt = System.currentTimeMillis()
                    )
                }

                val normalizedUrl = deployUrl.trim()
                if (normalizedUrl.isBlank()) {
                    return@runCatching app.copy(
                        runtimeBuildStatus = "skipped",
                        runtimeBuildError = "Deploy URL is missing",
                        runtimeBuildUpdatedAt = System.currentTimeMillis()
                    )
                }

                val bridgeBaseUrl = normalizeBridgeBaseUrl()
                if (bridgeBaseUrl.isBlank()) {
                    return@runCatching app.copy(
                        runtimeBuildStatus = "disabled",
                        runtimeBuildError = "Local runtime packager is not configured",
                        runtimeBuildUpdatedAt = System.currentTimeMillis()
                    )
                }

                val safeVersionModel = versionModel.coerceAtLeast(1)
                val runtimeVersionCode = computeRuntimeVersionCode(app.versionCode, safeVersionModel)
                val runtimeVersionName = computeRuntimeVersionName(app.versionName, app.versionCode, safeVersionModel)
                val packageSuffix = buildPackageSuffix(app.id)
                val runtimeShellPackage = RuntimeShellPlugin.packageName()
                val runtimeShellDownloadUrl = RuntimeShellPlugin.downloadUrl()
                val requestBody =
                    buildString {
                        append("{")
                        append("\"appId\":\"")
                        append(escapeJson(app.id))
                        append("\",")
                        append("\"appName\":\"")
                        append(escapeJson(app.name.trim()))
                        append("\",")
                        append("\"appUrl\":\"")
                        append(escapeJson(normalizedUrl))
                        append("\",")
                        append("\"packageSuffix\":\"")
                        append(escapeJson(packageSuffix))
                        append("\",")
                        append("\"runtimeTemplate\":\"builtin_shell_plugin\",")
                        append("\"runtimeShellPackage\":\"")
                        append(escapeJson(runtimeShellPackage))
                        append("\",")
                        append("\"runtimeShellDownloadUrl\":\"")
                        append(escapeJson(runtimeShellDownloadUrl))
                        append("\",")
                        append("\"versionName\":\"")
                        append(escapeJson(runtimeVersionName))
                        append("\",")
                        append("\"versionCode\":")
                        append(runtimeVersionCode)
                        append(",")
                        append("\"versionModel\":")
                        append(safeVersionModel)
                        append("}")
                    }

                val request =
                    Request.Builder()
                        .url("$bridgeBaseUrl/v1/runtime/builds")
                        .post(requestBody.toRequestBody(jsonMediaType))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("User-Agent", "ZionChat-App")
                        .applyAuthToken(config.localBridgeToken)
                        .build()

                val responseJson = executeJson(request)
                val requestId =
                    responseJson.getString("requestId")
                        ?.takeIf { it.isNotBlank() }
                        ?: error("Runtime packaging response is missing requestId")
                val normalizedStatus = normalizeStatus(responseJson.getString("status"))
                val statusUrl =
                    responseJson.getString("statusUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: buildStatusUrl(bridgeBaseUrl, requestId)
                val artifactUrl = responseJson.getString("artifactUrl")
                val artifactName = responseJson.getString("artifactName")
                val errorText = responseJson.getString("error") ?: responseJson.getString("message")

                app.copy(
                    runtimeBuildStatus = normalizedStatus,
                    runtimeBuildRequestId = requestId,
                    runtimeBuildRunId = null,
                    runtimeBuildRunUrl = statusUrl,
                    runtimeBuildArtifactName = artifactName,
                    runtimeBuildArtifactUrl = artifactUrl,
                    runtimeBuildError = if (normalizedStatus == "success") null else errorText,
                    runtimeBuildVersionName = runtimeVersionName,
                    runtimeBuildVersionCode = runtimeVersionCode,
                    runtimeBuildVersionModel = safeVersionModel,
                    runtimeBuildUpdatedAt =
                        responseJson.getLong("updatedAt")
                            ?: System.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun syncRuntimePackaging(app: SavedApp): Result<SavedApp> {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!RuntimeShellPlugin.isInstalled(appContext)) {
                    return@runCatching app.copy(
                        runtimeBuildStatus = "disabled",
                        runtimeBuildError = "Runtime shell template is required. Download it from Apps page.",
                        runtimeBuildUpdatedAt = System.currentTimeMillis()
                    )
                }

                val requestId = app.runtimeBuildRequestId?.trim().orEmpty()
                if (requestId.isBlank()) return@runCatching app

                val bridgeBaseUrl = normalizeBridgeBaseUrl()
                if (bridgeBaseUrl.isBlank()) {
                    return@runCatching app.copy(
                        runtimeBuildStatus = "disabled",
                        runtimeBuildError = "Local runtime packager is not configured",
                        runtimeBuildUpdatedAt = System.currentTimeMillis()
                    )
                }

                val request =
                    Request.Builder()
                        .url(buildStatusUrl(bridgeBaseUrl, requestId))
                        .get()
                        .header("Accept", "application/json")
                        .header("User-Agent", "ZionChat-App")
                        .applyAuthToken(config.localBridgeToken)
                        .build()

                val responseJson =
                    runCatching { executeJson(request) }
                        .getOrElse { throwable ->
                            return@runCatching app.copy(
                                runtimeBuildStatus = "failed",
                                runtimeBuildError =
                                    throwable.message?.trim()?.takeIf { it.isNotBlank() }
                                        ?: "Runtime packaging sync failed",
                                runtimeBuildUpdatedAt = System.currentTimeMillis()
                            )
                        }
                val normalizedStatus = normalizeStatus(responseJson.getString("status"))
                val artifactUrl = responseJson.getString("artifactUrl")
                val artifactName = responseJson.getString("artifactName")
                val errorText = responseJson.getString("error") ?: responseJson.getString("message")
                val statusUrl =
                    responseJson.getString("statusUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: buildStatusUrl(bridgeBaseUrl, requestId)

                app.copy(
                    runtimeBuildStatus = normalizedStatus,
                    runtimeBuildRunUrl = statusUrl,
                    runtimeBuildArtifactName = artifactName ?: app.runtimeBuildArtifactName,
                    runtimeBuildArtifactUrl = artifactUrl ?: app.runtimeBuildArtifactUrl,
                    runtimeBuildError = if (normalizedStatus == "success") null else (errorText ?: app.runtimeBuildError),
                    runtimeBuildVersionName = responseJson.getString("versionName") ?: app.runtimeBuildVersionName,
                    runtimeBuildVersionCode = responseJson.getInt("versionCode") ?: app.runtimeBuildVersionCode,
                    runtimeBuildVersionModel = responseJson.getInt("versionModel") ?: app.runtimeBuildVersionModel,
                    runtimeBuildUpdatedAt =
                        responseJson.getLong("updatedAt")
                            ?: System.currentTimeMillis()
                )
            }
        }
    }

    private fun normalizeBridgeBaseUrl(): String {
        return config.localBridgeBaseUrl.trim().trimEnd('/')
    }

    private fun buildStatusUrl(baseUrl: String, requestId: String): String {
        val encodedRequestId =
            URLEncoder.encode(requestId, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        return "$baseUrl/v1/runtime/builds/$encodedRequestId"
    }

    private fun computeRuntimeVersionCode(appVersionCode: Int, versionModel: Int): Int {
        val model = versionModel.coerceIn(1, 99)
        val appPart = appVersionCode.coerceIn(1, 999_999)
        return model * 1_000_000 + appPart
    }

    private fun computeRuntimeVersionName(appVersionName: String, appVersionCode: Int, versionModel: Int): String {
        val model = versionModel.coerceAtLeast(1)
        val compactAppVersion =
            appVersionName.trim()
                .removePrefix("v")
                .takeIf { it.isNotBlank() }
                ?: appVersionCode.toString()
        return "vm$model.$compactAppVersion"
    }

    private fun buildPackageSuffix(appId: String): String {
        val normalized =
            appId.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .ifBlank { "app" }
        return "zc_${normalized.take(28)}".take(32)
    }

    private fun normalizeStatus(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "queued", "pending", "requested", "waiting" -> "queued"
            "running", "in_progress", "building" -> "in_progress"
            "success", "completed", "ready" -> "success"
            "disabled" -> "disabled"
            "skipped" -> "skipped"
            "failed", "failure", "error", "cancelled" -> "failed"
            else -> "queued"
        }
    }

    private fun Request.Builder.applyAuthToken(token: String): Request.Builder {
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) return this
        return header("X-Zion-Packager-Token", normalizedToken)
    }

    private fun executeJson(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = rawBody.take(300).ifBlank { "No response body" }
                error("Runtime packaging bridge error: HTTP ${response.code}: $reason")
            }
            return runCatching { JsonParser.parseString(rawBody).asJsonObject }
                .getOrElse { error("Runtime packaging bridge returned invalid JSON") }
        }
    }

    private fun JsonObject.getString(key: String): String? {
        return get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.getInt(key: String): Int? {
        return get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asInt
    }

    private fun JsonObject.getLong(key: String): Long? {
        return get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asLong
    }

    private fun escapeJson(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
