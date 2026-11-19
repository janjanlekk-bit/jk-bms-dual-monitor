package com.jkbms.dualmonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkbms.dualmonitor.ble.BmsConnectionManager
import com.jkbms.dualmonitor.model.BmsData
import com.jkbms.dualmonitor.model.CellData
import com.jkbms.dualmonitor.model.ConnectionStatus
import com.jkbms.dualmonitor.model.DailyEnergyRecord
import com.jkbms.dualmonitor.model.TotalBankData
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.jkbms.dualmonitor.network.GatewayConnectionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: BmsConnectionManager,
    onInspectCells: (BmsData) -> Unit,
    onViewHistory: () -> Unit = {}
) {
    val b1 by manager.bms1.bmsState.collectAsState()
    val b2 by manager.bms2.bmsState.collectAsState()
    val bank by manager.totalBankState.collectAsState()
    val todayEnergy by manager.energyHistory.todayEnergy.collectAsState()
    val historyList by manager.energyHistory.historyList.collectAsState()
    val blePaused by manager.blePaused.collectAsState()
    val bleRemainingSec by manager.bleRemainingSec.collectAsState()
    val isGatewayConnected by manager.isGatewayMode.collectAsState()
    val connectionMode by manager.gatewayClient.connectionMode.collectAsState()
    val gatewayIp by manager.gatewayClient.gatewayIp.collectAsState()
    val firebaseUrl by manager.gatewayClient.firebaseUrl.collectAsState()
    val firebaseAuth by manager.gatewayClient.firebaseAuth.collectAsState()
    val telemetryTimestamp by manager.telemetryTimestamp.collectAsState()
    val watchdogOfflineSec by manager.watchdogOfflineSec.collectAsState()

    var showGatewaySettingsDialog by remember { mutableStateOf(false) }
    var showRebootConfirmDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                manager.refreshConnection()
                delay(800)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showGatewaySettingsDialog = true }
                    .padding(vertical = 4.dp)
            ) {
                Text("JK DUAL MONITOR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                when (connectionMode) {
                                    GatewayConnectionMode.LOCAL_WIFI -> Color(0xFF00E676)
                                    GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF29B6F6)
                                    GatewayConnectionMode.OFFLINE -> Color(0xFFFF5252)
                                },
                                CircleShape
                            )
                    )
                    Text(
                        text = when (connectionMode) {
                            GatewayConnectionMode.LOCAL_WIFI -> "ESP32 WI-FI GATEWAY (LOCAL)"
                            GatewayConnectionMode.FIREBASE_CLOUD -> "ESP32 CLOUD ONLINE (FIREBASE)"
                            GatewayConnectionMode.OFFLINE -> "ESP32 GATEWAY OFFLINE (UNREACHABLE)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (connectionMode) {
                            GatewayConnectionMode.LOCAL_WIFI -> Color(0xFF00E676)
                            GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF29B6F6)
                            GatewayConnectionMode.OFFLINE -> Color(0xFFFF5252)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onViewHistory) {
                    Text("📜", fontSize = 18.sp)
                }
                IconButton(onClick = { showGatewaySettingsDialog = true }) {
                    Text("⚙️", fontSize = 18.sp)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141C26)),
            border = BorderStroke(1.dp, Color(0xFF263238)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatusBadge(label = "BATTERY 1", status = b1.connectionStatus)
                StatusBadge(label = "BATTERY 2", status = b2.connectionStatus)
            }
        }

        // Auto-Heal & Cloud Recovery Card when batteries are offline
        val bothBatteriesOffline = b1.connectionStatus != ConnectionStatus.CONNECTED && b2.connectionStatus != ConnectionStatus.CONNECTED
        if (bothBatteriesOffline && connectionMode != GatewayConnectionMode.OFFLINE) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF251515)),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⚠️", fontSize = 14.sp)
                        Text(
                            text = if (watchdogOfflineSec > 0) {
                                "Batteries Offline (${watchdogOfflineSec}s / 180s Auto-Heal)"
                            } else {
                                "Batteries Offline • Auto-Heal Active"
                            },
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "Bluetooth link to the JK BMS dropped. The gateway will auto-reboot after 3 minutes offline, or you can trigger an instant cloud recovery below:",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { manager.forceReconnectBle() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("⚡ RECONNECT BLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Button(
                            onClick = { showRebootConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("🔄 REBOOT ESP32", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Stale Cloud Data Warning
        val nowSec = System.currentTimeMillis() / 1000
        val isCloudMode = connectionMode == GatewayConnectionMode.FIREBASE_CLOUD
        val telemetryAgeSec = if (telemetryTimestamp > 0) (nowSec - telemetryTimestamp).coerceAtLeast(0) else 0L
        if (isCloudMode && telemetryAgeSec > 35) {
            val ageText = when {
                telemetryAgeSec < 60 -> "${telemetryAgeSec}s ago"
                telemetryAgeSec < 3600 -> "${telemetryAgeSec / 60}m ago"
                telemetryAgeSec < 86400 -> "${telemetryAgeSec / 3600}h ago"
                else -> "${telemetryAgeSec / 86400}d ago"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF332005), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚠️ Stale cloud telemetry (from $ageText)",
                    color = Color(0xFFFFB74D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "REBOOT GATEWAY",
                    color = Color(0xFF4FC3F7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showRebootConfirmDialog = true }
                )
            }
        }

        TotalBankCard(bank)

        TodayEnergyCard(
            record = todayEnergy,
            historyList = historyList,
            bank = bank,
            onViewHistory = onViewHistory
        )

        CombinedBatteryPacksCard(
            b1 = b1,
            b2 = b2,
            onInspectB1 = { onInspectCells(b1) },
            onInspectB2 = { onInspectCells(b2) }
        )

        // ESP32 Wi-Fi Gateway Card & Bluetooth Release Control placed at the VERY BOTTOM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showGatewaySettingsDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = when {
                    connectionMode == GatewayConnectionMode.OFFLINE -> Color(0xFF2E1A1A)
                    blePaused -> Color(0xFF3E2723)
                    connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF0F1E2E)
                    else -> Color(0xFF14241A)
                }
            ),
            border = BorderStroke(
                1.dp,
                when {
                    connectionMode == GatewayConnectionMode.OFFLINE -> Color(0xFFFF5252).copy(alpha = 0.5f)
                    blePaused -> Color(0xFFFF5722)
                    connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF29B6F6).copy(alpha = 0.5f)
                    else -> Color(0xFF00E676).copy(alpha = 0.3f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier.size(8.dp).background(
                                when {
                                    connectionMode == GatewayConnectionMode.OFFLINE -> Color(0xFFFF5252)
                                    blePaused -> Color(0xFFFF5722)
                                    connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF29B6F6)
                                    else -> Color(0xFF00E676)
                                },
                                CircleShape
                            )
                        )
                        Text(
                            text = when {
                                connectionMode == GatewayConnectionMode.OFFLINE -> "ESP32 GATEWAY OFFLINE"
                                blePaused -> "BLE RELEASED (${bleRemainingSec / 60}:${String.format("%02d", bleRemainingSec % 60)})"
                                connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> "ESP32 CLOUD SYNCED (FIREBASE)"
                                else -> "ESP32 AUTONOMOUS GATEWAY"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                connectionMode == GatewayConnectionMode.OFFLINE -> Color(0xFFFF8A80)
                                blePaused -> Color(0xFFFFAB91)
                                connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF81D4FA)
                                else -> Color(0xFFA5D6A7)
                            }
                        )
                    }
                    Text(
                        text = when {
                            connectionMode == GatewayConnectionMode.OFFLINE -> "Unreachable over Wi-Fi • Check ESP32 power"
                            blePaused -> "Official JK app can connect now"
                            connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> "Remote Monitoring Active • Firebase RTDB"
                            else -> "24/7 Logging Active • Local Wi-Fi Mode"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
                if (blePaused) {
                    Button(
                        onClick = { manager.resumeBle() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("RESUME", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else if (connectionMode != GatewayConnectionMode.OFFLINE) {
                    OutlinedButton(
                        onClick = { manager.releaseBle(600) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("RELEASE BLE (10m)", color = Color(0xFF90CAF9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

    if (showGatewaySettingsDialog) {
        GatewaySettingsDialog(
            currentIp = gatewayIp,
            currentFirebaseUrl = firebaseUrl,
            currentFirebaseAuth = firebaseAuth,
            connectionMode = connectionMode,
            onForceReconnect = {
                manager.forceReconnectBle()
                showGatewaySettingsDialog = false
            },
            onReboot = {
                showGatewaySettingsDialog = false
                showRebootConfirmDialog = true
            },
            onSave = { newIp, newFirebaseUrl, newFirebaseAuth ->
                manager.gatewayClient.setGatewayIp(newIp)
                manager.gatewayClient.setFirebaseUrl(newFirebaseUrl)
                manager.gatewayClient.setFirebaseAuth(newFirebaseAuth)
            },
            onDismiss = { showGatewaySettingsDialog = false }
        )
    }

    if (showRebootConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRebootConfirmDialog = false },
            title = { Text("Reboot ESP32 Gateway?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This sends a reboot command to the ESP32. It will restart cleanly and reconnect to Wi-Fi and Bluetooth automatically within 10 seconds.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        manager.rebootGateway()
                        showRebootConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("REBOOT NOW", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirmDialog = false }) {
                    Text("CANCEL", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E232E)
        )
    }
}

@Composable
fun StatusBadge(label: String, status: ConnectionStatus) {
    val (dotColor, text) = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF00E676) to "ONLINE"
        ConnectionStatus.CONNECTING, ConnectionStatus.DISCOVERING_SERVICES, ConnectionStatus.ENABLING_NOTIFICATIONS -> Color(0xFFFFD600) to "CONNECTING"
        ConnectionStatus.RECONNECTING -> Color(0xFFFF9100) to "RECONNECTING"
        else -> Color(0xFFFF1744) to "OFFLINE"
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("●", color = dotColor, fontSize = 12.sp)
        Text("$label: $text", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun TotalBankCard(bank: TotalBankData) {
    val isCharging = bank.power > 0.5f || bank.current > 0.05f
    val isDischarging = bank.power < -0.5f || bank.current < -0.05f
    val absPower = kotlin.math.abs(bank.power)

    val totalPowerDisplay = when {
        absPower < 0.5f -> "0 W"
        absPower < 1000f -> {
            val formatted = String.format(Locale.US, "%.0f W", absPower)
            if (isCharging) "+$formatted" else if (isDischarging) "-$formatted" else formatted
        }
        else -> {
            val formatted = String.format(Locale.US, "%.2f kW", absPower / 1000f)
            if (isCharging) "+$formatted" else if (isDischarging) "-$formatted" else formatted
        }
    }

    val powerColor = when {
        isCharging -> Color(0xFF00E676)   // Green (Solar charging)
        isDischarging -> Color(0xFFFF9100) // Orange (Load discharging)
        else -> Color.White
    }

    val currentColor = when {
        isCharging -> Color(0xFF00E676)
        isDischarging -> Color(0xFFFF9100)
        else -> Color.White
    }

    val totalCurrentDisplay = when {
        kotlin.math.abs(bank.current) < 0.05f -> "0.0 A"
        isCharging -> String.format(Locale.US, "+%.1f A", kotlin.math.abs(bank.current))
        isDischarging -> String.format(Locale.US, "-%.1f A", kotlin.math.abs(bank.current))
        else -> String.format(Locale.US, "%.1f A", bank.current)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14243B)),
        border = BorderStroke(1.dp, Color(0xFF2979FF).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TOTAL BATTERY BANK", style = MaterialTheme.typography.titleMedium, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(
                    label = "BUS VOLTAGE",
                    value = String.format(Locale.US, "%.2f V", bank.voltage),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                )
                MetricItem(
                    label = "TOTAL CURRENT",
                    value = totalCurrentDisplay,
                    valueColor = currentColor,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(
                    label = "TOTAL POWER",
                    value = totalPowerDisplay,
                    valueColor = powerColor,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                )
                MetricItem(
                    label = "REMAINING CAPACITY",
                    value = String.format(Locale.US, "%.1f Ah", bank.remainingCapacityAh),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(
                    label = "WEIGHTED SOC",
                    value = "${bank.capacityWeightedSoc} %",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                )
                val totalEnergyKwh = (bank.voltage * bank.remainingCapacityAh) / 1000f
                MetricItem(
                    label = "STORED ENERGY",
                    value = String.format(Locale.US, "%.2f kWh", totalEnergyKwh),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                )
            }
        }
    }
}

@Composable
fun TodayEnergyCard(
    record: DailyEnergyRecord,
    historyList: List<DailyEnergyRecord> = emptyList(),
    bank: TotalBankData = TotalBankData(),
    onViewHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13222B)),
        border = BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header (without top button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val todayCalStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
                val isNightCycle = record.date.isNotBlank() && record.date != todayCalStr
                val displayDateText = if (isNightCycle) {
                    "${formatDisplayDate(record.date)} • Overnight Load Cycle"
                } else {
                    formatDisplayDate(record.date.ifBlank { "Today" })
                }
                Column {
                    Text(
                        if (isNightCycle) "CYCLE ENERGY YIELD & LOAD" else "TODAY'S ENERGY YIELD & LOAD",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF64FFDA),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        displayDateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNightCycle) Color(0xFFFFB74D) else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Charged (Solar) vs Discharged (Load)
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text("SOLAR CHARGED", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        formatEnergy(record.chargedKwh, "+"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        String.format("+%.1f Ah", record.chargedAh),
                        fontSize = 12.sp,
                        color = Color(0xFFA5D6A7),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("LOAD CONSUMED", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.End)
                    Text(
                        formatEnergy(record.dischargedKwh, "-"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9100),
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    )
                    Text(
                        String.format("-%.1f Ah", record.dischargedAh),
                        fontSize = 12.sp,
                        color = Color(0xFFFFCC80),
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E353B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Daily SOC Range & Battery Capacity Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text("DAILY SOC RANGE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    val socText = if (record.minSoc > 0 || record.maxSoc > 0) {
                        "${record.minSoc}% → ${record.maxSoc}%"
                    } else "—"
                    Text(
                        socText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                val totalCap = when {
                    bank.nominalCapacityAh > 10f -> bank.nominalCapacityAh
                    bank.remainingCapacityAh > 0f && bank.capacityWeightedSoc > 0 -> {
                        bank.remainingCapacityAh / (bank.capacityWeightedSoc / 100f)
                    }
                    else -> 200f
                }
                val displayMinAh = when {
                    record.minAh > 0.05f -> record.minAh
                    record.minSoc > 0 -> (record.minSoc / 100f) * totalCap
                    else -> 0f
                }
                val displayMaxAh = when {
                    record.maxAh > 0.05f -> record.maxAh
                    record.maxSoc > 0 -> (record.maxSoc / 100f) * totalCap
                    else -> 0f
                }

                val ahText = when {
                    displayMinAh > 0.05f && displayMaxAh > 0.05f -> {
                        if (kotlin.math.abs(displayMaxAh - displayMinAh) > 0.05f) {
                            String.format(Locale.US, "%.1f → %.1f Ah", displayMinAh, displayMaxAh)
                        } else {
                            String.format(Locale.US, "%.1f Ah", displayMinAh)
                        }
                    }
                    displayMaxAh > 0.05f -> String.format(Locale.US, "%.1f Ah", displayMaxAh)
                    displayMinAh > 0.05f -> String.format(Locale.US, "%.1f Ah", displayMinAh)
                    else -> "—"
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("CAPACITY RANGE", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.End)
                    Text(
                        ahText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCFD8DC),
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF1E353B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Month Bar Graph Preview
            val monthDays = remember(record, historyList) {
                buildMonthDays(historyList, record)
            }

            Text(
                "${getMonthHeaderTitle()} — DAILY BAR GRAPH",
                fontSize = 11.sp,
                color = Color(0xFF80CBC4),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            MonthlyEnergyBarChart(
                monthDays = monthDays,
                selectedDate = record.date,
                onSelectDate = { onViewHistory() },
                compact = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Button to open full history & monthly logs page
            Button(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3A36)),
                border = BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "VIEW FULL HISTORY & MONTHLY LOGS",
                    color = Color(0xFF64FFDA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MonthlyEnergyBarChart(
    monthDays: List<DailyEnergyRecord>,
    selectedDate: String?,
    onSelectDate: (DailyEnergyRecord) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val maxVal = monthDays.maxOfOrNull { maxOf(it.chargedKwh, it.dischargedKwh) } ?: 0f
    val maxKwh = when {
        maxVal > 4.0f -> maxVal * 1.15f
        maxVal > 1.5f -> 5.0f
        maxVal > 0.4f -> 2.0f
        else -> 1.0f
    }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()) }
    val scrollState = rememberScrollState()

    LaunchedEffect(monthDays.size) {
        // Scroll toward current day of month so active days are immediately visible
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val targetScroll = (currentDay - 5).coerceAtLeast(0) * 60
        scrollState.animateScrollTo(targetScroll)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E676), RoundedCornerShape(2.dp)))
                    Text("Solar", color = Color.LightGray, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9100), RoundedCornerShape(2.dp)))
                    Text("Load", color = Color.LightGray, fontSize = 10.sp)
                }
            }
            Text(
                if (maxVal > 0.005f) "Peak: ${formatEnergy(maxVal)}" else "Scale: ${formatEnergy(maxKwh)}",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val chartHeightDp = if (compact) 65f else 100f

        // Horizontal scroll container with all days in month
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            monthDays.forEach { rec ->
                val isToday = rec.date == todayStr
                val isSelected = rec.date == selectedDate
                val chargedH = if (rec.chargedKwh > 0f) {
                    (chartHeightDp * (rec.chargedKwh / maxKwh)).coerceIn(4f, chartHeightDp).dp
                } else 0.dp
                val dischargedH = if (rec.dischargedKwh > 0f) {
                    (chartHeightDp * (rec.dischargedKwh / maxKwh)).coerceIn(4f, chartHeightDp).dp
                } else 0.dp

                val dayNum = rec.date.takeLast(2)

                Column(
                    modifier = Modifier
                        .width(if (compact) 24.dp else 30.dp)
                        .clickable { onSelectDate(rec) }
                        .background(
                            if (isSelected) Color(0xFF1E3A3A) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeightDp.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Baseline axis line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF2C3240))
                                .align(Alignment.BottomCenter)
                        )

                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Solar bar (Green)
                            Box(
                                modifier = Modifier
                                    .width(if (compact) 7.dp else 9.dp)
                                    .height(chargedH)
                                    .background(
                                        Color(0xFF00E676),
                                        RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                    )
                            )
                            // Load bar (Orange)
                            Box(
                                modifier = Modifier
                                    .width(if (compact) 7.dp else 9.dp)
                                    .height(dischargedH)
                                    .background(
                                        Color(0xFFFF9100),
                                        RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dayNum,
                        fontSize = if (compact) 9.sp else 10.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isToday -> Color(0xFF64FFDA)
                            isSelected -> Color.White
                            rec.chargedKwh > 0f || rec.dischargedKwh > 0f -> Color.LightGray
                            else -> Color(0xFF555D6E)
                        }
                    )
                }
            }
        }
    }
}

data class BillingCycleInfo(
    val startDateStr: String,
    val endDateStr: String,
    val label: String,
    val days: List<DailyEnergyRecord>,
    val isCurrentCycle: Boolean
)

fun getDaySuffix(n: Int): String {
    return if (n in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

fun calculateBillingCycle(
    billingStartDay: Int,
    cycleOffset: Int,
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord
): BillingCycleInfo {
    val now = Calendar.getInstance()
    val currentDay = now.get(Calendar.DAY_OF_MONTH)

    val baseCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (currentDay < billingStartDay) {
            add(Calendar.MONTH, -1)
        }
        val maxDays = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, billingStartDay.coerceAtMost(maxDays))
    }

    baseCal.add(Calendar.MONTH, cycleOffset)

    val startCal = baseCal.clone() as Calendar
    val endCal = (baseCal.clone() as Calendar).apply {
        add(Calendar.MONTH, 1)
        add(Calendar.DAY_OF_MONTH, -1)
    }

    val isCurrentCycle = (cycleOffset == 0)

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val shortDisplayFormat = SimpleDateFormat("MMM d", Locale.US)

    val startDateStr = dateFormat.format(startCal.time)
    val endDateStr = dateFormat.format(endCal.time)

    val label = if (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)) {
        "${shortDisplayFormat.format(startCal.time)} – ${displayFormat.format(endCal.time)}"
    } else {
        "${displayFormat.format(startCal.time)} – ${displayFormat.format(endCal.time)}"
    }

    val todayStr = dateFormat.format(Date())
    val effectiveTodayDate = if (todayRecord.date.isNotBlank()) todayRecord.date else todayStr
    val populatedTodayRecord = if (todayRecord.date.isBlank()) todayRecord.copy(date = effectiveTodayDate) else todayRecord

    val recordsByDate = mutableMapOf<String, DailyEnergyRecord>()
    for (rec in historyList) {
        if (rec.date.isNotBlank()) recordsByDate[rec.date] = rec
    }
    recordsByDate[effectiveTodayDate] = populatedTodayRecord

    val latestCal = if (isCurrentCycle && now.before(endCal)) now else endCal

    val daysList = mutableListOf<DailyEnergyRecord>()
    val iterCal = latestCal.clone() as Calendar
    while (!iterCal.before(startCal)) {
        val dStr = dateFormat.format(iterCal.time)
        val rec = recordsByDate[dStr] ?: DailyEnergyRecord(date = dStr)
        daysList.add(rec)
        iterCal.add(Calendar.DAY_OF_MONTH, -1)
    }

    return BillingCycleInfo(
        startDateStr = startDateStr,
        endDateStr = endDateStr,
        label = label,
        days = daysList,
        isCurrentCycle = isCurrentCycle
    )
}

@Composable
fun EnergyHistoryScreen(
    manager: BmsConnectionManager,
    onBack: () -> Unit
) {
    val historyList by manager.energyHistory.historyList.collectAsState()
    val todayRecord by manager.energyHistory.todayEnergy.collectAsState()
    val bank by manager.totalBankState.collectAsState()
    val billingCycleStartDay by manager.energyHistory.billingCycleStartDay.collectAsState()

    EnergyHistoryScreen(
        historyList = historyList,
        todayRecord = todayRecord,
        bank = bank,
        billingCycleStartDay = billingCycleStartDay,
        onUpdateBillingCycleStartDay = { day -> manager.energyHistory.setBillingCycleStartDay(day) },
        onBack = onBack
    )
}

@Composable
fun EnergyHistoryScreen(
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord,
    bank: TotalBankData = TotalBankData(),
    billingCycleStartDay: Int = 10,
    onUpdateBillingCycleStartDay: (Int) -> Unit = {},
    onBack: () -> Unit
) {
    val nowCal = remember { Calendar.getInstance() }
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentMonth = nowCal.get(Calendar.MONTH)

    var targetYear by remember { mutableStateOf(currentYear) }
    var targetMonth by remember { mutableStateOf(currentMonth) }
    var cycleOffset by remember { mutableStateOf(0) }
    var isBillingCycleMode by remember { mutableStateOf(true) }
    var showCutoffDialog by remember { mutableStateOf(false) }

    val isCurrentMonth = (targetYear == currentYear && targetMonth == currentMonth)

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val effectiveTodayDate = if (todayRecord.date.isNotBlank()) todayRecord.date else todayStr
    val populatedTodayRecord = if (todayRecord.date.isBlank()) todayRecord.copy(date = effectiveTodayDate) else todayRecord

    var showAllDays by remember { mutableStateOf(false) }
    var showBarChart by remember { mutableStateOf(false) }
    var expandedDate by remember { mutableStateOf<String?>(null) }

    val combinedHistory = remember(historyList, populatedTodayRecord) {
        val list = mutableListOf(populatedTodayRecord)
        list.addAll(historyList.filter { it.date != populatedTodayRecord.date })
        list
    }

    // 1. Billing Cycle Calculation (Cutoff-based)
    val billingCycleInfo = remember(billingCycleStartDay, cycleOffset, historyList, populatedTodayRecord) {
        calculateBillingCycle(billingCycleStartDay, cycleOffset, historyList, populatedTodayRecord)
    }

    // 2. Calendar Month Calculation
    val monthPrefix = remember(targetYear, targetMonth) {
        String.format(Locale.US, "%04d-%02d", targetYear, targetMonth + 1)
    }

    val monthRecordsMap = remember(combinedHistory, monthPrefix) {
        combinedHistory.filter { it.date.startsWith(monthPrefix) }.associateBy { it.date }
    }

    val daysInMonthList = remember(monthRecordsMap, targetYear, targetMonth, isCurrentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, targetYear)
            set(Calendar.MONTH, targetMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = if (isCurrentMonth) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) else cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val list = mutableListOf<DailyEnergyRecord>()
        for (d in maxDay downTo 1) {
            val dateKey = String.format(Locale.US, "%04d-%02d-%02d", targetYear, targetMonth + 1, d)
            val record = monthRecordsMap[dateKey] ?: DailyEnergyRecord(date = dateKey)
            list.add(record)
        }
        list
    }

    // Full 365 days list
    val allDaysList = remember(combinedHistory) {
        combinedHistory.filter { it.chargedAh > 0.05f || it.dischargedAh > 0.05f || it.chargedKwh > 0.01f || it.dischargedKwh > 0.01f }
            .sortedByDescending { it.date }
            .take(365)
    }

    // Effective active list for the table and summary metrics
    val activePeriodDays = if (isBillingCycleMode) billingCycleInfo.days else daysInMonthList
    val displayedList = if (showAllDays) allDaysList else activePeriodDays

    // Totals calculation for the active period (Billing Cycle or Month)
    val totalChargedKwh = remember(activePeriodDays) {
        activePeriodDays.sumOf { it.chargedKwh.toDouble() }.toFloat()
    }
    val totalDischargedKwh = remember(activePeriodDays) {
        activePeriodDays.sumOf { it.dischargedKwh.toDouble() }.toFloat()
    }
    val totalChargedAh = remember(activePeriodDays) {
        activePeriodDays.sumOf { it.chargedAh.toDouble() }.toFloat()
    }
    val totalDischargedAh = remember(activePeriodDays) {
        activePeriodDays.sumOf { it.dischargedAh.toDouble() }.toFloat()
    }
    val netKwh = totalChargedKwh - totalDischargedKwh
    val activeDaysCount = remember(activePeriodDays) {
        activePeriodDays.count { it.chargedAh > 0.05f || it.dischargedAh > 0.05f || it.chargedKwh > 0.01f || it.dischargedKwh > 0.01f }
    }

    // Days formatted ascending for the visual bar chart
    val chartDays = remember(isBillingCycleMode, billingCycleInfo, historyList, populatedTodayRecord, targetYear, targetMonth) {
        if (isBillingCycleMode) {
            billingCycleInfo.days.reversed()
        } else {
            buildMonthDays(historyList, populatedTodayRecord, targetYear, targetMonth)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
    ) {
        // 1. Top Navigation Bar (< History)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Text(
                text = "History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        HorizontalDivider(color = Color(0xFF1E2633), thickness = 1.dp)

        // 2. Scrollable Body Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode Switcher: BILLING CYCLE vs CALENDAR MONTH + CUTOFF BUTTON
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF141A22), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isBillingCycleMode) Color(0xFF004D40) else Color.Transparent)
                            .clickable { isBillingCycleMode = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚡ BILLING CYCLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBillingCycleMode) Color(0xFF64FFDA) else Color.Gray
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isBillingCycleMode) Color(0xFF004D40) else Color.Transparent)
                            .clickable { isBillingCycleMode = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "📅 CALENDAR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isBillingCycleMode) Color(0xFF64FFDA) else Color.Gray
                        )
                    }
                }

                // Cutoff Setting Button (quick configure)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF162232))
                        .border(1.dp, Color(0xFF2979FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { showCutoffDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "CUTOFF: ${billingCycleStartDay}${getDaySuffix(billingCycleStartDay)} ⚙️",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90CAF9)
                    )
                }
            }

            // Period Navigation Bar (Cycle or Month)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isBillingCycleMode) {
                                if (cycleOffset > -12) cycleOffset--
                            } else {
                                if (targetMonth == 0) {
                                    targetMonth = 11
                                    targetYear--
                                } else {
                                    targetMonth--
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("◀", color = Color(0xFF64FFDA), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = if (isBillingCycleMode) billingCycleInfo.label else getMonthHeaderTitle(targetYear, targetMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            if (isBillingCycleMode) {
                                if (cycleOffset < 0) cycleOffset++
                            } else {
                                if (!isCurrentMonth) {
                                    if (targetMonth == 11) {
                                        targetMonth = 0
                                        targetYear++
                                    } else {
                                        targetMonth++
                                    }
                                }
                            }
                        },
                        enabled = if (isBillingCycleMode) cycleOffset < 0 else !isCurrentMonth,
                        modifier = Modifier.size(36.dp)
                    ) {
                        val isEnabled = if (isBillingCycleMode) cycleOffset < 0 else !isCurrentMonth
                        Text(
                            "▶",
                            color = if (isEnabled) Color(0xFF64FFDA) else Color.DarkGray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Toggle for Bar Chart
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showBarChart) Color(0xFF004D40) else Color(0xFF1E232E))
                        .clickable { showBarChart = !showBarChart }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (showBarChart) "HIDE CHART" else "BAR CHART",
                        fontSize = 11.sp,
                        color = if (showBarChart) Color(0xFF64FFDA) else Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Optional Expandable Bar Chart
            if (showBarChart) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131F2A)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DAILY YIELD & LOAD BARS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64FFDA))
                            Text("Tap bar to inspect", fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        MonthlyEnergyBarChart(
                            monthDays = chartDays,
                            selectedDate = expandedDate ?: effectiveTodayDate,
                            onSelectDate = { rec -> expandedDate = rec.date },
                            compact = false
                        )
                    }
                }
            }

            // 3. Period Total Summary Card (Billing Cycle or Month)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141D26)),
                border = BorderStroke(1.dp, Color(0xFF263238)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isBillingCycleMode) "BILLING CYCLE TOTALS" else "${getMonthHeaderTitle(targetYear, targetMonth)} TOTALS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80CBC4),
                                letterSpacing = 0.5.sp
                            )
                            if (isBillingCycleMode) {
                                Text(
                                    text = if (billingCycleInfo.isCurrentCycle) "Current Cycle (${billingCycleInfo.days.size} days elapsed)" else "${billingCycleInfo.days.size} days in cycle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64FFDA),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            text = if (activeDaysCount > 0) "$activeDaysCount active days" else "0 days logged",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF90A4AE)
                        )
                    }

                    // Two prominent metric boxes: Charged & Consumed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Solar Charged Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF0D281E), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "SOLAR CHARGED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatEnergy(totalChargedKwh, prefix = "+"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF00E676),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "+%.1f Ah", totalChargedAh),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA5D6A7),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Load Consumed Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF2B1C10), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFF9100).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "LOAD CONSUMED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatEnergy(totalDischargedKwh, prefix = "-"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF9100),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "-%.1f Ah", totalDischargedAh),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFCC80),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Net Difference & Daily Average Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "NET YIELD:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatNetEnergy(netKwh),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (netKwh >= 0f) Color(0xFF00E676) else Color(0xFFFF9100),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (activeDaysCount > 0) {
                            val avgYield = totalChargedKwh / activeDaysCount
                            val avgLoad = totalDischargedKwh / activeDaysCount
                            Text(
                                text = String.format(Locale.US, "Avg: %.1f kWh solar • %.1f kWh load", avgYield, avgLoad),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF90A4AE)
                            )
                        }
                    }
                }
            }

            // 4. Tabular Daily Breakdown Card (Matching User's Screenshot)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141820)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF263238))
            ) {
                Column {
                    // Header with Table View Mode (Period vs All 365 Days)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showAllDays) "ALL LOGGED DAYS (${allDaysList.size})"
                                   else if (isBillingCycleMode) "CYCLE LOGS (${displayedList.size} DAYS)"
                                   else "${getMonthHeaderTitle(targetYear, targetMonth)} LOGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF90A4AE),
                            letterSpacing = 0.5.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!showAllDays) Color(0xFF004D40) else Color(0xFF1E232E))
                                    .clickable { showAllDays = false }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isBillingCycleMode) "CYCLE" else "MONTH",
                                    fontSize = 10.sp,
                                    color = if (!showAllDays) Color(0xFF64FFDA) else Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (showAllDays) Color(0xFF004D40) else Color(0xFF1E232E))
                                    .clickable { showAllDays = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("ALL 365 DAYS", fontSize = 10.sp, color = if (showAllDays) Color(0xFF64FFDA) else Color.LightGray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E2633), thickness = 1.dp)

                    // Table Header Row (matching screenshot layout)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF171E28))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device time",
                            modifier = Modifier.weight(1.0f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0BEC5)
                        )
                        Text(
                            text = "Solar Charged",
                            modifier = Modifier.weight(1.1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "Load Consumed",
                            modifier = Modifier.weight(1.1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9100),
                            textAlign = TextAlign.End
                        )
                    }

                    HorizontalDivider(color = Color(0xFF263238), thickness = 1.dp)

                    // Table Rows
                    if (displayedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showAllDays) "Daily records are saved automatically each midnight."
                                else "No energy logs recorded for this period.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        displayedList.forEachIndexed { index, item ->
                            val isToday = item.date == todayStr
                            val isExpanded = expandedDate == item.date
                            val displayDate = if (item.date.length >= 10) item.date.substring(5) else item.date

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedDate = if (isExpanded) null else item.date }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isExpanded) Color(0xFF1C2533) else if (isToday) Color(0xFF14202B) else Color.Transparent)
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Column 1: Device time (e.g. 11-07)
                                    Row(
                                        modifier = Modifier.weight(1.0f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = displayDate,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isToday) Color(0xFF64FFDA) else Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("TODAY", fontSize = 8.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Column 2: Solar Charged
                                    Column(
                                        modifier = Modifier.weight(1.1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        if (item.chargedKwh <= 0.0005f && item.chargedAh <= 0.05f) {
                                            Text(
                                                text = "0.0 WH",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF78909C),
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.End
                                            )
                                        } else {
                                            Text(
                                                text = formatEnergy(item.chargedKwh, "+"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E676),
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.End
                                            )
                                            Text(
                                                text = String.format(Locale.US, "+%.1f Ah", item.chargedAh),
                                                fontSize = 10.sp,
                                                color = Color(0xFFA5D6A7),
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }

                                    // Column 3: Load Consumed
                                    Column(
                                        modifier = Modifier.weight(1.1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        if (item.dischargedKwh <= 0.0005f && item.dischargedAh <= 0.05f) {
                                            Text(
                                                text = "0.0 WH",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF78909C),
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.End
                                            )
                                        } else {
                                            Text(
                                                text = formatEnergy(item.dischargedKwh, "-"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF9100),
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.End
                                            )
                                            Text(
                                                text = String.format(Locale.US, "-%.1f Ah", item.dischargedAh),
                                                fontSize = 10.sp,
                                                color = Color(0xFFFFCC80),
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }

                                // Expanded details on tap
                                if (isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF18202C))
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = formatDisplayDate(item.date),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF80CBC4),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (item.minSoc > 0 || item.maxSoc > 0) {
                                                Text(
                                                    text = "SOC: ${item.minSoc}% → ${item.maxSoc}%",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray
                                                )
                                            }
                                            if (item.minAh > 0.1f || item.maxAh > 0.1f) {
                                                Text(
                                                    text = String.format(Locale.US, "Cap: %.1f → %.1f Ah", item.minAh, item.maxAh),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray
                                                )
                                            }
                                            Text(
                                                text = "Net: ${formatNetEnergy(item.netKwh)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.netKwh >= 0) Color(0xFF00E676) else Color(0xFFFF9100)
                                            )
                                        }
                                    }
                                }

                                // Divider between rows
                                if (index < displayedList.size - 1) {
                                    HorizontalDivider(color = Color(0xFF1E2633), thickness = 0.8.dp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Billing Cutoff Configuration Dialog
    if (showCutoffDialog) {
        var tempDay by remember { mutableStateOf(billingCycleStartDay) }
        AlertDialog(
            onDismissRequest = { showCutoffDialog = false },
            title = {
                Column {
                    Text("Billing Cycle Cutoff Day", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Set the day of the month your electricity bill starts. Energy data will be aligned to your exact billing period.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (tempDay > 1) tempDay-- },
                            modifier = Modifier.background(Color(0xFF1E2838), CircleShape)
                        ) {
                            Text("−", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Starts ${tempDay}${getDaySuffix(tempDay)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF64FFDA),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        IconButton(
                            onClick = { if (tempDay < 28) tempDay++ },
                            modifier = Modifier.background(Color(0xFF1E2838), CircleShape)
                        ) {
                            Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("Quick Presets:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(1, 5, 10, 15, 20, 25).forEach { d ->
                            val isSelected = tempDay == d
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF00E676) else Color(0xFF222B38))
                                    .clickable { tempDay = d }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${d}${getDaySuffix(d)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateBillingCycleStartDay(tempDay)
                        showCutoffDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("SAVE CUTOFF", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCutoffDialog = false }) {
                    Text("CANCEL", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF141820),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun EnergyHistoryDialog(
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord,
    bank: TotalBankData = TotalBankData(),
    onDismiss: () -> Unit
) {
    EnergyHistoryScreen(
        historyList = historyList,
        todayRecord = todayRecord,
        bank = bank,
        onBack = onDismiss
    )
}

@Composable
fun EnergyHistoryDialog(
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord,
    bank: TotalBankData = TotalBankData(),
    onDismiss: () -> Unit
) {
    EnergyHistoryScreen(
        historyList = historyList,
        todayRecord = todayRecord,
        bank = bank,
        onBack = onDismiss
    )
}

fun buildMonthDays(
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord,
    targetYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    targetMonth: Int = Calendar.getInstance().get(Calendar.MONTH) // 0-indexed
): List<DailyEnergyRecord> {
    val recordsByDate = mutableMapOf<String, DailyEnergyRecord>()
    for (rec in historyList) {
        if (rec.date.isNotBlank()) recordsByDate[rec.date] = rec
    }
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val effectiveTodayDate = if (todayRecord.date.isNotBlank()) todayRecord.date else todayStr
    val populatedTodayRecord = if (todayRecord.date.isBlank()) todayRecord.copy(date = effectiveTodayDate) else todayRecord
    recordsByDate[effectiveTodayDate] = populatedTodayRecord

    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, targetYear)
        set(Calendar.MONTH, targetMonth)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val result = mutableListOf<DailyEnergyRecord>()
    for (d in 1..maxDay) {
        val dateKey = String.format(Locale.US, "%04d-%02d-%02d", targetYear, targetMonth + 1, d)
        val existing = recordsByDate[dateKey]
        if (existing != null) {
            result.add(existing)
        } else {
            result.add(DailyEnergyRecord(date = dateKey))
        }
    }
    return result
}

fun getMonthHeaderTitle(
    targetYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    targetMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, targetYear)
        set(Calendar.MONTH, targetMonth)
    }
    return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time).uppercase()
}

fun formatEnergy(kwh: Float, prefix: String = ""): String {
    val absKwh = kotlin.math.abs(kwh)
    val wh = absKwh * 1000f
    return if (absKwh < 1.0f) {
        if (wh < 0.5f && absKwh < 0.0005f) {
            "${prefix}0 Wh"
        } else if (wh < 10f) {
            String.format(Locale.US, "%s%.1f Wh", prefix, wh)
        } else {
            String.format(Locale.US, "%s%.0f Wh", prefix, wh)
        }
    } else {
        String.format(Locale.US, "%s%.2f kWh", prefix, absKwh)
    }
}

fun formatNetEnergy(kwh: Float): String {
    val sign = if (kwh > 0.0005f) "+" else if (kwh < -0.0005f) "-" else ""
    return formatEnergy(kwh, prefix = sign)
}

fun formatDisplayDate(dateStr: String): String {
    return try {
        val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
        val date = inFormat.parse(dateStr)
        if (date != null) outFormat.format(date) else dateStr
    } catch (e: Exception) {
        dateStr
    }
}

@Composable
fun CombinedBatteryPacksCard(
    b1: BmsData,
    b2: BmsData,
    onInspectB1: () -> Unit,
    onInspectB2: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14202E)),
        border = BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INDIVIDUAL BATTERY PACKS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90CAF9)
                )
                Text(
                    text = "PARALLEL 16S",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            // Battery 1
            val b1Title = b1.displayName.ifBlank { "48V 100Ah #1" }.replace(" (24S)", "")
            BatteryPackRow(
                bms = b1,
                title = b1Title,
                onClick = onInspectB1
            )

            // Battery 2
            val b2Title = b2.displayName.ifBlank { "48V 100Ah #2" }.replace(" (20S)", "")
            BatteryPackRow(
                bms = b2,
                title = b2Title,
                onClick = onInspectB2
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Single Unified Inspection Button
            Button(
                onClick = onInspectB1,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔍", fontSize = 13.sp)
                    Text(
                        "INSPECT BATTERIES",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryPackRow(
    bms: BmsData,
    title: String,
    onClick: () -> Unit
) {
    val absBmsPower = kotlin.math.abs(bms.power)
    val isCharging = bms.power > 0.5f || bms.current > 0.05f
    val isDischarging = bms.power < -0.5f || bms.current < -0.05f

    val bmsPowerDisplay = if (absBmsPower < 1000f) {
        val formatted = String.format(Locale.US, "%.0f W", absBmsPower)
        if (isCharging) "+$formatted" else if (isDischarging) "-$formatted" else formatted
    } else {
        val formatted = String.format(Locale.US, "%.2f kW", absBmsPower / 1000f)
        if (isCharging) "+$formatted" else if (isDischarging) "-$formatted" else formatted
    }

    val powerColor = when {
        isCharging -> Color(0xFF00E676)
        isDischarging -> Color(0xFFFF9100)
        else -> Color.Gray
    }

    val currentDisplay = when {
        kotlin.math.abs(bms.current) < 0.05f -> "0.0 A"
        isCharging -> String.format(Locale.US, "+%.1f A", kotlin.math.abs(bms.current))
        isDischarging -> String.format(Locale.US, "-%.1f A", kotlin.math.abs(bms.current))
        else -> String.format(Locale.US, "%.1f A", bms.current)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1622)),
        border = BorderStroke(1.dp, Color(0xFF1C2B3C)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    "${bms.soc}%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        String.format(Locale.US, "%.2f V", bms.voltage),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            currentDisplay,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("•", color = Color.DarkGray, fontSize = 10.sp)
                        Text(
                            bmsPowerDisplay,
                            color = powerColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Temp: ${bms.temperature}°C", color = Color.LightGray, fontSize = 12.sp)
                    Text("Active: ${bms.cells.size}S", color = Color.LightGray, fontSize = 12.sp)
                    Text(
                        "Cell Delta: ${bms.deltaVoltageMv} mV",
                        color = if (bms.deltaVoltageMv > 30) Color(0xFFFF5252) else Color(0xFF69F0AE),
                        fontSize = 12.sp
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Min: C${bms.minCellNumber} (${String.format(Locale.US, "%.3f", bms.minCellVoltage)}V)", color = Color.Gray, fontSize = 11.sp)
                Text("Max: C${bms.maxCellNumber} (${String.format(Locale.US, "%.3f", bms.maxCellVoltage)}V)", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = if (horizontalAlignment == Alignment.End) TextAlign.End else TextAlign.Start
        )
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            textAlign = if (horizontalAlignment == Alignment.End) TextAlign.End else TextAlign.Start
        )
    }
}

@Composable
fun CellScreen(
    bmsData: BmsData,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("← DASHBOARD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bmsData.displayName.ifBlank { "BATTERY" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "INDIVIDUAL CELL VOLTAGES",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("AVG", color = Color.Gray, fontSize = 11.sp)
                    Text(String.format("%.3f V", bmsData.averageCellVoltage), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("DELTA", color = Color.Gray, fontSize = 11.sp)
                    Text("${bmsData.deltaVoltageMv} mV", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("MIN (C${bmsData.minCellNumber})", color = Color.Gray, fontSize = 11.sp)
                    Text(String.format("%.3f V", bmsData.minCellVoltage), color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("MAX (C${bmsData.maxCellNumber})", color = Color.Gray, fontSize = 11.sp)
                    Text(String.format("%.3f V", bmsData.maxCellVoltage), color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(bmsData.cells.size) { index ->
                val cell = bmsData.cells[index]
                CellCard(
                    cell = cell,
                    isMin = cell.index == bmsData.minCellNumber,
                    isMax = cell.index == bmsData.maxCellNumber
                )
            }
        }
    }
}

@Composable
fun CellCard(cell: CellData, isMin: Boolean, isMax: Boolean) {
    val borderColor = when {
        isMax -> Color(0xFF29B6F6)
        isMin -> Color(0xFFFF7043)
        else -> Color(0xFF2A2E39)
    }

    Box(
        modifier = Modifier
            .background(Color(0xFF1E222B), RoundedCornerShape(8.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = String.format("C%02d", cell.index),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                if (isMax) Text("MAX", fontSize = 9.sp, color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold)
                if (isMin) Text("MIN", fontSize = 9.sp, color = Color(0xFFFF7043), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("%.3f", cell.voltage),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            if (cell.resistance != null) {
                Text(
                    text = String.format("%.1f mΩ", cell.resistance),
                    fontSize = 9.sp,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun GatewaySettingsDialog(
    currentIp: String,
    currentFirebaseUrl: String,
    currentFirebaseAuth: String,
    connectionMode: GatewayConnectionMode,
    onForceReconnect: () -> Unit,
    onReboot: () -> Unit,
    onSave: (ip: String, firebaseUrl: String, firebaseAuth: String) -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember { mutableStateOf(currentIp) }
    var firebaseUrlText by remember { mutableStateOf(currentFirebaseUrl) }
    var firebaseAuthText by remember { mutableStateOf(currentFirebaseAuth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Gateway & Cloud Settings", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Live Connection Status
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when (connectionMode) {
                                    GatewayConnectionMode.LOCAL_WIFI -> Color(0xFF00E676)
                                    GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF29B6F6)
                                    GatewayConnectionMode.OFFLINE -> Color(0xFFFF5252)
                                },
                                CircleShape
                            )
                    )
                    Text(
                        text = when (connectionMode) {
                            GatewayConnectionMode.LOCAL_WIFI -> "Connected: Local Wi-Fi"
                            GatewayConnectionMode.FIREBASE_CLOUD -> "Connected: Firebase Cloud"
                            GatewayConnectionMode.OFFLINE -> "Status: Offline / Unreachable"
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }

                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("ESP32 Local Gateway IP") },
                    placeholder = { Text("192.168.31.111") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = firebaseUrlText,
                    onValueChange = { firebaseUrlText = it },
                    label = { Text("Firebase Database URL") },
                    placeholder = { Text("https://...firebasedatabase.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = firebaseAuthText,
                    onValueChange = { firebaseAuthText = it },
                    label = { Text("Firebase Auth Secret (Optional)") },
                    placeholder = { Text("Database secret key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Cloud / Local Gateway Action Controls
                Text("Remote Controls", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onForceReconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("⚡ RECONNECT BLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Button(
                        onClick = onReboot,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("🔄 REBOOT ESP32", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Text(
                    text = "• Uses Local Wi-Fi when at home for instant zero-latency updates.\n• Automatically switches to Firebase Cloud when outside on mobile data.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ipText, firebaseUrlText, firebaseAuthText)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
            ) {
                Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.LightGray)
            }
        },
        containerColor = Color(0xFF1A1F29)
    )
}

