package com.huimao.fairway.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 记分用的加减步进器（记分卡页与修改记录对话框共用）。 */
@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(30.dp),
        )
        IconButton(
            onClick = { onChange((value - 1).coerceAtLeast(0)) },
            enabled = value > 0,
            modifier = Modifier.size(36.dp),
        ) {
            // Remove 不在 material-icons-core 里，用文字减号（和加号对称）
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "增加")
        }
    }
}

/** null -> true -> false -> null 循环（未记录 -> 是 -> 否 -> 未记录） */
fun cycle(v: Boolean?): Boolean? = when (v) {
    null -> true
    true -> false
    false -> null
}

/** 三态 chip：未记录 / ✓ / ✗（开球上道、标准杆上果岭共用）。 */
@Composable
fun TriStateChip(label: String, state: Boolean?, onToggle: () -> Unit) {
    val text = when (state) {
        true -> "$label ✓"
        false -> "$label ✗"
        null -> label
    }
    FilterChip(
        selected = state == true,
        onClick = onToggle,
        label = { Text(text) },
    )
}
