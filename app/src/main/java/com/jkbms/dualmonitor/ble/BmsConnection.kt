package com.jkbms.dualmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
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
    private var macAddress: String? = null
    private var shouldReconnect = false

    private val _bmsState = MutableStateFlow(BmsData(id = slotId))
    val bmsState = _bmsState.asStateFlow()

    val logFlow = MutableSharedFlow<String>(extraBufferCapacity = 50)

    private val assembler = JkFrameAssembler(
        onFrameReceived = { frame ->
            val updated = Jk02Parser.parse(frame, _bmsState.value)
            _bmsState.value = updated
            scope.launch {
                logFlow.emit("[$slotId] RX Frame Type=${frame.frameType} CRC=OK")
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
                    scope.launch { logFlow.emit("[$slotId] Connected. Requesting MTU 512...") }
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
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
            scope.launch { logFlow.emit("[$slotId] MTU configured: $mtu. Discovering services...") }
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateStatus(ConnectionStatus.ERROR)
                return
            }

            val service = gatt.getService(Jk02Protocol.SERVICE_UUID)
            val characteristic = service?.getCharacteristic(Jk02Protocol.CHAR_UUID)

            if (service == null || characteristic == null) {
                scope.launch { logFlow.emit("[$slotId] Error: FFE0/FFE1 not found") }
                updateStatus(ConnectionStatus.ERROR)
                return
            }

            updateStatus(ConnectionStatus.ENABLING_NOTIFICATIONS)
            enableNotifications(gatt, characteristic)
        }

        @Deprecated("Used for compatibility below API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == Jk02Protocol.CHAR_UUID) {
                @Suppress("DEPRECATION")
                assembler.pushBytes(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == Jk02Protocol.CHAR_UUID) {
                assembler.pushBytes(value)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == Jk02Protocol.CCCD_UUID) {
                updateStatus(ConnectionStatus.CONNECTED)
                scope.launch {
                    logFlow.emit("[$slotId] Notifications active. Requesting initial data 0x96/0x97")
                    delay(300)
                    sendReadCommand(gatt, Jk02Protocol.CMD_REQUEST_SETTINGS)
                    delay(300)
                    sendReadCommand(gatt, Jk02Protocol.CMD_REQUEST_DEVICE_INFO)
                }
            }
        }
    }

    fun connect(address: String) {
        macAddress = address
        shouldReconnect = true
        updateStatus(ConnectionStatus.CONNECTING)
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(address)

        _bmsState.value = _bmsState.value.copy(macAddress = address, displayName = device.name ?: address)
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
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
        }
    }

    private fun sendReadCommand(gatt: BluetoothGatt, cmdByte: Byte) {
        val service = gatt.getService(Jk02Protocol.SERVICE_UUID) ?: return
        val char = service.getCharacteristic(Jk02Protocol.CHAR_UUID) ?: return
        val payload = JkCommandBuilder.buildReadCommand(cmdByte)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            char.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
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
        cleanupGatt()
        updateStatus(ConnectionStatus.DISCONNECTED)
    }

    private fun cleanupGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {}
        bluetoothGatt = null
    }

    private fun updateStatus(status: ConnectionStatus) {
        _bmsState.value = _bmsState.value.copy(connectionStatus = status)
    }
}
