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

        // 1. Parse individual cell voltages (bytes 6 to 69 in 32S layout, or 6 to 53 in 24S layout)
        val cells = mutableListOf<CellData>()
        for (i in 0 until 32) {
            val vOffset = 6 + (i * 2)
            if (vOffset + 1 >= data.size) break
            val rawMv = getUint16(data, vOffset)
            val voltage = rawMv * 0.001f

            // Li-ion / LFP cell voltage ranges between 0.5V and 5.0V
            if (voltage in 0.5f..5.0f) {
                // Resistance can be at 80 + 2*i (32S layout) or 64 + 2*i (24S layout)
                val rawRes32 = if (80 + (i * 2) + 1 < data.size) getUint16(data, 80 + (i * 2)) else 0
                val rawRes24 = if (64 + (i * 2) + 1 < data.size) getUint16(data, 64 + (i * 2)) else 0
                val rawRes = if (rawRes32 in 1..65000) rawRes32 else rawRes24
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
        val sumV = if (cells.isNotEmpty()) cells.sumOf { it.voltage.toDouble() }.toFloat() else 0f

        // 2. Identify the active layout base offset (150 for 32S layout, 118 for 24S layout, or 134)
        // We match candidate pack voltages against sumV (e.g. ~52.75V)
        val vCandidates = listOf(
            Triple(150, 0.01f, getUint32(data, 150) * 0.01f),
            Triple(150, 0.001f, getUint32(data, 150) * 0.001f),
            Triple(118, 0.01f, getUint32(data, 118) * 0.01f),
            Triple(118, 0.001f, getUint32(data, 118) * 0.001f),
            Triple(134, 0.01f, getUint32(data, 134) * 0.01f),
            Triple(134, 0.001f, getUint32(data, 134) * 0.001f)
        )

        val bestMatch = vCandidates.firstOrNull { (_, _, v) -> sumV > 0f && kotlin.math.abs(v - sumV) < 2.0f }
            ?: vCandidates.firstOrNull { (_, _, v) -> v in 10f..160f }

        val baseOffset = bestMatch?.first ?: 150
        val vFactor = bestMatch?.second ?: 0.01f
        val packVoltage = if (bestMatch != null && bestMatch.third in 5f..160f) bestMatch.third else sumV

        // 3. Current (signed 32-bit integer at 158 or 126 or 142)
        val currentOffset = when (baseOffset) {
            150 -> 158
            134 -> 142
            else -> 126
        }
        val rawCurrent = getInt32(data, currentOffset)
        val cFactor = if (vFactor == 0.001f || kotlin.math.abs(rawCurrent * 0.01f) > 500f) 0.001f else 0.01f
        val currentA = rawCurrent * cFactor
        val powerW = packVoltage * currentA

        // 4. State of Charge (SOC, in %)
        val socOffset = baseOffset + 23
        val rawSoc = if (socOffset < data.size) data[socOffset].toInt() and 0xFF else -1
        val soc = if (rawSoc in 0..100) {
            rawSoc
        } else {
            val candidate = listOf(173, 141, 157).firstOrNull { it < data.size && (data[it].toInt() and 0xFF) in 0..100 }
            if (candidate != null) data[candidate].toInt() and 0xFF else current.soc
        }

        // 5. Temperatures (0.1 °C)
        val mosTempOffset = baseOffset + 12
        val battTempOffset = baseOffset + 14

        val tMos = getInt16(data, mosTempOffset) * 0.1f
        val tBatt = getInt16(data, battTempOffset) * 0.1f

        val temperature = when {
            tBatt in 1.0f..85.0f -> tBatt
            tMos in 1.0f..85.0f -> tMos
            getInt16(data, 164) * 0.1f in 1.0f..85.0f -> getInt16(data, 164) * 0.1f
            getInt16(data, 162) * 0.1f in 1.0f..85.0f -> getInt16(data, 162) * 0.1f
            getInt16(data, 132) * 0.1f in 1.0f..85.0f -> getInt16(data, 132) * 0.1f
            getInt16(data, 130) * 0.1f in 1.0f..85.0f -> getInt16(data, 130) * 0.1f
            else -> current.temperature
        }

        // 6. Remaining Capacity (Ah)
        val capOffset = baseOffset + 24
        val rawCap = getUint32(data, capOffset)
        val parsedCap = when {
            soc == 0 -> 0f
            rawCap * 0.001f in 0.01f..2000f -> rawCap * 0.001f
            rawCap * 0.01f in 0.01f..2000f -> rawCap * 0.01f
            soc > 0 -> current.nominalCapacityAh * (soc / 100f)
            else -> 0f
        }
        val remainingCapacity = if (soc == 0) 0f else parsedCap

        return current.copy(
            voltage = packVoltage,
            current = currentA,
            power = powerW,
            soc = soc,
            remainingCapacityAh = remainingCapacity,
            nominalCapacityAh = current.nominalCapacityAh,
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

    private fun parseType01(data: ByteArray, current: BmsData): BmsData {
        if (data.size < Jk02Protocol.FRAME_SIZE) return current
        val rawCap = getUint32(data, 10) * 0.001f
        val nominal = if (rawCap in 10f..2000f) rawCap else current.nominalCapacityAh
        return current.copy(nominalCapacityAh = nominal)
    }

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
