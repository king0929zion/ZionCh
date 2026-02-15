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
    private const val minTemplateSizeBytes = 4 * 1024L
    private const val prefsName = "runtime_shell_template"
    private const val keyDownloadId = "download_id"
    private const val keyTemplateReady = "template_ready"

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
        val readyByFile = templateFile(context)?.let(::isValidTemplateFile) == true
        if (readyByFile) {
            markTemplateReady(context, true)
            return true
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        val downloadId = getSavedDownloadId(context)
        if (manager != null && downloadId > 0L) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            runCatching {
                manager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex < 0) return@use
                    val status = cursor.getInt(statusIndex)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            markTemplateReady(context, true)
                            return true
                        }
                        DownloadManager.STATUS_FAILED -> {
                            markTemplateReady(context, false)
                        }
                    }
                }
            }
        }

        return downloadId > 0L && isTemplateMarkedReady(context)
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
            val downloadId = manager.enqueue(request)
            saveDownloadId(context, downloadId)
            markTemplateReady(context, false)
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

    private fun isValidTemplateFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() >= minTemplateSizeBytes
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun getSavedDownloadId(context: Context): Long {
        return prefs(context).getLong(keyDownloadId, -1L)
    }

    private fun saveDownloadId(context: Context, downloadId: Long) {
        prefs(context).edit().putLong(keyDownloadId, downloadId).apply()
    }

    private fun isTemplateMarkedReady(context: Context): Boolean {
        return prefs(context).getBoolean(keyTemplateReady, false)
    }

    private fun markTemplateReady(context: Context, ready: Boolean) {
        prefs(context).edit().putBoolean(keyTemplateReady, ready).apply()
    }
}
