package com.zionchat.app.autosoul

import android.content.Context
import android.provider.Settings

object AutoSoulAccessibilityStatus {
    fun isServiceEnabled(context: Context): Boolean {
        val enabledServices =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
        val expected = "${context.packageName}/${AutoSoulAccessibilityService::class.java.name}"
        return enabledServices.contains(expected)
    }
}

