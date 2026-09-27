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

        val mask = (data[70].toLong() and 0xFF) or
                ((data[71].toLong() and 0xFF) shl 8) or
                ((data[72].toLong() and 0xFF) shl 16) or
                ((data[73].toLong() and 0xFF) shl 24)

        val cells = mutableListOf<CellData>()
        for (i in 0 until 32) {
            val offset = 6 + (i * 2)
            val rawMv = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            val isEnabled = (mask and (1L shl i)) != 0L

            if (isEnabled) {
                val resOffset = 80 + (i * 2)
                val rawRes = (data[resOffset].toInt() and 0xFF) or ((data[resOffset + 1].toInt() and 0xFF) shl 8)
                val resVal = if (rawRes in 1..65000) rawRes / 1000f else null

                cells.add(
                    CellData(
                        index = i + 1,
                        voltage = rawMv / 1000f,
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

        val rawPackV = (data[150].toLong() and 0xFF) or
                ((data[151].toLong() and 0xFF) shl 8) or
                ((data[152].toLong() and 0xFF) shl 16) or
                ((data[153].toLong() and 0xFF) shl 24)
        val packVoltage = if (rawPackV in 1L..15000L) rawPackV / 100f else cells.sumOf { it.voltage.toDouble() }.toFloat()

        val rawCurrent = (data[158].toInt() and 0xFF) or
                ((data[159].toInt() and 0xFF) shl 8) or
                ((data[160].toInt() and 0xFF) shl 16) or
                ((data[161].toInt() and 0xFF) shl 24)
        val currentA = rawCurrent / 100f

        val soc = data[173].toInt() and 0xFF

        val rawTemp1 = ((data[180].toInt() and 0xFF) or ((data[181].toInt() and 0xFF) shl 8)).toShort()
        val tempVal = rawTemp1 / 10f

        return current.copy(
            voltage = packVoltage,
            current = currentA,
            power = packVoltage * currentA,
            soc = soc.coerceIn(0, 100),
            temperature = tempVal,
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
}
