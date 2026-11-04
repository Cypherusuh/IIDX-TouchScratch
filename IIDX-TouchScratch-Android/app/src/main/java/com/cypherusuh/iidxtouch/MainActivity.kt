
package com.cypherusuh.iidxtouch

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
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
                    touchHandler.updateSize(size.width, size.height)
                },
                onTelemetryTimeout = {
                    angularVelocity = 0f
                    fingerCount = 0
                }
            )
        }

        udpClient.start()
    }

    private fun configureWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK
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
                Log.e(TAG, "Backend connection failed: $error")

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
                    lastTelemetryAt = android.os.SystemClock.uptimeMillis()
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
