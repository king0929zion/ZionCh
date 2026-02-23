package com.zionchat.app.autosoul

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class AutoSoulPermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
    val notificationGranted: Boolean
)

object AutoSoulPermissions {
    fun snapshot(context: Context): AutoSoulPermissionSnapshot {
        val notificationGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        val shizukuRunning = AutoSoulShizukuBridge.isServiceRunning()
        val shizukuGranted = AutoSoulShizukuBridge.hasPermission()
        return AutoSoulPermissionSnapshot(
            accessibilityEnabled = AutoSoulAccessibilityStatus.isServiceEnabled(context),
            overlayEnabled = Settings.canDrawOverlays(context),
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuRunning && shizukuGranted,
            notificationGranted = notificationGranted
        )
    }

    fun openAccessibilitySettings(context: Context): Result<Unit> {
        return runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openOverlaySettings(context: Context): Result<Unit> {
        return runCatching {
            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openNotificationSettings(context: Context): Result<Unit> {
        return runCatching {
            val intent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.recoverCatching {
            throw ActivityNotFoundException("无法打开通知设置")
        }
    }
}

