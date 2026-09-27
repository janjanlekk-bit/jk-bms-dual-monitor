package com.jkbms.dualmonitor.protocol

import java.util.UUID

object Jk02Protocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val FRAME_SIZE = 300

    const val FRAME_TYPE_SETTINGS: Byte = 0x01
    const val FRAME_TYPE_CELL_INFO: Byte = 0x02
    const val FRAME_TYPE_DEVICE_INFO: Byte = 0x03

    val CMD_REQUEST_SETTINGS: Byte = 0x96.toByte()
    val CMD_REQUEST_DEVICE_INFO: Byte = 0x97.toByte()

    fun computeChecksum(buffer: ByteArray, offset: Int, length: Int): Byte {
        var sum = 0
        for (i in offset until (offset + length)) {
            sum += buffer[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }
}

object JkCommandBuilder {
    fun buildReadCommand(cmd: Byte): ByteArray {
        val frame = ByteArray(20)
        frame[0] = 0xAA.toByte()
        frame[1] = 0x55.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0xEB.toByte()
        frame[4] = cmd
        frame[5] = 0x00

        var sum = 0
        for (i in 0..18) {
            sum += frame[i].toInt() and 0xFF
        }
        frame[19] = (sum and 0xFF).toByte()
        return frame
    }
}
