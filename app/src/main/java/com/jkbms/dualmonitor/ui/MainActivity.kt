package com.jkbms.dualmonitor.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jkbms.dualmonitor.ble.BleScanner
import com.jkbms.dualmonitor.ble.BmsConnectionManager
import com.jkbms.dualmonitor.model.BmsData

class MainActivity : ComponentActivity() {

    private lateinit var manager: BmsConnectionManager
    private lateinit var scanner: BleScanner

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) scanner.startScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = BmsConnectionManager(applicationContext)
        scanner = BleScanner(applicationContext)

        checkPermissionsAndStart()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF101216),
                surface = Color(0xFF1A1D24),
                primary = Color(0xFF00E676),
                secondary = Color(0xFF2979FF)
            )) {
                var selectedBmsForCells by remember { mutableStateOf<BmsData?>(null) }
                var showScanSheet by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (selectedBmsForCells != null) {
                        CellScreen(
                            bmsData = selectedBmsForCells!!,
                            onBack = { selectedBmsForCells = null }
                        )
                    } else {
                        DashboardScreen(
                            manager = manager,
                            onOpenScan = {
                                scanner.startScan()
                                showScanSheet = true
                            },
                            onInspectCells = { bms -> selectedBmsForCells = bms }
                        )
                    }

                    if (showScanSheet) {
                        ScanBottomSheet(
                            scanner = scanner,
                            manager = manager,
                            onDismiss = {
                                scanner.stopScan()
                                showScanSheet = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(permissions)
    }
}
