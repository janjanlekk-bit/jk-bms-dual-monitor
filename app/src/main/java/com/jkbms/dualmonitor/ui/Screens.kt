package com.jkbms.dualmonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

// ===== JK Monitor Design System v2 =====
private val BgPrimary      = Color(0xFF0B0F1A)
private val BgCard         = Color(0xFF111827)
private val BgCardAlt      = Color(0xFF0D1526)
private val AccentBlue     = Color(0xFF2979FF)
private val AccentBlueL    = Color(0xFF82B1FF)
private val SolarGreen     = Color(0xFF00C853)
private val SolarGreenL    = Color(0xFFA5D6A7)
private val LoadOrange     = Color(0xFFFF6D00)
private val LoadOrangeL    = Color(0xFFFFCC80)
private val CautionYellow  = Color(0xFFFFD600)
private val ErrorRed       = Color(0xFFFF1744)
private val TextPrimary    = Color(0xFFE8EAED)
private val TextSecondary  = Color(0xFF8F9BB3)
private val TextMuted      = Color(0xFF4A5568)
private val DividerColor   = Color(0xFF1E2D45)
private val CardBorder     = Color(0xFF1E3A5F)
private val CardBorderGreen= Color(0xFF1B4332)
private val CardBorderOrange=Color(0xFF4A2008)

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
    val electricityPricePerKwh by manager.energyHistory.electricityPricePerKwh.collectAsState()
    val currencySymbol by manager.energyHistory.currencySymbol.collectAsState()
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
                Text("JK DUAL MONITOR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = TextPrimary, letterSpacing = 0.5.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                when (connectionMode) {
                                    GatewayConnectionMode.LOCAL_WIFI -> SolarGreen
                                    GatewayConnectionMode.FIREBASE_CLOUD -> AccentBlue
                                    GatewayConnectionMode.OFFLINE -> ErrorRed
                                },
                                CircleShape
                            )
                    )
                    Text(
                        text = when (connectionMode) {
                            GatewayConnectionMode.LOCAL_WIFI -> "LOCAL WI-FI"
                            GatewayConnectionMode.FIREBASE_CLOUD -> "CLOUD (FIREBASE)"
                            GatewayConnectionMode.OFFLINE -> "GATEWAY OFFLINE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (connectionMode) {
                            GatewayConnectionMode.LOCAL_WIFI -> SolarGreen
                            GatewayConnectionMode.FIREBASE_CLOUD -> AccentBlueL
                            GatewayConnectionMode.OFFLINE -> ErrorRed
                        },
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
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
            colors = CardDefaults.cardColors(containerColor = BgCardAlt),
            border = BorderStroke(1.dp, DividerColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatusBadge(label = "BATTERY 1", status = b1.connectionStatus)
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(DividerColor))
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
            electricityPricePerKwh = electricityPricePerKwh,
            currencySymbol = currencySymbol,
            onViewHistory = onViewHistory
        )

        CombinedBatteryPacksCard(
            b1 = b1,
            b2 = b2,
            onInspectB1 = { onInspectCells(b1) },
            onInspectB2 = { onInspectCells(b2) }
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showGatewaySettingsDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = when {
                    connectionMode == GatewayConnectionMode.OFFLINE -> Color(0xFF1F0A0A)
                    blePaused -> Color(0xFF1A1200)
                    connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> Color(0xFF0A1526)
                    else -> Color(0xFF0A1A12)
                }
            ),
            border = BorderStroke(
                1.dp,
                when {
                    connectionMode == GatewayConnectionMode.OFFLINE -> ErrorRed.copy(alpha = 0.4f)
                    blePaused -> CautionYellow.copy(alpha = 0.4f)
                    connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> AccentBlue.copy(alpha = 0.3f)
                    else -> SolarGreen.copy(alpha = 0.25f)
                }
            ),
            shape = RoundedCornerShape(12.dp)
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
                                    connectionMode == GatewayConnectionMode.OFFLINE -> ErrorRed
                                    blePaused -> CautionYellow
                                    connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> AccentBlue
                                    else -> SolarGreen
                                },
                                CircleShape
                            )
                        )
                        Text(
                            text = when {
                                connectionMode == GatewayConnectionMode.OFFLINE -> "ESP32 GATEWAY OFFLINE"
                                blePaused -> "BLE RELEASED (${bleRemainingSec / 60}:${String.format("%02d", bleRemainingSec % 60)})"
                                connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> "CLOUD SYNC ACTIVE"
                                else -> "GATEWAY ONLINE"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                connectionMode == GatewayConnectionMode.OFFLINE -> ErrorRed
                                blePaused -> CautionYellow
                                connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> AccentBlueL
                                else -> SolarGreen
                            },
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = when {
                            connectionMode == GatewayConnectionMode.OFFLINE -> "Unreachable · Check ESP32 power"
                            blePaused -> "Official JK app can connect now"
                            connectionMode == GatewayConnectionMode.FIREBASE_CLOUD -> "Remote Monitoring · Firebase RTDB"
                            else -> "24/7 Logging Active · Local Wi-Fi"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                if (blePaused) {
                    Button(
                        onClick = { manager.resumeBle() },
                        colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("RESUME", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                } else if (connectionMode != GatewayConnectionMode.OFFLINE) {
                    OutlinedButton(
                        onClick = { manager.releaseBle(600) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
                    ) {
                        Text("RELEASE BLE (10m)", color = AccentBlueL, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
        ConnectionStatus.CONNECTED -> SolarGreen to "ONLINE"
        ConnectionStatus.CONNECTING, ConnectionStatus.DISCOVERING_SERVICES,
        ConnectionStatus.ENABLING_NOTIFICATIONS -> CautionYellow to "CONNECTING"
        ConnectionStatus.RECONNECTING -> LoadOrange to "RECONNECTING"
        else -> ErrorRed to "OFFLINE"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).background(dotColor, CircleShape))
        Text("$label: $text", color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
        isCharging   -> SolarGreen
        isDischarging-> LoadOrange
        else         -> TextPrimary
    }

    val totalCurrentDisplay = when {
        kotlin.math.abs(bank.current) < 0.05f -> "0.0 A"
        isCharging   -> String.format(Locale.US, "+%.1f A", kotlin.math.abs(bank.current))
        isDischarging-> String.format(Locale.US, "-%.1f A", kotlin.math.abs(bank.current))
        else         -> String.format(Locale.US, "%.1f A", bank.current)
    }

    val soc = bank.capacityWeightedSoc.coerceIn(0, 100)
    val socColor = when {
        soc >= 70 -> SolarGreen
        soc >= 40 -> CautionYellow
        else      -> ErrorRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "BATTERY BANK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "Total System Status",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                // Status pill
                val statusText = when {
                    isCharging    -> "CHARGING"
                    isDischarging -> "DISCHARGING"
                    else          -> "STANDBY"
                }
                Box(
                    modifier = Modifier
                        .background(
                            when {
                                isCharging    -> SolarGreen.copy(alpha = 0.15f)
                                isDischarging -> LoadOrange.copy(alpha = 0.15f)
                                else          -> AccentBlue.copy(alpha = 0.15f)
                            },
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    when {
                                        isCharging    -> SolarGreen
                                        isDischarging -> LoadOrange
                                        else          -> AccentBlue
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isCharging    -> SolarGreen
                                isDischarging -> LoadOrange
                                else          -> AccentBlue
                            },
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main content: SOC gauge + right metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Circular SOC Arc Gauge
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val startAngle = 135f
                        val sweepMax = 270f

                        // Track arc (background)
                        drawArc(
                            color = Color(0xFF1E2D45),
                            startAngle = startAngle,
                            sweepAngle = sweepMax,
                            useCenter = false,
                            topLeft = Offset(cx - radius, cy - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Value arc (SOC fill)
                        val fillSweep = sweepMax * (soc / 100f)
                        if (fillSweep > 0f) {
                            drawArc(
                                color = when {
                                    soc >= 70 -> SolarGreen
                                    soc >= 40 -> CautionYellow
                                    else      -> ErrorRed
                                },
                                startAngle = startAngle,
                                sweepAngle = fillSweep,
                                useCenter = false,
                                topLeft = Offset(cx - radius, cy - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Inner glow ring
                        drawCircle(
                            color = Color(0xFF0D1526),
                            radius = radius - strokeWidth / 2f - 4.dp.toPx()
                        )
                    }
                    // Center text
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$soc",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = socColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "% SOC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // RIGHT: Key metrics in a 2x2 grid
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricItem2(
                            label = "VOLTAGE",
                            value = String.format(Locale.US, "%.2f V", bank.voltage),
                            modifier = Modifier.weight(1f)
                        )
                        MetricItem2(
                            label = "CURRENT",
                            value = totalCurrentDisplay,
                            valueColor = powerColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricItem2(
                            label = "POWER",
                            value = totalPowerDisplay,
                            valueColor = powerColor,
                            modifier = Modifier.weight(1f)
                        )
                        MetricItem2(
                            label = "CAPACITY",
                            value = String.format(Locale.US, "%.1f Ah", bank.remainingCapacityAh),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: Stored Energy
            val totalEnergyKwh = (bank.voltage * bank.remainingCapacityAh) / 1000f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⚡", fontSize = 14.sp)
                    Column {
                        Text("STORED ENERGY", fontSize = 10.sp, color = TextSecondary, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
                        Text(
                            String.format(Locale.US, "%.2f kWh", totalEnergyKwh),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentBlueL,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                // SOC bar
                Column(horizontalAlignment = Alignment.End) {
                    Text("CHARGE LEVEL", fontSize = 10.sp, color = TextSecondary, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(8.dp)
                            .background(Color(0xFF1E2D45), RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(soc / 100f)
                                .background(socColor, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

// New compact metric item (smaller, for inside the SOC card grid)
@Composable
fun MetricItem2(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary
) {
    Column(modifier = modifier) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 0.5.sp
        )
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TodayEnergyCard(
    record: DailyEnergyRecord,
    historyList: List<DailyEnergyRecord> = emptyList(),
    bank: TotalBankData = TotalBankData(),
    electricityPricePerKwh: Float = 12.0f,
    currencySymbol: String = "₱",
    onViewHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, SolarGreen.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            val todayCalStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
            val isNightCycle = record.date.isNotBlank() && record.date != todayCalStr
            val displayDateText = if (isNightCycle) {
                "${formatDisplayDate(record.date)} · Overnight Cycle"
            } else {
                formatDisplayDate(record.date.ifBlank { "Today" })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isNightCycle) "ENERGY CYCLE" else "TODAY'S ENERGY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        displayDateText,
                        fontSize = 12.sp,
                        color = if (isNightCycle) LoadOrange else TextMuted
                    )
                }
                if (isNightCycle) {
                    Box(
                        modifier = Modifier
                            .background(LoadOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("OVERNIGHT", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = LoadOrange, letterSpacing = 0.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Solar vs Load - large metric cards side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Solar Charged
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0A1F12), RoundedCornerShape(12.dp))
                        .border(1.dp, SolarGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(SolarGreen, CircleShape))
                            Text("SOLAR", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = SolarGreen, letterSpacing = 1.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            formatEnergy(record.chargedKwh, "+"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SolarGreen,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            String.format("+%.1f Ah", record.chargedAh),
                            fontSize = 11.sp,
                            color = SolarGreenL,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Load Consumed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1F0F05), RoundedCornerShape(12.dp))
                        .border(1.dp, LoadOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(LoadOrange, CircleShape))
                            Text("LOAD", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = LoadOrange, letterSpacing = 1.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            formatEnergy(record.dischargedKwh, "-"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LoadOrange,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            String.format("-%.1f Ah", record.dischargedAh),
                            fontSize = 11.sp,
                            color = LoadOrangeL,
                            fontFamily = FontFamily.Monospace
                        )
                        if (record.dischargedKwh > 0.005f) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format(Locale.US, "%s%.2f saved", currencySymbol, record.dischargedKwh * electricityPricePerKwh),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlueL,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // SOC Range row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SOC RANGE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.8.sp)
                    val socText = if (record.minSoc > 0 || record.maxSoc > 0) {
                        "${record.minSoc}% → ${record.maxSoc}%"
                    } else "—"
                    Text(socText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                }

                val totalCap = when {
                    bank.nominalCapacityAh > 10f -> bank.nominalCapacityAh
                    bank.remainingCapacityAh > 0f && bank.capacityWeightedSoc > 0 ->
                        bank.remainingCapacityAh / (bank.capacityWeightedSoc / 100f)
                    else -> 200f
                }
                val displayMinAh = when {
                    record.minAh > 0.05f -> record.minAh
                    record.minSoc > 0    -> (record.minSoc / 100f) * totalCap
                    else                 -> 0f
                }
                val displayMaxAh = when {
                    record.maxAh > 0.05f -> record.maxAh
                    record.maxSoc > 0    -> (record.maxSoc / 100f) * totalCap
                    else                 -> 0f
                }
                val ahText = when {
                    displayMinAh > 0.05f && displayMaxAh > 0.05f -> {
                        if (kotlin.math.abs(displayMaxAh - displayMinAh) > 0.05f) {
                            String.format(Locale.US, "%.1f → %.1f Ah", displayMinAh, displayMaxAh)
                        } else String.format(Locale.US, "%.1f Ah", displayMinAh)
                    }
                    displayMaxAh > 0.05f -> String.format(Locale.US, "%.1f Ah", displayMaxAh)
                    displayMinAh > 0.05f -> String.format(Locale.US, "%.1f Ah", displayMinAh)
                    else -> "—"
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("CAPACITY RANGE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.8.sp)
                    Text(ahText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentBlueL, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Bar chart header
            Text(
                "${getMonthHeaderTitle()} — DAILY OVERVIEW",
                fontSize = 10.sp,
                color = AccentBlueL,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            val monthDays = remember(record, historyList) { buildMonthDays(historyList, record) }
            MonthlyEnergyBarChart(
                monthDays = monthDays,
                selectedDate = record.date,
                onSelectDate = { onViewHistory() },
                compact = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D1E3A)),
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "VIEW FULL HISTORY & MONTHLY LOGS",
                    color = AccentBlueL,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
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
    val electricityPricePerKwh by manager.energyHistory.electricityPricePerKwh.collectAsState()
    val currencySymbol by manager.energyHistory.currencySymbol.collectAsState()

    EnergyHistoryScreen(
        historyList = historyList,
        todayRecord = todayRecord,
        bank = bank,
        billingCycleStartDay = billingCycleStartDay,
        electricityPricePerKwh = electricityPricePerKwh,
        currencySymbol = currencySymbol,
        onUpdateBillingCycleStartDay = { day -> manager.energyHistory.setBillingCycleStartDay(day) },
        onUpdateElectricityPrice = { price -> manager.energyHistory.setElectricityPricePerKwh(price) },
        onUpdateCurrencySymbol = { symbol -> manager.energyHistory.setCurrencySymbol(symbol) },
        onBack = onBack
    )
}

@Composable
fun EnergyHistoryScreen(
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord,
    bank: TotalBankData = TotalBankData(),
    billingCycleStartDay: Int = 10,
    electricityPricePerKwh: Float = 12.0f,
    currencySymbol: String = "₱",
    onUpdateBillingCycleStartDay: (Int) -> Unit = {},
    onUpdateElectricityPrice: (Float) -> Unit = {},
    onUpdateCurrencySymbol: (String) -> Unit = {},
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

                // Billing & Rate Configuration Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF162232))
                            .border(1.dp, Color(0xFF2979FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { showCutoffDialog = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "CUTOFF: ${billingCycleStartDay}${getDaySuffix(billingCycleStartDay)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF90CAF9)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF14291E))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { showCutoffDialog = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${currencySymbol}${String.format(Locale.US, "%.2f", electricityPricePerKwh)} ⚙️",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64FFDA)
                        )
                    }
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

                    // 3b. Estimated Cost Savings (Battery Load Discharged Basis)
                    val totalCostSaved = totalDischargedKwh * electricityPricePerKwh
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF13281E))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { showCutoffDialog = true }
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💰 ESTIMATED COST SAVINGS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "@ ${currencySymbol}${String.format(Locale.US, "%.2f", electricityPricePerKwh)}/kWh ⚙️",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64FFDA),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "${currencySymbol} ${String.format(Locale.US, "%,.2f", totalCostSaved)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF00E676),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Saved by Battery Load Offset (${formatEnergy(totalDischargedKwh, "-")})",
                                        fontSize = 10.sp,
                                        color = Color(0xFFA5D6A7)
                                    )
                                }

                                if (activeDaysCount > 0) {
                                    val dailyAvgSavings = totalCostSaved / activeDaysCount
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "DAILY AVERAGE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "${currencySymbol}${String.format(Locale.US, "%.2f", dailyAvgSavings)}/day",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64FFDA),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            if (isBillingCycleMode && billingCycleInfo.isCurrentCycle && activeDaysCount > 0 && billingCycleInfo.days.isNotEmpty()) {
                                val cycleLength = billingCycleInfo.days.size
                                val projectedSavings = (totalCostSaved / activeDaysCount) * cycleLength
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Projected for ${cycleLength}-day cycle:",
                                        fontSize = 10.sp,
                                        color = Color(0xFF90A4AE)
                                    )
                                    Text(
                                        text = "~${currencySymbol}${String.format(Locale.US, "%,.2f", projectedSavings)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64FFDA),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
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
                                            if (item.dischargedKwh > 0.005f) {
                                                Text(
                                                    text = String.format(Locale.US, "(%s%.2f saved)", currencySymbol, item.dischargedKwh * electricityPricePerKwh),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF64FFDA),
                                                    fontFamily = FontFamily.Monospace,
                                                    textAlign = TextAlign.End
                                                )
                                            }
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
                                        if (item.dischargedKwh > 0.005f) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Cost Saved (Load Offset):",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF81C784)
                                                )
                                                Text(
                                                    text = "${currencySymbol}${String.format(Locale.US, "%.2f", item.dischargedKwh * electricityPricePerKwh)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF00E676),
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
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

    // Billing & Tariff Settings Dialog
    if (showCutoffDialog) {
        var tempDay by remember { mutableStateOf(billingCycleStartDay) }
        var tempRate by remember { mutableStateOf(electricityPricePerKwh) }
        var tempSymbol by remember { mutableStateOf(currencySymbol) }
        val dialogScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showCutoffDialog = false },
            title = {
                Column {
                    Text("Billing & Tariff Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Set your electricity rate and billing cutoff day to accurately compute money saved from battery usage.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECTION 1: Electricity Rate
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "ELECTRICITY PRICE PER KWH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80CBC4),
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (tempRate >= 0.5f) tempRate = (tempRate - 0.5f).coerceAtLeast(0f) },
                                modifier = Modifier.background(Color(0xFF1E2838), CircleShape)
                            ) {
                                Text("−", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "${tempSymbol} ${String.format(Locale.US, "%.2f", tempRate)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E676),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = { tempRate = (tempRate + 0.5f).coerceAtMost(100f) },
                                modifier = Modifier.background(Color(0xFF1E2838), CircleShape)
                            ) {
                                Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("Rate Presets:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f).forEach { r ->
                                val isSelected = kotlin.math.abs(tempRate - r) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF00E676) else Color(0xFF222B38))
                                        .clickable { tempRate = r }
                                        .padding(horizontal = 7.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "${r.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }

                        Text("Currency Symbol:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("₱", "$", "€", "£").forEach { sym ->
                                val isSelected = tempSymbol == sym
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF64FFDA) else Color(0xFF222B38))
                                        .clickable { tempSymbol = sym }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = sym,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF263238), thickness = 1.dp)

                    // SECTION 2: Billing Cutoff Day
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "BILLING CYCLE CUTOFF DAY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF90CAF9),
                            letterSpacing = 0.5.sp
                        )

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
                                color = Color(0xFF90CAF9),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            IconButton(
                                onClick = { if (tempDay < 28) tempDay++ },
                                modifier = Modifier.background(Color(0xFF1E2838), CircleShape)
                            ) {
                                Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("Day Presets:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(1, 5, 10, 15, 20, 25).forEach { d ->
                                val isSelected = tempDay == d
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF2979FF) else Color(0xFF222B38))
                                        .clickable { tempDay = d }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${d}${getDaySuffix(d)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateBillingCycleStartDay(tempDay)
                        onUpdateElectricityPrice(tempRate)
                        onUpdateCurrencySymbol(tempSymbol)
                        showCutoffDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("SAVE SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "BATTERY PACKS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Text("Parallel 16S LFP", fontSize = 11.sp, color = TextMuted)
                }
                Box(
                    modifier = Modifier
                        .background(AccentBlue.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("2 PACKS", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlueL, letterSpacing = 1.sp)
                }
            }

            val b1Title = b1.displayName.ifBlank { "48V 100Ah #1" }.replace(" (24S)", "").replace(" (20S)", "")
            BatteryPackRow(bms = b1, title = b1Title, onClick = onInspectB1)

            val b2Title = b2.displayName.ifBlank { "48V 100Ah #2" }.replace(" (24S)", "").replace(" (20S)", "")
            BatteryPackRow(bms = b2, title = b2Title, onClick = onInspectB2)

            Button(
                onClick = onInspectB1,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D1E3A)),
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔍", fontSize = 13.sp)
                    Text(
                        "INSPECT BATTERIES",
                        color = AccentBlueL,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
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
        isCharging    -> SolarGreen
        isDischarging -> LoadOrange
        else          -> TextMuted
    }

    val currentDisplay = when {
        kotlin.math.abs(bms.current) < 0.05f -> "0.0 A"
        isCharging    -> String.format(Locale.US, "+%.1f A", kotlin.math.abs(bms.current))
        isDischarging -> String.format(Locale.US, "-%.1f A", kotlin.math.abs(bms.current))
        else          -> String.format(Locale.US, "%.1f A", bms.current)
    }

    val soc = bms.soc.coerceIn(0, 100)
    val socColor = when {
        soc >= 70 -> SolarGreen
        soc >= 40 -> CautionYellow
        else      -> ErrorRed
    }

    val isConnected = bms.connectionStatus == ConnectionStatus.CONNECTED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF0D1526) else Color(0xFF1A0E0E)
        ),
        border = BorderStroke(
            1.dp,
            if (isConnected) CardBorder else ErrorRed.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top row: name, SOC%, connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                when (bms.connectionStatus) {
                                    ConnectionStatus.CONNECTED -> SolarGreen
                                    ConnectionStatus.CONNECTING, ConnectionStatus.DISCOVERING_SERVICES,
                                    ConnectionStatus.ENABLING_NOTIFICATIONS -> CautionYellow
                                    ConnectionStatus.RECONNECTING -> LoadOrange
                                    else -> ErrorRed
                                },
                                CircleShape
                            )
                    )
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Text(
                    "$soc%",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = socColor,
                    fontFamily = FontFamily.Monospace
                )
            }

            // SOC Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color(0xFF1E2D45), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(soc / 100f)
                        .background(socColor, RoundedCornerShape(3.dp))
                )
            }

            // Bottom metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    String.format(Locale.US, "%.2f V", bms.voltage),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(currentDisplay, color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("·", color = TextMuted, fontSize = 10.sp)
                    Text(bmsPowerDisplay, color = powerColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${bms.temperature}°C", color = TextSecondary, fontSize = 11.sp)
                    Text("Δ ${bms.deltaVoltageMv}mV",
                        color = if (bms.deltaVoltageMv > 30) ErrorRed else SolarGreen,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
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

