package com.jkbms.dualmonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jkbms.dualmonitor.ble.BmsConnectionManager
import com.jkbms.dualmonitor.model.BmsData

class MainActivity : ComponentActivity() {

    private lateinit var manager: BmsConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = BmsConnectionManager(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF0B0F1A),
                surface = Color(0xFF111827),
                primary = Color(0xFF2979FF),
                secondary = Color(0xFF00C853),
                tertiary = Color(0xFFFF6D00)
            )) {
                var selectedBmsSlot by remember { mutableStateOf<String?>(null) }
                var showHistoryScreen by remember { mutableStateOf(false) }
                var showExitDialog by remember { mutableStateOf(false) }

                val b1 by manager.bms1.bmsState.collectAsState()
                val b2 by manager.bms2.bmsState.collectAsState()

                // 1. If inspecting battery, back gesture returns to the main dashboard
                BackHandler(enabled = selectedBmsSlot != null) {
                    selectedBmsSlot = null
                }

                // 2. If on separate History page, back gesture returns to the main dashboard
                BackHandler(enabled = selectedBmsSlot == null && showHistoryScreen) {
                    showHistoryScreen = false
                }

                // 3. If on main dashboard, back gesture asks if the user wants to exit
                BackHandler(enabled = selectedBmsSlot == null && !showHistoryScreen && !showExitDialog) {
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
                    } else if (showHistoryScreen) {
                        EnergyHistoryScreen(
                            manager = manager,
                            onBack = { showHistoryScreen = false }
                        )
                    } else {
                        DashboardScreen(
                            manager = manager,
                            onInspectCells = { bms -> selectedBmsSlot = if (bms.id == "B2") "B2" else "B1" },
                            onViewHistory = { showHistoryScreen = true }
                        )
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
