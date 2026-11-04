package com.cypherusuh.iidxtouch

import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cypherusuh.iidxtouch.network.UdpClient

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "IIDXTouch"
        private const val BACKEND_IP = "192.168.1.69"
        private const val BACKEND_PORT = 6699
    }

    private var connected by mutableStateOf(false)
    private var angularVelocity by mutableFloatStateOf(0f)
    private var averageRadius by mutableFloatStateOf(0f)
    private var touchAngle by mutableFloatStateOf(0f)
    private var fingerCount by mutableIntStateOf(0)
    private var lastTelemetryAt by mutableLongStateOf(0L)

    private lateinit var udpClient: UdpClient
    private lateinit var touchHandler: ScratchTouchHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindow()
        initializeNetwork()
        initializeTouchHandler()

        setContent {
            ScratchScreen(
                connected = connected,
                angularVelocity = angularVelocity,
                averageRadius = averageRadius,
                touchAngle = touchAngle,
                fingerCount = fingerCount,
                lastTelemetryAt = lastTelemetryAt,
                onTouchEvent = ::handleTouchEvent,
                onTouchAreaSizeChanged = { size ->
                    touchHandler.updateSize(
                        size.width,
                        size.height
                    )
                },
                onTelemetryTimeout = {
                    angularVelocity = 0f
                    fingerCount = 0
                }
            )
        }

        configureGestureExclusion()

        udpClient.start()
    }

    private fun configureWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK
    }

    private fun configureGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val decorView = window.decorView

        fun updateExclusionRects(view: View) {
            val density = resources.displayMetrics.density

            val edgeWidth = (24 * density).toInt()
                .coerceAtLeast(1)

            val bottomReserved = (24 * density).toInt()

            val width = view.width
            val height = view.height

            if (width <= 0 || height <= 0) return

            val exclusionHeight = (
                    height - bottomReserved
                    ).coerceAtLeast(0)

            val actualEdgeWidth = minOf(edgeWidth, width / 2)

            view.systemGestureExclusionRects = listOf(
                Rect(
                    0,
                    0,
                    actualEdgeWidth,
                    exclusionHeight
                ),
                Rect(
                    width - actualEdgeWidth,
                    0,
                    width,
                    exclusionHeight
                )
            )
        }

        decorView.addOnLayoutChangeListener {
                view, _, _, _, _, _, _, _, _ ->

            updateExclusionRects(view)
        }

        // Apply once immediately after the view has been laid out.
        decorView.post {
            updateExclusionRects(decorView)
        }
    }

    private fun initializeNetwork() {
        udpClient = UdpClient(
            host = BACKEND_IP,
            port = BACKEND_PORT,

            onConnected = {
                runOnUiThread {
                    connected = true
                }
            },

            onError = { error ->
                Log.e(
                    TAG,
                    "Backend connection failed: $error"
                )

                runOnUiThread {
                    connected = false
                }
            },

            onTelemetry = { packet ->
                runOnUiThread {
                    angularVelocity = packet.deltaAngle
                    averageRadius = packet.averageRadius
                    touchAngle = packet.touchAngle
                    fingerCount = packet.fingerCount

                    lastTelemetryAt =
                        android.os.SystemClock.uptimeMillis()
                }
            }
        )
    }

    private fun initializeTouchHandler() {
        touchHandler = ScratchTouchHandler(
            onSample = { sample ->
                udpClient.sendTouch(
                    deltaAngle = sample.deltaAngle,
                    averageRadius = sample.averageRadius,
                    touchAngle = sample.touchAngle,
                    timestamp = sample.timestamp,
                    fingerCount = sample.fingerCount
                )
            },

            onRelease = { timestamp ->
                udpClient.sendRelease(timestamp)
            }
        )
    }

    private fun handleTouchEvent(event: MotionEvent) {
        touchHandler.handleTouchEvent(event)
    }

    override fun onDestroy() {
        if (::udpClient.isInitialized) {
            udpClient.close()
        }

        super.onDestroy()
    }
}