package com.jkbms.dualmonitor.protocol

import com.jkbms.dualmonitor.model.BmsData
import com.jkbms.dualmonitor.model.CellData

object Jk02Parser {

    fun parse(frame: RawJkFrame, existing: BmsData): BmsData {
        return when (frame.frameType) {
            Jk02Protocol.FRAME_TYPE_CELL_INFO -> parseType02(frame.payload, existing)
            Jk02Protocol.FRAME_TYPE_SETTINGS -> parseType01(frame.payload, existing)
            Jk02Protocol.FRAME_TYPE_DEVICE_INFO -> parseType03(frame.payload, existing)
            else -> existing
        }
    }

    private fun parseType02(data: ByteArray, current: BmsData): BmsData {
        if (data.size < Jk02Protocol.FRAME_SIZE) return current

        // Auto-detect 24S vs 32S offset:
        // In 24S (and <=24S like 20S), total voltage is at byte 118. In 32S, it is at byte 134.
        val testV24 = getUint32(data, 118) * 0.001f
        val testV32 = getUint32(data, 134) * 0.001f
        val offset = if (testV24 in 5f..160f) 0 else if (testV32 in 5f..160f) 16 else 0

        val maxCells = if (offset == 16) 32 else 24
        val cells = mutableListOf<CellData>()

        for (i in 0 until maxCells) {
            val vOffset = 6 + (i * 2)
            if (vOffset + 1 >= data.size) break
            val rawMv = getUint16(data, vOffset)
            val voltage = rawMv * 0.001f

            // Li-ion / LFP cell voltage ranges between 0.5V and 5.0V
            if (voltage in 0.5f..5.0f) {
                val resOffset = (64 + offset) + (i * 2)
                val rawRes = if (resOffset + 1 < data.size) getUint16(data, resOffset) else 0
                val resVal = if (rawRes in 1..65000) rawRes / 1000f else null

                cells.add(
                    CellData(
                        index = i + 1,
                        voltage = voltage,
                        resistance = resVal,
                        enabled = true
                    )
                )
            }
        }

        val minCell = cells.minByOrNull { it.voltage }
        val maxCell = cells.maxByOrNull { it.voltage }
        val avgVoltage = if (cells.isNotEmpty()) cells.map { it.voltage }.average().toFloat() else 0f
        val deltaMv = if (minCell != null && maxCell != null) {
            ((maxCell.voltage - minCell.voltage) * 1000).toInt()
        } else 0

        // Total Battery Pack Voltage (at byte 118 + offset)
        val rawPackV = getUint32(data, 118 + offset) * 0.001f
        val packVoltage = if (rawPackV in 5f..160f) {
            rawPackV
        } else if (cells.isNotEmpty()) {
            cells.sumOf { it.voltage.toDouble() }.toFloat()
        } else {
            0f
        }

        // Current (at byte 126 + offset, signed 32-bit int)
        val rawCurrent = getInt32(data, 126 + offset)
        val currentA = rawCurrent * 0.001f
        val powerW = packVoltage * currentA

        // Temperature (at byte 130 and 132 + offset, signed 16-bit int in 0.1 °C)
        val temp1 = getInt16(data, 130 + offset) * 0.1f
        val temp2 = getInt16(data, 132 + offset) * 0.1f
        val temperature = if (temp1 in -40f..100f) temp1 else if (temp2 in -40f..100f) temp2 else 0f

        // State of Charge (SOC) (at byte 141 + offset, 1 byte in %)
        val rawSoc = if (141 + offset < data.size) data[141 + offset].toInt() and 0xFF else 0
        val soc = rawSoc.coerceIn(0, 100)

        // Capacity Remaining (at byte 142 + offset, uint32 in 0.001 Ah)
        val rawCapacity = getUint32(data, 142 + offset) * 0.001f
        val remainingCapacity = if (rawCapacity > 0f) rawCapacity else current.remainingCapacityAh

        return current.copy(
            voltage = packVoltage,
            current = currentA,
            power = powerW,
            soc = soc,
            remainingCapacityAh = remainingCapacity,
            temperature = temperature,
            cells = cells,
            averageCellVoltage = avgVoltage,
            minCellVoltage = minCell?.voltage ?: 0f,
            maxCellVoltage = maxCell?.voltage ?: 0f,
            minCellNumber = minCell?.index ?: 0,
            maxCellNumber = maxCell?.index ?: 0,
            deltaVoltageMv = deltaMv,
            lastUpdate = System.currentTimeMillis()
        )
    }

    private fun parseType01(data: ByteArray, current: BmsData): BmsData = current

    private fun parseType03(data: ByteArray, current: BmsData): BmsData {
        val rawStr = String(data, Charsets.US_ASCII).filter { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }
        return current.copy(
            model = if (rawStr.contains("JK_")) rawStr.substringAfter("JK_").take(16) else current.model,
            lastUpdate = System.currentTimeMillis()
        )
    }

    private fun getUint16(data: ByteArray, offset: Int): Int {
        if (offset + 1 >= data.size) return 0
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun getInt16(data: ByteArray, offset: Int): Short {
        return getUint16(data, offset).toShort()
    }

    private fun getUint32(data: ByteArray, offset: Int): Long {
        if (offset + 3 >= data.size) return 0L
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun getInt32(data: ByteArray, offset: Int): Int {
        return getUint32(data, offset).toInt()
    }
}
