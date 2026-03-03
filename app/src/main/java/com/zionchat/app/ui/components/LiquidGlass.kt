package com.zionchat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = { onCheckedChange() },
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFF111111),
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFD1D5DB)
        )
    )
}
