
package com.cypherusuh.iidxtouch.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt

data class ScratchPacket(
    val type: Int,
    val sessionId: Long,
    val sequence: Int,
    val timestamp: Long,
    val deltaAngle: Float = 0f,
    val averageRadius: Float = 0f,
    val fingerCount: Int = 0,
    val touchAngle: Float = 0f
)

object ScratchProtocol {
    const val PACKET_SIZE = 40

    const val TYPE_HELLO = 1
    const val TYPE_TOUCH = 2
    const val TYPE_HEARTBEAT = 3
    const val TYPE_ACK = 4
    const val TYPE_TELEMETRY = 5
    const val TYPE_RELEASE = 6

    private const val VERSION = 2

    private val MAGIC = byteArrayOf(
        'I'.code.toByte(),
        'D'.code.toByte(),
        'X'.code.toByte(),
        'T'.code.toByte()
    )

    fun encode(packet: ScratchPacket): ByteArray {
        val angle = packet.touchAngle.coerceIn(
            -PI.toFloat(),
            PI.toFloat()
        )

        val encodedAngle = (
                angle / PI.toFloat() * 32767f
                ).roundToInt().coerceIn(-32767, 32767)

        return ByteBuffer.allocate(PACKET_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put(MAGIC)
                put(VERSION.toByte())
                put(packet.type.toByte())
                putShort(0)

                putLong(packet.sessionId)
                putInt(packet.sequence)
                putLong(packet.timestamp)

                putFloat(packet.deltaAngle)
                putFloat(packet.averageRadius)

                put(packet.fingerCount.toByte())
                putShort(encodedAngle.toShort())
                put(0)
            }
            .array()
    }

    fun decode(data: ByteArray): ScratchPacket? {
        if (data.size != PACKET_SIZE) return null

        val buffer = ByteBuffer.wrap(data)
            .order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4)
        buffer.get(magic)

        if (!magic.contentEquals(MAGIC)) return null
        if ((buffer.get().toInt() and 0xFF) != VERSION) return null

        val type = buffer.get().toInt() and 0xFF
        buffer.short

        val sessionId = buffer.long
        val sequence = buffer.int
        val timestamp = buffer.long
        val deltaAngle = buffer.float
        val averageRadius = buffer.float
        val fingerCount = buffer.get().toInt() and 0xFF

        val encodedAngle = buffer.short.toInt()
        val touchAngle = encodedAngle / 32767f * PI.toFloat()

        return ScratchPacket(
            type = type,
            sessionId = sessionId,
            sequence = sequence,
            timestamp = timestamp,
            deltaAngle = deltaAngle,
            averageRadius = averageRadius,
            fingerCount = fingerCount,
            touchAngle = touchAngle
        )
    }
}
