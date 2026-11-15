package com.dev.goodluckcy.podbattery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.goodluckcy.podbattery.bluetooth.PodsStatus
import com.dev.goodluckcy.podbattery.data.PodStatusRepository
import com.dev.goodluckcy.podbattery.service.PodMonitorService
import com.dev.goodluckcy.podbattery.ui.theme.PodBatteryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodBatteryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private val requiredPermissions: Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status by PodStatusRepository.status.collectAsStateWithLifecycle()
    val monitoring by PodStatusRepository.monitoring.collectAsStateWithLifecycle()

    fun allGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(allGranted()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = allGranted()
        if (granted) PodMonitorService.start(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("PodBattery", style = MaterialTheme.typography.headlineMedium)

        StatusCard(status, monitoring)

        when {
            !granted -> Button(
                onClick = { launcher.launch(requiredPermissions) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("블루투스·알림 권한 허용") }

            !monitoring -> Button(
                onClick = { PodMonitorService.start(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("모니터링 시작") }

            else -> OutlinedButton(
                onClick = { PodMonitorService.stop(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("모니터링 중지") }
        }

        Text(
            "에어팟이 연결되면 자동으로 시작되고, 잠금화면 알림에 배터리가 표시됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(status: PodsStatus?, monitoring: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = status?.model ?: if (monitoring) "에어팟에 연결하는 중…" else "모니터링 꺼짐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BatteryItem("왼쪽", status?.left, status?.leftCharging == true)
                BatteryItem("오른쪽", status?.right, status?.rightCharging == true)
                BatteryItem(
                    if (status?.caseStale == true) "케이스(이전)" else "케이스",
                    status?.case,
                    status?.caseCharging == true,
                )
            }
        }
    }
}

@Composable
private fun BatteryItem(label: String, value: Int?, charging: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.let { "$it%" } ?: "--",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = label + if (charging) " ⚡" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusCardPreview() {
    PodBatteryTheme {
        StatusCard(
            PodsStatus("AirPods Pro (2세대)", 80, 90, 50, false, false, true),
            monitoring = true,
        )
    }
}
