package com.jkbms.dualmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkbms.dualmonitor.ble.BleScanner
import com.jkbms.dualmonitor.ble.BmsConnectionManager
import com.jkbms.dualmonitor.model.BmsData
import com.jkbms.dualmonitor.model.CellData
import com.jkbms.dualmonitor.model.ConnectionStatus
import com.jkbms.dualmonitor.model.TotalBankData

@Composable
fun DashboardScreen(
    manager: BmsConnectionManager,
    onOpenScan: () -> Unit,
    onInspectCells: (BmsData) -> Unit
) {
    val b1 by manager.bms1.bmsState.collectAsState()
    val b2 by manager.bms2.bmsState.collectAsState()
    val bank by manager.totalBankState.collectAsState()

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
                Text("READ-ONLY TELEMETRY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Button(
                onClick = onOpenScan,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("PAIR BMS")
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
                StatusBadge(label = "B1 (24S)", status = b1.connectionStatus)
                StatusBadge(label = "B2 (20S)", status = b2.connectionStatus)
            }
        }

        TotalBankCard(bank)

        BatteryCard(bms = b1, title = "BATTERY 1 (JK_BD6A24S12P)", onViewCells = { onInspectCells(b1) })
        BatteryCard(bms = b2, title = "BATTERY 2 (JK_BD6A20S10P)", onViewCells = { onInspectCells(b2) })
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TOTAL BATTERY BANK", style = MaterialTheme.typography.titleMedium, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(label = "BUS VOLTAGE", value = String.format("%.2f V", bank.voltage))
                MetricItem(label = "TOTAL CURRENT", value = String.format("%.1f A", bank.current))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(label = "TOTAL POWER", value = String.format("%.2f kW", bank.power / 1000f))
                MetricItem(label = "WEIGHTED SOC", value = "${bank.capacityWeightedSoc} %")
            }
        }
    }
}

@Composable
fun BatteryCard(bms: BmsData, title: String, onViewCells: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("${bms.soc}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(String.format("%.2f V", bms.voltage), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text(String.format("%.1f A  |  %.0f W", bms.current, bms.power), color = Color.Gray, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Temp: ${bms.temperature}°C", color = Color.LightGray, fontSize = 13.sp)
                    Text("Active: ${bms.cells.size}S", color = Color.LightGray, fontSize = 13.sp)
                    Text("Delta: ${bms.deltaVoltageMv} mV", color = if (bms.deltaVoltageMv > 30) Color(0xFFFF5252) else Color(0xFF69F0AE), fontSize = 13.sp)
                }
            }

            HorizontalDivider(color = Color(0xFF2C303A), thickness = 1.dp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Min: C${bms.minCellNumber} (${String.format("%.3f", bms.minCellVoltage)}V)", color = Color.Gray, fontSize = 12.sp)
                Text("Max: C${bms.maxCellNumber} (${String.format("%.3f", bms.maxCellVoltage)}V)", color = Color.Gray, fontSize = 12.sp)
            }

            Button(
                onClick = onViewCells,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252B36)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("VIEW CELLS (${bms.cells.size} ACTIVE)", color = Color.White)
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252B36))
            ) {
                Text("← DASHBOARD")
            }
            Text("${bmsData.displayName} CELLS", style = MaterialTheme.typography.titleMedium, color = Color.White)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBottomSheet(
    scanner: BleScanner,
    onDismiss: () -> Unit,
    onAssignB1: (String) -> Unit,
    onAssignB2: (String) -> Unit
) {
    val devices by scanner.devices.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161920)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PAIR JK BMS DEVICES", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isScanning) "Scanning..." else "Idle", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { scanner.startScan() }) {
                        Text("RESCAN", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isScanning) "Scanning for nearby JK BMS BLE devices..."
                        else "No devices found. Tap RESCAN to search.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices.size) { index ->
                    val dev = devices[index]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222631)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(dev.name, color = Color.White, fontSize = 14.sp)
                                Text(dev.address, color = Color.Gray, fontSize = 11.sp)
                                Text("${dev.rssi} dBm", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onAssignB1(dev.address) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("B1", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { onAssignB2(dev.address) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("B2", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
