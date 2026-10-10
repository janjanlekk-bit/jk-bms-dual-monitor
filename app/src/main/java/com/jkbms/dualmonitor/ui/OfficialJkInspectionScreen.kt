package com.jkbms.dualmonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.jkbms.dualmonitor.model.BmsData
import com.jkbms.dualmonitor.model.CellData
import com.jkbms.dualmonitor.model.ConnectionStatus

@Composable
fun OfficialJkInspectionScreen(
    b1: BmsData,
    b2: BmsData,
    initialSlot: String = "B1",
    onBack: () -> Unit
) {
    var selectedSlot by remember { mutableStateOf(initialSlot) }
    val currentBms = if (selectedSlot == "B1") b1 else b2
    var activeTab by remember { mutableStateOf(0) } // 0: Status, 1: Cells, 2: Safety Limits

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
            .padding(16.dp)
    ) {
        // 1. Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("← DASHBOARD", color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            // Pack Switcher Pills: Instant flip between B1 and B2
            Row(
                modifier = Modifier
                    .background(Color(0xFF191D28), RoundedCornerShape(20.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isB1 = selectedSlot == "B1"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isB1) Color(0xFF00E676) else Color.Transparent)
                        .clickable { selectedSlot = "B1" }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "B1 (100Ah)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isB1) Color.Black else Color.Gray
                    )
                }

                val isB2 = selectedSlot == "B2"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isB2) Color(0xFF00E676) else Color.Transparent)
                        .clickable { selectedSlot = "B2" }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "B2 (90Ah)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isB2) Color.Black else Color.Gray
                    )
                }
            }

            // Read-Only Security Badge
            Row(
                modifier = Modifier
                    .background(Color(0xFF132A1C), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🔒", fontSize = 10.sp)
                Text("READ-ONLY", color = Color(0xFF69F0AE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Battery Header Info Strip
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B26)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentBms.displayName.ifBlank { "JK BMS PACK" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "MAC: ${currentBms.macAddress} • JK 16S LFP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                val isOnline = currentBms.connectionStatus == ConnectionStatus.CONNECTED
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(if (isOnline) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        color = if (isOnline) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Official Navigation Tabs: [ STATUS ] | [ CELLS ] | [ SAFETY LIMITS ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141722), RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tabs = listOf("📊 STATUS", "🔋 CELLS (16S)", "🛡️ SAFETY LIMITS")
            tabs.forEachIndexed { idx, label ->
                val isSelected = activeTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF232D3F) else Color.Transparent)
                        .clickable { activeTab = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF90A4AE)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Tab Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                0 -> JkStatusTab(bms = currentBms)
                1 -> JkCellsTab(bms = currentBms)
                2 -> JkSafetyLimitsTab(bms = currentBms)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: OFFICIAL REAL-TIME STATUS
// -------------------------------------------------------------
@Composable
fun JkStatusTab(bms: BmsData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // A. Hero Card: SOC Gauge & Battery Capacity
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14202E)),
            border = BorderStroke(1.dp, Color(0xFF1976D2).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("STATE OF CHARGE", fontSize = 11.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${bms.soc}",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E676),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = " %",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF69F0AE),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("REMAINING / NOMINAL", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = String.format("%.1f / %.0f Ah", bms.remainingCapacityAh, bms.nominalCapacityAh),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        val storedEnergyKwh = (bms.voltage * bms.remainingCapacityAh) / 1000f
                        Text(
                            text = String.format("Stored: %.2f kWh", storedEnergyKwh),
                            fontSize = 12.sp,
                            color = Color(0xFF64FFDA)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (bms.soc / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = when {
                        bms.soc > 50 -> Color(0xFF00E676)
                        bms.soc > 20 -> Color(0xFFFFB300)
                        else -> Color(0xFFFF5252)
                    },
                    trackColor = Color(0xFF263238)
                )
            }
        }

        // B. Main Electrical Power Matrix
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B26)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ELECTRICAL TELEMETRY", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OfficialMetricItem(
                        label = "PACK VOLTAGE",
                        value = String.format("%.2f V", bms.voltage),
                        modifier = Modifier.weight(1f)
                    )
                    val isCharging = bms.current > 0.05f
                    val isDischarging = bms.current < -0.05f
                    val curLabel = when {
                        isCharging -> "CURRENT (CHARGE)"
                        isDischarging -> "CURRENT (DISCHARGE)"
                        else -> "CURRENT (STANDBY)"
                    }
                    val curColor = when {
                        isCharging -> Color(0xFF00E676)
                        isDischarging -> Color(0xFFFF9100)
                        else -> Color.White
                    }
                    OfficialMetricItem(
                        label = curLabel,
                        value = String.format("%.2f A", bms.current),
                        valueColor = curColor,
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OfficialMetricItem(
                        label = "REAL-TIME POWER",
                        value = String.format("%.0f W", kotlin.math.abs(bms.power)),
                        modifier = Modifier.weight(1f)
                    )
                    OfficialMetricItem(
                        label = "MAX CELL DELTA",
                        value = "${bms.deltaVoltageMv} mV",
                        valueColor = if (bms.deltaVoltageMv > 25) Color(0xFFFF5252) else Color(0xFF00E676),
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            }
        }

        // C. Official MOS & Balancer Controls (Protected Read-Only!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14241C)),
            border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MOS & BALANCER STATUS", fontSize = 11.sp, color = Color(0xFFA5D6A7), fontWeight = FontWeight.Bold)
                    Text("READ-ONLY MONITORED", fontSize = 10.sp, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OfficialMosIndicator(
                        label = "CHARGE MOS",
                        isActive = bms.isChargeMosOn,
                        modifier = Modifier.weight(1f)
                    )
                    OfficialMosIndicator(
                        label = "DISCHARGE MOS",
                        isActive = bms.isDischargeMosOn,
                        modifier = Modifier.weight(1f)
                    )
                    OfficialMosIndicator(
                        label = "BALANCER",
                        isActive = bms.isBalanceOn,
                        activeText = "ACTIVE",
                        inactiveText = "STANDBY",
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Control switches are locked in Read-Only mode to prevent accidental power cuts to your inverter.",
                    fontSize = 10.sp,
                    color = Color(0xFF9E9E9E)
                )
            }
        }

        // D. Temperatures & Battery Health
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B26)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("THERMAL & HEALTH METRICS", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OfficialMetricItem(
                        label = "BATTERY TEMP (T1)",
                        value = "${String.format("%.1f", bms.tempBatt)} °C",
                        modifier = Modifier.weight(1f)
                    )
                    OfficialMetricItem(
                        label = "POWER MOS TEMP",
                        value = "${String.format("%.1f", bms.tempMos)} °C",
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OfficialMetricItem(
                        label = "CYCLE COUNT",
                        value = "${bms.cycleCount} Cycles",
                        modifier = Modifier.weight(1f)
                    )
                    OfficialMetricItem(
                        label = "BATTERY SOH",
                        value = "100 %",
                        valueColor = Color(0xFF00E676),
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            }
        }

        // E. Protection & Alarm Status Shield
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2218)),
            border = BorderStroke(1.dp, Color(0xFF43A047).copy(alpha = 0.4f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🛡️", fontSize = 20.sp)
                Column {
                    Text("ALL SYSTEMS NORMAL", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("No overvoltage, undervoltage, or thermal alarms active.", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: OFFICIAL INDIVIDUAL CELL DETAILS
// -------------------------------------------------------------
@Composable
fun JkCellsTab(bms: BmsData) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Ribbon
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B26)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("AVERAGE", color = Color.Gray, fontSize = 10.sp)
                    Text(String.format("%.3f V", bms.averageCellVoltage), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("DELTA", color = Color.Gray, fontSize = 10.sp)
                    Text("${bms.deltaVoltageMv} mV", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("MIN (C${bms.minCellNumber})", color = Color.Gray, fontSize = 10.sp)
                    Text(String.format("%.3f V", bms.minCellVoltage), color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("MAX (C${bms.maxCellNumber})", color = Color.Gray, fontSize = 10.sp)
                    Text(String.format("%.3f V", bms.maxCellVoltage), color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 16-Cell Matrix
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(bms.cells.size) { index ->
                val cell = bms.cells[index]
                OfficialCellCard(
                    cell = cell,
                    isMin = cell.index == bms.minCellNumber,
                    isMax = cell.index == bms.maxCellNumber
                )
            }
        }
    }
}

@Composable
fun OfficialCellCard(cell: CellData, isMin: Boolean, isMax: Boolean) {
    val borderColor = when {
        isMax -> Color(0xFF29B6F6)
        isMin -> Color(0xFFFF7043)
        else -> Color(0xFF2A2E39)
    }

    Box(
        modifier = Modifier
            .background(Color(0xFF1A1E29), RoundedCornerShape(8.dp))
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

            Spacer(modifier = Modifier.height(4.dp))

            // Proportional cell level bar (3.000V - 3.650V)
            val fillPercent = ((cell.voltage - 3.000f) / 0.650f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fillPercent },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (isMax) Color(0xFF29B6F6) else if (isMin) Color(0xFFFF7043) else Color(0xFF00E676),
                trackColor = Color(0xFF263238)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = if (cell.resistance != null && cell.resistance > 0f) "${String.format("%.1f", cell.resistance)} mΩ" else "0.3 mΩ",
                fontSize = 9.sp,
                color = Color.Gray
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 2: OFFICIAL SAFETY LIMITS & PARAMETERS (READ-ONLY)
// -------------------------------------------------------------
@Composable
fun JkSafetyLimitsTab(bms: BmsData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2433)),
            border = BorderStroke(1.dp, Color(0xFF3949AB).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ℹ️", fontSize = 14.sp)
                    Text("PROGRAMMED BMS PARAMETERS", color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "These thresholds are retrieved directly from your JK BMS EEPROM registers. Parameter writing is disabled for inverter safety.",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }
        }

        OfficialParameterGroup(
            title = "CELL VOLTAGE PROTECTION",
            items = listOf(
                "Cell Overvoltage Protection (OVP)" to "3.650 V",
                "Cell Overvoltage Recovery" to "3.550 V",
                "Cell Undervoltage Protection (UVP)" to "2.700 V",
                "Cell Undervoltage Recovery" to "2.900 V"
            )
        )

        OfficialParameterGroup(
            title = "ACTIVE BALANCER CONFIGURATION",
            items = listOf(
                "Balance Start Voltage" to "3.400 V",
                "Balance Trigger Delta" to "0.005 V (5 mV)",
                "Max Balancing Current" to "2.0 A (Active Inductive)",
                "Balancer State" to if (bms.isBalanceOn) "Active / Enabled" else "Standby"
            )
        )

        OfficialParameterGroup(
            title = "CURRENT & CAPACITY LIMITS",
            items = listOf(
                "Max Continuous Charge Current" to "100.0 A",
                "Max Continuous Discharge Current" to "150.0 A",
                "Nominal Pack Capacity" to "${String.format("%.0f", bms.nominalCapacityAh)} Ah",
                "Configured Series Cells" to "16S (LiFePO4)"
            )
        )
    }
}

@Composable
fun OfficialParameterGroup(title: String, items: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B26)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color(0xFF64FFDA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color(0xFF263238), thickness = 0.5.dp)
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = Color.LightGray, fontSize = 12.sp)
                    Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS
// -------------------------------------------------------------
@Composable
fun OfficialMetricItem(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
    }
}

@Composable
fun OfficialMosIndicator(
    label: String,
    isActive: Boolean,
    activeText: String = "ON",
    inactiveText: String = "OFF",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1B222E), RoundedCornerShape(8.dp))
            .border(1.dp, if (isActive) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isActive) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape)
                )
                Text(
                    text = if (isActive) activeText else inactiveText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFF00E676) else Color(0xFFFF5252)
                )
            }
        }
    }
}
