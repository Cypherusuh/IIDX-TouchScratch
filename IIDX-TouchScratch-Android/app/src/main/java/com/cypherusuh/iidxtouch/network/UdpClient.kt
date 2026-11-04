package com.cypherusuh.iidxtouch.network

import android.os.SystemClock
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class UdpClient(
    private val host: String,
    private val port: Int,
    private val onConnected: () -> Unit,
    private val onError: (String) -> Unit,
    private val onTelemetry: (ScratchPacket) -> Unit
) {
    companion object {
        private const val TAG = "IIDXTouchUDP"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val sequence = AtomicInteger(0)
    private val sessionId = SecureRandom().nextLong()

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var resolvedAddress: InetAddress? = null

    @Volatile
    private var connected = false

    @Volatile
    private var closed = false

    private var receiverThread: Thread? = null

    fun start() {
        executor.execute {
            try {
                val address = InetAddress.getByName(host)
                resolvedAddress = address

                val udpSocket = DatagramSocket()
                socket = udpSocket
                udpSocket.soTimeout = 500

                val hello = ScratchProtocol.encode(
                    ScratchPacket(
                        type = ScratchProtocol.TYPE_HELLO,
                        sessionId = sessionId,
                        sequence = sequence.getAndIncrement(),
                        timestamp = SystemClock.uptimeMillis()
                    )
                )

                val response = ByteArray(ScratchProtocol.PACKET_SIZE)
                val responsePacket = DatagramPacket(
                    response,
                    response.size
                )

                var acknowledged = false

                repeat(6) {
                    if (acknowledged) return@repeat

                    udpSocket.send(
                        DatagramPacket(
                            hello,
                            hello.size,
                            address,
                            port
                        )
                    )

                    try {
                        responsePacket.length = response.size
                        udpSocket.receive(responsePacket)

                        val ack = ScratchProtocol.decode(
                            response.copyOf(responsePacket.length)
                        )

                        if (
                            ack?.type == ScratchProtocol.TYPE_ACK &&
                            ack.sessionId == sessionId
                        ) {
                            acknowledged = true
                        }
                    } catch (_: SocketTimeoutException) {
                        // Retry HELLO.
                    }
                }

                if (!acknowledged) {
                    throw IllegalStateException(
                        "Windows backend did not acknowledge connection"
                    )
                }

                connected = true

                Log.i(TAG, "Connected to $host:$port")
                onConnected()

                startReceiver(udpSocket)

            } catch (error: Exception) {
                connected = false

                Log.e(TAG, "Connection failed", error)
                onError(error.message ?: "Unknown connection error")
            }
        }
    }

    private fun startReceiver(udpSocket: DatagramSocket) {
        receiverThread = Thread({
            val buffer = ByteArray(ScratchProtocol.PACKET_SIZE)

            while (!closed && !udpSocket.isClosed) {
                try {
                    val datagram = DatagramPacket(
                        buffer,
                        buffer.size
                    )

                    udpSocket.receive(datagram)

                    val packet = ScratchProtocol.decode(
                        buffer.copyOf(datagram.length)
                    ) ?: continue

                    if (
                        packet.type == ScratchProtocol.TYPE_TELEMETRY &&
                        packet.sessionId == sessionId
                    ) {
                        onTelemetry(packet)
                    }

                } catch (_: SocketTimeoutException) {
                    // Keep checking for shutdown.
                } catch (error: Exception) {
                    if (!closed) {
                        Log.e(TAG, "Telemetry receive failed", error)
                    }

                    break
                }
            }
        }, "IIDXTouch-UDP-Receiver").apply {
            isDaemon = true
            start()
        }
    }

    fun sendTouch(
        deltaAngle: Float,
        averageRadius: Float,
        touchAngle: Float,
        timestamp: Long,
        fingerCount: Int
    ) {
        sendPacket(
            ScratchPacket(
                type = ScratchProtocol.TYPE_TOUCH,
                sessionId = sessionId,
                sequence = sequence.getAndIncrement(),
                timestamp = timestamp,
                deltaAngle = deltaAngle,
                averageRadius = averageRadius,
                fingerCount = fingerCount,
                touchAngle = touchAngle
            ),
            "touch"
        )
    }

    fun sendRelease(
        timestamp: Long = SystemClock.uptimeMillis()
    ) {
        sendPacket(
            ScratchPacket(
                type = ScratchProtocol.TYPE_RELEASE,
                sessionId = sessionId,
                sequence = sequence.getAndIncrement(),
                timestamp = timestamp
            ),
            "release"
        )
    }


    private fun sendPacket(
        packet: ScratchPacket,
        description: String
    ) {
        if (!connected || closed) return

        val udpSocket = socket ?: return
        val address = resolvedAddress ?: return
        val encoded = ScratchProtocol.encode(packet)

        try {
            executor.execute {
                if (closed || udpSocket.isClosed) return@execute

                try {
                    udpSocket.send(
                        DatagramPacket(
                            encoded,
                            encoded.size,
                            address,
                            port
                        )
                    )

                    Log.d(
                        TAG,
                        "UDP packet successfully sent: " +
                                "type=${encoded[5].toInt() and 0xFF}, " +
                                "description=$description"
                    )
                } catch (error: Exception) {
                    if (!closed) {
                        Log.e(
                            TAG,
                            "Failed to send $description packet",
                            error
                        )
                    }
                }
            }
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            if (!closed) {
                Log.e(TAG, "UDP sender is shutting down", error)
            }
        }
    }


    fun close() {
        closed = true
        connected = false

        socket?.close()
        executor.shutdownNow()
        receiverThread?.interrupt()
    }
}