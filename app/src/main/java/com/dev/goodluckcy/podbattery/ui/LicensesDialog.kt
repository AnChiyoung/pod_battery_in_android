package com.dev.goodluckcy.podbattery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dev.goodluckcy.podbattery.R

private data class Library(val name: String, val copyright: String)

// 앱에 포함되는 라이브러리. 모두 Apache License 2.0.
private val libraries = listOf(
    Library("AndroidX (Core, Activity, Lifecycle, Compose, Material 3)", "Copyright The Android Open Source Project"),
    Library("Kotlin Standard Library, kotlinx.coroutines", "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors"),
)

@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showFullText by remember { mutableStateOf(false) }
    val licenseText = remember {
        context.resources.openRawResource(R.raw.apache_license_2_0)
            .bufferedReader().use { it.readText() }
            // 원문은 80자마다 줄이 바뀌어 있어 화면 폭에서 들쭉날쭉하다. 문단 안의 줄바꿈은 이어 붙인다.
            .split(Regex("\\n\\s*\\n"))
            .joinToString("\n\n") { para -> para.lines().joinToString(" ") { it.trim() }.trim() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오픈소스 라이선스") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                libraries.forEach { lib ->
                    Text(lib.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${lib.copyright}\nApache License 2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (showFullText) {
                    Text(licenseText, style = MaterialTheme.typography.bodySmall)
                } else {
                    TextButton(onClick = { showFullText = true }) { Text("Apache License 2.0 전문 보기") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}
