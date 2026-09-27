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

        val b1Title = if (b1.displayName.isNotBlank()) b1.displayName else "BATTERY 1 (24S)"
        val b2Title = if (b2.displayName.isNotBlank()) b2.displayName else "BATTERY 2 (20S)"

        BatteryCard(bms = b1, title = b1Title, onViewCells = { onInspectCells(b1) })
        BatteryCard(bms = b2, title = b2Title, onViewCells = { onInspectCells(b2) })
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
            val totalPowerDisplay = if (kotlin.math.abs(bank.power) < 1000f) {
                String.format("%.0f W", bank.power)
            } else {
                String.format("%.2f kW", bank.power / 1000f)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(label = "TOTAL POWER", value = totalPowerDisplay)
                MetricItem(label = "WEIGHTED SOC", value = "${bank.capacityWeightedSoc} %")
            }
        }
    }
}

@Composable
fun BatteryCard(bms: BmsData, title: String, onViewCells: () -> Unit) {
    val bmsPowerDisplay = if (kotlin.math.abs(bms.power) < 1000f) {
        String.format("%.0f W", bms.power)
    } else {
        String.format("%.2f kW", bms.power / 1000f)
    }

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
                    Text(String.format("%.1f A  |  %s", bms.current, bmsPowerDisplay), color = Color.Gray, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Temp: ${bms.temperature}°C", color = Color.LightGray, fontSize = 13.sp)
                    Text("Active: ${bms.cells.size}S", color = Color.LightGray, fontSize = 13.sp)
                    Text("Cell Delta: ${bms.deltaVoltageMv} mV", color = if (bms.deltaVoltageMv > 30) Color(0xFFFF5252) else Color(0xFF69F0AE), fontSize = 13.sp)
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
    manager: BmsConnectionManager,
    onDismiss: () -> Unit
) {
    val devices by scanner.devices.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()
    val b1 by manager.bms1.bmsState.collectAsState()
    val b2 by manager.bms2.bmsState.collectAsState()

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
                    TextButton(onClick = { scanner.startScan(clearExisting = false) }) {
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices.size) { index ->
                    val dev = devices[index]
                    val isAssignedB1 = b1.macAddress.isNotBlank() && b1.macAddress.equals(dev.address, ignoreCase = true)
                    val isAssignedB2 = b2.macAddress.isNotBlank() && b2.macAddress.equals(dev.address, ignoreCase = true)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222631)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(dev.address, color = Color.Gray, fontSize = 11.sp)
                                Text("${dev.rssi} dBm", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { manager.assignB1(dev.address) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAssignedB1) Color(0xFF1565C0) else Color(0xFF2C3240)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        if (isAssignedB1) "✓ B1 Active" else if (isAssignedB2) "Move to B1" else "Set B1",
                                        fontSize = 11.sp,
                                        color = if (isAssignedB1) Color.White else Color(0xFF90CAF9)
                                    )
                                }
                                Button(
                                    onClick = { manager.assignB2(dev.address) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAssignedB2) Color(0xFF2E7D32) else Color(0xFF2C3240)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        if (isAssignedB2) "✓ B2 Active" else if (isAssignedB1) "Move to B2" else "Set B2",
                                        fontSize = 11.sp,
                                        color = if (isAssignedB2) Color.White else Color(0xFFA5D6A7)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Connect Card for User's Saved Battery Units
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F29)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DIRECT BMS QUICK PAIR", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

                    val isB1Set = b1.macAddress.equals("C8:47:80:1B:76:00", ignoreCase = true)
                    val isB2Set = b2.macAddress.equals("C8:47:80:1C:14:68", ignoreCase = true)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("48V 100ah #1", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("C8:47:80:1B:76:00", color = Color.Gray, fontSize = 10.sp)
                        }
                        Button(
                            onClick = { manager.assignB1("C8:47:80:1B:76:00") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isB1Set) Color(0xFF1565C0) else Color(0xFF2C3240)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                if (isB1Set) "✓ Set as B1" else "Set as B1",
                                fontSize = 11.sp,
                                color = if (isB1Set) Color.White else Color(0xFF90CAF9)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("48V 100ah #2", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("C8:47:80:1C:14:68", color = Color.Gray, fontSize = 10.sp)
                        }
                        Button(
                            onClick = { manager.assignB2("C8:47:80:1C:14:68") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isB2Set) Color(0xFF2E7D32) else Color(0xFF2C3240)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                if (isB2Set) "✓ Set as B2" else "Set as B2",
                                fontSize = 11.sp,
                                color = if (isB2Set) Color.White else Color(0xFFA5D6A7)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2F3D)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("DONE / CLOSE", color = Color.White)
            }
        }
    }
}
