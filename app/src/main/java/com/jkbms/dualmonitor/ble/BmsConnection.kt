package com.jkbms.dualmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.jkbms.dualmonitor.model.BmsData
import com.jkbms.dualmonitor.model.ConnectionStatus
import com.jkbms.dualmonitor.protocol.Jk02Parser
import com.jkbms.dualmonitor.protocol.Jk02Protocol
import com.jkbms.dualmonitor.protocol.JkCommandBuilder
import com.jkbms.dualmonitor.protocol.JkFrameAssembler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class BmsConnection(
    val slotId: String,
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var macAddress: String? = null
    private var shouldReconnect = false
    private var servicesDiscovered = false
    private var pollingJob: Job? = null
    private var timeoutJob: Job? = null

    private val _bmsState = MutableStateFlow(BmsData(id = slotId))
    val bmsState = _bmsState.asStateFlow()

    val logFlow = MutableSharedFlow<String>(extraBufferCapacity = 50)

    private val assembler = JkFrameAssembler(
        onFrameReceived = { frame ->
            val updated = Jk02Parser.parse(frame, _bmsState.value)
            _bmsState.value = updated
            scope.launch {
                logFlow.emit("[$slotId] RX Frame Type=${frame.frameType} CRC=OK V=${String.format("%.2f", updated.voltage)}V Cells=${updated.cells.size}")
            }
        },
        onLog = { msg ->
            scope.launch { logFlow.emit("[$slotId] $msg") }
        }
    )

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    updateStatus(ConnectionStatus.DISCOVERING_SERVICES)
                    scope.launch(Dispatchers.Main) {
                        logFlow.emit("[$slotId] Connected. Discovering services...")
                        delay(250)
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    pollingJob?.cancel()
                    timeoutJob?.cancel()
                    updateStatus(if (shouldReconnect) ConnectionStatus.RECONNECTING else ConnectionStatus.DISCONNECTED)
                    scope.launch { logFlow.emit("[$slotId] Disconnected (status: $status)") }
                    cleanupGatt()
                    if (shouldReconnect) triggerReconnect()
                }
                else -> {
                    updateStatus(ConnectionStatus.ERROR)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            scope.launch { logFlow.emit("[$slotId] MTU configured: $mtu") }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateStatus(ConnectionStatus.ERROR)
                return
            }
            servicesDiscovered = true

            val service = gatt.getService(Jk02Protocol.SERVICE_UUID)
            if (service == null) {
                scope.launch { logFlow.emit("[$slotId] Error: FFE0 service not found") }
                updateStatus(ConnectionStatus.ERROR)
                return
            }

            // Find Notify characteristic
            notifyCharacteristic = service.characteristics.firstOrNull {
                it.uuid == Jk02Protocol.CHAR_UUID &&
                (it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
            } ?: service.getCharacteristic(Jk02Protocol.CHAR_UUID)

            // Find Write characteristic
            writeCharacteristic = service.characteristics.firstOrNull {
                it.uuid == Jk02Protocol.CHAR_UUID &&
                (it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
            } ?: service.getCharacteristic(Jk02Protocol.CHAR_UUID)

            if (notifyCharacteristic == null) {
                scope.launch { logFlow.emit("[$slotId] Error: FFE1 notify characteristic not found") }
                updateStatus(ConnectionStatus.ERROR)
                return
            }

            updateStatus(ConnectionStatus.ENABLING_NOTIFICATIONS)
            enableNotifications(gatt, notifyCharacteristic!!)
        }

        @Deprecated("Used for compatibility below API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == Jk02Protocol.CHAR_UUID) {
                @Suppress("DEPRECATION")
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    assembler.pushBytes(value)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == Jk02Protocol.CHAR_UUID && value.isNotEmpty()) {
                assembler.pushBytes(value)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == Jk02Protocol.CCCD_UUID) {
                timeoutJob?.cancel()
                updateStatus(ConnectionStatus.CONNECTED)
                scope.launch {
                    logFlow.emit("[$slotId] Notifications active. Requesting device info & cell stream...")
                }

                // Start continuous telemetry polling loop
                pollingJob?.cancel()
                pollingJob = scope.launch(Dispatchers.IO) {
                    delay(300)
                    sendReadCommand(gatt, Jk02Protocol.CMD_REQUEST_DEVICE_INFO)
                    delay(400)
                    sendReadCommand(gatt, Jk02Protocol.CMD_REQUEST_SETTINGS)

                    // Keepalive & Telemetry update poll every 1.5 seconds
                    while (isActive && _bmsState.value.connectionStatus == ConnectionStatus.CONNECTED) {
                        delay(1500)
                        sendReadCommand(gatt, Jk02Protocol.CMD_REQUEST_SETTINGS)
                    }
                }
            }
        }
    }

    fun connect(address: String) {
        macAddress = address
        shouldReconnect = true
        updateStatus(ConnectionStatus.CONNECTING)

        // Reset previous connection before connecting
        cleanupGatt()

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            updateStatus(ConnectionStatus.ERROR)
            return
        }

        val device = adapter.getRemoteDevice(address)
        _bmsState.value = _bmsState.value.copy(macAddress = address, displayName = device.name ?: address)

        // Start connection timeout watchdog (15 seconds)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(15000)
            if (_bmsState.value.connectionStatus != ConnectionStatus.CONNECTED) {
                logFlow.emit("[$slotId] Connection timeout. Retrying clean connect...")
                cleanupGatt()
                delay(1000)
                if (shouldReconnect && macAddress != null) {
                    connect(macAddress!!)
                } else {
                    updateStatus(ConnectionStatus.ERROR)
                }
            }
        }

        // Must connect on Main Thread so BluetoothGatt binder callbacks are properly scheduled
        Handler(Looper.getMainLooper()).post {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context.applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context.applicationContext, false, gattCallback)
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(Jk02Protocol.CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } else {
            scope.launch { logFlow.emit("[$slotId] Warning: CCCD 0x2902 not found on notify char") }
        }
    }

    private fun sendReadCommand(gatt: BluetoothGatt, cmdByte: Byte) {
        val targetChar = writeCharacteristic ?: gatt.getService(Jk02Protocol.SERVICE_UUID)?.getCharacteristic(Jk02Protocol.CHAR_UUID) ?: return
        val payload = JkCommandBuilder.buildReadCommand(cmdByte)

        val writeType = if ((targetChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(targetChar, payload, writeType)
        } else {
            @Suppress("DEPRECATION")
            targetChar.writeType = writeType
            @Suppress("DEPRECATION")
            targetChar.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(targetChar)
        }
    }

    private fun triggerReconnect() {
        scope.launch {
            delay(5000)
            if (shouldReconnect && macAddress != null) {
                logFlow.emit("[$slotId] Reconnecting to $macAddress...")
                connect(macAddress!!)
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        pollingJob?.cancel()
        timeoutJob?.cancel()
        cleanupGatt()
        updateStatus(ConnectionStatus.DISCONNECTED)
    }

    private fun cleanupGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {}
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
    }

    private fun updateStatus(status: ConnectionStatus) {
        _bmsState.value = _bmsState.value.copy(connectionStatus = status)
    }
}
