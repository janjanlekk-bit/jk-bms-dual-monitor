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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    manager: BmsConnectionManager,
    onInspectCells: (BmsData) -> Unit
) {
    val b1 by manager.bms1.bmsState.collectAsState()
    val b2 by manager.bms2.bmsState.collectAsState()
    val bank by manager.totalBankState.collectAsState()
    val todayEnergy by manager.energyHistory.todayEnergy.collectAsState()
    val historyList by manager.energyHistory.historyList.collectAsState()
    val blePaused by manager.blePaused.collectAsState()
    val bleRemainingSec by manager.bleRemainingSec.collectAsState()
    val isGatewayConnected by manager.isGatewayMode.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }

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
            Column {
                Text("JK DUAL MONITOR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isGatewayConnected) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape)
                    )
                    Text(
                        text = if (isGatewayConnected) "ESP32 WI-FI GATEWAY (192.168.31.111)" else "ESP32 GATEWAY OFFLINE (UNREACHABLE)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGatewayConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatusBadge(label = "BATTERY 1", status = b1.connectionStatus)
                StatusBadge(label = "BATTERY 2", status = b2.connectionStatus)
            }
        }

        TotalBankCard(bank)

        TodayEnergyCard(
            record = todayEnergy,
            historyList = historyList,
            bank = bank,
            onViewHistory = { showHistoryDialog = true }
        )

        CombinedBatteryPacksCard(
            b1 = b1,
            b2 = b2,
            onInspectB1 = { onInspectCells(b1) },
            onInspectB2 = { onInspectCells(b2) }
        )

        // ESP32 Wi-Fi Gateway Card & Bluetooth Release Control placed at the VERY BOTTOM
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    !isGatewayConnected -> Color(0xFF2E1A1A)
                    blePaused -> Color(0xFF3E2723)
                    else -> Color(0xFF14241A)
                }
            ),
            border = BorderStroke(
                1.dp,
                when {
                    !isGatewayConnected -> Color(0xFFFF5252).copy(alpha = 0.5f)
                    blePaused -> Color(0xFFFF5722)
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
                                    !isGatewayConnected -> Color(0xFFFF5252)
                                    blePaused -> Color(0xFFFF5722)
                                    else -> Color(0xFF00E676)
                                },
                                CircleShape
                            )
                        )
                        Text(
                            text = when {
                                !isGatewayConnected -> "ESP32 GATEWAY OFFLINE"
                                blePaused -> "BLE RELEASED (${bleRemainingSec / 60}:${String.format("%02d", bleRemainingSec % 60)})"
                                else -> "ESP32 AUTONOMOUS GATEWAY"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                !isGatewayConnected -> Color(0xFFFF8A80)
                                blePaused -> Color(0xFFFFAB91)
                                else -> Color(0xFFA5D6A7)
                            }
                        )
                    }
                    Text(
                        text = when {
                            !isGatewayConnected -> "Unreachable over Wi-Fi • Check ESP32 power"
                            blePaused -> "Official JK app can connect now"
                            else -> "24/7 Logging Active • Wi-Fi Gateway Mode"
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
                } else if (isGatewayConnected) {
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

    if (showHistoryDialog) {
        EnergyHistoryDialog(
            historyList = historyList,
            todayRecord = todayEnergy,
            bank = bank,
            onDismiss = { showHistoryDialog = false }
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
                    value = String.format("%.2f V", bank.voltage),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                )
                MetricItem(
                    label = "TOTAL CURRENT",
                    value = String.format("%.1f A", bank.current),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val absPower = kotlin.math.abs(bank.power)
            val totalPowerDisplay = if (absPower < 1000f) {
                val formatted = String.format("%.0f W", absPower)
                if (bank.power < -0.5f) "-$formatted" else formatted
            } else {
                val formatted = String.format("%.2f kW", absPower / 1000f)
                if (bank.power < -0.5f) "-$formatted" else formatted
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(
                    label = "TOTAL POWER",
                    value = totalPowerDisplay,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                )
                MetricItem(
                    label = "REMAINING CAPACITY",
                    value = String.format("%.1f Ah", bank.remainingCapacityAh),
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
                    value = String.format("%.2f kWh", totalEnergyKwh),
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
                Column {
                    Text(
                        "TODAY'S ENERGY YIELD & LOAD",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF64FFDA),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatDisplayDate(record.date.ifBlank { "Today" }),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
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

            // Button moved to the bottom of the card!
            Button(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3A36)),
                border = BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "VIEW 30-DAY LOG & DETAILED CHARTS",
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

@Composable
fun EnergyHistoryDialog(
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord,
    bank: TotalBankData = TotalBankData(),
    onDismiss: () -> Unit
) {
    val monthDays = remember(historyList, todayRecord) {
        buildMonthDays(historyList, todayRecord)
    }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()) }
    var selectedRecord by remember { mutableStateOf<DailyEnergyRecord?>(todayRecord) }
    val activeSelected = selectedRecord ?: todayRecord

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "30-Day Energy History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${getMonthHeaderTitle()} — Daily Solar & Load Graph",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF80CBC4)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Monthly Bar Graph Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131F2A)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DAILY YIELD & LOAD BARS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64FFDA))
                            Text("Tap bar to inspect", fontSize = 10.sp, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        MonthlyEnergyBarChart(
                            monthDays = monthDays,
                            selectedDate = activeSelected.date,
                            onSelectDate = { rec -> selectedRecord = rec },
                            compact = false
                        )
                    }
                }

                // 2. Selected Day Inspector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2836)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF26A69A).copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isToday = activeSelected.date == todayStr
                            Text(
                                text = "${formatDisplayDate(activeSelected.date)}${if (isToday) " (TODAY)" else ""}",
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) Color(0xFF64FFDA) else Color.White,
                                fontSize = 13.sp
                            )
                            if (activeSelected.minSoc > 0 || activeSelected.maxSoc > 0) {
                                val totalCap = when {
                                    bank.nominalCapacityAh > 10f -> bank.nominalCapacityAh
                                    bank.remainingCapacityAh > 0f && bank.capacityWeightedSoc > 0 -> {
                                        bank.remainingCapacityAh / (bank.capacityWeightedSoc / 100f)
                                    }
                                    else -> 200f
                                }
                                val selMinAh = when {
                                    activeSelected.minAh > 0.05f -> activeSelected.minAh
                                    activeSelected.minSoc > 0 -> (activeSelected.minSoc / 100f) * totalCap
                                    else -> 0f
                                }
                                val selMaxAh = when {
                                    activeSelected.maxAh > 0.05f -> activeSelected.maxAh
                                    activeSelected.maxSoc > 0 -> (activeSelected.maxSoc / 100f) * totalCap
                                    else -> 0f
                                }
                                val socAhSpan = if (selMinAh > 0.05f && selMaxAh > 0.05f) {
                                    if (kotlin.math.abs(selMaxAh - selMinAh) > 0.05f) {
                                        " (${String.format(Locale.US, "%.1f → %.1f Ah", selMinAh, selMaxAh)})"
                                    } else {
                                        " (${String.format(Locale.US, "%.1f Ah", selMinAh)})"
                                    }
                                } else ""
                                Text(
                                    text = "SOC ${activeSelected.minSoc}% → ${activeSelected.maxSoc}%$socAhSpan",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("SOLAR YIELD", fontSize = 10.sp, color = Color.Gray)
                                Text(
                                    formatEnergy(activeSelected.chargedKwh, "+"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    String.format("+%.1f Ah", activeSelected.chargedAh),
                                    fontSize = 11.sp,
                                    color = Color(0xFFA5D6A7),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("LOAD CONSUMED", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.End)
                                Text(
                                    formatEnergy(activeSelected.dischargedKwh, "-"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9100),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    String.format("-%.1f Ah", activeSelected.dischargedAh),
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFCC80),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                    }
                }

                // 3. 30-Day Breakdown List
                Text("PAST 30 DAYS LOG", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

                val combinedHistory = remember(historyList, todayRecord) {
                    val list = mutableListOf(todayRecord)
                    list.addAll(historyList.filter { it.date != todayRecord.date })
                    list.filter { it.chargedAh > 0.05f || it.dischargedAh > 0.05f || it.chargedKwh > 0.01f || it.dischargedKwh > 0.01f }
                }

                if (combinedHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Daily records are saved automatically each midnight.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    combinedHistory.take(30).forEach { item ->
                        val isToday = item.date == todayStr
                        val isSelected = item.date == activeSelected.date
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRecord = item },
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF22313F) else Color(0xFF1E232E)),
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFF64FFDA)) else null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${formatDisplayDate(item.date)}${if (isToday) " (TODAY)" else ""}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isToday) Color(0xFF64FFDA) else Color.White
                                    )
                                    if (item.minSoc > 0 || item.maxSoc > 0) {
                                        Text("SOC ${item.minSoc}% → ${item.maxSoc}%", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${formatEnergy(item.chargedKwh, "+")} (${String.format("%.1f", item.chargedAh)} Ah)",
                                        color = Color(0xFF00E676),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "${formatEnergy(item.dischargedKwh, "-")} (${String.format("%.1f", item.dischargedAh)} Ah)",
                                        color = Color(0xFFFF9100),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF141820),
        shape = RoundedCornerShape(16.dp)
    )
}

fun buildMonthDays(
    historyList: List<DailyEnergyRecord>,
    todayRecord: DailyEnergyRecord
): List<DailyEnergyRecord> {
    val recordsByDate = mutableMapOf<String, DailyEnergyRecord>()
    for (rec in historyList) {
        if (rec.date.isNotBlank()) recordsByDate[rec.date] = rec
    }
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val effectiveTodayDate = if (todayRecord.date.isNotBlank()) todayRecord.date else todayStr
    val populatedTodayRecord = if (todayRecord.date.isBlank()) todayRecord.copy(date = effectiveTodayDate) else todayRecord
    recordsByDate[effectiveTodayDate] = populatedTodayRecord

    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) // 0-indexed
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val result = mutableListOf<DailyEnergyRecord>()
    for (d in 1..maxDay) {
        val dateKey = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, d)
        val existing = recordsByDate[dateKey]
        if (existing != null) {
            result.add(existing)
        } else {
            result.add(DailyEnergyRecord(date = dateKey))
        }
    }
    return result
}

fun getMonthHeaderTitle(): String {
    val cal = Calendar.getInstance()
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    color = Color.White
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

            HorizontalDivider(color = Color(0xFF263238), thickness = 1.dp)

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
    val bmsPowerDisplay = if (absBmsPower < 1000f) {
        val formatted = String.format("%.0f W", absBmsPower)
        if (bms.power < -0.5f) "-$formatted" else formatted
    } else {
        val formatted = String.format("%.2f kW", absBmsPower / 1000f)
        if (bms.power < -0.5f) "-$formatted" else formatted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("${bms.soc}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    String.format("%.2f V", bms.voltage),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Text(String.format("%.1f A  |  %s", bms.current, bmsPowerDisplay), color = Color.Gray, fontSize = 13.sp)
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
            Text("Min: C${bms.minCellNumber} (${String.format("%.3f", bms.minCellVoltage)}V)", color = Color.Gray, fontSize = 11.sp)
            Text("Max: C${bms.maxCellNumber} (${String.format("%.3f", bms.maxCellVoltage)}V)", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
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
            color = Color.White,
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

