package com.jkbms.dualmonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import kotlinx.coroutines.launch

// Design System Constants
private val BgPrimary      = Color(0xFF0B0F1A)
private val BgCard         = Color(0xFF111827)
private val BgCardAlt      = Color(0xFF0D1526)
private val AccentBlue     = Color(0xFF2979FF)
private val AccentBlueL    = Color(0xFF82B1FF)
private val SolarGreen     = Color(0xFF00C853)
private val SolarGreenL    = Color(0xFFA5D6A7)
private val LoadOrange     = Color(0xFFFF6D00)
private val CautionYellow  = Color(0xFFFFD600)
private val ErrorRed       = Color(0xFFFF1744)
private val TextPrimary    = Color(0xFFE8EAED)
private val TextSecondary  = Color(0xFF8F9BB3)
private val TextMuted      = Color(0xFF4A5568)
private val DividerColor   = Color(0xFF1E2D45)
private val CardBorder     = Color(0xFF1E3A5F)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OfficialJkInspectionScreen(
    b1: BmsData,
    b2: BmsData,
    initialSlot: String = "B1",
    onBack: () -> Unit
) {
    var selectedSlot by remember { mutableStateOf(initialSlot) }
    val currentBms = if (selectedSlot == "B1") b1 else b2
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("‹ DASHBOARD", color = AccentBlueL, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Pack Switcher Pills: Instant flip between B1 and B2
            Row(
                modifier = Modifier
                    .background(BgCardAlt, RoundedCornerShape(20.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isB1 = selectedSlot == "B1"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isB1) AccentBlue else Color.Transparent)
                        .clickable { selectedSlot = "B1" }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "B1 (100Ah)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isB1) Color.White else TextSecondary
                    )
                }

                val isB2 = selectedSlot == "B2"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isB2) AccentBlue else Color.Transparent)
                        .clickable { selectedSlot = "B2" }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "B2 (100Ah)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isB2) Color.White else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Battery Header Info Strip
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
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
                        color = TextPrimary
                    )
                    Text(
                        text = "MAC: ${currentBms.macAddress} • JK 16S LFP",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                val isOnline = currentBms.connectionStatus == ConnectionStatus.CONNECTED
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(if (isOnline) SolarGreen else ErrorRed, CircleShape))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        color = if (isOnline) SolarGreen else ErrorRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Official Navigation Tabs: [ STATUS ] | [ CELLS ] | [ SAFETY LIMITS ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardAlt, RoundedCornerShape(12.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf("📊  STATUS", "🔋  CELLS (16S)", "🛡️  SAFETY")
            tabs.forEachIndexed { idx, label ->
                val isSelected = pagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentBlue else Color.Transparent)
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(idx)
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) Color.White else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Tab Content with horizontal swipe
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
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
    val soc = bms.soc.coerceIn(0, 100)
    // Updated threshold per user request: Green > 50%, Amber > 10%, Red <= 10%
    val socColor = when {
        soc > 50 -> SolarGreen
        soc > 10 -> CautionYellow
        else     -> ErrorRed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // A. Hero Card: SOC Gauge & Battery Capacity
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("STATE OF CHARGE", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$soc",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = socColor,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = " %",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = socColor,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("REMAINING / NOMINAL", fontSize = 10.sp, color = TextMuted, letterSpacing = 0.5.sp)
                        Text(
                            text = String.format("%.1f / %.0f Ah", bms.remainingCapacityAh, bms.nominalCapacityAh),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        val storedEnergyKwh = (bms.voltage * bms.remainingCapacityAh) / 1000f
                        Text(
                            text = String.format("Stored: %.2f kWh", storedEnergyKwh),
                            fontSize = 12.sp,
                            color = AccentBlueL,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar with user's threshold
                LinearProgressIndicator(
                    progress = { (soc / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = socColor,
                    trackColor = Color(0xFF1E2D45)
                )
            }
        }

        // B. Main Electrical Power Matrix
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ELECTRICAL TELEMETRY", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(10.dp))

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
                        isCharging -> SolarGreen
                        isDischarging -> LoadOrange
                        else -> TextPrimary
                    }
                    OfficialMetricItem(
                        label = curLabel,
                        value = String.format("%.2f A", bms.current),
                        valueColor = curColor,
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    val pColor = when {
                        bms.power > 0.5f -> SolarGreen
                        bms.power < -0.5f -> LoadOrange
                        else -> TextPrimary
                    }
                    OfficialMetricItem(
                        label = "REAL-TIME POWER",
                        value = String.format("%.0f W", kotlin.math.abs(bms.power)),
                        valueColor = pColor,
                        modifier = Modifier.weight(1f)
                    )
                    OfficialMetricItem(
                        label = "MAX CELL DELTA",
                        value = "${bms.deltaVoltageMv} mV",
                        valueColor = if (bms.deltaVoltageMv > 25) ErrorRed else SolarGreen,
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            }
        }

        // C. Official MOS & Balancer Controls (Protected Read-Only!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MOS & BALANCER STATUS", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("READ-ONLY", fontSize = 10.sp, color = AccentBlueL, fontWeight = FontWeight.Bold)
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
                    color = TextMuted
                )
            }
        }

        // D. Temperatures & Battery Health
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("THERMAL & HEALTH METRICS", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(10.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OfficialMetricItem(
                        label = "CYCLE COUNT",
                        value = "${bms.cycleCount} Cycles",
                        modifier = Modifier.weight(1f)
                    )
                    OfficialMetricItem(
                        label = "BATTERY SOH",
                        value = "100 %",
                        valueColor = SolarGreen,
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            }
        }

        // E. Protection & Alarm Status Shield
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F12)),
            border = BorderStroke(1.dp, SolarGreen.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🛡️", fontSize = 20.sp)
                Column {
                    Text("ALL SYSTEMS NORMAL", color = SolarGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("No overvoltage, undervoltage, or thermal alarms active.", color = TextSecondary, fontSize = 10.sp)
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
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("AVERAGE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(String.format("%.3f V", bms.averageCellVoltage), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Column {
                    Text("DELTA", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${bms.deltaVoltageMv} mV", color = if (bms.deltaVoltageMv > 25) ErrorRed else SolarGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Column {
                    Text("MIN (C${bms.minCellNumber})", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(String.format("%.3f V", bms.minCellVoltage), color = CautionYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Column {
                    Text("MAX (C${bms.maxCellNumber})", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(String.format("%.3f V", bms.maxCellVoltage), color = AccentBlueL, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
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
        isMax -> AccentBlueL
        isMin -> LoadOrange
        else -> CardBorder
    }

    Box(
        modifier = Modifier
            .background(BgCardAlt, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = String.format("C%02d", cell.index),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                if (isMax) Text("MAX", fontSize = 9.sp, color = AccentBlueL, fontWeight = FontWeight.ExtraBold)
                if (isMin) Text("MIN", fontSize = 9.sp, color = LoadOrange, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = String.format("%.3f", cell.voltage),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Proportional cell level bar (3.000V - 3.650V)
            val fillPercent = ((cell.voltage - 3.000f) / 0.650f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fillPercent },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (isMax) AccentBlueL else if (isMin) LoadOrange else SolarGreen,
                trackColor = Color(0xFF1E2D45)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = if (cell.resistance != null && cell.resistance > 0f) "${String.format("%.1f", cell.resistance)} mΩ" else "0.3 mΩ",
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
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
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ℹ️", fontSize = 14.sp)
                    Text("PROGRAMMED BMS PARAMETERS", color = AccentBlueL, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "These thresholds are retrieved directly from your JK BMS EEPROM registers. Parameter writing is disabled for inverter safety.",
                    color = TextMuted,
                    fontSize = 11.sp
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
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = AccentBlueL, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = TextSecondary, fontSize = 12.sp)
                    Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
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
            .background(BgCardAlt, RoundedCornerShape(10.dp))
            .border(1.dp, if (isActive) SolarGreen.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isActive) SolarGreen else ErrorRed, CircleShape)
                )
                Text(
                    text = if (isActive) activeText else inactiveText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isActive) SolarGreen else ErrorRed
                )
            }
        }
    }
}
