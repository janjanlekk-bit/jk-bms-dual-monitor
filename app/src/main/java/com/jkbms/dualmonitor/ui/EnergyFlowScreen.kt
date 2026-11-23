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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
            .height(350.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()

            // 1. Canvas Layer: Renders Stepped Dashed Conduits & Animated Particles
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseTrackColor = Color(0xFF1E2D45)
                val strokeWidthPx = 3.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)

                // Key Conduit Coordinates (Stepped Circuit 100% centered to each icon with clear gaps)
                val leftColumnCenterX = 34.dp.toPx()
                val rightColumnCenterX = width - 36.dp.toPx()
                val inverterLeftX = width * 0.5f - 44.dp.toPx()
                val inverterRightX = width * 0.5f + 44.dp.toPx()

                // Clean gap between icon and conduit line
                val solarStart = Offset(leftColumnCenterX, 82.dp.toPx())
                val solarCorner = Offset(leftColumnCenterX, height * 0.44f)
                val solarEnd = Offset(inverterLeftX, height * 0.44f)

                val houseStart = Offset(rightColumnCenterX, 82.dp.toPx())
                val houseCorner = Offset(rightColumnCenterX, height * 0.44f)
                val houseEnd = Offset(inverterRightX, height * 0.44f)

                // Distinct gap above battery top cap
                val batteryStart = Offset(leftColumnCenterX, height - 108.dp.toPx())
                val batteryCorner = Offset(leftColumnCenterX, height * 0.56f)
                val batteryEnd = Offset(inverterLeftX, height * 0.56f)

                // Conduit 1: Solar to Inverter (Blue line, centered to solar panel)
                val solarTrackColor = if (isCharging) Color(0xFF2979FF).copy(alpha = 0.5f) else baseTrackColor
                drawLine(
                    color = solarTrackColor,
                    start = solarStart,
                    end = solarCorner,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = solarTrackColor,
                    start = solarCorner,
                    end = solarEnd,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Conduit 2: House to Inverter (Red line, centered to house)
                val houseTrackColor = if (isDischarging) Color(0xFFFF5252).copy(alpha = 0.5f) else baseTrackColor
                drawLine(
                    color = houseTrackColor,
                    start = houseStart,
                    end = houseCorner,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = houseTrackColor,
                    start = houseCorner,
                    end = houseEnd,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Conduit 3: Battery to Inverter (Green line, centered to battery)
                val batteryTrackColor = if (isCharging || isDischarging) Color(0xFF00E676).copy(alpha = 0.5f) else baseTrackColor
                drawLine(
                    color = batteryTrackColor,
                    start = batteryStart,
                    end = batteryCorner,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = batteryTrackColor,
                    start = batteryCorner,
                    end = batteryEnd,
                    strokeWidth = strokeWidthPx,
                    pathEffect = dashEffect,
                    cap = StrokeCap.Round
                )

                // Animated Flow Particles
                if (isCharging) {
                    // Flow: Solar -> Inverter (stepped)
                    drawSteppedFlowParticles(
                        start = solarStart,
                        corner = solarCorner,
                        end = solarEnd,
                        progress = flowProgress,
                        particleColor = Color(0xFF2979FF),
                        count = 3
                    )
                    // Flow: Inverter -> Battery (stepped, reversed: batteryEnd -> batteryCorner -> batteryStart)
                    drawSteppedFlowParticles(
                        start = batteryEnd,
                        corner = batteryCorner,
                        end = batteryStart,
                        progress = flowProgress,
                        particleColor = Color(0xFF00E676),
                        count = 3
                    )
                } else if (isDischarging) {
                    // Flow: Battery -> Inverter (batteryStart -> batteryCorner -> batteryEnd)
                    drawSteppedFlowParticles(
                        start = batteryStart,
                        corner = batteryCorner,
                        end = batteryEnd,
                        progress = flowProgress,
                        particleColor = Color(0xFFFF9100),
                        count = 3
                    )
                    // Flow: Inverter -> House (houseEnd -> houseCorner -> houseStart)
                    drawSteppedFlowParticles(
                        start = houseEnd,
                        corner = houseCorner,
                        end = houseStart,
                        progress = flowProgress,
                        particleColor = Color(0xFFFF5252),
                        count = 3
                    )
                }
            }

            // 2. Interactive UI Nodes (Arranged snug next to each icon, right-aligned)
            // Top Row: Solar Panel (Left) & House Load (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left: Solar Panel Icon + Power & Current snug next to icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SolarPanelWithSunIcon()
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = formatPower(pvPower),
                            color = Color(0xFF2979FF),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f A", if (isCharging) abs(bank.current) else 0f),
                            color = Color(0xFF40C4FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                }

                // Top-Right: Power & Current snug next to House Load Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = formatPower(houseLoadPower),
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f A", if (isDischarging) abs(bank.current) else 0f),
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    HouseLoadIcon()
                }
            }

            // Center: Inverter Hub alone
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                InverterHubIcon()
            }

            // Bottom Row: Battery (Left) & Off-Grid Status (Right, no grid icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom-Left: Segmented Battery Icon with status label + Power & Current snug next to icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SegmentedBatteryIcon(
                        soc = bank.capacityWeightedSoc,
                        isCharging = isCharging,
                        isDischarging = isDischarging
                    )
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = formatPower(abs(bank.power)),
                            color = if (isCharging) Color(0xFF00E676) else if (isDischarging) Color(0xFFFF9100) else Color(0xFF8F9BB3),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f A", abs(bank.current)),
                            color = if (isCharging) Color(0xFF00E676) else if (isDischarging) Color(0xFFFF9100) else Color(0xFF8F9BB3),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                }

                // Bottom-Right: Voltage & Frequency snug next to Clean Off-Grid Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "230.0 V",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        Text(
                            text = "60.0 HZ",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    Surface(
                        color = Color(0xFF0D1E3A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E3A5F))
                    ) {
                        Text(
                            text = "☀️\nOFF-GRID",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF82B1FF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

// Particle rendering helper for stepped energy conduits
fun DrawScope.drawSteppedFlowParticles(
    start: Offset,
    corner: Offset,
    end: Offset,
    progress: Float,
    particleColor: Color,
    count: Int = 3
) {
    val d1 = kotlin.math.hypot((corner.x - start.x).toDouble(), (corner.y - start.y).toDouble()).toFloat()
    val d2 = kotlin.math.hypot((end.x - corner.x).toDouble(), (end.y - corner.y).toDouble()).toFloat()
    val totalD = d1 + d2
    if (totalD <= 0f) return

    for (i in 0 until count) {
        val t = (progress + (i.toFloat() / count.toFloat())) % 1.0f
        val curDist = t * totalD
        val pos = if (curDist <= d1) {
            val frac = if (d1 > 0f) curDist / d1 else 0f
            Offset(start.x + frac * (corner.x - start.x), start.y + frac * (corner.y - start.y))
        } else {
            val frac = if (d2 > 0f) (curDist - d1) / d2 else 0f
            Offset(corner.x + frac * (end.x - corner.x), corner.y + frac * (end.y - corner.y))
        }

        // Radiant Outer Glow
        drawCircle(
            color = particleColor.copy(alpha = 0.35f),
            radius = 6.dp.toPx(),
            center = pos
        )
        // Bright Inner Core
        drawCircle(
            color = particleColor,
            radius = 3.dp.toPx(),
            center = pos
        )
        // White Spark Center
        drawCircle(
            color = Color.White,
            radius = 1.5f.dp.toPx(),
            center = pos
        )
    }
}

// -------------------------------------------------------------
// Illustrated Custom Vector Icons (matching user reference image)
// -------------------------------------------------------------

@Composable
fun SolarPanelWithSunIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 68.dp, height = 76.dp)) {
        val w = size.width
        val h = size.height

        // 1. Sun at Top-Left of the panel
        val sunCenter = Offset(w * 0.20f, h * 0.18f)
        val sunRadius = 6.5f.dp.toPx()
        for (i in 0 until 8) {
            val angle = (i * 45) * (Math.PI / 180.0)
            val r1 = sunRadius + 2.dp.toPx()
            val r2 = sunRadius + 5.5f.dp.toPx()
            val start = Offset(
                (sunCenter.x + r1 * Math.cos(angle)).toFloat(),
                (sunCenter.y + r1 * Math.sin(angle)).toFloat()
            )
            val end = Offset(
                (sunCenter.x + r2 * Math.cos(angle)).toFloat(),
                (sunCenter.y + r2 * Math.sin(angle)).toFloat()
            )
            drawLine(
                color = Color(0xFFFFA000),
                start = start,
                end = end,
                strokeWidth = 1.8f.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = Color(0xFFFFB300),
            radius = sunRadius,
            center = sunCenter
        )
        drawCircle(
            color = Color(0xFFFFE082),
            radius = sunRadius * 0.65f,
            center = sunCenter
        )

        // 2. Stand & Legs at bottom
        val legColor = Color(0xFF78909C)
        val legStroke = 2.5f.dp.toPx()
        drawLine(
            color = legColor,
            start = Offset(w * 0.35f, h * 0.72f),
            end = Offset(w * 0.35f, h * 0.88f),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = legColor,
            start = Offset(w * 0.65f, h * 0.72f),
            end = Offset(w * 0.65f, h * 0.88f),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF90A4AE),
            start = Offset(w * 0.22f, h * 0.88f),
            end = Offset(w * 0.78f, h * 0.88f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 3. Tilted Solar Panel with Grid Cells
        val topLeft = Offset(w * 0.22f, h * 0.22f)
        val topRight = Offset(w * 0.90f, h * 0.22f)
        val bottomRight = Offset(w * 0.96f, h * 0.72f)
        val bottomLeft = Offset(w * 0.16f, h * 0.72f)

        val panelPath = Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }

        drawPath(path = panelPath, color = Color(0xFF0D47A1))
        drawPath(path = panelPath, color = Color(0xFFCFD8DC), style = Stroke(width = 2.5f.dp.toPx()))

        val gridColor = Color(0xFF90CAF9).copy(alpha = 0.85f)
        val gridStroke = 1.2f.dp.toPx()

        for (col in 1..3) {
            val frac = col / 4f
            val topP = Offset(
                topLeft.x + frac * (topRight.x - topLeft.x),
                topLeft.y + frac * (topRight.y - topLeft.y)
            )
            val botP = Offset(
                bottomLeft.x + frac * (bottomRight.x - bottomLeft.x),
                bottomLeft.y + frac * (bottomRight.y - bottomLeft.y)
            )
            drawLine(color = gridColor, start = topP, end = botP, strokeWidth = gridStroke)
        }

        for (row in 1..5) {
            val frac = row / 6f
            val leftP = Offset(
                topLeft.x + frac * (bottomLeft.x - topLeft.x),
                topLeft.y + frac * (bottomLeft.y - topLeft.y)
            )
            val rightP = Offset(
                topRight.x + frac * (bottomRight.x - topRight.x),
                topRight.y + frac * (bottomRight.y - topRight.y)
            )
            drawLine(color = gridColor, start = leftP, end = rightP, strokeWidth = gridStroke)
        }

        for (col in 1..3) {
            val fracC = col / 4f
            for (row in 1..5) {
                val fracR = row / 6f
                val topP = Offset(
                    topLeft.x + fracC * (topRight.x - topLeft.x),
                    topLeft.y + fracC * (topRight.y - topLeft.y)
                )
                val botP = Offset(
                    bottomLeft.x + fracC * (bottomRight.x - bottomLeft.x),
                    bottomLeft.y + fracC * (bottomRight.y - bottomLeft.y)
                )
                val dotX = topP.x + fracR * (botP.x - topP.x)
                val dotY = topP.y + fracR * (botP.y - topP.y)
                drawCircle(color = Color.White, radius = 1.2f.dp.toPx(), center = Offset(dotX, dotY))
            }
        }
    }
}

@Composable
fun HouseLoadIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 72.dp, height = 76.dp)) {
        val w = size.width
        val h = size.height

        // 1. Chimney on Right
        val chimneyLeft = w * 0.74f
        val chimneyTop = h * 0.12f
        val chimneyWidth = w * 0.12f
        val chimneyHeight = h * 0.22f
        drawRect(
            color = Color(0xFF455A64),
            topLeft = Offset(chimneyLeft, chimneyTop),
            size = Size(chimneyWidth, chimneyHeight)
        )
        drawRoundRect(
            color = Color(0xFF263238),
            topLeft = Offset(chimneyLeft - w * 0.02f, chimneyTop - h * 0.03f),
            size = Size(chimneyWidth + w * 0.04f, h * 0.04f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // 2. House Walls
        val wallLeft = w * 0.18f
        val wallTop = h * 0.44f
        val wallWidth = w * 0.64f
        val wallHeight = h * 0.44f
        drawRect(
            color = Color(0xFFF1F5F9),
            topLeft = Offset(wallLeft, wallTop),
            size = Size(wallWidth, wallHeight)
        )

        // 3. Gable Attic Wall
        val roofPeak = Offset(w * 0.50f, h * 0.14f)
        val eaveLeft = Offset(w * 0.10f, h * 0.44f)
        val eaveRight = Offset(w * 0.90f, h * 0.44f)

        val gablePath = Path().apply {
            moveTo(roofPeak.x, roofPeak.y)
            lineTo(eaveRight.x, eaveRight.y)
            lineTo(eaveLeft.x, eaveLeft.y)
            close()
        }
        drawPath(path = gablePath, color = Color(0xFFF8FAFC))

        // 4. Red Roof Overhang
        val roofThickness = 4.dp.toPx()
        val roofPath = Path().apply {
            moveTo(roofPeak.x, roofPeak.y)
            lineTo(eaveRight.x, eaveRight.y)
            lineTo(eaveRight.x + w * 0.03f, eaveRight.y + h * 0.02f)
            lineTo(roofPeak.x, roofPeak.y - h * 0.02f)
            lineTo(eaveLeft.x - w * 0.03f, eaveLeft.y + h * 0.02f)
            lineTo(eaveLeft.x, eaveLeft.y)
            close()
        }
        drawPath(path = roofPath, color = Color(0xFFEF5350))
        drawLine(
            color = Color(0xFFD32F2F),
            start = Offset(eaveLeft.x - w * 0.02f, eaveLeft.y),
            end = roofPeak,
            strokeWidth = roofThickness,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFD32F2F),
            start = roofPeak,
            end = Offset(eaveRight.x + w * 0.02f, eaveRight.y),
            strokeWidth = roofThickness,
            cap = StrokeCap.Round
        )

        // 5. Red Lightning Bolts in Gable Attic
        val bolt1Path = Path().apply {
            moveTo(w * 0.43f, h * 0.28f)
            lineTo(w * 0.38f, h * 0.35f)
            lineTo(w * 0.43f, h * 0.35f)
            lineTo(w * 0.40f, h * 0.41f)
            lineTo(w * 0.48f, h * 0.33f)
            lineTo(w * 0.44f, h * 0.33f)
            close()
        }
        drawPath(path = bolt1Path, color = Color(0xFFE53935))

        val bolt2Path = Path().apply {
            moveTo(w * 0.57f, h * 0.28f)
            lineTo(w * 0.52f, h * 0.35f)
            lineTo(w * 0.57f, h * 0.35f)
            lineTo(w * 0.54f, h * 0.41f)
            lineTo(w * 0.62f, h * 0.33f)
            lineTo(w * 0.58f, h * 0.33f)
            close()
        }
        drawPath(path = bolt2Path, color = Color(0xFFE53935))

        // 6. Brown Front Door
        val doorLeft = w * 0.25f
        val doorTop = h * 0.52f
        val doorWidth = w * 0.22f
        val doorHeight = h * 0.36f
        drawRoundRect(
            color = Color(0xFF8D6E63),
            topLeft = Offset(doorLeft, doorTop),
            size = Size(doorWidth, doorHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 1.8f.dp.toPx(),
            center = Offset(doorLeft + doorWidth * 0.75f, doorTop + doorHeight * 0.5f)
        )

        // 7. Cyan 4-Pane Window
        val winLeft = w * 0.54f
        val winTop = h * 0.52f
        val winWidth = w * 0.22f
        val winHeight = h * 0.26f
        drawRoundRect(
            color = Color(0xFF00B0FF),
            topLeft = Offset(winLeft, winTop),
            size = Size(winWidth, winHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawLine(
            color = Color.White,
            start = Offset(winLeft, winTop + winHeight * 0.5f),
            end = Offset(winLeft + winWidth, winTop + winHeight * 0.5f),
            strokeWidth = 1.5f.dp.toPx()
        )
        drawLine(
            color = Color.White,
            start = Offset(winLeft + winWidth * 0.5f, winTop),
            end = Offset(winLeft + winWidth * 0.5f, winTop + winHeight),
            strokeWidth = 1.5f.dp.toPx()
        )

        // 8. Green Lawn Base
        drawRoundRect(
            color = Color(0xFF00C853),
            topLeft = Offset(w * 0.12f, h * 0.88f),
            size = Size(w * 0.76f, h * 0.05f),
            cornerRadius = CornerRadius(2.5f.dp.toPx())
        )
    }
}

@Composable
fun InverterHubIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(76.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)

        // 1. Circular Halo Badge
        drawCircle(
            color = Color(0xFFE2E8F0),
            radius = w * 0.48f,
            center = center
        )
        drawCircle(
            color = Color(0xFFCBD5E1),
            radius = w * 0.48f,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // 2. Wall Mounted Inverter Body
        val invWidth = w * 0.46f
        val invHeight = h * 0.58f
        val invLeft = center.x - invWidth / 2f
        val invTop = center.y - invHeight / 2f

        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(invLeft, invTop),
            size = Size(invWidth, invHeight),
            cornerRadius = CornerRadius(7.dp.toPx())
        )

        // 3. Inverter Top Screen / Display
        val screenMargin = 3.5f.dp.toPx()
        val screenHeight = invHeight * 0.38f
        drawRoundRect(
            color = Color(0xFF020617),
            topLeft = Offset(invLeft + screenMargin, invTop + screenMargin),
            size = Size(invWidth - screenMargin * 2, screenHeight),
            cornerRadius = CornerRadius(3.dp.toPx())
        )

        // Display Status Dots & Line
        val ledY = invTop + screenMargin + screenHeight * 0.4f
        drawCircle(
            color = Color(0xFF22C55E),
            radius = 1.4f.dp.toPx(),
            center = Offset(center.x - 5.dp.toPx(), ledY)
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = 1.4f.dp.toPx(),
            center = Offset(center.x + 5.dp.toPx(), ledY)
        )
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(center.x - 7.dp.toPx(), ledY + 4.dp.toPx()),
            end = Offset(center.x + 7.dp.toPx(), ledY + 4.dp.toPx()),
            strokeWidth = 1.2f.dp.toPx()
        )

        // 4. Lower Seam Divider
        drawLine(
            color = Color(0xFF334155),
            start = Offset(invLeft, invTop + invHeight * 0.52f),
            end = Offset(invLeft + invWidth, invTop + invHeight * 0.52f),
            strokeWidth = 1.5f.dp.toPx()
        )

        // 5. White Lightning Bolt on Lower Compartment
        val boltY = invTop + invHeight * 0.72f
        val boltPath = Path().apply {
            moveTo(center.x, boltY - 6.dp.toPx())
            lineTo(center.x - 4.dp.toPx(), boltY)
            lineTo(center.x - 0.5f.dp.toPx(), boltY)
            lineTo(center.x - 2.dp.toPx(), boltY + 6.dp.toPx())
            lineTo(center.x + 5.dp.toPx(), boltY - 1.dp.toPx())
            lineTo(center.x + 1.dp.toPx(), boltY - 1.dp.toPx())
            close()
        }
        drawPath(path = boltPath, color = Color.White)
    }
}

@Composable
fun SegmentedBatteryIcon(
    soc: Int,
    isCharging: Boolean,
    isDischarging: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(68.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(width = 46.dp, height = 72.dp)) {
            val w = size.width
            val h = size.height

            // 1. Top Terminal Nub
            val nubWidth = w * 0.32f
            val nubHeight = h * 0.06f
            drawRoundRect(
                color = Color(0xFF475569),
                topLeft = Offset((w - nubWidth) / 2f, 0f),
                size = Size(nubWidth, nubHeight + 2.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // 2. Battery Outer Capsule
            val bodyLeft = 2.dp.toPx()
            val bodyTop = nubHeight
            val bodyWidth = w - 4.dp.toPx()
            val bodyHeight = h - nubHeight - 2.dp.toPx()
            val cornerRadius = CornerRadius(8.dp.toPx())

            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = Color(0xFF475569),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.5f.dp.toPx())
            )

            // 3. Segmented Horizontal Charge Bars (5 bars)
            val barCount = 5
            val paddingH = 4.5f.dp.toPx()
            val barWidth = bodyWidth - paddingH * 2
            val totalBarSpace = bodyHeight - 10.dp.toPx()
            val gap = 3.dp.toPx()
            val barHeight = (totalBarSpace - (gap * (barCount - 1))) / barCount

            val activeBars = when {
                soc >= 90 -> 5
                soc >= 70 -> 4
                soc >= 45 -> 3
                soc >= 20 -> 2
                soc > 0   -> 1
                else      -> 0
            }

            // Green > 50%, Amber > 10%, Red <= 10%
            val barColor = when {
                soc > 50 -> Color(0xFF00E676)
                soc > 10 -> Color(0xFFFFD600)
                else     -> Color(0xFFFF1744)
            }

            for (i in 0 until barCount) {
                val isLit = (i < activeBars)
                val barTop = (bodyTop + bodyHeight - 5.dp.toPx()) - ((i + 1) * barHeight) - (i * gap)

                drawRoundRect(
                    color = if (isLit) barColor else Color(0xFF1E293B),
                    topLeft = Offset(bodyLeft + paddingH, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.5f.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val stateText = when {
            isCharging -> "Charging"
            isDischarging -> "Discharging"
            else -> "Standby"
        }
        val stateColor = when {
            isCharging -> Color(0xFF00E676)
            isDischarging -> Color(0xFFFF9100)
            else -> Color(0xFF82B1FF)
        }
        Text(
            text = stateText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = stateColor,
            textAlign = TextAlign.Center
        )
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
