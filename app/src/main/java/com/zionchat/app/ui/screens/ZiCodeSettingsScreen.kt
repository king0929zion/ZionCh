package com.zionchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zionchat.app.LocalZiCodeGitHubService
import com.zionchat.app.LocalZiCodeRepository
import com.zionchat.app.ui.components.PageTopBarContentTopPadding
import com.zionchat.app.ui.components.SettingsPage
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.SourceSans3
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.zicode.data.ZiCodeSettings
import kotlinx.coroutines.launch

private data class ZiCodeCapability(
    val title: String,
    val description: String,
    val active: Boolean
)

@Composable
fun ZiCodeSettingsScreen(navController: NavController) {
    val repository = LocalZiCodeRepository.current
    val gitHubService = LocalZiCodeGitHubService.current
    val settings by repository.settingsFlow.collectAsState(initial = ZiCodeSettings())
    val scope = rememberCoroutineScope()

    var tokenText by rememberSaveable(settings.githubToken) { mutableStateOf(settings.githubToken) }
    var validating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    val capabilities =
        remember {
            listOf(
                ZiCodeCapability("仓库列表", "读取当前账号可访问仓库，并按最近访问优先排序。", true),
                ZiCodeCapability("项目会话", "同一仓库支持多会话，围绕项目持续对话。", true),
                ZiCodeCapability("工具状态流", "每轮任务都展示工具调用、日志和运行中 shimmer。", true),
                ZiCodeCapability("文件树与预览", "支持查看多层目录，并在底部面板预览文件。", true),
                ZiCodeCapability("新建仓库", "直接用 GitHub Token 创建新仓库并进入会话。", true),
                ZiCodeCapability("工作流检查", "读取 `.github/workflows`，帮助判断构建与发布入口。", true),
                ZiCodeCapability("工作流触发", "下一步会接入真实 dispatch 执行。", false),
                ZiCodeCapability("仓库写入", "下一步会接入文件创建/编辑型 Agent 操作。", false)
            )
        }

    SettingsPage(
        title = "ZiCode 设置",
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = PageTopBarContentTopPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZiCodeSectionTitle("GitHub")
            ZiCodePanel {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("GitHub Token") },
                        placeholder = { Text("ghp_xxx / github_pat_xxx") },
                        singleLine = true,
                        colors = ziCodeFieldColors()
                    )
                    settings.viewer?.let { viewer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .background(Color.White, CircleShape)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = AppIcons.GitHub,
                                    contentDescription = null,
                                    tint = TextPrimary
                                )
                            }
                            Column {
                                Text(
                                    text = viewer.displayName ?: viewer.login,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = SourceSans3
                                )
                                ZiCodeMetaText(text = "@${viewer.login}")
                            }
                        }
                    }
                    statusText?.takeIf { it.isNotBlank() }?.let {
                        ZiCodeMetaText(text = it)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    repository.setGitHubToken(tokenText)
                                    statusText = "Token 已保存。"
                                }
                            }
                        ) {
                            Text("保存", color = TextPrimary)
                        }
                        TextButton(
                            enabled = !validating,
                            onClick = {
                                scope.launch {
                                    val token = tokenText.trim()
                                    if (token.isBlank()) {
                                        statusText = "请先输入 Token。"
                                        return@launch
                                    }
                                    validating = true
                                    gitHubService.fetchViewer(token)
                                        .onSuccess { viewer ->
                                            repository.setGitHubToken(token)
                                            repository.updateViewer(viewer)
                                            statusText = "已连接到 GitHub 账号 ${viewer.login}。"
                                        }
                                        .onFailure { throwable ->
                                            statusText = throwable.message?.trim().orEmpty().ifBlank { "Token 校验失败。" }
                                        }
                                    validating = false
                                }
                            }
                        ) {
                            Text(if (validating) "校验中…" else "校验连接", color = TextPrimary)
                        }
                    }
                }
            }

            ZiCodeSectionTitle("Tools")
            ZiCodePanel {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    capabilities.forEach { capability ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ZiCodeMiniStatusBadge(text = if (capability.active) "Live" else "Next")
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = capability.title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = SourceSans3
                                )
                                ZiCodeMetaText(text = capability.description)
                            }
                        }
                    }
                }
            }

            ZiCodeSectionTitle("说明")
            ZiCodePanel {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZiCodePanelGray, RoundedCornerShape(ZiCodeInnerRadius))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ZiCode 是一个以对话为核心的 GitHub Agent 模块。",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SourceSans3
                    )
                    ZiCodeMetaText(text = "手机端负责发起目标、查看执行过程、查看文件与结果；GitHub 负责承载真实仓库和工作流能力。")
                    ZiCodeMetaText(text = "当前版本先把仓库、会话、文件树、工具状态可视化和新建仓库打通，后续会继续接入工作流 dispatch 与文件写入。")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
