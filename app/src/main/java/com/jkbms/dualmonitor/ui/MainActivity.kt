package com.jkbms.dualmonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jkbms.dualmonitor.ble.BmsConnectionManager
import com.jkbms.dualmonitor.model.BmsData
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    private lateinit var manager: BmsConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = BmsConnectionManager(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF101216),
                surface = Color(0xFF1A1D24),
                primary = Color(0xFF00E676),
                secondary = Color(0xFF2979FF)
            )) {
                var selectedBmsSlot by remember { mutableStateOf<String?>(null) }
                var showExitDialog by remember { mutableStateOf(false) }

                val b1 by manager.bms1.bmsState.collectAsState()
                val b2 by manager.bms2.bmsState.collectAsState()

                val pagerState = rememberPagerState(pageCount = { 2 })
                val coroutineScope = rememberCoroutineScope()

                // 1. If inspecting battery, back gesture returns to the main dashboard
                BackHandler(enabled = selectedBmsSlot != null) {
                    selectedBmsSlot = null
                }

                // 2. If on Page 2 (Energy Flow), back gesture smoothly returns to Page 1 (Telemetry)
                BackHandler(enabled = selectedBmsSlot == null && pagerState.currentPage == 1) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }

                // 3. If on main homescreen (Page 1), back gesture asks if the user wants to exit
                BackHandler(enabled = selectedBmsSlot == null && pagerState.currentPage == 0 && !showExitDialog) {
                    showExitDialog = true
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (selectedBmsSlot != null) {
                        OfficialJkInspectionScreen(
                            b1 = b1,
                            b2 = b2,
                            initialSlot = selectedBmsSlot!!,
                            onBack = { selectedBmsSlot = null }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Page Navigation Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .background(Color(0xFF141720), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val pages = listOf("📊 TELEMETRY", "⚡ ENERGY FLOW")
                                pages.forEachIndexed { index, title ->
                                    val isSelected = pagerState.currentPage == index
                                    val bgColor = if (isSelected) Color(0xFF263238) else Color.Transparent
                                    val textColor = if (isSelected) Color(0xFF00E676) else Color(0xFF90A4AE)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bgColor)
                                            .clickable {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = textColor
                                        )
                                    }
                                }
                            }

                            // Horizontal Swipe Pager
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize().weight(1f)
                            ) { page ->
                                when (page) {
                                    0 -> DashboardScreen(
                                        manager = manager,
                                        onInspectCells = { bms -> selectedBmsSlot = if (bms.id == "B2") "B2" else "B1" }
                                    )
                                    1 -> EnergyFlowScreen(
                                        manager = manager,
                                        onInspectCells = { bms -> selectedBmsSlot = if (bms.id == "B2") "B2" else "B1" }
                                    )
                                }
                            }
                        }
                    }

                    if (showExitDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitDialog = false },
                            title = {
                                Text(
                                    text = "Exit Application",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            },
                            text = {
                                Text(
                                    text = "Are you sure you want to quit JK BMS Dual Monitor?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFCFD8DC)
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { finish() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("EXIT", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { showExitDialog = false },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancel", color = Color.LightGray)
                                }
                            },
                            containerColor = Color(0xFF1E232D),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.disconnectAll()
    }
}
