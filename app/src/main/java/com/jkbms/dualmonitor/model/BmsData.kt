package com.jkbms.dualmonitor.model

enum class ConnectionStatus {
    DISCONNECTED, SCANNING, CONNECTING, DISCOVERING_SERVICES, ENABLING_NOTIFICATIONS, CONNECTED, RECONNECTING, ERROR
}

data class CellData(
    val index: Int,
    val voltage: Float,
    val resistance: Float? = null,
    val enabled: Boolean = true
)

data class BmsData(
    val id: String = "",
    val displayName: String = "",
    val macAddress: String = "",
    val model: String = "JK BMS",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val voltage: Float = 0f,
    val current: Float = 0f,
    val power: Float = 0f,
    val soc: Int = 0,
    val remainingCapacityAh: Float = 0f,
    val nominalCapacityAh: Float = 100f,
    val totalChargingCycleAh: Float = 0f,
    val temperature: Float = 0f,
    val cells: List<CellData> = emptyList(),
    val averageCellVoltage: Float = 0f,
    val minCellVoltage: Float = 0f,
    val maxCellVoltage: Float = 0f,
    val minCellNumber: Int = 0,
    val maxCellNumber: Int = 0,
    val deltaVoltageMv: Int = 0,
    val lastUpdate: Long = 0L
)

data class TotalBankData(
    val voltage: Float = 0f,
    val current: Float = 0f,
    val power: Float = 0f,
    val capacityWeightedSoc: Int = 0,
    val remainingCapacityAh: Float = 0f
)

data class DailyEnergyRecord(
    val date: String = "",
    val chargedAh: Float = 0f,
    val dischargedAh: Float = 0f,
    val chargedKwh: Float = 0f,
    val dischargedKwh: Float = 0f,
    val minSoc: Int = 0,
    val maxSoc: Int = 0,
    val lastUpdated: Long = 0L
) {
    val netKwh: Float get() = chargedKwh - dischargedKwh
    val netAh: Float get() = chargedAh - dischargedAh
}
