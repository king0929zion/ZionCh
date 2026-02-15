package com.zionchat.app.data

import android.content.Context
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.zionchat.app.BuildConfig
import java.io.File

object RuntimeShellPlugin {
    private const val fallbackPackageName = "com.zionchat.runtime.core_runtime_shell"
    private const val fallbackDownloadUrl =
        "https://github.com/king0929zion/core/releases/latest/download/runtime-release-unsigned.apk"
    private const val fallbackTemplateFileName = "runtime-shell-template.apk"
    private const val minTemplateSizeBytes = 128 * 1024L

    fun packageName(): String {
        return BuildConfig.RUNTIME_SHELL_PLUGIN_PACKAGE.trim().ifBlank { fallbackPackageName }
    }

    fun downloadUrl(): String {
        return BuildConfig.RUNTIME_SHELL_PLUGIN_DOWNLOAD_URL.trim().ifBlank { fallbackDownloadUrl }
    }

    fun templateFileName(): String {
        val fromUrl =
            runCatching { Uri.parse(downloadUrl()).lastPathSegment.orEmpty() }
                .getOrNull()
                ?.trim()
                .orEmpty()
        if (fromUrl.endsWith(".apk", ignoreCase = true)) {
            return sanitizeFileName(fromUrl)
        }
        return fallbackTemplateFileName
    }

    fun templateFile(context: Context): File? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(dir, templateFileName())
    }

    fun isInstalled(context: Context): Boolean {
        val file = templateFile(context) ?: return false
        return file.exists() && file.isFile && file.length() >= minTemplateSizeBytes
    }

    fun openDownloadPage(context: Context): Boolean {
        if (isInstalled(context)) return true
        val queued = enqueueTemplateDownload(context)
        if (queued) return true

        val uri = runCatching { Uri.parse(downloadUrl()) }.getOrNull() ?: return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrElse { false }
    }

    private fun enqueueTemplateDownload(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return false
        val uri = runCatching { Uri.parse(downloadUrl()) }.getOrNull() ?: return false
        val request =
            DownloadManager.Request(uri)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType("application/vnd.android.package-archive")
                .setTitle("Runtime shell template")
                .setDescription("Downloading runtime shell template APK")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    templateFileName()
                )

        return runCatching {
            templateFile(context)?.takeIf { it.exists() }?.delete()
            manager.enqueue(request)
            true
        }.getOrElse { false }
    }

    private fun sanitizeFileName(raw: String): String {
        val normalized =
            raw.replace(Regex("""[^\w.\-]"""), "_")
                .trim('_')
                .ifBlank { fallbackTemplateFileName }
        return if (normalized.endsWith(".apk", ignoreCase = true)) normalized else "$normalized.apk"
    }
}
