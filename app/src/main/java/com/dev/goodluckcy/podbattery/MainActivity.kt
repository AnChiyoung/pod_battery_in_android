package com.dev.goodluckcy.podbattery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.dev.goodluckcy.podbattery.bluetooth.ble.PodScanner
import com.dev.goodluckcy.podbattery.data.PodStatusRepository
import com.dev.goodluckcy.podbattery.ui.LicensesDialog
import com.dev.goodluckcy.podbattery.ui.theme.PodBatteryTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PodStatusRepository.load(this)
        setContent {
            PodBatteryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 블루투스를 껐다 켜는 등으로 스캔이 풀렸을 수 있어 앱을 열 때마다 다시 건다.
        if (PodStatusRepository.isEnabled(this)) PodScanner.start(this)
    }
}

private val requiredPermissions: Array<String> = buildList {
    addAll(PodScanner.requiredPermissions)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status by PodStatusRepository.status.collectAsStateWithLifecycle()
    val enabled by PodStatusRepository.enabled.collectAsStateWithLifecycle()

    fun allGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(allGranted()) }
    fun turnOn() {
        PodStatusRepository.setEnabled(context, true)
        PodScanner.start(context)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = allGranted()
        if (granted) turnOn()
    }

    var showLicenses by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("PodBattery", style = MaterialTheme.typography.headlineMedium)

        StatusCard(status)

        when {
            !granted -> Button(
                onClick = { launcher.launch(requiredPermissions) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("근처 기기·알림 권한 허용") }

            !enabled -> Button(
                onClick = { turnOn() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("배터리 확인 켜기") }

            else -> OutlinedButton(
                onClick = {
                    PodScanner.stop(context)
                    PodStatusRepository.setEnabled(context, false)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("배터리 확인 끄기") }
        }

        HowToCard()

        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = { showLicenses = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("오픈소스 라이선스", style = MaterialTheme.typography.bodySmall)
        }
        if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun StatusCard(status: PodsStatus?) {
    // "n분 전" 표시를 1분마다 갱신
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = status?.model ?: "아직 확인한 배터리가 없어요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (status != null && status.updatedAt > 0) {
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        status.updatedAt, now, DateUtils.MINUTE_IN_MILLIS,
                    ).toString() + " 확인",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BatteryItem("왼쪽", status?.left, status?.leftCharging == true)
                BatteryItem("오른쪽", status?.right, status?.rightCharging == true)
                BatteryItem("케이스", status?.case, status?.caseCharging == true)
            }
        }
    }
}

@Composable
private fun HowToCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("배터리 확인 방법", fontWeight = FontWeight.SemiBold)
            Text("1. 이어폰을 케이스에 넣고 뚜껑을 열어 주세요.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "2. 케이스 앞면을 두 번 톡톡 두드리세요. 뒷면에 버튼이 있는 모델은 버튼을 길게 누르세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "3. 표시등이 흰색으로 깜빡이면 몇 초 안에 배터리가 표시되고, 잠금화면 알림에도 남아요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "앱을 닫아 두어도 확인돼요. 이어폰이 페어링 모드일 때만 정확한 배터리를 알려 주기 때문에 이 동작이 필요해요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        StatusCard(PodsStatus("Pod", 80, 90, 50, false, false, true, System.currentTimeMillis()))
    }
}
