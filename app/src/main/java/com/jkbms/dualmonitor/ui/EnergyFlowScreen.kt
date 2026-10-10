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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkbms.dualmonitor.ble.BmsConnectionManager
import com.jkbms.dualmonitor.model.BmsData
import com.jkbms.dualmonitor.model.ConnectionStatus
import com.jkbms.dualmonitor.model.DailyEnergyRecord
import com.jkbms.dualmonitor.model.TotalBankData
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EnergyFlowScreen(
    manager: BmsConnectionManager,
    onInspectCells: (BmsData) -> Unit
) {
    val b1 by manager.bms1.bmsState.collectAsState()
    val b2 by manager.bms2.bmsState.collectAsState()
    val bank by manager.totalBankState.collectAsState()
    val todayEnergy by manager.energyHistory.todayEnergy.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with Live Status
        EnergyFlowHeader(bank = bank)

        // 2. Animated SolaX-Style Synoptic Energy Flow Diagram
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
fun EnergyFlowHeader(bank: TotalBankData) {
    val isCharging = bank.current > 0.05f
    val isDischarging = bank.current < -0.05f

    val modeTitle = when {
        isCharging -> "SOLAR CHARGING"
        isDischarging -> "BATTERY DISCHARGING"
        else -> "STANDBY / FLOAT"
    }

    val modeColor = when {
        isCharging -> Color(0xFF00E676)
        isDischarging -> Color(0xFFFF9100)
        else -> Color(0xFF2979FF)
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
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(modeColor)
                )
                Text(
                    text = modeTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = modeColor
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
        border = BorderStroke(1.dp, Color(0xFF242C3D)),
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
                // Background Track Lines
                val baseTrackColor = Color(0xFF1F2839)
                val strokeWidthPx = 4.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)

                // Line 1: Solar to Hub
                drawLine(
                    color = if (isCharging) Color(0xFF00E676).copy(alpha = 0.35f) else baseTrackColor,
                    start = solarCenter,
                    end = hubCenter,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Line 2: Hub to Battery
                drawLine(
                    color = if (isCharging) Color(0xFF00E676).copy(alpha = 0.35f) else if (isDischarging) Color(0xFFFF9100).copy(alpha = 0.35f) else baseTrackColor,
                    start = hubCenter,
                    end = batteryCenter,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Line 3: Hub to House
                drawLine(
                    color = if (isDischarging) Color(0xFFFF9100).copy(alpha = 0.35f) else if (isCharging) Color(0xFF00E676).copy(alpha = 0.25f) else baseTrackColor,
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
                        particleColor = Color(0xFF00E676),
                        count = 4
                    )
                    // Flow B: Hub -> Battery (Down-Left)
                    drawFlowParticles(
                        start = hubCenter,
                        end = batteryCenter,
                        progress = flowProgress,
                        particleColor = Color(0xFF00E676),
                        count = 4
                    )
                } else if (isDischarging) {
                    // Flow C: Battery -> Hub (Up-Right)
                    drawFlowParticles(
                        start = batteryCenter,
                        end = hubCenter,
                        progress = flowProgress,
                        particleColor = Color(0xFFFF9100),
                        count = 4
                    )
                    // Flow D: Hub -> House (Down-Right)
                    drawFlowParticles(
                        start = hubCenter,
                        end = houseCenter,
                        progress = flowProgress,
                        particleColor = Color(0xFFFF9100),
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
                modifier = Modifier
                    .fillMaxSize(),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2234)),
        border = BorderStroke(1.5.dp, if (isGenerating) Color(0xFF00E676) else Color(0xFF2A364F)),
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
                    color = if (isGenerating) Color(0xFF00E676) else Color(0xFF90A4AE)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isGenerating) formatPower(pvPower) else "0 W",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isGenerating) Color(0xFF00E676) else Color.White
            )
            Text(
                text = "Day: ${formatEnergy(todayHarvestKwh)}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color(0xFFCFD8DC)
            )
        }
    }
}

@Composable
fun CenterHubWidget(isCharging: Boolean, isDischarging: Boolean) {
    val hubColor = when {
        isCharging -> Color(0xFF00E676)
        isDischarging -> Color(0xFFFF9100)
        else -> Color(0xFF2979FF)
    }

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color(0xFF1A2234))
            .border(2.dp, hubColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚡", fontSize = 18.sp)
            Text("DC BUS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = hubColor)
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
    val soc = bank.capacityWeightedSoc
    val accentColor = when {
        isCharging -> Color(0xFF00E676)
        isDischarging -> Color(0xFFFF9100)
        else -> Color(0xFF2979FF)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2234)),
        border = BorderStroke(1.5.dp, accentColor),
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
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$soc",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                )
            }
            Text(
                text = String.format(Locale.US, "%.1fV • %.1fA", bank.voltage, bank.current),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color(0xFFCFD8DC)
            )
            Text(
                text = "B1:${b1.soc}% • B2:${b2.soc}%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = Color(0xFF90A4AE)
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
    val accentColor = if (isConsuming) Color(0xFFFF9100) else Color(0xFF2A364F)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2234)),
        border = BorderStroke(1.5.dp, accentColor),
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
                    color = if (isConsuming) Color(0xFFFF9100) else Color(0xFF90A4AE)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isConsuming) formatPower(loadPower) else "0 W",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isConsuming) Color(0xFFFF9100) else Color.White
            )
            Text(
                text = "Day: ${formatEnergy(todayLoadKwh)}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color(0xFFCFD8DC)
            )
            Text(
                text = if (isConsuming) "Battery Discharging" else "Solar / Direct Feed",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = Color(0xFF90A4AE)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF262C38))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "POWER MATRIX",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PowerStatItem(
                    label = "SOLAR HARVEST",
                    value = if (isCharging) formatPower(bank.power) else "0 W",
                    color = Color(0xFF00E676),
                    sub = "Today: ${formatEnergy(todayEnergy.chargedKwh)}"
                )
                PowerStatItem(
                    label = "BATTERY NET",
                    value = String.format(Locale.US, "%s%.2f kW", if (bank.current > 0.05f) "+" else "", bank.power / 1000f),
                    color = if (bank.current > 0.05f) Color(0xFF00E676) else if (bank.current < -0.05f) Color(0xFFFF9100) else Color.White,
                    sub = "${bank.capacityWeightedSoc}% • ${String.format(Locale.US, "%.1f", bank.remainingCapacityAh)}Ah"
                )
                PowerStatItem(
                    label = "LOAD FROM BATT",
                    value = if (isDischarging) formatPower(abs(bank.power)) else "0 W",
                    color = Color(0xFFFF9100),
                    sub = "Today: ${formatEnergy(todayEnergy.dischargedKwh)}"
                )
            }
        }
    }
}

@Composable
fun PowerStatItem(label: String, value: String, color: Color, sub: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = sub, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color(0xFFCFD8DC))
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF262C38))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PARALLEL PACK CONTRIBUTIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PackMiniSummary(
                    modifier = Modifier.weight(1f),
                    title = if (b1.displayName.isNotBlank()) b1.displayName else "Pack 1 (B1)",
                    bms = b1,
                    onInspect = onInspectB1
                )
                PackMiniSummary(
                    modifier = Modifier.weight(1f),
                    title = if (b2.displayName.isNotBlank()) b2.displayName else "Pack 2 (B2)",
                    bms = b2,
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
    onInspect: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onInspect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
        border = BorderStroke(1.dp, Color(0xFF242C3D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${bms.soc}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format(Locale.US, "%.2f V • %.1f A", bms.voltage, bms.current),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCFD8DC)
            )
            Text(
                text = String.format(Locale.US, "Power: %.0f W", bms.power),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF90A4AE)
            )
            Text(
                text = "Delta: ${bms.deltaVoltageMv} mV",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = if (bms.deltaVoltageMv > 50) Color(0xFFFF5252) else Color(0xFF00E676)
            )
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
