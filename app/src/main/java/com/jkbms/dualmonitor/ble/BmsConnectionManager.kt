package com.jkbms.dualmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.jkbms.dualmonitor.model.TotalBankData
import com.jkbms.dualmonitor.protocol.Jk02Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

class BleScanner(private val context: Context) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val rawName = device.name ?: result.scanRecord?.deviceName ?: ""
            val address = device.address ?: return
            val rssi = result.rssi

            // Match JK BMS by advertised name or service UUID
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList()
            val hasJkService = serviceUuids.any { it.contains("ffe0") }
            val isJkBms = rawName.contains("JK", ignoreCase = true) ||
                          rawName.contains("BMS", ignoreCase = true) ||
                          rawName.contains("BD6A", ignoreCase = true) ||
                          rawName.contains("B2A", ignoreCase = true) ||
                          hasJkService

            // Only show relevant devices in the picker
            if (!isJkBms && rawName.isBlank()) return

            val displayName = if (rawName.isNotBlank()) rawName else "JK BMS ($address)"

            val current = _devices.value.toMutableList()
            val index = current.indexOfFirst { it.address.equals(address, ignoreCase = true) }
            val item = DiscoveredDevice(displayName, address, rssi)

            if (index >= 0) {
                current[index] = item
            } else {
                current.add(item)
            }
            _devices.value = current.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(clearExisting: Boolean = false) {
        val leScanner = scanner ?: return

        // If a scan was already running, stop it first to ensure a fresh cycle
        if (_isScanning.value) {
            try {
                leScanner.stopScan(scanCallback)
            } catch (e: Exception) {}
            _isScanning.value = false
        }

        if (clearExisting) {
            _devices.value = emptyList()
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        try {
            // Null filter so hardware filter doesn't drop packets while connected to peripheral 1
            leScanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
        } catch (e: Exception) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {}
        _isScanning.value = false
    }
}

class BmsConnectionManager(context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val bms1 = BmsConnection("B1", context, scope)
    val bms2 = BmsConnection("B2", context, scope)

    val totalBankState: StateFlow<TotalBankData> = combine(bms1.bmsState, bms2.bmsState) { b1, b2 ->
        val activeBmsList = listOf(b1, b2).filter { it.voltage > 1.0f }

        val totalCurrent = activeBmsList.sumOf { it.current.toDouble() }.toFloat()
        val totalPower = activeBmsList.sumOf { it.power.toDouble() }.toFloat()
        val avgVoltage = if (activeBmsList.isNotEmpty()) activeBmsList.map { it.voltage }.average().toFloat() else 0f

        val totalCap = activeBmsList.sumOf { it.remainingCapacityAh.toDouble() }.toFloat()
        val weightedSoc = if (totalCap > 0f) {
            (activeBmsList.sumOf { (it.remainingCapacityAh * it.soc).toDouble() } / totalCap).toInt()
        } else if (activeBmsList.isNotEmpty()) {
            activeBmsList.map { it.soc }.average().toInt()
        } else {
            0
        }

        TotalBankData(
            voltage = avgVoltage,
            current = totalCurrent,
            power = totalPower,
            capacityWeightedSoc = weightedSoc.coerceIn(0, 100)
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), TotalBankData())
}
