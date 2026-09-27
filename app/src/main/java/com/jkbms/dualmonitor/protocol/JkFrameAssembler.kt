package com.jkbms.dualmonitor.protocol

import java.io.ByteArrayOutputStream

data class RawJkFrame(
    val frameType: Byte,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawJkFrame
        return frameType == other.frameType && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * frameType + payload.contentHashCode()
}

class JkFrameAssembler(
    private val onFrameReceived: (RawJkFrame) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val buffer = ByteArrayOutputStream()

    @Synchronized
    fun pushBytes(chunk: ByteArray) {
        buffer.write(chunk)
        processBuffer()
    }

    private fun processBuffer() {
        var data = buffer.toByteArray()

        while (data.size >= Jk02Protocol.FRAME_SIZE) {
            var headerIdx = -1
            for (i in 0..(data.size - 4)) {
                if (data[i] == 0x55.toByte() &&
                    data[i + 1] == 0xAA.toByte() &&
                    data[i + 2] == 0xEB.toByte() &&
                    data[i + 3] == 0x90.toByte()
                ) {
                    headerIdx = i
                    break
                }
            }

            if (headerIdx == -1) {
                val retain = if (data.size >= 3) data.copyOfRange(data.size - 3, data.size) else data
                buffer.reset()
                buffer.write(retain)
                return
            }

            if (headerIdx > 0) {
                data = data.copyOfRange(headerIdx, data.size)
            }

            if (data.size < Jk02Protocol.FRAME_SIZE) {
                buffer.reset()
                buffer.write(data)
                return
            }

            val frameBytes = data.copyOfRange(0, Jk02Protocol.FRAME_SIZE)
            val expectedCrc = frameBytes[299]
            val calculatedCrc = Jk02Protocol.computeChecksum(frameBytes, 0, 299)

            if (expectedCrc == calculatedCrc) {
                val frameType = frameBytes[4]
                onFrameReceived(RawJkFrame(frameType, frameBytes))
            } else {
                onLog("CRC Mismatch: calculated=${calculatedCrc.toHex()} expected=${expectedCrc.toHex()}")
            }

            data = data.copyOfRange(Jk02Protocol.FRAME_SIZE, data.size)
            buffer.reset()
            buffer.write(data)
        }
    }

    private fun Byte.toHex(): String = String.format("%02X", this)
}
