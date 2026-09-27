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
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown JK"
            val address = device.address
            val rssi = result.rssi

            val current = _devices.value.toMutableList()
            val index = current.indexOfFirst { it.address == address }
            val item = DiscoveredDevice(name, address, rssi)

            if (index >= 0) {
                current[index] = item
            } else {
                current.add(item)
            }
            _devices.value = current.sortedByDescending { it.rssi }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value || scanner == null) return

        _devices.value = emptyList()
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Jk02Protocol.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        scanner?.stopScan(scanCallback)
        _isScanning.value = false
    }
}

class BmsConnectionManager(context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val bms1 = BmsConnection("B1", context, scope)
    val bms2 = BmsConnection("B2", context, scope)

    val totalBankState: StateFlow<TotalBankData> = combine(bms1.bmsState, bms2.bmsState) { b1, b2 ->
        val totalCurrent = b1.current + b2.current
        val totalPower = b1.power + b2.power

        val activeVoltages = listOf(b1, b2).filter { it.voltage > 1.0f }.map { it.voltage }
        val avgVoltage = if (activeVoltages.isNotEmpty()) activeVoltages.average().toFloat() else 0f

        val totalCap = b1.remainingCapacityAh + b2.remainingCapacityAh
        val weightedSoc = if (totalCap > 0f) {
            (((b1.remainingCapacityAh * b1.soc) + (b2.remainingCapacityAh * b2.soc)) / totalCap).toInt()
        } else {
            ((b1.soc + b2.soc) / 2)
        }

        TotalBankData(
            voltage = avgVoltage,
            current = totalCurrent,
            power = totalPower,
            capacityWeightedSoc = weightedSoc.coerceIn(0, 100)
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), TotalBankData())
}
