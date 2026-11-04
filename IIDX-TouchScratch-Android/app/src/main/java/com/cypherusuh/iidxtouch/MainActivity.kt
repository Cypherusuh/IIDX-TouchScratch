
package com.cypherusuh.iidxtouch

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.graphics.Color
import android.view.WindowManager
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos

import com.cypherusuh.iidxtouch.network.ScratchPacket
import com.cypherusuh.iidxtouch.network.UdpClient

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IIDXTouch"
        private const val BACKEND_IP = "192.168.1.69"
        private const val BACKEND_PORT = 6699

        private const val MIN_RADIUS_RATIO = 0.05f
        private const val TELEMETRY_TIMEOUT_MS = 120L
    }

    private data class TouchPoint(
        var angle: Float,
        var radius: Float,
        var valid: Boolean
    )

    private data class ScratchSample(
        val deltaAngle: Float,
        val averageRadius: Float,
        val touchAngle: Float,
        val timestamp: Long,
        val fingerCount: Int
    )

    private val activePointers = mutableMapOf<Int, TouchPoint>()

    private var touchWidth = 0f
    private var touchHeight = 0f

    // Compose-observable diagnostic values.
    private var connected by mutableStateOf(false)
    private var angularVelocity by mutableFloatStateOf(0f)
    private var averageRadius by mutableFloatStateOf(0f)
    private var touchAngle by mutableFloatStateOf(0f)
    private var fingerCount by mutableIntStateOf(0)
    private var platterRotation by mutableFloatStateOf(0f)
    private var lastTelemetryAt by mutableLongStateOf(0L)

    private lateinit var udpClient: UdpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        udpClient = UdpClient(
            host = BACKEND_IP,
            port = BACKEND_PORT,
            onConnected = {
                runOnUiThread {
                    connected = true
                }
            },
            onError = { error ->
                Log.e(TAG, "Backend connection failed: $error")
                runOnUiThread {
                    connected = false
                }
            },
            onTelemetry = { packet ->
                runOnUiThread {
                    handleTelemetry(packet)
                }
            }
        )

        udpClient.start()

        setContent {
            // Integrate the velocity received from Go.
            LaunchedEffect(Unit) {
                var previousFrame = 0L

                while (true) {
                    withFrameNanos { frameTime ->
                        if (previousFrame != 0L) {
                            val deltaSeconds =
                                (frameTime - previousFrame) / 1_000_000_000f

                            val telemetryFresh =
                                SystemClock.uptimeMillis() - lastTelemetryAt <=
                                        TELEMETRY_TIMEOUT_MS

                            if (telemetryFresh) {
                                platterRotation = (
                                        platterRotation +
                                                angularVelocity *
                                                deltaSeconds *
                                                (180f / PI.toFloat())
                                        ) % 360f
                            } else {
                                angularVelocity = 0f
                                fingerCount = 0
                            }
                        }

                        previousFrame = frameTime
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black)
                    .onSizeChanged { size ->
                        touchWidth = size.width.toFloat()
                        touchHeight = size.height.toFloat()
                    }
                    .pointerInteropFilter { event ->
                        handleTouchEvent(event)
                        true
                    }
            ) {
                Platter(
                    rotation = platterRotation,
                    touchAngle = touchAngle,
                    touchRadius = averageRadius,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (connected) "BACKEND CONNECTED" else "CONNECTING...",
                        color = if (connected) {
                            ComposeColor(0xFF4CAF50)
                        } else {
                            ComposeColor(0xFFFFB74D)
                        },
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = String.format(
                            java.util.Locale.US,
                            "%.2f rad/s",
                            angularVelocity
                        ),
                        color = ComposeColor.White,
                        fontSize = 30.sp
                    )

                    Text(
                        text = when {
                            angularVelocity > 0.05f -> "CLOCKWISE"
                            angularVelocity < -0.05f -> "COUNTERCLOCKWISE"
                            else -> "STOPPED"
                        },
                        color = ComposeColor.LightGray,
                        fontSize = 13.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(ComposeColor(0xCC151515))
                        .padding(16.dp)
                ) {
                    DiagnosticRow("Touch radius", "%.2f".format(averageRadius))
                    DiagnosticRow("Touch angle", "%.2f rad".format(touchAngle))
                    DiagnosticRow("Active fingers", fingerCount.toString())
                    DiagnosticRow("Platter rotation", "%.1f°".format(platterRotation))
                }
            }
        }
    }

    private fun handleTelemetry(packet: ScratchPacket) {
        angularVelocity = packet.deltaAngle
        averageRadius = packet.averageRadius
        touchAngle = packet.touchAngle
        fingerCount = packet.fingerCount
        lastTelemetryAt = SystemClock.uptimeMillis()
    }

    private fun handleTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                activePointers.clear()
                syncPointers(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                processSample(event)
                syncPointers(event)
            }

            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) {
                    processSample(event, h)
                }

                processSample(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                processSample(event)

                val liftedId = event.getPointerId(event.actionIndex)
                activePointers.remove(liftedId)

                syncPointers(event, event.actionIndex)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    processSample(event)
                }

                activePointers.clear()
            }
        }
    }

    private fun processSample(
        event: MotionEvent,
        historicalPosition: Int = -1
    ) {
        val platterRadius = minOf(touchWidth, touchHeight) / 2f
        if (platterRadius <= 0f) return

        val centerX = touchWidth / 2f
        val centerY = touchHeight / 2f

        var totalDeltaAngle = 0f
        var totalRadius = 0f
        var firstTouchAngle: Float? = null
        var validFingerCount = 0

        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            val previous = activePointers[pointerId] ?: continue

            val x = if (historicalPosition >= 0) {
                event.getHistoricalX(i, historicalPosition)
            } else {
                event.getX(i)
            }

            val y = if (historicalPosition >= 0) {
                event.getHistoricalY(i, historicalPosition)
            } else {
                event.getY(i)
            }

            val dx = x - centerX
            val dy = y - centerY

            val distance = sqrt(dx * dx + dy * dy)
            val radius = distance / platterRadius
            val angle = atan2(dy, dx)

            if (radius < MIN_RADIUS_RATIO) {
                previous.valid = false
                previous.radius = radius
                continue
            }

            if (previous.valid) {
                var deltaAngle = angle - previous.angle

                if (deltaAngle > PI) {
                    deltaAngle -= (2f * PI.toFloat())
                } else if (deltaAngle < -PI) {
                    deltaAngle += (2f * PI.toFloat())
                }

                totalDeltaAngle += deltaAngle
                totalRadius += radius
                validFingerCount++

                if (firstTouchAngle == null) {
                    firstTouchAngle = angle
                }
            }

            previous.angle = angle
            previous.radius = radius
            previous.valid = true
        }

        if (validFingerCount == 0) return

        val timestamp = if (historicalPosition >= 0) {
            event.getHistoricalEventTime(historicalPosition)
        } else {
            event.eventTime
        }

        onScratchSample(
            ScratchSample(
                deltaAngle = totalDeltaAngle / validFingerCount,
                averageRadius = totalRadius / validFingerCount,
                touchAngle = firstTouchAngle ?: 0f,
                timestamp = timestamp,
                fingerCount = validFingerCount
            )
        )
    }

    private fun syncPointers(
        event: MotionEvent,
        excludedIndex: Int = -1
    ) {
        val platterRadius = minOf(touchWidth, touchHeight) / 2f
        if (platterRadius <= 0f) return

        val centerX = touchWidth / 2f
        val centerY = touchHeight / 2f

        for (i in 0 until event.pointerCount) {
            if (i == excludedIndex) continue

            val pointerId = event.getPointerId(i)

            val dx = event.getX(i) - centerX
            val dy = event.getY(i) - centerY

            val distance = sqrt(dx * dx + dy * dy)
            val radius = distance / platterRadius
            val angle = atan2(dy, dx)

            activePointers[pointerId] = TouchPoint(
                angle = angle,
                radius = radius,
                valid = radius >= MIN_RADIUS_RATIO
            )
        }
    }

    private fun onScratchSample(sample: ScratchSample) {
        udpClient.sendTouch(
            deltaAngle = sample.deltaAngle,
            averageRadius = sample.averageRadius,
            touchAngle = sample.touchAngle,
            timestamp = sample.timestamp,
            fingerCount = sample.fingerCount
        )
    }

    override fun onDestroy() {
        if (::udpClient.isInitialized) {
            udpClient.close()
        }

        super.onDestroy()
    }
}

@Composable
private fun Platter(
    rotation: Float,
    touchAngle: Float,
    touchRadius: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f

        // Rotating platter body and markings.
        rotate(rotation, pivot = center) {
            drawCircle(
                color = ComposeColor(0xFF292929),
                radius = radius,
                center = center
            )

            drawCircle(
                color = ComposeColor(0xFF555555),
                radius = radius * 0.86f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx()
                )
            )

            drawCircle(
                color = ComposeColor(0xFF444444),
                radius = radius * 0.55f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx()
                )
            )

            // 60 platter markings.
            for (index in 0 until 60) {
                val major = index % 5 == 0

                rotate(index * 6f, pivot = center) {
                    drawLine(
                        color = if (major) {
                            ComposeColor(0xFFAAAAAA)
                        } else {
                            ComposeColor(0xFF666666)
                        },
                        start = Offset(
                            center.x,
                            center.y - radius * 0.91f
                        ),
                        end = Offset(
                            center.x,
                            center.y - radius * if (major) 0.84f else 0.88f
                        ),
                        strokeWidth = if (major) 3.dp.toPx() else 1.dp.toPx()
                    )
                }
            }

            drawCircle(
                color = ComposeColor(0xFF777777),
                radius = radius * 0.18f,
                center = center
            )

            drawCircle(
                color = ComposeColor(0xFF222222),
                radius = radius * 0.10f,
                center = center
            )
        }

        // Touch marker remains fixed relative to the screen.
        if (touchRadius > 0f) {
            val markerDistance = radius * touchRadius

            val marker = Offset(
                x = center.x + cos(touchAngle) * markerDistance,
                y = center.y + sin(touchAngle) * markerDistance
            )

            drawCircle(
                color = ComposeColor(0xFF4CAF50),
                radius = 9.dp.toPx(),
                center = marker
            )

            drawCircle(
                color = ComposeColor.White,
                radius = 9.dp.toPx(),
                center = marker,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx()
                )
            )
        }

        drawCircle(
            color = ComposeColor(0xFFBBBBBB),
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ComposeColor.LightGray, fontSize = 13.sp)
        Text(value, color = ComposeColor.White, fontSize = 13.sp)
    }
}
