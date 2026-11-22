package com.jkbms.dualmonitor.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkbms.dualmonitor.ble.BmsConnectionManager
import com.jkbms.dualmonitor.model.BmsData
import com.jkbms.dualmonitor.model.DailyEnergyRecord
import com.jkbms.dualmonitor.model.TotalBankData
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Design System Constants
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

@Composable
fun EnergyFlowScreen(
    manager: BmsConnectionManager,
    onInspectCells: (BmsData) -> Unit
) {
    val b1 by manager.bms1.bmsState.collectAsState()
    val b2 by manager.bms2.bmsState.collectAsState()
    val bank by manager.totalBankState.collectAsState()
    val todayEnergy by manager.energyHistory.todayEnergy.collectAsState()
    val isGatewayConnected by manager.isGatewayMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with Live Status
        EnergyFlowHeader(bank = bank, isGatewayConnected = isGatewayConnected)

        // 2. Animated Synoptic Energy Flow Diagram
        EnergyFlowDiagram(
            bank = bank,
            todayEnergy = todayEnergy,
            b1 = b1,
            b2 = b2
        )

        // 3. Real-Time Power Distribution Matrix
        LivePowerDistributionCard(bank = bank, todayEnergy = todayEnergy)

        // 4. Parallel Battery Packs Quick Comparison
        ParallelPacksCard(
            b1 = b1,
            b2 = b2,
            onInspectB1 = { onInspectCells(b1) },
            onInspectB2 = { onInspectCells(b2) }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun EnergyFlowHeader(bank: TotalBankData, isGatewayConnected: Boolean = true) {
    val isCharging = isGatewayConnected && bank.current > 0.05f
    val isDischarging = isGatewayConnected && bank.current < -0.05f

    val modeTitle = when {
        !isGatewayConnected -> "GATEWAY OFFLINE"
        isCharging -> "SOLAR CHARGING"
        isDischarging -> "BATTERY DISCHARGING"
        else -> "STANDBY / FLOAT"
    }

    val modeColor = when {
        !isGatewayConnected -> ErrorRed
        isCharging -> SolarGreen
        isDischarging -> LoadOrange
        else -> AccentBlue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "ENERGY FLOW",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(modeColor)
                )
                Text(
                    text = modeTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = modeColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun EnergyFlowDiagram(
    bank: TotalBankData,
    todayEnergy: DailyEnergyRecord,
    b1: BmsData,
    b2: BmsData
) {
    val isCharging = bank.current > 0.05f
    val isDischarging = bank.current < -0.05f

    // Solar PV Power (when charging battery)
    val pvPower = if (isCharging) bank.power else 0f
    // Battery Load Power (when discharging to house)
    val houseLoadPower = if (isDischarging) abs(bank.power) else 0f

    // Animated Flow Particle Engine
    val infiniteTransition = rememberInfiniteTransition(label = "power_flow_anim")
    val flowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    abs(bank.power) > 2000f -> 800
                    abs(bank.power) > 1000f -> 1200
                    abs(bank.power) > 300f -> 1600
                    abs(bank.power) > 50f -> 2200
                    else -> 3000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(410.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()

            val solarCenter = Offset(width * 0.5f, height * 0.16f)
            val hubCenter = Offset(width * 0.5f, height * 0.49f)
            val batteryCenter = Offset(width * 0.23f, height * 0.81f)
            val houseCenter = Offset(width * 0.77f, height * 0.81f)

            // 1. Canvas Layer: Renders Animated Flow Lines & Particles
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseTrackColor = Color(0xFF1E2D45)
                val strokeWidthPx = 4.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)

                // Line 1: Solar to Hub
                drawLine(
                    color = if (isCharging) SolarGreen.copy(alpha = 0.35f) else baseTrackColor,
                    start = solarCenter,
                    end = hubCenter,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Line 2: Hub to Battery
                drawLine(
                    color = if (isCharging) SolarGreen.copy(alpha = 0.35f) else if (isDischarging) LoadOrange.copy(alpha = 0.35f) else baseTrackColor,
                    start = hubCenter,
                    end = batteryCenter,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Line 3: Hub to House
                drawLine(
                    color = if (isDischarging) LoadOrange.copy(alpha = 0.35f) else if (isCharging) SolarGreen.copy(alpha = 0.25f) else baseTrackColor,
                    start = hubCenter,
                    end = houseCenter,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Animated Flow Particles
                if (isCharging) {
                    // Flow A: Solar -> Hub (Downward)
                    drawFlowParticles(
                        start = solarCenter,
                        end = hubCenter,
                        progress = flowProgress,
                        particleColor = SolarGreen,
                        count = 4
                    )
                    // Flow B: Hub -> Battery (Down-Left)
                    drawFlowParticles(
                        start = hubCenter,
                        end = batteryCenter,
                        progress = flowProgress,
                        particleColor = SolarGreen,
                        count = 4
                    )
                } else if (isDischarging) {
                    // Flow C: Battery -> Hub (Up-Right)
                    drawFlowParticles(
                        start = batteryCenter,
                        end = hubCenter,
                        progress = flowProgress,
                        particleColor = LoadOrange,
                        count = 4
                    )
                    // Flow D: Hub -> House (Down-Right)
                    drawFlowParticles(
                        start = hubCenter,
                        end = houseCenter,
                        progress = flowProgress,
                        particleColor = LoadOrange,
                        count = 4
                    )
                }
            }

            // 2. UI Nodes Layer
            // Node 1: Solar Panels (Top)
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = 0.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                SolarNodeWidget(
                    pvPower = pvPower,
                    todayHarvestKwh = todayEnergy.chargedKwh,
                    isGenerating = isCharging
                )
            }

            // Node 2: Smart DC Bus Hub (Center)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CenterHubWidget(isCharging = isCharging, isDischarging = isDischarging)
            }

            // Node 3: Battery Bank (Bottom Left)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                BatteryNodeWidget(
                    bank = bank,
                    b1 = b1,
                    b2 = b2,
                    isCharging = isCharging,
                    isDischarging = isDischarging
                )
            }

            // Node 4: House Load (Bottom Right)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                HouseNodeWidget(
                    loadPower = houseLoadPower,
                    todayLoadKwh = todayEnergy.dischargedKwh,
                    isConsuming = isDischarging
                )
            }
        }
    }
}

// Particle rendering helper for animated energy currents
fun DrawScope.drawFlowParticles(
    start: Offset,
    end: Offset,
    progress: Float,
    particleColor: Color,
    count: Int = 4
) {
    for (i in 0 until count) {
        val t = (progress + (i.toFloat() / count.toFloat())) % 1.0f
        val pos = Offset(
            x = start.x + t * (end.x - start.x),
            y = start.y + t * (end.y - start.y)
        )
        // Radiant Outer Glow
        drawCircle(
            color = particleColor.copy(alpha = 0.35f),
            radius = 7.dp.toPx(),
            center = pos
        )
        // Bright Inner Core
        drawCircle(
            color = particleColor,
            radius = 3.5f.dp.toPx(),
            center = pos
        )
        // Intense White Center Spark
        drawCircle(
            color = Color.White,
            radius = 1.8f.dp.toPx(),
            center = pos
        )
    }
}

@Composable
fun SolarNodeWidget(
    pvPower: Float,
    todayHarvestKwh: Float,
    isGenerating: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCardAlt),
        border = BorderStroke(1.dp, if (isGenerating) SolarGreen.copy(alpha = 0.5f) else CardBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(170.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("☀️", fontSize = 16.sp)
                Text(
                    text = "SOLAR PV",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isGenerating) SolarGreen else TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isGenerating) formatPower(pvPower) else "0 W",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isGenerating) SolarGreen else TextPrimary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Day: ${formatEnergy(todayHarvestKwh)}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun CenterHubWidget(isCharging: Boolean, isDischarging: Boolean) {
    val hubColor = when {
        isCharging -> SolarGreen
        isDischarging -> LoadOrange
        else -> AccentBlue
    }

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(BgCardAlt)
            .border(2.dp, hubColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚡", fontSize = 18.sp)
            Text("DC BUS", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = hubColor, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun BatteryNodeWidget(
    bank: TotalBankData,
    b1: BmsData,
    b2: BmsData,
    isCharging: Boolean,
    isDischarging: Boolean
) {
    val soc = bank.capacityWeightedSoc.coerceIn(0, 100)
    // Updated threshold per user request: Green > 50%, Amber > 10%, Red <= 10%
    val socColor = when {
        soc > 50 -> SolarGreen
        soc > 10 -> CautionYellow
        else     -> ErrorRed
    }

    val accentColor = when {
        isCharging -> SolarGreen
        isDischarging -> LoadOrange
        else -> AccentBlue
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BgCardAlt),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(160.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🔋", fontSize = 16.sp)
                Text(
                    text = "BATTERY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$soc",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = socColor,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleSmall,
                    color = socColor,
                    modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                )
            }
            Text(
                text = String.format(Locale.US, "%.1fV • %.1fA", bank.voltage, bank.current),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "B1:${b1.soc}% • B2:${b2.soc}%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun HouseNodeWidget(
    loadPower: Float,
    todayLoadKwh: Float,
    isConsuming: Boolean
) {
    val accentColor = if (isConsuming) LoadOrange else CardBorder

    Card(
        colors = CardDefaults.cardColors(containerColor = BgCardAlt),
        border = BorderStroke(1.dp, accentColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(160.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🏠", fontSize = 16.sp)
                Text(
                    text = "HOUSE LOAD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isConsuming) LoadOrange else TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isConsuming) formatPower(loadPower) else "0 W",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isConsuming) LoadOrange else TextPrimary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Day: ${formatEnergy(todayLoadKwh)}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = TextSecondary
            )
            Text(
                text = if (isConsuming) "Battery Discharging" else "Standby / Floating",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun LivePowerDistributionCard(bank: TotalBankData, todayEnergy: DailyEnergyRecord) {
    val isCharging = bank.current > 0.05f
    val isDischarging = bank.current < -0.05f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "POWER DISTRIBUTION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PowerStatItem(
                    label = "SOLAR HARVEST",
                    value = if (isCharging) formatPower(bank.power) else "0 W",
                    color = SolarGreen,
                    sub = "Day: ${formatEnergy(todayEnergy.chargedKwh)}"
                )
                PowerStatItem(
                    label = "BATTERY NET",
                    value = String.format(Locale.US, "%s%.2f kW", if (bank.current > 0.05f) "+" else "", bank.power / 1000f),
                    color = if (bank.current > 0.05f) SolarGreen else if (bank.current < -0.05f) LoadOrange else TextPrimary,
                    sub = "${bank.capacityWeightedSoc}% · ${String.format(Locale.US, "%.1f", bank.remainingCapacityAh)}Ah"
                )
                PowerStatItem(
                    label = "LOAD FROM BATT",
                    value = if (isDischarging) formatPower(abs(bank.power)) else "0 W",
                    color = LoadOrange,
                    sub = "Day: ${formatEnergy(todayEnergy.dischargedKwh)}"
                )
            }
        }
    }
}

@Composable
fun PowerStatItem(label: String, value: String, color: Color, sub: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = sub, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
fun ParallelPacksCard(
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PARALLEL PACK CONTRIBUTIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val totalAbsCurrent = abs(b1.current) + abs(b2.current)
                val b1Share = if (totalAbsCurrent > 0.1f) {
                    ((abs(b1.current) / totalAbsCurrent) * 100f).roundToInt()
                } else {
                    50
                }
                val b2Share = if (totalAbsCurrent > 0.1f) {
                    ((abs(b2.current) / totalAbsCurrent) * 100f).roundToInt()
                } else {
                    50
                }

                val b1Name = if (b1.displayName.isNotBlank()) b1.displayName.replace(" (24S)", "").replace(" (20S)", "") else "48V 100Ah #1"
                val b2Name = if (b2.displayName.isNotBlank()) b2.displayName.replace(" (24S)", "").replace(" (20S)", "") else "48V 100Ah #2"

                PackMiniSummary(
                    modifier = Modifier.weight(1f),
                    title = b1Name,
                    bms = b1,
                    sharePercent = b1Share,
                    onInspect = onInspectB1
                )
                PackMiniSummary(
                    modifier = Modifier.weight(1f),
                    title = b2Name,
                    bms = b2,
                    sharePercent = b2Share,
                    onInspect = onInspectB2
                )
            }
        }
    }
}

@Composable
fun PackMiniSummary(
    modifier: Modifier = Modifier,
    title: String,
    bms: BmsData,
    sharePercent: Int,
    onInspect: () -> Unit
) {
    val soc = bms.soc.coerceIn(0, 100)
    // Updated threshold per user request: Green > 50%, Amber > 10%, Red <= 10%
    val socColor = when {
        soc > 50 -> SolarGreen
        soc > 10 -> CautionYellow
        else     -> ErrorRed
    }

    Card(
        modifier = modifier.clickable { onInspect() },
        colors = CardDefaults.cardColors(containerColor = BgCardAlt),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    color = AccentBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$sharePercent% share",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentBlueL,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SOC: $soc%",
                    style = MaterialTheme.typography.labelSmall,
                    color = socColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.US, "%.0f W", bms.power),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bms.power > 0.5f) SolarGreen else if (bms.power < -0.5f) LoadOrange else TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "%.2f V · %.1f A", bms.voltage, bms.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Δ ${bms.deltaVoltageMv} mV",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (bms.deltaVoltageMv > 50) ErrorRed else SolarGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatPower(watts: Float): String {
    val absW = abs(watts)
    return if (absW >= 1000f) {
        String.format(Locale.US, "%.2f kW", absW / 1000f)
    } else {
        String.format(Locale.US, "%.0f W", absW)
    }
}
