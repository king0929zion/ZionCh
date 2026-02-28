package com.zionchat.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.zionchat.app.BuildConfig
import com.zionchat.app.R
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionName = BuildConfig.VERSION_NAME
    
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateInfo?>(null) }

    SettingsPage(
        title = stringResource(R.string.about_title),
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
                // App Logo and Version
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = R.drawable.ic_app_logo,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SourceSans3,
                        color = TextPrimary
                    )
                    
                    Text(
                        text = versionName,
                        fontSize = 15.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // About Items
                AboutGroup(title = stringResource(R.string.about_group_info)) {
                    AboutItem(
                        icon = { Icon(AppIcons.Globe, null, Modifier.size(22.dp), tint = TextPrimary) },
                        label = stringResource(R.string.about_official_website),
                        value = stringResource(R.string.about_coming_soon),
                        showChevron = false,
                        showDivider = true,
                        onClick = { }
                    )
                    AboutItem(
                        icon = { Icon(AppIcons.Info, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.about_check_update),
                        value = if (isCheckingUpdate) stringResource(R.string.about_checking) else null,
                        showChevron = !isCheckingUpdate,
                        showDivider = true,
                        onClick = {
                            if (!isCheckingUpdate) {
                                scope.launch {
                                    isCheckingUpdate = true
                                    val updateInfo = checkGitHubRelease()
                                    isCheckingUpdate = false
                                    if (updateInfo != null) {
                                        showUpdateDialog = updateInfo
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                R.string.about_no_update,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    )
                    AboutItem(
                        icon = { Icon(AppIcons.GitHub, null, Modifier.size(22.dp), tint = TextPrimary) },
                        label = stringResource(R.string.about_github),
                        showChevron = true,
                        showDivider = false,
                        onClick = { openGitHub(context) }
                    )
                }

                AboutGroup(title = stringResource(R.string.about_group_community)) {
                    AboutItem(
                        icon = { Icon(AppIcons.QQ, null, Modifier.size(22.dp), tint = Color.Unspecified) },
                        label = stringResource(R.string.about_qq_group),
                        value = "1079727831",
                        showChevron = true,
                        showDivider = false,
                        onClick = { joinQQGroup(context, "1079727831") }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

    // Update Dialog
    showUpdateDialog?.let { updateInfo ->
        AlertDialog(
            onDismissRequest = { showUpdateDialog = null },
            title = {
                Text(
                    text = stringResource(R.string.about_update_available),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SourceSans3,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.about_new_version, updateInfo.version),
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    if (updateInfo.changelog.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateInfo.changelog,
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = null
                        downloadApk(context, updateInfo.downloadUrl)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.about_download),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = null }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = TextSecondary
                    )
                }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun AboutGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SourceSans3,
            color = Color(0xFF6B6B6B),
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
            .shadow(0.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun AboutItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (isPressed) Color(0xFFE5E5EA) else Color(0xFFF1F1F1))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SourceSans3,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            if (value != null) {
                Text(
                    text = value,
                    fontSize = 15.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (showChevron) {
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDivider) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

private data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String
)

private suspend fun checkGitHubRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/king0929zion/ZionChat/releases/latest")
        val connection = url.openConnection()
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        
        val tagName = json.optString("tag_name", "").removePrefix("v")
        val currentVersion = BuildConfig.VERSION_NAME
        
        if (tagName.isNotBlank() && isNewerVersion(tagName, currentVersion)) {
            val body = json.optString("body", "")
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            
            if (downloadUrl.isNotBlank()) {
                UpdateInfo(tagName, body, downloadUrl)
            } else null
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
    return try {
        val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(newParts.size, currentParts.size)) {
            val newPart = newParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            when {
                newPart > currentPart -> return true
                newPart < currentPart -> return false
            }
        }
        false
    } catch (e: Exception) {
        false
    }
}

private fun openGitHub(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/king0929zion/ZionChat"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, R.string.about_github_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun downloadApk(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, R.string.about_download_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun joinQQGroup(context: Context, groupId: String) {
    // 尝试多种 QQ 加群方案
    val schemes = listOf(
        // QQ 手机版原生协议 - 直接打开群资料页
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$groupId&card_type=group&source=qrcode",
        // QQ 国际版
        "mqqwpa://im/chat?chat_type=group&uin=$groupId&version=1&src_type=web",
        // QQ 轻聊版
        "mqq://card/show_pslcard?uin=$groupId&card_type=group",
        // QQ 加群网页版
        "https://qm.qq.com/cgi-bin/qm/qr?k=$groupId&jump_from=webapi",
        // 备用网页链接
        "https://qm.qq.com/q/$groupId"
    )
    
    for (scheme in schemes) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            continue
        }
    }
    
    // 所有方案都失败，复制群号到剪贴板并提示
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("QQ Group", groupId)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "QQ Group ID copied: $groupId", Toast.LENGTH_LONG).show()
}
