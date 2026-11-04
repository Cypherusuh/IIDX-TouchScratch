
package com.cypherusuh.iidxtouch

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI

private const val TELEMETRY_TIMEOUT_MS = 120L

@Composable
fun ScratchScreen(
    connected: Boolean,
    angularVelocity: Float,
    averageRadius: Float,
    touchAngle: Float,
    fingerCount: Int,
    lastTelemetryAt: Long,
    onTouchEvent: (MotionEvent) -> Unit,
    onTouchAreaSizeChanged: (IntSize) -> Unit,
    onTelemetryTimeout: () -> Unit
) {
    var platterRotation by remember { mutableFloatStateOf(0f) }

    val currentVelocity by rememberUpdatedState(angularVelocity)
    val currentTelemetryAt by rememberUpdatedState(lastTelemetryAt)
    val currentOnTelemetryTimeout by rememberUpdatedState(onTelemetryTimeout)

    LaunchedEffect(Unit) {
        var previousFrame = 0L
        var telemetryTimedOut = false

        while (true) {
            withFrameNanos { frameTime ->
                if (previousFrame != 0L) {
                    val deltaSeconds =
                        (frameTime - previousFrame) / 1_000_000_000f

                    val telemetryFresh =
                        SystemClock.uptimeMillis() - currentTelemetryAt <=
                                TELEMETRY_TIMEOUT_MS

                    if (telemetryFresh) {
                        telemetryTimedOut = false

                        platterRotation = (
                                platterRotation +
                                        currentVelocity *
                                        deltaSeconds *
                                        (180f / PI.toFloat())
                                ) % 360f
                    } else if (!telemetryTimedOut) {
                        telemetryTimedOut = true
                        currentOnTelemetryTimeout()
                    }
                }

                previousFrame = frameTime
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged(onTouchAreaSizeChanged)
            .pointerInteropFilter { event ->
                onTouchEvent(event)
                true
            }
    ) {
        Platter(
            rotation = platterRotation,
            touchAngle = touchAngle,
            touchRadius = averageRadius,
            modifier = Modifier.fillMaxSize()
        )

        ConnectionAndVelocityPanel(
            connected = connected,
            angularVelocity = angularVelocity,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(20.dp)
        )

        DiagnosticPanel(
            averageRadius = averageRadius,
            touchAngle = touchAngle,
            fingerCount = fingerCount,
            platterRotation = platterRotation,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
