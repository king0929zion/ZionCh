package com.zionchat.app.autosoul

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

object AutoSoulShizukuBridge {
    fun isServiceRunning(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    fun hasPermission(): Boolean {
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun isReady(): Boolean = isServiceRunning() && hasPermission()

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    fun openShizukuAppOrWebsite(context: Context) {
        val appContext = context.applicationContext
        val packageName = "moe.shizuku.privileged.api"
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { appContext.startActivity(launchIntent) }
            return
        }
        val webIntent =
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                Uri.parse("https://shizuku.rikka.app/zh-hans/")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(webIntent) }
    }

    fun captureScreenshot(context: Context, timeoutMs: Long = 10_000L): Result<File> {
        return runCatching {
            if (!isReady()) error("Shizuku 未授权或未启动")
            val baseDir = context.getExternalFilesDir(null) ?: error("外部存储不可用")
            val dir = File(baseDir, "autosoul/captures")
            if (!dir.exists() && !dir.mkdirs()) error("创建截图目录失败")
            val file = File(dir, "capture_${System.currentTimeMillis()}.png")
            if (file.exists()) runCatching { file.delete() }

            val cmd = "screencap -p ${escapeShellArg(file.absolutePath)}"
            val process = createProcess(arrayOf("sh", "-c", cmd))
            val finished = waitFor(process, timeoutMs)
            if (!finished) {
                runCatching { process.destroy() }
                error("screencap 超时")
            }
            val exitCode = runCatching { process.exitValue() }.getOrDefault(1)
            if (exitCode != 0 || !file.exists() || file.length() <= 0L) {
                val err =
                    runCatching { process.errorStream.bufferedReader().use { it.readText() } }
                        .getOrDefault("")
                error("screencap 失败：$err")
            }
            file
        }
    }

    private fun createProcess(cmd: Array<String>): Process {
        val method =
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, cmd, null, null) as Process
    }

    private fun waitFor(process: Process, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        Thread {
            runCatching { process.waitFor() }
            latch.countDown()
        }.start()
        return latch.await(timeoutMs.coerceAtLeast(100L), TimeUnit.MILLISECONDS)
    }

    private fun escapeShellArg(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}

