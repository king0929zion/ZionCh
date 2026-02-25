package com.zionchat.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun ChatScreen(navController: NavController) {
    ChatScreenContent(navController = navController, forcedGroupId = null)
}

@Composable
fun GroupChatScreen(navController: NavController, groupId: String?) {
    ChatScreenContent(navController = navController, forcedGroupId = groupId)
}
