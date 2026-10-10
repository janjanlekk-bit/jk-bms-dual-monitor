package com.jkbms.dualmonitor.ble

import android.content.Context
import com.jkbms.dualmonitor.model.EnergyHistoryManager
import com.jkbms.dualmonitor.model.TotalBankData
import com.jkbms.dualmonitor.network.Esp32GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BmsConnectionManager(context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val bms1 = BmsConnection("B1", context, scope)
    val bms2 = BmsConnection("B2", context, scope)
    val energyHistory = EnergyHistoryManager(context, scope)

    val gatewayClient: Esp32GatewayClient = Esp32GatewayClient(
        context = context,
        scope = scope,
        onDataReceived = { gB1, gB2, gBank, gDaily ->
            bms1.updateFromExternal(gB1)
            bms2.updateFromExternal(gB2)
            energyHistory.syncFromGateway(gDaily)
        },
        onHistoryReceived = { gYesterday ->
            energyHistory.syncYesterdayFromGateway(gYesterday)
        }
    )

    val isGatewayMode = gatewayClient.isGatewayConnected
    val blePaused = gatewayClient.blePaused
    val bleRemainingSec = gatewayClient.bleRemainingSec

    val totalBankState: StateFlow<TotalBankData> = combine(bms1.bmsState, bms2.bmsState) { b1, b2 ->
        val activeBmsList = listOf(b1, b2).filter { it.voltage > 1.0f }

        val totalCurrent = activeBmsList.sumOf { it.current.toDouble() }.toFloat()
        val totalPower = activeBmsList.sumOf { it.power.toDouble() }.toFloat()
        val avgVoltage = if (activeBmsList.isNotEmpty()) activeBmsList.map { it.voltage }.average().toFloat() else 0f

        val totalCap = activeBmsList.sumOf { it.remainingCapacityAh.toDouble() }.toFloat()
        val totalNominal = activeBmsList.sumOf { it.nominalCapacityAh.toDouble() }.toFloat()
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
            capacityWeightedSoc = weightedSoc.coerceIn(0, 100),
            remainingCapacityAh = totalCap,
            nominalCapacityAh = totalNominal
        )
    }.stateIn(scope, SharingStarted.Eagerly, TotalBankData())

    fun releaseBle(seconds: Int = 600) {
        scope.launch { gatewayClient.releaseBle(seconds) }
    }

    fun resumeBle() {
        scope.launch { gatewayClient.resumeBle() }
    }

    fun isDualConfigured(): Boolean = true

    fun disconnectAll() {
        gatewayClient.stopPolling()
    }
}
